package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SupabaseService private constructor() {

    companion object {
        private const val TAG = "SupabaseService"
        private const val SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
        private const val SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"

        @Volatile
        private var INSTANCE: SupabaseService? = null

        fun getInstance(): SupabaseService = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SupabaseService().also { INSTANCE = it }
        }
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private var client: OkHttpClient? = null

    private var _isInitialized = false
    val isInitialized: Boolean get() = _isInitialized

    fun initialize(): Boolean {
        if (_isInitialized) return true
        return try {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
                builder.addInterceptor(logging)
            }
            client = builder.build()

            _isInitialized = true
            Log.d(TAG, "Supabase initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Supabase initialization failed: ${e.message}")
            false
        }
    }

    private fun executeRequest(request: Request): Response? {
        return try {
            client?.newCall(request)?.execute()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Request Failed: ${e.message}")
            null
        }
    }

    private fun executeMutation(request: Request, actionLabel: String): Result<Unit> {
        val resp = executeRequest(request)
            ?: return Result.failure(Exception("$actionLabel: network request failed"))
        return resp.use { r ->
            if (r.isSuccessful) {
                Result.success(Unit)
            } else {
                val errBody = r.body?.string().orEmpty().take(300)
                Log.w(TAG, "$actionLabel failed: HTTP ${r.code} $errBody")
                Result.failure(Exception("$actionLabel failed with HTTP ${r.code}"))
            }
        }
    }

    private fun executeJsonArrayGet(request: Request, actionLabel: String): Result<JSONArray> {
        val resp = executeRequest(request)
            ?: return Result.failure(Exception("$actionLabel: network request failed"))
        return resp.use { r ->
            val bodyStr = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                Log.w(TAG, "$actionLabel failed: HTTP ${r.code} ${bodyStr.take(200)}")
                Result.failure(Exception("$actionLabel failed with HTTP ${r.code}"))
            } else if (bodyStr.isBlank()) {
                Result.success(JSONArray())
            } else {
                try {
                    Result.success(JSONArray(bodyStr))
                } catch (e: Exception) {
                    Log.w(TAG, "$actionLabel invalid JSON array: ${e.message}")
                    Result.failure(e)
                }
            }
        }
    }

    private fun encodeQueryParam(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        val value = optString(key, fallback)
        return if (value == "null") fallback else value
    }

    // ==================== ORDER OPERATIONS ====================

    suspend fun saveOrder(orderWithItems: OrderWithItems): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(orderWithItems.order.orderId)
            val orderObj = JSONObject().apply {
                put("order_id", orderWithItems.order.orderId)
                put("customer_name", orderWithItems.order.customerName)
                put("contact_number", orderWithItems.order.contactNumber)
                put("total_amount", orderWithItems.order.totalAmount)
                put("timestamp", orderWithItems.order.timestamp)
                put("is_made", orderWithItems.order.isMade)
                put("is_paid", orderWithItems.order.isPaid)
                put("is_delivered", orderWithItems.order.isDelivered)
                put("sync_status", orderWithItems.order.syncStatus)
                put("notes", orderWithItems.order.notes)
                put("payment_mode", orderWithItems.order.paymentMode)
                put("completed_timestamp", orderWithItems.order.completedTimestamp)
            }

            val upsertOrderReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(orderObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val upsertRes = executeMutation(upsertOrderReq, "saveOrder upsert")
            if (upsertRes.isFailure) {
                return@withContext Result.failure(upsertRes.exceptionOrNull() ?: Exception("Supabase order upsert failed"))
            }

            val deleteItemsReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val delRes = executeMutation(deleteItemsReq, "saveOrder deleteItems")
            if (delRes.isFailure) {
                return@withContext Result.failure(delRes.exceptionOrNull() ?: Exception("Supabase order items delete failed"))
            }

            if (orderWithItems.items.isNotEmpty()) {
                val itemsArr = JSONArray()
                for (item in orderWithItems.items) {
                    itemsArr.put(JSONObject().apply {
                        put("order_id", orderWithItems.order.orderId)
                        put("menu_item_id", item.menuItemId)
                        put("item_name", item.itemName)
                        put("item_price", item.itemPrice)
                        put("quantity", item.quantity)
                        put("subtotal", item.subtotal)
                        put("completed_quantity", item.completedQuantity)
                        put("completed_timestamp", item.completedTimestamp)
                    })
                }
                val insertItemsReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/order_items")
                    .addHeader("apikey", SERVICE_ROLE_KEY)
                    .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                    .addHeader("Prefer", "return=minimal")
                    .post(itemsArr.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val insRes = executeMutation(insertItemsReq, "saveOrder insertItems")
                if (insRes.isFailure) {
                    return@withContext Result.failure(insRes.exceptionOrNull() ?: Exception("Supabase order items insert failed"))
                }
            }
            Result.success("Order saved")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderFields(orderId: String, fields: Map<String, Any>): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(orderId)
            val json = JSONObject()
            for ((k, v) in fields) {
                val snakeKey = when (k) {
                    "isMade" -> "is_made"
                    "isPaid" -> "is_paid"
                    "isDelivered" -> "is_delivered"
                    "paymentMode" -> "payment_mode"
                    "completedTimestamp" -> "completed_timestamp"
                    "syncStatus" -> "sync_status"
                    else -> k
                }
                json.put(snakeKey, v)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "updateOrderFields($orderId)").map { "Order fields updated" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderItemCompletion(
        orderId: String,
        itemName: String,
        completedQuantity: Int,
        completedTimestamp: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(orderId)
            val encodedName = encodeQueryParam(itemName)
            val json = JSONObject().apply {
                put("completed_quantity", completedQuantity)
                put("completed_timestamp", completedTimestamp)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.$encodedOrderId&item_name=eq.$encodedName")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "updateOrderItemCompletion($orderId, $itemName)").map { "Order item completion updated" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAllOrderItemsCompletion(
        orderId: String,
        completedQuantity: Int,
        completedTimestamp: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(orderId)
            val json = JSONObject().apply {
                put("completed_quantity", completedQuantity)
                put("completed_timestamp", completedTimestamp)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "updateAllOrderItemsCompletion($orderId)").map { "Order items completion updated" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteOrder(orderId: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(orderId)
            val delItems = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val delItemsRes = executeMutation(delItems, "deleteOrder items($orderId)")
            if (delItemsRes.isFailure) {
                return@withContext Result.failure(delItemsRes.exceptionOrNull() ?: Exception("Delete order items failed"))
            }

            val delOrder = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(delOrder, "deleteOrder($orderId)").map { "Order deleted" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchOrdersForDateRange(startMs: Long, endMs: Long): List<OrderWithItems> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?select=*,order_items(*)&timestamp=gte.$startMs&timestamp=lte.$endMs&order=timestamp.desc&limit=5000")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "fetchOrdersForDateRange").getOrNull() ?: return@withContext emptyList()
            val list = mutableListOf<OrderWithItems>()
            for (i in 0 until arr.length()) {
                list.add(jsonToOrderWithItems(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "fetchOrdersForDateRange failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getOrderWithItems(orderId: String): OrderWithItems? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        try {
            val encodedOrderId = encodeQueryParam(orderId)
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?select=*,order_items(*)&order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "getOrderWithItems($orderId)").getOrNull() ?: return@withContext null
            if (arr.length() > 0) {
                jsonToOrderWithItems(arr.getJSONObject(0))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getOrderWithItems failed: ${e.message}")
            null
        }
    }

    fun listenToOrders(): Flow<List<OrderWithItems>> = flow {
        var cycle = 0
        while (true) {
            if (isInitialized) {
                try {
                    cycle++
                    // Optimized active sync:
                    // Frequent cycles (every 2s): poll today's orders (starting midnight today).
                    // Every 25 cycles (~50s): expand to 48 hours for cross-day reconciliation.
                    val isFullReconcile = cycle % 25 == 0
                    val syncWindowMs = if (isFullReconcile) {
                        System.currentTimeMillis() - (48L * 60 * 60 * 1000)
                    } else {
                        Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    val pageSize = 200
                    val maxTotal = if (isFullReconcile) 1000 else 600
                    val list = mutableListOf<OrderWithItems>()
                    var offset = 0
                    var pageFetchFailed = false

                    while (offset < maxTotal) {
                        val req = Request.Builder()
                            .url("$SUPABASE_URL/rest/v1/orders?select=*,order_items(*)&timestamp=gte.$syncWindowMs&order=timestamp.desc&limit=$pageSize&offset=$offset")
                            .addHeader("apikey", SERVICE_ROLE_KEY)
                            .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                            .get()
                            .build()
                        val pageResult = executeJsonArrayGet(req, "listenToOrders")
                        if (pageResult.isFailure) {
                            pageFetchFailed = true
                            break
                        }
                        val arr = pageResult.getOrNull() ?: break
                        for (i in 0 until arr.length()) {
                            list.add(jsonToOrderWithItems(arr.getJSONObject(i)))
                        }
                        if (arr.length() < pageSize) break
                        offset += pageSize
                    }

                    if (!pageFetchFailed) {
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToOrders error: ${e.message}")
                }
            }
            delay(2_000) // 2.0 seconds for instant multi-device order delivery
        }
    }.flowOn(Dispatchers.IO)

    // ==================== SEQUENCE GENERATION ====================

    suspend fun getNextOrderSequence(datePart: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply { put("p_date_part", datePart) }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/get_next_order_sequence")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = executeRequest(req) ?: return@withContext Result.failure(Exception("No response"))
            response.use { resp ->
                val bodyStr = resp.body?.string()?.trim().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "getNextOrderSequence HTTP ${resp.code}: ${bodyStr.take(200)}")
                    return@withContext Result.failure(Exception("Sequence RPC failed with HTTP ${resp.code}"))
                }
                val seq = bodyStr.toIntOrNull()
                    ?: return@withContext Result.failure(Exception("Invalid sequence response: $bodyStr"))
                Result.success(seq)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentMaxSequence(datePart: String): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext 1000
        try {
            val encodedDate = encodeQueryParam(datePart)
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata?key=eq.$encodedDate")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "getCurrentMaxSequence").getOrNull() ?: return@withContext 1000
            if (arr.length() > 0) {
                val valueStr = arr.getJSONObject(0).optNonNullString("value", "")
                return@withContext valueStr.toIntOrNull() ?: 1000
            }
            1000
        } catch (e: Exception) {
            1000
        }
    }

    suspend fun getTodayMaxOrderSequence(datePart: String): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext 1000
        try {
            val encodedDate = encodeQueryParam(datePart)
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?select=order_id&order_id=like.FLT-$encodedDate-%25&limit=5000")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "getTodayMaxOrderSequence").getOrNull() ?: return@withContext 1000
            var maxSeq = 1000
            for (i in 0 until arr.length()) {
                val orderId = arr.getJSONObject(i).optNonNullString("order_id", "")
                val seq = orderId.substringAfterLast("-", "").toIntOrNull()
                if (seq != null && seq > maxSeq) {
                    maxSeq = seq
                }
            }
            maxSeq
        } catch (e: Exception) {
            1000
        }
    }

    suspend fun getTodayMaxInventoryOrderSequence(datePrefix: String): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext 0
        try {
            val encodedDate = encodeQueryParam(datePrefix)
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_orders?select=order_id&order_id=like.INV-$encodedDate-%25&limit=5000")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "getTodayMaxInventoryOrderSequence").getOrNull() ?: return@withContext 0
            var maxSeq = 0
            for (i in 0 until arr.length()) {
                val orderId = arr.getJSONObject(i).optNonNullString("order_id", "")
                val seq = orderId.substringAfterLast("-", "").toIntOrNull()
                if (seq != null && seq > maxSeq) {
                    maxSeq = seq
                }
            }
            maxSeq
        } catch (e: Exception) {
            0
        }
    }

    // ==================== MENU ITEM OPERATIONS ====================

    suspend fun saveMenuItem(item: MenuItemEntity): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                if (item.id != 0) put("id", item.id)
                put("name", item.name)
                put("category", item.category)
                put("price", item.price)
                put("description", item.description)
                put("is_available", item.isAvailable)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/menu_items")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "saveMenuItem(${item.name})").map { "Menu item saved" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(itemId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/menu_items?id=eq.$itemId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(req, "deleteMenuItem($itemId)").map { "Menu item deleted" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAllMenuItems(items: List<MenuItemEntity>): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(JSONObject().apply {
                    if (item.id != 0) put("id", item.id)
                    put("name", item.name)
                    put("category", item.category)
                    put("price", item.price)
                    put("description", item.description)
                    put("is_available", item.isAvailable)
                })
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/menu_items")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(arr.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "syncAllMenuItems").map { "All menu items synced" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToMenuItems(): Flow<List<MenuItemEntity>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/menu_items")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val arr = executeJsonArrayGet(req, "listenToMenuItems").getOrNull()
                    if (arr != null) {
                        val list = mutableListOf<MenuItemEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                MenuItemEntity(
                                    id = obj.optInt("id", 0),
                                    name = obj.optNonNullString("name", ""),
                                    category = obj.optNonNullString("category", ""),
                                    price = obj.optDouble("price", 0.0),
                                    description = obj.optNonNullString("description", ""),
                                    isAvailable = obj.optBoolean("is_available", true)
                                )
                            )
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToMenuItems error: ${e.message}")
                }
            }
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    // ==================== UPI ACCOUNT OPERATIONS ====================

    suspend fun saveUpiAccount(account: UpiAccountEntity): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                if (account.id != 0) put("id", account.id)
                put("vpa", account.vpa)
                put("payee_name", account.payeeName)
                put("is_default", account.isDefault)
                put("note", account.note)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/upi_accounts")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "saveUpiAccount").map { "UPI account saved" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUpiAccount(accountId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/upi_accounts?id=eq.$accountId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(req, "deleteUpiAccount($accountId)").map { "UPI account deleted" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToUpiAccounts(): Flow<List<UpiAccountEntity>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/upi_accounts")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val arr = executeJsonArrayGet(req, "listenToUpiAccounts").getOrNull()
                    if (arr != null) {
                        val list = mutableListOf<UpiAccountEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                UpiAccountEntity(
                                    id = obj.optInt("id", 0),
                                    vpa = obj.optNonNullString("vpa", ""),
                                    payeeName = obj.optNonNullString("payee_name", ""),
                                    isDefault = obj.optBoolean("is_default", false),
                                    note = obj.optNonNullString("note", "")
                                )
                            )
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToUpiAccounts error: ${e.message}")
                }
            }
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    // ==================== INVENTORY OPERATIONS ====================

    suspend fun saveInventoryItem(item: InventoryItemEntity): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                if (item.id != 0) put("id", item.id)
                put("name", item.name)
                put("category", item.category)
                put("unit", item.unit)
                put("standard_quantity", item.standardQuantity)
                put("default_price", item.defaultPrice)
                put("current_stock", item.currentStock)
                put("is_active", item.isActive)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_items")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "saveInventoryItem(${item.name})").map { "Inventory item saved" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryItem(itemId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_items?id=eq.$itemId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(req, "deleteInventoryItem($itemId)").map { "Inventory item deleted" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateInventoryStock(itemId: Int, newStock: Double): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                put("current_stock", newStock)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_items?id=eq.$itemId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "updateInventoryStock($itemId)").map { "Inventory stock updated" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveInventoryOrder(order: InventoryOrderEntity, items: List<InventoryOrderItemEntity>): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(order.orderId)
            val orderJson = JSONObject().apply {
                put("order_id", order.orderId)
                put("timestamp", order.timestamp)
                put("supplier_name", order.supplierName)
                put("total_amount", order.totalAmount)
                put("notes", order.notes)
                put("sync_status", order.syncStatus)
            }
            val reqOrder = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_orders")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(orderJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val orderRes = executeMutation(reqOrder, "saveInventoryOrder(${order.orderId})")
            if (orderRes.isFailure) {
                return@withContext Result.failure(orderRes.exceptionOrNull() ?: Exception("Inventory order upsert failed"))
            }

            val delItemsReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_order_items?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val delRes = executeMutation(delItemsReq, "saveInventoryOrder deleteItems(${order.orderId})")
            if (delRes.isFailure) {
                return@withContext Result.failure(delRes.exceptionOrNull() ?: Exception("Inventory order items delete failed"))
            }

            if (items.isNotEmpty()) {
                val arr = JSONArray()
                for (item in items) {
                    arr.put(JSONObject().apply {
                        put("order_id", order.orderId)
                        put("item_name", item.itemName)
                        put("quantity", item.quantity)
                        put("unit", item.unit)
                        put("unit_price", item.unitPrice)
                        put("subtotal", item.subtotal)
                    })
                }
                val reqItems = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/inventory_order_items")
                    .addHeader("apikey", SERVICE_ROLE_KEY)
                    .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                    .addHeader("Prefer", "return=minimal")
                    .post(arr.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val insRes = executeMutation(reqItems, "saveInventoryOrder insertItems(${order.orderId})")
                if (insRes.isFailure) {
                    return@withContext Result.failure(insRes.exceptionOrNull() ?: Exception("Inventory order items insert failed"))
                }
            }
            Result.success("Inventory order saved")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryOrder(orderId: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedOrderId = encodeQueryParam(orderId)
            val reqItems = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_order_items?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val delItemsRes = executeMutation(reqItems, "deleteInventoryOrder items($orderId)")
            if (delItemsRes.isFailure) {
                return@withContext Result.failure(delItemsRes.exceptionOrNull() ?: Exception("Delete inventory order items failed"))
            }

            val reqOrder = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_orders?order_id=eq.$encodedOrderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(reqOrder, "deleteInventoryOrder($orderId)").map { "Inventory order deleted" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveInventoryUsage(usage: InventoryUsageEntity): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                if (usage.id != 0) put("id", usage.id)
                put("menu_item_id", usage.menuItemId)
                put("menu_item_name", usage.menuItemName)
                put("inventory_item_id", usage.inventoryItemId)
                put("inventory_item_name", usage.inventoryItemName)
                put("quantity_used", usage.quantityUsed)
                put("unit", usage.unit)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_usage")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "saveInventoryUsage").map { "Inventory usage saved" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryUsage(usageId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_usage?id=eq.$usageId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(req, "deleteInventoryUsage($usageId)").map { "Inventory usage deleted" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryUsagesForMenuItem(menuItemId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_usage?menu_item_id=eq.$menuItemId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(req, "deleteInventoryUsagesForMenuItem($menuItemId)").map { "Inventory usages deleted for menu item" }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== INVENTORY POLLING LISTENERS ====================

    fun listenToInventoryOrders(): Flow<List<Pair<InventoryOrderEntity, List<InventoryOrderItemEntity>>>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    // Fetch recent inventory orders (last 30 days)
                    val cutoffMs = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/inventory_orders?select=*,inventory_order_items(*)&timestamp=gte.$cutoffMs&order=timestamp.desc")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val arr = executeJsonArrayGet(req, "listenToInventoryOrders").getOrNull()
                    if (arr != null) {
                        val list = mutableListOf<Pair<InventoryOrderEntity, List<InventoryOrderItemEntity>>>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val orderId = obj.optNonNullString("order_id", "")
                            if (orderId.isBlank()) continue
                            val order = InventoryOrderEntity(
                                orderId = orderId,
                                timestamp = obj.optLong("timestamp", 0L),
                                supplierName = obj.optNonNullString("supplier_name", ""),
                                totalAmount = obj.optDouble("total_amount", 0.0),
                                notes = obj.optNonNullString("notes", ""),
                                syncStatus = "SYNCED"
                            )
                            val items = mutableListOf<InventoryOrderItemEntity>()
                            val itemsArr = obj.optJSONArray("inventory_order_items")
                            if (itemsArr != null) {
                                for (j in 0 until itemsArr.length()) {
                                    val itemObj = itemsArr.getJSONObject(j)
                                    items.add(InventoryOrderItemEntity(
                                        id = itemObj.optInt("id", 0),
                                        orderId = orderId,
                                        itemName = itemObj.optNonNullString("item_name", ""),
                                        quantity = itemObj.optDouble("quantity", 0.0),
                                        unit = itemObj.optNonNullString("unit", "pcs"),
                                        unitPrice = itemObj.optDouble("unit_price", 0.0),
                                        subtotal = itemObj.optDouble("subtotal", 0.0)
                                    ))
                                }
                            }
                            list.add(Pair(order, items))
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToInventoryOrders error: ${e.message}")
                }
            }
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    fun listenToInventoryItems(): Flow<List<InventoryItemEntity>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/inventory_items?order=id.asc")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val arr = executeJsonArrayGet(req, "listenToInventoryItems").getOrNull()
                    if (arr != null) {
                        val list = mutableListOf<InventoryItemEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(InventoryItemEntity(
                                id = obj.optInt("id", 0),
                                name = obj.optNonNullString("name", ""),
                                category = obj.optNonNullString("category", ""),
                                unit = obj.optNonNullString("unit", "kg"),
                                standardQuantity = obj.optDouble("standard_quantity", 1.0),
                                defaultPrice = obj.optDouble("default_price", 0.0),
                                currentStock = obj.optDouble("current_stock", 0.0),
                                isActive = obj.optBoolean("is_active", true)
                            ))
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToInventoryItems error: ${e.message}")
                }
            }
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    fun listenToInventoryUsage(): Flow<List<InventoryUsageEntity>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/inventory_usage?order=id.asc")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val arr = executeJsonArrayGet(req, "listenToInventoryUsage").getOrNull()
                    if (arr != null) {
                        val list = mutableListOf<InventoryUsageEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                InventoryUsageEntity(
                                    id = obj.optInt("id", 0),
                                    menuItemId = obj.optInt("menu_item_id", 0),
                                    menuItemName = obj.optNonNullString("menu_item_name", ""),
                                    inventoryItemId = obj.optInt("inventory_item_id", 0),
                                    inventoryItemName = obj.optNonNullString("inventory_item_name", ""),
                                    quantityUsed = obj.optDouble("quantity_used", 0.0),
                                    unit = obj.optNonNullString("unit", "g")
                                )
                            )
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToInventoryUsage error: ${e.message}")
                }
            }
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    // ==================== DATA MAPPERS ====================

    private fun jsonToOrderWithItems(json: JSONObject): OrderWithItems {
        val orderId = json.optNonNullString("order_id", "")
        val order = OrderEntity(
            orderId = orderId,
            customerName = json.optNonNullString("customer_name", "Walk-in").ifBlank { "Walk-in" },
            contactNumber = json.optNonNullString("contact_number", ""),
            totalAmount = json.optDouble("total_amount", 0.0),
            timestamp = json.optLong("timestamp", 0L),
            isMade = json.optBoolean("is_made", false),
            isPaid = json.optBoolean("is_paid", false),
            isDelivered = json.optBoolean("is_delivered", false),
            syncStatus = "SYNCED",
            notes = json.optNonNullString("notes", ""),
            paymentMode = json.optNonNullString("payment_mode", "UNPAID").ifBlank { "UNPAID" },
            completedTimestamp = json.optLong("completed_timestamp", 0L)
        )

        val items = mutableListOf<OrderItemEntity>()
        val itemsArr = json.optJSONArray("order_items")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(i) ?: continue
                items.add(
                    OrderItemEntity(
                        id = itemObj.optInt("id", 0),
                        orderId = orderId,
                        menuItemId = itemObj.optInt("menu_item_id", 0),
                        itemName = itemObj.optNonNullString("item_name", ""),
                        itemPrice = itemObj.optDouble("item_price", 0.0),
                        quantity = itemObj.optInt("quantity", 1),
                        subtotal = itemObj.optDouble("subtotal", 0.0),
                        completedQuantity = itemObj.optInt("completed_quantity", 0),
                        completedTimestamp = itemObj.optLong("completed_timestamp", 0L)
                    )
                )
            }
        }
        return OrderWithItems(order, items)
    }

    // ==================== CUSTOMER OPERATIONS ====================

    suspend fun saveCustomer(customer: CustomerEntity): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                if (customer.id != 0) put("id", customer.id)
                put("name", customer.name)
                put("contact_number", customer.contactNumber)
                put("total_orders", customer.totalOrders)
                put("total_spent", customer.totalSpent)
                put("last_order_timestamp", customer.lastOrderTimestamp)
                put("created_at", customer.createdAt)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/customers")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            executeMutation(req, "saveCustomer(${customer.name})").map { customer.name }
        } catch (e: Exception) {
            Log.e(TAG, "saveCustomer error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/customers?id=eq.$customerId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeMutation(req, "deleteCustomer($customerId)").map { "Deleted" }
        } catch (e: Exception) {
            Log.e(TAG, "deleteCustomer error: ${e.message}")
            Result.failure(e)
        }
    }

    fun listenToCustomers(): Flow<List<CustomerEntity>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/customers?order=name.asc")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val arr = executeJsonArrayGet(req, "listenToCustomers").getOrNull()
                    if (arr != null) {
                        val list = mutableListOf<CustomerEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                CustomerEntity(
                                    id = obj.optInt("id", 0),
                                    name = obj.optNonNullString("name", ""),
                                    contactNumber = obj.optNonNullString("contact_number", ""),
                                    totalOrders = obj.optInt("total_orders", 0),
                                    totalSpent = obj.optDouble("total_spent", 0.0),
                                    lastOrderTimestamp = obj.optLong("last_order_timestamp", 0L),
                                    createdAt = obj.optLong("created_at", 0L)
                                )
                            )
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToCustomers error: ${e.message}")
                }
            }
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    // ==================== SUPABASE STORAGE (Receipt Story Photos) ====================

    private val STORAGE_BUCKET = "receipt-stories"
    private val STORAGE_BASE_URL = "$SUPABASE_URL/storage/v1"

    /**
     * Upload a story photo to Supabase Storage.
     * @param fileName The file name to store as (e.g. "story_1693847283.jpg")
     * @param imageBytes JPEG image bytes
     * @return Public URL of the uploaded file on success
     */
    suspend fun uploadStoryPhoto(fileName: String, imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedFile = encodeQueryParam(fileName)
            val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$STORAGE_BUCKET/$encodedFile")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            val response = executeRequest(req) ?: return@withContext Result.failure(Exception("Upload failed: no response"))
            response.use { resp ->
                if (resp.isSuccessful) {
                    val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$encodedFile"
                    Log.d(TAG, "Story photo uploaded: $publicUrl")
                    Result.success(publicUrl)
                } else {
                    val errorBody = resp.body?.string().orEmpty().ifBlank { "HTTP ${resp.code}" }
                    Log.e(TAG, "Failed to upload story photo: $errorBody")
                    Result.failure(Exception("Upload failed: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadStoryPhoto error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * List all story photos in the Supabase Storage bucket.
     * @return List of file names
     */
    suspend fun listStoryPhotos(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val body = JSONObject().apply {
                put("prefix", "")
                put("limit", 100)
                put("offset", 0)
                put("sortBy", JSONObject().apply {
                    put("column", "created_at")
                    put("order", "desc")
                })
            }
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/list/$STORAGE_BUCKET")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val arrResult = executeJsonArrayGet(req, "listStoryPhotos")
            if (arrResult.isFailure) {
                return@withContext Result.failure(arrResult.exceptionOrNull() ?: Exception("Failed to list story photos"))
            }
            val arr = arrResult.getOrThrow()
            val fileNames = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optNonNullString("name", "")
                // Filter only image files, skip .emptyFolderPlaceholder
                if (name.isNotBlank() && !name.startsWith(".") &&
                    (name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) ||
                        name.endsWith(".png", ignoreCase = true))) {
                    fileNames.add(name)
                }
            }
            Result.success(fileNames)
        } catch (e: Exception) {
            Log.e(TAG, "listStoryPhotos error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete a story photo from Supabase Storage.
     */
    suspend fun deleteStoryPhoto(fileName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedFile = encodeQueryParam(fileName)
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$STORAGE_BUCKET/$encodedFile")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val response = executeRequest(req) ?: return@withContext Result.failure(Exception("Delete failed: no response"))
            response.use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "Story photo deleted: $fileName")
                    Result.success(true)
                } else {
                    val errorBody = resp.body?.string().orEmpty().ifBlank { "HTTP ${resp.code}" }
                    Result.failure(Exception("Delete failed: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteStoryPhoto error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Download a story photo from Supabase Storage as byte array.
     */
    suspend fun downloadStoryPhoto(fileName: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val encodedFile = encodeQueryParam(fileName)
            val req = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$encodedFile")
                .get()
                .build()
            val response = executeRequest(req) ?: return@withContext Result.failure(Exception("Download failed: no response"))
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null) {
                        Result.success(bytes)
                    } else {
                        Result.failure(Exception("Empty response body"))
                    }
                } else {
                    Result.failure(Exception("Download failed: HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadStoryPhoto error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the public URL for a story photo.
     */
    fun getStoryPhotoUrl(fileName: String): String {
        return "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/${encodeQueryParam(fileName)}"
    }

    // ==================== RECEIPT DESIGN CLOUD CONFIG ====================

    /**
     * Save receipt design configuration (text, footers, active story photo metadata) to Supabase.
     */
    suspend fun saveReceiptDesignConfig(jsonConfig: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val body = JSONObject().apply {
                put("key", "receipt_design_config")
                put("value", jsonConfig)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "saveReceiptDesignConfig").map { "Receipt design saved to cloud" }
        } catch (e: Exception) {
            Log.e(TAG, "saveReceiptDesignConfig error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetch receipt design configuration from Supabase sync_metadata table.
     */
    suspend fun fetchReceiptDesignConfig(): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata?key=eq.receipt_design_config&limit=1")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "fetchReceiptDesignConfig").getOrNull() ?: return@withContext null
            if (arr.length() > 0) {
                return@withContext arr.getJSONObject(0).optNonNullString("value", "").ifBlank { null }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "fetchReceiptDesignConfig error: ${e.message}")
            null
        }
    }

    /**
     * Fetch metadata by key from Supabase sync_metadata table.
     */
    suspend fun fetchSyncMetadata(key: String): String? = withContext(Dispatchers.IO) {
        if (!_isInitialized) {
            initialize()
        }
        try {
            val encodedKey = encodeQueryParam(key)
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata?key=eq.$encodedKey&limit=1")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "fetchSyncMetadata($key)").getOrNull() ?: return@withContext null
            if (arr.length() > 0) {
                val value = arr.getJSONObject(0).optNonNullString("value", "")
                if (value.isNotBlank()) return@withContext value
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "fetchSyncMetadata($key): ${e.message}")
            null
        }
    }

    /**
     * Save metadata by key to Supabase sync_metadata table.
     */
    suspend fun saveSyncMetadata(key: String, value: String): Result<String> = withContext(Dispatchers.IO) {
        if (!_isInitialized) {
            initialize()
        }
        try {
            val isoNow = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())
            val body = JSONObject().apply {
                put("key", key)
                put("value", value)
                put("updated_at", isoNow)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeMutation(req, "saveSyncMetadata($key)").map { "Metadata $key saved to cloud" }
        } catch (e: Exception) {
            Log.e(TAG, "saveSyncMetadata($key) error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetch remote GitHub update repository configuration from Supabase sync_metadata table.
     */
    suspend fun fetchUpdateRepoConfig(): String? = fetchSyncMetadata("github_update_repo")

    /**
     * Save remote GitHub update repository configuration to Supabase sync_metadata table.
     */
    suspend fun saveUpdateRepoConfig(repoConfig: String): Result<String> = saveSyncMetadata("github_update_repo", repoConfig)

    // ==================== VOICE ORDER CLOUD TELEMETRY ====================
    private val VOICE_ORDERS_BUCKET = "voice-orders"

    /**
     * Uploads voice order recording (.m4a) to Supabase Storage bucket `voice-orders`.
     */
    suspend fun uploadVoiceOrderAudio(fileName: String, audioBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Supabase not initialized"))
        try {
            val encodedFile = encodeQueryParam(fileName)
            val body = audioBytes.toRequestBody("audio/mp4".toMediaType())
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$VOICE_ORDERS_BUCKET/$encodedFile")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "audio/mp4")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            val response = executeRequest(req) ?: return@withContext Result.failure(Exception("Voice audio upload failed: no response"))
            response.use { resp ->
                if (resp.isSuccessful) {
                    val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$VOICE_ORDERS_BUCKET/$encodedFile"
                    Result.success(publicUrl)
                } else {
                    val err = resp.body?.string().orEmpty().ifBlank { "HTTP ${resp.code}" }
                    Result.failure(Exception("Voice audio upload failed: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads companion JSON diagnostics (.json) to Supabase Storage bucket `voice-orders`.
     */
    suspend fun uploadVoiceOrderDiagnostics(fileName: String, jsonDiagnostics: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Supabase not initialized"))
        try {
            val encodedFile = encodeQueryParam(fileName)
            val body = jsonDiagnostics.toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$VOICE_ORDERS_BUCKET/$encodedFile")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            val response = executeRequest(req) ?: return@withContext Result.failure(Exception("Diagnostics upload failed: no response"))
            response.use { resp ->
                if (resp.isSuccessful) {
                    val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$VOICE_ORDERS_BUCKET/$encodedFile"
                    Result.success(publicUrl)
                } else {
                    val err = resp.body?.string().orEmpty().ifBlank { "HTTP ${resp.code}" }
                    Result.failure(Exception("Diagnostics upload failed: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remote update policy model to enforce mandatory app updates across all staff devices.
     */
    data class AppUpdatePolicy(
        val forceUpdate: Boolean = false,
        val minVersionCode: Int = 0,
        val minVersionName: String = "",
        val title: String = "",
        val message: String = "",
        val apkDownloadUrl: String = "",
        val apkFileName: String = "",
        val apkSizeBytes: Long = 0L
    )

    /**
     * Fetches mandatory update policy from Supabase sync_metadata.
     */
    suspend fun fetchAppUpdatePolicy(): AppUpdatePolicy? = withContext(Dispatchers.IO) {
        if (!_isInitialized) {
            initialize()
        }
        try {
            // First check if github_update_repo JSON contains force_update or min_version_code
            val repoConfig = fetchUpdateRepoConfig()
            if (!repoConfig.isNullOrBlank() && repoConfig.startsWith("{")) {
                val json = JSONObject(repoConfig)
                val force = json.optBoolean("force_update", false)
                val minCode = json.optInt("min_version_code", 0)
                if (force || minCode > 0) {
                    return@withContext AppUpdatePolicy(
                        forceUpdate = force,
                        minVersionCode = minCode,
                        minVersionName = json.optNonNullString("min_version_name", ""),
                        title = json.optNonNullString("title", "Mandatory Update Required"),
                        message = json.optNonNullString("message", "A new version of FILTR Billing is required. Please update to continue taking orders."),
                        apkDownloadUrl = json.optNonNullString("apk_download_url", ""),
                        apkFileName = json.optNonNullString("apk_file_name", "filtr-billing-v1.3.apk"),
                        apkSizeBytes = json.optLong("apk_size_bytes", 0L)
                    )
                }
            }

            // Also check dedicated app_update_policy key
            val policyRaw = fetchSyncMetadata("app_update_policy")
            if (!policyRaw.isNullOrBlank()) {
                val json = JSONObject(policyRaw)
                return@withContext AppUpdatePolicy(
                    forceUpdate = json.optBoolean("force_update", false),
                    minVersionCode = json.optInt("min_version_code", 0),
                    minVersionName = json.optNonNullString("min_version_name", ""),
                    title = json.optNonNullString("title", "Mandatory Update Required"),
                    message = json.optNonNullString("message", "A new version of FILTR Billing is required. Please update to continue taking orders."),
                    apkDownloadUrl = json.optNonNullString("apk_download_url", ""),
                    apkFileName = json.optNonNullString("apk_file_name", "filtr-billing-v1.3.apk"),
                    apkSizeBytes = json.optLong("apk_size_bytes", 0L)
                )
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppUpdatePolicy failed: ${e.message}")
            null
        }
    }

    // ==================== CHECKLIST CLOUD SYNC (via sync_metadata) ====================

    suspend fun fetchCloudChecklistCompletions(dateKey: String): String? = withContext(Dispatchers.IO) {
        fetchSyncMetadata("checklist_state_$dateKey")
    }

    suspend fun saveCloudChecklistCompletions(dateKey: String, json: String): Result<String> = withContext(Dispatchers.IO) {
        saveSyncMetadata("checklist_state_$dateKey", json)
    }

    suspend fun fetchCloudCustomTasks(): String? = withContext(Dispatchers.IO) {
        fetchSyncMetadata("checklist_custom_tasks")
    }

    suspend fun saveCloudCustomTasks(json: String): Result<String> = withContext(Dispatchers.IO) {
        saveSyncMetadata("checklist_custom_tasks", json)
    }

    // ==================== DEEPGRAM REMOTE CONFIG ====================

    data class DeepgramConfig(
        val apiKey: String = "",
        val model: String = "nova-3",
        val language: String = "multi"
    )

    suspend fun fetchDeepgramConfig(): DeepgramConfig? = withContext(Dispatchers.IO) {
        try {
            val raw = fetchSyncMetadata("deepgram_config") ?: return@withContext null
            if (!raw.startsWith("{")) return@withContext null
            val json = JSONObject(raw)
            DeepgramConfig(
                apiKey = json.optNonNullString("api_key", ""),
                model = json.optNonNullString("model", "nova-3").ifBlank { "nova-3" },
                language = json.optNonNullString("language", "multi").ifBlank { "multi" }
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchDeepgramConfig failed: ${e.message}")
            null
        }
    }

    // ==================== DEVICE ACCESS REGISTRY & PASSCODE POLICY ====================

    data class AccessPolicyConfig(
        val adminPasscode: String = "FILTR007",
        val staffPasscode: String = "1234",
        val passcodeEpoch: Int = 1,
        val requireOwnerApprovalForAdmin: Boolean = false
    )

    data class DeviceRegistration(
        val registryKey: String,
        val deviceId: String,
        val appRole: String, // "ADMIN" or "STAFF"
        val operatorName: String,
        val deviceModel: String,
        val androidVersion: String,
        val appVersionName: String,
        val appVersionCode: Int,
        val status: String, // "ACTIVE", "PENDING", "REVOKED"
        val passcodeEpoch: Int = 1,
        val firstSeenMs: Long = System.currentTimeMillis(),
        val lastSeenMs: Long = System.currentTimeMillis()
    )

    suspend fun fetchAccessPolicyConfig(): AccessPolicyConfig = withContext(Dispatchers.IO) {
        try {
            val raw = fetchSyncMetadata("access_policy_config")
            if (!raw.isNullOrBlank() && raw.startsWith("{")) {
                val json = JSONObject(raw)
                return@withContext AccessPolicyConfig(
                    adminPasscode = json.optNonNullString("admin_passcode", "FILTR007"),
                    staffPasscode = json.optNonNullString("staff_passcode", "1234"),
                    passcodeEpoch = json.optInt("passcode_epoch", 1),
                    requireOwnerApprovalForAdmin = json.optBoolean("require_owner_approval_for_admin", false)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAccessPolicyConfig fallback: ${e.message}")
        }
        AccessPolicyConfig()
    }

    suspend fun saveAccessPolicyConfig(policy: AccessPolicyConfig): Result<String> = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("admin_passcode", policy.adminPasscode)
            put("staff_passcode", policy.staffPasscode)
            put("passcode_epoch", policy.passcodeEpoch)
            put("require_owner_approval_for_admin", policy.requireOwnerApprovalForAdmin)
        }
        saveSyncMetadata("access_policy_config", json.toString())
    }

    private fun parseDeviceRegistration(key: String, rawValue: String): DeviceRegistration? {
        return try {
            val json = JSONObject(rawValue)
            val devId = json.optNonNullString("device_id", "").ifBlank {
                key.removePrefix("device_reg_").substringAfterLast("_")
            }
            DeviceRegistration(
                registryKey = key,
                deviceId = devId,
                appRole = json.optNonNullString("app_role", "STAFF"),
                operatorName = json.optNonNullString("operator_name", "Unknown Device"),
                deviceModel = json.optNonNullString("device_model", "Android"),
                androidVersion = json.optNonNullString("android_version", ""),
                appVersionName = json.optNonNullString("app_version_name", ""),
                appVersionCode = json.optInt("app_version_code", 0),
                status = json.optNonNullString("status", "ACTIVE"),
                passcodeEpoch = json.optInt("passcode_epoch", 1),
                firstSeenMs = json.optLong("first_seen_ms", System.currentTimeMillis()),
                lastSeenMs = json.optLong("last_seen_ms", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun deviceRegistrationToJson(reg: DeviceRegistration): String {
        return JSONObject().apply {
            put("device_id", reg.deviceId)
            put("app_role", reg.appRole)
            put("operator_name", reg.operatorName)
            put("device_model", reg.deviceModel)
            put("android_version", reg.androidVersion)
            put("app_version_name", reg.appVersionName)
            put("app_version_code", reg.appVersionCode)
            put("status", reg.status)
            put("passcode_epoch", reg.passcodeEpoch)
            put("first_seen_ms", reg.firstSeenMs)
            put("last_seen_ms", reg.lastSeenMs)
        }.toString()
    }

    /**
     * Registers or updates device heartbeat while preserving remote REVOKED/PENDING status
     * set by the Owner/Admin. Client heartbeats can never self-promote PENDING -> ACTIVE.
     */
    suspend fun syncDeviceHeartbeat(
        deviceId: String,
        appRole: String,
        operatorName: String,
        deviceModel: String,
        androidVersion: String,
        appVersionName: String,
        appVersionCode: Int,
        localStatus: String,
        passcodeEpoch: Int
    ): DeviceRegistration? = withContext(Dispatchers.IO) {
        if (!_isInitialized) initialize()
        val key = "device_reg_$deviceId"
        return@withContext try {
            val existingRaw = fetchSyncMetadata(key)
                ?: fetchSyncMetadata("device_reg_${appRole.lowercase()}_$deviceId")
            val existing = if (!existingRaw.isNullOrBlank()) parseDeviceRegistration(key, existingRaw) else null

            // Remote REVOKED and PENDING always take precedence; clients cannot self-promote PENDING -> ACTIVE
            val resolvedStatus = when {
                existing?.status == "REVOKED" -> "REVOKED"
                existing?.status == "PENDING" -> "PENDING"
                existing?.status == "ACTIVE" && localStatus == "PENDING" -> "ACTIVE"
                else -> localStatus
            }

            val updated = DeviceRegistration(
                registryKey = key,
                deviceId = deviceId,
                appRole = appRole,
                operatorName = operatorName.ifBlank { existing?.operatorName ?: "Unassigned Device" },
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                status = resolvedStatus,
                passcodeEpoch = passcodeEpoch,
                firstSeenMs = existing?.firstSeenMs ?: System.currentTimeMillis(),
                lastSeenMs = System.currentTimeMillis()
            )
            val saveRes = saveSyncMetadata(key, deviceRegistrationToJson(updated))
            if (saveRes.isFailure) {
                Log.w(TAG, "syncDeviceHeartbeat save failed: ${saveRes.exceptionOrNull()?.message}")
                null
            } else {
                updated
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncDeviceHeartbeat failed: ${e.message}")
            null
        }
    }

    suspend fun fetchAllRegisteredDevices(): List<DeviceRegistration> = withContext(Dispatchers.IO) {
        if (!_isInitialized) initialize()
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata?key=like.device_reg_%25&order=updated_at.desc")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val arr = executeJsonArrayGet(req, "fetchAllRegisteredDevices").getOrNull() ?: return@withContext emptyList()
            val deviceMap = mutableMapOf<String, DeviceRegistration>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val key = obj.optNonNullString("key", "")
                val value = obj.optNonNullString("value", "")
                parseDeviceRegistration(key, value)?.let { reg ->
                    val dedupId = reg.deviceId.ifBlank { key }
                    val current = deviceMap[dedupId]
                    if (current == null || reg.lastSeenMs > current.lastSeenMs) {
                        deviceMap[dedupId] = reg
                    }
                }
            }
            deviceMap.values.sortedByDescending { it.lastSeenMs }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllRegisteredDevices failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun setDeviceAccessStatus(registryKey: String, newStatus: String): Result<String> = withContext(Dispatchers.IO) {
        if (!_isInitialized) initialize()
        try {
            val existingRaw = fetchSyncMetadata(registryKey) ?: return@withContext Result.failure(Exception("Device not found"))
            val existing = parseDeviceRegistration(registryKey, existingRaw) ?: return@withContext Result.failure(Exception("Invalid device record"))
            val updated = existing.copy(status = newStatus)
            saveSyncMetadata(registryKey, deviceRegistrationToJson(updated))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
