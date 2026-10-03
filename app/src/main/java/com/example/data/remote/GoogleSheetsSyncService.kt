package com.example.data.remote

import android.util.Log
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
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
            error.message?.contains("5", ignoreCase = false) == true
                    && error.message?.contains("HTTP Error") == true -> true // 5xx server errors
            else -> false
        }
    }

    private fun buildOrderJsonObject(orderWithItems: OrderWithItems): JSONObject {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val orderTs = orderWithItems.order.timestamp

        return JSONObject().apply {
            put("orderId", orderWithItems.order.orderId)
            put("customerName", orderWithItems.order.customerName)
            put("contactNumber", orderWithItems.order.contactNumber.ifBlank { "N/A" })
            put("totalAmount", orderWithItems.order.totalAmount)
            put("notes", orderWithItems.order.notes)

            val itemsArray = JSONArray()
            orderWithItems.items.forEach { item ->
                val completedTsStr = if (item.completedTimestamp > 0L) sdf.format(Date(item.completedTimestamp)) else ""
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

            put("timestamp", sdf.format(Date(orderTs)))
            val completedOrderTsStr = if (orderWithItems.order.completedTimestamp > 0L) sdf.format(Date(orderWithItems.order.completedTimestamp)) else ""
            put("completedTimestamp", completedOrderTsStr)
        }
    }

    suspend fun syncOrderToGoogleSheets(
        webhookUrl: String,
        orderWithItems: OrderWithItems
    ): Result<String> = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncOrder(${orderWithItems.order.orderId})") {
            try {
                val urlWithParams = if (webhookUrl.contains("?")) "$webhookUrl&action=syncOrder" else "$webhookUrl?action=syncOrder"
                val jsonObject = buildOrderJsonObject(orderWithItems).apply {
                    put("action", "syncOrder")
                }

                val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(urlWithParams)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 302 || response.code == 200) {
                        Result.success("Synced order successfully to Google Sheets")
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                    }
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
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }

        executeWithRetry("syncAllOrders(${ordersList.size})") {
            try {
                val urlWithParams = if (webhookUrl.contains("?")) "$webhookUrl&action=syncAll" else "$webhookUrl?action=syncAll"
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
                    if (response.isSuccessful || response.code == 302 || response.code == 200) {
                        Result.success("Synced all orders successfully to Google Sheets")
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                    }
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
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets Webhook URL is not configured."))
        }
        if (ordersList.isEmpty()) {
            return@withContext Result.success("No pending orders to sync.")
        }

        executeWithRetry("syncBatchOrders(${ordersList.size})") {
            try {
                val urlWithParams = if (webhookUrl.contains("?")) "$webhookUrl&action=syncBatch" else "$webhookUrl?action=syncBatch"
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
                    if (response.isSuccessful || response.code == 302 || response.code == 200) {
                        Result.success("Synced ${ordersList.size} pending orders successfully to Google Sheets")
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                    }
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
        if (webhookUrl.isBlank()) return@withContext Result.success("No webhook configured")

        executeWithRetry("deleteOrder($orderId)") {
            try {
                val encodedId = java.net.URLEncoder.encode(orderId, "UTF-8")
                val urlWithParams = if (webhookUrl.contains("?")) {
                    "$webhookUrl&action=deleteOrder&orderId=$encodedId"
                } else {
                    "$webhookUrl?action=deleteOrder&orderId=$encodedId"
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
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Log.d(TAG, "Delete order from Sheets: $orderId → $body")
                        Result.success("Order deleted from Google Sheets")
                    } else {
                        Log.w(TAG, "Delete order failed: HTTP ${response.code} → $body")
                        Result.failure(Exception("HTTP Error ${response.code}"))
                    }
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
        if (webhookUrl.isBlank()) {
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
                    .url(webhookUrl.trim())
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 302 || response.code == 200) {
                        Result.success("Item Master synced successfully to Google Sheets")
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                    }
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
        if (webhookUrl.isBlank()) {
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
                    .url(webhookUrl.trim())
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 302 || response.code == 200) {
                        Result.success("Range analytics calculated and synced to Google Sheets")
                    } else {
                        Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                    }
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
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Webhook URL is blank"))
        }

        try {
            val url = if (webhookUrl.contains("?")) {
                "${webhookUrl.trim()}&action=getTodayMaxOrderId&date=$datePart"
            } else {
                "${webhookUrl.trim()}?action=getTodayMaxOrderId&date=$datePart"
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            var responseJson: JSONObject? = null

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if ((response.isSuccessful || response.code == 302 || response.code == 200) && bodyString.isNotBlank()) {
                        try {
                            responseJson = JSONObject(bodyString)
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }

            if (responseJson == null || responseJson?.optString("result") != "success") {
                val postPayload = JSONObject().apply {
                    put("action", "getTodayMaxOrderId")
                    put("date", datePart)
                }
                val postRequest = Request.Builder()
                    .url(webhookUrl.trim())
                    .post(postPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(postRequest).execute().use { postResponse ->
                    val bodyString = postResponse.body?.string() ?: ""
                    if ((postResponse.isSuccessful || postResponse.code == 302 || postResponse.code == 200) && bodyString.isNotBlank()) {
                        responseJson = JSONObject(bodyString)
                    }
                }
            }

            val json = responseJson ?: return@withContext Result.failure(Exception("Failed to fetch order sync info from Google Sheets"))

            if (json.optString("result") == "success") {
                val maxSeq = json.optInt("maxSequence", 1000)
                val maxOrderId = json.optString("maxOrderId", "")
                val todayOrdersArray = json.optJSONArray("todayOrders") ?: JSONArray()

                val remoteOrders = mutableListOf<OrderWithItems>()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (i in 0 until todayOrdersArray.length()) {
                    val orderObj = todayOrdersArray.getJSONObject(i)
                    val orderId = orderObj.optString("orderId", "")
                    if (orderId.isBlank()) continue

                    val customerName = orderObj.optString("customerName", "Walk-in Customer")
                    val contactNumber = orderObj.optString("contactNumber", "N/A")
                    val totalAmount = orderObj.optDouble("totalAmount", 0.0)
                    val isPaidStr = orderObj.optString("isPaid", "NO")
                    val paymentMode = orderObj.optString("paymentMode", "UNPAID")
                    val isMadeStr = orderObj.optString("isMade", "NO")
                    val isDeliveredStr = orderObj.optString("isDelivered", "NO")
                    val notes = orderObj.optString("notes", "")
                    val timestampStr = orderObj.optString("timestamp", "")

                    val ts = try {
                        if (timestampStr.isNotBlank()) sdf.parse(timestampStr)?.time ?: System.currentTimeMillis() else System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }

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
                        val name = itemObj.optString("itemName", "Item")
                        val unitPrice = itemObj.optDouble("unitPrice", 0.0)
                        val qty = itemObj.optInt("quantity", 1)
                        val completedQty = itemObj.optInt("completedQuantity", 0)
                        val completedTsStr = itemObj.optString("completedTimestamp", "")
                        val completedTs = try {
                            if (completedTsStr.isNotBlank()) sdf.parse(completedTsStr)?.time ?: 0L else 0L
                        } catch (_: Exception) { 0L }

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
                Result.failure(Exception(json.optString("message", "Error from Google Sheets")))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
      withLock_(function() { syncAllOrders_(ss, allOrders); });
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
"""
    }
}

