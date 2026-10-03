package com.example.voice.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.voice.speech.InProcessVoskRecognizer
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureService : Service(), VoiceActivityDetector.VadListener {

    private val binder = LocalBinder()
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var vad: VoiceActivityDetector
    lateinit var sileroVad: SileroVadDetector
    lateinit var voskRecognizer: InProcessVoskRecognizer

    var audioLevelCallback: ((Double) -> Unit)? = null
    var speechStateCallback: ((Boolean) -> Unit)? = null
    var transcriptCallback: ((String, Boolean, VoiceModalityProfile?) -> Unit)? = null
    var statusCallback: ((Boolean, String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FILTR:VoiceAudioWakeLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            wakeLock = null
        }

        sileroVad = SileroVadDetector(this)
        vad = VoiceActivityDetector(sileroVad = sileroVad, listener = this)
        voskRecognizer = InProcessVoskRecognizer(
            context = this,
            onTranscriptResult = { text, isFinal, modality ->
                transcriptCallback?.invoke(text, isFinal, modality)
            },
            onReadyStateChanged = { ready, msg ->
                statusCallback?.invoke(ready, msg)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            // Ignore if wake lock denied
        }
        startAudioCapture()

        return START_STICKY
    }

    fun startAudioCapture() {
        if (isRecording.getAndSet(true)) return

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()

            recordingThread = Thread({
                val frameBuffer = ShortArray(1024)
                while (isRecording.get()) {
                    val read = audioRecord?.read(frameBuffer, 0, frameBuffer.size) ?: -1
                    if (read > 0) {
                        vad.processFrame(frameBuffer, read)
                    }
                }
            }, "FILTRAudioRecordThread").apply { start() }

        } catch (e: SecurityException) {
            isRecording.set(false)
        } catch (e: Exception) {
            isRecording.set(false)
        }
    }

    fun stopAudioCapture() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(500)
            recordingThread = null
        } catch (e: Exception) {
            // Ignore on shutdown
        }
        vad.reset()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onSpeechStart(preRollAudio: ByteArray) {
        speechStateCallback?.invoke(true)
        voskRecognizer.onTurnStart(preRollAudio)
    }

    override fun onSpeechChunk(chunk: ByteArray) {
        voskRecognizer.onTurnChunk(chunk)
    }

    override fun onSpeechEnd() {
        speechStateCallback?.invoke(false)
        voskRecognizer.onTurnEnd { _, _ -> }
    }

    override fun onAudioLevel(rms: Double) {
        audioLevelCallback?.invoke(rms)
    }

    override fun onDestroy() {
        stopAudioCapture()
        voskRecognizer.shutdown()
        if (::sileroVad.isInitialized) {
            sileroVad.close()
        }
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FILTR Voice Ordering")
            .setContentText("Offline voice order listening active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FILTR Voice Ordering Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "filtr_counter_voice_channel"
    }
}
