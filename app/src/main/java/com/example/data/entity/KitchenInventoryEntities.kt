package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Storage zone catalog item for Smart Daily Kitchen Inventory.
 * Uses stable string IDs so multi-device offline-first sync merges deterministically.
 */
@Entity(tableName = "kitchen_inventory_items")
data class KitchenInventoryItemEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconEmoji: String = "📦",
    val sectionId: String, // DEEP_FREEZER, REFRIGERATOR_FREEZER, OUTSIDE_FREEZER, NORMAL_TEMPERATURE
    val countingMode: String, // SEALED_AND_PERCENTAGE, SEALED_AND_GRAMS, SEALED_AND_ML, SEALED_AND_PIECES, DIRECT_GRAMS, DIRECT_ML, DIRECT_PIECES
    val unitLabel: String, // g, ml, pcs, tubs
    val containerLabel: String = "packets", // packets, bottles, jars, tubs, ""
    val packSize: Double = 1.0, // quantity inside 1 sealed container in unitLabel (1.0 for percentage/direct)
    val minStockThreshold: Double = 1.0, // manager-configured ORANGE threshold in unitLabel
    val targetStockLevel: Double = 2.0, // par level used to compute suggested reorder qty
    val orderIndex: Int = 0,
    val isDefault: Boolean = true,
    val isActive: Boolean = true,
    val updatedAt: Long = 0L
)

/**
 * Daily shift header record keyed by "<dateKey>_<shiftType>" so every date and shift
 * preserves its own independent historical record without overwriting prior checks.
 */
@Entity(tableName = "kitchen_inventory_shifts")
data class KitchenInventoryShiftEntity(
    @PrimaryKey
    val id: String, // e.g. "2026-11-25_OPENING"
    val dateKey: String, // yyyy-MM-dd
    val shiftType: String, // OPENING, CLOSING
    val status: String = "IN_PROGRESS", // IN_PROGRESS, SUBMITTED
    val staffName: String = "Staff",
    val startedAt: Long = 0L,
    val submittedAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val notes: String = ""
)

/**
 * Per-item count row within a specific daily shift.
 * Snapshots metadata so historical audits stay accurate even if the manager edits an item later.
 */
@Entity(
    tableName = "kitchen_inventory_entries",
    primaryKeys = ["shiftId", "itemId"]
)
data class KitchenInventoryEntryEntity(
    val shiftId: String,
    val dateKey: String,
    val shiftType: String,
    val itemId: String,
    val itemNameSnapshot: String,
    val sectionIdSnapshot: String,
    val unitLabelSnapshot: String,
    val countingModeSnapshot: String,
    val packSizeSnapshot: Double,
    val sealedQty: Int = 0,
    val openedQty: Int = 0,
    val openedRemainingQty: Double = 0.0, // 25/50/75/100 for PERCENTAGE, or g/ml/pcs, or direct total
    val totalUsableQty: Double = 0.0,
    val wastageQty: Double = 0.0,
    val wastageReason: String = "",
    val stockStatus: String = "GREEN", // GREEN, ORANGE, RED
    val isChecked: Boolean = false,
    val checkedBy: String = "",
    val checkedAt: Long = 0L
)
