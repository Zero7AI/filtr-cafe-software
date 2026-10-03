package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_usage")
data class InventoryUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val menuItemId: Int,            // FK to menu_items.id
    val menuItemName: String,       // Denormalized for display
    val inventoryItemId: Int,       // FK to inventory_items.id
    val inventoryItemName: String,  // Denormalized for display
    val quantityUsed: Double,       // How much per 1 SKU quantity
    val unit: String = "g"          // g, ml, pcs
)
