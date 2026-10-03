package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_orders")
data class InventoryOrderEntity(
    @PrimaryKey
    val orderId: String, // e.g., "INV-20260807-001"
    val timestamp: Long,
    val supplierName: String = "",
    val totalAmount: Double = 0.0,
    val notes: String = "",
    val syncStatus: String = "PENDING"
)
