package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String = "",
    val unit: String = "kg", // kg, L, g, ml, pcs, pack
    val standardQuantity: Double = 1.0, // pack size (5kg brick = 5, 250g pack = 250)
    val defaultPrice: Double = 0.0, // price per pack
    val currentStock: Double = 0.0, // current stock in number of packs
    val isActive: Boolean = true
)

fun calculatePacksAndCost(
    quantity: Double,
    consumedUnit: String,
    item: InventoryItemEntity
): Pair<Double, Double> {
    val u1 = consumedUnit.trim().lowercase()
    val u2 = item.unit.trim().lowercase()

    val normalizedQtyInItemUnit = when {
        u1 == u2 -> quantity
        u1 == "g" && (u2 == "kg" || u2 == "kilogram" || u2 == "kgs") -> quantity / 1000.0
        (u1 == "kg" || u1 == "kgs") && u2 == "g" -> quantity * 1000.0
        u1 == "ml" && (u2 == "l" || u2 == "liter" || u2 == "liters" || u2 == "litre" || u2 == "litres") -> quantity / 1000.0
        (u1 == "l" || u1 == "liter" || u1 == "litres") && u2 == "ml" -> quantity * 1000.0
        else -> quantity
    }

    val packSize = if (item.standardQuantity > 0.0) item.standardQuantity else 1.0
    val packs = normalizedQtyInItemUnit / packSize
    val cost = packs * item.defaultPrice
    return Pair(packs, cost)
}

