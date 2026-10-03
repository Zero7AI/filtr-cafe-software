package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upi_accounts")
data class UpiAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vpa: String,
    val payeeName: String,
    val isDefault: Boolean = false,
    val note: String = "FILTR Coffee Bill"
)
