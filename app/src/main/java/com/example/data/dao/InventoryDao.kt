package com.example.data.dao

import androidx.room.*
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.InventoryUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // ==================== INVENTORY MASTER ====================

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getInventoryItemById(id: Int): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getInventoryItemByName(name: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItemEntity): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryItemEntity)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItemEntity)

    // ==================== STOCK MANAGEMENT ====================

    @Query("UPDATE inventory_items SET currentStock = :newStock WHERE id = :itemId")
    suspend fun updateStock(itemId: Int, newStock: Double)

    @Query("UPDATE inventory_items SET currentStock = currentStock + :amount WHERE id = :itemId")
    suspend fun addStock(itemId: Int, amount: Double)

    @Query("UPDATE inventory_items SET currentStock = MAX(0, currentStock - :amount) WHERE id = :itemId")
    suspend fun deductStock(itemId: Int, amount: Double)

    // ==================== INVENTORY ORDERS ====================

    @Transaction
    @Query("SELECT * FROM inventory_orders ORDER BY timestamp DESC")
    fun getAllInventoryOrdersWithItems(): Flow<List<InventoryOrderWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryOrder(order: InventoryOrderEntity)

    @Query("SELECT * FROM inventory_orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getInventoryOrderById(orderId: String): InventoryOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryOrderItem(item: InventoryOrderItemEntity): Long

    @Insert
    suspend fun insertInventoryOrderItems(items: List<InventoryOrderItemEntity>)

    @Transaction
    suspend fun insertInventoryOrderWithItems(
        order: InventoryOrderEntity,
        items: List<InventoryOrderItemEntity>
    ) {
        insertInventoryOrder(order)
        insertInventoryOrderItems(items)
    }

    @Query("DELETE FROM inventory_order_items WHERE orderId = :orderId")
    suspend fun deleteInventoryOrderItemsByOrderId(orderId: String)

    @Query("DELETE FROM inventory_orders WHERE orderId = :orderId")
    suspend fun deleteInventoryOrder(orderId: String)

    @Transaction
    suspend fun deleteInventoryOrderWithItems(orderId: String) {
        deleteInventoryOrderItemsByOrderId(orderId)
        deleteInventoryOrder(orderId)
    }

    @Query("SELECT MAX(CAST(SUBSTR(orderId, LENGTH(:prefix) + 1) AS INTEGER)) FROM inventory_orders WHERE orderId LIKE :prefix || '%'")
    suspend fun getMaxSequenceForPrefix(prefix: String): Int?

    // ==================== INVENTORY USAGE (RECIPES) ====================

    @Query("SELECT * FROM inventory_usage ORDER BY menuItemName ASC")
    fun getAllUsages(): Flow<List<InventoryUsageEntity>>

    @Query("SELECT * FROM inventory_usage")
    suspend fun getAllUsagesSnapshot(): List<InventoryUsageEntity>

    @Query("SELECT * FROM inventory_usage WHERE menuItemId = :menuItemId")
    suspend fun getUsageForMenuItem(menuItemId: Int): List<InventoryUsageEntity>

    @Query("SELECT * FROM inventory_usage WHERE menuItemId = :menuItemId AND inventoryItemId = :inventoryItemId LIMIT 1")
    suspend fun getUsageForMenuAndInventoryItem(menuItemId: Int, inventoryItemId: Int): InventoryUsageEntity?

    @Transaction
    @Query("SELECT * FROM inventory_orders WHERE orderId = :orderId")
    suspend fun getInventoryOrderWithItemsById(orderId: String): InventoryOrderWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: InventoryUsageEntity): Long

    @Update
    suspend fun updateUsage(usage: InventoryUsageEntity)

    @Delete
    suspend fun deleteUsage(usage: InventoryUsageEntity)

    @Query("DELETE FROM inventory_usage WHERE menuItemId = :menuItemId")
    suspend fun deleteAllUsagesForMenuItem(menuItemId: Int)
}
