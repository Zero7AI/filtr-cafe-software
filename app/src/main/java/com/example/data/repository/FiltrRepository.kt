package com.example.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.data.dao.ChecklistDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.FixedCostDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.MenuDao
import com.example.data.dao.OrderDao
import com.example.data.dao.UpiAccountDao
import com.example.data.database.FiltrDatabase
import com.example.data.entity.ChecklistCompletionEntity
import com.example.data.entity.ChecklistTaskEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.FixedCostEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.calculatePacksAndCost
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.UpiAccountEntity
import com.example.data.remote.DeepgramVoiceService
import com.example.data.remote.FirestoreService
import com.example.data.remote.SupabaseService
import com.example.data.remote.GeminiVoiceOrderService
import com.example.data.remote.GoogleSheetsSyncService
import com.example.data.remote.ParsedVoiceOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class FiltrRepository(
    private val menuDao: MenuDao,
    private val orderDao: OrderDao,
    private val upiAccountDao: UpiAccountDao? = null,
    private val inventoryDao: InventoryDao? = null,
    private val customerDao: CustomerDao? = null,
    private val fixedCostDao: FixedCostDao? = null,
    private val checklistDao: ChecklistDao? = null,
    private val sheetsSyncService: GoogleSheetsSyncService = GoogleSheetsSyncService(),
    private val geminiVoiceService: GeminiVoiceOrderService = GeminiVoiceOrderService(),
    private val deepgramVoiceService: DeepgramVoiceService = DeepgramVoiceService.getInstance(),
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val supabaseService: SupabaseService = SupabaseService.getInstance(),
    private val externalScope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "FiltrRepository"
    }

    private val orderIdMutex = Mutex()
    private val inventoryOrderIdMutex = Mutex()
    private val stockMutex = Mutex()
    private val menuSyncMutex = Mutex()
    private val checklistSyncMutex = Mutex()
    private val customerSyncMutex = Mutex()

    @Volatile
    private var managedScope: CoroutineScope? = externalScope
    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory protection & retry tracking for non-order entities and dual-backend sync status
    private val pendingOrderDeletions = ConcurrentHashMap.newKeySet<String>()
    private val sheetsPendingOrderIds = ConcurrentHashMap.newKeySet<String>()
    private val pendingMenuUpsertIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingMenuDeleteIds = ConcurrentHashMap.newKeySet<Int>()
    private val dirtyInventoryItemIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingUsageUpsertKeys = ConcurrentHashMap.newKeySet<Pair<Int, Int>>()
    private val pendingUsageDeleteIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingUsageDeleteMenuIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingCustomerNames = ConcurrentHashMap.newKeySet<String>()
    private val pendingCustomerDeleteIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingChecklistTaskAdds = ConcurrentHashMap.newKeySet<String>()
    private val pendingChecklistTaskDeletes = ConcurrentHashMap.newKeySet<String>()
    private val dirtyChecklistDateKeys = ConcurrentHashMap.newKeySet<String>()
    private val localChecklistActionTimestamps = ConcurrentHashMap<Pair<String, String>, Long>()

    private fun launchManagedSync(block: suspend CoroutineScope.() -> Unit) {
        (managedScope ?: fallbackScope).launch(Dispatchers.IO, block = block)
    }

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        val db = FiltrDatabase.getInstanceOrNull()
        return if (db != null) db.withTransaction { block() } else block()
    }

    val allMenuItems: Flow<List<MenuItemEntity>> = menuDao.getAllMenuItems()
    val availableMenuItems: Flow<List<MenuItemEntity>> = menuDao.getAvailableMenuItems()
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()
    val allUpiAccounts: Flow<List<UpiAccountEntity>> = upiAccountDao?.getAllUpiAccounts() ?: flowOf(emptyList())
    val allInventoryItems: Flow<List<InventoryItemEntity>> = inventoryDao?.getAllActiveInventoryItems() ?: flowOf(emptyList())
    val allInventoryOrders: Flow<List<InventoryOrderWithItems>> = inventoryDao?.getAllInventoryOrdersWithItems() ?: flowOf(emptyList())
    val allInventoryUsages: Flow<List<InventoryUsageEntity>> = inventoryDao?.getAllUsages() ?: flowOf(emptyList())
    val allCustomers: Flow<List<CustomerEntity>> = customerDao?.getAllCustomers() ?: flowOf(emptyList())
    val allFixedCosts: Flow<List<FixedCostEntity>> = fixedCostDao?.getAllFixedCosts() ?: flowOf(emptyList())
    val allChecklistTasks: Flow<List<ChecklistTaskEntity>> = checklistDao?.getAllTasks() ?: flowOf(emptyList())

    fun getChecklistCompletionsForDate(dateKey: String): Flow<List<ChecklistCompletionEntity>> {
        return checklistDao?.getCompletionsForDate(dateKey) ?: flowOf(emptyList())
    }

    // ==================== FIRESTORE REAL-TIME LISTENERS ====================

    /**
     * Start Firestore real-time listeners that sync remote changes into Room DB.
     * Call this from ViewModel init{} after Firestore is initialized.
     */

    /**
     * On-demand backfill: fetch orders from Firestore for a specific date range
     * and insert any missing ones into Room DB. Called when user picks dates
     * from the dashboard calendar.
     *
     * @return number of new orders backfilled
     */
    suspend fun backfillOrdersForDateRange(startMs: Long, endMs: Long): Int {
        // Use Supabase as primary, Firestore as fallback
        val remoteOrders = if (supabaseService.isInitialized) {
            supabaseService.fetchOrdersForDateRange(startMs, endMs)
        } else if (firestoreService.isInitialized) {
            firestoreService.fetchOrdersForDateRange(startMs, endMs)
        } else {
            return 0
        }
        var backfilled = 0

        for (remoteOrder in remoteOrders) {
            try {
                if (remoteOrder.order.orderId in pendingOrderDeletions) continue
                val localOrder = orderDao.getOrderById(remoteOrder.order.orderId)
                if (localOrder == null && remoteOrder.items.isNotEmpty()) {
                    // New order not in Room — insert it
                    val itemsForRoom = remoteOrder.items.map { it.copy(id = 0) }
                    orderDao.insertOrderWithItems(
                        remoteOrder.order.copy(syncStatus = "SYNCED"),
                        itemsForRoom
                    )
                    backfilled++
                    Log.d(TAG, "Backfilled order: ${remoteOrder.order.orderId}")
                } else if (localOrder != null && remoteOrder.items.isNotEmpty()) {
                    // Existing order in Room — check if edited on another device (different item count, quantity, or total amount)
                    val itemsDiffer = localOrder.items.size != remoteOrder.items.size ||
                            localOrder.items.sumOf { it.quantity } != remoteOrder.items.sumOf { it.quantity } ||
                            kotlin.math.abs(localOrder.order.totalAmount - remoteOrder.order.totalAmount) > 0.01 ||
                            localOrder.items.map { it.itemName }.sorted() != remoteOrder.items.map { it.itemName }.sorted()

                    if (itemsDiffer && localOrder.order.syncStatus != "PENDING") {
                        val itemsForRoom = remoteOrder.items.map { it.copy(id = 0) }
                        orderDao.updateOrderWithItems(
                            remoteOrder.order.copy(syncStatus = "SYNCED"),
                            itemsForRoom
                        )
                        backfilled++
                        Log.d(TAG, "Backfill updated edited order: ${remoteOrder.order.orderId}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error backfilling order ${remoteOrder.order.orderId}: ${e.message}")
            }
        }

        Log.d(TAG, "Backfill complete: $backfilled new orders from ${remoteOrders.size} remote")
        return backfilled
    }

    // ==================== SUPABASE REAL-TIME LISTENERS ====================

    /**
     * Start Supabase polling listeners that sync remote changes into Room DB.
     * This is the primary cloud sync method (replaces Firestore listeners).
     */
    fun startSupabaseListeners(scope: CoroutineScope) {
        managedScope = scope
        if (!supabaseService.isInitialized) return

        // Listen to order changes from Supabase (polling every 2s)
        scope.launch(Dispatchers.IO) {
            supabaseService.listenToOrders().collect { remoteOrders ->
                for (remoteOrder in remoteOrders) {
                    try {
                        val orderId = remoteOrder.order.orderId
                        if (orderId in pendingOrderDeletions) continue
                        val localOrder = orderDao.getOrderById(orderId)

                        if (localOrder == null) {
                            if (remoteOrder.items.isNotEmpty()) {
                                val itemsForRoom = remoteOrder.items.map { it.copy(id = 0) }
                                orderDao.insertOrderWithItems(
                                    remoteOrder.order.copy(syncStatus = "SYNCED"),
                                    itemsForRoom
                                )
                                Log.d(TAG, "Supabase: Synced new order: $orderId")
                            }
                        } else {
                            val localIsPending = localOrder.order.syncStatus == "PENDING"

                            // Detect if remote order has different items/SKUs (e.g. edited from 3 to 5 SKUs on another phone)
                            val itemsDiffer = localOrder.items.size != remoteOrder.items.size ||
                                    localOrder.items.sumOf { it.quantity } != remoteOrder.items.sumOf { it.quantity } ||
                                    kotlin.math.abs(localOrder.order.totalAmount - remoteOrder.order.totalAmount) > 0.01 ||
                                    localOrder.items.map { it.itemName }.sorted() != remoteOrder.items.map { it.itemName }.sorted()

                            if (itemsDiffer) {
                                // Guard against transient empty items emitted mid-write during remote saveOrder
                                if (!localIsPending && remoteOrder.items.isNotEmpty()) {
                                    val itemsForRoom = remoteOrder.items.map { it.copy(id = 0) }
                                    orderDao.updateOrderWithItems(
                                        remoteOrder.order.copy(syncStatus = "SYNCED"),
                                        itemsForRoom
                                    )
                                    Log.d(TAG, "Supabase: Synced edited order items for $orderId (${itemsForRoom.size} items)")
                                }
                            } else {
                                // Items match: Reconcile item completions without deleting/reinserting rows
                                for (remoteItem in remoteOrder.items) {
                                    val localItem = localOrder.items.find { it.itemName.equals(remoteItem.itemName, ignoreCase = true) }
                                    if (localItem != null) {
                                        // If local is pending, never revert local completed progress with older remote data
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

                                // Update order status: Reconcile status fields so remote updates (paid, made, delivered) reflect across devices
                                val statusDiffers = localOrder.order.isPaid != remoteOrder.order.isPaid ||
                                        localOrder.order.isDelivered != remoteOrder.order.isDelivered ||
                                        localOrder.order.isMade != remoteOrder.order.isMade ||
                                        localOrder.order.paymentMode != remoteOrder.order.paymentMode

                                if (localOrder.order.syncStatus != "PENDING" && statusDiffers) {
                                    orderDao.updateOrder(
                                        localOrder.order.copy(
                                            isPaid = remoteOrder.order.isPaid,
                                            paymentMode = remoteOrder.order.paymentMode,
                                            isMade = remoteOrder.order.isMade,
                                            isDelivered = remoteOrder.order.isDelivered,
                                            completedTimestamp = maxOf(localOrder.order.completedTimestamp, remoteOrder.order.completedTimestamp),
                                            syncStatus = "SYNCED"
                                        )
                                    )
                                    Log.d(TAG, "Supabase: Synced status update for $orderId (paid=${remoteOrder.order.isPaid}, made=${remoteOrder.order.isMade}, delivered=${remoteOrder.order.isDelivered})")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error syncing order: ${e.message}")
                    }
                }
            }
        }

        // Listen to menu item changes from Supabase
        scope.launch(Dispatchers.IO) {
            deduplicateMenuItems()
            supabaseService.listenToMenuItems().collect { remoteItems ->
                menuSyncMutex.withLock {
                    if (remoteItems.isNotEmpty()) {
                        val remoteIds = remoteItems.map { it.id }.toSet()
                        val localItems = menuDao.getAllMenuItemsList()
                        // Remove obsolete local items that are no longer in Supabase (protect unsynced local additions)
                        for (local in localItems) {
                            if (local.id !in remoteIds && local.id !in pendingMenuUpsertIds) {
                                menuDao.deleteMenuItem(local)
                                Log.d(TAG, "Removed obsolete local menu item #${local.id} (${local.name})")
                            }
                        }
                    }
                    for (remoteItem in remoteItems) {
                        try {
                            if (remoteItem.id > 0 &&
                                remoteItem.id !in pendingMenuDeleteIds &&
                                remoteItem.id !in pendingMenuUpsertIds
                            ) {
                                val localItem = menuDao.getMenuItemById(remoteItem.id)
                                if (localItem == null) menuDao.insertMenuItem(remoteItem)
                                else menuDao.updateMenuItem(remoteItem)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Supabase: Error syncing menu item: ${e.message}")
                        }
                    }
                }
                deduplicateMenuItems()
            }
        }

        // Listen to UPI account changes from Supabase
        scope.launch(Dispatchers.IO) {
            supabaseService.listenToUpiAccounts().collect { remoteAccounts ->
                val dao = upiAccountDao ?: return@collect
                for (remoteAccount in remoteAccounts) {
                    try {
                        if (remoteAccount.id > 0) {
                            dao.insert(remoteAccount)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error syncing UPI account: ${e.message}")
                    }
                }
            }
        }

        // Listen to inventory order changes from Supabase (WhatsApp orders)
        scope.launch(Dispatchers.IO) {
            val dao = inventoryDao ?: return@launch
            supabaseService.listenToInventoryOrders().collect { remoteOrders ->
                for ((order, items) in remoteOrders) {
                    try {
                        val existing = dao.getInventoryOrderById(order.orderId)
                        if (existing == null && items.isNotEmpty()) {
                            runInTransaction {
                                dao.insertInventoryOrder(order.copy(syncStatus = "SYNCED"))
                                for (item in items) {
                                    dao.insertInventoryOrderItem(item.copy(id = 0))
                                }
                            }
                            Log.d(TAG, "Supabase: Synced new inventory order: ${order.orderId}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error syncing inventory order: ${e.message}")
                    }
                }
            }
        }

        // Listen to inventory item changes from Supabase
        scope.launch(Dispatchers.IO) {
            val dao = inventoryDao ?: return@launch
            supabaseService.listenToInventoryItems().collect { remoteItems ->
                stockMutex.withLock {
                    for (remoteItem in remoteItems) {
                        try {
                            if (remoteItem.id > 0 && remoteItem.id !in dirtyInventoryItemIds) {
                                val localItem = dao.getInventoryItemById(remoteItem.id)
                                if (localItem == null) dao.insertInventoryItem(remoteItem)
                                else dao.updateInventoryItem(remoteItem)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Supabase: Error syncing inventory item: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen to inventory usage (recipes) from Supabase
        scope.launch(Dispatchers.IO) {
            val dao = inventoryDao ?: return@launch
            supabaseService.listenToInventoryUsage().collect { remoteUsages ->
                if (remoteUsages.isNotEmpty()) {
                    try {
                        val remoteKeys = remoteUsages.map { Pair(it.menuItemId, it.inventoryItemId) }.toSet()
                        val localUsages = dao.getAllUsagesSnapshot()
                        for (local in localUsages) {
                            val key = Pair(local.menuItemId, local.inventoryItemId)
                            if (key !in remoteKeys && key !in pendingUsageUpsertKeys) {
                                dao.deleteUsage(local)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error pruning stale local usages: ${e.message}")
                    }

                    for (remoteUsage in remoteUsages) {
                        try {
                            val key = Pair(remoteUsage.menuItemId, remoteUsage.inventoryItemId)
                            if (remoteUsage.id > 0 &&
                                remoteUsage.id !in pendingUsageDeleteIds &&
                                remoteUsage.menuItemId !in pendingUsageDeleteMenuIds &&
                                key !in pendingUsageUpsertKeys
                            ) {
                                val localUsage = dao.getUsageForMenuAndInventoryItem(remoteUsage.menuItemId, remoteUsage.inventoryItemId)
                                if (localUsage == null) {
                                    dao.insertUsage(remoteUsage)
                                } else if (localUsage.quantityUsed != remoteUsage.quantityUsed || localUsage.unit != remoteUsage.unit) {
                                    dao.updateUsage(remoteUsage.copy(id = localUsage.id))
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Supabase: Error syncing inventory usage: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen to customer changes from Supabase
        scope.launch(Dispatchers.IO) {
            val dao = customerDao ?: return@launch
            supabaseService.listenToCustomers().collect { remoteCustomers ->
                for (remoteCustomer in remoteCustomers) {
                    try {
                        val normalizedName = remoteCustomer.name.trim().lowercase(Locale.US)
                        if (remoteCustomer.id in pendingCustomerDeleteIds || normalizedName in pendingCustomerNames) {
                            continue
                        }
                        val localCustomer = dao.getCustomerByName(remoteCustomer.name.trim())
                        if (localCustomer == null) {
                            dao.insertCustomer(remoteCustomer.copy(name = remoteCustomer.name.trim()))
                        } else if (remoteCustomer.lastOrderTimestamp > localCustomer.lastOrderTimestamp ||
                            remoteCustomer.totalOrders > localCustomer.totalOrders) {
                            dao.updateCustomer(remoteCustomer.copy(id = localCustomer.id, name = localCustomer.name))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error syncing customer: ${e.message}")
                    }
                }
            }
        }

        Log.d(TAG, "Supabase real-time listeners started (orders, menu, UPI, inventory, customers)")
    }

    // ==================== FIRESTORE REAL-TIME LISTENERS (LEGACY) ====================

    fun startFirestoreListeners(scope: CoroutineScope) {
        managedScope = scope
        if (!firestoreService.isInitialized) return

        // Listen to order changes from other devices
        scope.launch(Dispatchers.IO) {
            firestoreService.listenToOrders().collect { remoteOrders ->
                for (remoteOrderStub in remoteOrders) {
                    try {
                        val orderId = remoteOrderStub.order.orderId
                        if (orderId in pendingOrderDeletions) continue
                        val localOrder = orderDao.getOrderById(orderId)

                        if (localOrder == null) {
                            // New order from another device — fetch full order with items
                            val fullOrder = firestoreService.getOrderWithItems(orderId)
                            if (fullOrder != null && fullOrder.items.isNotEmpty()) {
                                // Insert items with id=0 so Room auto-generates IDs
                                val itemsForRoom = fullOrder.items.map { it.copy(id = 0) }
                                orderDao.insertOrderWithItems(
                                    fullOrder.order.copy(syncStatus = "SYNCED"),
                                    itemsForRoom
                                )
                                Log.d(TAG, "Synced new remote order: $orderId (${itemsForRoom.size} items)")
                            }
                        } else {
                            val remoteOrder = remoteOrderStub.order.copy(syncStatus = "SYNCED")
                            val localItems = localOrder.items
                            val localIsPending = localOrder.order.syncStatus == "PENDING"

                            val itemsDiffer = localItems.isEmpty() ||
                                    localItems.size != remoteOrderStub.items.size && remoteOrderStub.items.isNotEmpty() ||
                                    kotlin.math.abs(localOrder.order.totalAmount - remoteOrder.totalAmount) > 0.01

                            if (itemsDiffer && !localIsPending) {
                                // Fetch full order with items to get the edited SKU list
                                val fullOrder = firestoreService.getOrderWithItems(orderId)
                                if (fullOrder != null && fullOrder.items.isNotEmpty()) {
                                    val itemsForRoom = fullOrder.items.map { it.copy(id = 0) }
                                    orderDao.updateOrderWithItems(remoteOrder, itemsForRoom)
                                    Log.d(TAG, "Firestore: Reconciled edited order items for $orderId (${itemsForRoom.size} items)")
                                } else {
                                    orderDao.updateOrder(remoteOrder)
                                }
                            } else if (!localIsPending) {
                                orderDao.updateOrder(remoteOrder)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error syncing remote order: ${e.message}")
                    }
                }
            }
        }

        // Listen to menu item changes from other devices
        scope.launch(Dispatchers.IO) {
            firestoreService.listenToMenuItems().collect { remoteItems ->
                if (remoteItems.isNotEmpty()) {
                    for (remoteItem in remoteItems) {
                        try {
                            if (remoteItem.id > 0) {
                                val localItem = menuDao.getMenuItemById(remoteItem.id)
                                if (localItem == null) {
                                    menuDao.insertMenuItem(remoteItem)
                                } else {
                                    menuDao.updateMenuItem(remoteItem)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error syncing remote menu item: ${e.message}")
                        }
                    }
                }
            }
        }

        // Listen to UPI account changes from other devices
        scope.launch(Dispatchers.IO) {
            firestoreService.listenToUpiAccounts().collect { remoteAccounts ->
                val dao = upiAccountDao ?: return@collect
                for (remoteAccount in remoteAccounts) {
                    try {
                        if (remoteAccount.id > 0) {
                            dao.insert(remoteAccount) // REPLACE strategy handles upsert
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error syncing remote UPI account: ${e.message}")
                    }
                }
            }
        }

        Log.d(TAG, "Firestore real-time listeners started")
    }

    // ==================== UPI MANAGEMENT ====================

    suspend fun getDefaultUpiAccount(): UpiAccountEntity? = upiAccountDao?.getDefaultUpiAccount()

    suspend fun addUpiAccount(vpa: String, payeeName: String, isDefault: Boolean) {
        val dao = upiAccountDao ?: return
        val count = dao.getAccountCount()
        val makeDefault = isDefault || count == 0
        if (makeDefault) {
            dao.clearDefaultFlags()
        }
        val newAccount = UpiAccountEntity(
            vpa = vpa.trim(),
            payeeName = payeeName.trim().ifBlank { "FILTR Coffee" },
            isDefault = makeDefault
        )
        val insertedId = dao.insert(newAccount)

        // Sync to Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveUpiAccount(newAccount.copy(id = insertedId.toInt()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync UPI account to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.saveUpiAccount(newAccount.copy(id = insertedId.toInt()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync UPI account to Firestore: ${e.message}")
            }
        }
    }

    suspend fun deleteUpiAccount(account: UpiAccountEntity) {
        val dao = upiAccountDao ?: return
        dao.delete(account)
        if (account.isDefault) {
            // Auto-promote the first remaining account to default
            val remaining = dao.getAccountCount()
            if (remaining > 0) {
                val firstAccount = dao.getFirstAccount()
                if (firstAccount != null) {
                    dao.setDefaultAccount(firstAccount.id)
                    Log.d(TAG, "Auto-promoted UPI account ${firstAccount.vpa} as new default")
                    // Sync promoted account to Firestore
                    if (supabaseService.isInitialized) {
                        try {
                            supabaseService.saveUpiAccount(firstAccount.copy(isDefault = true))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to sync promoted default UPI to Supabase: ${e.message}")
                        }
                    }
                    if (firestoreService.isInitialized) {
                        try {
                            firestoreService.saveUpiAccount(firstAccount.copy(isDefault = true))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to sync promoted default UPI to Firestore: ${e.message}")
                        }
                    }
                }
            }
        }

        // Sync to Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.deleteUpiAccount(account.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete UPI account from Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.deleteUpiAccount(account.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete UPI account from Firestore: ${e.message}")
            }
        }
    }

    suspend fun setDefaultUpiAccount(accountId: Int) {
        upiAccountDao?.setDefaultAccount(accountId)

        // Sync updated default flags to Firestore
        if (supabaseService.isInitialized) {
            try {
                val targetAccount = upiAccountDao?.getDefaultUpiAccount()
                if (targetAccount != null) {
                    supabaseService.saveUpiAccount(targetAccount)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync default UPI to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                val targetAccount = upiAccountDao?.getDefaultUpiAccount()
                if (targetAccount != null) {
                    firestoreService.saveUpiAccount(targetAccount)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync default UPI to Firestore: ${e.message}")
            }
        }
    }

    // ==================== MENU MANAGEMENT ====================

    suspend fun hasOrderHistoryForMenuItem(menuItem: MenuItemEntity): Boolean {
        return orderDao.countOrderItemsForMenuItem(menuItem.id, menuItem.name) > 0
    }

    suspend fun addMenuItem(item: MenuItemEntity): Long {
        val insertedId = menuSyncMutex.withLock {
            val id = menuDao.insertMenuItem(item)
            if (id > 0) pendingMenuUpsertIds.add(id.toInt())
            id
        }
        val persisted = item.copy(id = insertedId.toInt())

        // Sync to Supabase/Firestore
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.saveMenuItem(persisted)
                if (res.isSuccess) {
                    pendingMenuUpsertIds.remove(persisted.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item to Supabase: ${e.message}")
            }
        } else {
            pendingMenuUpsertIds.remove(persisted.id)
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.saveMenuItem(persisted)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item to Firestore: ${e.message}")
            }
        }

        return insertedId
    }

    suspend fun updateMenuItem(item: MenuItemEntity) {
        menuSyncMutex.withLock {
            menuDao.updateMenuItem(item)
            if (item.id > 0) pendingMenuUpsertIds.add(item.id)
        }

        // Sync to Supabase/Firestore
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.saveMenuItem(item)
                if (res.isSuccess) {
                    pendingMenuUpsertIds.remove(item.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item update to Supabase: ${e.message}")
            }
        } else {
            pendingMenuUpsertIds.remove(item.id)
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.saveMenuItem(item)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item update to Firestore: ${e.message}")
            }
        }
    }

    suspend fun deleteMenuItem(item: MenuItemEntity) {
        menuSyncMutex.withLock {
            pendingMenuUpsertIds.remove(item.id)
            if (item.id > 0) pendingMenuDeleteIds.add(item.id)
            menuDao.deleteMenuItem(item)
        }

        // Sync to Supabase/Firestore
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.deleteMenuItem(item.id)
                if (res.isSuccess) {
                    pendingMenuDeleteIds.remove(item.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete menu item from Supabase: ${e.message}")
            }
        } else {
            pendingMenuDeleteIds.remove(item.id)
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.deleteMenuItem(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete menu item from Firestore: ${e.message}")
            }
        }
    }

    suspend fun deduplicateMenuItems() {
        try {
            val duplicates = menuSyncMutex.withLock {
                val all = menuDao.getAllMenuItemsList()
                val usages = inventoryDao?.getAllUsagesSnapshot() ?: emptyList()
                val grouped = all.groupBy { it.name.trim().lowercase(Locale.US) }
                val dupes = mutableListOf<MenuItemEntity>()

                for ((_, group) in grouped) {
                    if (group.size > 1) {
                        // Prefer keeping the item that has recipe usages defined, then lowest ID
                        val canonical = group.find { item ->
                            usages.any { it.menuItemId == item.id || it.menuItemName.equals(item.name, ignoreCase = true) }
                        } ?: group.minByOrNull { it.id } ?: group.first()

                        for (item in group) {
                            if (item.id != canonical.id) {
                                dupes.add(item)
                            }
                        }
                    }
                }

                dupes.forEach {
                    menuDao.deleteMenuItem(it)
                    pendingMenuUpsertIds.remove(it.id)
                    if (it.id > 0) pendingMenuDeleteIds.add(it.id)
                    Log.d(TAG, "Deduplication: deleted duplicate menu item #${it.id} '${it.name}'")
                }
                dupes
            }

            duplicates.forEach {
                // Also delete from Supabase / Firestore
                if (supabaseService.isInitialized) {
                    try {
                        val res = supabaseService.deleteMenuItem(it.id)
                        if (res.isSuccess) pendingMenuDeleteIds.remove(it.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete duplicate from Supabase: ${e.message}")
                    }
                } else {
                    pendingMenuDeleteIds.remove(it.id)
                }
                if (firestoreService.isInitialized) {
                    try {
                        firestoreService.deleteMenuItem(it.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete duplicate from Firestore: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in deduplicateMenuItems: ${e.message}")
        }
    }

    suspend fun resetMenuToFiltrDefault() {
        val allItems = menuSyncMutex.withLock {
            try {
                menuDao.deleteAllMenuItems()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear old menu items: ${e.message}")
            }
            val defaultItems = listOf(
                // ICED COFFEE
                MenuItemEntity(name = "Americano", category = "ICED COFFEE", price = 79.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Vanila Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Hazelnut Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Caramel Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Vietnamese Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "OG Vietnamese", category = "ICED COFFEE", price = 109.0, description = "Milk + Whipped Cream + Flavour + Coffee"),

                // FRAPPE
                MenuItemEntity(name = "Just Frappe", category = "FRAPPE", price = 109.0, description = "Ice Cream + Coffee + Flavour"),
                MenuItemEntity(name = "Vanila Frappe", category = "FRAPPE", price = 129.0, description = "Ice Cream + Coffee + Flavour"),
                MenuItemEntity(name = "Hazelnut Frappe", category = "FRAPPE", price = 129.0, description = "Ice Cream + Coffee + Flavour"),
                MenuItemEntity(name = "Caramel Frappe", category = "FRAPPE", price = 129.0, description = "Ice Cream + Coffee + Flavour"),

                // FRIES
                MenuItemEntity(name = "Salted Fries", category = "FRIES", price = 89.0, description = "Potato + Deep Fry"),
                MenuItemEntity(name = "Peri Peri Fries", category = "FRIES", price = 99.0, description = "Potato + Deep Fry"),

                // SANDWICH
                MenuItemEntity(name = "Bombay", category = "SANDWICH", price = 89.0, description = "Fried Meshed Aaloo + Cheese (Extra Cheese Slice ₹15)"),
                MenuItemEntity(name = "Cheese Corn", category = "SANDWICH", price = 99.0, description = "Sweet Corn + Cheese (Extra Cheese Slice ₹15)"),
                MenuItemEntity(name = "Caramalized Onion", category = "SANDWICH", price = 109.0, description = "Caramel Onion + Cheese (Extra Cheese Slice ₹15)"),
                MenuItemEntity(name = "Paneer Sandwich", category = "SANDWICH", price = 119.0, description = "Paneer + Olive + Onion + Cucumber (Extra Cheese Slice ₹15)")
            )
            menuDao.insertAll(defaultItems)
            menuDao.getAllMenuItemsList()
        }

        // Sync defaults to Supabase / Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.syncAllMenuItems(allItems)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync default menu to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.syncAllMenuItems(allItems)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync default menu to Firestore: ${e.message}")
            }
        }
    }

    // ==================== ORDER ID GENERATION ====================

    // Generate sequential nomenclature order ID e.g. FLT-20260804-1017, FLT-20260804-1018
    suspend fun generateOrderId(webhookUrl: String = ""): String = orderIdMutex.withLock {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        var maxSequence = 1000

        // 1. Calculate max order sequence from local Room DB
        val localOrderIds = orderDao.getTodayOrderIds(startOfDay)
        localOrderIds.forEach { id ->
            val seqStr = id.substringAfterLast("-", "")
            val seq = seqStr.toIntOrNull()
            if (seq != null && seq > maxSequence) {
                maxSequence = seq
            }
        }

        // 2. Try Supabase atomic counter
        if (supabaseService.isInitialized) {
            try {
                val supabaseOrdersMax = supabaseService.getTodayMaxOrderSequence(datePart)
                if (supabaseOrdersMax > maxSequence) {
                    maxSequence = supabaseOrdersMax
                }

                // Use Supabase atomic transaction to get next sequence
                val nextSeqResult = supabaseService.getNextOrderSequence(datePart)
                if (nextSeqResult.isSuccess) {
                    val rpcSeq = nextSeqResult.getOrNull()
                    if (rpcSeq != null) {
                        var nextSeq = maxOf(rpcSeq, maxSequence + 1)
                        var candidateId = "FLT-$datePart-$nextSeq"

                        // Bounded verification against local Room DB
                        var attempts = 0
                        val maxAttempts = 5
                        while (orderDao.getOrderById(candidateId) != null && attempts < maxAttempts) {
                            attempts++
                            val retryResult = supabaseService.getNextOrderSequence(datePart)
                            val retrySeq = retryResult.getOrNull() ?: break
                            if (retrySeq <= nextSeq) break
                            nextSeq = retrySeq
                            candidateId = "FLT-$datePart-$nextSeq"
                        }

                        if (orderDao.getOrderById(candidateId) == null) {
                            return@withLock candidateId
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Supabase sequence generation failed, falling back: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                val firestoreMax = firestoreService.getCurrentMaxSequence(datePart)
                if (firestoreMax > maxSequence) {
                    maxSequence = firestoreMax
                }

                // Use Firestore atomic transaction to get next sequence
                val nextSeqResult = firestoreService.getNextOrderSequence(datePart)
                if (nextSeqResult.isSuccess) {
                    val fsSeq = nextSeqResult.getOrNull()
                    if (fsSeq != null) {
                        var nextSeq = maxOf(fsSeq, maxSequence + 1)
                        var candidateId = "FLT-$datePart-$nextSeq"

                        var attempts = 0
                        val maxAttempts = 5
                        while (orderDao.getOrderById(candidateId) != null && attempts < maxAttempts) {
                            attempts++
                            val retryResult = firestoreService.getNextOrderSequence(datePart)
                            val retrySeq = retryResult.getOrNull() ?: break
                            if (retrySeq <= nextSeq) break
                            nextSeq = retrySeq
                            candidateId = "FLT-$datePart-$nextSeq"
                        }

                        if (orderDao.getOrderById(candidateId) == null) {
                            return@withLock candidateId
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sequence generation failed, falling back: ${e.message}")
            }
        }

        // 3. Fallback: Query Google Sheets for cloud max sequence
        if (webhookUrl.isNotBlank()) {
            try {
                val remoteResult = sheetsSyncService.fetchLatestOrderInfoFromSheets(webhookUrl, datePart)
                if (remoteResult.isSuccess) {
                    val syncData = remoteResult.getOrNull()
                    if (syncData != null) {
                        if (syncData.maxSequence > maxSequence) {
                            maxSequence = syncData.maxSequence
                        }
                        // Sync missing remote orders from Google Sheets into local Room DB
                        for (remoteOrder in syncData.remoteOrders) {
                            if (remoteOrder.order.orderId !in pendingOrderDeletions &&
                                remoteOrder.items.isNotEmpty() &&
                                orderDao.getOrderById(remoteOrder.order.orderId) == null
                            ) {
                                orderDao.insertOrderWithItems(
                                    remoteOrder.order.copy(syncStatus = "SYNCED"),
                                    remoteOrder.items
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sheets sequence fallback failed: ${e.message}")
            }
        }

        var nextSequence = maxSequence + 1
        var candidateId = "FLT-$datePart-$nextSequence"

        // Ensure candidateId is unique in local Room DB (bounded)
        var localAttempts = 0
        while (orderDao.getOrderById(candidateId) != null && localAttempts < 1000) {
            nextSequence++
            localAttempts++
            candidateId = "FLT-$datePart-$nextSequence"
        }

        return@withLock candidateId
    }

    // ==================== ORDER CREATION & LIFECYCLE ====================

    suspend fun createOrder(
        customerName: String,
        contactNumber: String,
        cartItems: Map<MenuItemEntity, Int>,
        notes: String = "",
        webhookUrl: String = ""
    ): OrderWithItems {
        val orderId = generateOrderId(webhookUrl)
        val totalAmount = cartItems.entries.sumOf { (item, qty) -> item.price * qty }
        val name = if (customerName.isBlank()) "Walk-in Customer" else customerName.trim()

        val orderEntity = OrderEntity(
            orderId = orderId,
            customerName = name,
            contactNumber = contactNumber.trim(),
            totalAmount = totalAmount,
            timestamp = System.currentTimeMillis(),
            isMade = false,
            isPaid = false, // By default order is UNPAID
            paymentMode = "UNPAID",
            isDelivered = false,
            syncStatus = "PENDING",
            notes = notes
        )

        val itemEntities = cartItems.map { (item, qty) ->
            OrderItemEntity(
                orderId = orderId,
                menuItemId = item.id,
                itemName = item.name,
                itemPrice = item.price,
                quantity = qty,
                subtotal = item.price * qty
            )
        }

        // Save to Room DB first (instant, offline-first)
        orderDao.insertOrderWithItems(orderEntity, itemEntities)
        sheetsPendingOrderIds.add(orderId)

        // Get the order back with generated item IDs
        val savedOrder = orderDao.getOrderById(orderId) ?: OrderWithItems(orderEntity, itemEntities)

        // Push to remote
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.saveOrder(savedOrder)
                if (res.isSuccess) {
                    orderDao.updateSyncStatus(orderId, "SYNCED")
                } else {
                    orderDao.updateSyncStatus(orderId, "FAILED")
                    Log.w(TAG, "Failed to sync new order to Supabase: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                orderDao.updateSyncStatus(orderId, "FAILED")
                Log.w(TAG, "Failed to sync new order to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                val res = firestoreService.saveOrder(savedOrder)
                if (!supabaseService.isInitialized) {
                    if (res.isSuccess) {
                        orderDao.updateSyncStatus(orderId, "SYNCED")
                    } else {
                        orderDao.updateSyncStatus(orderId, "FAILED")
                        Log.w(TAG, "Failed to sync new order to Firestore: ${res.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                if (!supabaseService.isInitialized) {
                    orderDao.updateSyncStatus(orderId, "FAILED")
                }
                Log.w(TAG, "Failed to sync new order to Firestore: ${e.message}")
            }
        }

        // Track customer stats using managed scope
        launchManagedSync {
            recordCustomerOrder(name, contactNumber.trim(), totalAmount)
        }

        return savedOrder
    }

    suspend fun updateOrderMadeStatus(orderId: String, isMade: Boolean) {
        val now = if (isMade) System.currentTimeMillis() else 0L
        runInTransaction {
            orderDao.updateMadeStatus(orderId, isMade, completedTimestamp = now)
            if (isMade) {
                orderDao.markAllOrderItemsCompleted(orderId, completedTimestamp = now)
            } else {
                orderDao.resetAllOrderItemsCompleted(orderId)
            }
        }
        sheetsPendingOrderIds.add(orderId)

        // Sync made status + order_items completion to Supabase
        if (supabaseService.isInitialized) {
            try {
                val fieldsRes = supabaseService.updateOrderFields(orderId, mapOf(
                    "isMade" to isMade,
                    "completedTimestamp" to now,
                    "syncStatus" to "SYNCED"
                ))
                val itemsSuccess = if (isMade) {
                    val fullOrder = orderDao.getOrderById(orderId)
                    var allOk = true
                    fullOrder?.items?.forEach { item ->
                        val itemRes = supabaseService.updateOrderItemCompletion(
                            orderId = orderId,
                            itemName = item.itemName,
                            completedQuantity = item.quantity,
                            completedTimestamp = now
                        )
                        if (itemRes.isFailure) allOk = false
                    }
                    allOk
                } else {
                    supabaseService.updateAllOrderItemsCompletion(orderId, 0, 0L).isSuccess
                }
                if (fieldsRes.isSuccess && itemsSuccess) {
                    orderDao.updateSyncStatus(orderId, "SYNCED")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync made status to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                val fullOrder = orderDao.getOrderById(orderId)
                if (fullOrder != null) {
                    firestoreService.saveOrder(fullOrder)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync made status to Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateOrderItemCompletedQuantity(orderId: String, itemId: Int, completedQty: Int): OrderWithItems? {
        val now = System.currentTimeMillis()
        val completedTs = if (completedQty > 0) now else 0L
        orderDao.updateOrderItemCompletedQuantity(itemId, completedQty, completedTimestamp = completedTs)
        orderDao.updateSyncStatus(orderId, "PENDING")
        sheetsPendingOrderIds.add(orderId)
        val orderWithItems = orderDao.getOrderById(orderId)
        if (orderWithItems != null) {
            val allCompleted = orderWithItems.items.isNotEmpty() && orderWithItems.items.all { it.completedQuantity >= it.quantity }
            val orderCompletedTs = if (allCompleted) now else 0L
            val madeChanged = allCompleted != orderWithItems.order.isMade
            if (madeChanged) {
                orderDao.updateMadeStatus(orderId, allCompleted, completedTimestamp = orderCompletedTs)
            }

            val targetItem = orderWithItems.items.find { it.id == itemId }
            val itemName = targetItem?.itemName ?: ""

            // Sync item completion to Supabase
            if (supabaseService.isInitialized && itemName.isNotBlank()) {
                try {
                    val itemRes = supabaseService.updateOrderItemCompletion(orderId, itemName, completedQty, completedTs)
                    val fieldsOk = if (madeChanged) {
                        supabaseService.updateOrderFields(orderId, mapOf(
                            "isMade" to allCompleted,
                            "completedTimestamp" to orderCompletedTs,
                            "syncStatus" to "SYNCED"
                        )).isSuccess
                    } else true
                    if (itemRes.isSuccess && fieldsOk) {
                        orderDao.updateSyncStatus(orderId, "SYNCED")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync item completion to Supabase: ${e.message}")
                }
            }
            if (firestoreService.isInitialized) {
                try {
                    if (madeChanged) {
                        firestoreService.updateOrderFields(orderId, mapOf(
                            "isMade" to allCompleted,
                            "completedTimestamp" to orderCompletedTs
                        ))
                    }
                    firestoreService.updateOrderItemCompletion(orderId, itemId, completedQty, completedTs)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync item completion to Firestore: ${e.message}")
                }
            }

            return orderDao.getOrderById(orderId)
        }
        return orderWithItems
    }

    suspend fun updateOrderPaidStatus(orderId: String, isPaid: Boolean, paymentMode: String = "UNPAID") {
        orderDao.updatePaidStatus(orderId, isPaid, paymentMode)
        sheetsPendingOrderIds.add(orderId)

        // Sync to Supabase
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.updateOrderFields(orderId, mapOf(
                    "isPaid" to isPaid,
                    "paymentMode" to paymentMode,
                    "syncStatus" to "SYNCED"
                ))
                if (res.isSuccess) {
                    orderDao.updateSyncStatus(orderId, "SYNCED")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync paid status to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.updateOrderFields(orderId, mapOf(
                    "isPaid" to isPaid,
                    "paymentMode" to paymentMode
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync paid status to Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateOrderDeliveredStatus(orderId: String, isDelivered: Boolean) {
        orderDao.updateDeliveredStatus(orderId, isDelivered)
        sheetsPendingOrderIds.add(orderId)

        // Sync to Supabase
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.updateOrderFields(orderId, mapOf(
                    "isDelivered" to isDelivered,
                    "syncStatus" to "SYNCED"
                ))
                if (res.isSuccess) {
                    orderDao.updateSyncStatus(orderId, "SYNCED")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync delivered status to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.updateOrderFields(orderId, mapOf(
                    "isDelivered" to isDelivered
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync delivered status to Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateOrderWithItems(orderWithItems: OrderWithItems, updatedItems: List<OrderItemEntity>, webhookUrl: String = "") {
        val newTotal = updatedItems.sumOf { it.subtotal }
        val allCompleted = updatedItems.isNotEmpty() && updatedItems.all { it.completedQuantity >= it.quantity }
        val updatedOrder = orderWithItems.order.copy(
            totalAmount = newTotal,
            isMade = allCompleted,
            syncStatus = "PENDING"
        )
        val cleanItems = updatedItems.map { it.copy(orderId = updatedOrder.orderId) }
        orderDao.updateOrderWithItems(updatedOrder, cleanItems)
        sheetsPendingOrderIds.add(updatedOrder.orderId)

        // Sync to Supabase
        if (supabaseService.isInitialized) {
            try {
                val savedOrder = orderDao.getOrderById(updatedOrder.orderId)
                if (savedOrder != null) {
                    val res = supabaseService.saveOrder(savedOrder)
                    if (res.isSuccess) {
                        orderDao.updateSyncStatus(updatedOrder.orderId, "SYNCED")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync order update to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                val savedOrder = orderDao.getOrderById(updatedOrder.orderId)
                if (savedOrder != null) {
                    firestoreService.saveOrder(savedOrder)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync order update to Firestore: ${e.message}")
            }
        }

        // Sync to Google Sheets (using protected repository method so Supabase PENDING isn't overwritten)
        if (webhookUrl.isNotBlank()) {
            val latest = orderDao.getOrderById(updatedOrder.orderId) ?: OrderWithItems(updatedOrder, cleanItems)
            syncOrderToGoogleSheets(webhookUrl, latest)
        }
    }

    suspend fun deleteOrder(orderId: String, webhookUrl: String = "") {
        pendingOrderDeletions.add(orderId)
        sheetsPendingOrderIds.remove(orderId)
        runInTransaction {
            orderDao.deleteOrderItemsByOrderId(orderId)
            orderDao.deleteOrder(orderId)
        }

        // Delete from Supabase / Firestore
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.deleteOrder(orderId)
                if (res.isSuccess) {
                    pendingOrderDeletions.remove(orderId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete order from Supabase: ${e.message}")
            }
        } else {
            pendingOrderDeletions.remove(orderId)
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.deleteOrder(orderId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete order from Firestore: ${e.message}")
            }
        }

        // Delete from Google Sheets
        if (webhookUrl.isNotBlank()) {
            sheetsSyncService.deleteOrderFromGoogleSheets(webhookUrl, orderId)
        }
    }

    // ==================== GOOGLE SHEETS SYNC (PRESERVED) ====================

    private fun hasPrimaryCloudBackend(): Boolean =
        supabaseService.isInitialized || firestoreService.isInitialized

    suspend fun syncOrderToGoogleSheets(webhookUrl: String, orderWithItems: OrderWithItems): Result<String> {
        val result = sheetsSyncService.syncOrderToGoogleSheets(webhookUrl, orderWithItems)
        val orderId = orderWithItems.order.orderId
        if (result.isSuccess) {
            sheetsPendingOrderIds.remove(orderId)
            // Only write Room syncStatus when no primary cloud (Supabase/Firestore) owns it
            if (!hasPrimaryCloudBackend()) {
                orderDao.updateSyncStatus(orderId, "SYNCED")
            }
        } else {
            sheetsPendingOrderIds.add(orderId)
            if (!hasPrimaryCloudBackend()) {
                orderDao.updateSyncStatus(orderId, "FAILED")
            }
        }
        return result
    }

    suspend fun syncPendingOrdersToSupabase(): Result<String> {
        if (!supabaseService.isInitialized) return Result.failure(Exception("Supabase not initialized"))

        // 1. Retry pending order deletions so deleted orders cannot be resurrected
        for (delOrderId in pendingOrderDeletions.toList()) {
            val res = supabaseService.deleteOrder(delOrderId)
            if (res.isSuccess) {
                pendingOrderDeletions.remove(delOrderId)
            }
        }

        // 2. Retry unsynced orders
        val unsynced = orderDao.getUnsyncedOrders().filter { it.order.orderId !in pendingOrderDeletions }
        var syncedCount = 0
        for (orderWithItems in unsynced) {
            val res = supabaseService.saveOrder(orderWithItems)
            if (res.isSuccess) {
                orderDao.updateSyncStatus(orderWithItems.order.orderId, "SYNCED")
                syncedCount++
            }
        }

        // 3. Retry pending non-order mutations (menu, inventory, recipes, customers, checklist)
        for (menuId in pendingMenuUpsertIds.toList()) {
            val item = menuDao.getMenuItemById(menuId)
            if (item != null && supabaseService.saveMenuItem(item).isSuccess) {
                pendingMenuUpsertIds.remove(menuId)
            }
        }
        for (menuId in pendingMenuDeleteIds.toList()) {
            if (supabaseService.deleteMenuItem(menuId).isSuccess) {
                pendingMenuDeleteIds.remove(menuId)
            }
        }
        for (invId in dirtyInventoryItemIds.toList()) {
            val item = inventoryDao?.getInventoryItemById(invId)
            if (item != null && supabaseService.saveInventoryItem(item).isSuccess) {
                dirtyInventoryItemIds.remove(invId)
            }
        }
        for (menuId in pendingUsageDeleteMenuIds.toList()) {
            if (supabaseService.deleteInventoryUsagesForMenuItem(menuId).isSuccess) {
                pendingUsageDeleteMenuIds.remove(menuId)
            }
        }
        for (usageId in pendingUsageDeleteIds.toList()) {
            if (supabaseService.deleteInventoryUsage(usageId).isSuccess) {
                pendingUsageDeleteIds.remove(usageId)
            }
        }
        for (pair in pendingUsageUpsertKeys.toList()) {
            val usage = inventoryDao?.getUsageForMenuAndInventoryItem(pair.first, pair.second)
            if (usage != null && supabaseService.saveInventoryUsage(usage).isSuccess) {
                pendingUsageUpsertKeys.remove(pair)
            }
        }
        for (custId in pendingCustomerDeleteIds.toList()) {
            if (supabaseService.deleteCustomer(custId).isSuccess) {
                pendingCustomerDeleteIds.remove(custId)
            }
        }
        for (custName in pendingCustomerNames.toList()) {
            val cust = customerDao?.getCustomerByName(custName)
            if (cust != null && supabaseService.saveCustomer(cust).isSuccess) {
                pendingCustomerNames.remove(custName)
            }
        }
        for (dateKey in dirtyChecklistDateKeys.toList()) {
            checklistSyncMutex.withLock {
                syncChecklistCompletionsToCloud(dateKey)
            }
        }

        return Result.success("Synced $syncedCount pending orders to Supabase")
    }

    suspend fun syncPendingOrdersToGoogleSheets(webhookUrl: String): Result<String> {
        val roomUnsynced = orderDao.getUnsyncedOrders()
        val extraIds = sheetsPendingOrderIds.toList().filter { id ->
            id !in pendingOrderDeletions && roomUnsynced.none { it.order.orderId == id }
        }
        val extraOrders = if (extraIds.isNotEmpty()) orderDao.getOrdersByIds(extraIds) else emptyList()
        val unsynced = (roomUnsynced + extraOrders)
            .filter { it.order.orderId !in pendingOrderDeletions }
            .distinctBy { it.order.orderId }

        if (unsynced.isEmpty()) {
            return Result.success("All orders are already synced")
        }

        val hasPrimaryCloud = hasPrimaryCloudBackend()
        val batchResult = sheetsSyncService.syncBatchOrdersToGoogleSheets(webhookUrl, unsynced)
        if (batchResult.isSuccess) {
            val syncedIds = unsynced.map { it.order.orderId }
            syncedIds.forEach { sheetsPendingOrderIds.remove(it) }
            if (!hasPrimaryCloud) {
                orderDao.updateSyncStatusForOrders(syncedIds, "SYNCED")
            }
            return Result.success("Successfully synced ${unsynced.size} pending orders to Google Sheets")
        }

        // Fallback to sequential single order syncs if batch endpoint failed
        var syncedCount = 0
        unsynced.forEach { orderWithItems ->
            val id = orderWithItems.order.orderId
            val singleRes = sheetsSyncService.syncOrderToGoogleSheets(webhookUrl, orderWithItems)
            if (singleRes.isSuccess) {
                sheetsPendingOrderIds.remove(id)
                if (!hasPrimaryCloud) {
                    orderDao.updateSyncStatus(id, "SYNCED")
                }
                syncedCount++
            } else {
                sheetsPendingOrderIds.add(id)
                if (!hasPrimaryCloud) {
                    orderDao.updateSyncStatus(id, "FAILED")
                }
            }
        }

        return if (syncedCount > 0) {
            Result.success("Synced $syncedCount of ${unsynced.size} pending orders to Google Sheets")
        } else {
            batchResult
        }
    }

    suspend fun syncAllOrdersToGoogleSheets(webhookUrl: String, ordersList: List<OrderWithItems>, datePrefix: String = ""): Result<String> {
        val result = sheetsSyncService.syncAllOrdersToGoogleSheets(webhookUrl, ordersList, datePrefix)
        if (result.isSuccess) {
            val orderIds = ordersList.map { it.order.orderId }
            orderIds.forEach { sheetsPendingOrderIds.remove(it) }
            if (!hasPrimaryCloudBackend() && orderIds.isNotEmpty()) {
                orderDao.updateSyncStatusForOrders(orderIds, "SYNCED")
            }
        }
        return result
    }

    suspend fun syncItemMasterToGoogleSheets(webhookUrl: String, menuItems: List<MenuItemEntity>): Result<String> {
        // Also sync to Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.syncAllMenuItems(menuItems)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync item master to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.syncAllMenuItems(menuItems)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync item master to Firestore: ${e.message}")
            }
        }

        return sheetsSyncService.syncItemMasterToGoogleSheets(webhookUrl, menuItems)
    }

    suspend fun syncDateRangeAnalyticsToGoogleSheets(
        webhookUrl: String,
        startDateStr: String,
        endDateStr: String,
        ordersList: List<OrderWithItems>
    ): Result<String> {
        return sheetsSyncService.syncDateRangeAnalyticsToGoogleSheets(webhookUrl, startDateStr, endDateStr, ordersList)
    }

    // ==================== AI VOICE ORDER (PRESERVED) ====================

    suspend fun parseVoiceOrder(transcript: String, availableItems: List<MenuItemEntity>): Result<ParsedVoiceOrder> {
        return deepgramVoiceService.parseTranscriptToOrder(transcript, availableItems)
    }

    suspend fun parseVoiceAudio(audioBytes: ByteArray, mimeType: String, availableItems: List<MenuItemEntity>): Result<ParsedVoiceOrder> {
        return deepgramVoiceService.parseVoiceAudio(audioBytes, mimeType, availableItems)
    }

    suspend fun generateStoryNote(title: String, date: String = ""): Result<String> {
        return geminiVoiceService.generateStoryNote(title, date)
    }

    // ==================== RECEIPT STORY PHOTOS (Supabase Storage) ====================

    suspend fun uploadStoryPhoto(fileName: String, imageBytes: ByteArray): Result<String> {
        return supabaseService.uploadStoryPhoto(fileName, imageBytes)
    }

    suspend fun listStoryPhotos(): Result<List<String>> {
        return supabaseService.listStoryPhotos()
    }

    suspend fun deleteStoryPhoto(fileName: String): Result<Boolean> {
        return supabaseService.deleteStoryPhoto(fileName)
    }

    suspend fun downloadStoryPhoto(fileName: String): Result<ByteArray> {
        return supabaseService.downloadStoryPhoto(fileName)
    }

    fun getStoryPhotoUrl(fileName: String): String {
        return supabaseService.getStoryPhotoUrl(fileName)
    }

    suspend fun saveReceiptDesignConfig(jsonConfig: String): Result<String> {
        return supabaseService.saveReceiptDesignConfig(jsonConfig)
    }

    suspend fun fetchReceiptDesignConfig(): String? {
        return supabaseService.fetchReceiptDesignConfig()
    }

    // ==================== INVENTORY ====================

    suspend fun addInventoryItem(item: InventoryItemEntity): Long {
        val id = inventoryDao?.insertInventoryItem(item) ?: -1
        // Sync to Supabase
        if (id > 0 && supabaseService.isInitialized) {
            val intId = id.toInt()
            dirtyInventoryItemIds.add(intId)
            try {
                val res = supabaseService.saveInventoryItem(item.copy(id = intId))
                if (res.isSuccess) dirtyInventoryItemIds.remove(intId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory item to Supabase: ${e.message}")
            }
        }
        return id
    }

    suspend fun updateInventoryItem(item: InventoryItemEntity) {
        inventoryDao?.updateInventoryItem(item)
        if (supabaseService.isInitialized) {
            if (item.id > 0) dirtyInventoryItemIds.add(item.id)
            try {
                val res = supabaseService.saveInventoryItem(item)
                if (res.isSuccess) dirtyInventoryItemIds.remove(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory item update to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteInventoryItem(item: InventoryItemEntity) {
        dirtyInventoryItemIds.remove(item.id)
        inventoryDao?.deleteInventoryItem(item)
        if (supabaseService.isInitialized) {
            try {
                supabaseService.deleteInventoryItem(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory item deletion to Supabase: ${e.message}")
            }
        }
    }

    suspend fun getInventoryItemByName(name: String): InventoryItemEntity? {
        return inventoryDao?.getInventoryItemByName(name)
    }

    suspend fun addInventoryOrder(order: InventoryOrderEntity, items: List<InventoryOrderItemEntity>) {
        val affectedIds = mutableSetOf<Int>()
        stockMutex.withLock {
            runInTransaction {
                inventoryDao?.insertInventoryOrderWithItems(order, items)
                for (item in items) {
                    val masterItem = inventoryDao?.getInventoryItemByName(item.itemName)
                    if (masterItem != null && item.quantity > 0) {
                        val (packsToAdd, _) = calculatePacksAndCost(item.quantity, item.unit, masterItem)
                        if (packsToAdd > 0.0) {
                            inventoryDao?.addStock(masterItem.id, packsToAdd)
                            affectedIds.add(masterItem.id)
                        }
                    }
                }
            }
        }
        if (supabaseService.isInitialized) {
            for (itemId in affectedIds) {
                dirtyInventoryItemIds.add(itemId)
                try {
                    val updatedItem = inventoryDao?.getInventoryItemById(itemId)
                    if (updatedItem != null && supabaseService.updateInventoryStock(itemId, updatedItem.currentStock).isSuccess) {
                        dirtyInventoryItemIds.remove(itemId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync stock update to Supabase: ${e.message}")
                }
            }
            try {
                supabaseService.saveInventoryOrder(order, items)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory order to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteInventoryOrder(orderId: String) {
        val affectedIds = mutableSetOf<Int>()
        stockMutex.withLock {
            runInTransaction {
                val orderWithItems = inventoryDao?.getInventoryOrderWithItemsById(orderId)
                orderWithItems?.items?.forEach { item ->
                    val masterItem = inventoryDao?.getInventoryItemByName(item.itemName)
                    if (masterItem != null && item.quantity > 0) {
                        val (packsToDeduct, _) = calculatePacksAndCost(item.quantity, item.unit, masterItem)
                        if (packsToDeduct > 0.0) {
                            inventoryDao?.deductStock(masterItem.id, packsToDeduct)
                            affectedIds.add(masterItem.id)
                        }
                    }
                }
                inventoryDao?.deleteInventoryOrderWithItems(orderId)
            }
        }
        if (supabaseService.isInitialized) {
            for (itemId in affectedIds) {
                dirtyInventoryItemIds.add(itemId)
                try {
                    val updatedItem = inventoryDao?.getInventoryItemById(itemId)
                    if (updatedItem != null && supabaseService.updateInventoryStock(itemId, updatedItem.currentStock).isSuccess) {
                        dirtyInventoryItemIds.remove(itemId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync stock reversal to Supabase: ${e.message}")
                }
            }
            try {
                supabaseService.deleteInventoryOrder(orderId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory order deletion to Supabase: ${e.message}")
            }
        }
    }

    suspend fun getNextInventoryOrderId(): String = inventoryOrderIdMutex.withLock {
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val prefix = "INV-$datePrefix-"
        var maxSeq = inventoryDao?.getMaxSequenceForPrefix(prefix) ?: 0
        if (supabaseService.isInitialized) {
            val remoteMax = supabaseService.getTodayMaxInventoryOrderSequence(datePrefix)
            if (remoteMax > maxSeq) maxSeq = remoteMax
        }
        var nextSeq = maxSeq + 1
        var candidate = "$prefix${String.format(Locale.US, "%03d", nextSeq)}"
        var attempts = 0
        while (inventoryDao?.getInventoryOrderById(candidate) != null && attempts < 1000) {
            nextSeq++
            attempts++
            candidate = "$prefix${String.format(Locale.US, "%03d", nextSeq)}"
        }
        return@withLock candidate
    }

    // ==================== STOCK MANAGEMENT ====================

    suspend fun updateStock(itemId: Int, newStock: Double) {
        stockMutex.withLock {
            inventoryDao?.updateStock(itemId, newStock)
        }
        if (supabaseService.isInitialized) {
            dirtyInventoryItemIds.add(itemId)
            try {
                if (supabaseService.updateInventoryStock(itemId, newStock).isSuccess) {
                    dirtyInventoryItemIds.remove(itemId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync stock update to Supabase: ${e.message}")
            }
        }
    }

    suspend fun addStock(itemId: Int, amount: Double) {
        if (amount == 0.0) return
        val updatedStock = stockMutex.withLock {
            inventoryDao?.addStock(itemId, amount)
            inventoryDao?.getInventoryItemById(itemId)?.currentStock
        }
        if (supabaseService.isInitialized && updatedStock != null) {
            dirtyInventoryItemIds.add(itemId)
            try {
                if (supabaseService.updateInventoryStock(itemId, updatedStock).isSuccess) {
                    dirtyInventoryItemIds.remove(itemId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync stock addition to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deductStockForOrder(orderItems: List<OrderItemEntity>) {
        if (orderItems.isEmpty()) return
        val dao = inventoryDao ?: return

        val packsToDeductByItem = mutableMapOf<Int, Double>()
        stockMutex.withLock {
            runInTransaction {
                val allUsages = dao.getAllUsagesSnapshot()
                val usagesByMenuId = allUsages.groupBy { it.menuItemId }
                for (orderItem in orderItems) {
                    val usages = if (orderItem.menuItemId > 0) {
                        usagesByMenuId[orderItem.menuItemId].orEmpty()
                    } else {
                        allUsages.filter { it.menuItemName.equals(orderItem.itemName, ignoreCase = true) }
                    }
                    for (usage in usages) {
                        val totalUsed = usage.quantityUsed * orderItem.quantity
                        if (totalUsed <= 0.0) continue
                        val item = dao.getInventoryItemById(usage.inventoryItemId) ?: continue
                        val (packsToDeduct, _) = calculatePacksAndCost(totalUsed, usage.unit, item)
                        if (packsToDeduct > 0.0) {
                            packsToDeductByItem[usage.inventoryItemId] =
                                (packsToDeductByItem[usage.inventoryItemId] ?: 0.0) + packsToDeduct
                        }
                    }
                }
                for ((invId, totalPacks) in packsToDeductByItem) {
                    dao.deductStock(invId, totalPacks)
                }
            }
        }

        if (supabaseService.isInitialized) {
            for (invId in packsToDeductByItem.keys) {
                dirtyInventoryItemIds.add(invId)
                try {
                    val updatedItem = dao.getInventoryItemById(invId)
                    if (updatedItem != null && supabaseService.updateInventoryStock(invId, updatedItem.currentStock).isSuccess) {
                        dirtyInventoryItemIds.remove(invId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync stock deduction to Supabase: ${e.message}")
                }
            }
        }
    }

    // ==================== INVENTORY USAGE (RECIPES) ====================

    suspend fun getUsageForMenuItem(menuItemId: Int): List<InventoryUsageEntity> {
        return inventoryDao?.getUsageForMenuItem(menuItemId) ?: emptyList()
    }

    suspend fun getUsageForMenuAndInventoryItem(menuItemId: Int, inventoryItemId: Int): InventoryUsageEntity? {
        return inventoryDao?.getUsageForMenuAndInventoryItem(menuItemId, inventoryItemId)
    }

    suspend fun getInventoryItemById(id: Int): InventoryItemEntity? {
        return inventoryDao?.getInventoryItemById(id)
    }

    suspend fun saveUsage(usage: InventoryUsageEntity): Long {
        val key = Pair(usage.menuItemId, usage.inventoryItemId)
        pendingUsageDeleteMenuIds.remove(usage.menuItemId)
        val id = inventoryDao?.insertUsage(usage) ?: -1
        if (id > 0 && supabaseService.isInitialized) {
            pendingUsageUpsertKeys.add(key)
            try {
                if (supabaseService.saveInventoryUsage(usage.copy(id = id.toInt())).isSuccess) {
                    pendingUsageUpsertKeys.remove(key)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync usage to Supabase: ${e.message}")
            }
        }
        return id
    }

    suspend fun updateUsage(usage: InventoryUsageEntity) {
        val key = Pair(usage.menuItemId, usage.inventoryItemId)
        pendingUsageDeleteMenuIds.remove(usage.menuItemId)
        inventoryDao?.updateUsage(usage)
        if (supabaseService.isInitialized) {
            pendingUsageUpsertKeys.add(key)
            try {
                if (supabaseService.saveInventoryUsage(usage).isSuccess) {
                    pendingUsageUpsertKeys.remove(key)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync usage update to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteUsage(usage: InventoryUsageEntity) {
        pendingUsageUpsertKeys.remove(Pair(usage.menuItemId, usage.inventoryItemId))
        inventoryDao?.deleteUsage(usage)
        if (supabaseService.isInitialized && usage.id > 0) {
            pendingUsageDeleteIds.add(usage.id)
            try {
                if (supabaseService.deleteInventoryUsage(usage.id).isSuccess) {
                    pendingUsageDeleteIds.remove(usage.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync usage deletion to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteAllUsagesForMenuItem(menuItemId: Int) {
        pendingUsageUpsertKeys.removeAll { it.first == menuItemId }
        inventoryDao?.deleteAllUsagesForMenuItem(menuItemId)
        if (supabaseService.isInitialized) {
            pendingUsageDeleteMenuIds.add(menuItemId)
            try {
                if (supabaseService.deleteInventoryUsagesForMenuItem(menuItemId).isSuccess) {
                    pendingUsageDeleteMenuIds.remove(menuItemId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync deleteAllUsagesForMenuItem($menuItemId) to Supabase: ${e.message}")
            }
        }
    }

    // ==================== CUSTOMER MANAGEMENT ====================

    suspend fun searchCustomers(query: String): List<CustomerEntity> {
        return customerDao?.searchCustomers(query) ?: emptyList()
    }

    suspend fun saveCustomer(name: String, contactNumber: String = ""): Result<String> {
        val dao = customerDao ?: return Result.failure(Exception("Customer DAO not available"))
        val cleanName = name.trim()
        val cleanContact = contactNumber.trim()
        val normalizedKey = cleanName.lowercase(Locale.US)
        return try {
            val existing = dao.getCustomerByName(cleanName)
            if (existing != null) {
                return Result.failure(Exception("Customer '$cleanName' already exists"))
            }
            val customer = CustomerEntity(
                name = cleanName,
                contactNumber = cleanContact,
                createdAt = System.currentTimeMillis()
            )
            val insertedId = dao.insertCustomer(customer)
            val savedCustomer = customer.copy(id = insertedId.toInt())
            pendingCustomerNames.add(normalizedKey)
            launchManagedSync {
                customerSyncMutex.withLock {
                    try {
                        if (supabaseService.saveCustomer(savedCustomer).isSuccess) {
                            pendingCustomerNames.remove(normalizedKey)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync customer to Supabase: ${e.message}")
                    }
                }
            }
            Result.success(cleanName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity): Result<String> {
        val dao = customerDao ?: return Result.failure(Exception("Customer DAO not available"))
        val cleaned = customer.copy(name = customer.name.trim(), contactNumber = customer.contactNumber.trim())
        val normalizedKey = cleaned.name.lowercase(Locale.US)
        return try {
            dao.updateCustomer(cleaned)
            pendingCustomerNames.add(normalizedKey)
            launchManagedSync {
                customerSyncMutex.withLock {
                    try {
                        if (supabaseService.saveCustomer(cleaned).isSuccess) {
                            pendingCustomerNames.remove(normalizedKey)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync customer update to Supabase: ${e.message}")
                    }
                }
            }
            Result.success(cleaned.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity): Result<String> {
        val dao = customerDao ?: return Result.failure(Exception("Customer DAO not available"))
        return try {
            pendingCustomerNames.remove(customer.name.trim().lowercase(Locale.US))
            dao.deleteCustomer(customer)
            if (customer.id > 0) {
                pendingCustomerDeleteIds.add(customer.id)
                launchManagedSync {
                    customerSyncMutex.withLock {
                        try {
                            if (supabaseService.deleteCustomer(customer.id).isSuccess) {
                                pendingCustomerDeleteIds.remove(customer.id)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete customer from Supabase: ${e.message}")
                        }
                    }
                }
            }
            Result.success(customer.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordCustomerOrder(customerName: String, contactNumber: String, amount: Double) {
        val cleanName = customerName.trim()
        val cleanContact = contactNumber.trim()
        if (cleanName.isBlank() || cleanName == "Walk-in Customer") return
        val dao = customerDao ?: return
        val normalizedKey = cleanName.lowercase(Locale.US)
        try {
            val savedCustomer = runInTransaction {
                val existing = dao.getCustomerByName(cleanName)
                val now = System.currentTimeMillis()
                if (existing != null) {
                    dao.incrementOrderStats(cleanName, amount, now, cleanContact)
                    dao.getCustomerByName(cleanName)
                } else {
                    val newCustomer = CustomerEntity(
                        name = cleanName,
                        contactNumber = cleanContact,
                        totalOrders = 1,
                        totalSpent = amount,
                        lastOrderTimestamp = now,
                        createdAt = now
                    )
                    val insertedId = dao.insertCustomer(newCustomer)
                    newCustomer.copy(id = insertedId.toInt())
                }
            }
            if (savedCustomer != null) {
                pendingCustomerNames.add(normalizedKey)
                launchManagedSync {
                    customerSyncMutex.withLock {
                        try {
                            if (supabaseService.saveCustomer(savedCustomer).isSuccess) {
                                pendingCustomerNames.remove(normalizedKey)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to sync recorded customer order to Supabase: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recording customer order: ${e.message}")
        }
    }

    // ==================== FIXED COSTS & P&L ====================

    suspend fun saveFixedCost(cost: FixedCostEntity) {
        fixedCostDao?.insertOrUpdate(cost)
    }

    suspend fun getFixedCostForMonth(monthKey: String): FixedCostEntity? {
        return fixedCostDao?.getFixedCostByMonth(monthKey)
    }

    suspend fun deleteFixedCost(monthKey: String) {
        fixedCostDao?.deleteByMonth(monthKey)
    }

    // ==================== DAILY STORE CHECKLIST ====================

    suspend fun toggleChecklistTask(
        taskId: String,
        dateKey: String,
        completedBy: String = "Staff"
    ): Result<Unit> {
        return try {
            val existing = checklistDao?.getCompletion(dateKey, taskId)
            val newStatus = !(existing?.isCompleted ?: false)
            val now = System.currentTimeMillis()
            val completion = ChecklistCompletionEntity(
                dateKey = dateKey,
                taskId = taskId,
                isCompleted = newStatus,
                completedTimestamp = if (newStatus) now else 0L,
                completedBy = completedBy,
                isOutOfStock = existing?.isOutOfStock ?: false
            )
            checklistDao?.insertOrUpdateCompletion(completion)
            localChecklistActionTimestamps[dateKey to taskId] = now
            dirtyChecklistDateKeys.add(dateKey)

            // Asynchronously sync completions to cloud metadata on managed scope
            launchManagedSync {
                checklistSyncMutex.withLock {
                    try {
                        syncChecklistCompletionsToCloud(dateKey)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync checklist completions: ${e.message}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleChecklistTask error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun toggleChecklistTask(
        taskId: String,
        dateKey: String,
        isCompleted: Boolean,
        completedBy: String = "Staff"
    ): Result<Unit> {
        return try {
            val existing = checklistDao?.getCompletion(dateKey, taskId)
            val now = System.currentTimeMillis()
            val completion = ChecklistCompletionEntity(
                dateKey = dateKey,
                taskId = taskId,
                isCompleted = isCompleted,
                completedTimestamp = if (isCompleted) now else 0L,
                completedBy = completedBy,
                isOutOfStock = existing?.isOutOfStock ?: false
            )
            checklistDao?.insertOrUpdateCompletion(completion)
            localChecklistActionTimestamps[dateKey to taskId] = now
            dirtyChecklistDateKeys.add(dateKey)

            // Asynchronously sync completions to cloud metadata on managed scope
            launchManagedSync {
                checklistSyncMutex.withLock {
                    try {
                        syncChecklistCompletionsToCloud(dateKey)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync checklist completions: ${e.message}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleChecklistTask error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun addChecklistTask(
        sectionId: String,
        sectionName: String,
        title: String,
        description: String = ""
    ): Result<ChecklistTaskEntity> {
        return try {
            val id = "custom_" + System.currentTimeMillis()
            val task = ChecklistTaskEntity(
                id = id,
                sectionId = sectionId,
                sectionName = sectionName,
                title = title.trim(),
                description = description.trim(),
                isDefault = false,
                orderIndex = 100,
                createdAt = System.currentTimeMillis()
            )
            pendingChecklistTaskAdds.add(id)
            pendingChecklistTaskDeletes.remove(id)
            checklistDao?.insertTask(task)

            // Push custom tasks to cloud metadata on managed scope
            launchManagedSync {
                checklistSyncMutex.withLock {
                    try {
                        syncCustomTasksToCloud()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync custom tasks: ${e.message}")
                    }
                }
            }
            Result.success(task)
        } catch (e: Exception) {
            Log.e(TAG, "addChecklistTask error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteChecklistTask(taskId: String): Result<Unit> {
        return try {
            pendingChecklistTaskAdds.remove(taskId)
            pendingChecklistTaskDeletes.add(taskId)
            checklistDao?.deleteTaskById(taskId)
            launchManagedSync {
                checklistSyncMutex.withLock {
                    try {
                        syncCustomTasksToCloud()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync custom task deletion: ${e.message}")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChecklistTask(task: ChecklistTaskEntity): Result<Unit> {
        return deleteChecklistTask(task.id)
    }

    suspend fun syncChecklistCompletionsToCloud(dateKey: String) {
        val dao = checklistDao ?: return
        val completions = dao.getCompletionsForDateSync(dateKey)
        val arr = JSONArray()
        completions.forEach { c ->
            val updatedAt = maxOf(c.completedTimestamp, localChecklistActionTimestamps[dateKey to c.taskId] ?: 0L)
            val obj = JSONObject().apply {
                put("taskId", c.taskId)
                put("isCompleted", c.isCompleted)
                put("completedTimestamp", c.completedTimestamp)
                put("updatedAt", updatedAt)
                put("completedBy", c.completedBy)
                put("isOutOfStock", c.isOutOfStock)
            }
            arr.put(obj)
        }
        val res = supabaseService.saveCloudChecklistCompletions(dateKey, arr.toString())
        if (res.isSuccess) {
            dirtyChecklistDateKeys.remove(dateKey)
        }
    }

    suspend fun syncCustomTasksToCloud() {
        val dao = checklistDao ?: return
        val allTasks = dao.getAllTasksSync().filter { !it.isDefault }
        val arr = JSONArray()
        allTasks.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("sectionId", t.sectionId)
                put("sectionName", t.sectionName)
                put("title", t.title)
                put("description", t.description)
                put("orderIndex", t.orderIndex)
                put("isWeeklyMondayOnly", t.isWeeklyMondayOnly)
                put("createdAt", t.createdAt)
            }
            arr.put(obj)
        }
        val res = supabaseService.saveCloudCustomTasks(arr.toString())
        if (res.isSuccess) {
            allTasks.forEach { pendingChecklistTaskAdds.remove(it.id) }
            pendingChecklistTaskDeletes.clear()
        }
    }

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        val value = optString(key, fallback)
        return if (value == "null") fallback else value
    }

    suspend fun reconcileChecklistFromCloud(dateKey: String) {
        val dao = checklistDao ?: return
        checklistSyncMutex.withLock {
            try {
                // 1. Reconcile custom tasks (adding new cloud tasks and removing deleted custom tasks)
                val customTasksJson = supabaseService.fetchCloudCustomTasks()
                if (!customTasksJson.isNullOrBlank() && customTasksJson.startsWith("[")) {
                    val arr = JSONArray(customTasksJson)
                    val cloudTasks = mutableListOf<ChecklistTaskEntity>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val taskId = o.optNonNullString("id", "")
                        if (taskId.isBlank() || taskId in pendingChecklistTaskDeletes) continue
                        cloudTasks.add(
                            ChecklistTaskEntity(
                                id = taskId,
                                sectionId = o.optNonNullString("sectionId", "01_OPENING"),
                                sectionName = o.optNonNullString("sectionName", "01. Opening & Daily Preparation"),
                                title = o.optNonNullString("title", ""),
                                description = o.optNonNullString("description", ""),
                                isDefault = false,
                                orderIndex = o.optInt("orderIndex", 100),
                                isWeeklyMondayOnly = o.optBoolean("isWeeklyMondayOnly", false),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                    val cloudIds = cloudTasks.map { it.id }.toSet()
                    val localCustomTasks = dao.getAllTasksSync().filter { !it.isDefault }
                    for (localTask in localCustomTasks) {
                        if (localTask.id !in cloudIds && localTask.id !in pendingChecklistTaskAdds) {
                            dao.deleteTaskById(localTask.id)
                        }
                    }
                    if (cloudTasks.isNotEmpty()) {
                        dao.insertTasks(cloudTasks)
                    }
                    if (pendingChecklistTaskAdds.isNotEmpty() || pendingChecklistTaskDeletes.isNotEmpty()) {
                        syncCustomTasksToCloud()
                    }
                } else if (pendingChecklistTaskAdds.isNotEmpty() || pendingChecklistTaskDeletes.isNotEmpty()) {
                    syncCustomTasksToCloud()
                }

                // 2. Reconcile completions for dateKey using timestamp-based merge
                val completionsJson = supabaseService.fetchCloudChecklistCompletions(dateKey)
                if (!completionsJson.isNullOrBlank() && completionsJson.startsWith("[")) {
                    val arr = JSONArray(completionsJson)
                    val toSaveLocally = mutableListOf<ChecklistCompletionEntity>()
                    var localHasNewerChanges = dirtyChecklistDateKeys.contains(dateKey)

                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val taskId = o.optNonNullString("taskId", "")
                        if (taskId.isBlank()) continue
                        val cloudCompleted = o.optBoolean("isCompleted", false)
                        val cloudCompletedTs = o.optLong("completedTimestamp", 0L)
                        val cloudUpdatedAt = o.optLong("updatedAt", cloudCompletedTs)

                        val cloudEntity = ChecklistCompletionEntity(
                            dateKey = dateKey,
                            taskId = taskId,
                            isCompleted = cloudCompleted,
                            completedTimestamp = cloudCompletedTs,
                            completedBy = o.optNonNullString("completedBy", "Staff").ifBlank { "Staff" },
                            isOutOfStock = o.optBoolean("isOutOfStock", false)
                        )

                        val local = dao.getCompletion(dateKey, taskId)
                        val localUpdatedAt = maxOf(
                            local?.completedTimestamp ?: 0L,
                            localChecklistActionTimestamps[dateKey to taskId] ?: 0L
                        )

                        when {
                            local == null -> {
                                toSaveLocally.add(cloudEntity)
                                localChecklistActionTimestamps[dateKey to taskId] = cloudUpdatedAt
                            }
                            cloudUpdatedAt > localUpdatedAt -> {
                                toSaveLocally.add(cloudEntity)
                                localChecklistActionTimestamps[dateKey to taskId] = cloudUpdatedAt
                            }
                            localUpdatedAt > cloudUpdatedAt -> {
                                localHasNewerChanges = true
                            }
                            // Equal timestamps: adopt cloud completion if cloud is completed while local has no recorded action
                            cloudCompleted && !local.isCompleted && localUpdatedAt == 0L -> {
                                toSaveLocally.add(cloudEntity)
                            }
                        }
                    }
                    if (toSaveLocally.isNotEmpty()) {
                        dao.insertOrUpdateCompletions(toSaveLocally)
                    }
                    if (localHasNewerChanges) {
                        syncChecklistCompletionsToCloud(dateKey)
                    }
                } else if (dirtyChecklistDateKeys.contains(dateKey)) {
                    syncChecklistCompletionsToCloud(dateKey)
                }
            } catch (e: Exception) {
                Log.w(TAG, "reconcileChecklistFromCloud failed: ${e.message}")
            }
        }
    }
}

