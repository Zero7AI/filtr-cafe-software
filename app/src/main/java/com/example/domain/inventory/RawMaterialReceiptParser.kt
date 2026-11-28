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

    private val NON_INGREDIENT_EQUIPMENT_KEYWORDS = setOf(
        "stand", "dispenser", "cover", "machine", "basket", "repair", "repairs",
        "rent", "electricity", "wifi", "wi-fi", "rapido", "courier", "paint",
        "table", "chair", "shelf", "rack", "stool", "signage", "board", "banner",
        "delivery", "transport", "freight"
    )

    private val SKU_ALIAS_MAP: List<Pair<Regex, String>> = listOf(
        Regex("\\b(tht|tnt|tkt)\\s*(vanilla|vancia)\\b") to "vanilla base ice cream",
        Regex("\\bgallon\\s+vanilla\\b") to "vanilla base ice cream",
        Regex("\\bvanilla\\s*5\\s*(l|lt|ltr)\\b") to "vanilla base ice cream",
        Regex("\\bperi\\s*[-]?\\s*peri\\b") to "peri peri seasoning",
        Regex("\\b(pama|pamac)\\b") to "caramel sauce pamac",
        Regex("\\bdoodh\\b") to "milk",
        Regex("\\bcheeni\\b") to "sugar",
        Regex("\\bmonin\\s+caramel(\\s+syrup)?\\b") to "monin caramel syrup"
    )

    private val PACK_SUFFIX_REGEX = Regex(
        "\\b\\d+(?:\\.\\d+)?\\s*(?:kgs?|kilograms?|gms?|grams?|g|ltrs?|litres?|liters?|lt|l|mls?|ml|pcs?|pieces?|nos?|pkts?|packs?|packets?|btls?|bottles?|tins?|cans?|jars?|boxes?)\\b",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_SINGLE_TOKENS = setOf(
        "water", "ice", "milk", "sugar", "cup", "cups", "sauce", "syrup", "base", "powder", "bottle", "bottles", "monin"
    )
    private val DISTINCT_FLAVOR_TOKENS = setOf(
        "caramel", "hazelnut", "vanilla", "tiramisu", "coconut", "chocolate", "choc",
        "peach", "lemon", "cranberry", "strawberry", "mango", "mint", "irish", "mocha", "toffee", "cinnamon"
    )

    fun normalizeSkuMatchText(raw: String): String {
        var normalized = raw.trim().lowercase(Locale.US)
        for ((pattern, replacement) in SKU_ALIAS_MAP) {
            if (pattern.containsMatchIn(normalized)) {
                normalized = normalized.replace(pattern, replacement)
            }
        }
        normalized = normalized
            .replace(PACK_SUFFIX_REGEX, " ")
            .replace(Regex("[^a-z0-9\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized
    }

    fun isLikelyNonIngredientHardware(rawName: String): Boolean {
        val tokens = rawName.trim().lowercase(Locale.US)
            .split(Regex("[^a-z0-9-]+"))
            .filter { it.isNotBlank() }
        return tokens.any { it in NON_INGREDIENT_EQUIPMENT_KEYWORDS }
    }

    fun matchCatalogItem(
        rawName: String,
        catalog: List<InventoryItemEntity>
    ): InventoryItemEntity? {
        val clean = rawName.trim()
        if (clean.isEmpty() || catalog.isEmpty()) return null

        // Exact case-insensitive match always wins
        catalog.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }

        // Guard: never coerce non-consumable store hardware (e.g. "3 Tier Water Dispenser Stand") into consumables
        if (isLikelyNonIngredientHardware(clean)) return null

        val cleanNorm = normalizeSkuMatchText(clean)
        if (cleanNorm.isEmpty()) return null

        val cleanTokens = cleanNorm.split(" ").filter { it.length >= 2 }.toSet()

        return catalog
            .mapNotNull { candidate ->
                val candRaw = candidate.name.trim()
                val candNorm = normalizeSkuMatchText(candRaw)
                if (candNorm.isEmpty()) return@mapNotNull null

                if (candNorm == cleanNorm) {
                    return@mapNotNull candidate to 150
                }

                val candTokens = candNorm.split(" ").filter { it.length >= 2 }.toSet()
                val meaningfulClean = cleanTokens.filter { it.length >= 3 }.toSet()
                val meaningfulCand = candTokens.filter { it.length >= 3 }.toSet()

                val cleanFlavors = meaningfulClean.filter { it in DISTINCT_FLAVOR_TOKENS }
                val candFlavors = meaningfulCand.filter { it in DISTINCT_FLAVOR_TOKENS }
                if (cleanFlavors.isNotEmpty() && candFlavors.isNotEmpty() && cleanFlavors.none { it in candFlavors }) {
                    return@mapNotNull null
                }
                if (cleanTokens.size == 1 && cleanTokens.first() in GENERIC_SINGLE_TOKENS && cleanTokens.first() != "water" && candTokens.size >= 2) {
                    return@mapNotNull null
                }

                val overlap = meaningfulClean.intersect(meaningfulCand)

                val minLen = minOf(meaningfulClean.size, meaningfulCand.size).coerceAtLeast(1)
                val maxLen = maxOf(meaningfulClean.size, meaningfulCand.size).coerceAtLeast(1)
                val shorterCoverage = overlap.size.toDouble() / minLen.toDouble()
                val jaccard = overlap.size.toDouble() / (meaningfulClean.size + meaningfulCand.size - overlap.size).coerceAtLeast(1).toDouble()

                // Prevent naive substring collision like "water" matching a long unrelated phrase
                val isSingleGenericOverlap = overlap.size == 1 &&
                    overlap.first() in GENERIC_SINGLE_TOKENS &&
                    meaningfulClean.size >= 4

                val score = when {
                    isSingleGenericOverlap -> 0
                    (candNorm.contains(cleanNorm) || cleanNorm.contains(candNorm)) &&
                        (minOf(cleanTokens.size, candTokens.size) >= 2 || maxLen <= 3) -> 120 + overlap.size * 5
                    shorterCoverage >= 0.6 && jaccard >= 0.35 ->
                        (60 + shorterCoverage * 30 + jaccard * 20).toInt()
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
