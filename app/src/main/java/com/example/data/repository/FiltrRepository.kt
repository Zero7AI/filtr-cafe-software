package com.example.data.repository

import android.util.Log
import com.example.data.dao.ChecklistDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.FixedCostDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.MenuDao
import com.example.data.dao.OrderDao
import com.example.data.dao.UpiAccountDao
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
import com.example.data.remote.FirestoreService
import com.example.data.remote.SupabaseService
import com.example.data.remote.GeminiVoiceOrderService
import com.example.data.remote.GoogleSheetsSyncService
import com.example.data.remote.ParsedVoiceOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val supabaseService: SupabaseService = SupabaseService.getInstance()
) {
    companion object {
        private const val TAG = "FiltrRepository"
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
        if (!supabaseService.isInitialized) return

        // Listen to order changes from Supabase (polling every 4s)
        scope.launch(Dispatchers.IO) {
            supabaseService.listenToOrders().collect { remoteOrders ->
                for (remoteOrder in remoteOrders) {
                    try {
                        val orderId = remoteOrder.order.orderId
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
                                // If local device is not currently mid-edit, adopt the edited order items from the other device
                                if (!localIsPending) {
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

                                if (statusDiffers) {
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
                if (remoteItems.isNotEmpty()) {
                    val remoteIds = remoteItems.map { it.id }.toSet()
                    val localItems = menuDao.getAllMenuItemsList()
                    // Remove obsolete local items that are no longer in Supabase
                    for (local in localItems) {
                        if (local.id !in remoteIds) {
                            menuDao.deleteMenuItem(local)
                            Log.d(TAG, "Removed obsolete local menu item #${local.id} (${local.name})")
                        }
                    }
                }
                for (remoteItem in remoteItems) {
                    try {
                        if (remoteItem.id > 0) {
                            val localItem = menuDao.getMenuItemById(remoteItem.id)
                            if (localItem == null) menuDao.insertMenuItem(remoteItem)
                            else menuDao.updateMenuItem(remoteItem)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error syncing menu item: ${e.message}")
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
                        if (existing == null) {
                            dao.insertInventoryOrder(order.copy(syncStatus = "SYNCED"))
                            for (item in items) {
                                dao.insertInventoryOrderItem(item.copy(id = 0))
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
                for (remoteItem in remoteItems) {
                    try {
                        if (remoteItem.id > 0) {
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

        // Listen to inventory usage (recipes) from Supabase
        scope.launch(Dispatchers.IO) {
            val dao = inventoryDao ?: return@launch
            supabaseService.listenToInventoryUsage().collect { remoteUsages ->
                if (remoteUsages.isNotEmpty()) {
                    try {
                        val remoteKeys = remoteUsages.map { Pair(it.menuItemId, it.inventoryItemId) }.toSet()
                        val localUsages = dao.getAllUsagesSnapshot()
                        for (local in localUsages) {
                            if (Pair(local.menuItemId, local.inventoryItemId) !in remoteKeys) {
                                dao.deleteUsage(local)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supabase: Error pruning stale local usages: ${e.message}")
                    }

                    for (remoteUsage in remoteUsages) {
                        try {
                            if (remoteUsage.id > 0) {
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
                        val localCustomer = dao.getCustomerByName(remoteCustomer.name)
                        if (localCustomer == null) {
                            dao.insertCustomer(remoteCustomer)
                        } else if (remoteCustomer.lastOrderTimestamp > localCustomer.lastOrderTimestamp ||
                            remoteCustomer.totalOrders > localCustomer.totalOrders) {
                            dao.updateCustomer(remoteCustomer.copy(id = localCustomer.id))
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
        if (!firestoreService.isInitialized) return

        // Listen to order changes from other devices
        scope.launch(Dispatchers.IO) {
            firestoreService.listenToOrders().collect { remoteOrders ->
                for (remoteOrderStub in remoteOrders) {
                    try {
                        val orderId = remoteOrderStub.order.orderId
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

    suspend fun addMenuItem(item: MenuItemEntity): Long {
        val insertedId = menuDao.insertMenuItem(item)

        // Sync to Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveMenuItem(item.copy(id = insertedId.toInt()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.saveMenuItem(item.copy(id = insertedId.toInt()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item to Firestore: ${e.message}")
            }
        }

        return insertedId
    }

    suspend fun updateMenuItem(item: MenuItemEntity) {
        menuDao.updateMenuItem(item)

        // Sync to Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveMenuItem(item)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync menu item update to Supabase: ${e.message}")
            }
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
        menuDao.deleteMenuItem(item)

        // Sync to Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.deleteMenuItem(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete menu item from Supabase: ${e.message}")
            }
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
            val all = menuDao.getAllMenuItemsList()
            val usages = inventoryDao?.getAllUsagesSnapshot() ?: emptyList()
            val grouped = all.groupBy { it.name.trim().lowercase() }
            val duplicates = mutableListOf<MenuItemEntity>()

            for ((_, group) in grouped) {
                if (group.size > 1) {
                    // Prefer keeping the item that has recipe usages defined, then lowest ID
                    val canonical = group.find { item -> usages.any { it.menuItemId == item.id || it.menuItemName.equals(item.name, ignoreCase = true) } }
                        ?: group.minByOrNull { it.id }
                        ?: group.first()

                    for (item in group) {
                        if (item.id != canonical.id) {
                            duplicates.add(item)
                        }
                    }
                }
            }

            duplicates.forEach {
                menuDao.deleteMenuItem(it)
                Log.d(TAG, "Deduplication: deleted duplicate menu item #${it.id} '${it.name}'")
                // Also delete from Supabase / Firestore
                if (supabaseService.isInitialized) {
                    try {
                        supabaseService.deleteMenuItem(it.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete duplicate from Supabase: ${e.message}")
                    }
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

        // Sync defaults to Firestore
        if (supabaseService.isInitialized) {
            try {
                val allItems = menuDao.getAllMenuItemsList()
                supabaseService.syncAllMenuItems(allItems)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync default menu to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                val allItems = menuDao.getAllMenuItemsList()
                firestoreService.syncAllMenuItems(allItems)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync default menu to Firestore: ${e.message}")
            }
        }
    }

    // ==================== ORDER ID GENERATION ====================

    // Generate sequential nomenclature order ID e.g. FLT-20260804-1017, FLT-20260804-1018
    suspend fun generateOrderId(webhookUrl: String = ""): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
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
                    var nextSeq = nextSeqResult.getOrNull() ?: (maxSequence + 1)
                    // If zero orders exist today locally and remotely, ensure first order is 1001
                    if (localOrderIds.isEmpty() && supabaseOrdersMax <= 1000 && nextSeq > 1001) {
                        nextSeq = 1001
                    }
                    var candidateId = "FLT-$datePart-$nextSeq"

                    // Verify unique in local Room DB
                    while (orderDao.getOrderById(candidateId) != null) {
                        // Extremely rare edge case — get another sequence
                        val retryResult = supabaseService.getNextOrderSequence(datePart)
                        if (retryResult.isSuccess) {
                            candidateId = "FLT-$datePart-${retryResult.getOrNull() ?: break}"
                        } else {
                            break
                        }
                    }

                    return candidateId
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
                    val nextSeq = nextSeqResult.getOrNull() ?: (maxSequence + 1)
                    var candidateId = "FLT-$datePart-$nextSeq"

                    // Verify unique in local Room DB
                    while (orderDao.getOrderById(candidateId) != null) {
                        // Extremely rare edge case — get another sequence
                        val retryResult = firestoreService.getNextOrderSequence(datePart)
                        if (retryResult.isSuccess) {
                            candidateId = "FLT-$datePart-${retryResult.getOrNull() ?: break}"
                        } else {
                            break
                        }
                    }

                    return candidateId
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sequence generation failed, falling back: ${e.message}")
            }
        }

        // 3. Fallback: Query Google Sheets for cloud max sequence (original logic)
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
                            if (orderDao.getOrderById(remoteOrder.order.orderId) == null) {
                                orderDao.insertOrderWithItems(
                                    remoteOrder.order.copy(syncStatus = "SYNCED"),
                                    remoteOrder.items
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        var nextSequence = maxSequence + 1
        var candidateId = "FLT-$datePart-$nextSequence"

        // Ensure candidateId is unique in local Room DB
        while (orderDao.getOrderById(candidateId) != null) {
            nextSequence++
            candidateId = "FLT-$datePart-$nextSequence"
        }

        return candidateId
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

        // Get the order back with generated item IDs
        val savedOrder = orderDao.getOrderById(orderId) ?: OrderWithItems(orderEntity, itemEntities)

        // Push to Firestore in background (non-blocking)
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveOrder(savedOrder)
                orderDao.updateSyncStatus(orderId, "SYNCED")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync new order to Supabase: ${e.message}")
            }
        }
        if (firestoreService.isInitialized) {
            try {
                firestoreService.saveOrder(savedOrder)
                orderDao.updateSyncStatus(orderId, "SYNCED")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync new order to Firestore: ${e.message}")
            }
        }

        // Track customer stats (non-blocking)
        CoroutineScope(Dispatchers.IO).launch {
            recordCustomerOrder(name, contactNumber.trim(), totalAmount)
        }

        return savedOrder
    }

    suspend fun updateOrderMadeStatus(orderId: String, isMade: Boolean) {
        val now = if (isMade) System.currentTimeMillis() else 0L
        orderDao.updateMadeStatus(orderId, isMade, completedTimestamp = now)
        if (isMade) {
            orderDao.markAllOrderItemsCompleted(orderId, completedTimestamp = now)
        } else {
            orderDao.resetAllOrderItemsCompleted(orderId)
        }

        // Sync made status to Supabase via field update (non-destructive)
        if (supabaseService.isInitialized) {
            try {
                val res = supabaseService.updateOrderFields(orderId, mapOf(
                    "isMade" to isMade,
                    "completedTimestamp" to now,
                    "syncStatus" to "SYNCED"
                ))
                if (res.isSuccess) {
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
        val orderWithItems = orderDao.getOrderById(orderId)
        if (orderWithItems != null) {
            val allCompleted = orderWithItems.items.isNotEmpty() && orderWithItems.items.all { it.completedQuantity >= it.quantity }
            val orderCompletedTs = if (allCompleted) now else 0L
            if (allCompleted != orderWithItems.order.isMade) {
                orderDao.updateMadeStatus(orderId, allCompleted, completedTimestamp = orderCompletedTs)
            }

            val targetItem = orderWithItems.items.find { it.id == itemId }
            val itemName = targetItem?.itemName ?: ""

            // Sync item completion to Supabase
            if (supabaseService.isInitialized && itemName.isNotBlank()) {
                try {
                    val res = supabaseService.updateOrderItemCompletion(orderId, itemName, completedQty, completedTs)
                    if (allCompleted != orderWithItems.order.isMade) {
                        supabaseService.updateOrderFields(orderId, mapOf(
                            "isMade" to allCompleted,
                            "completedTimestamp" to orderCompletedTs,
                            "syncStatus" to "SYNCED"
                        ))
                    }
                    if (res.isSuccess) {
                        orderDao.updateSyncStatus(orderId, "SYNCED")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync item completion to Supabase: ${e.message}")
                }
            }
            if (firestoreService.isInitialized) {
                try {
                    if (allCompleted != orderWithItems.order.isMade) {
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

        // Sync to Google Sheets
        if (webhookUrl.isNotBlank()) {
            sheetsSyncService.syncOrderToGoogleSheets(webhookUrl, OrderWithItems(updatedOrder, updatedItems))
        }
    }

    suspend fun deleteOrder(orderId: String, webhookUrl: String = "") {
        orderDao.deleteOrderItemsByOrderId(orderId)
        orderDao.deleteOrder(orderId)

        // Delete from Firestore
        if (supabaseService.isInitialized) {
            try {
                supabaseService.deleteOrder(orderId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete order from Supabase: ${e.message}")
            }
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

    suspend fun syncOrderToGoogleSheets(webhookUrl: String, orderWithItems: OrderWithItems): Result<String> {
        val result = sheetsSyncService.syncOrderToGoogleSheets(webhookUrl, orderWithItems)
        if (result.isSuccess) {
            orderDao.updateSyncStatus(orderWithItems.order.orderId, "SYNCED")
        } else {
            orderDao.updateSyncStatus(orderWithItems.order.orderId, "FAILED")
        }
        return result
    }

    suspend fun syncPendingOrdersToSupabase(): Result<String> {
        if (!supabaseService.isInitialized) return Result.failure(Exception("Supabase not initialized"))
        val unsynced = orderDao.getUnsyncedOrders()
        if (unsynced.isEmpty()) return Result.success("All orders already synced to Supabase")
        var syncedCount = 0
        for (orderWithItems in unsynced) {
            val res = supabaseService.saveOrder(orderWithItems)
            if (res.isSuccess) {
                orderDao.updateSyncStatus(orderWithItems.order.orderId, "SYNCED")
                syncedCount++
            }
        }
        return Result.success("Synced $syncedCount pending orders to Supabase")
    }

    suspend fun syncPendingOrdersToGoogleSheets(webhookUrl: String): Result<String> {
        val unsynced = orderDao.getUnsyncedOrders()
        if (unsynced.isEmpty()) {
            return Result.success("All orders are already synced")
        }

        val batchResult = sheetsSyncService.syncBatchOrdersToGoogleSheets(webhookUrl, unsynced)
        if (batchResult.isSuccess) {
            orderDao.updateSyncStatusForOrders(unsynced.map { it.order.orderId }, "SYNCED")
            return Result.success("Successfully synced ${unsynced.size} pending orders to Google Sheets")
        }

        // Fallback to sequential single order syncs if batch endpoint failed
        var syncedCount = 0
        unsynced.forEach { orderWithItems ->
            val singleRes = sheetsSyncService.syncOrderToGoogleSheets(webhookUrl, orderWithItems)
            if (singleRes.isSuccess) {
                orderDao.updateSyncStatus(orderWithItems.order.orderId, "SYNCED")
                syncedCount++
            } else {
                orderDao.updateSyncStatus(orderWithItems.order.orderId, "FAILED")
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
            if (orderIds.isNotEmpty()) {
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
        return geminiVoiceService.parseVoiceOrder(transcript, availableItems)
    }

    suspend fun parseVoiceAudio(audioBytes: ByteArray, mimeType: String, availableItems: List<MenuItemEntity>): Result<ParsedVoiceOrder> {
        return geminiVoiceService.parseVoiceAudio(audioBytes, mimeType, availableItems)
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
            try {
                supabaseService.saveInventoryItem(item.copy(id = id.toInt()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory item to Supabase: ${e.message}")
            }
        }
        return id
    }

    suspend fun updateInventoryItem(item: InventoryItemEntity) {
        inventoryDao?.updateInventoryItem(item)
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveInventoryItem(item)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory item update to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteInventoryItem(item: InventoryItemEntity) {
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
        inventoryDao?.insertInventoryOrderWithItems(order, items)
        // Auto-add stock for each item
        for (item in items) {
            val masterItem = inventoryDao?.getInventoryItemByName(item.itemName)
            if (masterItem != null && item.quantity > 0) {
                inventoryDao?.addStock(masterItem.id, item.quantity)
                // Sync updated stock to Supabase
                if (supabaseService.isInitialized) {
                    try {
                        val updatedItem = inventoryDao?.getInventoryItemById(masterItem.id)
                        if (updatedItem != null) {
                            supabaseService.updateInventoryStock(masterItem.id, updatedItem.currentStock)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync stock update to Supabase: ${e.message}")
                    }
                }
            }
        }
        // Sync order to Supabase
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveInventoryOrder(order, items)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory order to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteInventoryOrder(orderId: String) {
        // Reverse stock additions before deleting
        val orderWithItems = inventoryDao?.getInventoryOrderWithItemsById(orderId)
        orderWithItems?.items?.forEach { item ->
            val masterItem = inventoryDao?.getInventoryItemByName(item.itemName)
            if (masterItem != null && item.quantity > 0) {
                inventoryDao?.deductStock(masterItem.id, item.quantity)
                // Sync updated stock to Supabase
                if (supabaseService.isInitialized) {
                    try {
                        val updatedItem = inventoryDao?.getInventoryItemById(masterItem.id)
                        if (updatedItem != null) {
                            supabaseService.updateInventoryStock(masterItem.id, updatedItem.currentStock)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync stock reversal to Supabase: ${e.message}")
                    }
                }
            }
        }
        inventoryDao?.deleteInventoryOrderWithItems(orderId)
        // Sync deletion to Supabase
        if (supabaseService.isInitialized) {
            try {
                supabaseService.deleteInventoryOrder(orderId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync inventory order deletion to Supabase: ${e.message}")
            }
        }
    }

    suspend fun getNextInventoryOrderId(): String {
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val prefix = "INV-$datePrefix-"
        val maxSeq = inventoryDao?.getMaxSequenceForPrefix(prefix) ?: 0
        val nextSeq = maxSeq + 1
        return "$prefix${String.format("%03d", nextSeq)}"
    }

    // ==================== STOCK MANAGEMENT ====================

    suspend fun updateStock(itemId: Int, newStock: Double) {
        inventoryDao?.updateStock(itemId, newStock)
        if (supabaseService.isInitialized) {
            try {
                supabaseService.updateInventoryStock(itemId, newStock)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync stock update to Supabase: ${e.message}")
            }
        }
    }

    suspend fun addStock(itemId: Int, amount: Double) {
        inventoryDao?.addStock(itemId, amount)
        if (supabaseService.isInitialized) {
            try {
                val updatedItem = inventoryDao?.getInventoryItemById(itemId)
                if (updatedItem != null) {
                    supabaseService.updateInventoryStock(itemId, updatedItem.currentStock)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync stock addition to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deductStockForOrder(orderItems: List<OrderItemEntity>) {
        for (orderItem in orderItems) {
            val usages = inventoryDao?.getUsageForMenuItem(orderItem.menuItemId) ?: emptyList()
            for (usage in usages) {
                val totalUsed = usage.quantityUsed * orderItem.quantity
                val item = inventoryDao?.getInventoryItemById(usage.inventoryItemId)
                if (item != null) {
                    val (packsToDeduct, _) = calculatePacksAndCost(totalUsed, usage.unit, item)
                    inventoryDao?.deductStock(usage.inventoryItemId, packsToDeduct)
                    // Sync updated stock to Supabase
                    if (supabaseService.isInitialized) {
                        try {
                            val updatedItem = inventoryDao?.getInventoryItemById(usage.inventoryItemId)
                            if (updatedItem != null) {
                                supabaseService.updateInventoryStock(usage.inventoryItemId, updatedItem.currentStock)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to sync stock deduction to Supabase: ${e.message}")
                        }
                    }
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
        val id = inventoryDao?.insertUsage(usage) ?: -1
        if (id > 0 && supabaseService.isInitialized) {
            try {
                supabaseService.saveInventoryUsage(usage.copy(id = id.toInt()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync usage to Supabase: ${e.message}")
            }
        }
        return id
    }

    suspend fun updateUsage(usage: InventoryUsageEntity) {
        inventoryDao?.updateUsage(usage)
        if (supabaseService.isInitialized) {
            try {
                supabaseService.saveInventoryUsage(usage)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync usage update to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteUsage(usage: InventoryUsageEntity) {
        inventoryDao?.deleteUsage(usage)
        if (supabaseService.isInitialized) {
            try {
                supabaseService.deleteInventoryUsage(usage.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync usage deletion to Supabase: ${e.message}")
            }
        }
    }

    suspend fun deleteAllUsagesForMenuItem(menuItemId: Int) {
        inventoryDao?.deleteAllUsagesForMenuItem(menuItemId)
    }

    // ==================== CUSTOMER MANAGEMENT ====================

    suspend fun searchCustomers(query: String): List<CustomerEntity> {
        return customerDao?.searchCustomers(query) ?: emptyList()
    }

    suspend fun saveCustomer(name: String, contactNumber: String = ""): Result<String> {
        val dao = customerDao ?: return Result.failure(Exception("Customer DAO not available"))
        return try {
            val existing = dao.getCustomerByName(name)
            if (existing != null) {
                return Result.failure(Exception("Customer '$name' already exists"))
            }
            val customer = CustomerEntity(
                name = name,
                contactNumber = contactNumber,
                createdAt = System.currentTimeMillis()
            )
            dao.insertCustomer(customer)
            CoroutineScope(Dispatchers.IO).launch {
                try { supabaseService.saveCustomer(customer) } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync customer to Supabase: ${e.message}")
                }
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity): Result<String> {
        val dao = customerDao ?: return Result.failure(Exception("Customer DAO not available"))
        return try {
            dao.updateCustomer(customer)
            CoroutineScope(Dispatchers.IO).launch {
                try { supabaseService.saveCustomer(customer) } catch (_: Exception) {}
            }
            Result.success(customer.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity): Result<String> {
        val dao = customerDao ?: return Result.failure(Exception("Customer DAO not available"))
        return try {
            dao.deleteCustomer(customer)
            CoroutineScope(Dispatchers.IO).launch {
                try { supabaseService.deleteCustomer(customer.id) } catch (_: Exception) {}
            }
            Result.success(customer.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordCustomerOrder(customerName: String, contactNumber: String, amount: Double) {
        if (customerName.isBlank() || customerName == "Walk-in Customer") return
        val dao = customerDao ?: return
        try {
            val existing = dao.getCustomerByName(customerName)
            val now = System.currentTimeMillis()
            if (existing != null) {
                dao.incrementOrderStats(customerName, amount, now, contactNumber)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val updated = dao.getCustomerByName(customerName)
                        if (updated != null) supabaseService.saveCustomer(updated)
                    } catch (_: Exception) {}
                }
            } else {
                val newCustomer = CustomerEntity(
                    name = customerName,
                    contactNumber = contactNumber,
                    totalOrders = 1,
                    totalSpent = amount,
                    lastOrderTimestamp = now,
                    createdAt = now
                )
                dao.insertCustomer(newCustomer)
                CoroutineScope(Dispatchers.IO).launch {
                    try { supabaseService.saveCustomer(newCustomer) } catch (_: Exception) {}
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
            val completion = ChecklistCompletionEntity(
                dateKey = dateKey,
                taskId = taskId,
                isCompleted = newStatus,
                completedTimestamp = if (newStatus) System.currentTimeMillis() else 0L,
                completedBy = completedBy
            )
            checklistDao?.insertOrUpdateCompletion(completion)

            // Asynchronously sync completions to cloud metadata
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    syncChecklistCompletionsToCloud(dateKey)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync checklist completions: ${e.message}")
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
            val completion = ChecklistCompletionEntity(
                dateKey = dateKey,
                taskId = taskId,
                isCompleted = isCompleted,
                completedTimestamp = if (isCompleted) System.currentTimeMillis() else 0L,
                completedBy = completedBy
            )
            checklistDao?.insertOrUpdateCompletion(completion)

            // Asynchronously sync completions to cloud metadata
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    syncChecklistCompletionsToCloud(dateKey)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync checklist completions: ${e.message}")
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
            checklistDao?.insertTask(task)

            // Push custom tasks to cloud metadata
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    syncCustomTasksToCloud()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync custom tasks: ${e.message}")
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
            checklistDao?.deleteTaskById(taskId)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    syncCustomTasksToCloud()
                } catch (_: Exception) {}
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
            val obj = JSONObject().apply {
                put("taskId", c.taskId)
                put("isCompleted", c.isCompleted)
                put("completedTimestamp", c.completedTimestamp)
                put("completedBy", c.completedBy)
                put("isOutOfStock", c.isOutOfStock)
            }
            arr.put(obj)
        }
        supabaseService.saveCloudChecklistCompletions(dateKey, arr.toString())
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
        supabaseService.saveCloudCustomTasks(arr.toString())
    }

    suspend fun reconcileChecklistFromCloud(dateKey: String) {
        val dao = checklistDao ?: return
        try {
            // 1. Reconcile custom tasks
            val customTasksJson = supabaseService.fetchCloudCustomTasks()
            if (!customTasksJson.isNullOrBlank() && customTasksJson.startsWith("[")) {
                val arr = JSONArray(customTasksJson)
                val cloudTasks = mutableListOf<ChecklistTaskEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    cloudTasks.add(
                        ChecklistTaskEntity(
                            id = o.getString("id"),
                            sectionId = o.optString("sectionId", "01_OPENING"),
                            sectionName = o.optString("sectionName", "01. Opening & Daily Preparation"),
                            title = o.getString("title"),
                            description = o.optString("description", ""),
                            isDefault = false,
                            orderIndex = o.optInt("orderIndex", 100),
                            isWeeklyMondayOnly = o.optBoolean("isWeeklyMondayOnly", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (cloudTasks.isNotEmpty()) {
                    dao.insertTasks(cloudTasks)
                }
            }

            // 2. Reconcile completions for dateKey
            val completionsJson = supabaseService.fetchCloudChecklistCompletions(dateKey)
            if (!completionsJson.isNullOrBlank() && completionsJson.startsWith("[")) {
                val arr = JSONArray(completionsJson)
                val cloudCompletions = mutableListOf<ChecklistCompletionEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    cloudCompletions.add(
                        ChecklistCompletionEntity(
                            dateKey = dateKey,
                            taskId = o.getString("taskId"),
                            isCompleted = o.optBoolean("isCompleted", false),
                            completedTimestamp = o.optLong("completedTimestamp", 0L),
                            completedBy = o.optString("completedBy", "Staff"),
                            isOutOfStock = o.optBoolean("isOutOfStock", false)
                        )
                    )
                }
                if (cloudCompletions.isNotEmpty()) {
                    dao.insertOrUpdateCompletions(cloudCompletions)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "reconcileChecklistFromCloud failed: ${e.message}")
        }
    }
}

