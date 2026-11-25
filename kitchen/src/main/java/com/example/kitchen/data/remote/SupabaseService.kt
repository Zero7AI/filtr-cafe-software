package com.example.kitchen.data.remote

import android.util.Log
import com.example.kitchen.BuildConfig
import com.example.kitchen.data.entity.CustomerEntity
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.CoroutineDispatcher
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

class SupabaseService internal constructor(
    private val baseUrl: String = SUPABASE_URL,
    clientOverride: OkHttpClient? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "KitchenSupabase"
        private const val SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
        private const val SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
        private const val PAGE_SIZE = 200
        private const val MAX_PAGINATION_OFFSET = 2000

        @Volatile
        private var INSTANCE: SupabaseService? = null

        fun getInstance(): SupabaseService = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SupabaseService().also { INSTANCE = it }
        }
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private var client: OkHttpClient? = clientOverride

    @Volatile
    private var _isInitialized = clientOverride != null
    val isInitialized: Boolean get() = _isInitialized

    fun initialize(): Boolean {
        if (_isInitialized) return true
        return try {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
                builder.addInterceptor(logging)
            }
            client = builder.build()

            _isInitialized = true
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Supabase initialized successfully")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Supabase initialization failed: ${e.message}")
            false
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun Request.Builder.addSupabaseHeaders(): Request.Builder =
        addHeader("apikey", SERVICE_ROLE_KEY)
            .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")

    private fun executeRequest(request: Request): Response? {
        return try {
            client?.newCall(request)?.execute()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Request Failed: ${e.message}")
            null
        }
    }

    private fun executeWrite(request: Request, action: String): Result<String> {
        val response = executeRequest(request)
            ?: return Result.failure(Exception("$action failed: no response"))
        return response.use { resp ->
            if (resp.isSuccessful) {
                Result.success("$action succeeded")
            } else {
                Result.failure(Exception("$action failed: HTTP ${resp.code}"))
            }
        }
    }

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        val value = optString(key, fallback)
        return if (value.equals("null", ignoreCase = true)) fallback else value
    }

    suspend fun checkReachability(): Boolean = withContext(ioDispatcher) {
        if (!isInitialized) initialize()
        try {
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/menu_items?select=id&limit=1")
                .addSupabaseHeaders()
                .get()
                .build()
            executeRequest(req)?.use { it.isSuccessful } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // ==================== ORDER OPERATIONS ====================

    suspend fun saveOrder(orderWithItems: OrderWithItems): Result<String> = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val orderId = orderWithItems.order.orderId
            val orderObj = JSONObject().apply {
                put("order_id", orderId)
                put("customer_name", orderWithItems.order.customerName)
                put("contact_number", orderWithItems.order.contactNumber)
                put("total_amount", orderWithItems.order.totalAmount)
                put("timestamp", orderWithItems.order.timestamp)
                put("is_made", orderWithItems.order.isMade)
                put("is_paid", orderWithItems.order.isPaid)
                put("is_delivered", orderWithItems.order.isDelivered)
                put("sync_status", "SYNCED")
                put("notes", orderWithItems.order.notes)
                put("payment_mode", orderWithItems.order.paymentMode)
                put("completed_timestamp", orderWithItems.order.completedTimestamp)
            }

            val upsertOrderReq = Request.Builder()
                .url("$baseUrl/rest/v1/orders")
                .addSupabaseHeaders()
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(orderObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val upsertResult = executeWrite(upsertOrderReq, "Order upsert")
            if (upsertResult.isFailure) return@withContext upsertResult

            val deleteItemsReq = Request.Builder()
                .url("$baseUrl/rest/v1/order_items?order_id=eq.${urlEncode(orderId)}")
                .addSupabaseHeaders()
                .delete()
                .build()
            val deleteResult = executeWrite(deleteItemsReq, "Order items delete")
            if (deleteResult.isFailure) return@withContext deleteResult

            if (orderWithItems.items.isNotEmpty()) {
                val itemsArr = JSONArray()
                for (item in orderWithItems.items) {
                    itemsArr.put(JSONObject().apply {
                        put("order_id", orderId)
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
                    .url("$baseUrl/rest/v1/order_items")
                    .addSupabaseHeaders()
                    .addHeader("Prefer", "return=minimal")
                    .post(itemsArr.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val insertResult = executeWrite(insertItemsReq, "Order items insert")
                if (insertResult.isFailure) return@withContext insertResult
            }
            Result.success("Order saved")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderFields(orderId: String, fields: Map<String, Any>): Result<String> = withContext(ioDispatcher) {
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
                    "customerName" -> "customer_name"
                    "contactNumber" -> "contact_number"
                    "totalAmount" -> "total_amount"
                    else -> k
                }
                json.put(snakeKey, v)
            }
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/orders?order_id=eq.${urlEncode(orderId)}")
                .addSupabaseHeaders()
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeWrite(req, "Order fields update")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderItemCompletion(
        orderId: String,
        itemName: String,
        completedQuantity: Int,
        completedTimestamp: Long
    ): Result<String> = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply {
                put("completed_quantity", completedQuantity)
                put("completed_timestamp", completedTimestamp)
            }
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/order_items?order_id=eq.${urlEncode(orderId)}&item_name=eq.${urlEncode(itemName)}")
                .addSupabaseHeaders()
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeWrite(req, "Order item completion")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markOrderMade(orderId: String, completedTimestamp: Long = System.currentTimeMillis()): Result<String> {
        return updateOrderFields(
            orderId,
            mapOf(
                "isMade" to true,
                "completedTimestamp" to completedTimestamp,
                "syncStatus" to "SYNCED"
            )
        )
    }

    suspend fun markOrderDelivered(orderId: String): Result<String> {
        return updateOrderFields(
            orderId,
            mapOf(
                "isDelivered" to true,
                "syncStatus" to "SYNCED"
            )
        )
    }

    internal suspend fun fetchOrdersSince(syncWindowMs: Long): List<OrderWithItems>? = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext null
        try {
            val allOrders = mutableListOf<OrderWithItems>()
            var offset = 0
            while (true) {
                val req = Request.Builder()
                    .url(
                        "$baseUrl/rest/v1/orders?select=*,order_items(*)" +
                            "&timestamp=gte.$syncWindowMs&order=timestamp.desc&limit=$PAGE_SIZE&offset=$offset"
                    )
                    .addSupabaseHeaders()
                    .get()
                    .build()
                val pageBody = executeRequest(req)?.use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: return@withContext null

                val arr = JSONArray(pageBody)
                for (i in 0 until arr.length()) {
                    allOrders.add(jsonToOrderWithItems(arr.getJSONObject(i)))
                }
                if (arr.length() < PAGE_SIZE || offset >= MAX_PAGINATION_OFFSET) break
                offset += PAGE_SIZE
            }
            allOrders
        } catch (e: Exception) {
            Log.e(TAG, "fetchOrdersSince error: ${e.message}")
            null
        }
    }

    fun listenToOrders(intervalMs: Long = 5_000L): Flow<List<OrderWithItems>> = flow {
        var cycle = 0
        while (true) {
            if (isInitialized) {
                cycle++
                val isFullReconcile = cycle % 20 == 0
                val syncWindowMs = if (isFullReconcile) {
                    System.currentTimeMillis() - (24L * 60 * 60 * 1000)
                } else {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                val orders = fetchOrdersSince(syncWindowMs)
                if (orders != null) {
                    emit(orders)
                }
            }
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)

    // ==================== SEQUENCE GENERATION ====================

    suspend fun getNextOrderSequence(datePart: String): Result<Int> = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val json = JSONObject().apply { put("p_date_part", datePart) }
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/get_next_order_sequence")
                .addSupabaseHeaders()
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(req)?.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Sequence RPC failed: HTTP ${resp.code}"))
                }
                val bodyStr = resp.body?.string()?.trim()
                val seq = bodyStr?.toIntOrNull()
                    ?: return@withContext Result.failure(Exception("Invalid sequence response: $bodyStr"))
                Result.success(seq)
            } ?: Result.failure(Exception("Sequence RPC failed: no response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTodayMaxOrderSequence(datePart: String): Int = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext 1000
        try {
            var maxSeq = 1000
            var offset = 0
            while (true) {
                val req = Request.Builder()
                    .url(
                        "$baseUrl/rest/v1/orders?select=order_id" +
                            "&order_id=like.FLT-${urlEncode(datePart)}-%25&limit=$PAGE_SIZE&offset=$offset"
                    )
                    .addSupabaseHeaders()
                    .get()
                    .build()
                val bodyStr = executeRequest(req)?.use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                } ?: break

                val arr = JSONArray(bodyStr)
                for (i in 0 until arr.length()) {
                    val orderId = arr.getJSONObject(i).optNonNullString("order_id", "")
                    val seq = orderId.substringAfterLast("-", "").toIntOrNull()
                    if (seq != null && seq > maxSeq) {
                        maxSeq = seq
                    }
                }
                if (arr.length() < PAGE_SIZE || offset >= MAX_PAGINATION_OFFSET) break
                offset += PAGE_SIZE
            }
            maxSeq
        } catch (_: Exception) {
            1000
        }
    }

    // ==================== MENU ITEM OPERATIONS ====================

    internal suspend fun fetchMenuItemsOrNull(): List<MenuItemEntity>? = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext null
        try {
            val list = mutableListOf<MenuItemEntity>()
            var offset = 0
            while (true) {
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/menu_items?order=category.asc,name.asc&limit=$PAGE_SIZE&offset=$offset")
                    .addSupabaseHeaders()
                    .get()
                    .build()
                val bodyStr = executeRequest(req)?.use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                } ?: return@withContext null

                val arr = JSONArray(bodyStr)
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
                if (arr.length() < PAGE_SIZE || offset >= MAX_PAGINATION_OFFSET) break
                offset += PAGE_SIZE
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "fetchMenuItems error: ${e.message}")
            null
        }
    }

    suspend fun fetchMenuItems(): List<MenuItemEntity> = fetchMenuItemsOrNull() ?: emptyList()

    fun listenToMenuItems(intervalMs: Long = 30_000L): Flow<List<MenuItemEntity>> = flow {
        while (true) {
            val items = fetchMenuItemsOrNull()
            if (!items.isNullOrEmpty()) {
                emit(items)
            }
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)

    // ==================== CUSTOMER OPERATIONS ====================

    suspend fun saveCustomer(customer: CustomerEntity): Result<String> = withContext(ioDispatcher) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        val trimmedName = customer.name.trim()
        if (trimmedName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Customer name is blank"))
        try {
            val json = JSONObject().apply {
                put("name", trimmedName)
                put("contact_number", customer.contactNumber.trim())
                put("total_orders", customer.totalOrders)
                put("total_spent", customer.totalSpent)
                put("last_order_timestamp", customer.lastOrderTimestamp)
                put("created_at", customer.createdAt)
            }
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/customers?on_conflict=name")
                .addSupabaseHeaders()
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeWrite(req, "Customer save").map { trimmedName }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== JSON CONVERTER ====================

    internal fun jsonToOrderWithItems(json: JSONObject): OrderWithItems {
        val orderId = json.optNonNullString("order_id", "")
        val order = OrderEntity(
            orderId = orderId,
            customerName = json.optNonNullString("customer_name", "Walk-in Customer").ifBlank { "Walk-in Customer" },
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
}
