package com.example.kitchen.repository

import android.util.Log
import com.example.kitchen.data.dao.CustomerDao
import com.example.kitchen.data.dao.MenuDao
import com.example.kitchen.data.dao.OrderDao
import com.example.kitchen.data.entity.*
import com.example.kitchen.data.remote.GoogleSheetsSyncService
import com.example.kitchen.data.remote.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class KitchenRepository(
    private val orderDao: OrderDao,
    private val menuDao: MenuDao,
    private val customerDao: CustomerDao,
    private val supabaseService: SupabaseService,
    private val sheetsSyncService: GoogleSheetsSyncService
) {
    companion object {
        private const val TAG = "KitchenRepository"
    }

    val pendingOrders: Flow<List<OrderWithItems>> = orderDao.getPendingOrdersWithItems().distinctUntilChanged()
    val allMenuItems: Flow<List<MenuItemEntity>> = menuDao.getAvailableMenuItems().distinctUntilChanged()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers().distinctUntilChanged()
    val pendingOrderCount: Flow<Int> = orderDao.getPendingOrderCount().distinctUntilChanged()

    fun getCompletedTodayOrders(): Flow<List<OrderWithItems>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return orderDao.getCompletedTodayOrdersWithItems(calendar.timeInMillis).distinctUntilChanged()
    }

    // ==================== REAL-TIME CLOUD SYNC ====================

    fun startCloudSync(scope: CoroutineScope) {
        supabaseService.initialize()

        // 1. Listen to Supabase orders (every 4 seconds)
        scope.launch(Dispatchers.IO) {
            try {
                supabaseService.listenToOrders(4_000L).collect { remoteOrders ->
                    if (remoteOrders.isNotEmpty()) {
                        for (remote in remoteOrders) {
                            val local = orderDao.getOrderById(remote.order.orderId)
                            if (local == null) {
                                // New order placed from mobile POS or another device!
                                orderDao.insertOrderWithItems(remote.order.copy(syncStatus = "SYNCED"), remote.items.map { it.copy(id = 0) })
                                Log.d(TAG, "Synced new remote order into Room: ${remote.order.orderId}")
                            } else {
                                val localIsPending = local.order.syncStatus == "PENDING"

                                // Check if items/SKUs differ (e.g. edited from 3 to 5 SKUs)
                                val itemsDiffer = local.items.size != remote.items.size ||
                                        local.items.sumOf { it.quantity } != remote.items.sumOf { it.quantity } ||
                                        kotlin.math.abs(local.order.totalAmount - remote.order.totalAmount) > 0.01 ||
                                        local.items.map { it.itemName }.sorted() != remote.items.map { it.itemName }.sorted()

                                if (itemsDiffer) {
                                    if (!localIsPending) {
                                        orderDao.updateOrderWithItems(remote.order.copy(syncStatus = "SYNCED"), remote.items.map { it.copy(id = 0) })
                                        Log.d(TAG, "Updated local order items from remote: ${remote.order.orderId} (${remote.items.size} items)")
                                    }
                                } else {
                                    // Items match: Reconcile item completions
                                    for (remoteItem in remote.items) {
                                        val localItem = local.items.find { it.itemName.equals(remoteItem.itemName, ignoreCase = true) }
                                        if (localItem != null) {
                                            val resolvedQty = if (localIsPending) {
                                                maxOf(localItem.completedQuantity, remoteItem.completedQuantity)
                                            } else {
                                                remoteItem.completedQuantity
                                            }
                                            val resolvedTs = if (resolvedQty > 0) maxOf(localItem.completedTimestamp, remoteItem.completedTimestamp) else 0L
                                            if (localItem.completedQuantity != resolvedQty || localItem.completedTimestamp != resolvedTs) {
                                                orderDao.updateOrderItemCompletedQuantity(localItem.id, resolvedQty, resolvedTs)
                                            }
                                        }
                                    }

                                    // Update order status if remote changed and local is not pending
                                    if (!localIsPending && (local.order.isMade != remote.order.isMade ||
                                        local.order.isPaid != remote.order.isPaid ||
                                        local.order.isDelivered != remote.order.isDelivered ||
                                        local.order.totalAmount != remote.order.totalAmount)) {
                                        orderDao.updateOrder(remote.order.copy(syncStatus = "SYNCED"))
                                        Log.d(TAG, "Updated local order status from remote: ${remote.order.orderId}")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase order listen loop error: ${e.message}")
            }
        }

        // 2. Listen to Supabase menu items (every 30 seconds)
        scope.launch(Dispatchers.IO) {
            try {
                supabaseService.listenToMenuItems(30_000L).collect { remoteItems ->
                    if (remoteItems.isNotEmpty()) {
                        menuDao.insertAll(remoteItems)
                        Log.d(TAG, "Synced ${remoteItems.size} menu items from Supabase")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase menu listen loop error: ${e.message}")
            }
        }
    }

    // ==================== KDS ACTIONS ====================

    suspend fun markOrderReady(orderId: String, webhookUrl: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            // 1. Local Room DB update immediately
            orderDao.updateMadeStatus(orderId, true, now)
            orderDao.markAllOrderItemsCompleted(orderId, now)
            orderDao.updateSyncStatus(orderId, "PENDING")

            // 2. Background push to Supabase
            val supabaseResult = supabaseService.markOrderMade(orderId, now)
            if (supabaseResult.isSuccess) {
                orderDao.updateSyncStatus(orderId, "SYNCED")
            }

            // 3. Optional Google Sheets sync
            if (webhookUrl.isNotBlank()) {
                val updated = orderDao.getOrderById(orderId)
                if (updated != null) {
                    sheetsSyncService.syncSingleOrder(webhookUrl, updated)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markOrderReady error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markOrderDelivered(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            orderDao.updateDeliveredStatus(orderId, true)
            orderDao.updateSyncStatus(orderId, "PENDING")
            val res = supabaseService.markOrderDelivered(orderId)
            if (res.isSuccess) {
                orderDao.updateSyncStatus(orderId, "SYNCED")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ORDER TAKING (TABLET POS) ====================

    suspend fun generateOrderId(): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val datePart = dateFormat.format(Date())

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        // Check local Room sequence
        val todayOrderIds = orderDao.getTodayOrderIds(startOfDay)
        var maxLocalSeq = 1000
        for (id in todayOrderIds) {
            val seqStr = id.substringAfterLast("-", "")
            val seq = seqStr.toIntOrNull()
            if (seq != null && seq > maxLocalSeq) {
                maxLocalSeq = seq
            }
        }

        // Check Supabase sequence
        var nextSeq = maxLocalSeq + 1
        val supabaseSeqResult = supabaseService.getNextOrderSequence(datePart)
        if (supabaseSeqResult.isSuccess) {
            val cloudSeq = supabaseSeqResult.getOrDefault(1000)
            if (cloudSeq > maxLocalSeq) {
                nextSeq = cloudSeq
            }
        } else {
            val maxCloud = supabaseService.getTodayMaxOrderSequence(datePart)
            if (maxCloud >= nextSeq) {
                nextSeq = maxCloud + 1
            }
        }

        // Avoid collision
        var candidateId = "FLT-$datePart-$nextSeq"
        while (orderDao.getOrderById(candidateId) != null) {
            nextSeq++
            candidateId = "FLT-$datePart-$nextSeq"
        }

        candidateId
    }

    suspend fun createOrder(
        customerName: String,
        contactNumber: String,
        cartItems: Map<MenuItemEntity, Int>,
        notes: String,
        paymentMode: String,
        isPaid: Boolean,
        webhookUrl: String = ""
    ): Result<OrderWithItems> = withContext(Dispatchers.IO) {
        try {
            val orderId = generateOrderId()
            val totalAmount = cartItems.entries.sumOf { (item, qty) -> item.price * qty }
            val now = System.currentTimeMillis()

            val orderEntity = OrderEntity(
                orderId = orderId,
                customerName = customerName.ifBlank { "Walk-in Customer" },
                contactNumber = contactNumber,
                totalAmount = totalAmount,
                timestamp = now,
                isMade = false,
                isPaid = isPaid,
                isDelivered = false,
                syncStatus = "PENDING",
                notes = notes,
                paymentMode = paymentMode,
                completedTimestamp = 0L
            )

            val orderItemEntities = cartItems.map { (item, qty) ->
                OrderItemEntity(
                    orderId = orderId,
                    menuItemId = item.id,
                    itemName = item.name,
                    itemPrice = item.price,
                    quantity = qty,
                    subtotal = item.price * qty,
                    completedQuantity = 0,
                    completedTimestamp = 0L
                )
            }

            // 1. Save locally to Room
            orderDao.insertOrderWithItems(orderEntity, orderItemEntities)

            val orderWithItems = OrderWithItems(orderEntity, orderItemEntities)

            // 2. Non-blocking sync to Supabase
            launch {
                try {
                    val supaResult = supabaseService.saveOrder(orderWithItems)
                    if (supaResult.isSuccess) {
                        orderDao.updateSyncStatus(orderId, "SYNCED")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Supabase save error (will retry): ${e.message}")
                }
            }

            // 3. Non-blocking sync to Sheets if URL present
            if (webhookUrl.isNotBlank()) {
                launch {
                    try {
                        sheetsSyncService.syncSingleOrder(webhookUrl, orderWithItems)
                    } catch (e: Exception) {
                        Log.w(TAG, "Sheets save error: ${e.message}")
                    }
                }
            }

            // 4. Update customer stats
            if (customerName.isNotBlank() && customerName != "Walk-in Customer") {
                launch {
                    try {
                        customerDao.incrementOrderStats(customerName, totalAmount, now, contactNumber)
                        val existing = customerDao.getCustomerByName(customerName)
                        if (existing == null) {
                            val newCustomer = CustomerEntity(
                                name = customerName,
                                contactNumber = contactNumber,
                                totalOrders = 1,
                                totalSpent = totalAmount,
                                lastOrderTimestamp = now
                            )
                            customerDao.insertCustomer(newCustomer)
                            supabaseService.saveCustomer(newCustomer)
                        } else {
                            supabaseService.saveCustomer(existing.copy(
                                totalOrders = existing.totalOrders + 1,
                                totalSpent = existing.totalSpent + totalAmount,
                                lastOrderTimestamp = now
                            ))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Customer update error: ${e.message}")
                    }
                }
            }

            Result.success(orderWithItems)
        } catch (e: Exception) {
            Log.e(TAG, "createOrder error: ${e.message}")
            Result.failure(e)
        }
    }
}
