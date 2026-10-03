package com.example.voice.engine

import com.example.voice.audio.VoiceModalityProfile
import com.example.voice.model.VoiceOrderLineItem
import com.example.voice.model.VoiceOrderState
import com.example.voice.model.VoiceSelectedModifier
import com.example.voice.model.VoiceStateMachineEnum
import java.util.UUID

class LocalOrderEngine {

    private var lastOrderModality: VoiceModalityProfile? = null

    private val wordToNum: Map<String, Int> = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "single" to 1,
        "two" to 2, "couple" to 2, "pair" to 2, "double" to 2,
        "three" to 3, "triple" to 3,
        "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        // Hindi / Hinglish numerals
        "ek" to 1, "ik" to 1, "aik" to 1, "एक" to 1,
        "do" to 2, "dono" to 2, "दो" to 2,
        "teen" to 3, "tin" to 3, "तीन" to 3,
        "chaar" to 4, "char" to 4, "चार" to 4,
        "paanch" to 5, "panch" to 5, "punch" to 5, "पांच" to 5,
        "chhe" to 6, "che" to 6, "छह" to 6,
        "saat" to 7, "sat" to 7, "सात" to 7,
        "aath" to 8, "ath" to 8, "आठ" to 8,
        "nau" to 9, "नौ" to 9,
        "das" to 10, "दस" to 10
    )

    private val removalKeywords = listOf(
        "remove", "hata do", "hatao", "hata dena", "hata de", "nikal do", "nikal dena", "nikalo",
        "cancel", "minus", "delete", "kam kar do", "kam kardo", "mat daalna", "mat dalo", "mat do",
        "nahi chahiye", "nahi mangta", "cancel karo", "cancel kardo", "last wala hata do", "drop"
    )

    private val explicitAdditiveKeywords = listOf(
        "add", "more", "another", "plus", "also", "aur", "ek aur", "do aur", "extra"
    )

    private val resetKeywords = listOf(
        "clear", "reset", "naya order", "cancel all", "sab hata do", "pura hata do",
        "sab cancel", "cancel order", "sab nikal do", "start over", "khatam karo", "restart"
    )

    private val confirmKeywords = listOf(
        "confirm", "done", "bas itna hi", "itna hi", "bill bana do", "bill banao",
        "order done", "place order", "checkout", "ho gaya", "theek hai", "thik hai",
        "bas", "pack kar do", "pack kardo", "order confirm"
    )

    fun resetSession() {
        lastOrderModality = null
    }

    private data class ExtractedItemMatch(
        val item: CatalogItem,
        val start: Int,
        val end: Int,
        val matchedAlias: String
    )

    fun processUtterance(
        rawText: String,
        currentState: VoiceOrderState,
        modality: VoiceModalityProfile? = null
    ): VoiceOrderState {
        val text = rawText.lowercase().trim()
        if (text.isBlank()) return currentState

        // 1. Check Global Reset Commands
        if (resetKeywords.any { text.contains(it) }) {
            lastOrderModality = null
            return VoiceOrderState(
                sessionId = UUID.randomUUID().toString(),
                state = VoiceStateMachineEnum.IDLE,
                items = emptyList(),
                subtotal = 0.0,
                tax = 0.0,
                total = 0.0,
                lastTranscript = rawText,
                statusMessage = "Order reset. Ready for next order."
            )
        }

        // 2. Normalize Hinglish verb phrases and conversational sentence openers
        val cleaned = normalizeHinglish(text)

        // 3. Extract all non-overlapping menu items in sequence
        val matches = extractAllMenuItems(cleaned)

        // 3b. Extract order notes & modifiers (parking, vehicle plate, dietary notes, packaging)
        val extractedNotes = extractOrderNotes(rawText, currentState.orderNotes)

        // If no menu items matched, check for standalone confirmation, removal, quantity update, or notes
        if (matches.isEmpty()) {
            // Standalone notes clear: user says "remove notes", "clear notes", "notes hata do", "hata do notes"
            val notesClearKeywords = listOf("remove notes", "clear notes", "notes hata do", "hata do notes", "delete notes", "reset notes")
            if (notesClearKeywords.any { cleaned.contains(it) || text.lowercase().contains(it) }) {
                return currentState.copy(
                    lastTranscript = rawText,
                    orderNotes = null,
                    statusMessage = "Cleared order notes"
                )
            }

            if (confirmKeywords.any { cleaned.contains(it) }) {
                return currentState.copy(
                    state = VoiceStateMachineEnum.CONFIRMED,
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Order confirmed! Ready for billing."
                )
            }

            // Standalone removal: user says "hata do", "sandwich hata do", "fries hata do", "cancel", "remove"
            val isStandaloneRemoval = removalKeywords.any { cleaned.contains(it) }
            if (isStandaloneRemoval && currentState.items.isNotEmpty()) {
                val updatedItems = currentState.items.toMutableList()

                // Check if user specifically named a category/item word present in cart (e.g. "sandwich hata do")
                val categoryWords = listOf("sandwich", "fries", "frappe", "coffee", "tea", "burger", "pizza", "wrap")
                val mentionedWord = categoryWords.firstOrNull { cleaned.contains(it) }
                val targetIdx = if (mentionedWord != null) {
                    val found = updatedItems.indexOfLast { it.name.lowercase().contains(mentionedWord) }
                    if (found >= 0) found else updatedItems.size - 1
                } else {
                    updatedItems.size - 1
                }

                val removedItem = updatedItems.removeAt(targetIdx)
                val subtotal = updatedItems.sumOf { it.itemTotal }
                val tax = 0.0 // FILTR: Zero GST in Voice Order
                val grandTotal = subtotal
                val newState = if (updatedItems.isEmpty()) VoiceStateMachineEnum.IDLE else VoiceStateMachineEnum.ORDER_BUILDING
                return currentState.copy(
                    state = newState,
                    items = updatedItems,
                    subtotal = subtotal,
                    tax = tax,
                    total = grandTotal,
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Removed ${removedItem.name}"
                )
            }

            // Standalone quantity update on existing cart items:
            // e.g. "do", "two", "teen", "three", "ek aur", "one more", "double kar do", "do kar do"
            val standaloneQty = extractStandaloneQuantity(cleaned)
            if (standaloneQty != null && currentState.items.isNotEmpty()) {
                val updatedItems = currentState.items.toMutableList()
                val lastIdx = updatedItems.size - 1
                val lastItem = updatedItems[lastIdx]
                val newQty = if (standaloneQty.isAdditive) {
                    lastItem.quantity + standaloneQty.quantity
                } else {
                    standaloneQty.quantity
                }
                val lineTotal = newQty * lastItem.unitPrice
                updatedItems[lastIdx] = lastItem.copy(quantity = newQty, itemTotal = lineTotal)

                val subtotal = updatedItems.sumOf { it.itemTotal }
                val tax = 0.0 // FILTR: Zero GST in Voice Order
                val grandTotal = subtotal

                return currentState.copy(
                    state = VoiceStateMachineEnum.ORDER_BUILDING,
                    items = updatedItems,
                    subtotal = subtotal,
                    tax = tax,
                    total = grandTotal,
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Updated ${lastItem.name} to ${newQty}×"
                )
            }

            // If notes or vehicle details were captured in a standalone remark
            if (extractedNotes != currentState.orderNotes) {
                return currentState.copy(
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Notes added: $extractedNotes"
                )
            }

            return currentState.copy(
                lastTranscript = rawText,
                statusMessage = currentState.statusMessage ?: "Listening..."
            )
        }

        val prevModality = lastOrderModality
        val isDifferentSpeaker = modality?.isDifferentSpeakerFrom(prevModality) ?: false
        val modalityTag = modality?.shortSummary(prevModality)

        // Check if entire utterance starts with an additive conjunction
        val utteranceHasLeadingAdditive = text.startsWith("aur ") ||
            text.startsWith("or ") ||
            text.startsWith("add ") ||
            text.contains("one more") ||
            text.contains("another")

        val updatedItems = currentState.items.toMutableList()
        val actionsFeedback = mutableListOf<String>()
        var anyItemMatched = false

        // Detect if utterance is a multi-item Staff/Customer repeat-back confirmation
        val allItemsAlreadyInCart = matches.size >= 2 && matches.all { match ->
            currentState.items.any { it.name == match.item.name }
        }
        val isPureConfirmationTurn = allItemsAlreadyInCart && !utteranceHasLeadingAdditive

        for ((i, match) in matches.withIndex()) {
            val prefixStart = if (i > 0) matches[i - 1].end else 0
            val prefix = cleaned.substring(prefixStart, match.start).trim()

            // Intervening tokens between Item A and Item B belong strictly to Item B's prefix.
            val suffix = if (i + 1 < matches.size) {
                val nextStart = matches[i + 1].start
                val intervening = cleaned.substring(match.end, nextStart).trim()
                val conjMatch = Regex("\\b(aur|and|plus|or|with)\\b").find(intervening)
                if (conjMatch != null) {
                    intervening.substring(0, conjMatch.range.first).trim()
                } else {
                    "" // Belongs to next item's prefix
                }
            } else {
                cleaned.substring(match.end).trim()
            }

            val isRemoval = removalKeywords.any { prefix.contains(it) || suffix.contains(it) }
            val rawQuantity = extractQuantityFromContext(prefix, suffix, isFirstItem = (i == 0))
            val isExplicitlyAdditive = utteranceHasLeadingAdditive ||
                explicitAdditiveKeywords.any { kw -> Regex("\\b$kw\\b").containsMatchIn(prefix) }
            val modifiers = extractModifiers("$prefix $suffix")

            val matchedItem = match.item
            anyItemMatched = true

            val existingIndex = updatedItems.indexOfFirst {
                it.name == matchedItem.name && (isRemoval || it.modifiers == modifiers)
            }
            val existing = if (existingIndex >= 0) updatedItems[existingIndex] else null

            // In a pure confirmation turn without explicit additive words, keep existing cart quantity
            val hasExplicitPrefixNumeral = Regex("\\b(\\d+|ek|do|teen|char|chaar|punch|paanch|one|two|three|four|five)\\b").containsMatchIn(prefix)
            val quantity = if (isPureConfirmationTurn && existing != null && !hasExplicitPrefixNumeral) {
                existing.quantity
            } else {
                rawQuantity
            }

            if (isRemoval) {
                if (existingIndex >= 0 && existing != null) {
                    val newQty = existing.quantity - quantity
                    if (newQty <= 0) {
                        updatedItems.removeAt(existingIndex)
                        actionsFeedback.add("Removed ${matchedItem.name}")
                    } else {
                        val lineTotal = newQty * existing.unitPrice
                        updatedItems[existingIndex] = existing.copy(quantity = newQty, itemTotal = lineTotal)
                        actionsFeedback.add("Updated ${matchedItem.name} to ${newQty}×")
                    }
                } else {
                    actionsFeedback.add("${matchedItem.name} was not in cart")
                }
            } else {
                if (existingIndex >= 0 && existing != null) {
                    if (isExplicitlyAdditive) {
                        // Explicit request to add more ("add one...", "one more...", "aur ek...")
                        val newQty = existing.quantity + quantity
                        val lineTotal = newQty * existing.unitPrice
                        updatedItems[existingIndex] = existing.copy(quantity = newQty, itemTotal = lineTotal)
                        actionsFeedback.add("Added +$quantity ${matchedItem.name} (${newQty}×)")
                    } else if (quantity == existing.quantity) {
                        // Staff Repeat-Back Confirmation (or Customer repeating same item & quantity) -> IDEMPOTENT (No double-add)
                        actionsFeedback.add("Confirmed ${existing.quantity}× ${matchedItem.name}")
                    } else {
                        // Different target quantity stated without 'more' -> Reconcile/SET to target quantity
                        val lineTotal = quantity * existing.unitPrice
                        updatedItems[existingIndex] = existing.copy(quantity = quantity, itemTotal = lineTotal)
                        actionsFeedback.add("Synced ${matchedItem.name} to ${quantity}×")
                    }
                } else {
                    // Brand-new item added to cart
                    val lineTotal = quantity * matchedItem.price
                    val newItem = VoiceOrderLineItem(
                        lineId = UUID.randomUUID().toString(),
                        sku = "SKU_${matchedItem.id}",
                        name = matchedItem.name,
                        quantity = quantity,
                        unitPrice = matchedItem.price,
                        modifiers = modifiers,
                        itemTotal = lineTotal
                    )
                    updatedItems.add(newItem)
                    actionsFeedback.add("Added ${quantity}× ${matchedItem.name}")
                }
            }
        }

        if (anyItemMatched && modality != null && modality.isValid) {
            lastOrderModality = modality
        }

        val subtotal = updatedItems.sumOf { it.itemTotal }
        val tax = 0.0 // FILTR: Zero GST in Voice Order
        val grandTotal = subtotal

        val isUtteranceConfirmed = confirmKeywords.any { cleaned.contains(it) }
        val newState = when {
            isUtteranceConfirmed -> VoiceStateMachineEnum.CONFIRMED
            updatedItems.isNotEmpty() -> VoiceStateMachineEnum.ORDER_BUILDING
            else -> VoiceStateMachineEnum.IDLE
        }

        val feedbackMsg = if (actionsFeedback.isNotEmpty()) {
            actionsFeedback.joinToString("; ")
        } else {
            currentState.statusMessage ?: "Listening..."
        }

        return currentState.copy(
            state = newState,
            items = updatedItems,
            subtotal = subtotal,
            tax = tax,
            total = grandTotal,
            lastTranscript = rawText,
            statusMessage = feedbackMsg,
            orderNotes = extractedNotes
        )
    }

    private fun normalizeHinglish(raw: String): String {
        return raw.lowercase().trim()
            .replace(Regex("\\bkar\\s+do\\b"), "kardo")
            .replace(Regex("\\bhata\\s*(do|dena|de|o)?\\b"), "remove")
            .replace(Regex("\\bnikal\\s*(do|dena|de|o)?\\b"), "remove")
            .replace(Regex("\\bmat\\s+(dalo|dalna|do)\\b"), "remove")
            .replace(Regex("\\bnahi\\s+chahiye\\b"), "remove")
            .replace(Regex("\\bcancel\\s+(kar\\s*do|karo)?\\b"), "cancel")
            .replace(Regex("\\bbana\\s+do\\b"), "make")
            .replace(Regex("\\bde\\s+do\\b"), "give")
            .replace(Regex("\\brakh\\s+do\\b"), "keep")
            .replace(Regex("\\bpack\\s+(kar\\s*do|kardo)?\\b"), "pack")
            .replace(Regex("^(acha|achha|theek|thik|aapka|aap|sir|madam|bhaiya|bhai|hello|hey|are|arrey)\\s+"), "")
    }

    private fun extractAllMenuItems(text: String): List<ExtractedItemMatch> {
        val allMatches = mutableListOf<ExtractedItemMatch>()
        for (item in MenuCatalog.items) {
            val candidates = (item.aliases + listOf(item.name.lowercase()))
                .distinct()
                .sortedByDescending { it.length }
            for (alias in candidates) {
                val pattern = Regex("\\b${Regex.escape(alias)}\\b")
                for (m in pattern.findAll(text)) {
                    allMatches.add(ExtractedItemMatch(item, m.range.first, m.range.last + 1, alias))
                }
            }
        }

        // Sort by start position ascending, then length descending
        allMatches.sortWith(compareBy({ it.start }, { -(it.end - it.start) }))

        // Greedy non-overlapping filter
        val nonOverlapping = mutableListOf<ExtractedItemMatch>()
        var lastEnd = 0
        for (m in allMatches) {
            if (m.start >= lastEnd) {
                nonOverlapping.add(m)
                lastEnd = m.end
            }
        }
        return nonOverlapping
    }

    private fun extractQuantityFromContext(prefix: String, suffix: String, isFirstItem: Boolean): Int {
        // 1. Check explicit digits (1..99) in prefix
        val prefixDigitMatch = Regex("\\b(\\d+)\\b").find(prefix)
        if (prefixDigitMatch != null) {
            val num = prefixDigitMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..99) return num
        }

        // 2. Check word numerals in prefix
        val tokens = prefix.split(Regex("\\s+")).filter { it.isNotBlank() }
        for ((idx, token) in tokens.withIndex()) {
            // Disambiguate "char" / "chaar" at sentence start:
            if ((token == "char" || token == "chaar") && isFirstItem && idx == 0) {
                if (idx + 1 < tokens.size && wordToNum.containsKey(tokens[idx + 1])) {
                    continue
                }
            }
            val num = wordToNum[token]
            if (num != null) return num
        }

        // 3. Fallback: check immediate suffix for digits or trailing numerals (e.g. "peri peri fries do")
        val suffixTokens = suffix.split(Regex("\\s+")).take(2)
        for (token in suffixTokens) {
            val num = token.toIntOrNull() ?: wordToNum[token]
            if (num != null && num in 1..99) return num
        }

        return 1
    }

    private fun extractModifiers(segment: String): List<VoiceSelectedModifier> {
        val mods = mutableListOf<VoiceSelectedModifier>()
        if (segment.contains("extra cheese")) {
            mods.add(VoiceSelectedModifier(groupId = "cheese", name = "Extra Cheese", priceDelta = 20.0))
        }
        if (segment.contains("no ice") || segment.contains("bina baraf")) {
            mods.add(VoiceSelectedModifier(groupId = "ice", name = "No Ice", priceDelta = 0.0))
        }
        if (segment.contains("bina pyaz") || segment.contains("no onion")) {
            mods.add(VoiceSelectedModifier(groupId = "onion", name = "No Onion", priceDelta = 0.0))
        }
        if (segment.contains("less sweet") || segment.contains("kam cheeni")) {
            mods.add(VoiceSelectedModifier(groupId = "sweetness", name = "Less Sweet", priceDelta = 0.0))
        }
        return mods
    }

    private data class StandaloneQtyResult(val quantity: Int, val isAdditive: Boolean)

    private fun extractStandaloneQuantity(text: String): StandaloneQtyResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Ignore meta/system conversation phrases that might accidentally contain numerals
        val metaKeywords = listOf("check", "recording", "conversation", "analyze", "listen", "understand", "improvement", "minutes", "minute", "model", "converter", "mortal")
        if (metaKeywords.any { trimmed.contains(it) }) {
            return null
        }

        // Explicit additive: "ek aur", "one more", "aur ek", "do aur", "two more"
        if (Regex("\\b(ek\\s+aur|one\\s+more|aur\\s+ek|plus\\s+one)\\b").containsMatchIn(trimmed)) {
            return StandaloneQtyResult(1, isAdditive = true)
        }
        if (Regex("\\b(do\\s+aur|two\\s+more|aur\\s+do|plus\\s+two)\\b").containsMatchIn(trimmed)) {
            return StandaloneQtyResult(2, isAdditive = true)
        }
        if (Regex("\\b(teen\\s+aur|three\\s+more|aur\\s+teen)\\b").containsMatchIn(trimmed)) {
            return StandaloneQtyResult(3, isAdditive = true)
        }

        // Double / Triple
        if (Regex("\\b(double|do\\s*kardo|do\\s*kar\\s*do|two\\s*times)\\b").containsMatchIn(trimmed)) {
            return StandaloneQtyResult(2, isAdditive = false)
        }
        if (Regex("\\b(triple|teen\\s*kardo|teen\\s*kar\\s*do|three\\s*times)\\b").containsMatchIn(trimmed)) {
            return StandaloneQtyResult(3, isAdditive = false)
        }

        // Direct isolated numeral tokens: "do", "two", "teen", "three", "chaar", "four", "paanch", "five", "2", "3", "4"
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size <= 2 || (tokens.size == 3 && Regex("\\b(kar\\s*do|kardo|piece|only)\\b").containsMatchIn(trimmed))) {
            for (t in tokens) {
                val num = t.toIntOrNull() ?: wordToNum[t]
                if (num != null && num in 1..99) {
                    return StandaloneQtyResult(num, isAdditive = false)
                }
            }
        }

        return null
    }

    fun extractOrderNotes(rawText: String, existingNotes: String?): String? {
        val text = rawText.lowercase().trim()
        val detected = mutableListOf<String>()

        // 1. Vehicle / Parking extraction
        // Standard Indian Plate: DL 3C 1234, HR 26 DK 5678, UP 16 Z 9999, etc.
        val plateRegex = Regex("\\b([a-z]{2}\\s*\\d{1,2}\\s*[a-z]{0,3}\\s*\\d{3,4})\\b")
        val plateMatch = plateRegex.find(text)

        val carNumRegex = Regex("\\b(?:car|gaadi|gadi|vehicle)\\s*(?:no\\.?|number|num)?\\s*([a-z0-9\\s]{2,10})\\b")
        val carNumMatch = carNumRegex.find(text)

        if (plateMatch != null) {
            val plate = plateMatch.groupValues[1].uppercase().replace(Regex("\\s+"), " ")
            detected.add("🚗 $plate")
        } else if (carNumMatch != null) {
            val valStr = carNumMatch.groupValues[1].trim().uppercase()
            if (valStr.any { it.isDigit() }) {
                val cleanNum = Regex("\\d+").find(valStr)?.value ?: valStr
                detected.add("🚗 Car #$cleanNum")
            }
        }

        if (Regex("\\b(parking|parking delivery|car mein|gaadi mein)\\b").containsMatchIn(text)) {
            if (detected.none { it.contains("🚗") }) {
                detected.add("🅿️ Parking Delivery")
            }
        }

        // 2. Packaging / Service
        if (Regex("\\b(pack|pack\\s*kardo|pack\\s*kar\\s*do|parcel|parcel\\s*kardo|parcel\\s*kar\\s*do|takeaway|take\\s*away|to\\s*go)\\b").containsMatchIn(text)) {
            detected.add("📦 Parcel / Takeaway")
        } else if (Regex("\\b(dine\\s*in|yahi\\s*khayenge|table\\s*pe)\\b").containsMatchIn(text)) {
            detected.add("🍽️ Dine-In")
        }

        // 3. Dietary / Customization Notes
        if (Regex("\\b(bina\\s*cheeni|no\\s*sugar|without\\s*sugar|sugar\\s*free|sugarfree)\\b").containsMatchIn(text)) {
            detected.add("🚫 No Sugar")
        } else if (Regex("\\b(kam\\s*cheeni|less\\s*sugar|less\\s*sweet|sweet\\s*kam|kam\\s*meetha)\\b").containsMatchIn(text)) {
            detected.add("☕ Less Sugar")
        }

        if (Regex("\\b(extra\\s*hot|bahut\\s*garam|jyada\\s*garam|kadak)\\b").containsMatchIn(text)) {
            detected.add("🔥 Extra Hot")
        }

        if (Regex("\\b(kam\\s*spicy|less\\s*spicy|no\\s*spicy|kam\\s*mirchi|bina\\s*mirchi)\\b").containsMatchIn(text)) {
            detected.add("🌶️ Mild / Less Spicy")
        } else if (Regex("\\b(extra\\s*spicy|jyada\\s*spicy|bahut\\s*spicy|tez\\s*mirchi)\\b").containsMatchIn(text)) {
            detected.add("🌶️🌶️ Extra Spicy")
        }

        if (Regex("\\b(bina\\s*baraf|no\\s*ice|without\\s*ice)\\b").containsMatchIn(text)) {
            detected.add("🧊 No Ice")
        } else if (Regex("\\b(less\\s*ice|kam\\s*baraf)\\b").containsMatchIn(text)) {
            detected.add("🧊 Less Ice")
        }

        if (Regex("\\b(bina\\s*pyaz|no\\s*onion|jain)\\b").containsMatchIn(text)) {
            detected.add("🧅 No Onion (Jain)")
        } else if (Regex("\\b(extra\\s*onion|jyada\\s*pyaz)\\b").containsMatchIn(text)) {
            detected.add("🧅 Extra Onion")
        }

        if (Regex("\\b(oat\\s*milk)\\b").containsMatchIn(text)) {
            detected.add("🥛 Oat Milk")
        } else if (Regex("\\b(almond\\s*milk)\\b").containsMatchIn(text)) {
            detected.add("🥛 Almond Milk")
        }

        if (detected.isEmpty()) return existingNotes

        // Merge with existing notes (avoid duplicates by prefix emoji/type)
        val allNotes = (existingNotes?.split(" • ") ?: emptyList())
            .toMutableList()
        for (d in detected) {
            val prefix = d.take(2)
            allNotes.removeAll { it.startsWith(prefix) }
            allNotes.add(d)
        }

        return allNotes.joinToString(" • ")
    }
}
