package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.data.entity.KitchenInventoryItemEntity
import com.example.data.entity.KitchenInventoryShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KitchenInventoryDao {

    // ==================== CATALOG ITEMS ====================

    @Query("SELECT * FROM kitchen_inventory_items WHERE isActive = 1 ORDER BY sectionId ASC, orderIndex ASC, name ASC")
    fun getActiveItems(): Flow<List<KitchenInventoryItemEntity>>

    @Query("SELECT * FROM kitchen_inventory_items ORDER BY sectionId ASC, orderIndex ASC, name ASC")
    fun getAllItems(): Flow<List<KitchenInventoryItemEntity>>

    @Query("SELECT * FROM kitchen_inventory_items ORDER BY sectionId ASC, orderIndex ASC, name ASC")
    suspend fun getAllItemsSync(): List<KitchenInventoryItemEntity>

    @Query("SELECT * FROM kitchen_inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): KitchenInventoryItemEntity?

    @Query("SELECT COUNT(*) FROM kitchen_inventory_items")
    suspend fun getItemCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<KitchenInventoryItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: KitchenInventoryItemEntity)

    // ==================== SHIFTS ====================

    @Query("SELECT * FROM kitchen_inventory_shifts WHERE dateKey = :dateKey ORDER BY shiftType ASC")
    fun getShiftsForDate(dateKey: String): Flow<List<KitchenInventoryShiftEntity>>

    @Query("SELECT * FROM kitchen_inventory_shifts ORDER BY dateKey DESC, startedAt DESC")
    fun getAllShifts(): Flow<List<KitchenInventoryShiftEntity>>

    @Query("SELECT * FROM kitchen_inventory_shifts ORDER BY dateKey DESC, startedAt DESC")
    suspend fun getAllShiftsSync(): List<KitchenInventoryShiftEntity>

    @Query("SELECT * FROM kitchen_inventory_shifts WHERE id = :shiftId LIMIT 1")
    suspend fun getShiftById(shiftId: String): KitchenInventoryShiftEntity?

    @Query(
        "SELECT * FROM kitchen_inventory_shifts " +
            "WHERE shiftType = 'CLOSING' AND status = 'SUBMITTED' AND dateKey <= :beforeOrOnDateKey " +
            "ORDER BY dateKey DESC, submittedAt DESC LIMIT 1"
    )
    suspend fun getLatestSubmittedClosingShift(beforeOrOnDateKey: String): KitchenInventoryShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(shift: KitchenInventoryShiftEntity)

    // ==================== ENTRIES ====================

    @Query("SELECT * FROM kitchen_inventory_entries WHERE dateKey = :dateKey")
    fun getEntriesForDate(dateKey: String): Flow<List<KitchenInventoryEntryEntity>>

    @Query("SELECT * FROM kitchen_inventory_entries WHERE shiftId = :shiftId")
    fun getEntriesForShift(shiftId: String): Flow<List<KitchenInventoryEntryEntity>>

    @Query("SELECT * FROM kitchen_inventory_entries WHERE shiftId = :shiftId")
    suspend fun getEntriesForShiftSync(shiftId: String): List<KitchenInventoryEntryEntity>

    @Query("SELECT * FROM kitchen_inventory_entries ORDER BY dateKey DESC, checkedAt DESC")
    fun getAllEntries(): Flow<List<KitchenInventoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: KitchenInventoryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<KitchenInventoryEntryEntity>)
}
