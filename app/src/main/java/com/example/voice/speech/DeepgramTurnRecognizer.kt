package com.example.voice.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.entity.MenuItemEntity
import com.example.data.remote.DeepgramVoiceService
import com.example.voice.audio.SpeakerModalityAnalyzer
import com.example.voice.audio.VoiceModalityProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VAD-driven Speech Recognizer backed by Deepgram Nova-3 REST API.
 * Collects clean 16kHz PCM16 speech turns isolated by Silero VAD v5 and
 * transcribes each completed utterance with menu keyterm boosting.
 */
class DeepgramTurnRecognizer(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val onTranscriptResult: (text: String, isFinal: Boolean, modality: VoiceModalityProfile?) -> Unit,
    private val onReadyStateChanged: (ready: Boolean, statusMsg: String) -> Unit
) {
    private val tag = "DeepgramTurnRecognizer"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modalityAnalyzer = SpeakerModalityAnalyzer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pcmBuffer = ByteArrayOutputStream()
    private val bufferLock = Any()

    @Volatile
    private var activeMenuItems: List<MenuItemEntity> = emptyList()

    val isReady = AtomicBoolean(true)
    val isEnabled = AtomicBoolean(true)

    init {
        mainHandler.post {
            onReadyStateChanged(true, "Deepgram Voice AI Ready")
        }
    }

    fun updateMenuContext(items: List<MenuItemEntity>) {
        activeMenuItems = items
    }

    fun updateGrammar(@Suppress("UNUSED_PARAMETER") phrases: List<String>) {
        // Keyterms are dynamically pulled from activeMenuItems + MenuCatalog during transcription.
    }

    fun onTurnStart(preRollBytes: ByteArray) {
        if (!isReady.get() || !isEnabled.get()) return
        synchronized(bufferLock) {
            pcmBuffer.reset()
            modalityAnalyzer.reset()
            if (preRollBytes.isNotEmpty()) {
                modalityAnalyzer.acceptPcmBytes(preRollBytes)
                pcmBuffer.write(preRollBytes)
            }
        }
        mainHandler.post {
            onTranscriptResult("Listening...", false, null)
        }
    }

    fun onTurnChunk(chunkBytes: ByteArray) {
        if (!isReady.get() || !isEnabled.get() || chunkBytes.isEmpty()) return
        synchronized(bufferLock) {
            // Cap single turn buffer at ~25 seconds (16000 * 2 bytes/sec * 25 = 800KB)
            if (pcmBuffer.size() < 800_000) {
                modalityAnalyzer.acceptPcmBytes(chunkBytes)
                pcmBuffer.write(chunkBytes)
            }
        }
    }

    fun onTurnEnd(onFinalized: ((String, VoiceModalityProfile?) -> Unit)? = null) {
        if (!isReady.get() || !isEnabled.get()) {
            onFinalized?.invoke("", null)
            return
        }

        val pcmData: ByteArray
        val modality: VoiceModalityProfile
        synchronized(bufferLock) {
            pcmData = pcmBuffer.toByteArray()
            modality = modalityAnalyzer.buildProfile()
            pcmBuffer.reset()
            modalityAnalyzer.reset()
        }

        // Ignore ultra-short clicks (< 0.25s = 8000 bytes at 16kHz mono 16-bit)
        if (pcmData.size < 8000) {
            mainHandler.post { onTranscriptResult("", false, null) }
            onFinalized?.invoke("", modality)
            return
        }

        mainHandler.post {
            onTranscriptResult("Transcribing with Deepgram...", false, null)
        }

        val wavBytes = wrapPcm16AsWav(pcmData, sampleRate = 16000, channels = 1)
        scope.launch {
            val result = DeepgramVoiceService.getInstance().transcribeAudio(
                audioBytes = wavBytes,
                mimeType = "audio/wav",
                menuItems = activeMenuItems
            )
            val transcript = result.getOrNull()?.transcript?.trim().orEmpty()
            if (transcript.isNotBlank()) {
                Log.i(tag, "Deepgram Turn Complete: \"$transcript\" (${modality.shortSummary()})")
                mainHandler.post {
                    onTranscriptResult(transcript, true, modality)
                }
            } else {
                Log.w(tag, "Deepgram Turn empty/failed: ${result.exceptionOrNull()?.message}")
                mainHandler.post {
                    onTranscriptResult("", false, null)
                }
            }
            onFinalized?.invoke(transcript, modality)
        }
    }

    private fun wrapPcm16AsWav(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val totalDataLen = pcm.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1.toShort()) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 2).toShort()) // block align
            putShort(16.toShort()) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()

        return ByteArray(44 + pcm.size).apply {
            System.arraycopy(header, 0, this, 0, 44)
            System.arraycopy(pcm, 0, this, 44, pcm.size)
        }
    }

    fun shutdown() {
        isReady.set(false)
        scope.cancel()
    }
}
