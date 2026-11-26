package com.example.domain.inventory

import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawMaterialPriceTrackerTest {

    private val milk = InventoryItemEntity(
        id = 1,
        name = "Full Cream Milk",
        category = "Dairy",
        unit = "L",
        standardQuantity = 1.0,
        defaultPrice = 65.0,
        currentStock = 10.0
    )

    private val beans = InventoryItemEntity(
        id = 2,
        name = "Espresso Coffee Beans",
        category = "Coffee",
        unit = "kg",
        standardQuantity = 1.0,
        defaultPrice = 1200.0,
        currentStock = 4.0
    )

    @Test
    fun `resolvePackUnitPrice handles sub-unit conversion cleanly`() {
        // 500 g of 1kg coffee beans bought for 700 -> pack rate is 1400/kg
        val halfKgLine = InventoryOrderItemEntity(
            orderId = "INV-1",
            itemName = "Espresso Coffee Beans",
            quantity = 500.0,
            unit = "g",
            unitPrice = 1.4,
            subtotal = 700.0
        )
        assertEquals(1400.0, RawMaterialPriceTracker.resolvePackUnitPrice(halfKgLine, beans), 0.01)
    }

    @Test
    fun `buildIngredientPricingSummaries detects latest vs previous price hike and exports utf8 excel csv`() {
        val bill1 = InventoryOrderWithItems(
            order = InventoryOrderEntity(
                orderId = "INV-20261120-001",
                timestamp = 1_000L,
                supplierName = "Metro Dairy",
                totalAmount = 650.0,
                syncStatus = "SYNCED"
            ),
            items = listOf(
                InventoryOrderItemEntity(
                    id = 1,
                    orderId = "INV-20261120-001",
                    itemName = "Full Cream Milk",
                    quantity = 10.0,
                    unit = "L",
                    unitPrice = 65.0,
                    subtotal = 650.0
                ),
                InventoryOrderItemEntity(
                    id = 2,
                    orderId = "INV-20261120-001",
                    itemName = "Espresso Coffee Beans",
                    quantity = 2.0,
                    unit = "kg",
                    unitPrice = 1200.0,
                    subtotal = 2400.0
                )
            )
        )

        val bill2 = InventoryOrderWithItems(
            order = InventoryOrderEntity(
                orderId = "INV-20261126-002",
                timestamp = 2_000L,
                supplierName = "Blinkit Wholesale",
                totalAmount = 720.0,
                syncStatus = "SYNCED"
            ),
            items = listOf(
                InventoryOrderItemEntity(
                    id = 3,
                    orderId = "INV-20261126-002",
                    itemName = "Full Cream Milk",
                    quantity = 10.0,
                    unit = "L",
                    unitPrice = 72.0,
                    subtotal = 720.0
                ),
                InventoryOrderItemEntity(
                    id = 4,
                    orderId = "INV-20261126-002",
                    itemName = "Espresso Coffee Beans",
                    quantity = 500.0,
                    unit = "g",
                    unitPrice = 1.1,
                    subtotal = 550.0 // ₹1100/kg drop vs ₹1200
                )
            )
        )

        val summaries = RawMaterialPriceTracker.buildIngredientPricingSummaries(
            inventoryItems = listOf(milk, beans),
            inventoryOrders = listOf(bill2, bill1) // unsorted input checks chronological ordering
        )

        // Price increase (Milk) comes first, then drop (Beans)
        assertEquals(2, summaries.size)
        val milkSummary = summaries[0]
        assertEquals("Full Cream Milk", milkSummary.itemName)
        assertEquals(PriceTrend.INCREASED, milkSummary.trend)
        assertEquals(72.0, milkSummary.latestRate, 0.01)
        assertEquals(65.0, milkSummary.previousRate!!, 0.01)
        assertEquals(7.0, milkSummary.priceDelta, 0.01)
        assertEquals("Blinkit Wholesale", milkSummary.lastSupplier)

        val beansSummary = summaries[1]
        assertEquals("Espresso Coffee Beans", beansSummary.itemName)
        assertEquals(PriceTrend.DECREASED, beansSummary.trend)
        assertEquals(1100.0, beansSummary.latestRate, 0.01)
        assertEquals(1200.0, beansSummary.previousRate!!, 0.01)

        val csv = RawMaterialPriceTracker.buildIngredientPricingCsv(summaries, listOf(bill1, bill2))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Full Cream Milk"))
        assertTrue(csv.contains("Blinkit Wholesale"))
        assertTrue(csv.contains("72.00"))
        assertTrue(csv.contains("65.00"))
        assertTrue(csv.contains("INV-20261126-002"))
    }
}
