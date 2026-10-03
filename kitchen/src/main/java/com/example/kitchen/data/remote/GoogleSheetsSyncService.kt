package com.example.kitchen.data.remote

import android.util.Log
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleSheetsSyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val TAG = "KitchenSheetsSync"

    suspend fun syncSingleOrder(webhookUrl: String, orderWithItems: OrderWithItems): Result<String> =
        withContext(Dispatchers.IO) {
            if (webhookUrl.isBlank()) return@withContext Result.failure(Exception("Webhook URL is empty"))
            try {
                val jsonPayload = buildOrderJsonObject(orderWithItems)
                val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val urlWithAction = if (webhookUrl.contains("?")) {
                    "$webhookUrl&action=syncOrder"
                } else {
                    "$webhookUrl?action=syncOrder"
                }

                val request = Request.Builder()
                    .url(urlWithAction)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                val responseBody = response.body?.string() ?: ""
                response.close()

                if (response.isSuccessful || code in 200..299) {
                    Result.success("Order synced successfully")
                } else {
                    Result.failure(Exception("HTTP $code: $responseBody"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncSingleOrder error: ${e.message}")
                Result.failure(e)
            }
        }

    private fun buildOrderJsonObject(orderWithItems: OrderWithItems): JSONObject {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val orderTs = orderWithItems.order.timestamp

        return JSONObject().apply {
            put("action", "syncOrder")
            put("orderId", orderWithItems.order.orderId)
            put("customerName", orderWithItems.order.customerName)
            put("contactNumber", orderWithItems.order.contactNumber.ifBlank { "N/A" })
            put("totalAmount", orderWithItems.order.totalAmount)
            put("notes", orderWithItems.order.notes)
            put("timestamp", if (orderTs > 0) sdf.format(Date(orderTs)) else sdf.format(Date()))
            put("isMade", if (orderWithItems.order.isMade) "YES" else "NO")
            put("isPaid", if (orderWithItems.order.isPaid) "YES" else "NO")
            put("paymentMode", orderWithItems.order.paymentMode)
            put("isDelivered", if (orderWithItems.order.isDelivered) "YES" else "NO")

            val itemsArr = JSONArray()
            for (item in orderWithItems.items) {
                itemsArr.put(JSONObject().apply {
                    put("itemName", item.itemName)
                    put("unitPrice", item.itemPrice)
                    put("quantity", item.quantity)
                    put("subtotal", item.subtotal)
                    put("completedQuantity", item.completedQuantity)
                })
            }
            put("items", itemsArr)
        }
    }
}
