package com.example.domain.inventory

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zero-dependency OOXML (.xlsx) workbook generator producing two crisp tabs:
 *  1. "SKU Price History" (horizontal Bill 1..N progression, no blank date gaps, red highlight on >0% hikes, amber on CHECK UNIT)
 *  2. "Bill Ledger" (chronological raw bill reference)
 */
object XlsxWorkbookBuilder {

    fun buildSkuPriceHistoryXlsx(
        matrix: SkuPriceHistoryMatrix,
        ledger: List<BillLedgerRow>,
        includeDateInCells: Boolean = true
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", contentTypesXml())
            writeZipEntry(zos, "_rels/.rels", rootRelsXml())
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeZipEntry(zos, "xl/workbook.xml", workbookXml())
            writeZipEntry(zos, "xl/styles.xml", stylesXml())
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildPriceHistorySheetXml(matrix, includeDateInCells))
            writeZipEntry(zos, "xl/worksheets/sheet2.xml", buildBillLedgerSheetXml(ledger))
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun colLetter(zeroBasedIdx: Int): String {
        var n = zeroBasedIdx
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun xmlEscape(text: String): String {
        return buildString(text.length + 8) {
            for (ch in text) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') append(ch)
                }
            }
        }
    }

    private fun inlineCell(colIdx: Int, rowNum: Int, value: String, styleId: Int): String {
        val ref = "${colLetter(colIdx)}$rowNum"
        return "<c r=\"$ref\" s=\"$styleId\" t=\"inlineStr\"><is><t>${xmlEscape(value)}</t></is></c>"
    }

    private fun numCell(colIdx: Int, rowNum: Int, value: Double, styleId: Int): String {
        val ref = "${colLetter(colIdx)}$rowNum"
        val formatted = if (kotlin.math.abs(value - kotlin.math.round(value)) < 0.0001) {
            kotlin.math.round(value).toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
        return "<c r=\"$ref\" s=\"$styleId\"><v>$formatted</v></c>"
    }

    // Style indices mapped in stylesXml():
    // 0 = normal text
    // 1 = dark bold header
    // 2 = price hike red fill + bold red text
    // 3 = check unit amber fill + bold amber text
    // 4 = normal bold text
    // 5 = green drop text
    private fun buildPriceHistorySheetXml(
        matrix: SkuPriceHistoryMatrix,
        includeDateInCells: Boolean
    ): String {
        val totalCols = (4 + matrix.maxBillColumns).coerceAtLeast(5)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane xSplit=\"1\" ySplit=\"1\" topLeftCell=\"B2\" activePane=\"bottomRight\" state=\"frozen\"/></sheetView></sheetViews>")
        sb.append("<cols>")
        sb.append("<col min=\"1\" max=\"1\" width=\"30\" customWidth=\"1\"/>")
        sb.append("<col min=\"2\" max=\"2\" width=\"16\" customWidth=\"1\"/>")
        sb.append("<col min=\"3\" max=\"4\" width=\"18\" customWidth=\"1\"/>")
        if (totalCols > 4) {
            sb.append("<col min=\"5\" max=\"$totalCols\" width=\"16\" customWidth=\"1\"/>")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        // Header Row (Row 1)
        sb.append("<row r=\"1\">")
        val headers = mutableListOf(
            "SKU Name",
            "Unit / Weight",
            "Latest Price (INR)",
            "Price Change vs First (%)"
        )
        for (i in 1..matrix.maxBillColumns.coerceAtLeast(1)) {
            headers.add("Bill $i")
        }
        headers.forEachIndexed { cIdx, h ->
            sb.append(inlineCell(cIdx, 1, h, 1))
        }
        sb.append("</row>")

        matrix.rows.forEachIndexed { idx, row ->
            val rNum = idx + 2
            sb.append("<row r=\"$rNum\">")
            sb.append(inlineCell(0, rNum, row.skuName, 4))
            sb.append(inlineCell(1, rNum, row.unitWeightLabel, 0))
            sb.append(numCell(2, rNum, row.latestPrice, 4))

            val pct = row.priceChangeVsFirstPct
            val pctStyle = when {
                pct != null && pct > 0.0 -> 2
                pct != null && pct < 0.0 -> 5
                else -> 0
            }
            val pctText = if (pct != null) {
                val sign = if (pct > 0.0) "+" else ""
                "$sign${String.format(Locale.US, "%.1f", pct)}%"
            } else {
                "0.0%"
            }
            sb.append(inlineCell(3, rNum, pctText, pctStyle))

            val colsCount = matrix.maxBillColumns.coerceAtLeast(1)
            for (bIdx in 0 until colsCount) {
                val bill = row.bills.getOrNull(bIdx)
                if (bill == null) {
                    sb.append(inlineCell(4 + bIdx, rNum, "", 0))
                } else if (bill.isUnitAnomaly) {
                    sb.append(inlineCell(4 + bIdx, rNum, bill.formatCell(includeDateInCells), 3))
                } else if (includeDateInCells) {
                    sb.append(inlineCell(4 + bIdx, rNum, bill.formatCell(true), 0))
                } else {
                    sb.append(numCell(4 + bIdx, rNum, bill.price, 0))
                }
            }
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildBillLedgerSheetXml(ledger: List<BillLedgerRow>): String {
        val headers = listOf(
            "Bill ID",
            "Date",
            "Supplier",
            "SKU Name (Raw)",
            "Matched SKU",
            "Quantity",
            "Unit",
            "Unit Price (INR)",
            "Subtotal (INR)",
            "Normalized Rate (INR)",
            "Status",
            "Notes"
        )
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        sb.append("<cols>")
        sb.append("<col min=\"1\" max=\"1\" width=\"18\" customWidth=\"1\"/>")
        sb.append("<col min=\"2\" max=\"2\" width=\"20\" customWidth=\"1\"/>")
        sb.append("<col min=\"3\" max=\"5\" width=\"24\" customWidth=\"1\"/>")
        sb.append("<col min=\"6\" max=\"11\" width=\"15\" customWidth=\"1\"/>")
        sb.append("<col min=\"12\" max=\"12\" width=\"30\" customWidth=\"1\"/>")
        sb.append("</cols>")
        sb.append("<sheetData>")

        sb.append("<row r=\"1\">")
        headers.forEachIndexed { idx, h -> sb.append(inlineCell(idx, 1, h, 1)) }
        sb.append("</row>")

        ledger.forEachIndexed { idx, row ->
            val rNum = idx + 2
            val statusStyle = if (row.statusLabel == "CHECK UNIT") 3 else 0
            sb.append("<row r=\"$rNum\">")
            sb.append(inlineCell(0, rNum, row.orderId, 0))
            sb.append(inlineCell(1, rNum, row.dateFormatted, 0))
            sb.append(inlineCell(2, rNum, row.supplierName, 0))
            sb.append(inlineCell(3, rNum, row.rawItemName, 0))
            sb.append(inlineCell(4, rNum, row.matchedSkuName, 4))
            sb.append(numCell(5, rNum, row.quantity, 0))
            sb.append(inlineCell(6, rNum, row.unit, 0))
            sb.append(numCell(7, rNum, row.unitPrice, 0))
            sb.append(numCell(8, rNum, row.subtotal, 0))
            sb.append(numCell(9, rNum, row.normalizedPrice, 4))
            sb.append(inlineCell(10, rNum, row.statusLabel, statusStyle))
            sb.append(inlineCell(11, rNum, row.notes, 0))
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun workbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="SKU Price History" sheetId="1" r:id="rId1"/>
    <sheet name="Bill Ledger" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="5">
    <font><sz val="11"/><color rgb="FF18181B"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFB91C1C"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFB45309"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF15803D"/><name val="Calibri"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF09090B"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEE2E2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEF3C7"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"><font><b/></font></xf>
    <xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
}
