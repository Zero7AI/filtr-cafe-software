package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff_attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey
    val id: String,
    val dateKey: String,
    val monthKey: String,
    val staffName: String,
    val deviceId: String = "",
    val checkInTimestamp: Long = 0L,
    val checkInLat: Double = 0.0,
    val checkInLng: Double = 0.0,
    val checkInDistanceMeters: Double = 0.0,
    val checkOutTimestamp: Long = 0L,
    val checkOutLat: Double = 0.0,
    val checkOutLng: Double = 0.0,
    val checkOutDistanceMeters: Double = 0.0,
    val workedMinutes: Int = 0,
    val notes: String = "",
    val updatedAt: Long = 0L
)
