package com.example.domain.inventory

import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.OrderWithItems
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class CleanIngredientUsageRow(
    val inventoryItemId: Int,
    val itemName: String,
    val totalQuantity: Double,
    val unit: String,
    val exactUsageLabel: String
)

data class CleanInventoryUsageReport(
    val ordersCount: Int,
    val items: List<CleanIngredientUsageRow>
)

/**
 * Clean, deterministic calculator computing exact raw material usage from Recipe Master
 * (`inventory_usage`) multiplied by daily/period orders made.
 */
object InventoryUsageCalculator {

    private val KG_UNITS = setOf("kg", "kgs", "kilogram", "kilograms")
    private val G_UNITS = setOf("g", "gm", "gms", "gram", "grams")
    private val L_UNITS = setOf("l", "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres")
    private val ML_UNITS = setOf("ml", "mls", "milliliter", "milliliters", "millilitre", "millilitres")

    fun computeIngredientUsage(
        orders: List<OrderWithItems>,
        inventoryItems: List<InventoryItemEntity>,
        usages: List<InventoryUsageEntity>
    ): CleanInventoryUsageReport {
        val activeOrders = orders.filter { it.order.syncStatus != "CANCELLED" }
        val invById = inventoryItems.associateBy { it.id }

        // Accumulate into canonical base unit per inventory item:
        // weight -> g, volume -> ml, otherwise keep normalized recipe unit
        data class Acc(var baseQty: Double, var baseKind: String, var fallbackUnit: String, var fallbackName: String)

        val accByInvId = linkedMapOf<Int, Acc>()

        for (orderWithItems in activeOrders) {
            for (orderItem in orderWithItems.items) {
                if (orderItem.quantity <= 0) continue
                val recipeRows = usages.filter {
                    (orderItem.menuItemId > 0 && it.menuItemId == orderItem.menuItemId) ||
                        it.menuItemName.trim().equals(orderItem.itemName.trim(), ignoreCase = true)
                }

                if (recipeRows.isNotEmpty()) {
                    for (recipe in recipeRows) {
                        val qty = recipe.quantityUsed * orderItem.quantity
                        if (qty <= 0.0) continue
                        val invId = recipe.inventoryItemId
                        val unitLower = recipe.unit.trim().lowercase(Locale.US)
                        val (kind, baseDelta, canonicalUnit) = toBaseUnit(qty, unitLower)
                        val existing = accByInvId[invId]
                        if (existing == null) {
                            accByInvId[invId] = Acc(
                                baseQty = baseDelta,
                                baseKind = kind,
                                fallbackUnit = canonicalUnit,
                                fallbackName = recipe.inventoryItemName
                            )
                        } else if (existing.baseKind == kind) {
                            existing.baseQty += baseDelta
                        } else {
                            // Incompatible units in different recipes for same item: still add best effort
                            existing.baseQty += qty
                        }
                    }
                } else {
                    val directInv = inventoryItems.firstOrNull {
                        it.name.trim().equals(orderItem.itemName.trim(), ignoreCase = true)
                    }
                    if (directInv != null) {
                        val qty = orderItem.quantity.toDouble()
                        val (kind, baseDelta, canonicalUnit) = toBaseUnit(qty, directInv.unit.trim().lowercase(Locale.US))
                        val existing = accByInvId[directInv.id]
                        if (existing == null) {
                            accByInvId[directInv.id] = Acc(baseDelta, kind, canonicalUnit, directInv.name)
                        } else {
                            existing.baseQty += baseDelta
                        }
                    }
                }
            }
        }

        val rows = accByInvId.mapNotNull { (invId, acc) ->
            if (acc.baseQty <= 0.0) return@mapNotNull null
            val name = invById[invId]?.name?.takeIf { it.isNotBlank() } ?: acc.fallbackName
            val (displayQty, displayUnit) = fromBaseUnit(acc.baseQty, acc.baseKind, acc.fallbackUnit)
            val label = "${formatQty(displayQty)} $displayUnit"
            CleanIngredientUsageRow(
                inventoryItemId = invId,
                itemName = name,
                totalQuantity = displayQty,
                unit = displayUnit,
                exactUsageLabel = label
            )
        }.sortedWith(
            compareBy<CleanIngredientUsageRow>(
                { it.itemName.lowercase(Locale.US) }
            )
        )

        return CleanInventoryUsageReport(
            ordersCount = activeOrders.size,
            items = rows
        )
    }

    private fun toBaseUnit(qty: Double, unitLower: String): Triple<String, Double, String> {
        return when {
            unitLower in KG_UNITS -> Triple("WEIGHT", qty * 1000.0, "g")
            unitLower in G_UNITS -> Triple("WEIGHT", qty, "g")
            unitLower in L_UNITS -> Triple("VOLUME", qty * 1000.0, "ml")
            unitLower in ML_UNITS -> Triple("VOLUME", qty, "ml")
            else -> Triple("COUNT:${unitLower.ifBlank { "pcs" }}", qty, unitLower.ifBlank { "pcs" })
        }
    }

    private fun fromBaseUnit(baseQty: Double, kind: String, fallbackUnit: String): Pair<Double, String> {
        return when (kind) {
            "WEIGHT" -> if (baseQty >= 1000.0) (baseQty / 1000.0) to "kg" else baseQty to "g"
            "VOLUME" -> if (baseQty >= 1000.0) (baseQty / 1000.0) to "L" else baseQty to "ml"
            else -> baseQty to fallbackUnit
        }
    }

    private fun formatQty(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (abs(rounded - rounded.roundToLong()) < 0.005) {
            rounded.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }
}
