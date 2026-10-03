package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_order_items",
    foreignKeys = [
        ForeignKey(
            entity = InventoryOrderEntity::class,
            parentColumns = ["orderId"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class InventoryOrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val orderId: String,
    val itemName: String,
    val quantity: Double = 0.0,
    val unit: String = "kg", // kg, L, pcs, pack
    val unitPrice: Double = 0.0,
    val subtotal: Double = 0.0
)
