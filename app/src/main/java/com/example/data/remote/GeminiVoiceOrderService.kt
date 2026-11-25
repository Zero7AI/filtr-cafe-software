package com.example.data.remote

import com.example.BuildConfig
import com.example.data.entity.MenuItemEntity
import com.example.voice.engine.LocalOrderEngine
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedOrderItem(
    val menuItemName: String,
    val quantity: Int
)

data class ParsedVoiceOrder(
    val customerName: String,
    val contactNumber: String,
    val items: List<ParsedOrderItem>,
    val notes: String = "",
    val rawTranscript: String = ""
)

class GeminiVoiceOrderService {

    private data class HttpResult(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String
    )

    companion object {
        private const val GEMINI_GENERATE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun resolveApiKey(): String? {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return key.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
    }

    private suspend fun executeRequest(request: Request): HttpResult =
        suspendCancellableCoroutine { cont ->
            val call = sharedClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { resp ->
                            val result = HttpResult(
                                code = resp.code,
                                isSuccessful = resp.isSuccessful,
                                body = resp.body?.string().orEmpty()
                            )
                            if (cont.isActive) {
                                cont.resume(result)
                            }
                        }
                    } catch (t: Throwable) {
                        if (cont.isActive) {
                            cont.resumeWithException(t)
                        }
                    }
                }
            })
        }

    private fun buildGeminiRequest(apiKey: String, payload: JSONObject): Request {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(GEMINI_GENERATE_URL)
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()
    }

    private fun extractGeminiText(responseBodyStr: String): String {
        val responseJson = JSONObject(responseBodyStr)
        val candidates = responseJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
        return parts?.optJSONObject(0)?.optString("text", "")?.trim().orEmpty()
    }

    private fun matchParsedOrderItems(
        itemsArray: JSONArray,
        availableItems: List<MenuItemEntity>
    ): List<ParsedOrderItem> {
        val parsedItems = mutableListOf<ParsedOrderItem>()
        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val rawName = itemObj.optString("menuItemName", "").trim()
            if (rawName.isBlank()) continue
            val qty = itemObj.optInt("quantity", 1).coerceAtLeast(1)

            val matchedItem = availableItems.find {
                it.name.equals(rawName, ignoreCase = true)
            } ?: if (rawName.length >= 2) {
                availableItems.find {
                    it.name.lowercase().contains(rawName.lowercase()) ||
                        rawName.lowercase().contains(it.name.lowercase())
                }
            } else {
                null
            }

            if (matchedItem != null) {
                parsedItems.add(ParsedOrderItem(menuItemName = matchedItem.name, quantity = qty))
            }
        }
        return parsedItems
    }

    suspend fun parseVoiceOrder(
        transcript: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> = withContext(Dispatchers.IO) {
        val cleanedTranscript = transcript.trim()
        if (cleanedTranscript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Empty transcript"))
        }

        val availableItems = menuItems.filter { it.isAvailable }
        if (availableItems.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No available menu items"))
        }

        val apiKey = resolveApiKey()
            ?: return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))

        try {
            val menuSummary = availableItems.joinToString("\n") {
                "- ${it.name} (Category: ${it.category}, Price: ₹${it.price})"
            }

            val promptText = """
                Spoken Order Transcript: "$cleanedTranscript"

                Available Menu Items:
                $menuSummary

                Parse the spoken order above into structured JSON with keys:
                - customerName (String, default to "Walk-in Customer" if not mentioned)
                - contactNumber (String, default to "" if not mentioned)
                - notes (String, default to "" if no special request/customizations mentioned like "less sugar", "extra hot", "no ice", "spicy", "takeaway")
                - items (Array of objects with "menuItemName" matching one of the menu item names above, and "quantity" as integer >= 1)

                Output ONLY valid raw JSON with no markdown backticks.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val response = executeRequest(buildGeminiRequest(apiKey, requestJson))
            if (!response.isSuccessful) {
                return@withContext parseOrderLocally(cleanedTranscript, availableItems)
            }

            val textResponse = extractGeminiText(response.body)
            if (textResponse.isBlank()) {
                return@withContext parseOrderLocally(cleanedTranscript, availableItems)
            }

            val parsedOrderJson = JSONObject(textResponse)
            val customerName = parsedOrderJson.optString("customerName", "Walk-in Customer").trim()
            val contactNumber = parsedOrderJson.optString("contactNumber", "").trim()
            val notes = parsedOrderJson.optString("notes", "").trim()
            val itemsArray = parsedOrderJson.optJSONArray("items") ?: JSONArray()

            val parsedItems = matchParsedOrderItems(itemsArray, availableItems)
            if (parsedItems.isEmpty()) {
                return@withContext parseOrderLocally(cleanedTranscript, availableItems)
            }

            Result.success(
                ParsedVoiceOrder(
                    customerName = customerName.ifBlank { "Walk-in Customer" },
                    contactNumber = contactNumber,
                    items = parsedItems,
                    notes = notes,
                    rawTranscript = cleanedTranscript
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            parseOrderLocally(cleanedTranscript, availableItems)
        }
    }

    suspend fun parseVoiceAudio(
        audioBytes: ByteArray,
        mimeType: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> = withContext(Dispatchers.IO) {
        if (audioBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty audio recording"))
        }

        val availableItems = menuItems.filter { it.isAvailable }
        if (availableItems.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No available menu items"))
        }

        val apiKey = resolveApiKey()
            ?: return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))

        try {
            val menuSummary = availableItems.joinToString("\n") {
                "- Item Name: \"${it.name}\" | Category: ${it.category} | Price: ₹${it.price}"
            }
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

            val promptText = """
                You are an expert AI Cashier and POS Order Assistant for FILTR Coffee Store.
                Here is our official store Menu Catalog & SKU List:
                $menuSummary

                === ACOUSTIC & AMBIENT NOISE FILTERING ===
                - The audio is recorded at a busy physical coffee shop counter.
                - Ambient noise may be present: espresso machine steam hissing, grinder noise, blender sounds, background music, and secondary voices.
                - Isolate and focus ONLY on the primary customer speaking directly at the counter. Ignore background kitchen/barista talk.

                === HINGLISH & CAFE VOCABULARY ===
                - Customers frequently order in Hindi, English, or mixed Hinglish.
                - Map Hindi numbers to integer quantities:
                  * "ek" / "single" -> 1
                  * "do" / "double" -> 2
                  * "teen" -> 3
                  * "chaar" -> 4
                  * "paanch" -> 5
                  * "chhe" -> 6
                - Conversational fillers to ignore: "bhaiya", "suno", "de do", "kar dena", "bana do", "pack kar do".
                - Modifiers & Notes:
                  * "parcel", "packing", "takeaway", "pack kar do" -> Add "Takeaway" to notes.
                  * "bina cheeni", "no sugar", "without sugar" -> Add "No sugar" to notes.
                  * "kam meetha", "less sugar" -> Add "Less sugar" to notes.
                  * "extra hot", "kadak", "zyada garam" -> Add "Extra hot" to notes.
                  * "kam theekha", "less spicy" -> Add "Less spicy" to notes.
                  * "extra cheese" -> Add "Extra cheese" to notes.
                - Self-corrections: If customer says "ek cappuccino... nahi, ek hot filter coffee do", honor the final choice.
                - Name & Phone: "for Rahul", "Rahul ke naam pe" -> customerName: "Rahul". Spoken 10-digit number -> contactNumber: "9876543210".

                === SKU MATCHING ===
                - Match each ordered item to the closest exact "Item Name" from the Menu Catalog SKU list above.
                - Common nicknames: "kaapi" / "south filter" -> "CLASSIC", "vietnamese without milk" -> "OG VIETNAMESE WITHOUT MILK", "hottie" -> "CARAMEL HOTTIE" or "HAZELNUT HOTTIE" as spoken.

                Return strictly JSON with no markdown formatting or backticks:
                {
                  "rawTranscript": "verbatim speech heard in Hinglish/English",
                  "customerName": "Walk-in Customer",
                  "contactNumber": "",
                  "notes": "",
                  "items": [
                    {
                      "menuItemName": "exact item name from catalog",
                      "quantity": 1
                    }
                  ]
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Audio)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val response = executeRequest(buildGeminiRequest(apiKey, requestJson))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini API error (HTTP ${response.code})"))
            }

            val textResponse = extractGeminiText(response.body)
            if (textResponse.isBlank()) {
                return@withContext Result.failure(Exception("Gemini returned an empty response"))
            }

            val parsedOrderJson = JSONObject(textResponse)
            val rawTranscript = parsedOrderJson.optString("rawTranscript", "").trim()
            val customerName = parsedOrderJson.optString("customerName", "Walk-in Customer").trim()
            val contactNumber = parsedOrderJson.optString("contactNumber", "").trim()
            val notes = parsedOrderJson.optString("notes", "").trim()
            val itemsArray = parsedOrderJson.optJSONArray("items") ?: JSONArray()

            val parsedItems = matchParsedOrderItems(itemsArray, availableItems)
            if (parsedItems.isEmpty()) {
                return@withContext Result.failure(Exception("No matching menu items recognized from audio"))
            }

            Result.success(
                ParsedVoiceOrder(
                    customerName = customerName.ifBlank { "Walk-in Customer" },
                    contactNumber = contactNumber,
                    items = parsedItems,
                    notes = notes,
                    rawTranscript = rawTranscript
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOrderLocally(
        transcript: String,
        availableItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> {
        val cleaned = transcript.trim()
        if (cleaned.isBlank() || availableItems.isEmpty()) {
            return Result.failure(Exception("No menu items matched"))
        }

        MenuCatalog.syncWithRoomMenuItems(availableItems)
        val orderState = LocalOrderEngine().processUtterance(cleaned, VoiceOrderState())

        val parsedItems = orderState.items.map { line ->
            val roomName = availableItems.find { it.name.equals(line.name, ignoreCase = true) }?.name ?: line.name
            ParsedOrderItem(menuItemName = roomName, quantity = line.quantity.coerceAtLeast(1))
        }

        if (parsedItems.isEmpty()) {
            return Result.failure(Exception("Could not match any available menu items"))
        }

        val lower = cleaned.lowercase()
        val stopWords = setOf(
            "a", "an", "the", "me", "us", "takeaway", "parcel", "packing",
            "table", "one", "two", "three", "four", "five", "dine", "car",
            "parking", "order", "coffee", "frappe", "fries", "sandwich"
        )
        var customerName = "Walk-in Customer"
        val nameMatch = Regex("\\b(?:for|name is|customer)\\s+([A-Za-z]{2,20})\\b").findAll(lower)
            .map { it.groupValues[1].trim() }
            .firstOrNull { it.isNotBlank() && it !in stopWords }
        if (nameMatch != null) {
            customerName = nameMatch.replaceFirstChar { it.uppercase() }
        }

        val compact = cleaned.replace(Regex("(?<=\\d)[\\s-]+(?=\\d)"), "")
        val contactNumber = Regex("(?<!\\d)(?:(?:\\+?91|0)[\\s-]?)?([6-9]\\d{9})(?!\\d)")
            .find(compact)?.groupValues?.getOrNull(1)
            ?: Regex("(?<!\\d)(\\d{10})(?!\\d)").find(compact)?.groupValues?.getOrNull(1)
            ?: ""

        return Result.success(
            ParsedVoiceOrder(
                customerName = customerName,
                contactNumber = contactNumber,
                items = parsedItems,
                notes = orderState.orderNotes.orEmpty(),
                rawTranscript = cleaned
            )
        )
    }

    suspend fun generateStoryNote(
        title: String,
        date: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
            ?: return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))

        try {
            val promptText = """
                Write a short, heartfelt 2-sentence micro-journal entry for a specialty coffee shop customer receipt.
                Topic / R&D Title: "$title"
                ${if (date.isNotBlank()) "Date: $date" else ""}

                Guidelines:
                - Tone: passionate, vintage, personal, showing hard work and obsession behind perfecting coffee for our customers.
                - Max 25 words.
                - Example: "Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you."
                - Output ONLY the 1-2 sentence note, with no quotes or markdown.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val response = executeRequest(buildGeminiRequest(apiKey, requestJson))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini API error (HTTP ${response.code})"))
            }

            val textResponse = extractGeminiText(response.body)
            if (textResponse.isNotBlank()) {
                Result.success(textResponse.replace("\"", "").trim())
            } else {
                Result.failure(Exception("Empty story note response"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
