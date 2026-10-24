package com.example.data.remote

import com.example.BuildConfig
import com.example.data.entity.MenuItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun parseVoiceOrder(
        transcript: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> = withContext(Dispatchers.IO) {
        val userProvidedKey = "AQ.Ab8RN6ItBjoSsHK9wibcIad6P57X7vWBOE4O9fWheNkc8NI-Pg"
        val apiKey = if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            BuildConfig.GEMINI_API_KEY
        } else {
            userProvidedKey
        }

        try {
            val menuSummary = menuItems.joinToString("\n") { "- ${it.name} (Category: ${it.category}, Price: ₹${it.price})" }

            val promptText = """
                Spoken Order Transcript: "$transcript"

                Available Menu Items:
                $menuSummary

                Parse the spoken order above into structured JSON with keys:
                - customerName (String, default to "Walk-in Customer" if not mentioned)
                - contactNumber (String, default to "" if not mentioned)
                - notes (String, default to "" if no special request/customizations mentioned like "less sugar", "extra hot", "no ice", "spicy", "takeaway")
                - items (Array of objects with "menuItemName" matching one of the menu item names above, and "quantity" as integer)

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

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", generationConfig)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(requestUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext parseOrderLocally(transcript, menuItems)
                }

                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                val textResponse = parts?.optJSONObject(0)?.optString("text") ?: ""

                val parsedOrderJson = JSONObject(textResponse.trim())
                val customerName = parsedOrderJson.optString("customerName", "Walk-in Customer")
                val contactNumber = parsedOrderJson.optString("contactNumber", "")
                val notes = parsedOrderJson.optString("notes", "")
                val itemsArray = parsedOrderJson.optJSONArray("items") ?: JSONArray()

                val parsedItems = mutableListOf<ParsedOrderItem>()
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val rawName = itemObj.optString("menuItemName", "")
                    val qty = itemObj.optInt("quantity", 1)

                    // Find closest matching menu item name
                    val matchedItem = menuItems.find {
                        it.name.equals(rawName, ignoreCase = true)
                    } ?: menuItems.find {
                        it.name.lowercase().contains(rawName.lowercase()) || rawName.lowercase().contains(it.name.lowercase())
                    }

                    if (matchedItem != null) {
                        parsedItems.add(ParsedOrderItem(menuItemName = matchedItem.name, quantity = qty))
                    }
                }

                if (parsedItems.isEmpty()) {
                    return@withContext parseOrderLocally(transcript, menuItems)
                }

                Result.success(
                    ParsedVoiceOrder(
                        customerName = if (customerName.isBlank()) "Walk-in Customer" else customerName,
                        contactNumber = contactNumber,
                        items = parsedItems,
                        notes = notes
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback to local regex/keyword parser on error
            parseOrderLocally(transcript, menuItems)
        }
    }

    suspend fun parseVoiceAudio(
        audioBytes: ByteArray,
        mimeType: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> = withContext(Dispatchers.IO) {
        val userProvidedKey = "AQ.Ab8RN6ItBjoSsHK9wibcIad6P57X7vWBOE4O9fWheNkc8NI-Pg"
        val apiKey = if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            BuildConfig.GEMINI_API_KEY
        } else {
            userProvidedKey
        }

        try {
            val menuSummary = menuItems.joinToString("\n") { "- Item Name: \"${it.name}\" | Category: ${it.category} | Price: ₹${it.price}" }
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

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", generationConfig)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(requestUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext parseOrderLocally("", menuItems)
                }

                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                val textResponse = parts?.optJSONObject(0)?.optString("text") ?: ""

                val parsedOrderJson = JSONObject(textResponse.trim())
                val rawTranscript = parsedOrderJson.optString("rawTranscript", "")
                val customerName = parsedOrderJson.optString("customerName", "Walk-in Customer")
                val contactNumber = parsedOrderJson.optString("contactNumber", "")
                val notes = parsedOrderJson.optString("notes", "")
                val itemsArray = parsedOrderJson.optJSONArray("items") ?: JSONArray()

                val parsedItems = mutableListOf<ParsedOrderItem>()
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val rawName = itemObj.optString("menuItemName", "")
                    val qty = itemObj.optInt("quantity", 1)

                    val matchedItem = menuItems.find {
                        it.name.equals(rawName, ignoreCase = true)
                    } ?: menuItems.find {
                        it.name.lowercase().contains(rawName.lowercase()) || rawName.lowercase().contains(it.name.lowercase())
                    }

                    if (matchedItem != null) {
                        parsedItems.add(ParsedOrderItem(menuItemName = matchedItem.name, quantity = qty))
                    }
                }

                if (parsedItems.isEmpty()) {
                    return@withContext parseOrderLocally("", menuItems)
                }

                Result.success(
                    ParsedVoiceOrder(
                        customerName = if (customerName.isBlank()) "Walk-in Customer" else customerName,
                        contactNumber = contactNumber,
                        items = parsedItems,
                        notes = notes,
                        rawTranscript = rawTranscript
                    )
                )
            }
        } catch (e: Exception) {
            parseOrderLocally("", menuItems)
        }
    }

    private fun parseOrderLocally(
        transcript: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> {
        val lower = transcript.lowercase()
        val parsedItems = mutableListOf<ParsedOrderItem>()

        // Extract numbers / quantities
        menuItems.forEach { menuItem ->
            val itemNameLower = menuItem.name.lowercase()
            // Check for key words e.g. "filter coffee", "brownie", "croissant"
            val keyTokens = itemNameLower.split(" ").filter { it.length > 3 }
            val matches = keyTokens.any { lower.contains(it) } || lower.contains(itemNameLower)

            if (matches) {
                // Determine quantity around item name
                var qty = 1
                if (lower.contains("2 ") || lower.contains("two ")) qty = 2
                else if (lower.contains("3 ") || lower.contains("three ")) qty = 3
                else if (lower.contains("4 ") || lower.contains("four ")) qty = 4
                else if (lower.contains("5 ") || lower.contains("five ")) qty = 5

                parsedItems.add(ParsedOrderItem(menuItem.name, qty))
            }
        }

        // Extract customer name if "for <Name>" is present
        var customerName = "Walk-in Customer"
        val forIndex = lower.indexOf("for ")
        if (forIndex != -1) {
            val afterFor = transcript.substring(forIndex + 4).trim().split(" ")[0]
            if (afterFor.isNotBlank() && afterFor.lowercase() !in listOf("a", "the", "me", "us", "takeaway")) {
                customerName = afterFor.replaceFirstChar { it.uppercase() }
            }
        }

        // Extract phone number if digits are present
        val phoneRegex = Regex("\\b\\d{10}\\b")
        val phoneMatch = phoneRegex.find(transcript)
        val contactNumber = phoneMatch?.value ?: ""

        val defaultItems = if (parsedItems.isEmpty() && menuItems.isNotEmpty()) {
            listOf(ParsedOrderItem(menuItems.first().name, 1))
        } else {
            parsedItems
        }

        return Result.success(
            ParsedVoiceOrder(
                customerName = customerName,
                contactNumber = contactNumber,
                items = defaultItems
            )
        )
    }

    suspend fun generateStoryNote(
        title: String,
        date: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val userProvidedKey = "AQ.Ab8RN6ItBjoSsHK9wibcIad6P57X7vWBOE4O9fWheNkc8NI-Pg"
        val apiKey = if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            BuildConfig.GEMINI_API_KEY
        } else {
            userProvidedKey
        }

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
                val generationConfig = JSONObject().apply {
                    put("temperature", 0.7)
                }
                put("generationConfig", generationConfig)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(requestUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.success("Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.")
                }
                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                val textResponse = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""
                if (textResponse.isNotBlank()) {
                    Result.success(textResponse.replace("\"", "").trim())
                } else {
                    Result.success("Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.")
                }
            }
        } catch (e: Exception) {
            Result.success("Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.")
        }
    }
}

