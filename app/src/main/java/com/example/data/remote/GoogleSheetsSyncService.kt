package com.example.data.remote

import android.util.Log
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.domain.inventory.BillLedgerRow
import com.example.domain.inventory.IngredientPriceMatrix
import com.example.domain.inventory.IngredientPriceSummary
import com.example.domain.inventory.SkuPriceHistoryMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TodaySyncResult(
    val maxSequence: Int,
    val maxOrderId: String,
    val remoteOrders: List<OrderWithItems>
)

class GoogleSheetsSyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val TAG = "SheetsSyncService"
    private val MAX_RETRIES = 3
    private val INITIAL_RETRY_DELAY_MS = 1000L // 1 second
    private val HTTP_5XX_REGEX = Regex("""HTTP Error 5\d{2}""")

    private val sheetDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private fun formatSheetDate(ts: Long): String =
        sheetDateFormat.get()!!.format(Date(ts))

    private fun parseSheetDate(dateStr: String, fallback: Long): Long {
        if (dateStr.isBlank()) return fallback
        return try {
            sheetDateFormat.get()!!.parse(dateStr)?.time ?: fallback
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date '$dateStr': ${e.message}")
            fallback
        }
    }

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        val value = optString(key, fallback)
        return if (value == "null") fallback else value
    }

    private fun parseMutationResponse(response: Response, successMessage: String): Result<String> {
        val bodyStr = response.body?.string()?.trim().orEmpty()
        if (!response.isSuccessful) {
            return Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
        }
        if (bodyStr.isBlank()) {
            return Result.failure(Exception("Empty response from Google Sheets webhook"))
        }
        return try {
            val json = JSONObject(bodyStr)
            if (json.optNonNullString("result") == "success") {
                Result.success(successMessage)
            } else {
                val msg = json.optNonNullString("message", "Google Sheets sync failed")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Invalid JSON response from Google Sheets webhook: ${e.message}"))
        }
    }

    /**
     * Execute an HTTP operation with exponential backoff retry.
     * Retries up to MAX_RETRIES times on transient failures (timeouts, 5xx, network errors).
     */
    private suspend fun <T> executeWithRetry(
        operationName: String,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                val result = block()
                if (result.isSuccess) return result

                // Check if error is retryable (5xx, timeout)
                val error = result.exceptionOrNull()
                if (error != null && isRetryableError(error) && attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1)) // Exponential backoff
                    android.util.Log.w(TAG, "$operationName attempt $attempt failed: ${error.message}. Retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                    lastException = error as? Exception
                    continue
                }
                return result
            } catch (e: Exception) {
                lastException = e
                if (isRetryableError(e) && attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                    android.util.Log.w(TAG, "$operationName attempt $attempt threw: ${e.message}. Retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(lastException ?: Exception("$operationName failed after $MAX_RETRIES attempts"))
    }

    private fun isRetryableError(error: Throwable): Boolean {
        return when {
            error is java.net.SocketTimeoutException -> true
            error is java.net.ConnectException -> true
            error is java.net.UnknownHostException -> true
            error is java.io.IOException -> true
            error.message?.let { HTTP_5XX_REGEX.containsMatchIn(it) } == true -> true // 5xx server errors
            else -> false
        }
    }

    private fun buildOrderJsonObject(orderWithItems: OrderWithItems): JSONObject {
        val orderTs = orderWithItems.order.timestamp

        return JSONObject().apply {
            put("orderId", orderWithItems.order.orderId)
            put("customerName", orderWithItems.order.customerName)
            put("contactNumber", orderWithItems.order.contactNumber.ifBlank { "N/A" })
            put("totalAmount", orderWithItems.order.totalAmount)
            put("notes", orderWithItems.order.notes)

            val itemsArray = JSONArray()
            orderWithItems.items.forEach { item ->
                val completedTsStr = if (item.completedTimestamp > 0L) formatSheetDate(item.completedTimestamp) else ""
                val turnaroundStr = if (item.completedTimestamp > orderTs) {
                    val diffSec = (item.completedTimestamp - orderTs) / 1000
                    val mins = diffSec / 60
                    val secs = diffSec % 60
                    if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                } else ""
                val itemStatus = if (item.completedQuantity >= item.quantity) "READY" else if (item.completedQuantity > 0) "PARTIAL" else "PREPARING"

                itemsArray.put(JSONObject().apply {
                    put("itemName", item.itemName)
                    put("name", item.itemName)
                    put("unitPrice", item.itemPrice)
                    put("price", item.itemPrice)
                    put("quantity", item.quantity)
                    put("qty", item.quantity)
                    put("subtotal", item.subtotal)
                    put("category", "General")
                    put("completedQuantity", item.completedQuantity)
                    put("completedTimestamp", completedTsStr)
                    put("itemStatus", itemStatus)
                    put("turnaroundTime", turnaroundStr)
                })
            }
            put("items", itemsArray)
            put("itemsSummary", orderWithItems.items.joinToString(", ") { "${it.quantity}x ${it.itemName}" })
            put("isMade", if (orderWithItems.order.isMade) "YES" else "NO")
            put("isPaid", if (orderWithItems.order.isPaid) "YES" else "NO")
            put("paymentMode", orderWithItems.order.paymentMode.ifBlank { if (orderWithItems.order.isPaid) "CASH" else "UNPAID" })
            put("isDelivered", if (orderWithItems.order.isDelivered) "YES" else "NO")

            put("timestamp", formatSheetDate(orderTs))
            val completedOrderTsStr = if (orderWithItems.order.completedTimestamp > 0L) formatSheetDate(orderWithItems.order.completedTimestamp) else ""
            put("completedTimestamp", completedOrderTsStr)
        }
    }

    suspend fun syncOrderToGoogleSheets(
        webhookUrl: String,
        orderWithItems: OrderWithItems
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncOrder(${orderWithItems.order.orderId})") {
            try {
                val urlWithParams = if (cleanUrl.contains("?")) "$cleanUrl&action=syncOrder" else "$cleanUrl?action=syncOrder"
                val jsonObject = buildOrderJsonObject(orderWithItems).apply {
                    put("action", "syncOrder")
                }

                val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(urlWithParams)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Synced order successfully to Google Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncAllOrdersToGoogleSheets(
        webhookUrl: String,
        ordersList: List<OrderWithItems>,
        datePrefix: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncAllOrders(${ordersList.size})") {
            try {
                val urlWithParams = if (cleanUrl.contains("?")) "$cleanUrl&action=syncAll" else "$cleanUrl?action=syncAll"
                val ordersArray = JSONArray()
                ordersList.forEach { orderWithItems ->
                    ordersArray.put(buildOrderJsonObject(orderWithItems))
                }

                val jsonObject = JSONObject().apply {
                    put("action", "syncAll")
                    put("orders", ordersArray)
                    if (datePrefix.isNotBlank()) put("datePrefix", datePrefix)
                }

                val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(urlWithParams)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Synced all orders successfully to Google Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncBatchOrdersToGoogleSheets(
        webhookUrl: String,
        ordersList: List<OrderWithItems>
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }
        if (ordersList.isEmpty()) {
            return@withContext Result.success("No pending orders to sync.")
        }

        executeWithRetry("syncBatchOrders(${ordersList.size})") {
            try {
                val urlWithParams = if (cleanUrl.contains("?")) "$cleanUrl&action=syncBatch" else "$cleanUrl?action=syncBatch"
                val ordersArray = JSONArray()
                ordersList.forEach { orderWithItems ->
                    ordersArray.put(buildOrderJsonObject(orderWithItems))
                }

                val jsonObject = JSONObject().apply {
                    put("action", "syncBatch")
                    put("orders", ordersArray)
                }

                val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(urlWithParams)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Synced ${ordersList.size} pending orders successfully to Google Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteOrderFromGoogleSheets(
        webhookUrl: String,
        orderId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) return@withContext Result.success("No webhook configured")

        executeWithRetry("deleteOrder($orderId)") {
            try {
                val encodedId = URLEncoder.encode(orderId, "UTF-8")
                val urlWithParams = if (cleanUrl.contains("?")) {
                    "$cleanUrl&action=deleteOrder&orderId=$encodedId"
                } else {
                    "$cleanUrl?action=deleteOrder&orderId=$encodedId"
                }

                // Use GET request — Apps Script doGet handles deleteOrder
                // POST with query params fails because the 302 redirect
                // (script.google.com → googleusercontent.com) drops both
                // POST body AND original URL query params
                val request = Request.Builder()
                    .url(urlWithParams)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Order deleted from Google Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncItemMasterToGoogleSheets(
        webhookUrl: String,
        menuItems: List<MenuItemEntity>
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncItemMaster(${menuItems.size} items)") {
            try {
                val menuArray = JSONArray()
                menuItems.forEach { item ->
                    menuArray.put(JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("category", item.category)
                        put("price", item.price)
                        put("description", item.description)
                        put("isAvailable", item.isAvailable)
                    })
                }

                val jsonObject = JSONObject().apply {
                    put("action", "syncItemMaster")
                    put("menuItems", menuArray)
                }

                val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Item Master synced successfully to Google Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncDateRangeAnalyticsToGoogleSheets(
        webhookUrl: String,
        startDateStr: String,
        endDateStr: String,
        ordersList: List<OrderWithItems>
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncDateRangeAnalytics($startDateStr to $endDateStr)") {
            try {
                val ordersArray = JSONArray()
                ordersList.forEach { orderWithItems ->
                    ordersArray.put(buildOrderJsonObject(orderWithItems))
                }

                val jsonObject = JSONObject().apply {
                    put("action", "filterRangeAnalytics")
                    put("startDate", startDateStr)
                    put("endDate", endDateStr)
                    put("orders", ordersArray)
                    put("totalOrdersCount", ordersList.size)
                    put("totalSalesAmount", ordersList.sumOf { it.order.totalAmount })
                }

                val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Range analytics calculated and synced to Google Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchLatestOrderInfoFromSheets(
        webhookUrl: String,
        datePart: String
    ): Result<TodaySyncResult> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Webhook URL is blank"))
        }

        try {
            val encodedDate = URLEncoder.encode(datePart, "UTF-8")
            val url = if (cleanUrl.contains("?")) {
                "$cleanUrl&action=getTodayMaxOrderId&date=$encodedDate"
            } else {
                "$cleanUrl?action=getTodayMaxOrderId&date=$encodedDate"
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            var responseJson: JSONObject? = null

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()?.trim() ?: ""
                    if (response.isSuccessful && bodyString.isNotBlank()) {
                        try {
                            responseJson = JSONObject(bodyString)
                        } catch (e: Exception) {
                            Log.w(TAG, "GET getTodayMaxOrderId returned non-JSON: ${e.message}")
                        }
                    } else if (!response.isSuccessful) {
                        Log.w(TAG, "GET getTodayMaxOrderId HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GET getTodayMaxOrderId failed, will try POST fallback: ${e.message}")
            }

            if (responseJson == null || responseJson?.optNonNullString("result") != "success") {
                val postPayload = JSONObject().apply {
                    put("action", "getTodayMaxOrderId")
                    put("date", datePart)
                }
                val postRequest = Request.Builder()
                    .url(cleanUrl)
                    .post(postPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(postRequest).execute().use { postResponse ->
                    val bodyString = postResponse.body?.string()?.trim() ?: ""
                    if (postResponse.isSuccessful && bodyString.isNotBlank()) {
                        try {
                            responseJson = JSONObject(bodyString)
                        } catch (e: Exception) {
                            Log.w(TAG, "POST getTodayMaxOrderId returned non-JSON: ${e.message}")
                        }
                    } else if (!postResponse.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP Error ${postResponse.code}: ${postResponse.message}"))
                    }
                }
            }

            val json = responseJson ?: return@withContext Result.failure(Exception("Failed to fetch order sync info from Google Sheets"))

            if (json.optNonNullString("result") == "success") {
                val maxSeq = json.optInt("maxSequence", 1000)
                val maxOrderId = json.optNonNullString("maxOrderId", "")
                val todayOrdersArray = json.optJSONArray("todayOrders") ?: JSONArray()

                val remoteOrders = mutableListOf<OrderWithItems>()

                for (i in 0 until todayOrdersArray.length()) {
                    val orderObj = todayOrdersArray.getJSONObject(i)
                    val orderId = orderObj.optNonNullString("orderId", "")
                    if (orderId.isBlank()) continue

                    val customerName = orderObj.optNonNullString("customerName", "Walk-in Customer")
                    val contactNumber = orderObj.optNonNullString("contactNumber", "N/A")
                    val totalAmount = orderObj.optDouble("totalAmount", 0.0)
                    val isPaidStr = orderObj.optNonNullString("isPaid", "NO")
                    val paymentMode = orderObj.optNonNullString("paymentMode", "UNPAID")
                    val isMadeStr = orderObj.optNonNullString("isMade", "NO")
                    val isDeliveredStr = orderObj.optNonNullString("isDelivered", "NO")
                    val notes = orderObj.optNonNullString("notes", "")
                    val timestampStr = orderObj.optNonNullString("timestamp", "")

                    val ts = parseSheetDate(timestampStr, System.currentTimeMillis())

                    val orderEntity = OrderEntity(
                        orderId = orderId,
                        customerName = customerName,
                        contactNumber = if (contactNumber == "N/A") "" else contactNumber,
                        totalAmount = totalAmount,
                        timestamp = ts,
                        isMade = isMadeStr.equals("YES", ignoreCase = true),
                        isPaid = isPaidStr.equals("YES", ignoreCase = true),
                        paymentMode = paymentMode,
                        isDelivered = isDeliveredStr.equals("YES", ignoreCase = true),
                        syncStatus = "SYNCED",
                        notes = notes
                    )

                    val itemsArray = orderObj.optJSONArray("items") ?: JSONArray()
                    val itemEntities = mutableListOf<OrderItemEntity>()
                    for (j in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.getJSONObject(j)
                        val name = itemObj.optNonNullString("itemName", "Item")
                        val unitPrice = itemObj.optDouble("unitPrice", 0.0)
                        val qty = itemObj.optInt("quantity", 1)
                        val completedQty = itemObj.optInt("completedQuantity", 0)
                        val completedTsStr = itemObj.optNonNullString("completedTimestamp", "")
                        val completedTs = parseSheetDate(completedTsStr, 0L)

                        itemEntities.add(
                            OrderItemEntity(
                                orderId = orderId,
                                menuItemId = 0,
                                itemName = name,
                                itemPrice = unitPrice,
                                quantity = qty,
                                completedQuantity = completedQty,
                                completedTimestamp = completedTs,
                                subtotal = unitPrice * qty
                            )
                        )
                    }

                    remoteOrders.add(OrderWithItems(orderEntity, itemEntities))
                }

                Result.success(TodaySyncResult(maxSeq, maxOrderId, remoteOrders))
            } else {
                Result.failure(Exception(json.optNonNullString("message", "Error from Google Sheets")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ingredientSummaryToJson(summary: IngredientPriceSummary): JSONObject {
        return JSONObject().apply {
            put("itemName", summary.itemName)
            put("category", summary.category)
            put("unit", summary.unit)
            put("packSize", summary.packSize)
            put("currentStock", summary.currentStock)
            put("latestRate", summary.latestRate)
            put("previousRate", summary.previousRate ?: 0.0)
            put("hasPreviousRate", summary.previousRate != null)
            put("priceDelta", summary.priceDelta)
            put("priceChangePercent", summary.priceChangePercent)
            put("trend", summary.trend.name)
            put("alertLabel", summary.alertLabel)
            put("lastSupplier", summary.lastSupplier.ifBlank { "-" })
            put("lastPurchasedAt", if (summary.lastPurchasedTimestamp > 0L) formatSheetDate(summary.lastPurchasedTimestamp) else "")
            put("purchaseCount", summary.totalPurchasesCount)
        }
    }

    private fun priceMatrixToJson(matrix: IngredientPriceMatrix): JSONObject {
        val datesArr = JSONArray()
        matrix.dates.forEach { datesArr.put(it) }
        val rowsArr = JSONArray()
        matrix.rows.forEach { r ->
            val ratesObj = JSONObject()
            r.ratesByDate.forEach { (dateKey, rate) -> ratesObj.put(dateKey, rate) }
            rowsArr.put(JSONObject().apply {
                put("itemName", r.itemName)
                put("category", r.category)
                put("unit", r.unit)
                put("currentStock", r.currentStock)
                put("stockAlertLevel", r.stockAlertLevel.name)
                put("stockAlertLabel", r.stockAlertLabel)
                put("suggestedOrderLabel", r.suggestedOrderLabel)
                put("latestRate", r.latestRate)
                put("priceChangePercent", r.priceChangePercent)
                put("priceAlertLevel", r.priceAlertLevel.name)
                put("priceAlertLabel", r.priceAlertLabel)
                put("trend", r.trend.name)
                put("ratesByDate", ratesObj)
            })
        }
        return JSONObject().apply {
            put("dates", datesArr)
            put("rows", rowsArr)
        }
    }

    suspend fun syncInventoryPurchaseToGoogleSheets(
        webhookUrl: String,
        order: InventoryOrderEntity,
        items: List<InventoryOrderItemEntity>,
        pricingSummaries: List<IngredientPriceSummary>,
        priceMatrix: IngredientPriceMatrix? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncInventoryPurchase(${order.orderId})") {
            try {
                val urlWithParams = if (cleanUrl.contains("?")) "$cleanUrl&action=syncInventoryPurchase" else "$cleanUrl?action=syncInventoryPurchase"
                val itemsArray = JSONArray()
                items.forEach { line ->
                    itemsArray.put(JSONObject().apply {
                        put("orderId", order.orderId)
                        put("itemName", line.itemName)
                        put("quantity", line.quantity)
                        put("unit", line.unit)
                        put("unitPrice", line.unitPrice)
                        put("subtotal", line.subtotal)
                    })
                }
                val summariesArray = JSONArray()
                pricingSummaries.forEach { summariesArray.put(ingredientSummaryToJson(it)) }

                val payload = JSONObject().apply {
                    put("action", "syncInventoryPurchase")
                    put("orderId", order.orderId)
                    put("timestamp", formatSheetDate(order.timestamp))
                    put("supplierName", order.supplierName.ifBlank { "Supplier" })
                    put("totalAmount", order.totalAmount)
                    put("notes", order.notes)
                    put("items", itemsArray)
                    put("pricingSummaries", summariesArray)
                    if (priceMatrix != null) {
                        put("priceMatrix", priceMatrixToJson(priceMatrix))
                    }
                }

                val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(urlWithParams).post(requestBody).build()
                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Synced raw material bill & pricing to Excel/Sheets")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncSkuPriceHistoryToGoogleSheets(
        webhookUrl: String,
        matrix: SkuPriceHistoryMatrix,
        ledger: List<BillLedgerRow>,
        includeDateInCells: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncSkuPriceHistory(${matrix.rows.size})") {
            try {
                val urlWithParams = if (cleanUrl.contains("?")) "$cleanUrl&action=syncSkuPriceHistory" else "$cleanUrl?action=syncSkuPriceHistory"
                val rowsArr = JSONArray()
                for (row in matrix.rows) {
                    val billsArr = JSONArray()
                    row.bills.forEach { b ->
                        billsArr.put(JSONObject().apply {
                            put("billIndex", b.billIndex)
                            put("price", b.price)
                            put("dateLabel", b.dateLabel)
                            put("cellText", b.formatCell(includeDateInCells))
                            put("isUnitAnomaly", b.isUnitAnomaly)
                            put("orderId", b.orderId)
                            put("supplierName", b.supplierName)
                        })
                    }
                    rowsArr.put(JSONObject().apply {
                        put("skuName", row.skuName)
                        put("unitWeightLabel", row.unitWeightLabel)
                        put("latestPrice", row.latestPrice)
                        put("priceChangeVsFirstPct", row.priceChangeVsFirstPct ?: 0.0)
                        put("hasChangePct", row.priceChangeVsFirstPct != null)
                        put("isPriceHike", row.isPriceHike)
                        put("hasUnitAnomaly", row.hasUnitAnomaly)
                        put("bills", billsArr)
                    })
                }

                val ledgerArr = JSONArray()
                ledger.forEach { l ->
                    ledgerArr.put(JSONObject().apply {
                        put("orderId", l.orderId)
                        put("dateFormatted", l.dateFormatted)
                        put("supplierName", l.supplierName)
                        put("rawItemName", l.rawItemName)
                        put("matchedSkuName", l.matchedSkuName)
                        put("quantity", l.quantity)
                        put("unit", l.unit)
                        put("unitPrice", l.unitPrice)
                        put("subtotal", l.subtotal)
                        put("normalizedPrice", l.normalizedPrice)
                        put("statusLabel", l.statusLabel)
                        put("notes", l.notes)
                    })
                }

                val payload = JSONObject().apply {
                    put("action", "syncSkuPriceHistory")
                    put("includeDateInCells", includeDateInCells)
                    put("maxBillColumns", matrix.maxBillColumns)
                    put("rows", rowsArr)
                    put("ledger", ledgerArr)
                }

                val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(urlWithParams).post(requestBody).build()
                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Synced SKU Price History (${matrix.rows.size} SKUs)")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun syncIngredientPricingToGoogleSheets(
        webhookUrl: String,
        pricingSummaries: List<IngredientPriceSummary>,
        recentOrders: List<InventoryOrderWithItems>,
        priceMatrix: IngredientPriceMatrix? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncIngredientPricing(${pricingSummaries.size})") {
            try {
                val urlWithParams = if (cleanUrl.contains("?")) "$cleanUrl&action=syncIngredientPricing" else "$cleanUrl?action=syncIngredientPricing"
                val summariesArray = JSONArray()
                pricingSummaries.forEach { summariesArray.put(ingredientSummaryToJson(it)) }

                val purchasesArray = JSONArray()
                recentOrders.sortedByDescending { it.order.timestamp }.take(200).forEach { bill ->
                    val ts = formatSheetDate(bill.order.timestamp)
                    bill.items.forEach { line ->
                        purchasesArray.put(JSONObject().apply {
                            put("orderId", bill.order.orderId)
                            put("timestamp", ts)
                            put("supplierName", bill.order.supplierName.ifBlank { "Supplier" })
                            put("itemName", line.itemName)
                            put("quantity", line.quantity)
                            put("unit", line.unit)
                            put("unitPrice", line.unitPrice)
                            put("subtotal", line.subtotal)
                            put("notes", bill.order.notes)
                        })
                    }
                }

                val payload = JSONObject().apply {
                    put("action", "syncIngredientPricing")
                    put("pricingSummaries", summariesArray)
                    put("purchases", purchasesArray)
                    if (priceMatrix != null) {
                        put("priceMatrix", priceMatrixToJson(priceMatrix))
                    }
                }

                val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(urlWithParams).post(requestBody).build()
                client.newCall(request).execute().use { response ->
                    parseMutationResponse(response, "Ingredient pricing balanced to Google Sheets (${pricingSummaries.size} items)")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        const val GOOGLE_APPS_SCRIPT_TEMPLATE = """
// ============================================================================
// FILTR COFFEE — Google Apps Script (v5.0 — Production)
// ============================================================================
//
// SETUP INSTRUCTIONS:
// 1. Open your Google Sheet → Extensions → Apps Script
// 2. Delete any existing code and paste this ENTIRE script
// 3. Save (Ctrl+S)
// 4. Click "Deploy" → "New deployment"
//    - Type: "Web app"
//    - Execute as: "Me"
//    - Who has access: "Anyone"
// 5. Click "Deploy" and copy the Web App URL
// 6. Paste that URL into FILTR Billing App → Settings → Google Sheets Webhook URL
//
// SHEETS CREATED AUTOMATICALLY:
//   • Orders_Billing      — All orders with status
//   • Order_Items_Detail  — Line-item breakdown per order
//   • Item_Master          — Menu/catalog items
//   • Date_Range_Analytics — Revenue analytics per date range
//   • Filtered_Orders_Export — Filtered order snapshots
//   • Sync_Log             — Audit trail of all sync operations
//
// ============================================================================

// ======================== CONFIGURATION ========================

var CONFIG = {
  LOCK_TIMEOUT_MS: 25000,       // Max wait for concurrent write lock
  MAX_BATCH_SIZE: 500,          // Max orders per batch to avoid timeout
  ENABLE_LOGGING: true,         // Write to Sync_Log sheet
  DATE_FORMAT: "yyyy-MM-dd HH:mm:ss",
  TIMEZONE: Session.getScriptTimeZone()
};

// ======================== SHEET HEADERS ========================

var HEADERS = {
  BILLING: ["Order ID", "Timestamp", "Customer Name", "Contact Number", "Total Amount",
            "Payment Status", "Payment Mode", "Kitchen Status", "Delivery Status",
            "Notes", "Items Summary", "Completed Timestamp", "Sync Source", "Last Updated"],
  ITEMS: ["Order ID", "Item Name", "Category", "Unit Price", "Quantity", "Subtotal",
          "Completed Qty", "Item Status", "Completed Timestamp", "Turnaround Time"],
  MENU: ["Item ID", "Item Name", "Category", "Price (₹)", "Description", "Status"],
  ANALYTICS: ["Filter Start Date", "Filter End Date", "Total Orders", "Total Revenue (₹)",
              "Cash Revenue (₹)", "UPI Revenue (₹)", "Unpaid Revenue (₹)",
              "Avg Order Value (₹)", "Calculated At"],
  FILTERED: ["Order ID", "Timestamp", "Customer Name", "Contact Number", "Total Amount",
             "Payment Status", "Payment Mode", "Kitchen Status", "Delivery Status"],
  INGREDIENT_PRICING: ["Ingredient Name", "Category", "Unit", "Pack Size", "Current Stock",
                       "Last Rate (₹)", "Previous Rate (₹)", "Price Diff (₹)", "Change (%)",
                       "Price Alert", "Last Supplier", "Last Purchased", "Total Bills"],
  INVENTORY_PURCHASES: ["Bill ID", "Date", "Supplier", "Ingredient Name", "Quantity",
                        "Unit", "Unit Rate (₹)", "Subtotal (₹)", "Notes"],
  LOG: ["Timestamp", "Action", "Details", "Status", "Duration (ms)"]
};

// ======================== ENTRY POINTS ========================

function doGet(e) {
  var startTime = Date.now();
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var params = (e && e.parameter) ? e.parameter : {};
    var action = params.action || "";

    // GET: Fetch max order sequence
    if (action === "getTodayMaxOrderId" || action === "getLatestOrderInfo" ||
        action === "getMaxSequence" || action === "get_max_sequence") {
      var datePrefix = params.date || params.datePrefix || "";
      var result = getTodayMaxOrderId_(ss, datePrefix);
      logSync_(ss, action, "datePrefix=" + datePrefix, "OK", Date.now() - startTime);
      return jsonResponse_(result);
    }

    // GET: Delete order
    if (action === "deleteOrder" || action === "delete_order" || action === "delete") {
      var orderId = params.orderId || params.order_id || "";
      if (orderId) {
        withLock_(function() { deleteOrderById_(ss, orderId); });
        logSync_(ss, "deleteOrder", "orderId=" + orderId, "OK", Date.now() - startTime);
        return successResponse_("Order '" + orderId + "' deleted successfully");
      }
      return errorResponse_("Missing orderId parameter");
    }

    // GET: Health check
    logSync_(ss, "healthCheck", "GET ping", "OK", Date.now() - startTime);
    return jsonResponse_({ result: "success", status: "FILTR Coffee Sync Webhook Active", version: "5.0" });

  } catch (err) {
    logSync_(SpreadsheetApp.getActiveSpreadsheet(), "doGet:ERROR", err.toString(), "FAILED", Date.now() - startTime);
    return errorResponse_(err.toString());
  }
}

function doPost(e) {
  var startTime = Date.now();
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    // Parse POST body — handle both JSON body and URL params
    var data = {};
    if (e && e.postData && e.postData.contents) {
      try {
        data = JSON.parse(e.postData.contents);
      } catch (parseErr) {
        // If JSON parse fails, try URL-encoded form data
        data = {};
      }
    }

    // Action from URL param takes priority, then from JSON body
    var urlParams = (e && e.parameter) ? e.parameter : {};
    var action = urlParams.action || data.action || "";

    // ---- DELETE ORDER ----
    if (action === "deleteOrder" || action === "delete_order" || action === "delete") {
      var delId = data.orderId || data.order_id ||
                  (data.order && data.order.orderId) ||
                  urlParams.orderId || urlParams.order_id || "";
      if (delId) {
        withLock_(function() { deleteOrderById_(ss, delId); });
        logSync_(ss, "deleteOrder", "orderId=" + delId, "OK", Date.now() - startTime);
        return successResponse_("Order '" + delId + "' deleted successfully");
      }
      return errorResponse_("Missing orderId for delete action");
    }

    // ---- GET MAX ORDER SEQUENCE ----
    if (action === "getTodayMaxOrderId" || action === "getLatestOrderInfo" ||
        action === "getMaxSequence" || action === "get_max_sequence") {
      var dateVal = data.date || data.datePrefix || urlParams.date || "";
      var seqResult = getTodayMaxOrderId_(ss, dateVal);
      logSync_(ss, action, "datePrefix=" + dateVal, "OK", Date.now() - startTime);
      return jsonResponse_(seqResult);
    }

    // ---- SYNC ITEM MASTER (MENU) ----
    if (action === "syncItemMaster" || action === "sync_menu") {
      var menuItems = data.menuItems || data.menu_items || [];
      withLock_(function() { syncItemMaster_(ss, menuItems); });
      logSync_(ss, "syncItemMaster", menuItems.length + " items", "OK", Date.now() - startTime);
      return successResponse_("Item Master synced: " + menuItems.length + " items");
    }

    // ---- SYNC BATCH (PENDING ORDERS) ----
    if (action === "syncBatch" || action === "syncPending" || action === "sync_batch") {
      var batchOrders = data.orders || [];
      withLock_(function() { syncBatchOrders_(ss, batchOrders); });
      logSync_(ss, "syncBatch", batchOrders.length + " orders", "OK", Date.now() - startTime);
      return successResponse_("Batch synced: " + batchOrders.length + " orders");
    }

    // ---- SYNC ALL ORDERS (FULL RESYNC) ----
    if (action === "syncAll" || action === "sync_all") {
      var allOrders = data.orders || [];
      var syncDatePrefix = data.datePrefix || data.date || urlParams.datePrefix || urlParams.date || "";
      withLock_(function() { syncAllOrders_(ss, allOrders, syncDatePrefix); });
      logSync_(ss, "syncAll", allOrders.length + " orders", "OK", Date.now() - startTime);
      return successResponse_("Full sync completed: " + allOrders.length + " orders");
    }

    // ---- DATE RANGE ANALYTICS ----
    if (action === "filterRangeAnalytics" || action === "filter_range_analytics") {
      var rangeOrders = data.orders || [];
      withLock_(function() {
        syncRangeAnalytics_(ss, data.startDate || "", data.endDate || "", rangeOrders);
      });
      logSync_(ss, "filterRangeAnalytics",
        (data.startDate || "ALL") + " to " + (data.endDate || "ALL") + " | " + rangeOrders.length + " orders",
        "OK", Date.now() - startTime);
      return successResponse_("Range analytics calculated: " + rangeOrders.length + " orders");
    }

    // ---- RAW MATERIAL PURCHASE & INGREDIENT PRICING ----
    if (action === "syncInventoryPurchase") {
      withLock_(function() {
        syncInventoryPurchase_(ss, data);
      });
      logSync_(ss, "syncInventoryPurchase", "bill=" + (data.orderId || ""), "OK", Date.now() - startTime);
      return successResponse_("Inventory purchase & pricing synced");
    }

    if (action === "syncSkuPriceHistory") {
      withLock_(function() {
        syncSkuPriceHistory_(ss, data);
      });
      var rowCount = (data.rows || []).length;
      logSync_(ss, "syncSkuPriceHistory", rowCount + " SKUs", "OK", Date.now() - startTime);
      return successResponse_("SKU Price History synced: " + rowCount + " SKUs");
    }

    if (action === "syncIngredientPricing") {
      var summaries = data.pricingSummaries || [];
      var purchases = data.purchases || [];
      var priceMatrix = data.priceMatrix || null;
      withLock_(function() {
        syncIngredientPricingSheet_(ss, summaries);
        syncInventoryPurchaseRows_(ss, purchases);
        if (priceMatrix) {
          syncPriceHistoryMatrixSheet_(ss, priceMatrix);
        }
      });
      logSync_(ss, "syncIngredientPricing", summaries.length + " ingredients", "OK", Date.now() - startTime);
      return successResponse_("Ingredient pricing updated: " + summaries.length + " items");
    }

    // ---- SYNC SINGLE ORDER (default fallback) ----
    if (action === "syncOrder" || action === "sync_order" ||
        (data && (data.orderId || (data.order && data.order.orderId)))) {
      var singleOrder = data.order || data;
      if (singleOrder && singleOrder.orderId) {
        withLock_(function() { syncSingleOrder_(ss, singleOrder); });
        logSync_(ss, "syncOrder", "orderId=" + singleOrder.orderId, "OK", Date.now() - startTime);
        return successResponse_("Order '" + singleOrder.orderId + "' synced successfully");
      }
    }

    logSync_(ss, "doPost:NOOP", "No matching action: " + action, "SKIPPED", Date.now() - startTime);
    return successResponse_("Webhook received — no action matched");

  } catch (err) {
    logSync_(SpreadsheetApp.getActiveSpreadsheet(), "doPost:ERROR", err.toString(), "FAILED", Date.now() - startTime);
    return errorResponse_(err.toString());
  }
}

// ======================== CORE SYNC FUNCTIONS ========================

/**
 * Sync a single order — upserts into Orders_Billing and Order_Items_Detail.
 * If the order already exists, it updates in-place. Otherwise appends.
 */
function syncSingleOrder_(ss, data) {
  if (!data || !data.orderId) return;

  var rawOrderId = String(data.orderId).trim();
  var targetId = normalizeId_(rawOrderId);
  if (!targetId) return;

  var billingSheet = ensureSheet_(ss, "Orders_Billing", HEADERS.BILLING);
  var itemsSheet = ensureSheet_(ss, "Order_Items_Detail", HEADERS.ITEMS);

  var formattedOrderId = "'" + rawOrderId; // Prefix with apostrophe to force text format
  var nowStr = formatDate_(new Date());
  var orderTimestamp = data.timestamp || "";

  // If no timestamp provided and order exists, preserve existing timestamp
  var billingRowIndex = findRowByOrderId_(billingSheet, targetId, 1);
  if (!orderTimestamp && billingRowIndex > 1) {
    var existingTs = billingSheet.getRange(billingRowIndex, 2).getDisplayValue();
    if (existingTs && existingTs !== "null" && existingTs !== "undefined" && existingTs !== "") {
      orderTimestamp = existingTs;
    }
  }
  if (!orderTimestamp) orderTimestamp = nowStr;

  // Completed timestamp
  var completedTs = data.completedTimestamp || "";

  // Items summary
  var itemsSummary = data.itemsSummary || "";
  if (!itemsSummary && data.items && data.items.length > 0) {
    var summaryParts = [];
    for (var s = 0; s < data.items.length; s++) {
      var si = data.items[s];
      summaryParts.push((si.quantity || si.qty || 1) + "x " + (si.itemName || si.name || "Item"));
    }
    itemsSummary = summaryParts.join(", ");
  }

  // Build billing row
  var rowData = [
    formattedOrderId,
    orderTimestamp,
    data.customerName || "Walk-in Customer",
    data.contactNumber || "N/A",
    roundNum_(Number(data.totalAmount || 0)),
    resolvePaidStatus_(data),
    resolvePaymentMode_(data),
    resolveKitchenStatus_(data),
    resolveDeliveryStatus_(data),
    data.notes || "",
    itemsSummary,
    completedTs,
    "FILTR App",
    nowStr
  ];

  // Upsert into Orders_Billing
  if (billingRowIndex > 1) {
    billingSheet.getRange(billingRowIndex, 1, 1, rowData.length).setValues([rowData]);
  } else {
    billingSheet.appendRow(rowData);
  }

  // Upsert items into Order_Items_Detail
  var items = data.items || [];
  if (items.length > 0) {
    syncOrderItems_(itemsSheet, targetId, formattedOrderId, items);
  }
}

/**
 * Sync order items — upserts each item row by matching (orderId + itemName).
 * Also removes orphaned items that no longer exist in the order.
 */
function syncOrderItems_(itemsSheet, targetId, formattedOrderId, items) {
  // Build map of existing item rows for this order
  var existingMap = buildItemRowMap_(itemsSheet, targetId);

  var processedKeys = {};

  for (var i = 0; i < items.length; i++) {
    var item = items[i];
    var name = item.itemName || item.name || "Item";
    var cat = item.category || "General";
    var unitPrice = roundNum_(Number(item.unitPrice !== undefined ? item.unitPrice : (item.price || 0)));
    var qty = Number(item.quantity !== undefined ? item.quantity : (item.qty || 1));
    var subtotal = roundNum_(Number(item.subtotal !== undefined ? item.subtotal : (unitPrice * qty)));
    var completedQty = Number(item.completedQuantity !== undefined ? item.completedQuantity : 0);
    var completedTs = item.completedTimestamp || "";
    var itemStatus = item.itemStatus ||
      (completedQty >= qty ? "READY" : (completedQty > 0 ? "PARTIAL" : "PREPARING"));
    var turnaroundTime = item.turnaroundTime || "";

    var itemRowData = [
      formattedOrderId, name, cat, unitPrice, qty, subtotal,
      completedQty, itemStatus, completedTs, turnaroundTime
    ];

    var nameKey = String(name).trim().toLowerCase();
    processedKeys[nameKey] = true;
    var existingRowIdx = existingMap[nameKey];

    if (existingRowIdx && existingRowIdx > 1) {
      // Update existing row in-place
      itemsSheet.getRange(existingRowIdx, 1, 1, itemRowData.length).setValues([itemRowData]);
    } else {
      // Append new item
      itemsSheet.appendRow(itemRowData);
    }
  }

  // Remove orphaned items (items in sheet but NOT in current order payload)
  removeOrphanedItems_(itemsSheet, targetId, processedKeys);
}

/**
 * Build a map of itemName (lowercase) → row index for a given order ID.
 */
function buildItemRowMap_(itemsSheet, targetId) {
  var map = {};
  var lastRow = itemsSheet.getLastRow();
  if (lastRow <= 1) return map;

  var data = itemsSheet.getRange(2, 1, lastRow - 1, 2).getDisplayValues();
  for (var r = 0; r < data.length; r++) {
    var rowOrderId = normalizeId_(data[r][0]);
    if (rowOrderId === targetId) {
      var rowItemName = String(data[r][1] || "").trim().toLowerCase();
      if (rowItemName) {
        map[rowItemName] = r + 2; // 1-based sheet row
      }
    }
  }
  return map;
}

/**
 * Remove item rows that belong to an order but are no longer in the items payload.
 * This handles the case where items are removed from an order via edit.
 */
function removeOrphanedItems_(itemsSheet, targetId, validKeys) {
  var lastRow = itemsSheet.getLastRow();
  if (lastRow <= 1) return;

  var data = itemsSheet.getRange(2, 1, lastRow - 1, 2).getDisplayValues();
  // Iterate backwards to safely delete rows
  for (var r = data.length - 1; r >= 0; r--) {
    var rowOrderId = normalizeId_(data[r][0]);
    if (rowOrderId === targetId) {
      var rowItemName = String(data[r][1] || "").trim().toLowerCase();
      if (rowItemName && !validKeys[rowItemName]) {
        itemsSheet.deleteRow(r + 2);
      }
    }
  }
}

/**
 * Sync a batch of orders (pending/unsynced).
 */
function syncBatchOrders_(ss, orders) {
  if (!orders || orders.length === 0) return;

  // Cap batch size to avoid Apps Script timeout (6 min limit)
  var limit = Math.min(orders.length, CONFIG.MAX_BATCH_SIZE);
  for (var o = 0; o < limit; o++) {
    syncSingleOrder_(ss, orders[o]);
  }
}

/**
 * Full sync — upserts all provided orders and removes sheet rows
 * that match the same date(s) but are NOT in the incoming list.
 * Preserves ALL historical data from other dates.
 */
function syncAllOrders_(ss, orders, datePrefix) {
  var billingSheet = ensureSheet_(ss, "Orders_Billing", HEADERS.BILLING);
  var itemsSheet = ensureSheet_(ss, "Order_Items_Detail", HEADERS.ITEMS);

  // Determine which date(s) we're syncing
  var syncDates = {};
  if (datePrefix) {
    syncDates[String(datePrefix).toLowerCase()] = true;
  }
  if (orders && orders.length > 0) {
    for (var i = 0; i < orders.length; i++) {
      var oid = String(orders[i].orderId || "");
      var parts = oid.split("-");
      if (parts.length >= 2) {
        syncDates[parts[1].toLowerCase()] = true;
      }
    }
  }

  // Build set of incoming order IDs (normalized)
  var incomingIds = {};
  if (orders && orders.length > 0) {
    for (var j = 0; j < orders.length; j++) {
      var nid = normalizeId_(orders[j].orderId || "");
      if (nid) incomingIds[nid] = true;
    }
  }

  // Remove rows matching sync date(s) but NOT in incoming list
  if (billingSheet && Object.keys(syncDates).length > 0) {
    var lastRow = billingSheet.getLastRow();
    if (lastRow > 1) {
      var sheetIds = billingSheet.getRange(2, 1, lastRow - 1, 1).getDisplayValues();
      for (var r = sheetIds.length - 1; r >= 0; r--) {
        var cellVal = String(sheetIds[r][0] || "").trim();
        var sheetNid = normalizeId_(cellVal);
        if (!sheetNid) continue;
        var rowParts = cellVal.replace(/^'/, "").split("-");
        var rowDate = (rowParts.length >= 2) ? rowParts[1].toLowerCase() : "";
        if (rowDate && syncDates[rowDate] && !incomingIds[sheetNid]) {
          billingSheet.deleteRow(r + 2);
          if (itemsSheet) removeAllRowsByOrderId_(itemsSheet, sheetNid, 1);
        }
      }
    }
  }

  // Upsert all incoming orders
  if (orders && orders.length > 0) {
    for (var o = 0; o < orders.length; o++) {
      syncSingleOrder_(ss, orders[o]);
    }
  }

  if (billingSheet) cleanBlankRows_(billingSheet);
  if (itemsSheet) cleanBlankRows_(itemsSheet);
}

/**
 * Delete an order from all sheets by order ID.
 */
function deleteOrderById_(ss, orderId) {
  var targetId = normalizeId_(orderId);
  if (!targetId) return;

  var sheetNames = ["Orders_Billing", "Order_Items_Detail", "Filtered_Orders_Export"];
  for (var i = 0; i < sheetNames.length; i++) {
    var sheet = ss.getSheetByName(sheetNames[i]);
    if (sheet) {
      removeAllRowsByOrderId_(sheet, targetId, 1);
      cleanBlankRows_(sheet);
    }
  }
}

// ======================== ITEM MASTER ========================

/**
 * Full replace of item master sheet.
 */
function syncItemMaster_(ss, menuItems) {
  if (!menuItems || menuItems.length === 0) return;

  var menuSheet = ensureSheet_(ss, "Item_Master", HEADERS.MENU);
  clearSheetData_(menuSheet);

  var rows = [];
  for (var i = 0; i < menuItems.length; i++) {
    var m = menuItems[i];
    rows.push([
      m.id || (i + 1),
      m.name || "",
      m.category || "",
      roundNum_(Number(m.price || 0)),
      m.description || "",
      (m.isAvailable === true || m.isAvailable === "true") ? "Available" : "Unavailable"
    ]);
  }

  // Batch write all rows at once (much faster than appendRow loop)
  if (rows.length > 0) {
    menuSheet.getRange(2, 1, rows.length, HEADERS.MENU.length).setValues(rows);
  }
}

// ======================== DATE RANGE ANALYTICS ========================

function syncRangeAnalytics_(ss, startDate, endDate, orders) {
  var rangeSheet = ensureSheet_(ss, "Date_Range_Analytics", HEADERS.ANALYTICS);
  var orderList = orders || [];

  // Fallback: if no orders in payload, read from Orders_Billing sheet
  if (orderList.length === 0) {
    orderList = readOrdersFromBillingSheet_(ss, startDate, endDate);
  }

  // Calculate revenue breakdown
  var totalRev = 0, cashRev = 0, upiRev = 0, unpaidRev = 0;

  for (var i = 0; i < orderList.length; i++) {
    var amt = roundNum_(Number(orderList[i].totalAmount || 0));
    totalRev += amt;

    var mode = String(orderList[i].paymentMode || "").toUpperCase();
    var isPaid = String(orderList[i].isPaid || "").toUpperCase();

    if (isPaid === "YES" || isPaid === "PAID") {
      if (mode === "UPI") {
        upiRev += amt;
      } else {
        cashRev += amt; // CASH or unspecified paid
      }
    } else {
      unpaidRev += amt;
    }
  }

  var avgOrderValue = orderList.length > 0 ? roundNum_(totalRev / orderList.length) : 0;

  rangeSheet.appendRow([
    startDate || "ALL",
    endDate || "ALL",
    orderList.length,
    roundNum_(totalRev),
    roundNum_(cashRev),
    roundNum_(upiRev),
    roundNum_(unpaidRev),
    avgOrderValue,
    formatDate_(new Date())
  ]);

  // Export filtered orders snapshot
  var filteredSheet = ensureSheet_(ss, "Filtered_Orders_Export", HEADERS.FILTERED);
  clearSheetData_(filteredSheet);

  if (orderList.length > 0) {
    var exportRows = [];
    for (var o = 0; o < orderList.length; o++) {
      var ord = orderList[o];
      exportRows.push([
        "'" + String(ord.orderId || "").trim(),
        ord.timestamp || "",
        ord.customerName || "Walk-in Customer",
        ord.contactNumber || "N/A",
        roundNum_(Number(ord.totalAmount || 0)),
        resolvePaidStatus_(ord),
        resolvePaymentMode_(ord),
        resolveKitchenStatus_(ord),
        resolveDeliveryStatus_(ord)
      ]);
    }
    filteredSheet.getRange(2, 1, exportRows.length, HEADERS.FILTERED.length).setValues(exportRows);
  }
}

/**
 * Read orders from Orders_Billing sheet matching a date range (by order ID date part).
 */
function readOrdersFromBillingSheet_(ss, startDate, endDate) {
  var billingSheet = ss.getSheetByName("Orders_Billing");
  if (!billingSheet || billingSheet.getLastRow() <= 1) return [];

  var billingData = billingSheet.getRange(2, 1, billingSheet.getLastRow() - 1,
    Math.min(billingSheet.getLastColumn(), HEADERS.BILLING.length)).getDisplayValues();

  var startClean = (startDate || "").replace(/-/g, "").trim();
  var endClean = (endDate || "").replace(/-/g, "").trim();
  var results = [];

  for (var r = 0; r < billingData.length; r++) {
    var row = billingData[r];
    var rawId = normalizeId_(row[0]);
    if (!rawId) continue;

    // Extract 8-digit date from order ID (e.g., "20260806" from "flt-20260806-1001")
    var dateMatch = rawId.match(/(\d{8})/);
    var orderDateNum = dateMatch ? dateMatch[1] : "";

    var inRange = true;
    if (startClean && orderDateNum && orderDateNum < startClean) inRange = false;
    if (endClean && orderDateNum && orderDateNum > endClean) inRange = false;

    if (inRange) {
      results.push({
        orderId: String(row[0] || "").replace(/^'/, ""),
        timestamp: row[1] || "",
        customerName: row[2] || "Walk-in Customer",
        contactNumber: row[3] || "N/A",
        totalAmount: parseFloat(row[4]) || 0,
        isPaid: row[5] === "PAID" ? "YES" : "NO",
        paymentMode: row[6] || "UNPAID",
        isMade: row[7] === "READY" ? "YES" : "NO",
        isDelivered: row[8] === "DELIVERED" ? "YES" : "NO",
        notes: row[9] || ""
      });
    }
  }
  return results;
}

// ======================== ORDER ID SEQUENCE ========================

/**
 * Returns the max order sequence for a given date prefix,
 * plus all today's orders for cross-device sync.
 */
function getTodayMaxOrderId_(ss, datePrefix) {
  var billingSheet = ss.getSheetByName("Orders_Billing");
  var itemsSheet = ss.getSheetByName("Order_Items_Detail");

  var maxSequence = 1000;
  var maxOrderId = "";
  var todayOrdersMap = {};

  if (!billingSheet || billingSheet.getLastRow() <= 1) {
    return {
      result: "success",
      datePrefix: datePrefix,
      maxSequence: 1000,
      maxOrderId: "",
      todayOrders: []
    };
  }

  var lastRow = billingSheet.getLastRow();
  var numCols = Math.min(billingSheet.getLastColumn(), HEADERS.BILLING.length);
  var billingData = billingSheet.getRange(2, 1, lastRow - 1, numCols).getDisplayValues();

  for (var r = 0; r < billingData.length; r++) {
    var row = billingData[r];
    var rawId = normalizeId_(row[0]);
    if (!rawId) continue;

    // Extract sequence number (last segment after final hyphen)
    var parts = rawId.split("-");
    if (parts.length >= 2) {
      var seqStr = parts[parts.length - 1].trim();
      var seqNum = parseInt(seqStr, 10);
      if (!isNaN(seqNum)) {
        // Only consider orders matching the date prefix
        if (!datePrefix || rawId.indexOf(datePrefix) >= 0) {
          if (seqNum > maxSequence) {
            maxSequence = seqNum;
            maxOrderId = String(row[0] || "").replace(/^'/, "");
          }
        }
      }
    }

    // Collect today's orders for cross-device sync
    if (!datePrefix || rawId.indexOf(datePrefix) >= 0) {
      todayOrdersMap[rawId] = {
        orderId: String(row[0] || "").replace(/^'/, ""),
        timestamp: row[1] || "",
        customerName: row[2] || "Walk-in Customer",
        contactNumber: row[3] || "N/A",
        totalAmount: parseFloat(row[4]) || 0,
        isPaid: row[5] === "PAID" ? "YES" : "NO",
        paymentMode: row[6] || "UNPAID",
        isMade: row[7] === "READY" ? "YES" : "NO",
        isDelivered: row[8] === "DELIVERED" ? "YES" : "NO",
        notes: row[9] || "",
        items: []
      };
    }
  }

  // Attach items to today's orders
  if (itemsSheet && itemsSheet.getLastRow() > 1) {
    var itemsLastRow = itemsSheet.getLastRow();
    var itemsCols = Math.min(itemsSheet.getLastColumn(), HEADERS.ITEMS.length);
    var itemsData = itemsSheet.getRange(2, 1, itemsLastRow - 1, itemsCols).getDisplayValues();

    for (var i = 0; i < itemsData.length; i++) {
      var itemRow = itemsData[i];
      var itemOrderId = normalizeId_(itemRow[0]);
      if (todayOrdersMap[itemOrderId]) {
        todayOrdersMap[itemOrderId].items.push({
          itemName: itemRow[1] || "Item",
          category: itemRow[2] || "General",
          unitPrice: parseFloat(itemRow[3]) || 0,
          quantity: parseInt(itemRow[4], 10) || 1,
          subtotal: parseFloat(itemRow[5]) || 0,
          completedQuantity: parseInt(itemRow[6], 10) || 0,
          completedTimestamp: itemRow[8] || ""
        });
      }
    }
  }

  // Convert map to array
  var todayOrdersList = [];
  for (var k in todayOrdersMap) {
    if (todayOrdersMap.hasOwnProperty(k)) {
      todayOrdersList.push(todayOrdersMap[k]);
    }
  }

  return {
    result: "success",
    datePrefix: datePrefix,
    maxSequence: maxSequence,
    maxOrderId: maxOrderId,
    todayOrders: todayOrdersList,
    totalTodayOrders: todayOrdersList.length
  };
}

// ======================== UTILITY FUNCTIONS ========================

/**
 * Normalize order ID for reliable comparison:
 * Removes leading apostrophe, "Order #", "FLT-" prefix, trims, lowercases.
 */
function normalizeId_(id) {
  if (id === null || id === undefined) return "";
  var s = String(id).trim().toLowerCase();
  // Remove leading apostrophe (Google Sheets text prefix)
  if (s.charAt(0) === "'") s = s.substring(1).trim();
  // Remove common prefixes
  s = s.replace(/^order\s*#?/i, "").replace(/^flt-?/i, "").replace(/^#/i, "").trim();
  return s;
}

/**
 * Find the row index (1-based) of an order by its normalized ID.
 * Returns -1 if not found.
 */
function findRowByOrderId_(sheet, targetId, colIndex) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return -1;

  var displayValues = sheet.getRange(2, colIndex, lastRow - 1, 1).getDisplayValues();
  for (var r = 0; r < displayValues.length; r++) {
    var cellId = normalizeId_(displayValues[r][0]);
    if (cellId && cellId === targetId) {
      return r + 2; // 1-based, accounting for header
    }
  }
  return -1;
}

/**
 * Remove ALL rows matching an order ID (exact match only — no substring matching).
 */
function removeAllRowsByOrderId_(sheet, targetId, colIndex) {
  if (!sheet) return;
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return;

  var displayValues = sheet.getRange(2, colIndex, lastRow - 1, 1).getDisplayValues();
  // Delete from bottom to top to preserve row indices
  for (var r = displayValues.length - 1; r >= 0; r--) {
    var cellId = normalizeId_(displayValues[r][0]);
    if (cellId && cellId === targetId) {
      sheet.deleteRow(r + 2);
    }
  }
}

/**
 * Remove blank or invalid rows from a sheet.
 */
function cleanBlankRows_(sheet) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return;

  var displayValues = sheet.getRange(2, 1, lastRow - 1, 1).getDisplayValues();
  for (var r = displayValues.length - 1; r >= 0; r--) {
    var cellText = String(displayValues[r][0] || "").trim();
    if (!cellText || cellText === "null" || cellText === "undefined" || cellText === "'") {
      sheet.deleteRow(r + 2);
    }
  }
}

/**
 * Get or create a sheet with the given name and headers.
 * If the sheet exists, ensures headers are up to date.
 */
function ensureSheet_(ss, sheetName, headers) {
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    sheet.appendRow(headers);
    formatHeaderRow_(sheet, headers.length);
  } else {
    var lastRow = sheet.getLastRow();
    if (lastRow === 0) {
      sheet.appendRow(headers);
      formatHeaderRow_(sheet, headers.length);
    } else {
      // Ensure sheet has enough columns
      if (sheet.getMaxColumns() < headers.length) {
        sheet.insertColumnsAfter(sheet.getMaxColumns(), headers.length - sheet.getMaxColumns());
      }
      // Update headers to latest version
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
      formatHeaderRow_(sheet, headers.length);
    }
  }
  return sheet;
}

/**
 * Format the header row with FILTR branding.
 */
function formatHeaderRow_(sheet, numCols) {
  var headerRange = sheet.getRange(1, 1, 1, numCols);
  headerRange.setFontWeight("bold")
             .setBackground("#09090B")
             .setFontColor("#FFFFFF")
             .setHorizontalAlignment("center");
  sheet.setFrozenRows(1);
}

/**
 * Clear all data rows (keep header).
 */
function clearSheetData_(sheet) {
  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    sheet.deleteRows(2, lastRow - 1);
  }
}

/**
 * Format a Date object to the configured date format string.
 */
function formatDate_(date) {
  return Utilities.formatDate(date, CONFIG.TIMEZONE, CONFIG.DATE_FORMAT);
}

/**
 * Round a number to 2 decimal places.
 */
function roundNum_(n) {
  return Math.round((n || 0) * 100) / 100;
}

// ======================== STATUS RESOLVERS ========================

function resolvePaidStatus_(data) {
  if (data.isPaid === "YES" || data.isPaid === true || data.isPaid === "PAID") return "PAID";
  return "UNPAID";
}

function resolvePaymentMode_(data) {
  var mode = String(data.paymentMode || "").toUpperCase().trim();
  if (mode === "CASH" || mode === "UPI") return mode;
  if (resolvePaidStatus_(data) === "PAID") return "CASH"; // Default paid mode
  return "UNPAID";
}

function resolveKitchenStatus_(data) {
  if (data.isMade === "YES" || data.isMade === true || data.isMade === "READY") return "READY";
  return "PREPARING";
}

function resolveDeliveryStatus_(data) {
  if (data.isDelivered === "YES" || data.isDelivered === true || data.isDelivered === "DELIVERED") return "DELIVERED";
  return "PENDING";
}

// ======================== CONCURRENCY PROTECTION ========================

/**
 * Execute a function within a script-level lock to prevent concurrent write conflicts
 * from multiple devices syncing simultaneously.
 */
function withLock_(fn) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(CONFIG.LOCK_TIMEOUT_MS);
    fn();
  } finally {
    lock.releaseLock();
  }
}

// ======================== RESPONSE HELPERS ========================

function jsonResponse_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function successResponse_(msg) {
  return ContentService.createTextOutput(JSON.stringify({ result: "success", message: msg }))
    .setMimeType(ContentService.MimeType.JSON);
}

function errorResponse_(msg) {
  return ContentService.createTextOutput(JSON.stringify({ result: "error", message: msg }))
    .setMimeType(ContentService.MimeType.JSON);
}

// ======================== AUDIT LOGGING ========================

/**
 * Log sync operations to a Sync_Log sheet for debugging and audit trail.
 */
function logSync_(ss, action, details, status, durationMs) {
  if (!CONFIG.ENABLE_LOGGING) return;
  try {
    var logSheet = ensureSheet_(ss, "Sync_Log", HEADERS.LOG);
    var timestamp = formatDate_(new Date());
    logSheet.appendRow([timestamp, action, details, status, durationMs || 0]);

    // Auto-trim log to last 500 entries to prevent sheet bloat
    var logLastRow = logSheet.getLastRow();
    if (logLastRow > 502) {
      logSheet.deleteRows(2, logLastRow - 501);
    }
  } catch (e) {
    // Logging should never break the main flow
  }
}

// ======================== SPREADSHEET UI MENU ========================

function onOpen() {
  var ui = SpreadsheetApp.getUi();
  ui.createMenu("☕ FILTR Analytics")
    .addItem("📊 Run Date Range Analytics", "menuRunRangeAnalytics_")
    .addItem("🔄 Refresh Today Analytics", "menuRefreshTodayAnalytics_")
    .addSeparator()
    .addItem("🧹 Clean Blank Rows (All Sheets)", "menuCleanAllSheets_")
    .addItem("📋 View Sync Log", "menuViewSyncLog_")
    .addToUi();
}

function menuRunRangeAnalytics_() {
  var ui = SpreadsheetApp.getUi();
  var startPrompt = ui.prompt("Date Range Analytics",
    "Enter Start Date (YYYY-MM-DD):", ui.ButtonSet.OK_CANCEL);
  if (startPrompt.getSelectedButton() !== ui.Button.OK) return;
  var startDate = startPrompt.getResponseText().trim();

  var endPrompt = ui.prompt("Date Range Analytics",
    "Enter End Date (YYYY-MM-DD):", ui.ButtonSet.OK_CANCEL);
  if (endPrompt.getSelectedButton() !== ui.Button.OK) return;
  var endDate = endPrompt.getResponseText().trim();

  // Validate dates
  if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate) || !/^\d{4}-\d{2}-\d{2}$/.test(endDate)) {
    ui.alert("Invalid date format. Please use YYYY-MM-DD.");
    return;
  }

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  syncRangeAnalytics_(ss, startDate, endDate, []);
  ui.alert("✅ Analytics generated!\n\nCheck 'Date_Range_Analytics' and 'Filtered_Orders_Export' tabs.");
}

function menuRefreshTodayAnalytics_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var todayStr = formatDate_(new Date()).substring(0, 10); // YYYY-MM-DD
  syncRangeAnalytics_(ss, todayStr, todayStr, []);
  SpreadsheetApp.getUi().alert("✅ Today's analytics refreshed!");
}

function menuCleanAllSheets_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheetNames = ["Orders_Billing", "Order_Items_Detail", "Filtered_Orders_Export"];
  var cleaned = 0;
  for (var i = 0; i < sheetNames.length; i++) {
    var sheet = ss.getSheetByName(sheetNames[i]);
    if (sheet) {
      cleanBlankRows_(sheet);
      cleaned++;
    }
  }
  SpreadsheetApp.getUi().alert("✅ Cleaned blank rows from " + cleaned + " sheets.");
}

function menuViewSyncLog_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var logSheet = ss.getSheetByName("Sync_Log");
  if (logSheet) {
    ss.setActiveSheet(logSheet);
  } else {
    SpreadsheetApp.getUi().alert("No sync log found. Sync some orders first!");
  }
}

// ======================== INVENTORY PRICING & PURCHASES ========================

function syncInventoryPurchase_(ss, data) {
  if (!data) return;
  var rows = [];
  var items = data.items || [];
  for (var i = 0; i < items.length; i++) {
    var line = items[i];
    rows.push({
      orderId: data.orderId || line.orderId || "",
      timestamp: data.timestamp || formatDate_(new Date()),
      supplierName: data.supplierName || "Supplier",
      itemName: line.itemName || "",
      quantity: line.quantity || 0,
      unit: line.unit || "",
      unitPrice: line.unitPrice || 0,
      subtotal: line.subtotal || 0,
      notes: data.notes || ""
    });
  }
  syncInventoryPurchaseRows_(ss, rows);
  if (data.pricingSummaries && data.pricingSummaries.length > 0) {
    syncIngredientPricingSheet_(ss, data.pricingSummaries);
  }
  if (data.priceMatrix) {
    syncPriceHistoryMatrixSheet_(ss, data.priceMatrix);
  }
}

function syncInventoryPurchaseRows_(ss, purchaseRows) {
  if (!purchaseRows || purchaseRows.length === 0) return;
  var sheet = ensureSheet_(ss, "Inventory_Purchases", HEADERS.INVENTORY_PURCHASES);
  var existingKeys = {};
  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    var existingVals = sheet.getRange(2, 1, lastRow - 1, 4).getDisplayValues();
    for (var r = 0; r < existingVals.length; r++) {
      var k = String(existingVals[r][0]).replace(/^'/, "").trim().toLowerCase() + "::" + String(existingVals[r][3]).trim().toLowerCase();
      existingKeys[k] = r + 2;
    }
  }
  for (var i = 0; i < purchaseRows.length; i++) {
    var p = purchaseRows[i];
    var rowData = [
      "'" + String(p.orderId || ""),
      p.timestamp || "",
      p.supplierName || "Supplier",
      p.itemName || "",
      roundNum_(Number(p.quantity || 0)),
      p.unit || "",
      roundNum_(Number(p.unitPrice || 0)),
      roundNum_(Number(p.subtotal || 0)),
      p.notes || ""
    ];
    var key = String(p.orderId || "").replace(/^'/, "").trim().toLowerCase() + "::" + String(p.itemName || "").trim().toLowerCase();
    if (existingKeys[key]) {
      sheet.getRange(existingKeys[key], 1, 1, rowData.length).setValues([rowData]);
    } else {
      sheet.appendRow(rowData);
    }
  }
}

function syncIngredientPricingSheet_(ss, summaries) {
  var sheet = ensureSheet_(ss, "Ingredient_Pricing", HEADERS.INGREDIENT_PRICING);
  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    sheet.getRange(2, 1, lastRow - 1, HEADERS.INGREDIENT_PRICING.length).clearContent();
  }
  if (!summaries || summaries.length === 0) return;

  var rows = [];
  var bgColors = [];
  for (var i = 0; i < summaries.length; i++) {
    var s = summaries[i];
    rows.push([
      s.itemName || "",
      s.category || "General",
      s.unit || "kg",
      roundNum_(Number(s.packSize || 1)),
      roundNum_(Number(s.currentStock || 0)),
      roundNum_(Number(s.latestRate || 0)),
      s.hasPreviousRate ? roundNum_(Number(s.previousRate || 0)) : "",
      roundNum_(Number(s.priceDelta || 0)),
      roundNum_(Number(s.priceChangePercent || 0)) + "%",
      s.alertLabel || "",
      s.lastSupplier || "-",
      s.lastPurchasedAt || "",
      Number(s.purchaseCount || 0)
    ]);
    var rowBg = "#FFFFFF";
    if (s.trend === "INCREASED") rowBg = "#FEE2E2";
    else if (s.trend === "DECREASED") rowBg = "#DCFCE7";
    bgColors.push([rowBg]);
  }
  var range = sheet.getRange(2, 1, rows.length, HEADERS.INGREDIENT_PRICING.length);
  range.setValues(rows);
  // Highlight Price Alert column (col 10) for immediate price-hike visibility in Excel/Sheets
  sheet.getRange(2, 10, rows.length, 1).setBackgrounds(bgColors);
}

function syncPriceHistoryMatrixSheet_(ss, matrix) {
  if (!matrix || !matrix.rows) return;
  var dates = matrix.dates || [];
  var baseHeaders = ["Ingredient Name", "Category", "Unit", "Present Stock", "Stock Alert", "Suggested Order", "Latest Rate (INR)", "Price Alert"];
  var allHeaders = baseHeaders.concat(dates);
  var sheet = ensureSheet_(ss, "Price_History_Matrix", allHeaders);
  var lastRow = sheet.getLastRow();
  var lastCol = Math.max(sheet.getLastColumn(), allHeaders.length);
  if (lastRow > 1 && lastCol > 0) {
    sheet.getRange(2, 1, lastRow - 1, lastCol).clearContent().setBackground(null);
  }
  if (matrix.rows.length === 0) return;

  var values = [];
  var stockColors = [];
  var priceColors = [];
  for (var i = 0; i < matrix.rows.length; i++) {
    var r = matrix.rows[i];
    var ratesByDate = r.ratesByDate || {};
    var rowVals = [
      r.itemName || "",
      r.category || "General",
      r.unit || "kg",
      roundNum_(Number(r.currentStock || 0)),
      r.stockAlertLabel || "OK",
      r.suggestedOrderLabel || "—",
      roundNum_(Number(r.latestRate || 0)),
      r.priceAlertLabel || "OK"
    ];
    for (var d = 0; d < dates.length; d++) {
      var dk = dates[d];
      rowVals.push(ratesByDate[dk] !== undefined && ratesByDate[dk] !== null ? roundNum_(Number(ratesByDate[dk])) : "");
    }
    values.push(rowVals);

    var sBg = "#DCFCE7";
    if (r.stockAlertLevel === "OUT_OF_STOCK" || r.stockAlertLevel === "CRITICAL_REORDER") sBg = "#FEE2E2";
    else if (r.stockAlertLevel === "LOW_STOCK") sBg = "#FEF3C7";
    stockColors.push([sBg]);

    var pBg = "#FFFFFF";
    if (r.priceAlertLevel === "HIGH_HIKE") pBg = "#FEE2E2";
    else if (r.priceAlertLevel === "MINOR_UP") pBg = "#FEF3C7";
    else if (r.priceAlertLevel === "PRICE_DROP") pBg = "#DCFCE7";
    priceColors.push([pBg]);
  }
  sheet.getRange(2, 1, values.length, allHeaders.length).setValues(values);
  sheet.getRange(2, 5, values.length, 1).setBackgrounds(stockColors);
  sheet.getRange(2, 8, values.length, 1).setBackgrounds(priceColors);
  sheet.setFrozenColumns(1);
}

// ======================== v6.0 CLEAN SKU PRICE HISTORY & BILL LEDGER ========================

function syncSkuPriceHistory_(ss, data) {
  var rows = data.rows || [];
  var ledger = data.ledger || [];
  var includeDates = data.includeDateInCells !== false;
  var maxBills = Number(data.maxBillColumns || 0);
  for (var i = 0; i < rows.length; i++) {
    var bl = (rows[i].bills || []).length;
    if (bl > maxBills) maxBills = bl;
  }
  if (maxBills < 1) maxBills = 1;

  // 1. Primary Sheet: "SKU Price History"
  var headers = ["SKU Name", "Unit / Weight", "Latest Price (INR)", "Price Change vs First (%)"];
  for (var b = 1; b <= maxBills; b++) {
    headers.push("Bill " + b);
  }
  var sheet = ensureSheet_(ss, "SKU Price History", headers);
  try {
    ss.setActiveSheet(sheet);
    ss.moveActiveSheet(1);
  } catch (e) {}

  var lastRow = sheet.getLastRow();
  var maxCols = Math.max(sheet.getLastColumn(), headers.length);
  if (lastRow > 1 && maxCols > 0) {
    sheet.getRange(2, 1, lastRow - 1, maxCols).clearContent().setBackground(null).setFontColor("#18181B").setFontWeight("normal");
  }
  if (maxCols > headers.length) {
    sheet.getRange(1, headers.length + 1, 1, maxCols - headers.length).clearContent().setBackground(null);
  }

  if (rows.length > 0) {
    var sheetValues = [];
    var pctBgs = [];
    var pctColors = [];
    var pctWeights = [];
    var billBgs = [];
    var billColors = [];

    for (var r = 0; r < rows.length; r++) {
      var sku = rows[r];
      var pctNum = Number(sku.priceChangeVsFirstPct || 0);
      var pctLabel = sku.hasChangePct
        ? ((pctNum > 0 ? "+" : "") + (Math.round(pctNum * 10) / 10).toFixed(1) + "%")
        : "0.0%";

      var rowVals = [
        sku.skuName || "",
        sku.unitWeightLabel || "",
        roundNum_(Number(sku.latestPrice || 0)),
        pctLabel
      ];
      var rowBillBgs = [];
      var rowBillColors = [];
      var bills = sku.bills || [];
      for (var c = 0; c < maxBills; c++) {
        var bill = c < bills.length ? bills[c] : null;
        if (!bill) {
          rowVals.push("");
          rowBillBgs.push(null);
          rowBillColors.push("#18181B");
        } else if (bill.isUnitAnomaly) {
          rowVals.push(bill.cellText || "CHECK UNIT");
          rowBillBgs.push("#FEF3C7");
          rowBillColors.push("#B45309");
        } else {
          rowVals.push(includeDates ? (bill.cellText || String(bill.price)) : roundNum_(Number(bill.price || 0)));
          rowBillBgs.push(null);
          rowBillColors.push("#18181B");
        }
      }
      sheetValues.push(rowVals);
      billBgs.push(rowBillBgs);
      billColors.push(rowBillColors);

      if (sku.hasChangePct && pctNum > 0) {
        pctBgs.push(["#FEE2E2"]);
        pctColors.push(["#B91C1C"]);
        pctWeights.push(["bold"]);
      } else if (sku.hasChangePct && pctNum < 0) {
        pctBgs.push(["#DCFCE7"]);
        pctColors.push(["#15803D"]);
        pctWeights.push(["bold"]);
      } else {
        pctBgs.push([null]);
        pctColors.push(["#52525B"]);
        pctWeights.push(["normal"]);
      }
    }

    sheet.getRange(2, 1, sheetValues.length, headers.length).setValues(sheetValues);
    sheet.getRange(2, 1, sheetValues.length, 1).setFontWeight("bold");
    sheet.getRange(2, 3, sheetValues.length, 1).setFontWeight("bold");
    var pctRange = sheet.getRange(2, 4, sheetValues.length, 1);
    pctRange.setBackgrounds(pctBgs).setFontColors(pctColors).setFontWeights(pctWeights);
    if (maxBills > 0) {
      sheet.getRange(2, 5, sheetValues.length, maxBills).setBackgrounds(billBgs).setFontColors(billColors);
    }
    sheet.setFrozenRows(1);
    sheet.setFrozenColumns(1);
    try { sheet.autoResizeColumns(1, headers.length); } catch (e) {}
  }

  // 2. Second Sheet: "Bill Ledger"
  var ledgerHeaders = [
    "Bill ID", "Date", "Supplier", "SKU Name (Raw)", "Matched SKU",
    "Quantity", "Unit", "Unit Price (INR)", "Subtotal (INR)",
    "Normalized Rate (INR)", "Status", "Notes"
  ];
  var ledgerSheet = ensureSheet_(ss, "Bill Ledger", ledgerHeaders);
  try {
    ss.setActiveSheet(ledgerSheet);
    ss.moveActiveSheet(2);
  } catch (e) {}
  var ledgerLastRow = ledgerSheet.getLastRow();
  if (ledgerLastRow > 1) {
    ledgerSheet.getRange(2, 1, ledgerLastRow - 1, ledgerHeaders.length).clearContent().setBackground(null);
  }
  if (ledger.length > 0) {
    var lVals = [];
    var lStatusBgs = [];
    for (var i = 0; i < ledger.length; i++) {
      var l = ledger[i];
      lVals.push([
        "'" + String(l.orderId || ""),
        l.dateFormatted || "",
        l.supplierName || "Supplier",
        l.rawItemName || "",
        l.matchedSkuName || "",
        roundNum_(Number(l.quantity || 0)),
        l.unit || "",
        roundNum_(Number(l.unitPrice || 0)),
        roundNum_(Number(l.subtotal || 0)),
        roundNum_(Number(l.normalizedPrice || 0)),
        l.statusLabel || "OK",
        l.notes || ""
      ]);
      lStatusBgs.push([l.statusLabel === "CHECK UNIT" ? "#FEF3C7" : (l.statusLabel === "HARDWARE / EXCLUDED" ? "#F4F4F5" : null)]);
    }
    ledgerSheet.getRange(2, 1, lVals.length, ledgerHeaders.length).setValues(lVals);
    ledgerSheet.getRange(2, 11, lVals.length, 1).setBackgrounds(lStatusBgs);
    ledgerSheet.setFrozenRows(1);
    try { ledgerSheet.autoResizeColumns(1, ledgerHeaders.length); } catch (e) {}
  }
}
"""
    }
}

