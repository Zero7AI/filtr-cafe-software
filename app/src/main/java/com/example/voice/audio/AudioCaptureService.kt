package com.example.voice.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voice.speech.DeepgramTurnRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureService : Service(), VoiceActivityDetector.VadListener {

    private val binder = LocalBinder()
    private val isRecording = AtomicBoolean(false)
    private val vadWorkerRunning = AtomicBoolean(false)
    private val captureLock = Any()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var vadWorkerThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val frameQueue = ArrayBlockingQueue<ShortArray>(FRAME_QUEUE_CAPACITY)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private lateinit var vad: VoiceActivityDetector
    lateinit var sileroVad: SileroVadDetector
    lateinit var turnRecognizer: DeepgramTurnRecognizer

    var audioLevelCallback: ((Double) -> Unit)? = null
    var speechStateCallback: ((Boolean) -> Unit)? = null
    var transcriptCallback: ((String, Boolean, VoiceModalityProfile?) -> Unit)? = null
    var statusCallback: ((Boolean, String) -> Unit)? = null

    @Suppress("DEPRECATION")
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mainHandler.post {
                    if (isRecording.get()) {
                        pauseListening()
                        statusCallback?.invoke(false, "Audio focus lost — voice paused")
                    }
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FILTR:VoiceAudioWakeLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {
            wakeLock = null
        }

        sileroVad = SileroVadDetector(this)
        vad = VoiceActivityDetector(sileroVad = sileroVad, listener = this)
        turnRecognizer = DeepgramTurnRecognizer(
            onTranscriptResult = { text, isFinal, modality ->
                transcriptCallback?.invoke(text, isFinal, modality)
            },
            onReadyStateChanged = { ready, msg ->
                statusCallback?.invoke(ready, msg)
            }
        )

        // Load Silero ONNX off the Main thread
        serviceScope.launch {
            sileroVad.initialize()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopCaptureAndService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_LISTENING -> {
                pauseListening()
                return START_NOT_STICKY
            }
        }

        if (!hasRecordAudioPermission()) {
            mainHandler.post {
                statusCallback?.invoke(false, "Microphone permission required")
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startForegroundSafe()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (turnRecognizer.isEnabled.get()) {
            startAudioCapture()
        }

        return START_NOT_STICKY
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundSafe(): Boolean {
        return try {
            val notification = createNotification(isPaused = !turnRecognizer.isEnabled.get())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "Cannot start microphone foreground service without permission", se)
            mainHandler.post {
                statusCallback?.invoke(false, "Microphone permission denied")
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground state: ${e.message}", e)
            mainHandler.post {
                statusCallback?.invoke(false, "Could not start voice service")
            }
            false
        }
    }

    fun pauseListening() {
        turnRecognizer.isEnabled.set(false)
        stopAudioCapture()
        mainHandler.post {
            speechStateCallback?.invoke(false)
            audioLevelCallback?.invoke(0.0)
        }
        updateNotificationSafe(isPaused = true)
    }

    fun resumeListening() {
        if (!hasRecordAudioPermission()) {
            mainHandler.post {
                statusCallback?.invoke(false, "Microphone permission required")
            }
            return
        }
        if (!startForegroundSafe()) {
            stopSelf()
            return
        }
        turnRecognizer.isEnabled.set(true)
        startAudioCapture()
        updateNotificationSafe(isPaused = false)
    }

    fun stopCaptureAndService() {
        turnRecognizer.isEnabled.set(false)
        stopAudioCapture()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    fun startAudioCapture() {
        synchronized(captureLock) {
            if (!hasRecordAudioPermission()) {
                mainHandler.post {
                    statusCallback?.invoke(false, "Microphone permission required")
                }
                return
            }
            if (!isRecording.compareAndSet(false, true)) return

            val sampleRate = SAMPLE_RATE
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize <= 0) {
                isRecording.set(false)
                mainHandler.post {
                    statusCallback?.invoke(false, "Audio hardware unavailable ($minBufferSize)")
                }
                return
            }

            if (!requestAudioFocus()) {
                isRecording.set(false)
                mainHandler.post {
                    statusCallback?.invoke(false, "Microphone busy (audio focus denied)")
                }
                return
            }

            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
            var localRecord: AudioRecord? = null

            try {
                localRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (localRecord.state != AudioRecord.STATE_INITIALIZED) {
                    runCatching { localRecord.release() }
                    isRecording.set(false)
                    abandonAudioFocus()
                    mainHandler.post {
                        statusCallback?.invoke(false, "Microphone initialization failed")
                    }
                    return
                }

                audioRecord = localRecord
                localRecord.startRecording()

                if (localRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    runCatching { localRecord.stop() }
                    runCatching { localRecord.release() }
                    audioRecord = null
                    isRecording.set(false)
                    abandonAudioFocus()
                    mainHandler.post {
                        statusCallback?.invoke(false, "Microphone failed to start recording")
                    }
                    return
                }

                try {
                    wakeLock?.acquire(MAX_WAKELOCK_MS)
                } catch (_: Exception) {
                    // Ignore if wake lock denied
                }

                frameQueue.clear()
                vadWorkerRunning.set(true)
                vadWorkerThread = Thread({
                    sileroVad.initialize()
                    while (vadWorkerRunning.get()) {
                        val chunk = try {
                            frameQueue.poll(100, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            break
                        }
                        if (chunk != null) {
                            vad.processFrame(chunk, chunk.size)
                        }
                    }
                }, "FILTRVadWorkerThread").apply { start() }

                recordingThread = Thread({
                    val frameBuffer = ShortArray(CAPTURE_CHUNK_SAMPLES)
                    while (isRecording.get()) {
                        val activeRecord = audioRecord ?: break
                        val read = activeRecord.read(frameBuffer, 0, frameBuffer.size)
                        if (read > 0) {
                            val copy = frameBuffer.copyOf(read)
                            if (!frameQueue.offer(copy)) {
                                // Bounded backpressure: drop oldest frame rather than stalling AudioRecord.read()
                                frameQueue.poll()
                                frameQueue.offer(copy)
                            }
                        } else if (read == 0) {
                            Thread.yield()
                        } else {
                            Log.e(TAG, "AudioRecord.read failed with code $read")
                            isRecording.set(false)
                            mainHandler.post {
                                statusCallback?.invoke(false, "Microphone read error ($read)")
                                stopAudioCapture()
                            }
                            break
                        }
                    }
                }, "FILTRAudioRecordThread").apply { start() }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException starting AudioRecord", e)
                cleanupFailedStart(localRecord)
                mainHandler.post {
                    statusCallback?.invoke(false, "Microphone permission denied")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting AudioRecord: ${e.message}", e)
                cleanupFailedStart(localRecord)
                mainHandler.post {
                    statusCallback?.invoke(false, "Microphone start error: ${e.message ?: "unknown"}")
                }
            }
        }
    }

    private fun cleanupFailedStart(localRecord: AudioRecord?) {
        isRecording.set(false)
        vadWorkerRunning.set(false)
        val recordToRelease = audioRecord ?: localRecord
        audioRecord = null
        if (recordToRelease != null) {
            runCatching { recordToRelease.stop() }
            runCatching { recordToRelease.release() }
        }
        frameQueue.clear()
        abandonAudioFocus()
        releaseWakeLock()
    }

    fun stopAudioCapture() {
        synchronized(captureLock) {
            isRecording.set(false)
            vadWorkerRunning.set(false)

            val recThread = recordingThread
            recordingThread = null
            val record = audioRecord
            audioRecord = null
            val vadThread = vadWorkerThread
            vadWorkerThread = null

            try {
                if (recThread != null && recThread !== Thread.currentThread()) {
                    recThread.interrupt()
                    runCatching { recThread.join(1000) }
                }
            } finally {
                if (record != null) {
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (_: Exception) {
                        // Ignore stop errors on shutdown
                    } finally {
                        runCatching { record.release() }
                    }
                }
                if (vadThread != null && vadThread !== Thread.currentThread()) {
                    vadThread.interrupt()
                    runCatching { vadThread.join(1000) }
                }
                frameQueue.clear()
                vad.reset()
                abandonAudioFocus()
                releaseWakeLock()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = req
            runCatching { am.requestAudioFocus(req) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
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
            runCatching { am.abandonAudioFocus(audioFocusListener) }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    override fun onSpeechStart(preRollAudio: ByteArray) {
        speechStateCallback?.invoke(true)
        turnRecognizer.onTurnStart(preRollAudio)
    }

    override fun onSpeechChunk(chunk: ByteArray) {
        turnRecognizer.onTurnChunk(chunk)
    }

    override fun onSpeechEnd() {
        speechStateCallback?.invoke(false)
        turnRecognizer.onTurnEnd { _, _ -> }
    }

    override fun onAudioLevel(rms: Double) {
        audioLevelCallback?.invoke(rms)
    }

    override fun onDestroy() {
        audioLevelCallback = null
        speechStateCallback = null
        transcriptCallback = null
        statusCallback = null
        stopAudioCapture()
        if (::turnRecognizer.isInitialized) {
            turnRecognizer.shutdown()
        }
        serviceScope.cancel()
        if (::sileroVad.isInitialized) {
            sileroVad.close()
        }
        super.onDestroy()
    }

    private fun createNotification(isPaused: Boolean): Notification {
        val text = if (isPaused) "Voice order listening paused" else "Voice order listening active"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FILTR Voice Ordering")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotificationSafe(isPaused: Boolean) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, createNotification(isPaused))
        }
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
        private const val TAG = "AudioCaptureService"
        private const val SAMPLE_RATE = 16000
        private const val CAPTURE_CHUNK_SAMPLES = SileroVadDetector.WINDOW_SIZE // 512 samples (32ms)
        private const val FRAME_QUEUE_CAPACITY = 64
        private const val MAX_WAKELOCK_MS = 4 * 60 * 60 * 1000L // Safety timeout; released on pause/stop
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "filtr_counter_voice_channel"

        const val ACTION_START_LISTENING = "com.example.voice.ACTION_START_LISTENING"
        const val ACTION_PAUSE_LISTENING = "com.example.voice.ACTION_PAUSE_LISTENING"
        const val ACTION_STOP_SERVICE = "com.example.voice.ACTION_STOP_SERVICE"
    }
}
