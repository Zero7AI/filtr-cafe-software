package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kds_item_claims")
data class KdsItemClaimEntity(
    @PrimaryKey
    val key: String, // "${orderId}_${itemId}_${unitIndex}"
    val orderId: String,
    val itemId: Int,
    val itemName: String,
    val unitIndex: Int,
    val status: String, // "PENDING", "MAKING", "MADE"
    val staffName: String = "",
    val colorHex: String = "",
    val updatedAt: Long = 0L
)
