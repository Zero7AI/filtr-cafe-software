package com.example.domain.inventory

import com.example.data.entity.InventoryItemEntity
import com.example.ui.InventoryOrderItemInput
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ParsedRawMaterialReceipt(
    val supplierName: String,
    val notes: String,
    val items: List<InventoryOrderItemInput>,
    val sourceLabel: String = "",
    val extractionSummary: String = "",
    val parseError: String? = null
)

object RawMaterialReceiptParser {

    val SUPPORTED_UNITS: List<String> = listOf("kg", "L", "g", "ml", "pcs", "pack")

    fun normalizeUnit(rawUnit: String): String {
        return when (rawUnit.trim().lowercase(Locale.US)) {
            "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms" -> "kg"
            "l", "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres" -> "L"
            "g", "gm", "gms", "gram", "grams" -> "g"
            "ml", "mls", "milliliter", "milliliters", "millilitre", "millilitres" -> "ml"
            "pc", "pcs", "piece", "pieces", "unit", "units", "btl", "bottle", "bottles", "jar", "jars", "cup", "cups" -> "pcs"
            "pack", "packs", "pkt", "pkts", "packet", "packets", "pouch", "pouches", "box", "boxes", "bag", "bags", "tin", "tins", "can", "cans", "case", "carton", "crate" -> "pack"
            else -> "pcs"
        }
    }

    fun matchCatalogItem(
        rawName: String,
        catalog: List<InventoryItemEntity>
    ): InventoryItemEntity? {
        val clean = rawName.trim()
        if (clean.isEmpty() || catalog.isEmpty()) return null

        catalog.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }

        val cleanLower = clean.lowercase(Locale.US)
        val cleanTokens = cleanLower
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

        return catalog
            .mapNotNull { candidate ->
                val candLower = candidate.name.trim().lowercase(Locale.US)
                val score = when {
                    candLower.contains(cleanLower) || cleanLower.contains(candLower) -> 100
                    cleanTokens.isNotEmpty() -> {
                        val candTokens = candLower.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
                        val overlap = cleanTokens.intersect(candTokens).size
                        if (overlap > 0 && overlap * 2 >= minOf(cleanTokens.size, candTokens.size).coerceAtLeast(1)) {
                            50 + overlap * 10
                        } else {
                            0
                        }
                    }
                    else -> 0
                }
                if (score > 0) candidate to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    fun parseGeminiReceiptJson(
        rawJsonText: String,
        catalog: List<InventoryItemEntity>,
        sourceLabel: String
    ): Result<ParsedRawMaterialReceipt> {
        return try {
            val cleanedJson = stripMarkdownCodeFences(rawJsonText)
            val obj = JSONObject(cleanedJson)
            val supplierName = obj.optString("supplierName", "")
                .takeIf { it != "null" }
                .orEmpty()
                .trim()
                .ifBlank { "WhatsApp Supplier" }

            val invoiceNumber = obj.optString("invoiceNumber", "").takeIf { it != "null" }.orEmpty().trim()
            val invoiceDate = obj.optString("invoiceDate", "").takeIf { it != "null" }.orEmpty().trim()
            val extraNotes = obj.optString("notes", "").takeIf { it != "null" }.orEmpty().trim()

            val itemsArr = obj.optJSONArray("items") ?: JSONArray()
            val parsedItems = mutableListOf<InventoryOrderItemInput>()

            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(i) ?: continue
                val rawName = itemObj.optString("name", "")
                    .ifBlank { itemObj.optString("itemName", "") }
                    .trim()
                if (rawName.isBlank()) continue

                val qty = itemObj.optDouble("quantity", 0.0)
                    .let { if (it.isNaN() || it <= 0.0) 1.0 else it }

                val matchedCatalog = matchCatalogItem(rawName, catalog)
                val rawUnit = itemObj.optString("unit", "").trim()
                val resolvedUnit = when {
                    rawUnit.isNotBlank() -> normalizeUnit(rawUnit)
                    matchedCatalog != null && matchedCatalog.unit.isNotBlank() -> normalizeUnit(matchedCatalog.unit)
                    else -> "pcs"
                }

                val explicitRate = itemObj.optDouble("unitPrice", Double.NaN)
                val lineTotal = itemObj.optDouble("totalPrice", Double.NaN)
                val unitPrice = when {
                    !explicitRate.isNaN() && explicitRate >= 0.0 && explicitRate > 0.0 -> explicitRate
                    !lineTotal.isNaN() && lineTotal > 0.0 && qty > 0.0 -> lineTotal / qty
                    !explicitRate.isNaN() && explicitRate == 0.0 -> 0.0
                    matchedCatalog != null && matchedCatalog.defaultPrice > 0.0 -> matchedCatalog.defaultPrice
                    else -> 0.0
                }

                parsedItems.add(
                    InventoryOrderItemInput(
                        itemName = matchedCatalog?.name ?: rawName,
                        quantity = qty,
                        unit = resolvedUnit,
                        unitPrice = kotlin.math.round(unitPrice * 100.0) / 100.0
                    )
                )
            }

            if (parsedItems.isEmpty()) {
                return Result.failure(IllegalArgumentException("No raw material line items found in receipt"))
            }

            val noteParts = buildList {
                if (invoiceNumber.isNotBlank()) add("Invoice #$invoiceNumber")
                if (invoiceDate.isNotBlank()) add("Date: $invoiceDate")
                if (sourceLabel.isNotBlank()) add("File: $sourceLabel")
                if (extraNotes.isNotBlank()) add(extraNotes)
            }

            val total = parsedItems.sumOf { it.quantity * it.unitPrice }
            Result.success(
                ParsedRawMaterialReceipt(
                    supplierName = supplierName,
                    notes = noteParts.joinToString(" • "),
                    items = parsedItems,
                    sourceLabel = sourceLabel,
                    extractionSummary = "Extracted ${parsedItems.size} items (₹${String.format(Locale.US, "%.0f", total)}) from $sourceLabel"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deterministic offline fallback for plain-text supplier bills shared via WhatsApp.
     */
    fun parsePlainTextReceipt(
        rawText: String,
        catalog: List<InventoryItemEntity>,
        sourceLabel: String = "Shared Text"
    ): Result<ParsedRawMaterialReceipt> {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return Result.failure(IllegalArgumentException("Shared receipt text is empty"))
        }

        var supplierName = ""
        val items = mutableListOf<InventoryOrderItemInput>()

        // Match lines like "Milk 10 L @ 65", "Coffee Beans - 2 kg - 1800", or "Oat Milk - 4 pack - 920"
        val itemPattern = Regex(
            "^([A-Za-z][A-Za-z0-9 &/().-]{1,50}?)\\s*[-:]?\\s*(\\d+(?:\\.\\d+)?)\\s*(kgs?|kilograms?|g|gms?|grams?|ltrs?|litres?|liters?|l|ml|pcs?|pieces?|packs?|pkts?|packets?|boxes?|bags?)?(?:\\s*(?:-|–|@|at|rs\\.?|₹|inr)?\\s*(\\d+(?:\\.\\d+)?))?$",
            RegexOption.IGNORE_CASE
        )

        for ((idx, line) in lines.withIndex()) {
            val cleanedLine = line
                .replace("₹", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val match = itemPattern.matchEntire(cleanedLine)
            if (match != null) {
                val rawItemName = match.groupValues[1].trim()
                val qty = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val rawUnit = match.groupValues[3].trim()
                val rawPrice = match.groupValues[4].toDoubleOrNull() ?: 0.0

                if (rawItemName.isNotBlank() && qty > 0.0) {
                    val matched = matchCatalogItem(rawItemName, catalog)
                    val unit = when {
                        rawUnit.isNotBlank() -> normalizeUnit(rawUnit)
                        matched != null -> normalizeUnit(matched.unit)
                        else -> "pcs"
                    }
                    val rate = when {
                        line.contains("@") && rawPrice >= 0.0 -> rawPrice
                        rawPrice > 0.0 -> kotlin.math.round((rawPrice / qty) * 100.0) / 100.0
                        matched != null && matched.defaultPrice > 0.0 -> matched.defaultPrice
                        else -> 0.0
                    }
                    items.add(
                        InventoryOrderItemInput(
                            itemName = matched?.name ?: rawItemName,
                            quantity = qty,
                            unit = unit,
                            unitPrice = rate
                        )
                    )
                    continue
                }
            }

            if (idx == 0 && supplierName.isEmpty() && line.none { it.isDigit() }) {
                supplierName = line.take(50)
            }
        }

        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Could not extract line items from text"))
        }

        return Result.success(
            ParsedRawMaterialReceipt(
                supplierName = supplierName.ifBlank { "WhatsApp Supplier" },
                notes = "Imported from $sourceLabel",
                items = items,
                sourceLabel = sourceLabel,
                extractionSummary = "Extracted ${items.size} items from $sourceLabel"
            )
        )
    }

    private fun stripMarkdownCodeFences(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n').trim()
            if (text.endsWith("```")) {
                text = text.substringBeforeLast("```").trim()
            }
        }
        return text
    }
}
