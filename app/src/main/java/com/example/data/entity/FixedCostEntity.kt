package com.example.data.entity

import android.util.Log
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StaffMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val salary: Double = 15000.0
)

@Entity(tableName = "fixed_costs")
data class FixedCostEntity(
    @PrimaryKey
    val monthKey: String, // Format "YYYY-MM", e.g. "2026-09"
    val rent: Double = 9000.0,
    val electricity: Double = 6000.0,
    val staffSalaries: Double = 30000.0, // 2 staff @ 15,000 each
    val staffCount: Int = 2,
    val maidSalary: Double = 2000.0,
    val miscellaneous: Double = 5000.0,
    val notes: String = "",
    val staffMembersJson: String = "", // JSON list of StaffMember: [{"name":"Ramesh","salary":15000.0},...]
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalFixedCost: Double
        get() = rent + electricity + staffSalaries + maidSalary + miscellaneous

    fun dailyCost(daysInMonth: Int = 30): Double {
        val days = if (daysInMonth > 0) daysInMonth else 30
        return totalFixedCost / days
    }

    fun parseStaffMembers(): List<StaffMember> {
        if (staffMembersJson.isNotBlank()) {
            try {
                val array = JSONArray(staffMembersJson)
                val list = mutableListOf<StaffMember>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fallbackId = "staff_${monthKey}_${i + 1}"
                    val rawId = if (obj.isNull("id")) "" else obj.optString("id", "").let { if (it == "null") "" else it }
                    val rawName = if (obj.isNull("name")) "" else obj.optString("name", "").let { if (it == "null") "" else it }
                    list.add(
                        StaffMember(
                            id = rawId.ifBlank { fallbackId },
                            name = rawName.ifBlank { "Staff ${i + 1}" },
                            salary = obj.optDouble("salary", 0.0)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                Log.w("FixedCostEntity", "Failed to parse staffMembersJson for $monthKey: ${e.message}")
            }
        }

        // Fallback: Generate list with deterministic IDs based on staffCount and staffSalaries
        val count = if (staffCount > 0) staffCount else 1
        val perPerson = if (count > 0) staffSalaries / count else 0.0
        return (1..count).map { i ->
            StaffMember(
                id = "staff_${monthKey}_$i",
                name = "Staff $i",
                salary = perPerson
            )
        }
    }

    companion object {
        fun staffListToJson(staffList: List<StaffMember>): String {
            val array = JSONArray()
            staffList.forEach { member ->
                val obj = JSONObject().apply {
                    put("id", member.id)
                    put("name", member.name)
                    put("salary", member.salary)
                }
                array.put(obj)
            }
            return array.toString()
        }
    }
}

