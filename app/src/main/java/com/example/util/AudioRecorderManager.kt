package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    var isRecording: Boolean = false
        private set

    fun startRecording(): File? {
        return try {
            val outputFile = File.createTempFile("voice_order_", ".m4a", context.cacheDir)
            currentFile = outputFile

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                prepare()
                start()
            }
            recorder = mediaRecorder
            isRecording = true
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            recorder?.release()
            recorder = null
            isRecording = false
            null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return currentFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            currentFile
        } catch (e: Exception) {
            e.printStackTrace()
            recorder?.release()
            recorder = null
            isRecording = false
            currentFile
        }
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        }
        recorder = null
        isRecording = false
        currentFile?.delete()
        currentFile = null
    }
}
