package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.MenuItemEntity
import com.example.voice.engine.LocalOrderEngine
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeepgramTranscription(
    val transcript: String,
    val confidence: Double,
    val detectedLanguage: String = ""
)

/**
 * Speech-to-Text and POS Order Parsing service powered by Deepgram (Nova-3).
 * Uses dynamic keyterm prompting from the live menu catalog for high accuracy
 * in noisy coffee shop environments and supports Hinglish code-switching.
 */
class DeepgramVoiceService private constructor() {

    private data class HttpResult(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String
    )

    companion object {
        private const val TAG = "DeepgramVoiceService"
        private const val CONFIG_CACHE_TTL_MS = 15 * 60 * 1000L

        private val PHONE_COMPACT_REGEX = Regex("(?<=\\d)[\\s-]+(?=\\d)")
        private val INDIAN_PHONE_REGEX = Regex("(?<!\\d)(?:(?:\\+?91|0)[\\s-]?)?([6-9]\\d{9})(?!\\d)")
        private val FALLBACK_10_DIGIT_PHONE_REGEX = Regex("(?<!\\d)(\\d{10})(?!\\d)")

        private val NAME_PATTERNS = listOf(
            Regex("\\b(?:for|name is|customer)\\s+([A-Za-z]{2,20})\\b"),
            Regex("\\b([A-Za-z]{2,20})\\s+ke naam pe\\b")
        )

        private val NAME_STOP_WORDS = setOf(
            "a", "an", "the", "me", "us", "here", "now", "today", "only", "just",
            "takeaway", "parcel", "packing", "dine", "table", "car", "parking",
            "order", "pickup", "delivery", "counter", "bill", "customer", "guest",
            "someone", "anyone", "bhai", "bhaiya", "sir", "madam", "please", "pls",
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "ek", "do", "teen", "char", "chaar", "paanch", "panch", "chhe", "saat", "aath", "nau", "das",
            "single", "double", "triple", "more", "some", "extra", "less", "no", "with", "without",
            "coffee", "frappe", "fries", "sandwich", "americano", "vietnamese", "hazelnut",
            "caramel", "vanilla", "vanila", "paneer", "bombay", "cheese", "corn", "onion",
            "salted", "peri", "iced", "hot", "cold", "sugar", "ice", "milk", "oat", "almond"
        )

        @Volatile
        private var instance: DeepgramVoiceService? = null

        fun getInstance(): DeepgramVoiceService =
            instance ?: synchronized(this) {
                instance ?: DeepgramVoiceService().also { instance = it }
            }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val configMutex = Mutex()
    @Volatile
    private var cachedConfig: SupabaseService.DeepgramConfig? = null
    @Volatile
    private var lastConfigFetchMs: Long = 0L

    private suspend fun invalidateConfigCache() {
        configMutex.withLock {
            cachedConfig = null
            lastConfigFetchMs = 0L
        }
    }

    private suspend fun resolveConfig(forceRefresh: Boolean = false): SupabaseService.DeepgramConfig {
        return configMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = cachedConfig
            if (!forceRefresh && cached != null && cached.apiKey.isNotBlank() &&
                (now - lastConfigFetchMs) < CONFIG_CACHE_TTL_MS
            ) {
                return@withLock cached
            }

            val remote = try {
                SupabaseService.getInstance().fetchDeepgramConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

            val buildKey = try {
                val field = BuildConfig::class.java.getField("DEEPGRAM_API_KEY")
                (field.get(null) as? String).orEmpty()
            } catch (_: Exception) {
                ""
            }

            val effectiveKey = remote?.apiKey?.takeIf { it.isNotBlank() }
                ?: buildKey.takeIf { it.isNotBlank() && it != "MY_DEEPGRAM_API_KEY" }
                ?: ""

            val resolved = SupabaseService.DeepgramConfig(
                apiKey = effectiveKey,
                model = remote?.model?.ifBlank { "nova-3" } ?: "nova-3",
                language = remote?.language?.ifBlank { "multi" } ?: "multi"
            )
            if (resolved.apiKey.isNotBlank()) {
                cachedConfig = resolved
                lastConfigFetchMs = now
            }
            resolved
        }
    }

    private suspend fun executeRequest(request: Request): HttpResult =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
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

    private fun buildListenRequest(
        audioBytes: ByteArray,
        normalizedMime: String,
        config: SupabaseService.DeepgramConfig,
        menuItems: List<MenuItemEntity>
    ): Request {
        val urlBuilder = StringBuilder("https://api.deepgram.com/v1/listen")
        urlBuilder.append("?model=").append(encodeParam(config.model))
        urlBuilder.append("&language=").append(encodeParam(config.language))
        urlBuilder.append("&smart_format=true&punctuate=true&numerals=true")

        val terms = buildMenuKeyterms(menuItems)
        val isNova2 = config.model.startsWith("nova-2", ignoreCase = true)
        val paramName = if (isNova2) "keywords" else "keyterm"
        for (term in terms) {
            val encodedTerm = encodeParam(term)
            val paramValue = if (isNova2) "$encodedTerm:2" else encodedTerm
            urlBuilder.append("&$paramName=").append(paramValue)
        }

        return Request.Builder()
            .url(urlBuilder.toString())
            .addHeader("Authorization", "Token ${config.apiKey}")
            .addHeader("Content-Type", normalizedMime)
            .post(audioBytes.toRequestBody(normalizedMime.toMediaType()))
            .build()
    }

    /**
     * Transcribes recorded audio bytes (M4A, MP4, WAV) using Deepgram REST API.
     */
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String,
        menuItems: List<MenuItemEntity> = emptyList()
    ): Result<DeepgramTranscription> = withContext(Dispatchers.IO) {
        if (audioBytes.isEmpty()) {
            return@withContext Result.failure(Exception("Empty audio recording"))
        }

        var config = resolveConfig()
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Deepgram API key is not configured"))
        }

        val normalizedMime = when {
            mimeType.contains("wav", ignoreCase = true) -> "audio/wav"
            mimeType.contains("mp3", ignoreCase = true) -> "audio/mpeg"
            mimeType.contains("ogg", ignoreCase = true) -> "audio/ogg"
            else -> "audio/mp4"
        }

        var authRefreshed = false
        var lastError: Exception? = null

        for (attempt in 0..1) {
            try {
                val request = buildListenRequest(audioBytes, normalizedMime, config, menuItems)
                val response = executeRequest(request)

                if (response.code == 401 || response.code == 403) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Deepgram auth HTTP ${response.code}: ${response.body}")
                    } else {
                        Log.w(TAG, "Deepgram auth HTTP ${response.code}")
                    }
                    invalidateConfigCache()
                    if (!authRefreshed) {
                        authRefreshed = true
                        val refreshed = resolveConfig(forceRefresh = true)
                        if (refreshed.apiKey.isNotBlank() && refreshed.apiKey != config.apiKey) {
                            config = refreshed
                            continue
                        }
                    }
                    return@withContext Result.failure(Exception("Deepgram authentication failed (HTTP ${response.code})"))
                }

                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Deepgram HTTP ${response.code}: ${response.body}")
                    } else {
                        Log.w(TAG, "Deepgram HTTP ${response.code}")
                    }
                    lastError = Exception("Deepgram API error (HTTP ${response.code})")
                    if (response.code in 500..599 && attempt == 0) {
                        delay(250)
                        continue
                    }
                    return@withContext Result.failure(lastError)
                }

                val parsed = parseDeepgramResponseJson(response.body)
                if (parsed.transcript.isBlank()) {
                    return@withContext Result.failure(Exception("No speech detected in recording"))
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Deepgram transcript (${parsed.confidence}): \"${parsed.transcript}\"")
                }
                return@withContext Result.success(parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Deepgram IO error (attempt ${attempt + 1}): ${e.message}")
                }
                if (attempt == 0) {
                    delay(250)
                    continue
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Deepgram transcribeAudio failed: ${e.message}", e)
                } else {
                    Log.e(TAG, "Deepgram transcribeAudio failed: ${e.javaClass.simpleName}")
                }
                return@withContext Result.failure(e)
            }
        }

        val finalError = lastError ?: Exception("Deepgram transcription failed")
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Deepgram transcribeAudio failed after retry: ${finalError.message}", finalError)
        } else {
            Log.e(TAG, "Deepgram transcribeAudio failed after retry: ${finalError.javaClass.simpleName}")
        }
        Result.failure(finalError)
    }

    /**
     * Transcribes audio via Deepgram and maps it to a structured POS ParsedVoiceOrder.
     */
    suspend fun parseVoiceAudio(
        audioBytes: ByteArray,
        mimeType: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> = withContext(Dispatchers.IO) {
        val sttResult = transcribeAudio(audioBytes, mimeType, menuItems)
        sttResult.fold(
            onSuccess = { transcription ->
                parseTranscriptToOrder(transcription.transcript, menuItems)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Converts a raw Hinglish/English transcript into a structured ParsedVoiceOrder
     * using MenuCatalog + LocalOrderEngine plus customer name/phone/notes extraction.
     */
    fun parseTranscriptToOrder(
        transcript: String,
        menuItems: List<MenuItemEntity>
    ): Result<ParsedVoiceOrder> {
        val cleaned = transcript.trim()
        if (cleaned.isBlank()) {
            return Result.failure(Exception("Empty transcript"))
        }

        val availableMenu = menuItems.filter { it.isAvailable }
        if (menuItems.isNotEmpty()) {
            if (availableMenu.isEmpty()) {
                return Result.failure(Exception("No menu items are currently available."))
            }
            MenuCatalog.syncWithRoomMenuItems(availableMenu)
        }

        val engine = LocalOrderEngine()
        val orderState = engine.processUtterance(cleaned, VoiceOrderState())

        val parsedItems = orderState.items.map { line ->
            val roomName = availableMenu.find { it.name.equals(line.name, ignoreCase = true) }?.name ?: line.name
            ParsedOrderItem(menuItemName = roomName, quantity = line.quantity.coerceAtLeast(1))
        }

        if (parsedItems.isEmpty()) {
            return Result.failure(Exception("Heard \"$cleaned\", but no menu items matched. Please try again."))
        }

        val lower = cleaned.lowercase()
        val customerName = extractCustomerName(lower, availableMenu)
        val contactNumber = extractPhoneNumber(cleaned)
        val notes = extractOrderNotes(lower, orderState)

        return Result.success(
            ParsedVoiceOrder(
                customerName = customerName,
                contactNumber = contactNumber,
                items = parsedItems,
                notes = notes,
                rawTranscript = cleaned
            )
        )
    }

    internal fun parseDeepgramResponseJson(bodyStr: String): DeepgramTranscription {
        val root = JSONObject(bodyStr)
        val results = root.optJSONObject("results")
        val channels = results?.optJSONArray("channels")
        val firstChannel = channels?.optJSONObject(0)
        val alternatives = firstChannel?.optJSONArray("alternatives")
        val bestAlt = alternatives?.optJSONObject(0)
        val transcript = bestAlt?.optString("transcript", "")?.trim().orEmpty()
        val confidence = bestAlt?.optDouble("confidence", 0.0) ?: 0.0
        val detectedLang = firstChannel?.optString("detected_language", "").orEmpty()
        return DeepgramTranscription(
            transcript = transcript,
            confidence = confidence,
            detectedLanguage = detectedLang
        )
    }

    private fun buildMenuKeyterms(menuItems: List<MenuItemEntity>): List<String> {
        val coreTerms = listOf(
            "OG Vietnamese",
            "Hazelnut Iced Coffee",
            "Caramel Iced Coffee",
            "Hazelnut Frappe",
            "Caramel Frappe",
            "Peri Peri Fries",
            "Bombay Sandwich",
            "Cheese Corn Sandwich",
            "Paneer Sandwich",
            "Americano",
            "takeaway",
            "less sugar",
            "no sugar",
            "extra hot"
        )
        val menuNames = menuItems
            .filter { it.isAvailable }
            .map { it.name.trim() }
            .filter { it.isNotBlank() && it.length <= 40 }
        return (menuNames + coreTerms).distinct().take(65)
    }

    private fun extractCustomerName(lower: String, menuItems: List<MenuItemEntity>): String {
        val menuTokens = menuItems
            .flatMap { it.name.lowercase().split(Regex("[^a-z0-9]+")) }
            .filter { it.length >= 2 }
            .toSet()

        for (regex in NAME_PATTERNS) {
            for (match in regex.findAll(lower)) {
                val raw = match.groupValues[1].trim()
                if (raw.isNotBlank() && raw !in NAME_STOP_WORDS && raw !in menuTokens) {
                    return raw.replaceFirstChar { it.uppercase() }
                }
            }
        }
        return "Walk-in Customer"
    }

    private fun extractPhoneNumber(text: String): String {
        val compact = text.replace(PHONE_COMPACT_REGEX, "")
        val indianMatch = INDIAN_PHONE_REGEX.find(compact)
        if (indianMatch != null) {
            return indianMatch.groupValues[1]
        }
        return FALLBACK_10_DIGIT_PHONE_REGEX.find(compact)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun extractOrderNotes(lower: String, orderState: VoiceOrderState): String {
        val notes = mutableListOf<String>()
        orderState.items.forEach { item ->
            item.modifiers.forEach { mod ->
                if (!notes.contains(mod.name)) notes.add(mod.name)
            }
        }
        if (listOf("parcel", "packing", "takeaway", "take away", "pack kar").any { lower.contains(it) }) {
            if (!notes.contains("Takeaway")) notes.add("Takeaway")
        }
        if (listOf("bina cheeni", "no sugar", "without sugar", "sugar free").any { lower.contains(it) }) {
            if (!notes.contains("No sugar")) notes.add("No sugar")
        } else if (listOf("kam meetha", "less sugar", "less sweet").any { lower.contains(it) }) {
            if (!notes.contains("Less sugar")) notes.add("Less sugar")
        }
        if (listOf("extra hot", "kadak", "zyada garam").any { lower.contains(it) }) {
            if (!notes.contains("Extra hot")) notes.add("Extra hot")
        }
        if (listOf("no ice", "bina ice", "without ice").any { lower.contains(it) }) {
            if (!notes.contains("No ice")) notes.add("No ice")
        }
        return notes.joinToString(", ")
    }

    private fun encodeParam(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
