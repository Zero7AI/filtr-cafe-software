package com.example.kitchen.data.dao

import androidx.room.*
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE isMade = 0 ORDER BY timestamp ASC")
    fun getPendingOrdersWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE isMade = 1 AND timestamp >= :startOfDayTimestamp ORDER BY completedTimestamp DESC")
    fun getCompletedTodayOrdersWithItems(startOfDayTimestamp: Long): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: String): OrderWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Transaction
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>) {
        insertOrder(order)
        insertOrderItems(items)
    }

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItemsByOrderId(orderId: String)

    @Transaction
    suspend fun updateOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>) {
        updateOrder(order)
        deleteOrderItemsByOrderId(order.orderId)
        insertOrderItems(items)
    }

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE order_items SET completedQuantity = :completedQty, completedTimestamp = :completedTimestamp WHERE id = :itemId")
    suspend fun updateOrderItemCompletedQuantity(itemId: Int, completedQty: Int, completedTimestamp: Long)

    @Query("UPDATE order_items SET completedQuantity = quantity, completedTimestamp = :completedTimestamp WHERE orderId = :orderId")
    suspend fun markAllOrderItemsCompleted(orderId: String, completedTimestamp: Long)

    @Query("UPDATE order_items SET completedQuantity = 0, completedTimestamp = 0 WHERE orderId = :orderId")
    suspend fun resetAllOrderItemsCompleted(orderId: String)

    @Query("UPDATE orders SET isMade = :isMade, completedTimestamp = :completedTimestamp, syncStatus = 'PENDING' WHERE orderId = :orderId")
    suspend fun updateMadeStatus(orderId: String, isMade: Boolean, completedTimestamp: Long = 0L)

    @Query("UPDATE orders SET isPaid = :isPaid, paymentMode = :paymentMode, syncStatus = 'PENDING' WHERE orderId = :orderId")
    suspend fun updatePaidStatus(orderId: String, isPaid: Boolean, paymentMode: String = "UNPAID")

    @Query("UPDATE orders SET isDelivered = :isDelivered, syncStatus = 'PENDING' WHERE orderId = :orderId")
    suspend fun updateDeliveredStatus(orderId: String, isDelivered: Boolean)

    @Query("UPDATE orders SET syncStatus = :status WHERE orderId = :orderId")
    suspend fun updateSyncStatus(orderId: String, status: String)

    @Query("UPDATE orders SET syncStatus = :status WHERE orderId IN (:orderIds)")
    suspend fun updateSyncStatusForOrders(orderIds: List<String>, status: String)

    @Transaction
    @Query("SELECT * FROM orders WHERE syncStatus != 'SYNCED' ORDER BY timestamp DESC")
    suspend fun getUnsyncedOrders(): List<OrderWithItems>

    @Query("DELETE FROM orders WHERE orderId = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query("SELECT COUNT(*) FROM orders WHERE isMade = 0")
    fun getPendingOrderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE timestamp >= :startOfDayTimestamp")
    suspend fun getTodayOrderCount(startOfDayTimestamp: Long): Int

    @Query("SELECT orderId FROM orders WHERE timestamp >= :startOfDayTimestamp")
    suspend fun getTodayOrderIds(startOfDayTimestamp: Long): List<String>
}
