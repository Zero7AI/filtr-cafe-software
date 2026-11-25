package com.example.voice.speech

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.MenuItemEntity
import com.example.data.remote.DeepgramVoiceService
import com.example.voice.audio.SpeakerModalityAnalyzer
import com.example.voice.audio.VoiceModalityProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VAD-driven Speech Recognizer backed by Deepgram Nova-3 REST API.
 * Collects clean 16kHz PCM16 speech turns isolated by Silero VAD v5 and
 * transcribes each completed utterance sequentially with menu keyterm boosting.
 */
class DeepgramTurnRecognizer(
    private val onTranscriptResult: (text: String, isFinal: Boolean, modality: VoiceModalityProfile?) -> Unit,
    private val onReadyStateChanged: (ready: Boolean, statusMsg: String) -> Unit
) {
    private data class TurnJob(
        val wavBytes: ByteArray,
        val menuItems: List<MenuItemEntity>,
        val modality: VoiceModalityProfile,
        val onFinalized: ((String, VoiceModalityProfile?) -> Unit)?
    )

    companion object {
        private const val TAG = "DeepgramTurnRecognizer"
        private const val DEFAULT_TURN_BUFFER_BYTES = 32_768
        // Cap single turn buffer at ~25 seconds (16000 * 2 bytes/sec * 25 = 800KB)
        private const val MAX_TURN_BUFFER_BYTES = 800_000
        private const val MIN_TURN_BYTES = 8_000
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val modalityAnalyzer = SpeakerModalityAnalyzer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val turnChannel = Channel<TurnJob>(Channel.UNLIMITED)

    private var pcmBuffer = ByteArrayOutputStream(DEFAULT_TURN_BUFFER_BYTES)
    private val bufferLock = Any()

    @Volatile
    private var activeMenuItems: List<MenuItemEntity> = emptyList()

    val isReady = AtomicBoolean(true)
    val isEnabled = AtomicBoolean(true)

    init {
        mainHandler.post {
            onReadyStateChanged(true, "Deepgram Voice AI Ready")
        }

        // Single-consumer queue guarantees turns are transcribed and emitted in strict FIFO order.
        scope.launch {
            for (job in turnChannel) {
                if (!isReady.get()) break
                processTurnJob(job)
            }
        }
    }

    fun updateMenuContext(items: List<MenuItemEntity>) {
        activeMenuItems = items.toList()
    }

    fun onTurnStart(preRollBytes: ByteArray) {
        if (!isReady.get() || !isEnabled.get()) return
        synchronized(bufferLock) {
            pcmBuffer = ByteArrayOutputStream(DEFAULT_TURN_BUFFER_BYTES)
            modalityAnalyzer.reset()
            if (preRollBytes.isNotEmpty()) {
                val offset = (preRollBytes.size - MAX_TURN_BUFFER_BYTES).coerceAtLeast(0)
                val len = preRollBytes.size - offset
                if (len > 0) {
                    val slice = if (offset == 0) preRollBytes else preRollBytes.copyOfRange(offset, preRollBytes.size)
                    modalityAnalyzer.acceptPcmBytes(slice)
                    pcmBuffer.write(slice)
                }
            }
        }
        mainHandler.post {
            if (isReady.get()) {
                onTranscriptResult("Listening...", false, null)
            }
        }
    }

    fun onTurnChunk(chunkBytes: ByteArray) {
        if (!isReady.get() || !isEnabled.get() || chunkBytes.isEmpty()) return
        synchronized(bufferLock) {
            val remaining = MAX_TURN_BUFFER_BYTES - pcmBuffer.size()
            if (remaining > 0) {
                val writeLen = minOf(chunkBytes.size, remaining)
                val slice = if (writeLen == chunkBytes.size) chunkBytes else chunkBytes.copyOf(writeLen)
                modalityAnalyzer.acceptPcmBytes(slice)
                pcmBuffer.write(slice)
            }
        }
    }

    fun onTurnEnd(onFinalized: ((String, VoiceModalityProfile?) -> Unit)? = null) {
        if (!isReady.get() || !isEnabled.get()) {
            synchronized(bufferLock) {
                pcmBuffer = ByteArrayOutputStream(DEFAULT_TURN_BUFFER_BYTES)
                modalityAnalyzer.reset()
            }
            onFinalized?.invoke("", null)
            return
        }

        val pcmData: ByteArray
        val modality: VoiceModalityProfile
        synchronized(bufferLock) {
            pcmData = pcmBuffer.toByteArray()
            modality = modalityAnalyzer.buildProfile()
            // Recreate buffer so up to 800KB backing array is released immediately between turns
            pcmBuffer = ByteArrayOutputStream(DEFAULT_TURN_BUFFER_BYTES)
            modalityAnalyzer.reset()
        }

        // Ignore ultra-short clicks (< 0.25s = 8000 bytes at 16kHz mono 16-bit)
        if (pcmData.size < MIN_TURN_BYTES) {
            mainHandler.post {
                if (isReady.get()) onTranscriptResult("", false, null)
            }
            onFinalized?.invoke("", modality)
            return
        }

        mainHandler.post {
            if (isReady.get()) {
                onTranscriptResult("Transcribing with Deepgram...", false, null)
            }
        }

        val wavBytes = wrapPcm16AsWav(pcmData, sampleRate = 16000, channels = 1)
        val job = TurnJob(
            wavBytes = wavBytes,
            menuItems = activeMenuItems,
            modality = modality,
            onFinalized = onFinalized
        )
        val sendResult = turnChannel.trySend(job)
        if (sendResult.isFailure) {
            onFinalized?.invoke("", modality)
        }
    }

    private suspend fun processTurnJob(job: TurnJob) {
        try {
            val result = DeepgramVoiceService.getInstance().transcribeAudio(
                audioBytes = job.wavBytes,
                mimeType = "audio/wav",
                menuItems = job.menuItems
            )
            if (!isReady.get()) return

            if (result.isSuccess) {
                val transcript = result.getOrNull()?.transcript?.trim().orEmpty()
                if (transcript.isNotBlank()) {
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "Deepgram Turn Complete: \"$transcript\" (${job.modality.shortSummary()})")
                    }
                    mainHandler.post {
                        if (isReady.get()) {
                            onReadyStateChanged(true, "Deepgram Voice AI Ready")
                            onTranscriptResult(transcript, true, job.modality)
                        }
                    }
                } else {
                    mainHandler.post {
                        if (isReady.get()) {
                            onReadyStateChanged(true, "No speech detected")
                            onTranscriptResult("", false, null)
                        }
                    }
                }
                job.onFinalized?.invoke(transcript, job.modality)
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Transcription failed"
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Deepgram Turn failed: $errMsg")
                } else {
                    Log.w(TAG, "Deepgram Turn failed")
                }
                mainHandler.post {
                    if (isReady.get()) {
                        onReadyStateChanged(true, "Voice error: $errMsg")
                        onTranscriptResult("", false, null)
                    }
                }
                job.onFinalized?.invoke("", job.modality)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errMsg = e.message ?: "Voice processing failed"
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Turn job error: $errMsg", e)
            }
            mainHandler.post {
                if (isReady.get()) {
                    onReadyStateChanged(true, "Voice error: $errMsg")
                    onTranscriptResult("", false, null)
                }
            }
            job.onFinalized?.invoke("", job.modality)
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
        turnChannel.close()
        scope.cancel()
        synchronized(bufferLock) {
            pcmBuffer = ByteArrayOutputStream(0)
            modalityAnalyzer.reset()
        }
    }
}
