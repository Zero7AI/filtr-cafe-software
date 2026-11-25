package com.example.domain.kitchen

import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.data.entity.KitchenInventoryItemEntity
import com.example.data.entity.KitchenInventoryShiftEntity
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class KitchenSection(
    val id: String,
    val title: String,
    val shortTitle: String,
    val iconEmoji: String,
    val orderIndex: Int,
    val isConfigurableEmptyDefault: Boolean = false
) {
    DEEP_FREEZER(
        id = "DEEP_FREEZER",
        title = "Deep Freezer",
        shortTitle = "Deep Freezer",
        iconEmoji = "❄️",
        orderIndex = 1
    ),
    REFRIGERATOR_FREEZER(
        id = "REFRIGERATOR_FREEZER",
        title = "Refrigerator / Freezer",
        shortTitle = "Fridge",
        iconEmoji = "🧊",
        orderIndex = 2
    ),
    OUTSIDE_FREEZER(
        id = "OUTSIDE_FREEZER",
        title = "Outside Freezer",
        shortTitle = "Outside Freezer",
        iconEmoji = "🌡️",
        orderIndex = 3,
        isConfigurableEmptyDefault = true
    ),
    NORMAL_TEMPERATURE(
        id = "NORMAL_TEMPERATURE",
        title = "Normal Temperature Storage",
        shortTitle = "Normal Storage",
        iconEmoji = "🧺",
        orderIndex = 4
    );

    companion object {
        fun fromId(id: String): KitchenSection =
            entries.firstOrNull { it.id == id } ?: NORMAL_TEMPERATURE
    }
}

enum class KitchenCountingMode(
    val id: String,
    val displayLabel: String,
    val usesSealedOpened: Boolean
) {
    SEALED_AND_PERCENTAGE("SEALED_AND_PERCENTAGE", "Sealed + Opened (% left)", true),
    SEALED_AND_GRAMS("SEALED_AND_GRAMS", "Sealed + Opened (grams)", true),
    SEALED_AND_ML("SEALED_AND_ML", "Sealed + Opened (ml)", true),
    SEALED_AND_PIECES("SEALED_AND_PIECES", "Sealed + Opened (pcs)", true),
    DIRECT_GRAMS("DIRECT_GRAMS", "Total Remaining (grams)", false),
    DIRECT_ML("DIRECT_ML", "Total Remaining (ml)", false),
    DIRECT_PIECES("DIRECT_PIECES", "Total Pieces", false);

    companion object {
        fun fromId(id: String): KitchenCountingMode =
            entries.firstOrNull { it.id == id } ?: DIRECT_PIECES
    }
}

enum class KitchenShiftType(val id: String, val label: String) {
    OPENING("OPENING", "Opening Check"),
    CLOSING("CLOSING", "Closing Check");

    companion object {
        fun fromId(id: String): KitchenShiftType =
            if (id.equals("CLOSING", ignoreCase = true)) CLOSING else OPENING
    }
}

enum class KitchenStockStatus(val id: String, val label: String) {
    GREEN("GREEN", "Sufficient"),
    ORANGE("ORANGE", "Low Stock"),
    RED("RED", "Out of Stock");

    companion object {
        fun fromId(id: String): KitchenStockStatus =
            entries.firstOrNull { it.id == id } ?: GREEN
    }
}

data class KitchenReorderRecommendation(
    val item: KitchenInventoryItemEntity,
    val currentStock: Double,
    val minimumRequiredStock: Double,
    val suggestedPurchaseQty: Double,
    val suggestedPacksToOrder: Int,
    val status: KitchenStockStatus,
    val orderStatusLabel: String
)

data class KitchenVarianceRow(
    val item: KitchenInventoryItemEntity,
    val openingQty: Double?,
    val closingQty: Double?,
    val wastageQty: Double,
    val wastageReason: String,
    val consumedQty: Double?,
    val latestStatus: KitchenStockStatus
)

object KitchenInventoryCalculator {

    fun buildShiftId(dateKey: String, shiftType: KitchenShiftType): String =
        "${dateKey}_${shiftType.id}"

    /**
     * Computes usable stock quantity in the item's unitLabel.
     * - Percentage mode (e.g. Ice Cream): sealedQty * packSize + openedQty * packSize * (pct / 100.0)
     * - Sealed + grams/ml/pcs: (sealedQty * packSize) + openedRemainingQty (when openedQty > 0)
     * - Direct grams/ml/pcs: directRemainingQty (ignoring unused sealed/opened fields)
     */
    fun calculateTotalUsableQty(
        countingMode: String,
        packSize: Double,
        sealedQty: Int,
        openedQty: Int,
        openedRemainingQty: Double
    ): Double {
        val safePack = if (packSize > 0.0) packSize else 1.0
        val safeSealed = sealedQty.coerceAtLeast(0)
        val safeOpened = openedQty.coerceAtLeast(0)
        val safeRem = openedRemainingQty.coerceAtLeast(0.0)

        val raw = when (KitchenCountingMode.fromId(countingMode)) {
            KitchenCountingMode.SEALED_AND_PERCENTAGE -> {
                val pctFraction = (safeRem.coerceIn(0.0, 100.0)) / 100.0
                val openedPart = if (safeOpened > 0) safeOpened * safePack * pctFraction else 0.0
                (safeSealed * safePack) + openedPart
            }
            KitchenCountingMode.SEALED_AND_GRAMS,
            KitchenCountingMode.SEALED_AND_ML,
            KitchenCountingMode.SEALED_AND_PIECES -> {
                val openedPart = if (safeOpened > 0) safeRem else 0.0
                (safeSealed * safePack) + openedPart
            }
            KitchenCountingMode.DIRECT_GRAMS,
            KitchenCountingMode.DIRECT_ML,
            KitchenCountingMode.DIRECT_PIECES -> {
                safeRem
            }
        }
        return roundTo2Decimals(raw)
    }

    fun evaluateStockStatus(
        totalUsableQty: Double,
        minStockThreshold: Double
    ): KitchenStockStatus {
        val usable = totalUsableQty.coerceAtLeast(0.0)
        val minReq = minStockThreshold.coerceAtLeast(0.0)
        return when {
            usable <= 0.0001 -> KitchenStockStatus.RED
            minReq > 0.0 && usable < minReq -> KitchenStockStatus.ORANGE
            else -> KitchenStockStatus.GREEN
        }
    }

    fun computeReorderRecommendation(
        item: KitchenInventoryItemEntity,
        currentStock: Double
    ): KitchenReorderRecommendation? {
        val status = evaluateStockStatus(currentStock, item.minStockThreshold)
        if (status == KitchenStockStatus.GREEN) return null

        val target = max(item.targetStockLevel, item.minStockThreshold * 1.5)
        val deficit = max(target - currentStock.coerceAtLeast(0.0), item.minStockThreshold)
        val safePack = if (item.packSize > 0.0) item.packSize else 1.0
        val packsNeeded = if (KitchenCountingMode.fromId(item.countingMode).usesSealedOpened) {
            max(1, ceil(deficit / safePack).toInt())
        } else {
            0
        }
        val suggestedQty = if (packsNeeded > 0) {
            roundTo2Decimals(packsNeeded * safePack)
        } else {
            roundTo2Decimals(deficit)
        }
        val orderStatus = if (status == KitchenStockStatus.RED) {
            "URGENT — OUT OF STOCK"
        } else {
            "REORDER SOON"
        }

        return KitchenReorderRecommendation(
            item = item,
            currentStock = roundTo2Decimals(currentStock.coerceAtLeast(0.0)),
            minimumRequiredStock = roundTo2Decimals(item.minStockThreshold),
            suggestedPurchaseQty = suggestedQty,
            suggestedPacksToOrder = packsNeeded,
            status = status,
            orderStatusLabel = orderStatus
        )
    }

    fun computeVarianceRows(
        items: List<KitchenInventoryItemEntity>,
        openingEntries: List<KitchenInventoryEntryEntity>,
        closingEntries: List<KitchenInventoryEntryEntity>
    ): List<KitchenVarianceRow> {
        val openingByItem = openingEntries.associateBy { it.itemId }
        val closingByItem = closingEntries.associateBy { it.itemId }

        return items.filter { it.isActive }.map { item ->
            val openEntry = openingByItem[item.id]
            val closeEntry = closingByItem[item.id]
            val openQty = openEntry?.totalUsableQty
            val closeQty = closeEntry?.totalUsableQty
            val waste = closeEntry?.wastageQty ?: 0.0
            val consumed = if (openQty != null && closeQty != null) {
                roundTo2Decimals((openQty - closeQty - waste).coerceAtLeast(0.0))
            } else {
                null
            }
            val latestQty = closeQty ?: openQty ?: item.minStockThreshold
            val status = if (closeEntry != null) {
                KitchenStockStatus.fromId(closeEntry.stockStatus)
            } else if (openEntry != null) {
                KitchenStockStatus.fromId(openEntry.stockStatus)
            } else {
                KitchenStockStatus.GREEN
            }
            KitchenVarianceRow(
                item = item,
                openingQty = openQty,
                closingQty = closeQty,
                wastageQty = roundTo2Decimals(waste),
                wastageReason = closeEntry?.wastageReason.orEmpty(),
                consumedQty = consumed,
                latestStatus = status
            )
        }
    }

    fun canSubmitShift(existingShift: KitchenInventoryShiftEntity?): Boolean {
        return existingShift?.status != "SUBMITTED"
    }

    fun formatQty(qty: Double, unitLabel: String): String {
        val rounded = if (qty % 1.0 == 0.0) {
            qty.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", qty).trimEnd('0').trimEnd('.')
        }
        return if (unitLabel.isBlank()) rounded else "$rounded $unitLabel"
    }

    fun roundTo2Decimals(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0
}

/**
 * Exact 47 configured items across 3 populated storage zones plus the configurable empty
 * Outside Freezer category (0 items seeded by design).
 */
object KitchenInventorySeedCatalog {

    private fun item(
        id: String,
        name: String,
        emoji: String,
        section: KitchenSection,
        mode: KitchenCountingMode,
        unit: String,
        container: String,
        packSize: Double,
        minThreshold: Double,
        targetLevel: Double,
        orderIndex: Int
    ) = KitchenInventoryItemEntity(
        id = id,
        name = name,
        iconEmoji = emoji,
        sectionId = section.id,
        countingMode = mode.id,
        unitLabel = unit,
        containerLabel = container,
        packSize = packSize,
        minStockThreshold = minThreshold,
        targetStockLevel = targetLevel,
        orderIndex = orderIndex,
        isDefault = true,
        isActive = true,
        updatedAt = 0L
    )

    fun defaultItems(): List<KitchenInventoryItemEntity> = listOf(
        // ==================== 1. DEEP FREEZER (6 items) ====================
        item("df_french_fries", "French Fries", "🍟", KitchenSection.DEEP_FREEZER, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 2500.0, 2500.0, 7500.0, 1),
        item("df_ice_cream", "Ice Cream", "🍨", KitchenSection.DEEP_FREEZER, KitchenCountingMode.SEALED_AND_PERCENTAGE, "tubs", "tubs", 1.0, 1.5, 4.0, 2),
        item("df_whipped_cream", "Whipped Cream", "🥛", KitchenSection.DEEP_FREEZER, KitchenCountingMode.SEALED_AND_ML, "ml", "packets", 1000.0, 1000.0, 3000.0, 3),
        item("df_mozzarella_cheese", "Mozzarella Cheese", "🧀", KitchenSection.DEEP_FREEZER, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 1000.0, 1000.0, 2000.0, 4),
        item("df_ice", "Ice", "🧊", KitchenSection.DEEP_FREEZER, KitchenCountingMode.DIRECT_GRAMS, "g", "", 1.0, 2000.0, 5000.0, 5),
        item("df_sweet_corn", "Sweet Corn", "🌽", KitchenSection.DEEP_FREEZER, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 1000.0, 1000.0, 2000.0, 6),

        // ==================== 2. REFRIGERATOR / FREEZER (8 items) ====================
        item("rf_paneer", "Paneer", "🧈", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.DIRECT_GRAMS, "g", "", 1.0, 500.0, 1500.0, 1),
        item("rf_thousand_island_sauce", "Thousand Island Sauce", "🥣", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 1000.0, 500.0, 2000.0, 2),
        item("rf_melted_cheese_sauce", "Melted Cheese Sauce", "🧀", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 1000.0, 500.0, 2000.0, 3),
        item("rf_butter", "Butter", "🧈", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 500.0, 500.0, 1500.0, 4),
        item("rf_cucumber", "Cucumber", "🥒", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.DIRECT_GRAMS, "g", "", 1.0, 500.0, 1500.0, 5),
        item("rf_shimla_mirch", "Shimla Mirch", "🫑", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.DIRECT_GRAMS, "g", "", 1.0, 400.0, 1000.0, 6),
        item("rf_olives", "Olives", "🫒", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.SEALED_AND_GRAMS, "g", "jars", 450.0, 300.0, 900.0, 7),
        item("rf_jalapeno", "Jalapeno", "🌶️", KitchenSection.REFRIGERATOR_FREEZER, KitchenCountingMode.SEALED_AND_GRAMS, "g", "jars", 450.0, 300.0, 900.0, 8),

        // ==================== 3. OUTSIDE FREEZER (0 items — configurable empty category) ====================

        // ==================== 4. NORMAL TEMPERATURE STORAGE (33 items) ====================
        item("nt_onion", "Onion", "🧅", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_GRAMS, "g", "", 1.0, 1000.0, 3000.0, 1),
        item("nt_peanut_butter", "Peanut Butter", "🥜", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "jars", 1000.0, 500.0, 2000.0, 2),
        item("nt_nutella_spread", "Nutella Spread", "🍫", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "jars", 750.0, 400.0, 1500.0, 3),
        item("nt_lotus_biscoff_spread", "Lotus Biscoff Spread", "🍪", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "jars", 400.0, 300.0, 800.0, 4),
        item("nt_honey", "Honey", "🍯", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 500.0, 300.0, 1000.0, 5),
        item("nt_peach_ice_tea", "Peach Ice Tea", "🍑", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 500.0, 500.0, 1500.0, 6),
        item("nt_lemon_ice_tea", "Lemon Ice Tea", "🍋", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 500.0, 500.0, 1500.0, 7),
        item("nt_chaat_masala", "Chaat Masala", "🧂", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 100.0, 100.0, 300.0, 8),
        item("nt_hot_cups", "Hot Cups", "☕", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 50.0, 200.0, 9),
        item("nt_cold_cups", "Cold Cups", "🥤", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 50.0, 200.0, 10),
        item("nt_cinnamon", "Cinnamon", "🪵", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 100.0, 100.0, 300.0, 11),
        item("nt_condensed_milk", "Condensed Milk", "🥛", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 400.0, 800.0, 2000.0, 12),
        item("nt_dustbin_bags", "Dustbin Bags", "🗑️", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 15.0, 50.0, 13),
        item("nt_bread", "Bread", "🍞", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 20.0, 60.0, 14),
        item("nt_coffee_packets", "Coffee Packets", "☕", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 1000.0, 1000.0, 3000.0, 15),
        item("nt_tissue_paper", "Tissue Paper", "🧻", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 100.0, 500.0, 16),
        item("nt_sandwich_boxes", "Sandwich Boxes", "🥪", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 30.0, 100.0, 17),
        item("nt_fries_boxes", "Fries Boxes", "🍟", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 30.0, 100.0, 18),
        item("nt_hand_wash", "Hand Wash", "🧼", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 500.0, 300.0, 1000.0, 19),
        item("nt_hand_sanitizer", "Hand Sanitizer", "🧴", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 500.0, 300.0, 1000.0, 20),
        item("nt_cranberry_juice", "Cranberry Juice", "🧃", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 1000.0, 1000.0, 3000.0, 21),
        item("nt_peri_peri_masala", "Peri Peri Masala", "🌶️", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_GRAMS, "g", "packets", 250.0, 200.0, 500.0, 22),
        item("nt_hazelnut_monin_syrup", "Hazelnut Monin Syrup", "🌰", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 700.0, 500.0, 1400.0, 23),
        item("nt_vanilla_monin_syrup", "Vanilla Monin Syrup", "🌼", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 700.0, 500.0, 1400.0, 24),
        item("nt_caramel_monin_syrup", "Caramel Monin Syrup", "🍮", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "bottles", 700.0, 500.0, 1400.0, 25),
        item("nt_tomato_sauce_sachets", "Tomato Sauce Sachets", "🍅", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 50.0, 200.0, 26),
        item("nt_sugar_sachets", "Sugar Sachets", "🍬", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 50.0, 200.0, 27),
        item("nt_oil_packets", "Oil Packets", "🫗", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.SEALED_AND_ML, "ml", "packets", 1000.0, 1000.0, 3000.0, 28),
        item("nt_banana", "Banana", "🍌", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 6.0, 18.0, 29),
        item("nt_gloves", "Gloves", "🧤", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 20.0, 100.0, 30),
        item("nt_shower_caps", "Shower Caps", "🧢", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 20.0, 100.0, 31),
        item("nt_straws", "Straws", "🥤", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 50.0, 200.0, 32),
        item("nt_spoons", "Spoons", "🥄", KitchenSection.NORMAL_TEMPERATURE, KitchenCountingMode.DIRECT_PIECES, "pcs", "", 1.0, 50.0, 200.0, 33)
    )
}
