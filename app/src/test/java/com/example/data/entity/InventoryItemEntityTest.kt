package com.example.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InventoryItemEntityTest {

    private fun item(
        name: String = "Test Item",
        unit: String = "kg",
        standardQuantity: Double = 1.0,
        defaultPrice: Double = 100.0
    ) = InventoryItemEntity(
        id = 1,
        name = name,
        category = "Ingredients",
        unit = unit,
        standardQuantity = standardQuantity,
        defaultPrice = defaultPrice
    )

    @Test
    fun `same unit conversions compute packs and cost across kg g L ml and pcs`() {
        val beans = item(name = "Coffee Beans", unit = "kg", standardQuantity = 5.0, defaultPrice = 2500.0)
        val (kgPacks, kgCost) = calculatePacksAndCost(2.5, "kilograms", beans)
        assertEquals(0.5, kgPacks, 1e-6)
        assertEquals(1250.0, kgCost, 1e-6)

        val cocoa = item(name = "Cocoa Powder", unit = "g", standardQuantity = 250.0, defaultPrice = 200.0)
        val (gPacks, gCost) = calculatePacksAndCost(500.0, "g", cocoa)
        assertEquals(2.0, gPacks, 1e-6)
        assertEquals(400.0, gCost, 1e-6)

        val milk = item(name = "Milk", unit = "L", standardQuantity = 1.0, defaultPrice = 64.0)
        val (lPacks, lCost) = calculatePacksAndCost(3.0, "litres", milk)
        assertEquals(3.0, lPacks, 1e-6)
        assertEquals(192.0, lCost, 1e-6)

        val syrup = item(name = "Vanilla Syrup", unit = "ml", standardQuantity = 750.0, defaultPrice = 600.0)
        val (mlPacks, mlCost) = calculatePacksAndCost(375.0, "ml", syrup)
        assertEquals(0.5, mlPacks, 1e-6)
        assertEquals(300.0, mlCost, 1e-6)

        val cups = item(name = "Cups", unit = "pcs", standardQuantity = 50.0, defaultPrice = 150.0)
        val (pcsPacks, pcsCost) = calculatePacksAndCost(25.0, "pieces", cups)
        assertEquals(0.5, pcsPacks, 1e-6)
        assertEquals(75.0, pcsCost, 1e-6)
    }

    @Test
    fun `cross unit conversions convert between kg and g as well as L and ml`() {
        val sugarKg = item(name = "Sugar", unit = "kg", standardQuantity = 5.0, defaultPrice = 250.0)
        val (gToKgPacks, gToKgCost) = calculatePacksAndCost(500.0, "g", sugarKg)
        assertEquals(0.1, gToKgPacks, 1e-6)
        assertEquals(25.0, gToKgCost, 1e-6)

        val matchaGrams = item(name = "Matcha", unit = "g", standardQuantity = 250.0, defaultPrice = 800.0)
        val (kgToGPacks, kgToGCost) = calculatePacksAndCost(0.5, "kg", matchaGrams)
        assertEquals(2.0, kgToGPacks, 1e-6)
        assertEquals(1600.0, kgToGCost, 1e-6)

        val milkLiter = item(name = "Oat Milk", unit = "L", standardQuantity = 1.0, defaultPrice = 280.0)
        val (mlToLPacks, mlToLCost) = calculatePacksAndCost(250.0, "ml", milkLiter)
        assertEquals(0.25, mlToLPacks, 1e-6)
        assertEquals(70.0, mlToLCost, 1e-6)

        val creamMl = item(name = "Whipping Cream", unit = "ml", standardQuantity = 500.0, defaultPrice = 220.0)
        val (lToMlPacks, lToMlCost) = calculatePacksAndCost(1.5, "liter", creamMl)
        assertEquals(3.0, lToMlPacks, 1e-6)
        assertEquals(660.0, lToMlCost, 1e-6)
    }

    @Test
    fun `condensed milk packet 210g at 76 rupees converts grams kg and packet counts`() {
        val condensedMilk = item(
            name = "Condensed Milk",
            unit = "packet",
            standardQuantity = 210.0,
            defaultPrice = 76.0
        )

        val (directPacks, directCost) = calculatePacksAndCost(2.0, "packet", condensedMilk)
        assertEquals(2.0, directPacks, 1e-6)
        assertEquals(152.0, directCost, 1e-6)

        val (halfPacks, halfCost) = calculatePacksAndCost(105.0, "g", condensedMilk)
        assertEquals(0.5, halfPacks, 1e-6)
        assertEquals(38.0, halfCost, 1e-6)

        val (fullPacks, fullCost) = calculatePacksAndCost(210.0, "g", condensedMilk)
        assertEquals(1.0, fullPacks, 1e-6)
        assertEquals(76.0, fullCost, 1e-6)

        val (kgPacks, kgCost) = calculatePacksAndCost(0.42, "kg", condensedMilk)
        assertEquals(2.0, kgPacks, 1e-6)
        assertEquals(152.0, kgCost, 1e-6)
    }

    @Test
    fun `container unit with small standardQuantity treats standardQuantity as liters or kg`() {
        val bottle = item(name = "Caramel Sauce", unit = "bottle", standardQuantity = 1.0, defaultPrice = 400.0)

        val (mlPacks, mlCost) = calculatePacksAndCost(250.0, "ml", bottle)
        assertEquals(0.25, mlPacks, 1e-6)
        assertEquals(100.0, mlCost, 1e-6)

        val (literPacks, literCost) = calculatePacksAndCost(0.5, "L", bottle)
        assertEquals(0.5, literPacks, 1e-6)
        assertEquals(200.0, literCost, 1e-6)
    }

    @Test
    fun `zero or negative standardQuantity and non positive quantity are safely guarded`() {
        val zeroStdItem = item(name = "Zero Std", unit = "kg", standardQuantity = 0.0, defaultPrice = 50.0)
        val (packsZeroStd, costZeroStd) = calculatePacksAndCost(3.0, "kg", zeroStdItem)
        assertFalse(packsZeroStd.isNaN() || packsZeroStd.isInfinite())
        assertFalse(costZeroStd.isNaN() || costZeroStd.isInfinite())
        assertEquals(3.0, packsZeroStd, 1e-6)
        assertEquals(150.0, costZeroStd, 1e-6)

        val negStdItem = item(name = "Neg Std", unit = "g", standardQuantity = -10.0, defaultPrice = 40.0)
        val (packsNegStd, costNegStd) = calculatePacksAndCost(2.0, "g", negStdItem)
        assertEquals(2.0, packsNegStd, 1e-6)
        assertEquals(80.0, costNegStd, 1e-6)

        val normalItem = item(name = "Normal", unit = "kg", standardQuantity = 1.0, defaultPrice = 100.0)
        assertEquals(Pair(0.0, 0.0), calculatePacksAndCost(0.0, "kg", normalItem))
        assertEquals(Pair(0.0, 0.0), calculatePacksAndCost(-1.0, "kg", normalItem))
    }

    @Test
    fun `unit strings with whitespace and mixed casing are normalized`() {
        val milk = item(name = "Milk", unit = " Liter ", standardQuantity = 2.0, defaultPrice = 120.0)
        val (packs, cost) = calculatePacksAndCost(500.0, "  ML ", milk)
        assertEquals(0.25, packs, 1e-6)
        assertEquals(30.0, cost, 1e-6)
    }
}
