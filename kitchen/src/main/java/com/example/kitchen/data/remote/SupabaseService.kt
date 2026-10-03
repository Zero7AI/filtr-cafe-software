package com.example.kitchen.data.remote

import android.util.Log
import com.example.kitchen.data.entity.*
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
import java.util.concurrent.TimeUnit

class SupabaseService private constructor() {

    companion object {
        private const val TAG = "KitchenSupabase"
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
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

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

    suspend fun checkReachability(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) initialize()
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/menu_items?select=id&limit=1")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val response = executeRequest(req)
            val isSuccess = response?.isSuccessful == true
            response?.close()
            isSuccess
        } catch (e: Exception) {
            false
        }
    }

    // ==================== ORDER OPERATIONS ====================

    suspend fun saveOrder(orderWithItems: OrderWithItems): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
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
            executeRequest(upsertOrderReq)?.close()

            val deleteItemsReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.${orderWithItems.order.orderId}")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeRequest(deleteItemsReq)?.close()

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
                executeRequest(insertItemsReq)?.close()
            }
            Result.success("Order saved")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderFields(orderId: String, fields: Map<String, Any>): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
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
                .url("$SUPABASE_URL/rest/v1/orders?order_id=eq.$orderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(req)?.close()
            Result.success("Order fields updated")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markOrderMade(orderId: String, completedTimestamp: Long = System.currentTimeMillis()): Result<String> {
        return updateOrderFields(orderId, mapOf(
            "isMade" to true,
            "completedTimestamp" to completedTimestamp
        ))
    }

    suspend fun markOrderDelivered(orderId: String): Result<String> {
        return updateOrderFields(orderId, mapOf(
            "isDelivered" to true
        ))
    }

    fun listenToOrders(intervalMs: Long = 5_000L): Flow<List<OrderWithItems>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    // Sync orders from last 3 days
                    val syncWindowMs = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/orders?select=*,order_items(*)&timestamp=gte.$syncWindowMs&order=timestamp.desc")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
                    response?.close()
                    if (bodyStr != null) {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<OrderWithItems>()
                        for (i in 0 until arr.length()) {
                            list.add(jsonToOrderWithItems(arr.getJSONObject(i)))
                        }
                        emit(list)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "listenToOrders error: ${e.message}")
                }
            }
            delay(intervalMs)
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
            val response = executeRequest(req)
            val bodyStr = response?.body?.string()
            response?.close()
            val seq = bodyStr?.toIntOrNull() ?: 1000
            Result.success(seq)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTodayMaxOrderSequence(datePart: String): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext 1000
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?select=order_id&order_id=like.FLT-$datePart-%25&order=order_id.desc&limit=1")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val response = executeRequest(req)
            val bodyStr = response?.body?.string()
            response?.close()
            if (bodyStr != null) {
                val arr = JSONArray(bodyStr)
                if (arr.length() > 0) {
                    val orderId = arr.getJSONObject(0).optString("order_id", "")
                    val seqStr = orderId.substringAfterLast("-", "")
                    return@withContext seqStr.toIntOrNull() ?: 1000
                }
            }
            1000
        } catch (e: Exception) {
            1000
        }
    }

    // ==================== MENU ITEM OPERATIONS ====================

    suspend fun fetchMenuItems(): List<MenuItemEntity> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/menu_items?order=category.asc,name.asc")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val response = executeRequest(req)
            val bodyStr = response?.body?.string()
            response?.close()
            if (bodyStr != null) {
                val arr = JSONArray(bodyStr)
                val list = mutableListOf<MenuItemEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        MenuItemEntity(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", ""),
                            category = obj.optString("category", ""),
                            price = obj.optDouble("price", 0.0),
                            description = obj.optString("description", ""),
                            isAvailable = obj.optBoolean("is_available", true)
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "fetchMenuItems error: ${e.message}")
            emptyList()
        }
    }

    fun listenToMenuItems(intervalMs: Long = 30_000L): Flow<List<MenuItemEntity>> = flow {
        while (true) {
            val items = fetchMenuItems()
            if (items.isNotEmpty()) {
                emit(items)
            }
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

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
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(req)?.close()
            Result.success(customer.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== JSON CONVERTER ====================

    private fun jsonToOrderWithItems(json: JSONObject): OrderWithItems {
        val orderId = json.optString("order_id")
        val order = OrderEntity(
            orderId = orderId,
            customerName = json.optString("customer_name", "Walk-in Customer"),
            contactNumber = json.optString("contact_number", ""),
            totalAmount = json.optDouble("total_amount", 0.0),
            timestamp = json.optLong("timestamp", 0L),
            isMade = json.optBoolean("is_made", false),
            isPaid = json.optBoolean("is_paid", false),
            isDelivered = json.optBoolean("is_delivered", false),
            syncStatus = "SYNCED",
            notes = json.optString("notes", ""),
            paymentMode = json.optString("payment_mode", "UNPAID"),
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
                        itemName = itemObj.optString("item_name", ""),
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
}
