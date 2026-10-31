package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a task in the Daily Checklist.
 * Can be a system default task or custom-added by staff/manager.
 */
@Entity(tableName = "checklist_tasks")
data class ChecklistTaskEntity(
    @PrimaryKey
    val id: String,
    val sectionId: String,          // OPENING, CLEANING, ROUTINE, CLOSING, or custom
    val sectionName: String,        // "01. Opening & Daily Preparation", etc.
    val title: String,
    val description: String = "",
    val isDefault: Boolean = true,  // Pre-seeded vs custom-added
    val orderIndex: Int = 0,
    val isWeeklyMondayOnly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Tracks the completion status of a task for a specific date (YYYY-MM-DD).
 */
@Entity(
    tableName = "checklist_completions",
    primaryKeys = ["dateKey", "taskId"]
)
data class ChecklistCompletionEntity(
    val dateKey: String,            // "YYYY-MM-DD"
    val taskId: String,
    val isCompleted: Boolean = false,
    val completedTimestamp: Long = 0L,
    val completedBy: String = "Staff",
    val isOutOfStock: Boolean = false
)
