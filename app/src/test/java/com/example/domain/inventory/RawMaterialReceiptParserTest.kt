package com.example.domain.inventory

import com.example.data.entity.InventoryItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RawMaterialReceiptParserTest {

    private val catalog = listOf(
        InventoryItemEntity(id = 1, name = "Full Cream Milk", category = "Dairy", unit = "L", standardQuantity = 1.0, defaultPrice = 68.0),
        InventoryItemEntity(id = 2, name = "Espresso Coffee Beans", category = "Coffee", unit = "kg", standardQuantity = 1.0, defaultPrice = 1200.0),
        InventoryItemEntity(id = 3, name = "Oat Milk", category = "Dairy", unit = "pack", standardQuantity = 1.0, defaultPrice = 240.0)
    )

    @Test
    fun `normalizeUnit maps supplier units cleanly to supported app units`() {
        assertEquals("kg", RawMaterialReceiptParser.normalizeUnit("KGS"))
        assertEquals("L", RawMaterialReceiptParser.normalizeUnit("litres"))
        assertEquals("g", RawMaterialReceiptParser.normalizeUnit("gms"))
        assertEquals("ml", RawMaterialReceiptParser.normalizeUnit("ML"))
        assertEquals("pcs", RawMaterialReceiptParser.normalizeUnit("bottles"))
        assertEquals("pack", RawMaterialReceiptParser.normalizeUnit("pkt"))
    }

    @Test
    fun `parseGeminiReceiptJson matches catalog items and derives rate from line total`() {
        val rawJson = """
            ```json
            {
              "supplierName": "Blinkit Wholesale",
              "invoiceNumber": "BLK-9081",
              "invoiceDate": "2026-11-26",
              "notes": "GST Included",
              "items": [
                {
                  "name": "Full Cream Milk",
                  "quantity": 10.0,
                  "unit": "liters",
                  "totalPrice": 660.0
                },
                {
                  "name": "Espresso Coffee Beans",
                  "quantity": 2.0,
                  "unit": "kgs",
                  "unitPrice": 1150.0,
                  "totalPrice": 2300.0
                }
              ]
            }
            ```
        """.trimIndent()

        val result = RawMaterialReceiptParser.parseGeminiReceiptJson(
            rawJsonText = rawJson,
            catalog = catalog,
            sourceLabel = "blinkit_bill.pdf"
        )
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertEquals("Blinkit Wholesale", parsed!!.supplierName)
        assertTrue(parsed.notes.contains("Invoice #BLK-9081"))
        assertTrue(parsed.notes.contains("File: blinkit_bill.pdf"))
        assertEquals(2, parsed.items.size)

        assertEquals("Full Cream Milk", parsed.items[0].itemName)
        assertEquals("L", parsed.items[0].unit)
        assertEquals(10.0, parsed.items[0].quantity, 0.01)
        assertEquals(66.0, parsed.items[0].unitPrice, 0.01)

        assertEquals("Espresso Coffee Beans", parsed.items[1].itemName)
        assertEquals("kg", parsed.items[1].unit)
        assertEquals(1150.0, parsed.items[1].unitPrice, 0.01)
    }

    @Test
    fun `parsePlainTextReceipt handles whatsapp text bill fallback`() {
        val text = """
            Metro Dairy Supply
            Full Cream Milk 12 L @ 65
            Oat Milk - 4 pack - 920
        """.trimIndent()

        val result = RawMaterialReceiptParser.parsePlainTextReceipt(
            rawText = text,
            catalog = catalog,
            sourceLabel = "WhatsApp Text"
        )
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()!!
        assertEquals("Metro Dairy Supply", parsed.supplierName)
        assertEquals(2, parsed.items.size)
        assertEquals(65.0, parsed.items[0].unitPrice, 0.01)
        assertEquals(230.0, parsed.items[1].unitPrice, 0.01)
    }
}
