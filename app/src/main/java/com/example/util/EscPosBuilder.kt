package com.example.util

import com.example.data.entity.OrderWithItems
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ESC/POS command builder for thermal receipt printers.
 * Generates the byte stream matching the FILTR COFFEE receipt design.
 * Optimized for 80mm POS-80C thermal printers (48 chars/line).
 */
object EscPosBuilder {

    // ESC/POS Commands
    private val ESC = 0x1B.toByte()
    private val GS = 0x1D.toByte()
    private val LF = 0x0A.toByte()

    // Initialize printer
    private val CMD_INIT = byteArrayOf(ESC, 0x40) // ESC @

    // Text alignment
    private val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00) // ESC a 0
    private val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01) // ESC a 1
    private val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02) // ESC a 2

    // Text emphasis
    private val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01) // ESC E 1
    private val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00) // ESC E 0

    // Text size (double height/width)
    private val SIZE_NORMAL = byteArrayOf(GS, 0x21, 0x00) // GS ! 0
    private val SIZE_DOUBLE_HEIGHT = byteArrayOf(GS, 0x21, 0x01) // GS ! 1
    private val SIZE_DOUBLE_WIDTH = byteArrayOf(GS, 0x21, 0x10) // GS ! 16
    private val SIZE_DOUBLE_BOTH = byteArrayOf(GS, 0x21, 0x11) // GS ! 17

    // Underline
    private val UNDERLINE_ON = byteArrayOf(ESC, 0x2D, 0x01) // ESC - 1
    private val UNDERLINE_OFF = byteArrayOf(ESC, 0x2D, 0x00) // ESC - 0

    // Line spacing
    private val LINE_SPACING_DEFAULT = byteArrayOf(ESC, 0x32) // ESC 2
    private val LINE_SPACING_TIGHT = byteArrayOf(ESC, 0x33, 0x16) // ESC 3 22

    // Paper cut
    private val CUT_PAPER = byteArrayOf(GS, 0x56, 0x00) // GS V 0 (full cut)
    private val CUT_PAPER_PARTIAL = byteArrayOf(GS, 0x56, 0x01) // GS V 1 (partial cut)

    // Feed lines
    private fun feedLines(n: Int) = byteArrayOf(ESC, 0x64, n.toByte()) // ESC d n

    // 80mm printer = 48 monospace characters per line
    private const val LINE_WIDTH = 48

    /**
     * Build a compact kitchen token for the kitchen staff.
     * Printed FIRST before the customer receipt.
     */
    fun buildKitchenToken(orderWithItems: OrderWithItems): ByteArray {
        val out = ByteArrayOutputStream()
        val order = orderWithItems.order
        val items = orderWithItems.items
        val tokenNumber = order.orderId.substringAfterLast("-")
        val dateStr = SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.getDefault()).format(Date(order.timestamp))

        // Initialize
        out.write(CMD_INIT)
        out.write(LINE_SPACING_DEFAULT)

        // === HEADER ===
        out.write(ALIGN_CENTER)
        out.write(BOLD_ON)
        out.writeText("=".repeat(LINE_WIDTH))
        out.write(SIZE_DOUBLE_BOTH)
        out.writeText("KITCHEN TOKEN")
        out.write(SIZE_NORMAL)
        out.writeText("=".repeat(LINE_WIDTH))
        out.write(BOLD_OFF)

        out.writeText("")

        // === TOKEN NUMBER (big & bold) ===
        out.write(ALIGN_CENTER)
        out.write(SIZE_DOUBLE_BOTH)
        out.write(BOLD_ON)
        out.writeText("TOKEN #$tokenNumber")
        out.write(SIZE_NORMAL)
        out.write(BOLD_OFF)

        // Dine-in / Customer
        out.writeText("")
        if (order.customerName.isNotBlank() && order.customerName != "Walk-in Customer") {
            out.write(SIZE_DOUBLE_HEIGHT)
            out.writeText(order.customerName)
            out.write(SIZE_NORMAL)
        } else {
            out.write(SIZE_DOUBLE_HEIGHT)
            out.writeText("DINE-IN")
            out.write(SIZE_NORMAL)
        }

        out.writeText("")
        out.writeText("-".repeat(LINE_WIDTH))

        // === ITEMS LIST (simple, bold, big) ===
        out.write(ALIGN_LEFT)
        val groupedItems = items.groupBy { it.itemName }
        for ((name, groupItems) in groupedItems) {
            val qty = groupItems.sumOf { it.quantity }
            out.write(SIZE_DOUBLE_HEIGHT)
            out.write(BOLD_ON)
            val itemLine = "  $name"
            val qtyStr = "x$qty  "
            val padded = itemLine.padEnd(LINE_WIDTH / 2 - qtyStr.length) + qtyStr
            out.writeText(padded)
            out.write(BOLD_OFF)
            out.write(SIZE_NORMAL)
        }

        // Notes
        if (order.notes.isNotBlank()) {
            out.writeText("")
            out.writeText("-".repeat(LINE_WIDTH))
            out.write(BOLD_ON)
            out.writeText("  Notes: ${order.notes}")
            out.write(BOLD_OFF)
        }

        out.writeText("-".repeat(LINE_WIDTH))

        // Date
        out.write(ALIGN_CENTER)
        out.writeText(dateStr)
        out.writeText("")
        out.write(BOLD_ON)
        out.writeText("=".repeat(LINE_WIDTH))
        out.write(BOLD_OFF)

        // Feed and partial cut
        out.write(feedLines(3))
        out.write(CUT_PAPER_PARTIAL)

        return out.toByteArray()
    }

    /**
     * Build a complete customer receipt for thermal printing.
     * Designed for 80mm POS-80C (48 chars/line).
     */
    fun buildReceipt(
        orderWithItems: OrderWithItems,
        storyPhotoBytes: ByteArray? = null,
        storyPhotoWidthBytes: Int = 0,
        storyPhotoHeight: Int = 0,
        storyTitle: String? = null,
        storyCaption: String? = null,
        storyDate: String? = null,
        headerImageBytes: ByteArray? = null,
        headerImageWidthBytes: Int = 0,
        headerImageHeight: Int = 0,
        headerSubtitle: String = "where coffee meets vibe",
        tagline: String = "We are working for you <3",
        footerLine1: String = "Thank you for hanging out with us!",
        footerLine2: String = "Tag your cup on Instagram @filtr.coffeee",
        footerLine3: String = "Made fresh - Crafted with obsession"
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val order = orderWithItems.order
        val items = orderWithItems.items
        val dateStr = SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
        val tokenNumber = order.orderId.substringAfterLast("-")

        // Initialize
        out.write(CMD_INIT)
        out.write(LINE_SPACING_DEFAULT)

        // === HEADER ===
        out.write(ALIGN_CENTER)

        // If we have a rendered header image (e.g. Peace Sans), print it
        if (headerImageBytes != null && headerImageBytes.isNotEmpty() && headerImageHeight > 0) {
            out.write(printRasterImage(headerImageBytes, headerImageWidthBytes, headerImageHeight))
            out.writeText("")
        } else {
            // Fallback: text header
            out.write(SIZE_DOUBLE_BOTH)
            out.write(BOLD_ON)
            out.writeText("FILTR COFFEE")
            out.write(SIZE_NORMAL)
            out.write(BOLD_OFF)
            if (headerSubtitle.isNotBlank()) {
                out.writeText(headerSubtitle)
            }
        }

        out.writeText("")

        // === THICK DIVIDER ===
        out.write(BOLD_ON)
        out.writeText("=".repeat(LINE_WIDTH))
        out.write(BOLD_OFF)

        // === TOKEN & ORDER INFO ===
        out.write(ALIGN_LEFT)
        out.write(SIZE_DOUBLE_HEIGHT)
        out.write(BOLD_ON)

        // Token + Dine-in/Customer on same line
        val tokenStr = "TOKEN #$tokenNumber"
        val typeStr = if (order.customerName.isNotBlank() && order.customerName != "Walk-in Customer") {
            order.customerName
        } else {
            "DINE-IN"
        }
        // In double-height mode, effective chars per line = LINE_WIDTH / 1 (width is normal)
        val tokenLine = tokenStr.padEnd(LINE_WIDTH / 2) + typeStr.padStart(LINE_WIDTH / 2)
        out.writeText(tokenLine.take(LINE_WIDTH))
        out.write(SIZE_NORMAL)
        out.write(BOLD_OFF)

        // Date and Invoice
        val invStr = "INV: ${order.orderId}"
        val dateInvLine = dateStr.padEnd(LINE_WIDTH - invStr.length) + invStr
        out.writeText(dateInvLine.take(LINE_WIDTH))

        if (order.contactNumber.isNotBlank()) {
            out.writeText("Contact: ${order.contactNumber}")
        }

        // === DASH DIVIDER ===
        out.writeText("-".repeat(LINE_WIDTH))

        // === ITEMS TABLE HEADER ===
        out.write(BOLD_ON)
        out.writeText(formatTableRow("ITEM", "QTY", "PRICE"))
        out.write(BOLD_OFF)
        out.writeText("-".repeat(LINE_WIDTH))

        // === ITEMS ===
        val groupedItems = items.groupBy { it.itemName }
        var totalQty = 0
        for ((name, groupItems) in groupedItems) {
            val qty = groupItems.sumOf { it.quantity }
            val subtotal = groupItems.sumOf { it.subtotal }
            totalQty += qty
            out.writeText(formatTableRow(
                truncate(name, LINE_WIDTH - 16),
                qty.toString(),
                String.format(Locale.US, "%.0f", subtotal)
            ))
        }

        // Notes
        if (order.notes.isNotBlank()) {
            out.writeText("  * ${order.notes}")
        }

        // === SUBTOTAL LINE ===
        out.writeText("-".repeat(LINE_WIDTH))
        out.writeText(formatRightAligned(
            "Items Total ($totalQty items)",
            String.format(Locale.US, "%.0f", order.totalAmount)
        ))

        // === GRAND TOTAL ===
        out.write(BOLD_ON)
        out.writeText("=".repeat(LINE_WIDTH))
        out.write(SIZE_DOUBLE_HEIGHT)
        val totalLabel = "TOTAL TO PAY"
        val totalValue = "Rs.${String.format(Locale.US, "%.0f", order.totalAmount)}"
        out.writeText(formatRightAligned(totalLabel, totalValue))
        out.write(SIZE_NORMAL)
        out.write(BOLD_OFF)

        // === PAYMENT INFO ===
        out.writeText("-".repeat(LINE_WIDTH))
        val paymentText = when {
            order.isPaid && order.paymentMode == "UPI" -> "Mode: UPI / Paid"
            order.isPaid && order.paymentMode == "CASH" -> "Mode: CASH / Paid"
            order.isPaid -> "Mode: ${order.paymentMode} / Paid"
            else -> "Mode: UNPAID"
        }
        out.writeText(paymentText)

        // === STORY PHOTO CARD (Behind The Brew) ===
        if (storyPhotoBytes != null && storyPhotoBytes.isNotEmpty() && storyPhotoHeight > 0) {
            out.writeText("")
            out.write(ALIGN_CENTER)
            out.writeText("-".repeat(LINE_WIDTH))
            out.write(BOLD_ON)
            val btbHeader = if (!storyDate.isNullOrBlank()) {
                "BEHIND THE BREW - $storyDate"
            } else {
                "BEHIND THE BREW"
            }
            out.writeText(btbHeader)
            out.write(BOLD_OFF)
            out.writeText("-".repeat(LINE_WIDTH))

            // Print raster image using GS v 0
            out.write(printRasterImage(storyPhotoBytes, storyPhotoWidthBytes, storyPhotoHeight))

            out.writeText("")

            if (!storyTitle.isNullOrBlank()) {
                out.write(BOLD_ON)
                out.writeText(storyTitle)
                out.write(BOLD_OFF)
            }
            if (!storyCaption.isNullOrBlank()) {
                out.write(ALIGN_LEFT)
                // Word wrap caption
                val wrapped = wordWrap(storyCaption, LINE_WIDTH)
                for (line in wrapped) {
                    out.writeText(line)
                }
            }
            if (tagline.isNotBlank()) {
                out.write(ALIGN_CENTER)
                out.writeText("")
                out.writeText(tagline)
            }
        }

        // === FOOTER ===
        out.writeText("")
        out.write(ALIGN_CENTER)
        out.writeText("-".repeat(LINE_WIDTH))
        if (footerLine1.isNotBlank()) out.writeText(footerLine1)
        if (footerLine2.isNotBlank()) out.writeText(footerLine2)
        if (footerLine3.isNotBlank()) {
            out.writeText("")
            out.writeText(footerLine3)
        }
        out.writeText("")

        // Feed and cut
        out.write(feedLines(4))
        out.write(CUT_PAPER_PARTIAL)

        return out.toByteArray()
    }

    /**
     * Build GS v 0 raster image command.
     */
    private fun printRasterImage(data: ByteArray, widthBytes: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        // GS v 0 m xL xH yL yH [data]
        out.write(byteArrayOf(GS, 0x76, 0x30, 0x00)) // GS v 0 (normal density)
        out.write(byteArrayOf(
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(),
            ((height shr 8) and 0xFF).toByte()
        ))
        out.write(data)
        return out.toByteArray()
    }

    // === HELPERS ===

    private fun ByteArrayOutputStream.writeText(text: String) {
        // Use CP437-safe ASCII only — no multi-byte UTF-8
        val safeText = text
            .replace("\u2022", "-")  // bullet
            .replace("\u2013", "-")  // en dash
            .replace("\u2014", "-")  // em dash
            .replace("\u2018", "'")  // left single quote
            .replace("\u2019", "'")  // right single quote
            .replace("\u201C", "\"") // left double quote
            .replace("\u201D", "\"") // right double quote
            .replace("\u2026", "...") // ellipsis
            .replace("\u00B7", "-")  // middle dot
            .replace("\u2022", "*")  // bullet
            .replace("\u2665", "<3") // heart
            .replace("\u2764", "<3") // heavy heart
        write(safeText.toByteArray(Charsets.US_ASCII))
        write(byteArrayOf(LF))
    }

    private fun formatTableRow(item: String, qty: String, price: String): String {
        val qtyCol = 6
        val priceCol = 10
        val itemCol = LINE_WIDTH - qtyCol - priceCol
        return item.padEnd(itemCol).take(itemCol) +
               qty.padStart(qtyCol) +
               price.padStart(priceCol)
    }

    private fun formatRightAligned(label: String, value: String): String {
        val available = LINE_WIDTH - value.length - 1
        return label.take(available).padEnd(available) + " " + value
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text else text.take(maxLen - 2) + ".."
    }

    private fun wordWrap(text: String, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            if (currentLine.length + word.length + 1 > maxWidth) {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
