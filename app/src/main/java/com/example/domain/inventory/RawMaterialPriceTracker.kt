package com.example.domain.inventory

import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.calculatePacksAndCost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class PriceTrend {
    INCREASED,
    DECREASED,
    UNCHANGED,
    FIRST_ENTRY
}

data class IngredientPriceSummary(
    val itemId: Int,
    val itemName: String,
    val category: String,
    val unit: String,
    val packSize: Double,
    val currentStock: Double,
    val latestRate: Double,
    val previousRate: Double?,
    val priceDelta: Double,
    val priceChangePercent: Double,
    val trend: PriceTrend,
    val lastSupplier: String,
    val lastPurchasedTimestamp: Long,
    val totalPurchasesCount: Int
) {
    val alertLabel: String
        get() = when (trend) {
            PriceTrend.INCREASED -> "↑ UP (+₹${formatCompact(priceDelta)} / +${String.format(Locale.US, "%.1f", priceChangePercent)}%)"
            PriceTrend.DECREASED -> "↓ DROP (-₹${formatCompact(abs(priceDelta))} / -${String.format(Locale.US, "%.1f", abs(priceChangePercent))}%)"
            PriceTrend.UNCHANGED -> "= SAME"
            PriceTrend.FIRST_ENTRY -> if (totalPurchasesCount > 0) "FIRST BILL" else "MASTER RATE"
        }

    private fun formatCompact(v: Double): String {
        return if (abs(v - v.roundToLong()) < 0.005) {
            v.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", v)
        }
    }
}

object RawMaterialPriceTracker {

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

    /**
     * Normalizes an ordered line item's cost to the InventoryItemEntity pack rate so
     * unit conversions (e.g. 500 g ordered for a 1 kg pack item) always preserve accurate per-pack pricing.
     */
    fun resolvePackUnitPrice(
        orderItem: InventoryOrderItemEntity,
        masterItem: InventoryItemEntity?
    ): Double {
        if (orderItem.quantity <= 0.0) return round2(orderItem.unitPrice.coerceAtLeast(0.0))
        val effectiveTotal = if (orderItem.subtotal > 0.0) {
            orderItem.subtotal
        } else {
            orderItem.quantity * orderItem.unitPrice
        }
        if (masterItem != null) {
            val (packs, _) = calculatePacksAndCost(orderItem.quantity, orderItem.unit, masterItem)
            if (packs > 0.0) {
                return round2(effectiveTotal / packs)
            }
        }
        return round2(effectiveTotal / orderItem.quantity)
    }

    fun buildIngredientPricingSummaries(
        inventoryItems: List<InventoryItemEntity>,
        inventoryOrders: List<InventoryOrderWithItems>
    ): List<IngredientPriceSummary> {
        val sortedOrders = inventoryOrders.sortedBy { it.order.timestamp }
        val masterByLower = inventoryItems.associateBy { it.name.trim().lowercase(Locale.US) }

        data class PurchasePoint(
            val rate: Double,
            val supplier: String,
            val timestamp: Long
        )

        val historyByKey = linkedMapOf<String, MutableList<PurchasePoint>>()
        val uncatalogedSamples = linkedMapOf<String, Pair<String, String>>() // key -> (DisplayName, Unit)

        for (orderWithItems in sortedOrders) {
            val order = orderWithItems.order
            for (line in orderWithItems.items) {
                if (line.itemName.isBlank() || line.quantity <= 0.0) continue
                val matchedMaster = RawMaterialReceiptParser.matchCatalogItem(line.itemName, inventoryItems)
                val canonicalName = matchedMaster?.name ?: line.itemName.trim()
                val key = canonicalName.trim().lowercase(Locale.US)
                val rate = resolvePackUnitPrice(line, matchedMaster)
                if (rate <= 0.0) continue

                historyByKey.getOrPut(key) { mutableListOf() }.add(
                    PurchasePoint(
                        rate = rate,
                        supplier = order.supplierName.ifBlank { "Supplier" },
                        timestamp = order.timestamp
                    )
                )
                if (matchedMaster == null && key !in uncatalogedSamples) {
                    uncatalogedSamples[key] = canonicalName to line.unit.ifBlank { "pcs" }
                }
            }
        }

        val summaries = mutableListOf<IngredientPriceSummary>()

        // 1. All cataloged master inventory items
        for (master in inventoryItems) {
            val key = master.name.trim().lowercase(Locale.US)
            val points = historyByKey[key].orEmpty()
            summaries.add(
                createSummary(
                    itemId = master.id,
                    itemName = master.name,
                    category = master.category.ifBlank { "General" },
                    unit = master.unit.ifBlank { "kg" },
                    packSize = master.standardQuantity,
                    currentStock = master.currentStock,
                    fallbackRate = round2(master.defaultPrice),
                    points = points.map { Triple(it.rate, it.supplier, it.timestamp) }
                )
            )
        }

        // 2. Any ingredients present only in purchase orders so nothing is missed in Excel
        for ((key, meta) in uncatalogedSamples) {
            if (key in masterByLower) continue
            val points = historyByKey[key].orEmpty()
            summaries.add(
                createSummary(
                    itemId = 0,
                    itemName = meta.first,
                    category = "Purchase History",
                    unit = meta.second,
                    packSize = 1.0,
                    currentStock = 0.0,
                    fallbackRate = 0.0,
                    points = points.map { Triple(it.rate, it.supplier, it.timestamp) }
                )
            )
        }

        // Surface price increases first so owner spots hikes immediately on open
        return summaries.sortedWith(
            compareBy<IngredientPriceSummary> {
                when (it.trend) {
                    PriceTrend.INCREASED -> 0
                    PriceTrend.DECREASED -> 1
                    PriceTrend.UNCHANGED -> 2
                    PriceTrend.FIRST_ENTRY -> 3
                }
            }.thenByDescending { abs(it.priceChangePercent) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.itemName }
        )
    }

    private fun createSummary(
        itemId: Int,
        itemName: String,
        category: String,
        unit: String,
        packSize: Double,
        currentStock: Double,
        fallbackRate: Double,
        points: List<Triple<Double, String, Long>>
    ): IngredientPriceSummary {
        val latest = points.lastOrNull()
        val latestRate = latest?.first ?: fallbackRate
        // Find previous distinct-bill entry (last bill before the latest purchase)
        val prevPoint = if (points.size >= 2) {
            points[points.size - 2]
        } else {
            null
        }
        val prevRate = prevPoint?.first ?: if (points.size == 1 && fallbackRate > 0.0 && abs(fallbackRate - latestRate) > 0.005) {
            fallbackRate
        } else {
            null
        }

        val delta = if (prevRate != null) round2(latestRate - prevRate) else 0.0
        val pct = if (prevRate != null && prevRate > 0.0) {
            round2((delta / prevRate) * 100.0)
        } else {
            0.0
        }

        val trend = when {
            prevRate == null -> PriceTrend.FIRST_ENTRY
            delta > 0.01 -> PriceTrend.INCREASED
            delta < -0.01 -> PriceTrend.DECREASED
            else -> PriceTrend.UNCHANGED
        }

        return IngredientPriceSummary(
            itemId = itemId,
            itemName = itemName,
            category = category,
            unit = unit,
            packSize = packSize,
            currentStock = round2(currentStock),
            latestRate = latestRate,
            previousRate = prevRate,
            priceDelta = delta,
            priceChangePercent = pct,
            trend = trend,
            lastSupplier = latest?.second.orEmpty(),
            lastPurchasedTimestamp = latest?.third ?: 0L,
            totalPurchasesCount = points.size
        )
    }

    fun buildIngredientPricingCsv(
        summaries: List<IngredientPriceSummary>,
        inventoryOrders: List<InventoryOrderWithItems>,
        exportedAtMs: Long = System.currentTimeMillis()
    ): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel opens ₹ and ↑/↓ cleanly
        sb.append('\uFEFF')

        sb.appendLine("FILTR COFFEE — INGREDIENT PRICING & PRICE HIKE SHEET,${escapeCsv("Generated: ${df.format(Date(exportedAtMs))}")}")
        sb.appendLine("Ingredient Name,Category,Unit,Pack Size,Current Stock,Latest Price (INR),Previous Price (INR),Price Change (INR),Change (%),Price Alert,Last Supplier,Last Updated,Purchase Count")
        for (s in summaries) {
            val prevStr = s.previousRate?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            val dateStr = if (s.lastPurchasedTimestamp > 0L) df.format(Date(s.lastPurchasedTimestamp)) else ""
            sb.appendLine(
                listOf(
                    escapeCsv(s.itemName),
                    escapeCsv(s.category),
                    escapeCsv(s.unit),
                    String.format(Locale.US, "%.2f", s.packSize),
                    String.format(Locale.US, "%.2f", s.currentStock),
                    String.format(Locale.US, "%.2f", s.latestRate),
                    prevStr,
                    String.format(Locale.US, "%.2f", s.priceDelta),
                    String.format(Locale.US, "%.1f%%", s.priceChangePercent),
                    escapeCsv(s.alertLabel),
                    escapeCsv(s.lastSupplier.ifBlank { "-" }),
                    escapeCsv(dateStr),
                    s.totalPurchasesCount.toString()
                ).joinToString(",")
            )
        }

        sb.appendLine()
        sb.appendLine("PURCHASE BILL LEDGER (CHRONOLOGICAL)")
        sb.appendLine("Bill ID,Date,Supplier,Ingredient Name,Quantity,Unit,Unit Price (INR),Subtotal (INR),Notes")
        for (orderWithItems in inventoryOrders.sortedByDescending { it.order.timestamp }) {
            val order = orderWithItems.order
            val dateStr = df.format(Date(order.timestamp))
            for (line in orderWithItems.items) {
                sb.appendLine(
                    listOf(
                        escapeCsv(order.orderId),
                        escapeCsv(dateStr),
                        escapeCsv(order.supplierName.ifBlank { "Supplier" }),
                        escapeCsv(line.itemName),
                        String.format(Locale.US, "%.2f", line.quantity),
                        escapeCsv(line.unit),
                        String.format(Locale.US, "%.2f", line.unitPrice),
                        String.format(Locale.US, "%.2f", line.subtotal),
                        escapeCsv(order.notes)
                    ).joinToString(",")
                )
            }
        }
        return sb.toString()
    }

    private fun escapeCsv(raw: String): String {
        val cleaned = raw.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
