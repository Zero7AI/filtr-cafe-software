package com.example.voice.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Extracts physical voice modality features directly from 16kHz 16-bit mono PCM speech turns
 * to distinguish between different speakers (e.g., Customer vs. Counter Staff repeat-back confirmation)
 * with zero extra neural model RAM.
 *
 * Features extracted per turn:
 * 1. Fundamental Frequency (F0 Pitch in Hz) via normalized autocorrelation over voiced frames (75..350 Hz)
 * 2. Zero-Crossing Rate (ZCR in Hz) capturing spectral centroid / vocal brightness
 * 3. High-to-Low Frequency Energy Ratio (spectral tilt / vocal tract timbre & mic proximity)
 * 4. Voiced RMS Energy (distinguishing near-field staff ~0.4m vs. far-field customer ~1.2m)
 */
data class VoiceModalityProfile(
    val pitchHz: Double,
    val zcrHz: Double,
    val spectralTilt: Double,
    val voicedRms: Double,
    val voicedFramesCount: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isValid: Boolean
        get() = voicedFramesCount >= 3 && pitchHz in 70.0..360.0

    /**
     * Computes a normalized acoustic divergence score [0.0 .. 1.0+] between two speech turns.
     * A score >= 0.18 indicates a distinct speaker or distinct counter position (Customer <-> Staff).
     */
    fun distanceTo(other: VoiceModalityProfile): Double {
        if (!this.isValid || !other.isValid) return 0.0

        // 1. Relative vocal cord pitch shift (F0)
        val pitchDiffRatio = abs(this.pitchHz - other.pitchHz) / max(this.pitchHz, other.pitchHz)

        // 2. Relative vocal timbre / spectral centroid shift
        val zcrDiffRatio = abs(this.zcrHz - other.zcrHz) / max(max(this.zcrHz, other.zcrHz), 100.0)
        val tiltDiff = abs(this.spectralTilt - other.spectralTilt)

        // 3. Proximity / loudness ratio (log RMS distance)
        val rmsRatio = abs(ln(max(this.voicedRms, 1.0) / max(other.voicedRms, 1.0)))

        // Weighted combination: Pitch F0 (50%), Proximity RMS (25%), Timbre ZCR+Tilt (25%)
        return (pitchDiffRatio * 0.50) + (min(rmsRatio, 1.0) * 0.25) + (zcrDiffRatio * 0.15) + (tiltDiff * 0.10)
    }

    fun isDifferentSpeakerFrom(other: VoiceModalityProfile?): Boolean {
        if (other == null || !other.isValid || !this.isValid) return false
        // Either overall modality distance >= 0.18, OR pitch shift >= 18 Hz, OR proximity RMS changed by > 1.45x
        val pitchDeltaHz = abs(this.pitchHz - other.pitchHz)
        val rmsFactor = max(this.voicedRms, other.voicedRms) / max(min(this.voicedRms, other.voicedRms), 1.0)
        return distanceTo(other) >= 0.18 || pitchDeltaHz >= 18.0 || rmsFactor >= 1.45
    }

    fun shortSummary(previous: VoiceModalityProfile? = null): String {
        if (!isValid) return "F0: --"
        val p = pitchHz.roundToInt()
        val r = voicedRms.roundToInt()
        return if (previous != null && previous.isValid) {
            val prevP = previous.pitchHz.roundToInt()
            val diffTag = if (isDifferentSpeakerFrom(previous)) "New Speaker" else "Same Speaker"
            "$diffTag ($prevP→${p}Hz, RMS $r)"
        } else {
            "Pitch ${p}Hz, RMS $r"
        }
    }
}

class SpeakerModalityAnalyzer(private val sampleRate: Int = 16000) {

    private val framePcm = ShortArray(512) // 32ms analysis window at 16kHz
    private var frameFill = 0

    private val framePitches = mutableListOf<Double>()
    private val frameZcrs = mutableListOf<Double>()
    private val frameTilts = mutableListOf<Double>()
    private val frameRmsList = mutableListOf<Double>()

    @Synchronized
    fun reset() {
        frameFill = 0
        framePitches.clear()
        frameZcrs.clear()
        frameTilts.clear()
        frameRmsList.clear()
    }

    @Synchronized
    fun acceptPcmBytes(bytes: ByteArray) {
        if (bytes.size < 2) return
        val shortBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        while (shortBuf.hasRemaining()) {
            framePcm[frameFill++] = shortBuf.get()
            if (frameFill == framePcm.size) {
                analyzeWindow(framePcm)
                frameFill = 0
            }
        }
    }

    @Synchronized
    fun buildProfile(): VoiceModalityProfile {
        if (frameRmsList.isEmpty()) {
            return VoiceModalityProfile(0.0, 0.0, 0.0, 0.0, 0)
        }
        val medianPitch = if (framePitches.isNotEmpty()) {
            val sorted = framePitches.sorted()
            sorted[sorted.size / 2]
        } else {
            0.0
        }
        val avgZcr = frameZcrs.average()
        val avgTilt = frameTilts.average()
        val avgRms = frameRmsList.average()

        return VoiceModalityProfile(
            pitchHz = medianPitch,
            zcrHz = avgZcr,
            spectralTilt = avgTilt,
            voicedRms = avgRms,
            voicedFramesCount = framePitches.size
        )
    }

    private fun analyzeWindow(samples: ShortArray) {
        val n = samples.size
        var sumSq = 0.0
        var diffSq = 0.0
        var zeroCrossings = 0

        var prev = samples[0].toDouble()
        sumSq += prev * prev

        for (i in 1 until n) {
            val cur = samples[i].toDouble()
            sumSq += cur * cur
            val diff = cur - prev
            diffSq += diff * diff
            if ((cur >= 0 && prev < 0) || (cur < 0 && prev >= 0)) {
                zeroCrossings++
            }
            prev = cur
        }

        val rms = sqrt(sumSq / n)
        // Only analyze voiced speech frames above room noise floor
        if (rms < 240.0) return

        val zcrHz = (zeroCrossings.toDouble() * sampleRate) / (2.0 * n)
        val spectralTilt = (diffSq / max(sumSq * 2.0, 1.0)).coerceIn(0.0, 1.0)

        // Autocorrelation pitch estimation over human vocal range [75 Hz .. 340 Hz]
        // At 16kHz: lag 47 = 340 Hz, lag 213 = 75 Hz
        val minLag = 47
        val maxLag = 213
        var bestLag = -1
        var maxCorr = 0.0

        for (lag in minLag..maxLag) {
            var corr = 0.0
            var e1 = 0.0
            var e2 = 0.0
            val limit = n - lag
            var i = 0
            while (i < limit) {
                val s1 = samples[i].toDouble()
                val s2 = samples[i + lag].toDouble()
                corr += s1 * s2
                e1 += s1 * s1
                e2 += s2 * s2
                i += 2 // 2x decimation for fast real-time mobile DSP
            }
            val denom = sqrt(e1 * e2)
            if (denom > 1.0) {
                val normCorr = corr / denom
                if (normCorr > maxCorr) {
                    maxCorr = normCorr
                    bestLag = lag
                }
            }
        }

        // Voiced harmonic speech has strong periodicity (normalized autocorrelation >= 0.52)
        if (bestLag > 0 && maxCorr >= 0.52) {
            val f0 = sampleRate.toDouble() / bestLag.toDouble()
            framePitches.add(f0)
            frameZcrs.add(zcrHz)
            frameTilts.add(spectralTilt)
            frameRmsList.add(rms)
        }
    }
}
