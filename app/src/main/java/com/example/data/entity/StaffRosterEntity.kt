package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff_roster")
data class StaffRosterEntity(
    @PrimaryKey
    val id: String, // AttendanceCalculator.normalizeStaffKey(name) or "staff_<ts>"
    val name: String,
    val roleTitle: String = "Barista",
    val shiftLabel: String = "Full Day",
    val weeklyOffDayOfWeek: Int = 0, // 0 = None, else Calendar.SUNDAY (1)..Calendar.SATURDAY (7)
    val joinedDateKey: String = "", // yyyy-MM-dd
    val isActive: Boolean = true,
    val updatedAt: Long = 0L,
    @ColumnInfo(defaultValue = "")
    val colorHex: String = "",
    @ColumnInfo(defaultValue = "STAFF")
    val orgRole: String = "STAFF"
)
