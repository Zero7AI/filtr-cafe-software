package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.AppUpdateInfo

object AppUpdateNotificationHelper {

    private const val CHANNEL_ID = "filtr_app_updates"
    private const val CHANNEL_NAME = "App Updates"
    private const val NOTIFICATION_ID = 2001

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a new FILTR application update is available"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(context: Context, updateInfo: AppUpdateInfo) {
        initChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_UPDATE_DIALOG", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (updateInfo.isForceUpdate) {
            "Mandatory Update Required ⚠️ (v${updateInfo.versionName})"
        } else {
            "FILTR Update Available 🚀 (v${updateInfo.versionName})"
        }

        val body = updateInfo.forceUpdateMessage.ifBlank {
            "Version ${updateInfo.versionName} is ready. Tap to download and update."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(updateInfo.isForceUpdate)
            .setAutoCancel(!updateInfo.isForceUpdate)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted
        }
    }

    fun cancelUpdateNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
            // Ignore
        }
    }
}
