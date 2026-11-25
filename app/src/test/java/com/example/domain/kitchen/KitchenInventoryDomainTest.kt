package com.example.domain.kitchen

import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.ui.FiltrTab
import com.example.ui.STAFF_ALLOWED_TABS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenInventoryDomainTest {

    @Test
    fun `seed catalog contains exact 47 items with empty Outside Freezer`() {
        val items = KitchenInventorySeedCatalog.defaultItems()
        assertEquals(47, items.size)

        val deepFreezer = items.filter { it.sectionId == KitchenSection.DEEP_FREEZER.id }
        val fridge = items.filter { it.sectionId == KitchenSection.REFRIGERATOR_FREEZER.id }
        val outside = items.filter { it.sectionId == KitchenSection.OUTSIDE_FREEZER.id }
        val normal = items.filter { it.sectionId == KitchenSection.NORMAL_TEMPERATURE.id }

        assertEquals(6, deepFreezer.size)
        assertEquals(8, fridge.size)
        assertEquals(0, outside.size)
        assertEquals(33, normal.size)

        val names = items.map { it.name }.toSet()
        assertTrue(names.contains("French Fries"))
        assertTrue(names.contains("Ice Cream"))
        assertTrue(names.contains("Paneer"))
        assertTrue(names.contains("Thousand Island Sauce"))
        assertTrue(names.contains("Condensed Milk"))
        assertTrue(names.contains("Hazelnut Monin Syrup"))
        assertTrue(names.contains("Shower Caps"))
        assertTrue(names.contains("Spoons"))
    }

    @Test
    fun `sealed and opened stock calculations for grams and ml`() {
        // French Fries: packSize 2500g, 2 sealed + 1 opened with 800g remaining = 5800g
        val friesTotal = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_GRAMS.id,
            packSize = 2500.0,
            sealedQty = 2,
            openedQty = 1,
            openedRemainingQty = 800.0
        )
        assertEquals(5800.0, friesTotal, 0.001)

        // Thousand Island Sauce: packSize 1000ml, 1 sealed + 1 opened with 350ml remaining = 1350ml
        val sauceTotal = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_ML.id,
            packSize = 1000.0,
            sealedQty = 1,
            openedQty = 1,
            openedRemainingQty = 350.0
        )
        assertEquals(1350.0, sauceTotal, 0.001)

        // When openedQty == 0, leftover openedRemainingQty should not inflate total
        val sealedOnly = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_GRAMS.id,
            packSize = 400.0,
            sealedQty = 3,
            openedQty = 0,
            openedRemainingQty = 200.0
        )
        assertEquals(1200.0, sealedOnly, 0.001)
    }

    @Test
    fun `percentage based remaining quantities calculate accurately`() {
        // Ice Cream: packSize 1.0 tub, 2 sealed + 1 opened @ 25% = 2.25
        val pct25 = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_PERCENTAGE.id,
            packSize = 1.0,
            sealedQty = 2,
            openedQty = 1,
            openedRemainingQty = 25.0
        )
        assertEquals(2.25, pct25, 0.001)

        val pct50 = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_PERCENTAGE.id,
            packSize = 1.0,
            sealedQty = 1,
            openedQty = 1,
            openedRemainingQty = 50.0
        )
        assertEquals(1.5, pct50, 0.001)

        val pct75 = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_PERCENTAGE.id,
            packSize = 1.0,
            sealedQty = 0,
            openedQty = 2,
            openedRemainingQty = 75.0
        )
        assertEquals(1.5, pct75, 0.001)

        val pct100 = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.SEALED_AND_PERCENTAGE.id,
            packSize = 1.0,
            sealedQty = 3,
            openedQty = 1,
            openedRemainingQty = 100.0
        )
        assertEquals(4.0, pct100, 0.001)
    }

    @Test
    fun `gram ml and piece direct quantities ignore sealed counts`() {
        val paneerGrams = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.DIRECT_GRAMS.id,
            packSize = 1.0,
            sealedQty = 99,
            openedQty = 99,
            openedRemainingQty = 750.0
        )
        assertEquals(750.0, paneerGrams, 0.001)

        val directMl = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.DIRECT_ML.id,
            packSize = 1.0,
            sealedQty = 0,
            openedQty = 0,
            openedRemainingQty = 620.0
        )
        assertEquals(620.0, directMl, 0.001)

        val cupsPcs = KitchenInventoryCalculator.calculateTotalUsableQty(
            countingMode = KitchenCountingMode.DIRECT_PIECES.id,
            packSize = 1.0,
            sealedQty = 0,
            openedQty = 0,
            openedRemainingQty = 140.0
        )
        assertEquals(140.0, cupsPcs, 0.001)
    }

    @Test
    fun `automatic low stock alerts and reorder list suggestions`() {
        val items = KitchenInventorySeedCatalog.defaultItems()
        val fries = items.first { it.id == "df_french_fries" } // min 2500, target 7500, packSize 2500
        val hotCups = items.first { it.id == "nt_hot_cups" } // min 50, target 200, direct pcs

        assertEquals(KitchenStockStatus.GREEN, KitchenInventoryCalculator.evaluateStockStatus(3000.0, fries.minStockThreshold))
        assertEquals(KitchenStockStatus.ORANGE, KitchenInventoryCalculator.evaluateStockStatus(1200.0, fries.minStockThreshold))
        assertEquals(KitchenStockStatus.RED, KitchenInventoryCalculator.evaluateStockStatus(0.0, fries.minStockThreshold))

        assertNull(KitchenInventoryCalculator.computeReorderRecommendation(fries, 3000.0))

        val friesReorder = KitchenInventoryCalculator.computeReorderRecommendation(fries, 1200.0)
        assertNotNull(friesReorder)
        assertEquals(KitchenStockStatus.ORANGE, friesReorder!!.status)
        assertEquals("REORDER SOON", friesReorder.orderStatusLabel)
        assertEquals(3, friesReorder.suggestedPacksToOrder) // ceil((7500 - 1200) / 2500) = 3 packs = 7500g
        assertEquals(7500.0, friesReorder.suggestedPurchaseQty, 0.001)

        val cupsOut = KitchenInventoryCalculator.computeReorderRecommendation(hotCups, 0.0)
        assertNotNull(cupsOut)
        assertEquals(KitchenStockStatus.RED, cupsOut!!.status)
        assertEquals("URGENT — OUT OF STOCK", cupsOut.orderStatusLabel)
        assertEquals(200.0, cupsOut.suggestedPurchaseQty, 0.001)
    }

    @Test
    fun `opening vs closing variance identifies consumed and wastage accurately`() {
        val items = KitchenInventorySeedCatalog.defaultItems()
        val paneer = items.first { it.id == "rf_paneer" }

        val opening = listOf(
            KitchenInventoryEntryEntity(
                shiftId = "2026-11-25_OPENING",
                dateKey = "2026-11-25",
                shiftType = "OPENING",
                itemId = paneer.id,
                itemNameSnapshot = paneer.name,
                sectionIdSnapshot = paneer.sectionId,
                unitLabelSnapshot = paneer.unitLabel,
                countingModeSnapshot = paneer.countingMode,
                packSizeSnapshot = paneer.packSize,
                totalUsableQty = 1500.0,
                stockStatus = "GREEN",
                isChecked = true
            )
        )
        val closing = listOf(
            KitchenInventoryEntryEntity(
                shiftId = "2026-11-25_CLOSING",
                dateKey = "2026-11-25",
                shiftType = "CLOSING",
                itemId = paneer.id,
                itemNameSnapshot = paneer.name,
                sectionIdSnapshot = paneer.sectionId,
                unitLabelSnapshot = paneer.unitLabel,
                countingModeSnapshot = paneer.countingMode,
                packSizeSnapshot = paneer.packSize,
                totalUsableQty = 400.0,
                wastageQty = 100.0,
                wastageReason = "Expired",
                stockStatus = "ORANGE",
                isChecked = true
            )
        )

        val rows = KitchenInventoryCalculator.computeVarianceRows(listOf(paneer), opening, closing)
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(1500.0, row.openingQty!!, 0.001)
        assertEquals(400.0, row.closingQty!!, 0.001)
        assertEquals(100.0, row.wastageQty, 0.001)
        assertEquals("Expired", row.wastageReason)
        assertEquals(1000.0, row.consumedQty!!, 0.001) // 1500 - 400 - 100
        assertEquals(KitchenStockStatus.ORANGE, row.latestStatus)
    }

    @Test
    fun `staff allowed tabs preserve billing orders checklist and kitchen inventory only`() {
        assertTrue(FiltrTab.KITCHEN_INVENTORY in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.BILLING in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.ORDERS in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.CHECKLIST in STAFF_ALLOWED_TABS)
        assertFalse(FiltrTab.PROFIT_LOSS in STAFF_ALLOWED_TABS)
        assertFalse(FiltrTab.DASHBOARD in STAFF_ALLOWED_TABS)
    }
}
