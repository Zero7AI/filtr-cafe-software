package com.example.kitchen.data.remote

import android.util.Log
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleSheetsSyncService(
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "KitchenSheetsSync"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private val HTTP_5XX_REGEX = Regex("""\b5\d{2}\b""")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private suspend fun <T> executeWithRetry(
        operationName: String,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                val result = block()
                if (result.isSuccess) return result

                val error = result.exceptionOrNull()
                if (error != null && isRetryableError(error) && attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                    Log.w(TAG, "$operationName attempt $attempt failed: ${error.message}. Retrying in ${delayMs}ms...")
                    delay(delayMs)
                    lastError = error
                    continue
                }
                return result
            } catch (e: Exception) {
                lastError = e
                if (isRetryableError(e) && attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                    Log.w(TAG, "$operationName attempt $attempt threw: ${e.message}. Retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(lastError ?: Exception("$operationName failed after $MAX_RETRIES attempts"))
    }

    private fun isRetryableError(error: Throwable): Boolean {
        return when {
            error is SocketTimeoutException -> true
            error is ConnectException -> true
            error is UnknownHostException -> true
            error is IOException -> true
            error.message?.contains("HTTP Error") == true &&
                HTTP_5XX_REGEX.containsMatchIn(error.message.orEmpty()) -> true
            else -> false
        }
    }

    suspend fun syncSingleOrder(webhookUrl: String, orderWithItems: OrderWithItems): Result<String> =
        withContext(ioDispatcher) {
            val trimmedUrl = webhookUrl.trim()
            if (trimmedUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Webhook URL is empty"))
            }

            executeWithRetry("syncOrder(${orderWithItems.order.orderId})") {
                try {
                    val jsonPayload = buildOrderJsonObject(orderWithItems).apply {
                        put("action", "syncOrder")
                    }
                    val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                    val urlWithAction = if (trimmedUrl.contains("?")) {
                        "$trimmedUrl&action=syncOrder"
                    } else {
                        "$trimmedUrl?action=syncOrder"
                    }

                    val request = Request.Builder()
                        .url(urlWithAction)
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        val code = response.code
                        val responseBody = response.body?.string() ?: ""

                        if (response.isSuccessful || code == 302 || code == 200) {
                            if (responseBody.trimStart().startsWith("{")) {
                                val parsed = try {
                                    JSONObject(responseBody)
                                } catch (_: Exception) {
                                    null
                                }
                                if (parsed != null && parsed.optString("result").equals("error", ignoreCase = true)) {
                                    return@use Result.failure(
                                        Exception(parsed.optString("message", "Google Sheets webhook error"))
                                    )
                                }
                            }
                            Result.success("Order synced successfully")
                        } else {
                            Result.failure(Exception("HTTP Error $code: $responseBody"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "syncSingleOrder error: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    internal fun buildOrderJsonObject(orderWithItems: OrderWithItems): JSONObject {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val orderTs = orderWithItems.order.timestamp

        return JSONObject().apply {
            put("action", "syncOrder")
            put("orderId", orderWithItems.order.orderId)
            put("customerName", orderWithItems.order.customerName)
            put("contactNumber", orderWithItems.order.contactNumber.ifBlank { "N/A" })
            put("totalAmount", orderWithItems.order.totalAmount)
            put("notes", orderWithItems.order.notes)

            val itemsArr = JSONArray()
            for (item in orderWithItems.items) {
                val completedTsStr = if (item.completedTimestamp > 0L) sdf.format(Date(item.completedTimestamp)) else ""
                val turnaroundStr = if (item.completedTimestamp > orderTs && orderTs > 0L) {
                    val diffSec = (item.completedTimestamp - orderTs) / 1000
                    val mins = diffSec / 60
                    val secs = diffSec % 60
                    if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                } else ""
                val itemStatus = when {
                    item.completedQuantity >= item.quantity -> "READY"
                    item.completedQuantity > 0 -> "PARTIAL"
                    else -> "PREPARING"
                }

                itemsArr.put(JSONObject().apply {
                    put("itemName", item.itemName)
                    put("name", item.itemName)
                    put("category", "General")
                    put("unitPrice", item.itemPrice)
                    put("price", item.itemPrice)
                    put("quantity", item.quantity)
                    put("qty", item.quantity)
                    put("subtotal", item.subtotal)
                    put("completedQuantity", item.completedQuantity)
                    put("completedTimestamp", completedTsStr)
                    put("itemStatus", itemStatus)
                    put("turnaroundTime", turnaroundStr)
                })
            }
            put("items", itemsArr)
            put("itemsSummary", orderWithItems.items.joinToString(", ") { "${it.quantity}x ${it.itemName}" })
            put("isMade", if (orderWithItems.order.isMade) "YES" else "NO")
            put("isPaid", if (orderWithItems.order.isPaid) "YES" else "NO")
            put(
                "paymentMode",
                orderWithItems.order.paymentMode.ifBlank { if (orderWithItems.order.isPaid) "CASH" else "UNPAID" }
            )
            put("isDelivered", if (orderWithItems.order.isDelivered) "YES" else "NO")
            put("timestamp", if (orderTs > 0L) sdf.format(Date(orderTs)) else sdf.format(Date()))
            val completedOrderTsStr = if (orderWithItems.order.completedTimestamp > 0L) {
                sdf.format(Date(orderWithItems.order.completedTimestamp))
            } else ""
            put("completedTimestamp", completedOrderTsStr)
        }
    }
}
