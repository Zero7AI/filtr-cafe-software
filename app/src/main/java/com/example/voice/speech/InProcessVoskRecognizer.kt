package com.example.voice.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.voice.audio.SpeakerModalityAnalyzer
import com.example.voice.audio.VoiceModalityProfile
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * 100% Silent, In-Process Offline Speech Recognizer powered by Vosk (Indian English).
 *
 * Driven directly by VoiceActivityDetector turns from AudioCaptureService's single AudioRecord stream:
 * - onTurnStart(preRollBytes): resets decoder and feeds 500ms pre-roll audio
 * - onTurnChunk(chunkBytes): feeds active speech audio and emits live partial transcripts
 * - onTurnEnd(): flushes rec.finalResult immediately when speech ends so orders are punched with zero lag
 */
class InProcessVoskRecognizer(
    private val context: Context,
    private val onTranscriptResult: (text: String, isFinal: Boolean, modality: VoiceModalityProfile?) -> Unit,
    private val onReadyStateChanged: (ready: Boolean, statusMsg: String) -> Unit
) {
    private val tag = "InProcessVosk"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modalityAnalyzer = SpeakerModalityAnalyzer()

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    val isReady = AtomicBoolean(false)
    val isEnabled = AtomicBoolean(true)

    // Domain-constrained vocabulary for FILTR Coffee POS + Hinglish + [unk] for noise absorption.
    private val filtrGrammarPhrases = listOf(
        "caramel frap", "caramel trap", "caramel wrap", "caramel flap", "caramel frappe",
        "hazelnut frap", "hazelnut trap", "hazelnut wrap", "hazelnut flap", "hazelnut frappe",
        "just frap", "just trap", "just wrap", "just frappe",
        "vanilla frap", "vanilla trap", "vanilla wrap", "vanilla frappe", "frap", "frappe",
        "perry perry fries", "perry perry", "periphery fries", "periphery",
        "berry berry fries", "berry berry", "ferry ferry fries", "ferry ferry",
        "very very fries", "very very", "fairy fairy fries", "peri peri fries", "peri peri",
        "salted fries", "french fries", "fries",
        "americano", "iced americano", "ice americano", "black coffee",
        "caramel iced coffee", "caramel ice coffee", "caramelized coffee", "caramel coffee", "iced caramel",
        "hazelnut iced coffee", "hazelnut ice coffee", "hazelnut coffee", "iced hazelnut",
        "og vietnamese", "o g vietnamese", "o g", "oh gee vietnamese", "oh gee", "vietnamese iced coffee", "vietnamese ice coffee", "vietnamese coffee", "vietnamese",
        "vanilla iced coffee", "vanilla ice coffee", "vanilla coffee",
        "bombay sandwich", "bombay",
        "caramelized onion sandwich", "onion sandwich", "caramelized onion",
        "cheese corn sandwich", "corn sandwich", "cheese corn", "cheese sandwich",
        "pioneer sandwich", "pan near sandwich", "pane sandwich", "funny sandwich", "paneer sandwich", "paneer",
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "a", "an", "single", "double", "triple", "more", "one more", "another",
        "ek", "do", "teen", "char", "punch", "or",
        "and", "plus", "with", "also", "okay", "ok",
        "acha", "to", "aap", "hai", "sir", "madam", "right", "yes", "no", "yeah", "sure",
        "kar", "dena", "aur", "bas", "total", "bill", "repeat", "check",
        "add", "remove", "cancel", "delete", "minus", "hata do",
        "clear", "reset", "start over", "confirm", "done", "order", "please",
        "extra cheese", "no ice", "no onion", "less sweet", "sugar free",
        "[unk]"
    )

    init {
        initModelAsync()
    }

    private fun initModelAsync() {
        executor.execute {
            try {
                mainHandler.post {
                    onReadyStateChanged(false, "Loading Indian-English Offline Voice Model...")
                }

                val targetRoot = File(context.filesDir, "vosk_models")
                val modelDir = File(targetRoot, "vosk-model-small-en-in-0.4")
                val finalMdl = File(modelDir, "am/final.mdl")

                if (!finalMdl.exists()) {
                    Log.i(tag, "Extracting vosk-model-en-in.zip from assets to ${targetRoot.absolutePath}...")
                    extractZipFromAssets("vosk-model-en-in.zip", targetRoot)
                }

                if (!finalMdl.exists()) {
                    Log.e(tag, "Model file missing after extraction: ${finalMdl.absolutePath}")
                    mainHandler.post {
                        onReadyStateChanged(false, "Offline STT Model Missing")
                    }
                    return@execute
                }

                val loadedModel = Model(modelDir.absolutePath)
                val initialPhrases = com.example.voice.engine.MenuCatalog.getActiveGrammarPhrases().ifEmpty { filtrGrammarPhrases }
                val grammarJson = JSONArray(initialPhrases).toString()
                val rec = try {
                    Log.i(tag, "Initializing Vosk Recognizer with dynamic domain grammar (${initialPhrases.size} phrases)...")
                    Recognizer(loadedModel, 16000.0f, grammarJson)
                } catch (e: Exception) {
                    Log.w(tag, "Grammar init fallback to full vocabulary: ${e.message}")
                    Recognizer(loadedModel, 16000.0f)
                }

                this.model = loadedModel
                this.recognizer = rec
                this.lastAppliedPhrases = initialPhrases
                isReady.set(true)

                // Wire automatic real-time grammar updates directly from MenuCatalog
                com.example.voice.engine.MenuCatalog.onMenuUpdated = { updatedPhrases ->
                    updateGrammar(updatedPhrases)
                }

                Log.i(tag, "InProcessVoskRecognizer initialized successfully (100% offline, menu-constrained)")
                mainHandler.post {
                    onReadyStateChanged(true, "Offline Voice STT Active (${initialPhrases.size} phrases)")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize InProcessVoskRecognizer", e)
                mainHandler.post {
                    onReadyStateChanged(false, "STT Init Error: ${e.message}")
                }
            }
        }
    }

    @Volatile
    private var lastAppliedPhrases: List<String> = emptyList()

    /**
     * Hot-swaps the speech recognizer decoding grammar in memory (<15ms)
     * whenever menu items are added, removed, or modified.
     * Runs 100% on the device without server calls or model retraining.
     */
    fun updateGrammar(phrases: List<String>) {
        if (!isReady.get()) {
            Log.w(tag, "Vosk recognizer not ready yet, grammar will be loaded during initialization.")
            return
        }
        if (phrases == lastAppliedPhrases) {
            return
        }
        executor.execute {
            val mdl = model ?: return@execute
            if (phrases == lastAppliedPhrases) return@execute
            try {
                val grammarJson = JSONArray(phrases).toString()
                Log.i(tag, "Hot-swapping Vosk recognizer grammar with ${phrases.size} phrases in memory (<15ms)...")
                val newRec = try {
                    Recognizer(mdl, 16000.0f, grammarJson)
                } catch (e: Exception) {
                    Log.w(tag, "Dynamic grammar init fallback: ${e.message}")
                    Recognizer(mdl, 16000.0f)
                }
                val oldRec = recognizer
                recognizer = newRec
                lastAppliedPhrases = phrases
                try {
                    oldRec?.close()
                } catch (e: Exception) {
                    Log.w(tag, "Error closing previous recognizer: ${e.message}")
                }
                Log.i(tag, "Vosk recognizer grammar hot-swapped successfully on-device!")
            } catch (e: Exception) {
                Log.e(tag, "Error updating Vosk grammar dynamically", e)
            }
        }
    }

    private val turnParts = mutableListOf<String>()

    fun onTurnStart(preRollBytes: ByteArray) {
        if (!isReady.get() || !isEnabled.get()) return
        val copy = preRollBytes.clone()
        executor.execute {
            val rec = recognizer ?: return@execute
            try {
                turnParts.clear()
                modalityAnalyzer.reset()
                rec.reset()
                if (copy.isNotEmpty()) {
                    modalityAnalyzer.acceptPcmBytes(copy)
                    rec.acceptWaveForm(copy, copy.size)
                }
            } catch (e: Exception) {
                Log.w(tag, "Error in onTurnStart", e)
            }
        }
    }

    fun onTurnChunk(chunkBytes: ByteArray) {
        if (!isReady.get() || !isEnabled.get() || chunkBytes.isEmpty()) return
        val copy = chunkBytes.clone()
        executor.execute {
            val rec = recognizer ?: return@execute
            try {
                modalityAnalyzer.acceptPcmBytes(copy)
                val isEndpoint = rec.acceptWaveForm(copy, copy.size)
                if (isEndpoint) {
                    val text = cleanTranscript(parseVoskJson(rec.result, "text"))
                    if (text.isNotBlank()) {
                        turnParts.add(text)
                        val combined = turnParts.joinToString(" ")
                        Log.i(tag, "Mid-turn endpoint STT: $text (Accumulated: $combined)")
                        mainHandler.post {
                            onTranscriptResult(combined, false, null)
                        }
                    }
                } else {
                    val partial = cleanTranscript(parseVoskJson(rec.partialResult, "partial"))
                    val combinedPartial = (turnParts + listOf(partial)).filter { it.isNotBlank() }.joinToString(" ")
                    if (combinedPartial.isNotBlank()) {
                        mainHandler.post {
                            onTranscriptResult(combinedPartial, false, null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error in onTurnChunk", e)
            }
        }
    }

    fun onTurnEnd(onFinalized: ((String, VoiceModalityProfile?) -> Unit)? = null) {
        if (!isReady.get() || !isEnabled.get()) {
            onFinalized?.invoke("", null)
            return
        }
        executor.execute {
            val rec = recognizer
            if (rec == null) {
                onFinalized?.invoke("", null)
                return@execute
            }
            try {
                val finalJson = rec.finalResult
                val tailText = cleanTranscript(parseVoskJson(finalJson, "text"))
                if (tailText.isNotBlank()) {
                    turnParts.add(tailText)
                }
                val fullTurnText = turnParts.joinToString(" ").trim()
                val modality = modalityAnalyzer.buildProfile()
                turnParts.clear()
                rec.reset()
                if (fullTurnText.isNotBlank()) {
                    Log.i(tag, "Turn Final Complete STT: $fullTurnText (${modality.shortSummary()})")
                    mainHandler.post {
                        onTranscriptResult(fullTurnText, true, modality)
                    }
                }
                onFinalized?.invoke(fullTurnText, modality)
            } catch (e: Exception) {
                Log.w(tag, "Error in onTurnEnd", e)
                onFinalized?.invoke("", null)
            }
        }
    }

    private fun cleanTranscript(raw: String): String {
        return raw.replace("[unk]", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseVoskJson(jsonStr: String?, key: String): String {
        if (jsonStr.isNullOrBlank()) return ""
        return try {
            JSONObject(jsonStr).optString(key, "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractZipFromAssets(assetName: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        context.assets.open(assetName).use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(16384)
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    fun shutdown() {
        isReady.set(false)
        executor.execute {
            try {
                recognizer?.close()
                model?.close()
            } catch (e: Exception) {
                // Ignore on shutdown
            }
        }
        executor.shutdown()
    }
}
