package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["orderId"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val orderId: String,
    val menuItemId: Int = 0,
    val itemName: String,
    val itemPrice: Double,
    val quantity: Int,
    val subtotal: Double,
    @ColumnInfo(defaultValue = "0")
    val completedQuantity: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val completedTimestamp: Long = 0L // Timestamp when SKU item was marked completed
)
