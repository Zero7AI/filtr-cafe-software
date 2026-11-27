package com.example.domain.inventory

import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.calculatePacksAndCost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

enum class PriceTrend {
    INCREASED,
    DECREASED,
    UNCHANGED,
    FIRST_ENTRY
}

enum class StockAlertLevel {
    OUT_OF_STOCK,
    CRITICAL_REORDER,
    LOW_STOCK,
    HEALTHY
}

enum class PriceAlertLevel {
    HIGH_HIKE,
    MINOR_UP,
    PRICE_DROP,
    STABLE
}

data class InventoryAlertRow(
    val itemId: Int,
    val itemName: String,
    val category: String,
    val unit: String,
    val packSize: Double,
    val currentStock: Double,
    val estimatedOrdersLeft: Int?,
    val stockAlertLevel: StockAlertLevel,
    val stockAlertLabel: String,
    val suggestedOrderPacks: Int,
    val suggestedOrderLabel: String,
    val latestRate: Double,
    val previousRate: Double?,
    val priceDelta: Double,
    val priceChangePercent: Double,
    val priceAlertLevel: PriceAlertLevel,
    val priceAlertLabel: String,
    val lastSupplier: String,
    val lastPurchasedTimestamp: Long
)

data class IngredientPriceMatrixRow(
    val itemName: String,
    val category: String,
    val unit: String,
    val currentStock: Double,
    val stockAlertLevel: StockAlertLevel,
    val stockAlertLabel: String,
    val suggestedOrderLabel: String,
    val latestRate: Double,
    val priceChangePercent: Double,
    val priceAlertLevel: PriceAlertLevel,
    val priceAlertLabel: String,
    val trend: PriceTrend,
    val ratesByDate: Map<String, Double>
)

data class IngredientPriceMatrix(
    val dates: List<String>,
    val rows: List<IngredientPriceMatrixRow>
)

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

    fun evaluateInventoryAlerts(
        inventoryItems: List<InventoryItemEntity>,
        inventoryOrders: List<InventoryOrderWithItems>,
        usages: List<InventoryUsageEntity> = emptyList(),
        priceAlertThresholdPct: Double = 5.0
    ): List<InventoryAlertRow> {
        val safeThreshold = priceAlertThresholdPct.coerceAtLeast(0.1)
        val summaries = buildIngredientPricingSummaries(inventoryItems, inventoryOrders)
        val summaryByKey = summaries.associateBy { it.itemName.trim().lowercase(Locale.US) }
        val usageByItemId = usages.groupBy { it.inventoryItemId }
        val activeCatalog = inventoryItems.filter { it.isActive }
        val results = mutableListOf<InventoryAlertRow>()
        val seenKeys = mutableSetOf<String>()

        fun buildRow(
            itemId: Int,
            itemName: String,
            category: String,
            unit: String,
            packSize: Double,
            currentStock: Double,
            master: InventoryItemEntity?,
            summary: IngredientPriceSummary?
        ): InventoryAlertRow {
            val itemUsages = if (master != null) usageByItemId[master.id].orEmpty() else emptyList()
            val maxPacksPerOrder = if (master != null && itemUsages.isNotEmpty()) {
                itemUsages.maxOfOrNull { u -> calculatePacksAndCost(u.quantityUsed, u.unit, master).first } ?: 0.0
            } else {
                0.0
            }
            val ordersLeft = if (maxPacksPerOrder > 0.0) {
                (currentStock / maxPacksPerOrder).toInt().coerceAtLeast(0)
            } else {
                null
            }
            val stockLevel = when {
                currentStock <= 0.0 -> StockAlertLevel.OUT_OF_STOCK
                (ordersLeft != null && ordersLeft <= 7) || currentStock <= 1.0 -> StockAlertLevel.CRITICAL_REORDER
                (ordersLeft != null && ordersLeft <= 15) || currentStock <= 2.0 -> StockAlertLevel.LOW_STOCK
                else -> StockAlertLevel.HEALTHY
            }
            val stockLabel = when (stockLevel) {
                StockAlertLevel.OUT_OF_STOCK -> "OUT OF STOCK"
                StockAlertLevel.CRITICAL_REORDER -> if (ordersLeft != null) "REORDER (~$ordersLeft orders)" else "CRITICAL LOW"
                StockAlertLevel.LOW_STOCK -> if (ordersLeft != null) "LOW (~$ordersLeft orders)" else "LOW STOCK"
                StockAlertLevel.HEALTHY -> if (ordersLeft != null) "OK (~$ordersLeft orders)" else "OK"
            }
            val suggestedPacks = if (stockLevel == StockAlertLevel.HEALTHY) {
                0
            } else {
                val targetPacks = if (maxPacksPerOrder > 0.0) {
                    maxOf(3.0, maxPacksPerOrder * 14.0)
                } else {
                    5.0
                }
                ceil((targetPacks - currentStock).coerceAtLeast(1.0)).toInt()
            }
            val suggestedLabel = if (suggestedPacks > 0) {
                val totalQty = round2(suggestedPacks * packSize.coerceAtLeast(1.0))
                val qtyStr = if (abs(totalQty - totalQty.roundToLong()) < 0.005) {
                    totalQty.roundToLong().toString()
                } else {
                    String.format(Locale.US, "%.1f", totalQty)
                }
                "+$suggestedPacks packs ($qtyStr $unit)"
            } else {
                "—"
            }

            val trend = summary?.trend ?: PriceTrend.FIRST_ENTRY
            val pct = summary?.priceChangePercent ?: 0.0
            val priceLevel = when {
                trend == PriceTrend.INCREASED && pct >= safeThreshold -> PriceAlertLevel.HIGH_HIKE
                trend == PriceTrend.INCREASED -> PriceAlertLevel.MINOR_UP
                trend == PriceTrend.DECREASED -> PriceAlertLevel.PRICE_DROP
                else -> PriceAlertLevel.STABLE
            }
            val priceLabel = when (priceLevel) {
                PriceAlertLevel.HIGH_HIKE -> "HIGH HIKE (+${String.format(Locale.US, "%.1f", pct)}%)"
                PriceAlertLevel.MINOR_UP -> "UP (+${String.format(Locale.US, "%.1f", pct)}%)"
                PriceAlertLevel.PRICE_DROP -> "DROP (-${String.format(Locale.US, "%.1f", abs(pct))}%)"
                PriceAlertLevel.STABLE -> summary?.alertLabel ?: "OK"
            }

            return InventoryAlertRow(
                itemId = itemId,
                itemName = itemName,
                category = category,
                unit = unit,
                packSize = packSize,
                currentStock = round2(currentStock),
                estimatedOrdersLeft = ordersLeft,
                stockAlertLevel = stockLevel,
                stockAlertLabel = stockLabel,
                suggestedOrderPacks = suggestedPacks,
                suggestedOrderLabel = suggestedLabel,
                latestRate = summary?.latestRate ?: (master?.defaultPrice ?: 0.0),
                previousRate = summary?.previousRate,
                priceDelta = summary?.priceDelta ?: 0.0,
                priceChangePercent = pct,
                priceAlertLevel = priceLevel,
                priceAlertLabel = priceLabel,
                lastSupplier = summary?.lastSupplier.orEmpty(),
                lastPurchasedTimestamp = summary?.lastPurchasedTimestamp ?: 0L
            )
        }

        for (master in activeCatalog) {
            val key = master.name.trim().lowercase(Locale.US)
            seenKeys.add(key)
            results.add(
                buildRow(
                    itemId = master.id,
                    itemName = master.name,
                    category = master.category.ifBlank { "General" },
                    unit = master.unit.ifBlank { "kg" },
                    packSize = master.standardQuantity,
                    currentStock = master.currentStock,
                    master = master,
                    summary = summaryByKey[key]
                )
            )
        }

        for (summary in summaries) {
            val key = summary.itemName.trim().lowercase(Locale.US)
            if (key in seenKeys) continue
            seenKeys.add(key)
            results.add(
                buildRow(
                    itemId = summary.itemId,
                    itemName = summary.itemName,
                    category = summary.category,
                    unit = summary.unit,
                    packSize = summary.packSize,
                    currentStock = summary.currentStock,
                    master = null,
                    summary = summary
                )
            )
        }

        return results.sortedWith(
            compareBy<InventoryAlertRow> {
                when (it.stockAlertLevel) {
                    StockAlertLevel.OUT_OF_STOCK -> 0
                    StockAlertLevel.CRITICAL_REORDER -> 1
                    StockAlertLevel.LOW_STOCK -> 2
                    StockAlertLevel.HEALTHY -> when (it.priceAlertLevel) {
                        PriceAlertLevel.HIGH_HIKE -> 3
                        PriceAlertLevel.MINOR_UP -> 4
                        PriceAlertLevel.PRICE_DROP -> 5
                        PriceAlertLevel.STABLE -> 6
                    }
                }
            }.thenByDescending { abs(it.priceChangePercent) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.itemName }
        )
    }

    fun buildIngredientPriceMatrix(
        inventoryItems: List<InventoryItemEntity>,
        inventoryOrders: List<InventoryOrderWithItems>,
        usages: List<InventoryUsageEntity> = emptyList(),
        priceAlertThresholdPct: Double = 5.0
    ): IngredientPriceMatrix {
        val dayDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sortedOrders = inventoryOrders.sortedBy { it.order.timestamp }
        val datesSet = linkedSetOf<String>()
        val ratesByItemAndDate = mutableMapOf<String, MutableMap<String, Double>>()

        for (orderWithItems in sortedOrders) {
            if (orderWithItems.order.timestamp <= 0L) continue
            val dateKey = dayDf.format(Date(orderWithItems.order.timestamp))
            var orderHasValidLine = false
            for (line in orderWithItems.items) {
                if (line.itemName.isBlank() || line.quantity <= 0.0) continue
                val matchedMaster = RawMaterialReceiptParser.matchCatalogItem(line.itemName, inventoryItems)
                val canonicalName = matchedMaster?.name ?: line.itemName.trim()
                val key = canonicalName.trim().lowercase(Locale.US)
                val rate = resolvePackUnitPrice(line, matchedMaster)
                if (rate <= 0.0) continue
                orderHasValidLine = true
                ratesByItemAndDate.getOrPut(key) { linkedMapOf() }[dateKey] = rate
            }
            if (orderHasValidLine) {
                datesSet.add(dateKey)
            }
        }

        val alertRows = evaluateInventoryAlerts(
            inventoryItems = inventoryItems,
            inventoryOrders = inventoryOrders,
            usages = usages,
            priceAlertThresholdPct = priceAlertThresholdPct
        )
        val dates = datesSet.toList()
        val matrixRows = alertRows.map { alert ->
            val key = alert.itemName.trim().lowercase(Locale.US)
            val trend = when (alert.priceAlertLevel) {
                PriceAlertLevel.HIGH_HIKE, PriceAlertLevel.MINOR_UP -> PriceTrend.INCREASED
                PriceAlertLevel.PRICE_DROP -> PriceTrend.DECREASED
                PriceAlertLevel.STABLE -> if (alert.previousRate == null) PriceTrend.FIRST_ENTRY else PriceTrend.UNCHANGED
            }
            IngredientPriceMatrixRow(
                itemName = alert.itemName,
                category = alert.category,
                unit = alert.unit,
                currentStock = alert.currentStock,
                stockAlertLevel = alert.stockAlertLevel,
                stockAlertLabel = alert.stockAlertLabel,
                suggestedOrderLabel = alert.suggestedOrderLabel,
                latestRate = alert.latestRate,
                priceChangePercent = alert.priceChangePercent,
                priceAlertLevel = alert.priceAlertLevel,
                priceAlertLabel = alert.priceAlertLabel,
                trend = trend,
                ratesByDate = ratesByItemAndDate[key].orEmpty()
            )
        }
        return IngredientPriceMatrix(dates = dates, rows = matrixRows)
    }

    fun buildIngredientPricingCsv(
        summaries: List<IngredientPriceSummary>,
        inventoryOrders: List<InventoryOrderWithItems>,
        exportedAtMs: Long = System.currentTimeMillis(),
        inventoryItems: List<InventoryItemEntity> = emptyList(),
        usages: List<InventoryUsageEntity> = emptyList(),
        priceAlertThresholdPct: Double = 5.0
    ): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel opens ₹ and ↑/↓ cleanly
        sb.append('\uFEFF')

        val effectiveItems = inventoryItems.ifEmpty {
            summaries.map {
                InventoryItemEntity(
                    id = it.itemId,
                    name = it.itemName,
                    category = it.category,
                    unit = it.unit,
                    standardQuantity = it.packSize,
                    defaultPrice = it.latestRate,
                    currentStock = it.currentStock,
                    isActive = true
                )
            }
        }
        val matrix = buildIngredientPriceMatrix(
            inventoryItems = effectiveItems,
            inventoryOrders = inventoryOrders,
            usages = usages,
            priceAlertThresholdPct = priceAlertThresholdPct
        )

        sb.appendLine("FILTR COFFEE — DATE-WISE PRICE HISTORY & REORDER MATRIX,${escapeCsv("Generated: ${df.format(Date(exportedAtMs))}")},Alert Threshold,${String.format(Locale.US, "%.1f%%", priceAlertThresholdPct)}")
        val matrixHeader = listOf(
            "Ingredient Name",
            "Category",
            "Unit",
            "Present Stock",
            "Stock Alert",
            "Suggested Order",
            "Latest Price (INR)",
            "Price Alert"
        ) + matrix.dates
        sb.appendLine(matrixHeader.joinToString(",") { escapeCsv(it) })
        for (row in matrix.rows) {
            val baseCols = listOf(
                escapeCsv(row.itemName),
                escapeCsv(row.category),
                escapeCsv(row.unit),
                String.format(Locale.US, "%.2f", row.currentStock),
                escapeCsv(row.stockAlertLabel),
                escapeCsv(row.suggestedOrderLabel),
                String.format(Locale.US, "%.2f", row.latestRate),
                escapeCsv(row.priceAlertLabel)
            )
            val dateCols = matrix.dates.map { d ->
                row.ratesByDate[d]?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            }
            sb.appendLine((baseCols + dateCols).joinToString(","))
        }

        sb.appendLine()
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
