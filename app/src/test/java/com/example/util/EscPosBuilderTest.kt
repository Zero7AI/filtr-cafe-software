package com.example.util

import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class EscPosBuilderTest {

    private val fixedTimestamp = 1_700_000_000_000L

    private fun sampleOrder(
        orderId: String = "FLT-20261125-0042",
        customerName: String = "Rahul",
        contactNumber: String = "9876543210",
        notes: String = "Less sugar",
        isPaid: Boolean = true,
        paymentMode: String = "UPI",
        items: List<OrderItemEntity> = listOf(
            OrderItemEntity(
                id = 1,
                orderId = orderId,
                menuItemId = 1,
                itemName = "OG Vietnamese",
                itemPrice = 109.0,
                quantity = 2,
                subtotal = 218.0
            ),
            OrderItemEntity(
                id = 2,
                orderId = orderId,
                menuItemId = 2,
                itemName = "Peri Peri Fries",
                itemPrice = 99.0,
                quantity = 1,
                subtotal = 99.0
            )
        )
    ): OrderWithItems {
        val total = items.sumOf { it.subtotal }
        return OrderWithItems(
            order = OrderEntity(
                orderId = orderId,
                customerName = customerName,
                contactNumber = contactNumber,
                totalAmount = total,
                timestamp = fixedTimestamp,
                isPaid = isPaid,
                paymentMode = paymentMode,
                notes = notes
            ),
            items = items
        )
    }

    /**
     * Strips ESC/POS control sequences so tests can inspect pure ASCII receipt lines.
     */
    private fun extractPrintedLines(bytes: ByteArray): List<String> {
        val textOut = ByteArrayOutputStream()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when (b) {
                0x1B -> {
                    val cmd = bytes.getOrNull(i + 1)?.toInt()?.and(0xFF)
                    i += if (cmd == 0x40 || cmd == 0x32) 2 else 3
                }
                0x1D -> {
                    val cmd = bytes.getOrNull(i + 1)?.toInt()?.and(0xFF)
                    if (cmd == 0x76 && i + 7 < bytes.size) {
                        val widthBytes = (bytes[i + 4].toInt() and 0xFF) or
                            ((bytes[i + 5].toInt() and 0xFF) shl 8)
                        val height = (bytes[i + 6].toInt() and 0xFF) or
                            ((bytes[i + 7].toInt() and 0xFF) shl 8)
                        i += 8 + (widthBytes * height)
                    } else {
                        i += 3
                    }
                }
                else -> {
                    textOut.write(b)
                    i++
                }
            }
        }
        return textOut.toString(Charsets.US_ASCII.name()).split('\n')
    }

    private fun extractTextRawBytes(bytes: ByteArray): ByteArray {
        val textOut = ByteArrayOutputStream()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when (b) {
                0x1B -> {
                    val cmd = bytes.getOrNull(i + 1)?.toInt()?.and(0xFF)
                    i += if (cmd == 0x40 || cmd == 0x32) 2 else 3
                }
                0x1D -> {
                    i += 3
                }
                else -> {
                    textOut.write(b)
                    i++
                }
            }
        }
        return textOut.toByteArray()
    }

    @Test
    fun `column constants match 58mm and 80mm thermal paper specs`() {
        assertEquals(32, EscPosBuilder.COLUMNS_58MM)
        assertEquals(48, EscPosBuilder.COLUMNS_80MM)
    }

    @Test
    fun `buildReceipt respects 58mm and 80mm line widths and wraps invoice on 58mm`() {
        val order = sampleOrder()

        val bytes58 = EscPosBuilder.buildReceipt(order, lineWidth = EscPosBuilder.COLUMNS_58MM)
        val lines58 = extractPrintedLines(bytes58)
        assertTrue(lines58.contains("=".repeat(32)))
        assertTrue(lines58.contains("-".repeat(32)))
        assertTrue(lines58.all { it.length <= EscPosBuilder.COLUMNS_58MM })
        assertTrue(lines58.any { it == "INV: FLT-20261125-0042" })

        val bytes80 = EscPosBuilder.buildReceipt(order, lineWidth = EscPosBuilder.COLUMNS_80MM)
        val lines80 = extractPrintedLines(bytes80)
        assertTrue(lines80.contains("=".repeat(48)))
        assertTrue(lines80.contains("-".repeat(48)))
        assertTrue(lines80.all { it.length <= EscPosBuilder.COLUMNS_80MM })
        assertTrue(lines80.any { it.contains("INV: FLT-20261125-0042") && it.length == 48 })
    }

    @Test
    fun `buildReceipt and buildKitchenToken emit init and cut sequences with expected content`() {
        val order = sampleOrder(customerName = "Walk-in Customer", isPaid = false, paymentMode = "UNPAID")

        val tokenBytes = EscPosBuilder.buildKitchenToken(order, lineWidth = EscPosBuilder.COLUMNS_58MM)
        assertArrayEquals(byteArrayOf(0x1B, 0x40), tokenBytes.copyOfRange(0, 2))
        assertArrayEquals(
            byteArrayOf(0x1B, 0x64, 0x03, 0x1D, 0x56, 0x01),
            tokenBytes.copyOfRange(tokenBytes.size - 6, tokenBytes.size)
        )
        val tokenLines = extractPrintedLines(tokenBytes)
        assertTrue(tokenLines.contains("KITCHEN TOKEN"))
        assertTrue(tokenLines.contains("TOKEN #0042"))
        assertTrue(tokenLines.contains("DINE-IN"))
        assertTrue(tokenLines.any { it.contains("OG Vietnamese") && it.endsWith("x2") })
        assertTrue(tokenLines.all { it.length <= EscPosBuilder.COLUMNS_58MM })

        val receiptBytes = EscPosBuilder.buildReceipt(order, lineWidth = EscPosBuilder.COLUMNS_80MM)
        assertArrayEquals(byteArrayOf(0x1B, 0x40), receiptBytes.copyOfRange(0, 2))
        assertArrayEquals(
            byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x01),
            receiptBytes.copyOfRange(receiptBytes.size - 6, receiptBytes.size)
        )
        val receiptLines = extractPrintedLines(receiptBytes)
        assertTrue(receiptLines.contains("FILTR COFFEE"))
        assertTrue(receiptLines.any { it.startsWith("TOKEN #0042") && it.endsWith("DINE-IN") })
        assertTrue(receiptLines.any { it.contains("Items Total (3 items)") && it.endsWith("317") })
        assertTrue(receiptLines.any { it.contains("TOTAL TO PAY") && it.endsWith("Rs.317") })
        assertTrue(receiptLines.contains("Mode: UNPAID"))
    }

    @Test
    fun `buildReceipt embeds raster header and story images using GS v 0`() {
        val order = sampleOrder()
        val headerRaster = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0xFF.toByte(), 0x00)
        val storyRaster = byteArrayOf(0x11, 0x22, 0x33, 0x44)

        val receiptBytes = EscPosBuilder.buildReceipt(
            orderWithItems = order,
            storyPhotoBytes = storyRaster,
            storyPhotoWidthBytes = 2,
            storyPhotoHeight = 2,
            storyTitle = "Morning Roast",
            storyCaption = "Single origin pour over",
            storyDate = "25 Nov",
            headerImageBytes = headerRaster,
            headerImageWidthBytes = 2,
            headerImageHeight = 2,
            lineWidth = EscPosBuilder.COLUMNS_80MM
        )

        val lines = extractPrintedLines(receiptBytes)
        assertFalse(lines.contains("FILTR COFFEE"))
        assertTrue(lines.contains("BEHIND THE BREW - 25 Nov"))
        assertTrue(lines.contains("Morning Roast"))
        assertTrue(lines.contains("Single origin pour over"))

        // Verify GS v 0 header + payload appears in byte stream
        val expectedHeaderCmd = byteArrayOf(0x1D, 0x76, 0x30, 0x00, 0x02, 0x00, 0x02, 0x00) + headerRaster
        assertTrue(containsSubarray(receiptBytes, expectedHeaderCmd))
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        for (start in 0..(haystack.size - needle.size)) {
            var match = true
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    @Test
    fun `long text overflow truncates and wraps without IllegalArgumentException`() {
        val longName = "SupercalifragilisticexpialidociousArtisanalHazelnutCaramelFrappe"
        val longCustomer = "AlexanderMaximilianBartholomewTheThirdOfSouthMumbai"
        val longUnbrokenNote = "X".repeat(95)
        val order = sampleOrder(
            orderId = "FLT-20261125-9999",
            customerName = longCustomer,
            contactNumber = "+91-9876543210-EXT-99999999999999",
            notes = longUnbrokenNote,
            items = listOf(
                OrderItemEntity(
                    id = 1,
                    orderId = "FLT-20261125-9999",
                    menuItemId = 1,
                    itemName = longName,
                    itemPrice = 129.0,
                    quantity = 2,
                    subtotal = 258.0
                )
            )
        )

        for (width in listOf(-5, 0, 10, EscPosBuilder.COLUMNS_58MM, EscPosBuilder.COLUMNS_80MM)) {
            val expectedSafeWidth = width.coerceAtLeast(24)
            val tokenBytes = EscPosBuilder.buildKitchenToken(order, lineWidth = width)
            val tokenLines = extractPrintedLines(tokenBytes)
            assertTrue(tokenLines.all { it.length <= expectedSafeWidth })
            assertTrue(tokenLines.any { it.contains("..") })

            val receiptBytes = EscPosBuilder.buildReceipt(
                orderWithItems = order,
                footerLine1 = "A".repeat(80),
                lineWidth = width
            )
            val receiptLines = extractPrintedLines(receiptBytes)
            assertTrue(receiptLines.all { it.length <= expectedSafeWidth })
            assertTrue(receiptLines.any { it.contains("..") })
        }
    }

    @Test
    fun `text output sanitizes unicode punctuation to CP437 safe ASCII bytes`() {
        val order = sampleOrder(
            notes = "\u2022 \u2013 \u2014 \u2018hi\u2019 \u201Cyo\u201D \u2026 \u00B7 \u20B9150 \u2665 \u2764 \u2615"
        )
        val bytes = EscPosBuilder.buildReceipt(
            orderWithItems = order,
            tagline = "Crafted \u2665 \u20B999",
            lineWidth = EscPosBuilder.COLUMNS_80MM
        )

        val textRawBytes = extractTextRawBytes(bytes)
        for (rawByte in textRawBytes) {
            val unsigned = rawByte.toInt() and 0xFF
            assertTrue("Expected 7-bit ASCII byte, got $unsigned", unsigned <= 0x7F)
        }

        val joined = extractPrintedLines(bytes).joinToString("\n")
        assertTrue(joined.contains("* - - 'hi' \"yo\" ... - Rs.150 <3 <3 ?"))
    }
}
