package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

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

private val CONTAINER_UNITS = setOf("packet", "packets", "pack", "packs", "can", "cans", "tin", "tins", "box", "boxes", "bottle", "bottles")
private val KG_UNITS = setOf("kg", "kgs", "kilogram", "kilograms")
private val LITER_UNITS = setOf("l", "liter", "liters", "litre", "litres")
private val PCS_UNITS = setOf("pcs", "pc", "piece", "pieces")

fun calculatePacksAndCost(
    quantity: Double,
    consumedUnit: String,
    item: InventoryItemEntity
): Pair<Double, Double> {
    if (quantity <= 0.0) return Pair(0.0, 0.0)

    val u1 = consumedUnit.trim().lowercase(Locale.US)
    val u2 = item.unit.trim().lowercase(Locale.US)
    val packSize = if (item.standardQuantity > 0.0) item.standardQuantity else 1.0
    val isPackItem = u2 in CONTAINER_UNITS

    val packs = when {
        // Explicit container unit (pack, box, bottle, etc.) represents whole packs directly
        u1 in CONTAINER_UNITS -> quantity
        // Same base unit (e.g. kg->kg, g->g, L->L, ml->ml, pcs->pcs): divide by packSize
        u1 == u2 || (u1 in KG_UNITS && u2 in KG_UNITS) || (u1 in LITER_UNITS && u2 in LITER_UNITS) || (u1 in PCS_UNITS && u2 in PCS_UNITS) -> {
            quantity / packSize
        }
        u1 == "g" && u2 in KG_UNITS -> (quantity / 1000.0) / packSize
        u1 in KG_UNITS && u2 == "g" -> (quantity * 1000.0) / packSize
        u1 == "ml" && u2 in LITER_UNITS -> (quantity / 1000.0) / packSize
        u1 in LITER_UNITS && u2 == "ml" -> (quantity * 1000.0) / packSize
        isPackItem && (u1 in KG_UNITS || u1 in LITER_UNITS) -> {
            if (packSize > 10.0) (quantity * 1000.0) / packSize else quantity / packSize
        }
        isPackItem && (u1 == "g" || u1 == "ml") -> {
            if (packSize > 10.0) quantity / packSize else (quantity / 1000.0) / packSize
        }
        else -> quantity / packSize
    }

    val cost = packs * item.defaultPrice
    return Pair(packs, cost)
}

