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

/**
 * Single chronological bill cell for the horizontal SKU Price History matrix.
 */
data class SkuBillPoint(
    val billIndex: Int, // 1-based: Bill 1, Bill 2, ...
    val price: Double,
    val timestamp: Long,
    val dateLabel: String, // e.g. "28 Oct"
    val orderId: String,
    val supplierName: String,
    val isUnitAnomaly: Boolean = false,
    val anomalyReason: String = ""
) {
    fun formatCell(includeDateInCells: Boolean): String {
        val compact = formatCompactNumber(price)
        return if (isUnitAnomaly) {
            if (includeDateInCells) "CHECK UNIT ($compact, $dateLabel)" else "CHECK UNIT"
        } else {
            if (includeDateInCells) "$compact ($dateLabel)" else compact
        }
    }
}

/**
 * One row in the horizontal SKU Price History sheet.
 * Columns: SKU Name | Unit / Weight | Latest Price (INR) | Price Change vs First (%) | Bill 1 .. Bill N
 */
data class SkuPriceHistoryRow(
    val skuName: String,
    val unitWeightLabel: String,
    val latestPrice: Double,
    val firstPrice: Double,
    val priceChangeVsFirstPct: Double?, // null when < 2 valid bills
    val isPriceHike: Boolean,
    val hasUnitAnomaly: Boolean,
    val bills: List<SkuBillPoint>
)

data class SkuPriceHistoryMatrix(
    val maxBillColumns: Int,
    val rows: List<SkuPriceHistoryRow>
)

data class BillLedgerRow(
    val orderId: String,
    val timestamp: Long,
    val dateFormatted: String,
    val supplierName: String,
    val rawItemName: String,
    val matchedSkuName: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val subtotal: Double,
    val normalizedPrice: Double,
    val statusLabel: String,
    val notes: String
)

private fun formatCompactNumber(v: Double): String {
    val rounded = kotlin.math.round(v * 100.0) / 100.0
    return if (abs(rounded - rounded.roundToLong()) < 0.005) {
        rounded.roundToLong().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded)
    }
}

object RawMaterialPriceTracker {

    private val WEIGHT_G = setOf("g", "gm", "gms", "gram", "grams")
    private val WEIGHT_KG = setOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms")
    private val VOL_ML = setOf("ml", "mls", "milliliter", "milliliters", "millilitre", "millilitres")
    private val VOL_L = setOf("l", "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres")
    private val COUNT_CONTAINER = setOf(
        "pcs", "pc", "piece", "pieces", "nos", "no", "unit", "units",
        "pack", "packs", "pkt", "pkts", "packet", "packets",
        "box", "boxes", "btl", "btls", "bottle", "bottles",
        "jar", "jars", "tin", "tins", "can", "cans",
        "case", "cases", "bag", "bags", "pouch", "pouches", "carton", "cartons", "bill"
    )

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

    /**
     * Normalizes an ordered line item's cost to a consistent per-SKU pack/unit rate.
     * Prevents false multiplication spikes when bills use container/count units (e.g. "1 pcs" of a 250g pack
     * or "1 pcs" of a 24-pack Water Bottle case).
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
        if (effectiveTotal <= 0.0) return 0.0
        val rawPerBilledUnit = effectiveTotal / orderItem.quantity
        if (masterItem == null) return round2(rawPerBilledUnit)

        val bUnit = orderItem.unit.trim().lowercase(Locale.US)
        val mUnit = masterItem.unit.trim().lowercase(Locale.US)
        val packSize = if (masterItem.standardQuantity > 0.0) masterItem.standardQuantity else 1.0

        val bIsWeight = bUnit in WEIGHT_G || bUnit in WEIGHT_KG
        val mIsWeight = mUnit in WEIGHT_G || mUnit in WEIGHT_KG
        val bIsVol = bUnit in VOL_ML || bUnit in VOL_L
        val mIsVol = mUnit in VOL_ML || mUnit in VOL_L
        val bIsCount = bUnit.isEmpty() || bUnit in COUNT_CONTAINER
        val mIsPcs = mUnit in setOf("pcs", "pc", "piece", "pieces")

        // Case A: Both Weight (g <-> kg)
        if (bIsWeight && mIsWeight) {
            val convertedQty = when {
                bUnit in WEIGHT_G && mUnit in WEIGHT_KG -> orderItem.quantity / 1000.0
                bUnit in WEIGHT_KG && mUnit in WEIGHT_G -> orderItem.quantity * 1000.0
                else -> orderItem.quantity
            }
            val packs = convertedQty / packSize
            return if (packs > 0.0) round2(effectiveTotal / packs) else round2(rawPerBilledUnit)
        }

        // Case B: Both Volume (ml <-> L)
        if (bIsVol && mIsVol) {
            val convertedQty = when {
                bUnit in VOL_ML && mUnit in VOL_L -> orderItem.quantity / 1000.0
                bUnit in VOL_L && mUnit in VOL_ML -> orderItem.quantity * 1000.0
                else -> orderItem.quantity
            }
            val packs = convertedQty / packSize
            return if (packs > 0.0) round2(effectiveTotal / packs) else round2(rawPerBilledUnit)
        }

        // Case C: Billed in Count/Container ("1 pcs", "2 pkt", "1 btl") for a Weight/Volume pack item
        // e.g. Peri Peri: "1 pcs @ ₹140" mapped to "Peri Peri Seasoning (250 g pack)" -> ₹140 (NOT 140 * 250 = ₹35,000!)
        if (bIsCount && (mIsWeight || mIsVol)) {
            return round2(rawPerBilledUnit)
        }

        // Case D: Billed in Count/Container ("1 pcs" or "12 pcs") mapped to multi-piece pack (e.g. 24-pack Water)
        if (bIsCount && mIsPcs && packSize > 1.0) {
            val defaultPackRate = masterItem.defaultPrice
            // Only scale up by packSize if clearly billed per single bottle/piece (< 40% of standard pack price)
            if (defaultPackRate > 0.0 && rawPerBilledUnit < defaultPackRate * 0.4 &&
                bUnit !in setOf("pack", "packs", "pkt", "pkts", "box", "boxes", "case", "cases", "carton", "cartons")
            ) {
                val packs = orderItem.quantity / packSize
                if (packs > 0.0) return round2(effectiveTotal / packs)
            }
            return round2(rawPerBilledUnit)
        }

        // Case E: Fallback — treat billed unit directly without hazardous division
        return round2(rawPerBilledUnit)
    }

    fun formatUnitWeightLabel(masterItem: InventoryItemEntity?, fallbackUnit: String): String {
        if (masterItem == null) {
            val u = RawMaterialReceiptParser.normalizeUnit(fallbackUnit.ifBlank { "pcs" })
            return "per $u"
        }
        val mUnit = masterItem.unit.trim().lowercase(Locale.US)
        val qty = masterItem.standardQuantity.let { if (it > 0.0) it else 1.0 }
        val qtyStr = formatCompactNumber(qty)
        return when {
            mUnit in WEIGHT_KG -> if (abs(qty - 1.0) < 0.01) "per kg" else "per ${qtyStr}kg pack"
            mUnit in WEIGHT_G -> if (abs(qty - 1000.0) < 0.01) "per kg" else "per ${qtyStr}g pack"
            mUnit in VOL_L -> if (abs(qty - 1.0) < 0.01) "per L" else "per ${qtyStr}L pack"
            mUnit in VOL_ML -> if (abs(qty - 1000.0) < 0.01) "per L" else "per ${qtyStr}ml pack"
            mUnit in setOf("pcs", "pc", "piece", "pieces") ->
                if (abs(qty - 1.0) < 0.01) "per pcs" else "per $qtyStr-pack"
            else -> if (abs(qty - 1.0) < 0.01) "per ${masterItem.unit}" else "per $qtyStr ${masterItem.unit}"
        }
    }

    /**
     * Builds the clean, compact horizontal SKU Price History matrix requested by the owner:
     * - One row per billed SKU
     * - Bill 1 (oldest) -> Bill N (latest), no empty date column gaps
     * - Flags >3x or <0.3x sudden unit mismatches as CHECK UNIT without skewing % change or latest price
     */
    fun buildSkuPriceHistoryMatrix(
        inventoryItems: List<InventoryItemEntity>,
        inventoryOrders: List<InventoryOrderWithItems>
    ): SkuPriceHistoryMatrix {
        val sortedOrders = inventoryOrders.sortedBy { it.order.timestamp }
        val dayDf = SimpleDateFormat("dd MMM", Locale.US)

        data class RawEntry(
            val rate: Double,
            val timestamp: Long,
            val dateLabel: String,
            val orderId: String,
            val supplier: String,
            val master: InventoryItemEntity?,
            val fallbackUnit: String,
            val canonicalName: String
        )

        val entriesByKey = linkedMapOf<String, MutableList<RawEntry>>()

        for (orderWithItems in sortedOrders) {
            val order = orderWithItems.order
            val ts = if (order.timestamp > 0L) order.timestamp else System.currentTimeMillis()
            val dateLabel = dayDf.format(Date(ts))
            for (line in orderWithItems.items) {
                if (line.itemName.isBlank() || line.quantity <= 0.0) continue
                if (RawMaterialReceiptParser.isLikelyNonIngredientHardware(line.itemName)) continue
                val matchedMaster = RawMaterialReceiptParser.matchCatalogItem(line.itemName, inventoryItems)
                val canonicalName = matchedMaster?.name ?: RawMaterialReceiptParser.normalizeSkuMatchText(line.itemName)
                    .split(" ")
                    .joinToString(" ") { w -> w.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() } }
                    .ifBlank { line.itemName.trim() }
                val key = canonicalName.trim().lowercase(Locale.US)
                val rate = resolvePackUnitPrice(line, matchedMaster)
                if (rate <= 0.0) continue

                entriesByKey.getOrPut(key) { mutableListOf() }.add(
                    RawEntry(
                        rate = rate,
                        timestamp = ts,
                        dateLabel = dateLabel,
                        orderId = order.orderId,
                        supplier = order.supplierName.ifBlank { "Supplier" },
                        master = matchedMaster,
                        fallbackUnit = line.unit,
                        canonicalName = canonicalName
                    )
                )
            }
        }

        val rows = mutableListOf<SkuPriceHistoryRow>()
        var maxBills = 0

        for ((_, rawList) in entriesByKey) {
            if (rawList.isEmpty()) continue
            val sample = rawList.first()
            val displaySku = sample.canonicalName
            val unitWeight = formatUnitWeightLabel(sample.master, sample.fallbackUnit)

            val billPoints = mutableListOf<SkuBillPoint>()
            var lastValidPrice: Double? = null
            var hasAnomaly = false

            rawList.forEachIndexed { idx, entry ->
                val isAnomaly = lastValidPrice != null &&
                    (entry.rate > lastValidPrice!! * 3.0 || entry.rate < lastValidPrice!! * 0.3)
                if (isAnomaly) {
                    hasAnomaly = true
                } else {
                    lastValidPrice = entry.rate
                }
                billPoints.add(
                    SkuBillPoint(
                        billIndex = idx + 1,
                        price = entry.rate,
                        timestamp = entry.timestamp,
                        dateLabel = entry.dateLabel,
                        orderId = entry.orderId,
                        supplierName = entry.supplier,
                        isUnitAnomaly = isAnomaly,
                        anomalyReason = if (isAnomaly) "CHECK UNIT" else ""
                    )
                )
            }

            if (billPoints.size > maxBills) {
                maxBills = billPoints.size
            }

            val validBills = billPoints.filter { !it.isUnitAnomaly }
            val firstValid = validBills.firstOrNull()?.price ?: billPoints.first().price
            val latestValid = validBills.lastOrNull()?.price ?: billPoints.last().price
            val pctChange = if (validBills.size >= 2 && firstValid > 0.0) {
                round2(((latestValid - firstValid) / firstValid) * 100.0)
            } else {
                null
            }
            val isHike = (pctChange ?: 0.0) > 0.0

            rows.add(
                SkuPriceHistoryRow(
                    skuName = displaySku,
                    unitWeightLabel = unitWeight,
                    latestPrice = round2(latestValid),
                    firstPrice = round2(firstValid),
                    priceChangeVsFirstPct = pctChange,
                    isPriceHike = isHike,
                    hasUnitAnomaly = hasAnomaly,
                    bills = billPoints
                )
            )
        }

        // Put price hikes (> 0%) at the very top descending, then anomalies, then rest alphabetically
        val sortedRows = rows.sortedWith(
            compareBy<SkuPriceHistoryRow>(
                { if (it.isPriceHike) 0 else if (it.hasUnitAnomaly) 1 else 2 },
                { -(it.priceChangeVsFirstPct ?: 0.0) },
                { it.skuName.lowercase(Locale.US) }
            )
        )

        return SkuPriceHistoryMatrix(
            maxBillColumns = maxBills,
            rows = sortedRows
        )
    }

    /**
     * Chronological Bill Ledger (oldest -> newest) for the second Excel/Google Sheet tab.
     */
    fun buildBillLedger(
        inventoryItems: List<InventoryItemEntity>,
        inventoryOrders: List<InventoryOrderWithItems>
    ): List<BillLedgerRow> {
        val df = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        val sortedOrders = inventoryOrders.sortedBy { it.order.timestamp }
        val result = mutableListOf<BillLedgerRow>()

        val lastValidByKey = mutableMapOf<String, Double>()

        for (orderWithItems in sortedOrders) {
            val order = orderWithItems.order
            val dateStr = if (order.timestamp > 0L) df.format(Date(order.timestamp)) else ""
            for (line in orderWithItems.items) {
                if (line.itemName.isBlank() || line.quantity <= 0.0) continue
                val isHardware = RawMaterialReceiptParser.isLikelyNonIngredientHardware(line.itemName)
                val matched = if (isHardware) null else RawMaterialReceiptParser.matchCatalogItem(line.itemName, inventoryItems)
                val matchedName = matched?.name ?: if (isHardware) "Non-Ingredient / Hardware" else line.itemName.trim()
                val rate = resolvePackUnitPrice(line, matched)
                val key = matchedName.lowercase(Locale.US)
                val prev = lastValidByKey[key]
                val isAnomaly = !isHardware && prev != null && rate > 0.0 && (rate > prev * 3.0 || rate < prev * 0.3)
                if (!isAnomaly && !isHardware && rate > 0.0) {
                    lastValidByKey[key] = rate
                }
                val status = when {
                    isHardware -> "HARDWARE / EXCLUDED"
                    isAnomaly -> "CHECK UNIT"
                    else -> "OK"
                }
                result.add(
                    BillLedgerRow(
                        orderId = order.orderId,
                        timestamp = order.timestamp,
                        dateFormatted = dateStr,
                        supplierName = order.supplierName.ifBlank { "Supplier" },
                        rawItemName = line.itemName,
                        matchedSkuName = matchedName,
                        quantity = line.quantity,
                        unit = line.unit,
                        unitPrice = line.unitPrice,
                        subtotal = line.subtotal,
                        normalizedPrice = rate,
                        statusLabel = status,
                        notes = order.notes
                    )
                )
            }
        }
        return result
    }

    fun buildSkuPriceHistoryCsv(
        matrix: SkuPriceHistoryMatrix,
        includeDateInCells: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        val headerCols = mutableListOf(
            "SKU Name",
            "Unit / Weight",
            "Latest Price (INR)",
            "Price Change vs First (%)"
        )
        for (i in 1..matrix.maxBillColumns) {
            headerCols.add("Bill $i")
        }
        sb.appendLine(headerCols.joinToString(",") { escapeCsv(it) })

        for (row in matrix.rows) {
            val pctStr = row.priceChangeVsFirstPct?.let {
                val prefix = if (it > 0.0) "+" else ""
                "$prefix${String.format(Locale.US, "%.1f", it)}%"
            } ?: "0.0%"
            val cols = mutableListOf(
                escapeCsv(row.skuName),
                escapeCsv(row.unitWeightLabel),
                formatCompactNumber(row.latestPrice),
                escapeCsv(pctStr)
            )
            for (i in 0 until matrix.maxBillColumns) {
                val cell = row.bills.getOrNull(i)?.formatCell(includeDateInCells).orEmpty()
                cols.add(escapeCsv(cell))
            }
            sb.appendLine(cols.joinToString(","))
        }
        return sb.toString()
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
        val uncatalogedSamples = linkedMapOf<String, Pair<String, String>>()

        for (orderWithItems in sortedOrders) {
            val order = orderWithItems.order
            for (line in orderWithItems.items) {
                if (line.itemName.isBlank() || line.quantity <= 0.0) continue
                if (RawMaterialReceiptParser.isLikelyNonIngredientHardware(line.itemName)) continue
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
        // Filter >3x/<0.3x anomalies out when determining previous/latest rate
        val validPoints = mutableListOf<Triple<Double, String, Long>>()
        var lastValid: Double? = null
        for (p in points) {
            val isAnomaly = lastValid != null && (p.first > lastValid!! * 3.0 || p.first < lastValid!! * 0.3)
            if (!isAnomaly) {
                validPoints.add(p)
                lastValid = p.first
            }
        }
        val effective = validPoints.ifEmpty { points }
        val latest = effective.lastOrNull()
        val latestRate = latest?.first ?: fallbackRate
        val prevPoint = if (effective.size >= 2) effective[effective.size - 2] else null
        val prevRate = prevPoint?.first ?: if (effective.size == 1 && fallbackRate > 0.0 && abs(fallbackRate - latestRate) > 0.005) {
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
            totalPurchasesCount = effective.size
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
                if (RawMaterialReceiptParser.isLikelyNonIngredientHardware(line.itemName)) continue
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
        val skuMatrix = buildSkuPriceHistoryMatrix(
            inventoryItems = inventoryItems.ifEmpty {
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
            },
            inventoryOrders = inventoryOrders
        )
        return buildSkuPriceHistoryCsv(skuMatrix, includeDateInCells = true)
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
