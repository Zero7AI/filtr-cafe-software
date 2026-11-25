package com.example.kitchen.repository

import android.util.Log
import com.example.kitchen.data.dao.CustomerDao
import com.example.kitchen.data.dao.MenuDao
import com.example.kitchen.data.dao.OrderDao
import com.example.kitchen.data.entity.CustomerEntity
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import com.example.kitchen.data.remote.GoogleSheetsSyncService
import com.example.kitchen.data.remote.SupabaseService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class KitchenRepository(
    private val orderDao: OrderDao,
    private val menuDao: MenuDao,
    private val customerDao: CustomerDao,
    private val supabaseService: SupabaseService,
    private val sheetsSyncService: GoogleSheetsSyncService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val externalSyncScope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "KitchenRepository"
        private const val RETRY_SYNC_INTERVAL_MS = 30_000L
        private const val MAX_RPC_COLLISION_RETRIES = 10
    }

    private val orderIdMutex = Mutex()
    private val syncRetryMutex = Mutex()
    private var lastReservedSeqForDate: Pair<String, Int>? = null
    @Volatile
    private var activeSyncScope: CoroutineScope? = externalSyncScope

    val pendingOrders: Flow<List<OrderWithItems>> = orderDao.getPendingOrdersWithItems().distinctUntilChanged()
    val allMenuItems: Flow<List<MenuItemEntity>> = menuDao.getAvailableMenuItems().distinctUntilChanged()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers().distinctUntilChanged()
    val pendingOrderCount: Flow<Int> = orderDao.getPendingOrderCount().distinctUntilChanged()

    private fun getBackgroundScope(): CoroutineScope =
        externalSyncScope ?: activeSyncScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun getCompletedTodayOrders(): Flow<List<OrderWithItems>> {
        return orderDao.getCompletedTodayOrdersWithItems(startOfTodayMillis()).distinctUntilChanged()
    }

    // ==================== REAL-TIME CLOUD SYNC ====================

    fun startCloudSync(scope: CoroutineScope) {
        if (externalSyncScope == null) {
            activeSyncScope = scope
        }
        supabaseService.initialize()

        // 1. Listen to Supabase orders (every 4 seconds)
        scope.launch(ioDispatcher) {
            try {
                supabaseService.listenToOrders(4_000L).collect { remoteOrders ->
                    reconcileRemoteOrders(remoteOrders, startOfTodayMillis())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase order listen loop error: ${e.message}")
            }
        }

        // 2. Listen to Supabase menu items (every 30 seconds)
        scope.launch(ioDispatcher) {
            try {
                supabaseService.listenToMenuItems(30_000L).collect { remoteItems ->
                    reconcileRemoteMenuItems(remoteItems)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase menu listen loop error: ${e.message}")
            }
        }

        // 3. Periodic retry loop for offline/failed local orders
        scope.launch(ioDispatcher) {
            while (isActive) {
                try {
                    syncUnsyncedOrders()
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic unsynced order retry error: ${e.message}")
                }
                delay(RETRY_SYNC_INTERVAL_MS)
            }
        }
    }

    internal suspend fun reconcileRemoteOrders(
        remoteOrders: List<OrderWithItems>,
        startOfTodayMs: Long = startOfTodayMillis()
    ) {
        val remoteIds = remoteOrders.map { it.order.orderId }.toSet()
        val localTodayOrders = orderDao.getOrdersSince(startOfTodayMs)
        for (localOrder in localTodayOrders) {
            if (localOrder.syncStatus == "SYNCED" && localOrder.orderId !in remoteIds) {
                orderDao.deleteOrderItemsByOrderId(localOrder.orderId)
                orderDao.deleteOrder(localOrder.orderId)
                Log.d(TAG, "Pruned deleted remote order from Room: ${localOrder.orderId}")
            }
        }

        for (remote in remoteOrders) {
            val local = orderDao.getOrderById(remote.order.orderId)
            if (local == null) {
                if (remote.items.isNotEmpty()) {
                    orderDao.insertOrderWithItems(
                        remote.order.copy(syncStatus = "SYNCED"),
                        remote.items.map { it.copy(id = 0) }
                    )
                    Log.d(TAG, "Synced new remote order into Room: ${remote.order.orderId}")
                }
            } else {
                val localIsPending = local.order.syncStatus == "PENDING"

                val itemsDiffer = local.items.size != remote.items.size ||
                    local.items.sumOf { it.quantity } != remote.items.sumOf { it.quantity } ||
                    kotlin.math.abs(local.order.totalAmount - remote.order.totalAmount) > 0.01 ||
                    local.items.map { it.itemName }.sorted() != remote.items.map { it.itemName }.sorted()

                if (itemsDiffer) {
                    if (!localIsPending && remote.items.isNotEmpty()) {
                        orderDao.updateOrderWithItems(
                            remote.order.copy(syncStatus = "SYNCED"),
                            remote.items.map { it.copy(id = 0) }
                        )
                        Log.d(TAG, "Updated local order items from remote: ${remote.order.orderId} (${remote.items.size} items)")
                    }
                } else {
                    for (remoteItem in remote.items) {
                        val localItem = local.items.find { it.itemName.equals(remoteItem.itemName, ignoreCase = true) }
                        if (localItem != null) {
                            val resolvedQty = if (localIsPending) {
                                maxOf(localItem.completedQuantity, remoteItem.completedQuantity)
                            } else {
                                remoteItem.completedQuantity
                            }
                            val resolvedTs = if (resolvedQty > 0) {
                                maxOf(localItem.completedTimestamp, remoteItem.completedTimestamp)
                            } else 0L
                            if (localItem.completedQuantity != resolvedQty || localItem.completedTimestamp != resolvedTs) {
                                orderDao.updateOrderItemCompletedQuantity(localItem.id, resolvedQty, resolvedTs)
                            }
                        }
                    }

                    val statusDiffers = local.order.isMade != remote.order.isMade ||
                        local.order.isPaid != remote.order.isPaid ||
                        local.order.isDelivered != remote.order.isDelivered ||
                        local.order.paymentMode != remote.order.paymentMode ||
                        local.order.notes != remote.order.notes ||
                        local.order.customerName != remote.order.customerName ||
                        local.order.contactNumber != remote.order.contactNumber ||
                        local.order.completedTimestamp != remote.order.completedTimestamp ||
                        kotlin.math.abs(local.order.totalAmount - remote.order.totalAmount) > 0.01

                    if (!localIsPending && statusDiffers) {
                        orderDao.updateOrder(remote.order.copy(syncStatus = "SYNCED"))
                        Log.d(TAG, "Updated local order status from remote: ${remote.order.orderId}")
                    }
                }
            }
        }
    }

    internal suspend fun reconcileRemoteMenuItems(remoteItems: List<MenuItemEntity>) {
        if (remoteItems.isEmpty()) return
        val remoteIds = remoteItems.map { it.id }.filter { it > 0 }.toSet()
        val localItems = menuDao.getAllMenuItemsList()
        for (local in localItems) {
            if (local.id !in remoteIds) {
                menuDao.deleteMenuItem(local)
                Log.d(TAG, "Removed obsolete local menu item #${local.id} (${local.name})")
            }
        }
        menuDao.insertAll(remoteItems)
        Log.d(TAG, "Synced ${remoteItems.size} menu items from Supabase")
    }

    suspend fun syncUnsyncedOrders(): Int = withContext(ioDispatcher) {
        syncRetryMutex.withLock {
            if (!supabaseService.isInitialized) return@withLock 0
            val unsynced = orderDao.getUnsyncedOrders()
            if (unsynced.isEmpty()) return@withLock 0

            var syncedCount = 0
            for (orderWithItems in unsynced) {
                val res = supabaseService.saveOrder(
                    orderWithItems.copy(order = orderWithItems.order.copy(syncStatus = "SYNCED"))
                )
                if (res.isSuccess) {
                    orderDao.updateSyncStatus(orderWithItems.order.orderId, "SYNCED")
                    syncedCount++
                }
            }
            if (syncedCount > 0) {
                Log.d(TAG, "Retried and synced $syncedCount/${unsynced.size} pending orders to Supabase")
            }
            syncedCount
        }
    }

    // ==================== KDS ACTIONS ====================

    suspend fun markOrderReady(orderId: String, webhookUrl: String = ""): Result<Unit> = withContext(ioDispatcher) {
        try {
            val now = System.currentTimeMillis()
            // 1. Local Room DB update immediately
            orderDao.updateMadeStatus(orderId, true, now)
            orderDao.markAllOrderItemsCompleted(orderId, now)
            orderDao.updateSyncStatus(orderId, "PENDING")

            val updatedOrder = orderDao.getOrderById(orderId)

            // 2. Push order status and item completions to Supabase
            val supabaseResult = supabaseService.markOrderMade(orderId, now)
            var allItemsSynced = true
            if (supabaseResult.isSuccess && updatedOrder != null) {
                for (item in updatedOrder.items) {
                    val itemRes = supabaseService.updateOrderItemCompletion(
                        orderId = orderId,
                        itemName = item.itemName,
                        completedQuantity = item.quantity,
                        completedTimestamp = now
                    )
                    if (itemRes.isFailure) {
                        allItemsSynced = false
                    }
                }
                if (allItemsSynced) {
                    orderDao.updateSyncStatus(orderId, "SYNCED")
                }
            }

            // 3. Optional Google Sheets sync (non-blocking)
            if (webhookUrl.isNotBlank() && updatedOrder != null) {
                getBackgroundScope().launch(ioDispatcher) {
                    try {
                        sheetsSyncService.syncSingleOrder(webhookUrl, updatedOrder)
                    } catch (e: Exception) {
                        Log.w(TAG, "Sheets sync error on markOrderReady: ${e.message}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markOrderReady error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markOrderDelivered(orderId: String): Result<Unit> = withContext(ioDispatcher) {
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

    suspend fun generateOrderId(): String = orderIdMutex.withLock {
        withContext(ioDispatcher) {
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val datePart = dateFormat.format(Date())
            val startOfDay = startOfTodayMillis()

            val todayOrderIds = orderDao.getTodayOrderIds(startOfDay)
            var maxLocalSeq = 1000
            for (id in todayOrderIds) {
                val seq = id.substringAfterLast("-", "").toIntOrNull()
                if (seq != null && seq > maxLocalSeq) {
                    maxLocalSeq = seq
                }
            }
            val reservedForToday = lastReservedSeqForDate?.takeIf { it.first == datePart }?.second ?: 1000
            val effectiveMinSeq = maxOf(maxLocalSeq, reservedForToday)

            var nextSeq = effectiveMinSeq + 1
            val supabaseSeqResult = supabaseService.getNextOrderSequence(datePart)
            if (supabaseSeqResult.isSuccess) {
                val cloudSeq = supabaseSeqResult.getOrNull()
                if (cloudSeq != null && cloudSeq > effectiveMinSeq) {
                    nextSeq = cloudSeq
                } else {
                    var rpcRetries = 0
                    while (rpcRetries < MAX_RPC_COLLISION_RETRIES) {
                        rpcRetries++
                        val retrySeq = supabaseService.getNextOrderSequence(datePart).getOrNull() ?: break
                        if (retrySeq > effectiveMinSeq) {
                            nextSeq = retrySeq
                            break
                        }
                    }
                }
                var candidateId = "FLT-$datePart-$nextSeq"
                var collisionRetries = 0
                while (orderDao.getOrderById(candidateId) != null && collisionRetries < MAX_RPC_COLLISION_RETRIES) {
                    collisionRetries++
                    val retrySeq = supabaseService.getNextOrderSequence(datePart).getOrNull() ?: break
                    if (retrySeq > nextSeq) {
                        nextSeq = retrySeq
                        candidateId = "FLT-$datePart-$nextSeq"
                    } else {
                        break
                    }
                }
                while (orderDao.getOrderById(candidateId) != null) {
                    nextSeq++
                    candidateId = "FLT-$datePart-$nextSeq"
                }
                lastReservedSeqForDate = datePart to nextSeq
                return@withContext candidateId
            }

            val maxCloud = supabaseService.getTodayMaxOrderSequence(datePart)
            if (maxCloud >= nextSeq) {
                nextSeq = maxCloud + 1
            }

            var candidateId = "FLT-$datePart-$nextSeq"
            while (orderDao.getOrderById(candidateId) != null) {
                nextSeq++
                candidateId = "FLT-$datePart-$nextSeq"
            }
            lastReservedSeqForDate = datePart to nextSeq

            candidateId
        }
    }

    suspend fun createOrder(
        customerName: String,
        contactNumber: String,
        cartItems: Map<MenuItemEntity, Int>,
        notes: String,
        paymentMode: String,
        isPaid: Boolean,
        webhookUrl: String = ""
    ): Result<OrderWithItems> = withContext(ioDispatcher) {
        try {
            val orderId = generateOrderId()
            val totalAmount = cartItems.entries.sumOf { (item, qty) -> item.price * qty }
            val now = System.currentTimeMillis()
            val trimmedName = customerName.trim()
            val trimmedContact = contactNumber.trim()
            val resolvedName = trimmedName.ifBlank { "Walk-in Customer" }

            val orderEntity = OrderEntity(
                orderId = orderId,
                customerName = resolvedName,
                contactNumber = trimmedContact,
                totalAmount = totalAmount,
                timestamp = now,
                isMade = false,
                isPaid = isPaid,
                isDelivered = false,
                syncStatus = "PENDING",
                notes = notes.trim(),
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

            // 1. Save locally to Room first (offline-first)
            orderDao.insertOrderWithItems(orderEntity, orderItemEntities)

            val orderWithItems = OrderWithItems(orderEntity, orderItemEntities)
            val backgroundScope = getBackgroundScope()

            // 2. Non-blocking sync to Supabase on background scope
            backgroundScope.launch(ioDispatcher) {
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
                backgroundScope.launch(ioDispatcher) {
                    try {
                        sheetsSyncService.syncSingleOrder(webhookUrl, orderWithItems)
                    } catch (e: Exception) {
                        Log.w(TAG, "Sheets save error: ${e.message}")
                    }
                }
            }

            // 4. Non-blocking customer stats update (without double-counting)
            if (trimmedName.isNotBlank() && !trimmedName.equals("Walk-in Customer", ignoreCase = true)) {
                backgroundScope.launch(ioDispatcher) {
                    try {
                        val existing = customerDao.getCustomerByName(trimmedName)
                        if (existing == null) {
                            val newCustomer = CustomerEntity(
                                name = trimmedName,
                                contactNumber = trimmedContact,
                                totalOrders = 1,
                                totalSpent = totalAmount,
                                lastOrderTimestamp = now,
                                createdAt = now
                            )
                            customerDao.insertCustomer(newCustomer)
                            supabaseService.saveCustomer(newCustomer)
                        } else {
                            customerDao.incrementOrderStats(trimmedName, totalAmount, now, trimmedContact)
                            val updated = customerDao.getCustomerByName(trimmedName)
                            if (updated != null) {
                                supabaseService.saveCustomer(updated)
                            }
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
