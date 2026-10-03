package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val contactNumber: String = "",
    val totalOrders: Int = 0,
    val totalSpent: Double = 0.0,
    val lastOrderTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
