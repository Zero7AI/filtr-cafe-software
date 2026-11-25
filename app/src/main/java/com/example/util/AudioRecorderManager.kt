package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderManager(private val context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Suppress("DEPRECATION")
    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { }

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var recordingStartTimestamp: Long = 0L
        private set

    @Volatile
    var lastRecordingDurationMs: Long = 0L
        private set

    @Synchronized
    fun startRecording(): File? {
        if (isRecording || recorder != null || currentFile != null) {
            cancelAndRelease()
        }

        var outputFile: File? = null
        var mediaRecorder: MediaRecorder? = null
        return try {
            requestAudioFocus()
            outputFile = File.createTempFile("voice_order_", ".m4a", appContext.cacheDir)
            currentFile = outputFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder = mediaRecorder
            mediaRecorder.apply {
                // Hardware DSP noise suppression, speech beamforming & echo cancellation
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1) // Mono speech channel avoids stereo phasing issues
                setOutputFile(outputFile.absolutePath)
                setAudioSamplingRate(16000) // 16 kHz is standard for speech models
                setAudioEncodingBitRate(64000)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTimestamp = System.currentTimeMillis()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            releaseRecorderSafely(mediaRecorder ?: recorder)
            recorder = null
            isRecording = false
            recordingStartTimestamp = 0L
            runCatching { outputFile?.delete() }
            runCatching { currentFile?.delete() }
            currentFile = null
            abandonAudioFocus()
            null
        }
    }

    @Synchronized
    fun stopRecording(): File? {
        val activeRecorder = recorder
        val outputFile = currentFile
        if (!isRecording || activeRecorder == null) {
            cancelAndRelease()
            return null
        }

        var stoppedCleanly = false
        return try {
            lastRecordingDurationMs = (System.currentTimeMillis() - recordingStartTimestamp).coerceAtLeast(0L)
            activeRecorder.stop()
            stoppedCleanly = true
            if (outputFile != null && outputFile.exists() && outputFile.length() > 0L) {
                currentFile = null
                outputFile
            } else {
                runCatching { outputFile?.delete() }
                currentFile = null
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastRecordingDurationMs = 0L
            runCatching { outputFile?.delete() }
            currentFile = null
            null
        } finally {
            if (!stoppedCleanly) {
                runCatching { activeRecorder.reset() }
            }
            runCatching { activeRecorder.release() }
            recorder = null
            isRecording = false
            recordingStartTimestamp = 0L
            abandonAudioFocus()
        }
    }

    @Synchronized
    fun cancelRecording() {
        cancelAndRelease()
    }

    @Synchronized
    fun cancelAndRelease() {
        val activeRecorder = recorder
        val outputFile = currentFile
        recorder = null
        currentFile = null
        isRecording = false
        recordingStartTimestamp = 0L
        lastRecordingDurationMs = 0L

        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            releaseRecorderSafely(activeRecorder)
        }
        runCatching { outputFile?.delete() }
        abandonAudioFocus()
    }

    private fun releaseRecorderSafely(mediaRecorder: MediaRecorder?) {
        if (mediaRecorder == null) return
        runCatching { mediaRecorder.reset() }
        runCatching { mediaRecorder.release() }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(legacyFocusListener)
                .build()
            audioFocusRequest = req
            runCatching { am.requestAudioFocus(req) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                am.requestAudioFocus(
                    legacyFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req ->
                runCatching { am.abandonAudioFocusRequest(req) }
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(legacyFocusListener) }
        }
    }
}
