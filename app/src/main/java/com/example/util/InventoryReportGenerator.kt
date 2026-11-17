package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.entity.InventoryItemEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object InventoryReportGenerator {

    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    /**
     * Generates a clean, branded PDF inventory report with pagination.
     */
    fun generateInventoryPdf(
        context: Context,
        items: List<InventoryItemEntity>,
        stockCounts: Map<Int, Double>,
        staffNotes: String,
        operatorName: String = "Staff"
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 standard width (72 dpi)
        val pageHeight = 842 // A4 standard height
        val margin = 36f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9.5f
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.rgb(180, 83, 9) // Coffee/Amber
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(24, 24, 27)
            textSize = 9.5f
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(228, 228, 231)
            strokeWidth = 1f
        }

        val bgPaint = Paint().apply {
            color = Color.rgb(244, 244, 245)
        }

        val notesBgPaint = Paint().apply {
            color = Color.rgb(254, 243, 199)
        }

        val notesBorderPaint = Paint().apply {
            color = Color.rgb(253, 230, 138)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val notesTextPaint = Paint().apply {
            color = Color.rgb(120, 53, 15)
            textSize = 9f
            isAntiAlias = true
        }

        val grouped = items.filter { it.isActive }
            .groupBy { it.category.trim().uppercase().ifBlank { "GENERAL" } }
            .toSortedMap()

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin + 18f

        fun drawHeader() {
            canvas.drawText("FILTR COFFEE", margin, y, titlePaint)
            y += 16f
            canvas.drawText("MANUAL INVENTORY & STORE CLOSING REPORT", margin, y, subtitlePaint)
            y += 13f
            val dateStr = dateFormat.format(Date())
            canvas.drawText("Generated: $dateStr  |  Operator: $operatorName  |  Total Items: ${items.size}", margin, y, subtitlePaint)
            y += 10f
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 14f
        }

        drawHeader()

        // Staff Notes Banner if present
        if (staffNotes.isNotBlank()) {
            val noteRect = RectF(margin, y, margin + contentWidth, y + 42f)
            canvas.drawRoundRect(noteRect, 6f, 6f, notesBgPaint)
            canvas.drawRoundRect(noteRect, 6f, 6f, notesBorderPaint)
            canvas.drawText("📝 STAFF CLOSING NOTES / OBSERVATIONS:", margin + 8f, y + 14f, boldTextPaint)
            val displayNote = if (staffNotes.length > 125) staffNotes.take(122) + "..." else staffNotes
            canvas.drawText(displayNote, margin + 8f, y + 30f, notesTextPaint)
            y += 52f
        }

        for ((category, catItems) in grouped) {
            if (y > pageHeight - 80f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 18f
                drawHeader()
            }

            // Category Banner
            val catRect = RectF(margin, y, margin + contentWidth, y + 16f)
            canvas.drawRoundRect(catRect, 4f, 4f, bgPaint)
            canvas.drawText(category, margin + 8f, y + 12f, sectionPaint)
            y += 22f

            // Table Header
            canvas.drawText("ITEM NAME", margin + 8f, y, subtitlePaint)
            canvas.drawText("PACK SIZE", margin + 280f, y, subtitlePaint)
            canvas.drawText("COUNTED STOCK", margin + 400f, y, subtitlePaint)
            y += 5f
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 13f

            for (item in catItems) {
                if (y > pageHeight - 45f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin + 18f
                    drawHeader()
                }

                val count = stockCounts[item.id] ?: item.currentStock.coerceAtLeast(0.0)
                val countStr = if (count % 1.0 == 0.0) "${count.toInt()} ${item.unit}" else "${String.format(Locale.US, "%.2f", count)} ${item.unit}"
                val packInfo = if (item.standardQuantity > 0) "${item.standardQuantity} ${item.unit}" else item.unit

                canvas.drawText(item.name.take(36), margin + 8f, y, textPaint)
                canvas.drawText(packInfo, margin + 280f, y, subtitlePaint)
                canvas.drawText(countStr, margin + 400f, y, boldTextPaint)
                y += 15f
            }
            y += 8f
        }

        // Footer on final page
        y = pageHeight - margin - 8f
        canvas.drawLine(margin, y - 8f, margin + contentWidth, y - 8f, linePaint)
        canvas.drawText("FILTR Coffee POS • Crafted with obsession", margin, y, subtitlePaint)
        canvas.drawText("Page $pageNumber", margin + contentWidth - 36f, y, subtitlePaint)

        pdfDocument.finishPage(page)

        val reportsDir = File(context.cacheDir, "inventory_reports").apply { if (!exists()) mkdirs() }
        val outputFile = File(reportsDir, "FILTR_Inventory_${fileDateFormat.format(Date())}.pdf")
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return outputFile
    }

    /**
     * Generates a high-resolution PNG image of the inventory report for quick image sharing on WhatsApp.
     */
    fun generateInventoryImage(
        context: Context,
        items: List<InventoryItemEntity>,
        stockCounts: Map<Int, Double>,
        staffNotes: String,
        operatorName: String = "Staff"
    ): File {
        val width = 720
        val activeItems = items.filter { it.isActive }
        val grouped = activeItems.groupBy { it.category.trim().uppercase().ifBlank { "GENERAL" } }.toSortedMap()

        var totalRows = 0
        grouped.forEach { (_, catItems) -> totalRows += 2 + catItems.size }
        val notesHeight = if (staffNotes.isNotBlank()) 90 else 0
        val height = (240 + notesHeight + (totalRows * 36) + 80).coerceAtLeast(600)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = 32f
        val contentWidth = width - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.rgb(9, 9, 11)
            textSize = 30f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(113, 113, 122)
            textSize = 17f
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.rgb(180, 83, 9)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(24, 24, 27)
            textSize = 18f
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 19f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(228, 228, 231)
            strokeWidth = 2f
        }

        val bgPaint = Paint().apply {
            color = Color.rgb(244, 244, 245)
        }

        val notesBgPaint = Paint().apply {
            color = Color.rgb(254, 243, 199)
        }

        var y = margin + 30f

        // Header
        canvas.drawText("FILTR COFFEE", margin, y, titlePaint)
        y += 30f
        canvas.drawText("MANUAL INVENTORY & STORE CLOSING", margin, y, subtitlePaint)
        y += 26f
        val dateStr = dateFormat.format(Date())
        canvas.drawText("Date: $dateStr  |  Operator: $operatorName", margin, y, subtitlePaint)
        y += 18f
        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
        y += 22f

        // Notes
        if (staffNotes.isNotBlank()) {
            val noteRect = RectF(margin, y, margin + contentWidth, y + 68f)
            canvas.drawRoundRect(noteRect, 10f, 10f, notesBgPaint)
            val noteTitlePaint = Paint().apply {
                color = Color.rgb(146, 64, 14)
                textSize = 17f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val noteContentPaint = Paint().apply {
                color = Color.rgb(120, 53, 15)
                textSize = 16f
                isAntiAlias = true
            }
            canvas.drawText("📝 STAFF CLOSING NOTES:", margin + 12f, y + 25f, noteTitlePaint)
            val cleanNotes = if (staffNotes.length > 80) staffNotes.take(77) + "..." else staffNotes
            canvas.drawText(cleanNotes, margin + 12f, y + 52f, noteContentPaint)
            y += 86f
        }

        // Table
        for ((category, catItems) in grouped) {
            val catRect = RectF(margin, y, margin + contentWidth, y + 30f)
            canvas.drawRoundRect(catRect, 8f, 8f, bgPaint)
            canvas.drawText(category, margin + 12f, y + 22f, sectionPaint)
            y += 40f

            for (item in catItems) {
                val count = stockCounts[item.id] ?: item.currentStock.coerceAtLeast(0.0)
                val countStr = if (count % 1.0 == 0.0) "${count.toInt()} ${item.unit}" else "${String.format(Locale.US, "%.2f", count)} ${item.unit}"

                canvas.drawText(item.name.take(30), margin + 12f, y + 16f, textPaint)
                canvas.drawText(countStr, margin + contentWidth - 140f, y + 16f, boldTextPaint)
                y += 32f
                canvas.drawLine(margin + 8f, y, margin + contentWidth - 8f, y, linePaint)
                y += 6f
            }
            y += 10f
        }

        y += 12f
        canvas.drawText("Crafted with obsession • FILTR Coffee", margin, y, subtitlePaint)

        val reportsDir = File(context.cacheDir, "inventory_reports").apply { if (!exists()) mkdirs() }
        val outputFile = File(reportsDir, "FILTR_Inventory_${fileDateFormat.format(Date())}.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return outputFile
    }

    /**
     * Builds a clean text summary formatted for WhatsApp messages.
     */
    fun generateInventoryText(
        items: List<InventoryItemEntity>,
        stockCounts: Map<Int, Double>,
        staffNotes: String,
        operatorName: String = "Staff"
    ): String {
        val sb = StringBuilder()
        val dateStr = dateFormat.format(Date())
        sb.append("📋 *FILTR COFFEE - INVENTORY REPORT*\n")
        sb.append("📅 Date: $dateStr\n")
        sb.append("👤 Operator: $operatorName\n")
        if (staffNotes.isNotBlank()) {
            sb.append("\n📝 *Closing Remarks:*\n$staffNotes\n")
        }
        sb.append("\n────────────────────\n")

        val grouped = items.filter { it.isActive }
            .groupBy { it.category.trim().uppercase().ifBlank { "GENERAL" } }
            .toSortedMap()

        for ((category, catItems) in grouped) {
            sb.append("\n🔹 *${category}*\n")
            for (item in catItems) {
                val count = stockCounts[item.id] ?: item.currentStock.coerceAtLeast(0.0)
                val countStr = if (count % 1.0 == 0.0) "${count.toInt()}" else String.format(Locale.US, "%.1f", count)
                sb.append("• ${item.name}: *${countStr} ${item.unit}*\n")
            }
        }
        sb.append("\n────────────────────\n")
        sb.append("Total Items Counted: ${items.size}\n")
        return sb.toString()
    }

    /**
     * Shares a file directly via WhatsApp (or falls back to system chooser).
     */
    fun shareViaWhatsApp(
        context: Context,
        file: File,
        mimeType: String,
        caption: String
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                putExtra(Intent.EXTRA_SUBJECT, "FILTR Coffee - Closing Inventory Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val waIntent = Intent(shareIntent).apply { setPackage("com.whatsapp") }
            val pm = context.packageManager
            if (waIntent.resolveActivity(pm) != null) {
                context.startActivity(waIntent)
            } else {
                val waBusiness = Intent(shareIntent).apply { setPackage("com.whatsapp.w4b") }
                if (waBusiness.resolveActivity(pm) != null) {
                    context.startActivity(waBusiness)
                } else {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Inventory Report via WhatsApp"))
                }
            }
        } catch (e: Exception) {
            Log.e("InventoryReport", "Share error: ${e.message}", e)
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(fallback, "Share Inventory Report"))
            } catch (ex: Exception) {
                Log.e("InventoryReport", "Fallback share error: ${ex.message}", ex)
            }
        }
    }
}
