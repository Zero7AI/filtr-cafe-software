package com.example.voice.engine

import com.example.data.entity.MenuItemEntity

data class CatalogItem(
    val id: Int,
    val name: String,
    val category: String,
    val price: Double,
    val aliases: List<String> = emptyList(),
    val isAvailable: Boolean = true
)

object MenuCatalog {

    // Default phonetic aliases for FILTR Coffee's signature menu items
    private val defaultAliasesMap = mapOf(
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

    var onMenuUpdated: ((List<String>) -> Unit)? = null

    @Volatile
    private var liveItems: List<CatalogItem> = defaultItems

    /**
     * Automatic Acoustic Alias Generator for dynamic menu additions:
     * Derives singular/plural, reverse word order, category stripping, and phonetic shortcuts.
     */
    fun generateAliasesForItem(name: String, category: String = ""): List<String> {
        val raw = name.lowercase().trim()
        val clean = raw.replace(Regex("[^a-z0-9\\s]"), " ").trim().replace(Regex("\\s+"), " ")
        val result = mutableSetOf<String>()
        result.add(raw)
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
        for (w in listOf(raw, clean)) {
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

        // Common colloquial phonetic shortcuts
        if (clean.contains("frappe")) {
            result.add(clean.replace("frappe", "frap"))
            result.add(clean.replace("frappe", "frappy"))
            result.add(clean.replace("frappe", "flap"))
            result.add(clean.replace("frappe", "trap"))
        }
        if (clean.contains("sandwich")) {
            result.add(clean.replace("sandwich", "sand"))
            result.add(clean.replace("sandwich", "toast"))
            result.add(clean.replace("sandwich", "masala"))
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
     * - Filters out inactive/unavailable items (isAvailable == true only)
     * - Automatically synthesizes acoustic aliases for new custom items
     * - Preserves signature fine-tuned aliases for existing items
     */
    fun syncWithRoomMenuItems(menuItems: List<MenuItemEntity>) {
        if (menuItems.isEmpty()) return
        val available = menuItems.filter { it.isAvailable }
        liveItems = available.map { entity ->
            val key = entity.name.lowercase().trim()
            val existingAliases = defaultAliasesMap[key]
                ?: defaultAliasesMap.entries.find { key.contains(it.key) || it.key.contains(key) }?.value
            val generated = generateAliasesForItem(entity.name, entity.category)
            val combinedAliases = if (existingAliases != null) {
                (existingAliases + generated).distinct()
            } else {
                generated
            }
            CatalogItem(
                id = entity.id,
                name = entity.name,
                category = entity.category,
                price = entity.price,
                aliases = combinedAliases,
                isAvailable = entity.isAvailable
            )
        }
        onMenuUpdated?.invoke(getActiveGrammarPhrases())
    }

    val items: List<CatalogItem>
        get() = liveItems

    /**
     * Builds active speech grammar phrases including all active menu items,
     * synthesized aliases, numeric counts, modifiers, and unk sink.
     */
    fun getActiveGrammarPhrases(): List<String> {
        val phrases = mutableSetOf<String>()
        phrases.addAll(baseControlPhrases)
        liveItems.forEach { item ->
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
        val lower = phrase.lowercase().trim()
        // 1. Exact name or alias match
        items.forEach { item ->
            if (item.name.lowercase() == lower || item.aliases.any { it == lower }) {
                return item
            }
        }
        // 2. Substring / contains match
        items.forEach { item ->
            if (item.aliases.any { alias -> lower.contains(alias) || alias.contains(lower) }) {
                return item
            }
        }
        return null
    }
}
