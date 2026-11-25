package com.example.voice.engine

import com.example.data.entity.MenuItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CatalogItem(
    val id: Int,
    val name: String,
    val category: String,
    val price: Double,
    val aliases: List<String> = emptyList(),
    val isAvailable: Boolean = true
)

data class CompiledAliasMatcher(
    val item: CatalogItem,
    val alias: String,
    val regex: Regex
)

object MenuCatalog {

    private val NON_ALNUM_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private fun normalizeKey(value: String): String =
        value.lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .trim()
            .replace(MULTI_SPACE_REGEX, " ")

    // Default phonetic aliases for FILTR Coffee's signature menu items
    private val defaultAliasesMap: Map<String, List<String>> = mapOf(
        "caramel frappe" to listOf(
            "caramel frappe", "caramel frap", "caramel frappy", "caramel frappi",
            "caramel frappie", "caramel frape", "frappe caramel", "frap caramel",
            "caramel trap", "caramel wrap", "caramel flap", "caramel rap"
        ),
        "hazelnut frappe" to listOf(
            "hazelnut frappe", "hazelnut frap", "hazelnut frappy", "hazelnut frappi",
            "hazelnut frappie", "hazelnut frape", "frappe hazelnut", "frap hazelnut",
            "hazelnut trap", "hazelnut wrap", "hazelnut flap", "hazelnut rap"
        ),
        "just frappe" to listOf(
            "just frappe", "just frap", "just frappy", "regular frappe", "plain frappe",
            "frappe", "frap", "frappy", "frape", "just trap", "just wrap", "just flap"
        ),
        "vanila frappe" to listOf(
            "vanila frappe", "vanilla frappe", "vanilla frap", "vanila frappy",
            "vanilla frappy", "vanila frape", "vanilla frape", "frappe vanilla",
            "frap vanilla", "vanilla trap", "vanilla wrap", "vanilla flap"
        ),
        "peri peri fries" to listOf(
            "peri peri fries", "peri peri fry", "peri peri", "perry perry fries",
            "perry perry", "perry very fries", "very perry fries", "perry fries",
            "periphery fries", "periphery price", "periphery", "pari pari fries",
            "pari pari", "piri piri", "peri peri price", "very very fries",
            "very very", "fairy fairy fries", "fairy fairy", "berry berry fries",
            "berry berry", "ferry ferry fries", "ferry ferry"
        ),
        "salted fries" to listOf(
            "salted fries", "french fries", "fries", "salted fry", "regular fries",
            "salted price", "unsolved price", "salted flies", "salted"
        ),
        "americano" to listOf(
            "americano", "iced americano", "black coffee", "ice americano",
            "american", "america no", "america know"
        ),
        "caramel iced coffee" to listOf(
            "caramel iced coffee", "caramel ice coffee", "caramelized coffee",
            "caramel coffee", "iced caramel", "caramel iced", "caramel ice"
        ),
        "hazelnut iced coffee" to listOf(
            "hazelnut iced coffee", "hazelnut ice coffee", "hazelnut coffee",
            "iced hazelnut", "hazelnut iced", "hazelnut ice", "hazelnut ice tree", "hazelnut ice tea"
        ),
        "og vietnamese" to listOf(
            "og vietnamese", "o g vietnamese", "o g", "oh gee vietnamese", "oh gee",
            "og vietnamese coffee", "og coffee", "og iced coffee"
        ),
        "vanila iced coffee" to listOf(
            "vanila iced coffee", "vanilla iced coffee", "vanilla ice coffee",
            "vanilla coffee", "vanila coffee", "vanilla ice"
        ),
        "vietnamese iced coffee" to listOf(
            "vietnamese iced coffee", "vietnamese ice coffee", "vietnamese coffee",
            "vietnamese", "iced vietnamese", "vietnamese ice"
        ),
        "bombay" to listOf(
            "bombay sandwich", "bombay", "bombay masala", "bombay masala sandwich"
        ),
        "caramalized onion" to listOf(
            "caramalized onion", "caramelized onion", "caramelized onion sandwich",
            "onion sandwich", "caramel onion", "caramalized onion sandwich"
        ),
        "cheese corn" to listOf(
            "cheese corn", "cheese corn sandwich", "corn sandwich", "corn cheese",
            "cheese sandwich"
        ),
        "paneer sandwich" to listOf(
            "paneer sandwich", "paneer", "paneer toast", "paneer toastie",
            "pioneer sandwich", "pan near sandwich", "pane sandwich", "funny sandwich"
        )
    )

    // Normalized exact lookup map (prevents substring bleeding like "Bombay Pizza" matching "bombay")
    private val normalizedAliasesMap: Map<String, List<String>> = buildMap {
        defaultAliasesMap.forEach { (k, v) -> put(normalizeKey(k), v) }
        // Exact variant names used in tests/menus
        defaultAliasesMap["bombay"]?.let { put("bombay sandwich", it) }
        defaultAliasesMap["cheese corn"]?.let { put("cheese corn sandwich", it) }
        defaultAliasesMap["caramalized onion"]?.let {
            put("caramalized onion sandwich", it)
            put("caramelized onion", it)
            put("caramelized onion sandwich", it)
        }
        defaultAliasesMap["vanila frappe"]?.let { put("vanilla frappe", it) }
        defaultAliasesMap["vanila iced coffee"]?.let { put("vanilla iced coffee", it) }
    }

    // Base seed catalog matching FiltrRepository default menu
    val defaultItems: List<CatalogItem> = listOf(
        CatalogItem(1, "Americano", "ICED COFFEE", 79.0, defaultAliasesMap["americano"] ?: emptyList()),
        CatalogItem(2, "Vanila Iced Coffee", "ICED COFFEE", 99.0, defaultAliasesMap["vanila iced coffee"] ?: emptyList()),
        CatalogItem(3, "Hazelnut Iced Coffee", "ICED COFFEE", 99.0, defaultAliasesMap["hazelnut iced coffee"] ?: emptyList()),
        CatalogItem(4, "Caramel Iced Coffee", "ICED COFFEE", 99.0, defaultAliasesMap["caramel iced coffee"] ?: emptyList()),
        CatalogItem(5, "Vietnamese Iced Coffee", "ICED COFFEE", 99.0, defaultAliasesMap["vietnamese iced coffee"] ?: emptyList()),
        CatalogItem(6, "OG Vietnamese", "ICED COFFEE", 109.0, defaultAliasesMap["og vietnamese"] ?: emptyList()),
        CatalogItem(7, "Just Frappe", "FRAPPE", 109.0, defaultAliasesMap["just frappe"] ?: emptyList()),
        CatalogItem(8, "Vanila Frappe", "FRAPPE", 129.0, defaultAliasesMap["vanila frappe"] ?: emptyList()),
        CatalogItem(9, "Hazelnut Frappe", "FRAPPE", 129.0, defaultAliasesMap["hazelnut frappe"] ?: emptyList()),
        CatalogItem(10, "Caramel Frappe", "FRAPPE", 129.0, defaultAliasesMap["caramel frappe"] ?: emptyList()),
        CatalogItem(11, "Salted Fries", "FRIES", 89.0, defaultAliasesMap["salted fries"] ?: emptyList()),
        CatalogItem(12, "Peri Peri Fries", "FRIES", 99.0, defaultAliasesMap["peri peri fries"] ?: emptyList()),
        CatalogItem(13, "Bombay", "SANDWICH", 89.0, defaultAliasesMap["bombay"] ?: emptyList()),
        CatalogItem(14, "Cheese Corn", "SANDWICH", 99.0, defaultAliasesMap["cheese corn"] ?: emptyList()),
        CatalogItem(15, "Caramalized Onion", "SANDWICH", 109.0, defaultAliasesMap["caramalized onion"] ?: emptyList()),
        CatalogItem(16, "Paneer Sandwich", "SANDWICH", 119.0, defaultAliasesMap["paneer sandwich"] ?: emptyList())
    )

    val baseControlPhrases = listOf(
        // Quantities (English & Hindi)
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "a", "an", "single", "double", "triple", "more", "one more", "another",
        "ek", "do", "teen", "char", "paanch", "chhe", "saat", "aath", "nau", "das",
        "punch", "or",
        // Connectors & Politeness
        "and", "plus", "with", "also", "okay", "ok", "acha", "to", "aap", "hai",
        "sir", "madam", "bhaiya", "right", "yes", "no", "yeah", "sure",
        "kar", "dena", "aur", "bas", "total", "bill", "repeat", "check",
        // Actions & Controls
        "add", "remove", "cancel", "delete", "minus", "hata do", "hata", "nikal do",
        "clear", "reset", "start over", "confirm", "done", "order", "please", "place order",
        // Modifiers & Order Notes
        "extra cheese", "no ice", "less ice", "no onion", "extra onion", "jain",
        "less sweet", "sugar free", "no sugar", "less sugar", "extra sugar",
        "extra hot", "mild", "less spicy", "extra spicy",
        "oat milk", "almond milk",
        "parcel", "takeaway", "pack", "pack kar do", "dine in", "table",
        "car", "parking",
        // Out of vocabulary sink (prevents hallucinating background noise into items)
        "[unk]"
    )

    private val catalogLock = Any()

    private val _itemsFlow = MutableStateFlow(defaultItems)
    val itemsFlow: StateFlow<List<CatalogItem>> = _itemsFlow.asStateFlow()

    @Volatile
    private var compiledMatchersSnapshot: List<CompiledAliasMatcher> = compileMatchers(defaultItems)

    private fun compileMatchers(items: List<CatalogItem>): List<CompiledAliasMatcher> {
        val list = mutableListOf<CompiledAliasMatcher>()
        for (item in items) {
            val candidates = (item.aliases + listOf(item.name.lowercase().trim()))
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByDescending { it.length }
            for (alias in candidates) {
                val regex = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(alias)}(?![\\p{L}\\p{N}_])")
                list.add(CompiledAliasMatcher(item = item, alias = alias, regex = regex))
            }
        }
        return list.toList()
    }

    /**
     * Automatic Acoustic Alias Generator for dynamic menu additions:
     * Derives singular/plural, reverse word order, category stripping, and safe phonetic shortcuts.
     */
    fun generateAliasesForItem(name: String, @Suppress("UNUSED_PARAMETER") category: String = ""): List<String> {
        val raw = name.lowercase().trim()
        val clean = normalizeKey(name)
        val result = mutableSetOf<String>()
        if (raw.isNotBlank()) result.add(raw)
        if (clean.isNotBlank()) result.add(clean)

        val words = clean.split(" ").filter { it.isNotBlank() }

        // Reverse two words: "cheese toast" -> "toast cheese"
        if (words.size == 2) {
            result.add("${words[1]} ${words[0]}")
        }

        // Strip common menu prefixes / descriptors
        val descriptorKeywords = setOf("iced", "ice", "hot", "special", "classic", "signature", "filtr", "fresh", "crispy")
        val strippedWords = words.filter { it !in descriptorKeywords }
        if (strippedWords.isNotEmpty() && strippedWords.size < words.size) {
            val stripped = strippedWords.joinToString(" ")
            result.add(stripped)
            if (strippedWords.size == 2) {
                result.add("${strippedWords[1]} ${strippedWords[0]}")
            }
        }

        // Singular / plural derivation
        for (w in listOf(raw, clean).distinct()) {
            if (w.isBlank()) continue
            if (w.endsWith("ies")) {
                result.add(w.removeSuffix("ies") + "y")
            } else if (w.endsWith("es") && !w.endsWith("cheese")) {
                result.add(w.removeSuffix("es"))
            } else if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("cheese")) {
                result.add(w.removeSuffix("s"))
            } else {
                result.add("${w}s")
            }
        }

        // Common colloquial phonetic shortcuts (avoid generic colliding words like "masala")
        if (clean.contains("frappe")) {
            result.add(clean.replace("frappe", "frap"))
            result.add(clean.replace("frappe", "frappy"))
            result.add(clean.replace("frappe", "flap"))
            result.add(clean.replace("frappe", "trap"))
        }
        if (clean.contains("sandwich")) {
            result.add(clean.replace("sandwich", "sand"))
            result.add(clean.replace("sandwich", "toast"))
        }
        if (clean.contains("coffee")) {
            result.add(clean.replace("coffee", "coffi"))
            result.add(clean.replace("coffee", "kopi"))
        }
        if (clean.contains("fries")) {
            result.add(clean.replace("fries", "fry"))
            result.add(clean.replace("fries", "chips"))
        }

        return result.filter { it.length >= 2 }.toList()
    }

    /**
     * Dynamically synchronizes catalog with live Room database menu items:
     * - Thread-safe atomic snapshot update
     * - Filters out inactive/unavailable items (isAvailable == true only)
     * - Exact normalized key lookup in defaultAliasesMap (zero substring bleeding)
     * - Prevents generated aliases from colliding across distinct menu items
     */
    fun syncWithRoomMenuItems(menuItems: List<MenuItemEntity>) {
        if (menuItems.isEmpty()) return

        synchronized(catalogLock) {
            val available = menuItems.filter { it.isAvailable }
            val canonicalNames = available.map { normalizeKey(it.name) }.toSet()

            // First pass: collect candidate aliases per item
            val rawCandidates = available.map { entity ->
                val normKey = normalizeKey(entity.name)
                val exactAliases = normalizedAliasesMap[normKey]
                val generated = generateAliasesForItem(entity.name, entity.category)
                val combined = if (exactAliases != null) {
                    (exactAliases + generated).distinct()
                } else {
                    generated
                }
                entity to combined
            }

            // Count alias ownership across items to prevent colliding non-canonical aliases
            val aliasOwnerCount = mutableMapOf<String, Int>()
            for ((_, aliases) in rawCandidates) {
                for (a in aliases.map { normalizeKey(it) }.distinct()) {
                    aliasOwnerCount[a] = (aliasOwnerCount[a] ?: 0) + 1
                }
            }

            val updatedItems = rawCandidates.map { (entity, aliases) ->
                val ownNormName = normalizeKey(entity.name)
                val filteredAliases = aliases.filter { alias ->
                    val normAlias = normalizeKey(alias)
                    when {
                        normAlias == ownNormName -> true
                        normAlias in canonicalNames -> false // Never steal another item's canonical name
                        (aliasOwnerCount[normAlias] ?: 0) > 1 -> false // Drop ambiguous cross-item aliases
                        else -> true
                    }
                }.ifEmpty { listOf(entity.name.lowercase().trim()) }

                CatalogItem(
                    id = entity.id,
                    name = entity.name,
                    category = entity.category,
                    price = entity.price,
                    aliases = filteredAliases.toList(),
                    isAvailable = entity.isAvailable
                )
            }.toList()

            compiledMatchersSnapshot = compileMatchers(updatedItems)
            _itemsFlow.value = updatedItems
        }
    }

    val items: List<CatalogItem>
        get() = _itemsFlow.value

    fun getCompiledMatchers(): List<CompiledAliasMatcher> = compiledMatchersSnapshot

    /**
     * Builds active speech grammar phrases including all active menu items,
     * synthesized aliases, numeric counts, modifiers, and unk sink.
     */
    fun getActiveGrammarPhrases(): List<String> {
        val snapshot = _itemsFlow.value
        val phrases = mutableSetOf<String>()
        phrases.addAll(baseControlPhrases)
        snapshot.forEach { item ->
            val cleanName = item.name.lowercase().trim()
            if (cleanName.isNotBlank()) phrases.add(cleanName)
            item.aliases.forEach { alias ->
                val cleanAlias = alias.lowercase().trim()
                if (cleanAlias.isNotBlank()) {
                    phrases.add(cleanAlias)
                }
            }
        }
        return phrases.toList()
    }

    fun findBestMatch(phrase: String): CatalogItem? {
        val norm = normalizeKey(phrase)
        if (norm.length < 2) return null

        val snapshot = _itemsFlow.value

        // 1. Exact canonical name or exact alias match
        for (item in snapshot) {
            if (normalizeKey(item.name) == norm || item.aliases.any { normalizeKey(it) == norm }) {
                return item
            }
        }

        if (norm.length < 3) return null

        // 2. Word-bounded alias inside query, preferring longest matched alias
        var bestItem: CatalogItem? = null
        var bestScore = 0
        for (item in snapshot) {
            for (alias in item.aliases) {
                val normAlias = normalizeKey(alias)
                if (normAlias.length < 3) continue
                val boundaryRegex = Regex("(?<![a-z0-9])${Regex.escape(normAlias)}(?![a-z0-9])")
                if (boundaryRegex.containsMatchIn(norm) && normAlias.length > bestScore) {
                    bestScore = normAlias.length
                    bestItem = item
                }
            }
        }
        if (bestItem != null) return bestItem

        // 3. Whole-word subset match (every query token matches a full word in item name)
        val queryTokens = norm.split(" ").filter { it.length >= 2 }
        if (queryTokens.isNotEmpty()) {
            return snapshot.firstOrNull { item ->
                val itemWords = normalizeKey(item.name).split(" ").toSet()
                queryTokens.all { qt -> qt in itemWords }
            }
        }

        return null
    }
}
