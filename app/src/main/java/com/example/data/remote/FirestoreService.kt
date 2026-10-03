package com.example.data.remote

import android.util.Log
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.UpiAccountEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FirestoreService handles all Firebase Firestore operations for the FILTR Billing app.
 *
 * Collections:
 *   - orders/{orderId}           → Order documents
 *   - orders/{orderId}/items/{autoId} → Order item sub-documents
 *   - menu_items/{itemId}        → Menu item documents
 *   - upi_accounts/{accountId}   → UPI account documents
 *   - sync_metadata/order_sequence → Atomic order sequence counter
 *
 * Features:
 *   - Real-time snapshot listeners for cross-device sync
 *   - Atomic order ID generation via Firestore transactions
 *   - Offline persistence (built into Firestore SDK)
 *   - Graceful degradation if Firebase is not configured
 */
class FirestoreService private constructor() {

    private var db: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var deviceId: String = ""
    private var _isInitialized = false

    // Active listener registrations (for cleanup)
    private val activeListeners = mutableListOf<ListenerRegistration>()

    val isInitialized: Boolean get() = _isInitialized

    companion object {
        private const val TAG = "FirestoreService"

        // Collection names
        private const val ORDERS_COLLECTION = "orders"
        private const val ORDER_ITEMS_SUBCOLLECTION = "items"
        private const val MENU_ITEMS_COLLECTION = "menu_items"
        private const val UPI_ACCOUNTS_COLLECTION = "upi_accounts"
        private const val SYNC_METADATA_COLLECTION = "sync_metadata"
        private const val ORDER_SEQUENCE_DOC = "order_sequence"

        @Volatile
        private var INSTANCE: FirestoreService? = null

        fun getInstance(): FirestoreService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreService().also { INSTANCE = it }
            }
        }
    }

    /**
     * Initialize Firestore and anonymous auth.
     * Returns true if initialization succeeded, false otherwise.
     */
    suspend fun initialize(): Boolean {
        if (_isInitialized) return true

        return try {
            // Initialize Firebase App Check for API security
            try {
                val appCheckFactory = com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory.getInstance()
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory)
                Log.d(TAG, "Firebase App Check initialized")
            } catch (e: Exception) {
                Log.w(TAG, "App Check init skipped (debug/sideload): ${e.message}")
            }

            db = FirebaseFirestore.getInstance()

            // Enable offline persistence with larger cache
            val settings = FirebaseFirestoreSettings.Builder()
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            db?.firestoreSettings = settings

            // Sign in anonymously for security rules
            auth = FirebaseAuth.getInstance()
            if (auth?.currentUser == null) {
                auth?.signInAnonymously()?.await()
            }

            deviceId = auth?.currentUser?.uid ?: android.os.Build.MODEL.replace(" ", "_")
            _isInitialized = true
            Log.d(TAG, "Firestore initialized successfully. Device ID: $deviceId")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firestore initialization failed (Firebase may not be configured): ${e.message}")
            _isInitialized = false
            false
        }
    }

    // ==================== ORDER OPERATIONS ====================

    /**
     * Save or update an order and its items to Firestore.
     */
    suspend fun saveOrder(orderWithItems: OrderWithItems): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val order = orderWithItems.order
            val orderRef = firestore.collection(ORDERS_COLLECTION).document(order.orderId)

            val orderData = orderToMap(order)
            orderRef.set(orderData, SetOptions.merge()).await()

            // Save items as subcollection
            val itemsRef = orderRef.collection(ORDER_ITEMS_SUBCOLLECTION)

            // Delete existing items first to handle updates cleanly
            val existingItems = itemsRef.get().await()
            for (doc in existingItems.documents) {
                doc.reference.delete().await()
            }

            // Write new items
            for (item in orderWithItems.items) {
                val itemData = orderItemToMap(item)
                if (item.id > 0) {
                    itemsRef.document(item.id.toString()).set(itemData).await()
                } else {
                    itemsRef.add(itemData).await()
                }
            }

            Log.d(TAG, "Order ${order.orderId} saved to Firestore")
            Result.success("Order saved to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save order: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update only specific order fields (for status changes).
     */
    suspend fun updateOrderFields(orderId: String, fields: Map<String, Any>): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val mutableFields = fields.toMutableMap()
            mutableFields["lastModified"] = FieldValue.serverTimestamp()
            mutableFields["lastModifiedBy"] = deviceId

            firestore.collection(ORDERS_COLLECTION)
                .document(orderId)
                .update(mutableFields)
                .await()

            Result.success("Order $orderId updated in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update order fields: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update order item completion status in Firestore.
     */
    suspend fun updateOrderItemCompletion(
        orderId: String,
        itemId: Int,
        completedQty: Int,
        completedTimestamp: Long
    ): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val itemsRef = firestore.collection(ORDERS_COLLECTION)
                .document(orderId)
                .collection(ORDER_ITEMS_SUBCOLLECTION)
                .document(itemId.toString())

            itemsRef.update(
                mapOf(
                    "completedQuantity" to completedQty,
                    "completedTimestamp" to completedTimestamp
                )
            ).await()

            Result.success("Order item updated in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update order item: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete an order and all its items from Firestore.
     */
    suspend fun deleteOrder(orderId: String): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val orderRef = firestore.collection(ORDERS_COLLECTION).document(orderId)

            // Delete subcollection items first
            val items = orderRef.collection(ORDER_ITEMS_SUBCOLLECTION).get().await()
            for (doc in items.documents) {
                doc.reference.delete().await()
            }

            // Delete order document
            orderRef.delete().await()

            Log.d(TAG, "Order $orderId deleted from Firestore")
            Result.success("Order deleted from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete order: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Listen to real-time order changes from Firestore.
     * Emits a list of OrderWithItems whenever any order changes.
     */
    fun listenToOrders(): Flow<List<OrderWithItems>> = callbackFlow {
        val firestore = db ?: run {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        // Real-time listener syncs last 7 days for live POS operations & multi-device sync
        // For older date ranges, use fetchOrdersForDateRange() on-demand when user picks dates
        val syncWindowMs = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val listener = firestore.collection(ORDERS_COLLECTION)
            .whereGreaterThan("timestamp", syncWindowMs)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Orders listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val orders = mutableListOf<OrderWithItems>()
                    for (doc in snapshots.documents) {
                        try {
                            val order = mapToOrder(doc)
                            if (order != null) {
                                // Items will be fetched separately to avoid nested async in listener
                                orders.add(OrderWithItems(order, emptyList()))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing order doc ${doc.id}: ${e.message}")
                        }
                    }
                    trySend(orders)
                }
            }

        activeListeners.add(listener)
        awaitClose {
            listener.remove()
            activeListeners.remove(listener)
        }
    }

    /**
     * Fetch a single order with its items from Firestore.
     */
    suspend fun getOrderWithItems(orderId: String): OrderWithItems? {
        val firestore = db ?: return null

        return try {
            val orderDoc = firestore.collection(ORDERS_COLLECTION)
                .document(orderId)
                .get()
                .await()

            val order = mapToOrder(orderDoc) ?: return null

            val itemsDocs = firestore.collection(ORDERS_COLLECTION)
                .document(orderId)
                .collection(ORDER_ITEMS_SUBCOLLECTION)
                .get()
                .await()

            val items = itemsDocs.documents.mapNotNull { mapToOrderItem(it, orderId) }
            OrderWithItems(order, items)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch order $orderId: ${e.message}")
            null
        }
    }

    // ==================== ON-DEMAND DATE RANGE FETCH ====================

    /**
     * Fetch all orders within a date range from Firestore (one-shot query).
     * Called when user picks a custom date range in the dashboard calendar.
     * Results are backfilled into Room DB by the repository layer.
     */
    suspend fun fetchOrdersForDateRange(startMs: Long, endMs: Long): List<OrderWithItems> {
        val firestore = db ?: return emptyList()

        return try {
            val snapshot = firestore.collection(ORDERS_COLLECTION)
                .whereGreaterThanOrEqualTo("timestamp", startMs)
                .whereLessThanOrEqualTo("timestamp", endMs)
                .get()
                .await()

            val orders = mutableListOf<OrderWithItems>()
            for (doc in snapshot.documents) {
                val order = mapToOrder(doc) ?: continue
                // Fetch items subcollection for each order
                val itemsDocs = doc.reference.collection(ORDER_ITEMS_SUBCOLLECTION)
                    .get()
                    .await()
                val items = itemsDocs.documents.mapNotNull { mapToOrderItem(it, order.orderId) }
                orders.add(OrderWithItems(order, items))
            }

            Log.d(TAG, "Fetched ${orders.size} orders for date range $startMs..$endMs")
            orders
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch orders for date range: ${e.message}")
            emptyList()
        }
    }

    // ==================== ATOMIC ORDER ID GENERATION ====================

    /**
     * Get the next order sequence number using a Firestore transaction.
     * This is atomic and prevents ID collisions across devices.
     *
     * @param datePart The date string e.g. "20260806"
     * @return The next sequence number (e.g. 1019)
     */
    suspend fun getNextOrderSequence(datePart: String): Result<Int> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val seqRef = firestore.collection(SYNC_METADATA_COLLECTION).document(ORDER_SEQUENCE_DOC)

            val nextSeq = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(seqRef)
                val sequenceMap = snapshot.get("maxSequence") as? Map<*, *> ?: emptyMap<String, Any>()
                val currentMax = (sequenceMap[datePart] as? Number)?.toInt() ?: 1000
                val nextSequence = currentMax + 1

                // Update the sequence atomically
                val updatedMap = sequenceMap.toMutableMap()
                updatedMap[datePart] = nextSequence

                transaction.set(
                    seqRef,
                    mapOf(
                        "maxSequence" to updatedMap,
                        "lastUpdated" to FieldValue.serverTimestamp(),
                        "lastUpdatedBy" to deviceId
                    ),
                    SetOptions.merge()
                )

                nextSequence
            }.await()

            Result.success(nextSeq)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get next order sequence: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the current max order sequence without incrementing (for read-only checks).
     */
    suspend fun getCurrentMaxSequence(datePart: String): Int {
        val firestore = db ?: return 1000

        return try {
            val seqDoc = firestore.collection(SYNC_METADATA_COLLECTION)
                .document(ORDER_SEQUENCE_DOC)
                .get()
                .await()

            val sequenceMap = seqDoc.get("maxSequence") as? Map<*, *> ?: emptyMap<String, Any>()
            (sequenceMap[datePart] as? Number)?.toInt() ?: 1000
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get current max sequence: ${e.message}")
            1000
        }
    }

    // ==================== MENU ITEM OPERATIONS ====================

    /**
     * Save a menu item to Firestore.
     */
    suspend fun saveMenuItem(item: MenuItemEntity): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val data = menuItemToMap(item)
            firestore.collection(MENU_ITEMS_COLLECTION)
                .document(item.id.toString())
                .set(data)
                .await()
            Result.success("Menu item saved to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save menu item: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete a menu item from Firestore.
     */
    suspend fun deleteMenuItem(itemId: Int): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            firestore.collection(MENU_ITEMS_COLLECTION)
                .document(itemId.toString())
                .delete()
                .await()
            Result.success("Menu item deleted from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete menu item: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sync all menu items to Firestore (replaces all).
     */
    suspend fun syncAllMenuItems(items: List<MenuItemEntity>): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val batch = firestore.batch()

            // Write all items
            for (item in items) {
                val ref = firestore.collection(MENU_ITEMS_COLLECTION).document(item.id.toString())
                batch.set(ref, menuItemToMap(item))
            }

            batch.commit().await()
            Result.success("All menu items synced to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync menu items: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Listen to real-time menu item changes from Firestore.
     */
    fun listenToMenuItems(): Flow<List<MenuItemEntity>> = callbackFlow {
        val firestore = db ?: run {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val listener = firestore.collection(MENU_ITEMS_COLLECTION)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Menu items listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val items = snapshots.documents.mapNotNull { doc ->
                        try {
                            mapToMenuItem(doc)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing menu item doc ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    trySend(items)
                }
            }

        activeListeners.add(listener)
        awaitClose {
            listener.remove()
            activeListeners.remove(listener)
        }
    }

    // ==================== UPI ACCOUNT OPERATIONS ====================

    /**
     * Save a UPI account to Firestore.
     */
    suspend fun saveUpiAccount(account: UpiAccountEntity): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            val data = upiAccountToMap(account)
            firestore.collection(UPI_ACCOUNTS_COLLECTION)
                .document(account.id.toString())
                .set(data)
                .await()
            Result.success("UPI account saved to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save UPI account: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete a UPI account from Firestore.
     */
    suspend fun deleteUpiAccount(accountId: Int): Result<String> {
        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))

        return try {
            firestore.collection(UPI_ACCOUNTS_COLLECTION)
                .document(accountId.toString())
                .delete()
                .await()
            Result.success("UPI account deleted from Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete UPI account: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Listen to real-time UPI account changes from Firestore.
     */
    fun listenToUpiAccounts(): Flow<List<UpiAccountEntity>> = callbackFlow {
        val firestore = db ?: run {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val listener = firestore.collection(UPI_ACCOUNTS_COLLECTION)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "UPI accounts listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val accounts = snapshots.documents.mapNotNull { doc ->
                        try {
                            mapToUpiAccount(doc)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing UPI account doc ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    trySend(accounts)
                }
            }

        activeListeners.add(listener)
        awaitClose {
            listener.remove()
            activeListeners.remove(listener)
        }
    }

    // ==================== CLEANUP ====================

    /**
     * Remove all active listeners.
     */
    fun removeAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // ==================== DATA MAPPERS ====================

    private fun orderToMap(order: OrderEntity): Map<String, Any?> {
        return mapOf(
            "orderId" to order.orderId,
            "customerName" to order.customerName,
            "contactNumber" to order.contactNumber,
            "totalAmount" to order.totalAmount,
            "timestamp" to order.timestamp,
            "isMade" to order.isMade,
            "isPaid" to order.isPaid,
            "isDelivered" to order.isDelivered,
            "notes" to order.notes,
            "paymentMode" to order.paymentMode,
            "completedTimestamp" to order.completedTimestamp,
            "syncStatus" to order.syncStatus,
            "lastModified" to FieldValue.serverTimestamp(),
            "lastModifiedBy" to deviceId
        )
    }

    private fun mapToOrder(doc: DocumentSnapshot): OrderEntity? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

        return try {
            OrderEntity(
                orderId = data["orderId"] as? String ?: doc.id,
                customerName = data["customerName"] as? String ?: "Walk-in Customer",
                contactNumber = data["contactNumber"] as? String ?: "",
                totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isMade = data["isMade"] as? Boolean ?: false,
                isPaid = data["isPaid"] as? Boolean ?: false,
                isDelivered = data["isDelivered"] as? Boolean ?: false,
                notes = data["notes"] as? String ?: "",
                paymentMode = data["paymentMode"] as? String ?: "UNPAID",
                completedTimestamp = (data["completedTimestamp"] as? Number)?.toLong() ?: 0L,
                syncStatus = "SYNCED" // Orders from Firestore are always considered synced
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error mapping order document: ${e.message}")
            null
        }
    }

    private fun orderItemToMap(item: OrderItemEntity): Map<String, Any?> {
        return mapOf(
            "orderId" to item.orderId,
            "menuItemId" to item.menuItemId,
            "itemName" to item.itemName,
            "itemPrice" to item.itemPrice,
            "quantity" to item.quantity,
            "subtotal" to item.subtotal,
            "completedQuantity" to item.completedQuantity,
            "completedTimestamp" to item.completedTimestamp,
            "category" to "General"
        )
    }

    private fun mapToOrderItem(doc: DocumentSnapshot, orderId: String): OrderItemEntity? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

        return try {
            OrderItemEntity(
                id = doc.id.toIntOrNull() ?: 0,
                orderId = orderId,
                menuItemId = (data["menuItemId"] as? Number)?.toInt() ?: 0,
                itemName = data["itemName"] as? String ?: "Item",
                itemPrice = (data["itemPrice"] as? Number)?.toDouble() ?: 0.0,
                quantity = (data["quantity"] as? Number)?.toInt() ?: 1,
                subtotal = (data["subtotal"] as? Number)?.toDouble() ?: 0.0,
                completedQuantity = (data["completedQuantity"] as? Number)?.toInt() ?: 0,
                completedTimestamp = (data["completedTimestamp"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error mapping order item document: ${e.message}")
            null
        }
    }

    private fun menuItemToMap(item: MenuItemEntity): Map<String, Any?> {
        return mapOf(
            "id" to item.id,
            "name" to item.name,
            "category" to item.category,
            "price" to item.price,
            "description" to item.description,
            "isAvailable" to item.isAvailable,
            "lastModified" to FieldValue.serverTimestamp()
        )
    }

    private fun mapToMenuItem(doc: DocumentSnapshot): MenuItemEntity? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

        return try {
            MenuItemEntity(
                id = (data["id"] as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: 0,
                name = data["name"] as? String ?: "",
                category = data["category"] as? String ?: "",
                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                description = data["description"] as? String ?: "",
                isAvailable = data["isAvailable"] as? Boolean ?: true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error mapping menu item document: ${e.message}")
            null
        }
    }

    private fun upiAccountToMap(account: UpiAccountEntity): Map<String, Any?> {
        return mapOf(
            "id" to account.id,
            "vpa" to account.vpa,
            "payeeName" to account.payeeName,
            "isDefault" to account.isDefault,
            "note" to account.note,
            "lastModified" to FieldValue.serverTimestamp()
        )
    }

    private fun mapToUpiAccount(doc: DocumentSnapshot): UpiAccountEntity? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

        return try {
            UpiAccountEntity(
                id = (data["id"] as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: 0,
                vpa = data["vpa"] as? String ?: "",
                payeeName = data["payeeName"] as? String ?: "",
                isDefault = data["isDefault"] as? Boolean ?: false,
                note = data["note"] as? String ?: "FILTR Coffee Bill"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error mapping UPI account document: ${e.message}")
            null
        }
    }
}
