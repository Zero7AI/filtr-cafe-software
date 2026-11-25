package com.example.voice.audio

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Robust Adaptive Voice Activity Detector engineered for noisy commercial environments (cafes, restaurants).
 *
 * Key capabilities:
 * 1. Exact 512-sample (32ms @ 16kHz) windowing for continuous Silero VAD v5 LSTM state evaluation.
 * 2. Dynamic Noise Floor Tracking via EMA across ambient cafe noise levels.
 * 3. Adaptive Energy Thresholds scaled from real-time noise floor.
 * 4. Hard Max-Turn Duration (7.0s) so continuous ambient sound cannot stall turn commits.
 * 5. Pre-Roll Circular Buffer (750ms) preserving soft word onsets without clipping.
 */
class VoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private val preRollMs: Int = 750,
    private val silenceHangoverMs: Int = 650,
    private val maxTurnDurationMs: Long = 7000L,
    private val sileroVad: SileroVadDetector? = null,
    private val listener: VadListener
) {
    companion object {
        private const val VAD_FRAME_SAMPLES = SileroVadDetector.WINDOW_SIZE // 512 samples = 32ms @ 16kHz
    }

    interface VadListener {
        fun onSpeechStart(preRollAudio: ByteArray)
        fun onSpeechChunk(chunk: ByteArray)
        fun onSpeechEnd()
        fun onAudioLevel(rms: Double)
    }

    private val safeSampleRate = sampleRate.coerceAtLeast(1)
    private val frameDurationMs = ((VAD_FRAME_SAMPLES * 1000) / safeSampleRate).coerceAtLeast(1)
    private val framesPerHangover = (silenceHangoverMs / frameDurationMs).coerceAtLeast(1)
    private val preRollBytesLimit = (safeSampleRate * 2 * preRollMs) / 1000

    private val preRollQueue = ArrayDeque<ByteArray>()
    private var currentPreRollBytes = 0

    // Accumulator ensuring Silero always receives exact 512-sample windows without dropping trailing samples
    private val pendingFrame = ShortArray(VAD_FRAME_SAMPLES)
    private var pendingSamplesCount = 0

    private var isSpeechActive = false
    private var silenceFramesCount = 0
    private var speechTurnStartTimeMs: Long = 0L

    // Adaptive noise floor tracking (initialized to a typical baseline, auto-calibrates immediately)
    var currentNoiseFloor: Double = 250.0
        private set
    private var frameCount: Long = 0L

    fun processFrame(pcmBuffer: ShortArray, readSize: Int) {
        if (readSize <= 0 || sampleRate <= 0) return
        val usableSize = minOf(readSize, pcmBuffer.size)
        var offset = 0

        while (offset < usableSize) {
            val needed = VAD_FRAME_SAMPLES - pendingSamplesCount
            val toCopy = minOf(needed, usableSize - offset)
            System.arraycopy(pcmBuffer, offset, pendingFrame, pendingSamplesCount, toCopy)
            pendingSamplesCount += toCopy
            offset += toCopy

            if (pendingSamplesCount == VAD_FRAME_SAMPLES) {
                processExactFrame(pendingFrame)
                pendingSamplesCount = 0
            }
        }
    }

    private fun processExactFrame(frame: ShortArray) {
        // 1. Calculate RMS energy
        var sumSquares = 0.0
        for (i in 0 until VAD_FRAME_SAMPLES) {
            val sample = frame[i].toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / VAD_FRAME_SAMPLES)
        listener.onAudioLevel(rms)

        // Encode 512-sample PCM16LE bytes without per-frame ByteBuffer allocation
        val frameBytes = ByteArray(VAD_FRAME_SAMPLES * 2)
        for (i in 0 until VAD_FRAME_SAMPLES) {
            val s = frame[i].toInt()
            frameBytes[i * 2] = (s and 0xFF).toByte()
            frameBytes[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }

        // 2. Evaluate Silero VAD continuously across EVERY 512-sample frame so LSTM state stays fresh
        val sileroReady = sileroVad != null && sileroVad.isReady()
        val speechProb = if (sileroReady) {
            sileroVad!!.computeProbabilityFromShorts(frame, 0, VAD_FRAME_SAMPLES)
        } else {
            1.0f
        }

        val now = System.currentTimeMillis()
        frameCount++

        // 3. Update Adaptive Noise Floor
        if (frameCount <= 15 && !isSpeechActive) {
            currentNoiseFloor = (currentNoiseFloor * 0.70) + (rms * 0.30)
            currentNoiseFloor = currentNoiseFloor.coerceIn(80.0, 3500.0)
        } else if (!isSpeechActive) {
            currentNoiseFloor = (currentNoiseFloor * 0.95) + (rms * 0.05)
            currentNoiseFloor = currentNoiseFloor.coerceIn(80.0, 3500.0)
        } else if (rms < currentNoiseFloor * 1.1 && (!sileroReady || speechProb < 0.25f)) {
            currentNoiseFloor = (currentNoiseFloor * 0.98) + (rms * 0.02)
            currentNoiseFloor = currentNoiseFloor.coerceIn(80.0, 3500.0)
        }

        // 4. Dynamic Thresholds scaled from real-time ambient noise floor
        val speechOnsetThreshold = max(currentNoiseFloor + 400.0, currentNoiseFloor * 1.70)
        val silenceOffsetThreshold = max(currentNoiseFloor + 150.0, currentNoiseFloor * 1.25)

        val isOnsetSpeech = rms > speechOnsetThreshold && (!sileroReady || speechProb >= 0.28f)
        val isActiveContinuation = if (sileroReady) {
            (rms > silenceOffsetThreshold && speechProb >= 0.22f) || speechProb >= 0.45f
        } else {
            rms > silenceOffsetThreshold
        }

        // 5. Hard Max Turn Duration Check (prevents infinite lock if background noise persists)
        if (isSpeechActive && (now - speechTurnStartTimeMs) >= maxTurnDurationMs) {
            isSpeechActive = false
            silenceFramesCount = 0
            listener.onSpeechEnd()

            if (isOnsetSpeech) {
                isSpeechActive = true
                speechTurnStartTimeMs = now
                listener.onSpeechStart(ByteArray(0))
                listener.onSpeechChunk(frameBytes)
            } else {
                appendPreRoll(frameBytes)
            }
            return
        }

        // 6. VAD State Machine
        if (isOnsetSpeech) {
            silenceFramesCount = 0

            if (!isSpeechActive) {
                isSpeechActive = true
                speechTurnStartTimeMs = now

                val preRollCombined = drainPreRoll()
                listener.onSpeechStart(preRollCombined)
            }

            listener.onSpeechChunk(frameBytes)
        } else if (isSpeechActive) {
            if (!isActiveContinuation) {
                silenceFramesCount++
            } else {
                silenceFramesCount = max(0, silenceFramesCount - 1)
            }

            if (silenceFramesCount >= framesPerHangover) {
                isSpeechActive = false
                silenceFramesCount = 0
                listener.onSpeechEnd()
                appendPreRoll(frameBytes)
            } else {
                // Within silence hangover or active voiced continuation: stream frame
                listener.onSpeechChunk(frameBytes)
            }
        } else {
            appendPreRoll(frameBytes)
        }
    }

    private fun appendPreRoll(frameBytes: ByteArray) {
        preRollQueue.addLast(frameBytes)
        currentPreRollBytes += frameBytes.size
        while (currentPreRollBytes > preRollBytesLimit && preRollQueue.isNotEmpty()) {
            val removed = preRollQueue.removeFirst()
            currentPreRollBytes -= removed.size
        }
    }

    private fun drainPreRoll(): ByteArray {
        val preRollCombined = ByteArray(currentPreRollBytes)
        var offset = 0
        for (chunk in preRollQueue) {
            System.arraycopy(chunk, 0, preRollCombined, offset, chunk.size)
            offset += chunk.size
        }
        preRollQueue.clear()
        currentPreRollBytes = 0
        return preRollCombined
    }

    fun reset() {
        pendingSamplesCount = 0
        isSpeechActive = false
        silenceFramesCount = 0
        preRollQueue.clear()
        currentPreRollBytes = 0
        speechTurnStartTimeMs = 0L
        sileroVad?.reset()
    }
}
