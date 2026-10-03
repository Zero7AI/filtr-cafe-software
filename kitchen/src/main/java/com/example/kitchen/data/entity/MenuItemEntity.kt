package com.example.kitchen.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String,
    val price: Double,
    val description: String = "",
    val isAvailable: Boolean = true
)
