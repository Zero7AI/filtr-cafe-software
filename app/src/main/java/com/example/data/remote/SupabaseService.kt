package com.example.data.remote

import android.util.Log
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
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
            val upsertResp = executeRequest(upsertOrderReq)
            if (upsertResp?.isSuccessful != true) {
                val code = upsertResp?.code
                Log.w(TAG, "saveOrder upsert failed: $code")
                upsertResp?.close()
                return@withContext Result.failure(Exception("Supabase order upsert failed with HTTP $code"))
            }
            upsertResp?.close()

            val deleteItemsReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.${orderWithItems.order.orderId}")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val delResp = executeRequest(deleteItemsReq)
            if (delResp?.isSuccessful != true) {
                Log.w(TAG, "saveOrder deleteItems failed: ${delResp?.code}")
            }
            delResp?.close()

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
                val insResp = executeRequest(insertItemsReq)
                if (insResp?.isSuccessful != true) {
                    Log.w(TAG, "saveOrder insertItems failed: ${insResp?.code}")
                }
                insResp?.close()
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
            val resp = executeRequest(req)
            val isSuccess = resp?.isSuccessful == true
            val code = resp?.code
            resp?.close()
            if (isSuccess) {
                Result.success("Order fields updated")
            } else {
                Result.failure(Exception("Order fields update failed with HTTP $code"))
            }
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
            val encodedName = java.net.URLEncoder.encode(itemName, "UTF-8").replace("+", "%20")
            val json = JSONObject().apply {
                put("completed_quantity", completedQuantity)
                put("completed_timestamp", completedTimestamp)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.$orderId&item_name=eq.$encodedName")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .patch(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = executeRequest(req)
            val success = resp?.isSuccessful == true
            resp?.close()
            if (success) {
                Result.success("Order item completion updated")
            } else {
                Result.failure(Exception("Update failed with code ${resp?.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteOrder(orderId: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val delItems = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/order_items?order_id=eq.$orderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeRequest(delItems)?.close()

            val delOrder = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?order_id=eq.$orderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeRequest(delOrder)?.close()
            Result.success("Order deleted")
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
            val response = executeRequest(req)
            val bodyStr = response?.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(bodyStr)
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
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/orders?select=*,order_items(*)&order_id=eq.$orderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val response = executeRequest(req)
            val bodyStr = response?.body?.string() ?: return@withContext null
            val arr = JSONArray(bodyStr)
            if (arr.length() > 0) {
                jsonToOrderWithItems(arr.getJSONObject(0))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getOrderWithItems failed: ${e.message}")
            null
        }
    }

    fun listenToOrders(): Flow<List<OrderWithItems>> = flow {
        while (true) {
            if (isInitialized) {
                try {
                    // Sync active recent orders (last 7 days, up to 300 orders) sorted newest-first to ensure live cross-device sync
                    val syncWindowMs = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/orders?select=*,order_items(*)&timestamp=gte.$syncWindowMs&order=timestamp.desc&limit=300")
                        .addHeader("apikey", SERVICE_ROLE_KEY)
                        .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                        .get()
                        .build()
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
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
            delay(4_000) // 4 seconds for responsive multi-device sync
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
            val bodyStr = response?.body?.string() ?: return@withContext Result.failure(Exception("No response"))
            val seq = bodyStr.toIntOrNull() ?: 1000
            Result.success(seq)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentMaxSequence(datePart: String): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext 1000
        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata?key=eq.$datePart")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .get()
                .build()
            val response = executeRequest(req)
            val bodyStr = response?.body?.string()
            if (bodyStr != null) {
                val arr = JSONArray(bodyStr)
                if (arr.length() > 0) {
                    val valueStr = arr.getJSONObject(0).optString("value")
                    return@withContext valueStr.toIntOrNull() ?: 1000
                }
            }
            1000
        } catch (e: Exception) {
            1000
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
            executeRequest(req)?.close()
            Result.success("Menu item saved")
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
            executeRequest(req)?.close()
            Result.success("Menu item deleted")
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
            executeRequest(req)?.close()
            Result.success("All menu items synced")
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
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
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
            executeRequest(req)?.close()
            Result.success("UPI account saved")
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
            executeRequest(req)?.close()
            Result.success("UPI account deleted")
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
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
                    if (bodyStr != null) {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<UpiAccountEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                UpiAccountEntity(
                                    id = obj.optInt("id", 0),
                                    vpa = obj.optString("vpa", ""),
                                    payeeName = obj.optString("payee_name", ""),
                                    isDefault = obj.optBoolean("is_default", false),
                                    note = obj.optString("note", "")
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
            executeRequest(req)?.close()
            Result.success("Inventory item saved")
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
            executeRequest(req)?.close()
            Result.success("Inventory item deleted")
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
            executeRequest(req)?.close()
            Result.success("Inventory stock updated")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveInventoryOrder(order: InventoryOrderEntity, items: List<InventoryOrderItemEntity>): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
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
            executeRequest(reqOrder)?.close()

            val delItemsReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_order_items?order_id=eq.${order.orderId}")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeRequest(delItemsReq)?.close()

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
                executeRequest(reqItems)?.close()
            }
            Result.success("Inventory order saved")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryOrder(orderId: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val reqItems = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_order_items?order_id=eq.$orderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeRequest(reqItems)?.close()

            val reqOrder = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/inventory_orders?order_id=eq.$orderId")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            executeRequest(reqOrder)?.close()
            Result.success("Inventory order deleted")
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
            executeRequest(req)?.close()
            Result.success("Inventory usage saved")
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
            executeRequest(req)?.close()
            Result.success("Inventory usage deleted")
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
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
                    if (bodyStr != null) {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<Pair<InventoryOrderEntity, List<InventoryOrderItemEntity>>>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val order = InventoryOrderEntity(
                                orderId = obj.optString("order_id"),
                                timestamp = obj.optLong("timestamp", 0L),
                                supplierName = obj.optString("supplier_name", ""),
                                totalAmount = obj.optDouble("total_amount", 0.0),
                                notes = obj.optString("notes", ""),
                                syncStatus = "SYNCED"
                            )
                            val items = mutableListOf<InventoryOrderItemEntity>()
                            val itemsArr = obj.optJSONArray("inventory_order_items")
                            if (itemsArr != null) {
                                for (j in 0 until itemsArr.length()) {
                                    val itemObj = itemsArr.getJSONObject(j)
                                    items.add(InventoryOrderItemEntity(
                                        id = itemObj.optInt("id", 0),
                                        orderId = obj.optString("order_id"),
                                        itemName = itemObj.optString("item_name", ""),
                                        quantity = itemObj.optDouble("quantity", 0.0),
                                        unit = itemObj.optString("unit", "pcs"),
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
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
                    if (bodyStr != null) {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<InventoryItemEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(InventoryItemEntity(
                                id = obj.optInt("id", 0),
                                name = obj.optString("name", ""),
                                category = obj.optString("category", ""),
                                unit = obj.optString("unit", "kg"),
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
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
                    if (bodyStr != null) {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<InventoryUsageEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                InventoryUsageEntity(
                                    id = obj.optInt("id", 0),
                                    menuItemId = obj.optInt("menu_item_id", 0),
                                    menuItemName = obj.optString("menu_item_name", ""),
                                    inventoryItemId = obj.optInt("inventory_item_id", 0),
                                    inventoryItemName = obj.optString("inventory_item_name", ""),
                                    quantityUsed = obj.optDouble("quantity_used", 0.0),
                                    unit = obj.optString("unit", "g")
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
        val orderId = json.optString("order_id")
        val order = OrderEntity(
            orderId = orderId,
            customerName = json.optString("customer_name", "Walk-in"),
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
            executeRequest(req)
            Result.success(customer.name)
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
            executeRequest(req)
            Result.success("Deleted")
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
                    val response = executeRequest(req)
                    val bodyStr = response?.body?.string()
                    if (bodyStr != null) {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<CustomerEntity>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                CustomerEntity(
                                    id = obj.optInt("id", 0),
                                    name = obj.optString("name", ""),
                                    contactNumber = obj.optString("contact_number", ""),
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
            val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$STORAGE_BUCKET/$fileName")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            val response = executeRequest(req)
            if (response?.isSuccessful == true) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$fileName"
                Log.d(TAG, "Story photo uploaded: $publicUrl")
                Result.success(publicUrl)
            } else {
                val errorBody = response?.body?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to upload story photo: $errorBody")
                Result.failure(Exception("Upload failed: $errorBody"))
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
            val response = executeRequest(req)
            val bodyStr = response?.body?.string()
            if (bodyStr != null) {
                val arr = JSONArray(bodyStr)
                val fileNames = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name", "")
                    // Filter only image files, skip .emptyFolderPlaceholder
                    if (name.isNotBlank() && !name.startsWith(".") &&
                        (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
                        fileNames.add(name)
                    }
                }
                Result.success(fileNames)
            } else {
                Result.failure(Exception("No response"))
            }
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
            val body = JSONObject().apply {
                put("prefixes", org.json.JSONArray().apply { put(fileName) })
            }
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$STORAGE_BUCKET/$fileName")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .delete()
                .build()
            val response = executeRequest(req)
            if (response?.isSuccessful == true) {
                Log.d(TAG, "Story photo deleted: $fileName")
                Result.success(true)
            } else {
                val errorBody = response?.body?.string() ?: "Unknown error"
                Result.failure(Exception("Delete failed: $errorBody"))
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
            val req = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$fileName")
                .get()
                .build()
            val response = executeRequest(req)
            if (response?.isSuccessful == true) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    Result.success(bytes)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("Download failed: ${response?.code}"))
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
        return "$SUPABASE_URL/storage/v1/object/public/$STORAGE_BUCKET/$fileName"
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
            executeRequest(req)?.close()
            Result.success("Receipt design saved to cloud")
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
            val response = executeRequest(req)
            val bodyStr = response?.body?.string()
            response?.close()
            if (bodyStr != null) {
                val arr = JSONArray(bodyStr)
                if (arr.length() > 0) {
                    return@withContext arr.getJSONObject(0).optString("value", null)
                }
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
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata?key=eq.$key&limit=1")
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
                    val value = arr.getJSONObject(0).optString("value", "")
                    if (value.isNotBlank()) return@withContext value
                }
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
        if (!isInitialized) return@withContext Result.failure(Exception("Not initialized"))
        try {
            val body = JSONObject().apply {
                put("key", key)
                put("value", value)
                put("updated_at", System.currentTimeMillis())
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/sync_metadata")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeRequest(req)?.close()
            Result.success("Metadata $key saved to cloud")
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
            val body = audioBytes.toRequestBody("audio/mp4".toMediaType())
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$VOICE_ORDERS_BUCKET/$fileName")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "audio/mp4")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            val response = executeRequest(req)
            if (response?.isSuccessful == true) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$VOICE_ORDERS_BUCKET/$fileName"
                response.close()
                Result.success(publicUrl)
            } else {
                val code = response?.code
                val err = response?.body?.string() ?: "HTTP $code"
                response?.close()
                Result.failure(Exception("Voice audio upload failed: $err"))
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
            val body = jsonDiagnostics.toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("$STORAGE_BASE_URL/object/$VOICE_ORDERS_BUCKET/$fileName")
                .addHeader("apikey", SERVICE_ROLE_KEY)
                .addHeader("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            val response = executeRequest(req)
            if (response?.isSuccessful == true) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$VOICE_ORDERS_BUCKET/$fileName"
                response.close()
                Result.success(publicUrl)
            } else {
                val code = response?.code
                val err = response?.body?.string() ?: "HTTP $code"
                response?.close()
                Result.failure(Exception("Diagnostics upload failed: $err"))
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
                        minVersionName = json.optString("min_version_name", ""),
                        title = json.optString("title", "Mandatory Update Required"),
                        message = json.optString("message", "A new version of FILTR Billing is required. Please update to continue taking orders."),
                        apkDownloadUrl = json.optString("apk_download_url", ""),
                        apkFileName = json.optString("apk_file_name", "filtr-billing-v1.3.apk"),
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
                    minVersionName = json.optString("min_version_name", ""),
                    title = json.optString("title", "Mandatory Update Required"),
                    message = json.optString("message", "A new version of FILTR Billing is required. Please update to continue taking orders."),
                    apkDownloadUrl = json.optString("apk_download_url", ""),
                    apkFileName = json.optString("apk_file_name", "filtr-billing-v1.3.apk"),
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
}
