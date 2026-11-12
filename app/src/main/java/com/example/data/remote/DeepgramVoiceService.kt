package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.MenuItemEntity
import com.example.voice.engine.LocalOrderEngine
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

    companion object {
        private const val TAG = "DeepgramVoiceService"
        private const val CONFIG_CACHE_TTL_MS = 15 * 60 * 1000L

        @Volatile
        private var instance: DeepgramVoiceService? = null

        fun getInstance(): DeepgramVoiceService =
            instance ?: synchronized(this) {
                instance ?: DeepgramVoiceService().also { instance = it }
            }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedConfig: SupabaseService.DeepgramConfig? = null
    @Volatile
    private var lastConfigFetchMs: Long = 0L

    private suspend fun resolveConfig(): SupabaseService.DeepgramConfig {
        val now = System.currentTimeMillis()
        val cached = cachedConfig
        if (cached != null && cached.apiKey.isNotBlank() && (now - lastConfigFetchMs) < CONFIG_CACHE_TTL_MS) {
            return cached
        }

        val remote = try {
            SupabaseService.getInstance().fetchDeepgramConfig()
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
        return resolved
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

        val config = resolveConfig()
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Deepgram API key is not configured"))
        }

        try {
            val normalizedMime = when {
                mimeType.contains("wav", ignoreCase = true) -> "audio/wav"
                mimeType.contains("mp3", ignoreCase = true) -> "audio/mpeg"
                mimeType.contains("ogg", ignoreCase = true) -> "audio/ogg"
                else -> "audio/mp4"
            }

            val urlBuilder = StringBuilder("https://api.deepgram.com/v1/listen")
            urlBuilder.append("?model=").append(encodeParam(config.model))
            urlBuilder.append("&language=").append(encodeParam(config.language))
            urlBuilder.append("&smart_format=true&punctuate=true&numerals=true")

            // Append dynamic menu keyterms (Nova-3) or keywords (Nova-2)
            val terms = buildMenuKeyterms(menuItems)
            val paramName = if (config.model.startsWith("nova-2", ignoreCase = true)) "keywords" else "keyterm"
            for (term in terms) {
                val value = if (paramName == "keywords") "$term:2" else term
                urlBuilder.append("&$paramName=").append(encodeParam(value))
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("Authorization", "Token ${config.apiKey}")
                .addHeader("Content-Type", normalizedMime)
                .post(audioBytes.toRequestBody(normalizedMime.toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Deepgram HTTP ${response.code}: $bodyStr")
                    return@withContext Result.failure(Exception("Deepgram API error (HTTP ${response.code})"))
                }

                val parsed = parseDeepgramResponseJson(bodyStr)
                if (parsed.transcript.isBlank()) {
                    return@withContext Result.failure(Exception("No speech detected in recording"))
                }
                Log.d(TAG, "Deepgram transcript (${parsed.confidence}): \"${parsed.transcript}\"")
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deepgram transcribeAudio failed: ${e.message}", e)
            Result.failure(e)
        }
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

        if (menuItems.isNotEmpty()) {
            MenuCatalog.syncWithRoomMenuItems(menuItems)
        }

        val engine = LocalOrderEngine()
        val orderState = engine.processUtterance(cleaned, VoiceOrderState())

        val parsedItems = orderState.items.map { line ->
            val roomName = menuItems.find { it.name.equals(line.name, ignoreCase = true) }?.name ?: line.name
            ParsedOrderItem(menuItemName = roomName, quantity = line.quantity)
        }

        if (parsedItems.isEmpty()) {
            return Result.failure(Exception("Heard \"$cleaned\", but no menu items matched. Please try again."))
        }

        val lower = cleaned.lowercase()
        val customerName = extractCustomerName(cleaned, lower)
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

    private fun extractCustomerName(original: String, lower: String): String {
        val patterns = listOf(
            Regex("(?:for|name is|customer)\\s+([A-Za-z]{2,20})"),
            Regex("([A-Za-z]{2,20})\\s+ke naam pe")
        )
        val stopWords = setOf(
            "a", "an", "the", "me", "us", "takeaway", "parcel", "packing",
            "coffee", "frappe", "fries", "sandwich", "less", "extra", "no"
        )
        for (regex in patterns) {
            val match = regex.find(lower)
            if (match != null) {
                val raw = match.groupValues[1].trim()
                if (raw.isNotBlank() && raw !in stopWords) {
                    return raw.replaceFirstChar { it.uppercase() }
                }
            }
        }
        return "Walk-in Customer"
    }

    private fun extractPhoneNumber(text: String): String {
        val compact = text.replace(Regex("(?<=\\d)[\\s-]+(?=\\d)"), "")
        return Regex("\\b\\d{10}\\b").find(compact)?.value ?: ""
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
