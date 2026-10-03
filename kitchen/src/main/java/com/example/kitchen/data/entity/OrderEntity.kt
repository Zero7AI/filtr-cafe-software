package com.example.kitchen.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val orderId: String, // e.g. "FLT-20260803-001"
    val customerName: String,
    val contactNumber: String = "",
    val totalAmount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isMade: Boolean = false,
    val isPaid: Boolean = false,
    val isDelivered: Boolean = false,
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED
    val notes: String = "",
    @ColumnInfo(defaultValue = "UNPAID")
    val paymentMode: String = "UNPAID", // CASH, UPI, UNPAID
    @ColumnInfo(defaultValue = "0")
    val completedTimestamp: Long = 0L // Timestamp when order was marked as Made/Completed
)
