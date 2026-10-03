package com.example.kitchen.data.dao

import androidx.room.*
import com.example.kitchen.data.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY category ASC, name ASC")
    fun getAllMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items ORDER BY id ASC")
    suspend fun getAllMenuItemsList(): List<MenuItemEntity>

    @Query("SELECT * FROM menu_items WHERE isAvailable = 1 ORDER BY category ASC, name ASC")
    fun getAvailableMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getMenuItemById(id: Int): MenuItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItem(item: MenuItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MenuItemEntity>)

    @Update
    suspend fun updateMenuItem(item: MenuItemEntity)

    @Delete
    suspend fun deleteMenuItem(item: MenuItemEntity)

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun getMenuItemCount(): Int

    @Query("DELETE FROM menu_items")
    suspend fun deleteAllMenuItems()
}
