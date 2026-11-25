package com.example.util

import com.example.data.entity.OrderWithItems
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ESC/POS command builder for thermal receipt printers.
 * Generates the byte stream matching the FILTR COFFEE receipt design.
 * Supports both 58mm (32 chars/line) and 80mm (48 chars/line) thermal printers.
 */
object EscPosBuilder {

    const val COLUMNS_58MM = 32
    const val COLUMNS_80MM = 48

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
    private fun feedLines(n: Int) = byteArrayOf(ESC, 0x64, n.coerceIn(0, 255).toByte()) // ESC d n

    private val receiptDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale.US)
    }

    private fun formatTimestamp(timestamp: Long): String =
        receiptDateFormat.get()!!.format(Date(timestamp))

    /**
     * Build a compact kitchen token for the kitchen staff.
     * Printed FIRST before the customer receipt.
     */
    fun buildKitchenToken(
        orderWithItems: OrderWithItems,
        lineWidth: Int = COLUMNS_80MM
    ): ByteArray {
        val safeWidth = lineWidth.coerceAtLeast(24)
        val out = ByteArrayOutputStream()
        val order = orderWithItems.order
        val items = orderWithItems.items
        val tokenNumber = order.orderId.substringAfterLast("-")
        val dateStr = formatTimestamp(order.timestamp)

        // Initialize
        out.write(CMD_INIT)
        out.write(LINE_SPACING_DEFAULT)

        // === HEADER ===
        out.write(ALIGN_CENTER)
        out.write(BOLD_ON)
        out.writeText("=".repeat(safeWidth))
        out.write(SIZE_DOUBLE_BOTH)
        out.writeText("KITCHEN TOKEN")
        out.write(SIZE_NORMAL)
        out.writeText("=".repeat(safeWidth))
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
            out.writeText(truncate(order.customerName, safeWidth))
            out.write(SIZE_NORMAL)
        } else {
            out.write(SIZE_DOUBLE_HEIGHT)
            out.writeText("DINE-IN")
            out.write(SIZE_NORMAL)
        }

        out.writeText("")
        out.writeText("-".repeat(safeWidth))

        // === ITEMS LIST (simple, bold, big) ===
        out.write(ALIGN_LEFT)
        val groupedItems = items.groupBy { it.itemName }
        for ((name, groupItems) in groupedItems) {
            val qty = groupItems.sumOf { it.quantity }
            out.write(SIZE_DOUBLE_HEIGHT)
            out.write(BOLD_ON)
            val qtyStr = "x$qty"
            val maxNameWidth = (safeWidth - qtyStr.length - 1).coerceAtLeast(4)
            val leftText = truncate("  $name", maxNameWidth).padEnd(maxNameWidth)
            out.writeText("$leftText $qtyStr")
            out.write(BOLD_OFF)
            out.write(SIZE_NORMAL)
        }

        // Notes
        if (order.notes.isNotBlank()) {
            out.writeText("")
            out.writeText("-".repeat(safeWidth))
            out.write(BOLD_ON)
            for (line in wordWrap("  Notes: ${order.notes}", safeWidth)) {
                out.writeText(line)
            }
            out.write(BOLD_OFF)
        }

        out.writeText("-".repeat(safeWidth))

        // Date
        out.write(ALIGN_CENTER)
        out.writeText(truncate(dateStr, safeWidth))
        out.writeText("")
        out.write(BOLD_ON)
        out.writeText("=".repeat(safeWidth))
        out.write(BOLD_OFF)

        // Feed and partial cut
        out.write(feedLines(3))
        out.write(CUT_PAPER_PARTIAL)

        return out.toByteArray()
    }

    /**
     * Build a complete customer receipt for thermal printing.
     * Supports 58mm (32 chars/line) and 80mm (48 chars/line, default).
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
        footerLine3: String = "Made fresh - Crafted with obsession",
        lineWidth: Int = COLUMNS_80MM
    ): ByteArray {
        val safeWidth = lineWidth.coerceAtLeast(24)
        val out = ByteArrayOutputStream()
        val order = orderWithItems.order
        val items = orderWithItems.items
        val dateStr = formatTimestamp(order.timestamp)
        val tokenNumber = order.orderId.substringAfterLast("-")

        // Initialize
        out.write(CMD_INIT)
        out.write(LINE_SPACING_DEFAULT)

        // === HEADER ===
        out.write(ALIGN_CENTER)

        // If we have a rendered header image (e.g. Peace Sans), print it
        if (headerImageBytes != null && headerImageBytes.isNotEmpty() && headerImageWidthBytes > 0 && headerImageHeight > 0) {
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
                for (line in wordWrap(headerSubtitle, safeWidth)) {
                    out.writeText(line)
                }
            }
        }

        out.writeText("")

        // === THICK DIVIDER ===
        out.write(BOLD_ON)
        out.writeText("=".repeat(safeWidth))
        out.write(BOLD_OFF)

        // === TOKEN & ORDER INFO ===
        out.write(ALIGN_LEFT)
        out.write(SIZE_DOUBLE_HEIGHT)
        out.write(BOLD_ON)

        // Token + Dine-in/Customer on same line (bounds-safe for both 32 and 48 cols)
        val tokenStr = "TOKEN #$tokenNumber"
        val typeStr = if (order.customerName.isNotBlank() && order.customerName != "Walk-in Customer") {
            order.customerName
        } else {
            "DINE-IN"
        }
        val maxCustomerCols = (safeWidth - tokenStr.length - 1).coerceAtLeast(4)
        out.writeText(formatRightAligned(tokenStr, truncate(typeStr, maxCustomerCols), safeWidth))
        out.write(SIZE_NORMAL)
        out.write(BOLD_OFF)

        // Date and Invoice (place on 2 lines if combined exceeds safeWidth, e.g. on 58mm)
        val invStr = "INV: ${order.orderId}"
        if (dateStr.length + 1 + invStr.length <= safeWidth) {
            out.writeText(formatRightAligned(dateStr, invStr, safeWidth))
        } else {
            out.writeText(truncate(dateStr, safeWidth))
            out.writeText(truncate(invStr, safeWidth))
        }

        if (order.contactNumber.isNotBlank()) {
            out.writeText(truncate("Contact: ${order.contactNumber}", safeWidth))
        }

        // === DASH DIVIDER ===
        out.writeText("-".repeat(safeWidth))

        // === ITEMS TABLE HEADER ===
        out.write(BOLD_ON)
        out.writeText(formatTableRow("ITEM", "QTY", "PRICE", safeWidth))
        out.write(BOLD_OFF)
        out.writeText("-".repeat(safeWidth))

        // === ITEMS ===
        val groupedItems = items.groupBy { it.itemName }
        var totalQty = 0
        for ((name, groupItems) in groupedItems) {
            val qty = groupItems.sumOf { it.quantity }
            val subtotal = groupItems.sumOf { it.subtotal }
            totalQty += qty
            out.writeText(
                formatTableRow(
                    name,
                    qty.toString(),
                    String.format(Locale.US, "%.0f", subtotal),
                    safeWidth
                )
            )
        }

        // Notes
        if (order.notes.isNotBlank()) {
            for (line in wordWrap("  * ${order.notes}", safeWidth)) {
                out.writeText(line)
            }
        }

        // === SUBTOTAL LINE ===
        out.writeText("-".repeat(safeWidth))
        out.writeText(
            formatRightAligned(
                "Items Total ($totalQty items)",
                String.format(Locale.US, "%.0f", order.totalAmount),
                safeWidth
            )
        )

        // === GRAND TOTAL ===
        out.write(BOLD_ON)
        out.writeText("=".repeat(safeWidth))
        out.write(SIZE_DOUBLE_HEIGHT)
        val totalLabel = "TOTAL TO PAY"
        val totalValue = "Rs.${String.format(Locale.US, "%.0f", order.totalAmount)}"
        out.writeText(formatRightAligned(totalLabel, totalValue, safeWidth))
        out.write(SIZE_NORMAL)
        out.write(BOLD_OFF)

        // === PAYMENT INFO ===
        out.writeText("-".repeat(safeWidth))
        val paymentText = when {
            order.isPaid && order.paymentMode == "UPI" -> "Mode: UPI / Paid"
            order.isPaid && order.paymentMode == "CASH" -> "Mode: CASH / Paid"
            order.isPaid -> "Mode: ${order.paymentMode} / Paid"
            else -> "Mode: UNPAID"
        }
        out.writeText(truncate(paymentText, safeWidth))

        // === STORY PHOTO CARD (Behind The Brew) ===
        if (storyPhotoBytes != null && storyPhotoBytes.isNotEmpty() && storyPhotoWidthBytes > 0 && storyPhotoHeight > 0) {
            out.writeText("")
            out.write(ALIGN_CENTER)
            out.writeText("-".repeat(safeWidth))
            out.write(BOLD_ON)
            val btbHeader = if (!storyDate.isNullOrBlank()) {
                "BEHIND THE BREW - $storyDate"
            } else {
                "BEHIND THE BREW"
            }
            for (line in wordWrap(btbHeader, safeWidth)) {
                out.writeText(line)
            }
            out.write(BOLD_OFF)
            out.writeText("-".repeat(safeWidth))

            // Print raster image using GS v 0
            out.write(printRasterImage(storyPhotoBytes, storyPhotoWidthBytes, storyPhotoHeight))

            out.writeText("")

            if (!storyTitle.isNullOrBlank()) {
                out.write(BOLD_ON)
                for (line in wordWrap(storyTitle, safeWidth)) {
                    out.writeText(line)
                }
                out.write(BOLD_OFF)
            }
            if (!storyCaption.isNullOrBlank()) {
                out.write(ALIGN_LEFT)
                for (line in wordWrap(storyCaption, safeWidth)) {
                    out.writeText(line)
                }
            }
            if (tagline.isNotBlank()) {
                out.write(ALIGN_CENTER)
                out.writeText("")
                for (line in wordWrap(tagline, safeWidth)) {
                    out.writeText(line)
                }
            }
        }

        // === FOOTER ===
        out.writeText("")
        out.write(ALIGN_CENTER)
        out.writeText("-".repeat(safeWidth))
        if (footerLine1.isNotBlank()) {
            for (line in wordWrap(footerLine1, safeWidth)) out.writeText(line)
        }
        if (footerLine2.isNotBlank()) {
            for (line in wordWrap(footerLine2, safeWidth)) out.writeText(line)
        }
        if (footerLine3.isNotBlank()) {
            out.writeText("")
            for (line in wordWrap(footerLine3, safeWidth)) out.writeText(line)
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
        out.write(
            byteArrayOf(
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            )
        )
        out.write(data)
        return out.toByteArray()
    }

    // === HELPERS ===

    private fun ByteArrayOutputStream.writeText(text: String) {
        // Use CP437-safe ASCII only — no multi-byte UTF-8
        val safeText = text
            .replace("\u2022", "*")  // bullet
            .replace("\u2013", "-")  // en dash
            .replace("\u2014", "-")  // em dash
            .replace("\u2018", "'")  // left single quote
            .replace("\u2019", "'")  // right single quote
            .replace("\u201C", "\"") // left double quote
            .replace("\u201D", "\"") // right double quote
            .replace("\u2026", "...") // ellipsis
            .replace("\u00B7", "-")  // middle dot
            .replace("\u20B9", "Rs.") // rupee symbol
            .replace("\u2665", "<3") // heart
            .replace("\u2764", "<3") // heavy heart
        write(safeText.toByteArray(Charsets.US_ASCII))
        write(byteArrayOf(LF))
    }

    private fun formatTableRow(item: String, qty: String, price: String, lineWidth: Int): String {
        val safeWidth = lineWidth.coerceAtLeast(24)
        val qtyCol = if (safeWidth <= COLUMNS_58MM) 4 else 6
        val priceCol = if (safeWidth <= COLUMNS_58MM) 8 else 10
        val itemCol = (safeWidth - qtyCol - priceCol).coerceAtLeast(4)
        return truncate(item, itemCol).padEnd(itemCol) +
            qty.take(qtyCol).padStart(qtyCol) +
            price.take(priceCol).padStart(priceCol)
    }

    private fun formatRightAligned(label: String, value: String, lineWidth: Int): String {
        val safeWidth = lineWidth.coerceAtLeast(1)
        val safeValue = if (value.length >= safeWidth) value.take(safeWidth) else value
        val available = (safeWidth - safeValue.length - 1).coerceAtLeast(0)
        if (available == 0) return safeValue.take(safeWidth)
        return truncate(label, available).padEnd(available) + " " + safeValue
    }

    private fun truncate(text: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        if (text.length <= maxLen) return text
        if (maxLen <= 2) return text.take(maxLen)
        return text.take(maxLen - 2) + ".."
    }

    private fun wordWrap(text: String, maxWidth: Int): List<String> {
        val safeMax = maxWidth.coerceAtLeast(1)
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.split(" ")
            var currentLine = StringBuilder()
            for (word in words) {
                if (word.length > safeMax) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder()
                    }
                    var idx = 0
                    while (idx < word.length) {
                        val end = minOf(idx + safeMax, word.length)
                        lines.add(word.substring(idx, end))
                        idx = end
                    }
                } else if (currentLine.isEmpty()) {
                    currentLine.append(word)
                } else if (currentLine.length + 1 + word.length > safeMax) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    currentLine.append(" ").append(word)
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        }
        return lines
    }
}
