package com.example.voice.engine

import com.example.voice.audio.VoiceModalityProfile
import com.example.voice.model.VoiceOrderLineItem
import com.example.voice.model.VoiceOrderState
import com.example.voice.model.VoiceSelectedModifier
import com.example.voice.model.VoiceStateMachineEnum
import java.util.UUID

class LocalOrderEngine {

    private var lastOrderModality: VoiceModalityProfile? = null

    private data class ExtractedItemMatch(
        val item: CatalogItem,
        val start: Int,
        val end: Int,
        val matchedAlias: String
    )

    private data class StandaloneQtyResult(val quantity: Int, val isAdditive: Boolean)

    private data class QtyCandidate(val value: Int, val isArticle: Boolean)

    private data class DetectedNote(val category: String, val text: String)

    companion object {
        private val WORD_TO_NUM: Map<String, Int> = mapOf(
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

        private val ARTICLE_WORDS = setOf("a", "an")

        private val UNIT_COUNTER_WORDS = setOf(
            "piece", "pieces", "pcs", "pc", "cup", "cups",
            "plate", "plates", "glass", "glasses", "bottle", "bottles", "nos", "no"
        )

        private val REMOVAL_KEYWORDS = listOf(
            "remove", "hata do", "hatao", "hata dena", "hata de", "nikal do", "nikal dena", "nikalo",
            "cancel", "minus", "delete", "kam kar do", "kam kardo", "mat daalna", "mat dalo", "mat do",
            "nahi chahiye", "nahi mangta", "cancel karo", "cancel kardo", "last wala hata do", "drop"
        )

        private val EXPLICIT_ADDITIVE_KEYWORDS = listOf(
            "add", "more", "another", "plus", "also", "aur", "ek aur", "do aur", "extra"
        )

        private val RESET_KEYWORDS = listOf(
            "clear", "reset", "naya order", "cancel all", "sab hata do", "pura hata do",
            "sab cancel", "cancel order", "sab nikal do", "start over", "khatam karo", "restart"
        )

        private val CONFIRM_KEYWORDS = listOf(
            "confirm", "done", "bas itna hi", "itna hi", "bill bana do", "bill banao",
            "order done", "place order", "checkout", "ho gaya", "theek hai", "thik hai",
            "bas", "pack kar do", "pack kardo", "order confirm"
        )

        private val NOTES_CLEAR_KEYWORDS = listOf(
            "remove notes", "clear notes", "notes hata do", "hata do notes", "delete notes", "reset notes"
        )

        private val META_KEYWORDS = listOf(
            "check", "recording", "conversation", "analyze", "listen", "understand",
            "improvement", "minutes", "minute", "model", "converter", "mortal"
        )

        private fun boundaryRegex(phrase: String): Regex =
            Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(phrase)}(?![\\p{L}\\p{N}_])")

        private val REMOVAL_REGEXES = REMOVAL_KEYWORDS.map(::boundaryRegex)
        private val ADDITIVE_REGEXES = EXPLICIT_ADDITIVE_KEYWORDS.map(::boundaryRegex)
        private val RESET_REGEXES = RESET_KEYWORDS.map(::boundaryRegex)
        private val CONFIRM_REGEXES = CONFIRM_KEYWORDS.map(::boundaryRegex)
        private val NOTES_CLEAR_REGEXES = NOTES_CLEAR_KEYWORDS.map(::boundaryRegex)
        private val META_REGEXES = META_KEYWORDS.map(::boundaryRegex)

        private val CATEGORY_WORD_REGEXES = listOf(
            "sandwich", "fries", "frappe", "coffee", "tea", "burger", "pizza", "wrap"
        ).associateWith(::boundaryRegex)

        // Strip leading/trailing punctuation without stripping Unicode combining marks (e.g. Devanagari matras)
        private val PUNCT_EDGE_REGEX = Regex("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val CONJUNCTION_REGEX = Regex("\\b(?:aur|and|plus|or|with)\\b|[,;.]+")
        private val NON_ITEM_NUM_CONTEXT_REGEX = Regex(
            "\\b(?:table|order|counter|token|bill|receipt|seat|chair|car|gaadi|gadi|vehicle|phone|mobile|contact)\\s*(?:no\\.?|number|num|#)?\\s*\\d+\\b"
        )

        private val NON_ARTICLE_NUMERAL_GROUP = (WORD_TO_NUM.keys - ARTICLE_WORDS)
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        private val EXPLICIT_PREFIX_NUMERAL_REGEX = Regex(
            "(?<![\\p{L}\\p{N}_])(?:\\d+|$NON_ARTICLE_NUMERAL_GROUP)(?![\\p{L}\\p{N}_])"
        )

        private val ADDITIVE_BEFORE_REGEX = Regex(
            "(?<![\\p{L}\\p{N}_])(\\d+|$NON_ARTICLE_NUMERAL_GROUP)\\s+(?:aur|more)(?![\\p{L}\\p{N}_])"
        )
        private val ADDITIVE_AFTER_REGEX = Regex(
            "(?<![\\p{L}\\p{N}_])(?:aur|more|plus|add|another)\\s+(\\d+|$NON_ARTICLE_NUMERAL_GROUP)(?![\\p{L}\\p{N}_])"
        )
        private val ANOTHER_REGEX = boundaryRegex("another")
        private val DOUBLE_REGEX = Regex("\\b(?:double|do\\s*kardo|do\\s*kar\\s*do|two\\s*times)\\b")
        private val TRIPLE_REGEX = Regex("\\b(?:triple|teen\\s*kardo|teen\\s*kar\\s*do|three\\s*times)\\b")
        private val STANDALONE_FILLER_REGEX = Regex("\\b(?:kar\\s*do|kardo|piece|only)\\b")
        private val LEADING_ADDITIVE_REGEX = Regex(
            "^(?:aur|or|add)\\b|\\b(?:another|(?:\\d+|$NON_ARTICLE_NUMERAL_GROUP)\\s+more)\\b"
        )

        // Precompiled Hinglish normalization rules
        private val HINGLISH_KAR_DO = Regex("\\bkar\\s+do\\b")
        private val HINGLISH_HATA = Regex("\\bhata\\s*(do|dena|de|o)?\\b")
        private val HINGLISH_NIKAL = Regex("\\bnikal\\s*(do|dena|de|o)?\\b")
        private val HINGLISH_MAT = Regex("\\bmat\\s+(dalo|dalna|do)\\b")
        private val HINGLISH_NAHI = Regex("\\bnahi\\s+chahiye\\b")
        private val HINGLISH_CANCEL = Regex("\\bcancel\\s+(kar\\s*do|karo)?\\b")
        private val HINGLISH_BANA = Regex("\\bbana\\s+do\\b")
        private val HINGLISH_DE = Regex("\\bde\\s+do\\b")
        private val HINGLISH_RAKH = Regex("\\brakh\\s+do\\b")
        private val HINGLISH_PACK = Regex("\\bpack\\s+(kar\\s*do|kardo)?\\b")
        private val HINGLISH_OPENER = Regex(
            "^(acha|achha|theek|thik|aapka|aap|sir|madam|bhaiya|bhai|hello|hey|are|arrey)\\s+"
        )

        // Order notes precompiled patterns
        private const val INDIAN_RTO_CODES =
            "(?:an|ap|ar|as|br|cg|ch|dd|dl|dn|ga|gj|hp|hr|jh|jk|ka|kl|la|ld|mh|ml|mn|mp|mz|nl|od|or|pb|py|rj|sk|tn|tr|ts|uk|up|wb)"
        private val INDIAN_PLATE_REGEX = Regex(
            "\\b($INDIAN_RTO_CODES\\s*\\d{1,2}\\s*[a-z]{1,3}\\s*\\d{4}|\\d{2}\\s*bh\\s*\\d{4}\\s*[a-z]{1,2})\\b"
        )
        private val CAR_NUM_EXPLICIT_REGEX = Regex(
            "\\b(?:car|gaadi|gadi|vehicle)\\s*(?:no\\.?|number|num|#)\\s*([a-z]{0,2}\\s*\\d{1,4})\\b"
        )
        private val CAR_NUM_BARE_REGEX = Regex(
            "\\b(?:car|gaadi|gadi|vehicle)\\s+(\\d{3,4})\\b"
        )
        private val PARKING_REGEX = Regex("\\b(parking|parking delivery|car mein|gaadi mein)\\b")
        private val PACK_REGEX = Regex(
            "\\b(pack|pack\\s*kardo|pack\\s*kar\\s*do|parcel|parcel\\s*kardo|parcel\\s*kar\\s*do|takeaway|take\\s*away|to\\s*go)\\b"
        )
        private val DINE_IN_REGEX = Regex("\\b(dine\\s*in|yahi\\s*khayenge|table\\s*pe)\\b")
        private val NO_SUGAR_REGEX = Regex("\\b(bina\\s*cheeni|no\\s*sugar|without\\s*sugar|sugar\\s*free|sugarfree)\\b")
        private val LESS_SUGAR_REGEX = Regex("\\b(kam\\s*cheeni|less\\s*sugar|less\\s*sweet|sweet\\s*kam|kam\\s*meetha)\\b")
        private val EXTRA_HOT_REGEX = Regex("\\b(extra\\s*hot|bahut\\s*garam|jyada\\s*garam|kadak)\\b")
        private val MILD_SPICY_REGEX = Regex("\\b(kam\\s*spicy|less\\s*spicy|no\\s*spicy|kam\\s*mirchi|bina\\s*mirchi)\\b")
        private val EXTRA_SPICY_REGEX = Regex("\\b(extra\\s*spicy|jyada\\s*spicy|bahut\\s*spicy|tez\\s*mirchi)\\b")
        private val NO_ICE_REGEX = Regex("\\b(bina\\s*baraf|no\\s*ice|without\\s*ice)\\b")
        private val LESS_ICE_REGEX = Regex("\\b(less\\s*ice|kam\\s*baraf)\\b")
        private val NO_ONION_REGEX = Regex("\\b(bina\\s*pyaz|no\\s*onion|jain)\\b")
        private val EXTRA_ONION_REGEX = Regex("\\b(extra\\s*onion|jyada\\s*pyaz)\\b")
        private val OAT_MILK_REGEX = Regex("\\b(oat\\s*milk)\\b")
        private val ALMOND_MILK_REGEX = Regex("\\b(almond\\s*milk)\\b")
        private val DIGITS_ONLY_REGEX = Regex("\\d+")
    }

    fun resetSession() {
        lastOrderModality = null
    }

    fun processUtterance(
        rawText: String,
        currentState: VoiceOrderState,
        modality: VoiceModalityProfile? = null
    ): VoiceOrderState {
        val text = rawText.lowercase().trim()
        if (text.isBlank()) return currentState

        val cleaned = normalizeHinglish(text)

        // Check notes clear BEFORE global reset so "clear notes" / "reset notes" does not wipe the entire cart
        if (NOTES_CLEAR_REGEXES.any { it.containsMatchIn(cleaned) || it.containsMatchIn(text) }) {
            return currentState.copy(
                lastTranscript = rawText,
                orderNotes = null,
                statusMessage = "Cleared order notes"
            )
        }

        // 1. Check Global Reset Commands (word-boundary safe)
        if (RESET_REGEXES.any { it.containsMatchIn(text) }) {
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

        // 2. Extract all non-overlapping menu items in sequence
        val matches = extractAllMenuItems(cleaned)

        // 3. Extract order notes & modifiers (parking, vehicle plate, dietary notes, packaging)
        val extractedNotes = extractOrderNotes(rawText, currentState.orderNotes)

        // If no menu items matched, check for standalone confirmation, removal, quantity update, or notes
        if (matches.isEmpty()) {
            if (CONFIRM_REGEXES.any { it.containsMatchIn(cleaned) }) {
                return currentState.copy(
                    state = VoiceStateMachineEnum.CONFIRMED,
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Order confirmed! Ready for billing."
                )
            }

            // Standalone removal: user says "hata do", "sandwich hata do", "fries hata do", "cancel", "remove"
            val isStandaloneRemoval = REMOVAL_REGEXES.any { it.containsMatchIn(cleaned) }
            if (isStandaloneRemoval && currentState.items.isNotEmpty()) {
                val updatedItems = currentState.items.toMutableList()

                val mentionedWord = CATEGORY_WORD_REGEXES.entries
                    .firstOrNull { (_, regex) -> regex.containsMatchIn(cleaned) }
                    ?.key
                val targetIdx = if (mentionedWord != null) {
                    val found = updatedItems.indexOfLast { it.name.lowercase().contains(mentionedWord) }
                    if (found >= 0) found else updatedItems.size - 1
                } else {
                    updatedItems.size - 1
                }

                val removedItem = updatedItems.removeAt(targetIdx)
                val subtotal = updatedItems.sumOf { it.itemTotal }
                val newState = if (updatedItems.isEmpty()) VoiceStateMachineEnum.IDLE else VoiceStateMachineEnum.ORDER_BUILDING
                return currentState.copy(
                    state = newState,
                    items = updatedItems,
                    subtotal = subtotal,
                    tax = 0.0,
                    total = subtotal,
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Removed ${removedItem.name}"
                )
            }

            // Standalone quantity update on existing cart items
            val standaloneQty = extractStandaloneQuantity(cleaned)
            if (standaloneQty != null && currentState.items.isNotEmpty()) {
                val updatedItems = currentState.items.toMutableList()
                val lastIdx = updatedItems.size - 1
                val lastItem = updatedItems[lastIdx]
                val newQty = if (standaloneQty.isAdditive) {
                    lastItem.quantity + standaloneQty.quantity
                } else {
                    standaloneQty.quantity
                }.coerceAtLeast(1)
                val lineTotal = newQty * lastItem.unitPrice
                updatedItems[lastIdx] = lastItem.copy(quantity = newQty, itemTotal = lineTotal)

                val subtotal = updatedItems.sumOf { it.itemTotal }
                return currentState.copy(
                    state = VoiceStateMachineEnum.ORDER_BUILDING,
                    items = updatedItems,
                    subtotal = subtotal,
                    tax = 0.0,
                    total = subtotal,
                    lastTranscript = rawText,
                    orderNotes = extractedNotes,
                    statusMessage = "Updated ${lastItem.name} to ${newQty}×"
                )
            }

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

        val utteranceHasLeadingAdditive = LEADING_ADDITIVE_REGEX.containsMatchIn(text)

        val updatedItems = currentState.items.toMutableList()
        val actionsFeedback = mutableListOf<String>()
        var anyItemMatched = false

        // Detect if utterance is a multi-item Staff/Customer repeat-back confirmation
        val allItemsAlreadyInCart = matches.size >= 2 && matches.all { match ->
            currentState.items.any { it.name == match.item.name }
        }
        val isPureConfirmationTurn = allItemsAlreadyInCart && !utteranceHasLeadingAdditive

        var nextPrefixStart = 0
        for ((i, match) in matches.withIndex()) {
            val prefixStart = nextPrefixStart.coerceAtMost(match.start)
            val prefix = cleaned.substring(prefixStart, match.start).trim()

            // Split intervening text strictly at conjunctions/punctuation so suffix[i] and prefix[i+1] never bleed
            val suffix: String
            if (i + 1 < matches.size) {
                val nextStart = matches[i + 1].start
                val intervening = cleaned.substring(match.end, nextStart)
                val conjMatch = CONJUNCTION_REGEX.find(intervening)
                if (conjMatch != null) {
                    suffix = intervening.substring(0, conjMatch.range.first).trim()
                    nextPrefixStart = match.end + conjMatch.range.last + 1
                } else {
                    suffix = ""
                    nextPrefixStart = match.end
                }
            } else {
                suffix = cleaned.substring(match.end).trim()
                nextPrefixStart = cleaned.length
            }

            val isRemoval = REMOVAL_REGEXES.any { it.containsMatchIn(prefix) || it.containsMatchIn(suffix) }
            val rawQuantity = extractQuantityFromContext(prefix, suffix)
            val isExplicitlyAdditive = utteranceHasLeadingAdditive ||
                ADDITIVE_REGEXES.any { it.containsMatchIn(prefix) }
            val modifiers = extractModifiers("$prefix $suffix")

            val matchedItem = match.item
            anyItemMatched = true

            val existingIndex = updatedItems.indexOfFirst {
                it.name == matchedItem.name && (isRemoval || it.modifiers == modifiers)
            }
            val existing = if (existingIndex >= 0) updatedItems[existingIndex] else null

            // In a pure confirmation turn without explicit numerals, keep existing cart quantity
            val hasExplicitPrefixNumeral = EXPLICIT_PREFIX_NUMERAL_REGEX.containsMatchIn(prefix)
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
                        val newQty = existing.quantity + quantity
                        val lineTotal = newQty * existing.unitPrice
                        updatedItems[existingIndex] = existing.copy(quantity = newQty, itemTotal = lineTotal)
                        actionsFeedback.add("Added +$quantity ${matchedItem.name} (${newQty}×)")
                    } else if (quantity == existing.quantity) {
                        actionsFeedback.add("Confirmed ${existing.quantity}× ${matchedItem.name}")
                    } else {
                        val lineTotal = quantity * existing.unitPrice
                        updatedItems[existingIndex] = existing.copy(quantity = quantity, itemTotal = lineTotal)
                        actionsFeedback.add("Synced ${matchedItem.name} to ${quantity}×")
                    }
                } else {
                    val effectiveUnitPrice = matchedItem.price + modifiers.sumOf { it.priceDelta }
                    val lineTotal = quantity * effectiveUnitPrice
                    val newItem = VoiceOrderLineItem(
                        lineId = UUID.randomUUID().toString(),
                        sku = "SKU_${matchedItem.id}",
                        name = matchedItem.name,
                        quantity = quantity,
                        unitPrice = effectiveUnitPrice,
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
        val isUtteranceConfirmed = CONFIRM_REGEXES.any { it.containsMatchIn(cleaned) }
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
            tax = 0.0,
            total = subtotal,
            lastTranscript = rawText,
            statusMessage = feedbackMsg,
            orderNotes = extractedNotes
        )
    }

    private fun normalizeHinglish(raw: String): String {
        return raw.lowercase().trim()
            .replace(HINGLISH_KAR_DO, "kardo")
            .replace(HINGLISH_HATA, "remove")
            .replace(HINGLISH_NIKAL, "remove")
            .replace(HINGLISH_MAT, "remove")
            .replace(HINGLISH_NAHI, "remove")
            .replace(HINGLISH_CANCEL, "cancel")
            .replace(HINGLISH_BANA, "make")
            .replace(HINGLISH_DE, "give")
            .replace(HINGLISH_RAKH, "keep")
            .replace(HINGLISH_PACK, "pack")
            .replace(HINGLISH_OPENER, "")
    }

    private fun extractAllMenuItems(text: String): List<ExtractedItemMatch> {
        val matchers = MenuCatalog.getCompiledMatchers()
        val allMatches = mutableListOf<ExtractedItemMatch>()
        for (matcher in matchers) {
            for (m in matcher.regex.findAll(text)) {
                allMatches.add(ExtractedItemMatch(matcher.item, m.range.first, m.range.last + 1, matcher.alias))
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

    private fun tokenizeWords(text: String): List<String> =
        text.split(WHITESPACE_REGEX)
            .map { it.replace(PUNCT_EDGE_REGEX, "") }
            .filter { it.isNotBlank() }

    private fun extractQuantityFromContext(prefix: String, suffix: String): Int {
        val qtyPrefix = NON_ITEM_NUM_CONTEXT_REGEX.replace(prefix, " ")
        val prefixTokens = tokenizeWords(qtyPrefix)

        val candidates = mutableListOf<QtyCandidate>()
        var prevNonArticleQty: Int? = null
        for (token in prefixTokens) {
            val digitNum = token.toIntOrNull()
            if (digitNum != null && digitNum in 1..99) {
                candidates.add(QtyCandidate(digitNum, isArticle = false))
                prevNonArticleQty = digitNum
                continue
            }
            val wordNum = WORD_TO_NUM[token]
            if (wordNum != null) {
                // In Hinglish, "chaar do" / "teen do" means "give 4/3", not 2
                if (token == "do" && prevNonArticleQty != null && prevNonArticleQty >= 3) {
                    continue
                }
                val isArticle = token in ARTICLE_WORDS
                candidates.add(QtyCandidate(wordNum, isArticle = isArticle))
                if (!isArticle) {
                    prevNonArticleQty = wordNum
                }
            } else {
                prevNonArticleQty = null
            }
        }

        val explicitMatch = candidates.lastOrNull { !it.isArticle }
        if (explicitMatch != null) return explicitMatch.value
        val articleMatch = candidates.lastOrNull()
        if (articleMatch != null) return articleMatch.value

        // Fallback: check immediate suffix for digits or trailing numerals (e.g. "Fries, 2." or "americano teen")
        val qtySuffix = NON_ITEM_NUM_CONTEXT_REGEX.replace(suffix, " ")
        val suffixTokens = tokenizeWords(qtySuffix).take(3)
        for ((idx, token) in suffixTokens.withIndex()) {
            val digitNum = token.toIntOrNull()
            if (digitNum != null && digitNum in 1..99) {
                return digitNum
            }
            val wordNum = WORD_TO_NUM[token]
            if (wordNum != null && token !in ARTICLE_WORDS) {
                // Standalone trailing "do" without counter unit means Hindi "give" ("peri peri fries do" -> 1)
                if (token == "do") {
                    val nextToken = suffixTokens.getOrNull(idx + 1)
                    if (nextToken == null || nextToken !in UNIT_COUNTER_WORDS) {
                        continue
                    }
                }
                return wordNum
            }
        }

        return 1
    }

    private fun extractModifiers(segment: String): List<VoiceSelectedModifier> {
        val mods = mutableListOf<VoiceSelectedModifier>()
        if (segment.contains("extra cheese")) {
            mods.add(VoiceSelectedModifier(groupId = "cheese", name = "Extra Cheese", priceDelta = 15.0))
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

    private fun parseNumeralToken(rawToken: String): Int? {
        val clean = rawToken.replace(PUNCT_EDGE_REGEX, "")
        val digit = clean.toIntOrNull()
        if (digit != null && digit in 1..99) return digit
        return WORD_TO_NUM[clean]
    }

    private fun extractStandaloneQuantity(text: String): StandaloneQtyResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        if (META_REGEXES.any { it.containsMatchIn(trimmed) }) {
            return null
        }

        // Generalized additive: "1 more", "2 more", "ek aur", "chaar aur", "plus 2", "add 3", "another"
        ADDITIVE_BEFORE_REGEX.find(trimmed)?.let { m ->
            val qty = parseNumeralToken(m.groupValues[1])
            if (qty != null) return StandaloneQtyResult(qty, isAdditive = true)
        }
        ADDITIVE_AFTER_REGEX.find(trimmed)?.let { m ->
            val qty = parseNumeralToken(m.groupValues[1])
            if (qty != null) return StandaloneQtyResult(qty, isAdditive = true)
        }
        if (ANOTHER_REGEX.containsMatchIn(trimmed)) {
            return StandaloneQtyResult(1, isAdditive = true)
        }

        // Double / Triple
        if (DOUBLE_REGEX.containsMatchIn(trimmed)) {
            return StandaloneQtyResult(2, isAdditive = false)
        }
        if (TRIPLE_REGEX.containsMatchIn(trimmed)) {
            return StandaloneQtyResult(3, isAdditive = false)
        }

        // Direct isolated numeral tokens: "do", "two", "teen", "three", "2.", "3", etc.
        val tokens = tokenizeWords(trimmed)
        if (tokens.size <= 2 || (tokens.size == 3 && STANDALONE_FILLER_REGEX.containsMatchIn(trimmed))) {
            for (t in tokens) {
                val num = parseNumeralToken(t)
                if (num != null && num in 1..99) {
                    return StandaloneQtyResult(num, isAdditive = false)
                }
            }
        }

        return null
    }

    private fun noteCategoryOf(noteText: String): String {
        return when {
            noteText.startsWith("🚗") || noteText.startsWith("🅿") -> "VEHICLE_PARKING"
            noteText.startsWith("📦") || noteText.startsWith("🍽") -> "SERVICE_MODE"
            noteText.contains("Sugar", ignoreCase = true) -> "SUGAR"
            noteText.startsWith("🔥") -> "TEMP"
            noteText.startsWith("🌶") -> "SPICE"
            noteText.startsWith("🧊") -> "ICE"
            noteText.startsWith("🧅") -> "ONION"
            noteText.startsWith("🥛") -> "MILK"
            else -> noteText
        }
    }

    fun extractOrderNotes(rawText: String, existingNotes: String?): String? {
        val text = rawText.lowercase().trim()
        val detected = mutableListOf<DetectedNote>()

        // 1. Vehicle / Parking extraction (valid Indian RTO codes or explicit car number marker)
        val plateMatch = INDIAN_PLATE_REGEX.find(text)
        val carNumMatch = CAR_NUM_EXPLICIT_REGEX.find(text) ?: CAR_NUM_BARE_REGEX.find(text)

        if (plateMatch != null) {
            val plate = plateMatch.groupValues[1].uppercase().replace(WHITESPACE_REGEX, " ")
            detected.add(DetectedNote("VEHICLE_PARKING", "🚗 $plate"))
        } else if (carNumMatch != null) {
            val valStr = carNumMatch.groupValues[1].trim().uppercase()
            if (valStr.any { it.isDigit() }) {
                val cleanNum = DIGITS_ONLY_REGEX.find(valStr)?.value ?: valStr
                detected.add(DetectedNote("VEHICLE_PARKING", "🚗 Car #$cleanNum"))
            }
        }

        if (PARKING_REGEX.containsMatchIn(text)) {
            if (detected.none { it.category == "VEHICLE_PARKING" }) {
                detected.add(DetectedNote("VEHICLE_PARKING", "🅿️ Parking Delivery"))
            }
        }

        // 2. Packaging / Service
        if (PACK_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("SERVICE_MODE", "📦 Parcel / Takeaway"))
        } else if (DINE_IN_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("SERVICE_MODE", "🍽️ Dine-In"))
        }

        // 3. Dietary / Customization Notes
        if (NO_SUGAR_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("SUGAR", "🚫 No Sugar"))
        } else if (LESS_SUGAR_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("SUGAR", "☕ Less Sugar"))
        }

        if (EXTRA_HOT_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("TEMP", "🔥 Extra Hot"))
        }

        if (MILD_SPICY_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("SPICE", "🌶️ Mild / Less Spicy"))
        } else if (EXTRA_SPICY_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("SPICE", "🌶️🌶️ Extra Spicy"))
        }

        if (NO_ICE_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("ICE", "🧊 No Ice"))
        } else if (LESS_ICE_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("ICE", "🧊 Less Ice"))
        }

        if (NO_ONION_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("ONION", "🧅 No Onion (Jain)"))
        } else if (EXTRA_ONION_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("ONION", "🧅 Extra Onion"))
        }

        if (OAT_MILK_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("MILK", "🥛 Oat Milk"))
        } else if (ALMOND_MILK_REGEX.containsMatchIn(text)) {
            detected.add(DetectedNote("MILK", "🥛 Almond Milk"))
        }

        if (detected.isEmpty()) return existingNotes

        val allNotes = (existingNotes?.split(" • ") ?: emptyList())
            .filter { it.isNotBlank() }
            .toMutableList()

        for (note in detected) {
            allNotes.removeAll { noteCategoryOf(it) == note.category }
            allNotes.add(note.text)
        }

        return allNotes.joinToString(" • ")
    }
}
