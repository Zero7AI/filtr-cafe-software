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
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.calculatePacksAndCost

object InventoryNotificationHelper {

    private const val CHANNEL_ID = "filtr_inventory_alerts"
    private const val CHANNEL_NAME = "Inventory Reorder Alerts"
    private const val PREFS_NAME = "filtr_inventory_prefs"
    private const val KEY_LAST_ALERT_TIME = "last_low_stock_alert_time"
    private const val NOTIFICATION_ID = 1001

    // Throttle notifications to at most once per 6 hours unless critical
    private const val THROTTLE_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when ingredients have 7 or fewer orders remaining"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    data class LowStockItem(
        val item: InventoryItemEntity,
        val ordersRemaining: Int
    )

    fun checkAndNotifyLowStock(
        context: Context,
        items: List<InventoryItemEntity>,
        usages: List<InventoryUsageEntity>,
        force: Boolean = false
    ) {
        val lowStockItems = mutableListOf<LowStockItem>()

        for (item in items) {
            if (!item.isActive) continue
            val itemUsages = usages.filter { it.inventoryItemId == item.id }
            val ordersLeft: Int = if (itemUsages.isNotEmpty()) {
                val maxPacksPerOrder = itemUsages.maxOfOrNull { usage ->
                    val (packs, _) = calculatePacksAndCost(usage.quantityUsed, usage.unit, item)
                    packs
                } ?: 0.0

                if (maxPacksPerOrder > 0.0) {
                    (item.currentStock / maxPacksPerOrder).toInt()
                } else {
                    (item.currentStock * item.standardQuantity).toInt()
                }
            } else {
                (item.currentStock * (if (item.standardQuantity > 0) item.standardQuantity else 1.0)).toInt()
            }

            if (ordersLeft <= 7) {
                lowStockItems.add(LowStockItem(item, ordersLeft.coerceAtLeast(0)))
            }
        }

        if (lowStockItems.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAlert = prefs.getLong(KEY_LAST_ALERT_TIME, 0L)
        val now = System.currentTimeMillis()

        if (!force && (now - lastAlert) < THROTTLE_INTERVAL_MS) {
            return
        }

        val itemNames = lowStockItems.take(3).joinToString(", ") { "${it.item.name} (~${it.ordersRemaining} orders)" }
        val moreCount = lowStockItems.size - 3
        val body = if (moreCount > 0) {
            "Low stock on $itemNames and $moreCount more. Reorder soon!"
        } else {
            "Low stock on $itemNames. Reorder soon!"
        }

        initChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ FILTR Reorder Alert (≤ 7 Orders Left)")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            prefs.edit().putLong(KEY_LAST_ALERT_TIME, now).apply()
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
