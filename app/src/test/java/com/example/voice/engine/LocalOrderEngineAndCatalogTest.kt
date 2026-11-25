package com.example.voice.engine

import com.example.data.entity.MenuItemEntity
import com.example.voice.model.VoiceOrderState
import com.example.voice.model.VoiceStateMachineEnum
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalOrderEngineAndCatalogTest {

    private lateinit var engine: LocalOrderEngine

    private val defaultMenuEntities: List<MenuItemEntity> = MenuCatalog.defaultItems.map { item ->
        MenuItemEntity(
            id = item.id,
            name = item.name,
            category = item.category,
            price = item.price,
            isAvailable = item.isAvailable
        )
    }

    @Before
    fun setUp() {
        MenuCatalog.syncWithRoomMenuItems(defaultMenuEntities)
        engine = LocalOrderEngine()
    }

    @After
    fun tearDown() {
        MenuCatalog.syncWithRoomMenuItems(defaultMenuEntities)
    }

    @Test
    fun `conjunction splits suffix and prefix without bleeding hindi verb do into next item`() {
        val state = engine.processUtterance("peri peri fries do aur americano teen", VoiceOrderState())

        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, state.state)
        assertEquals(2, state.items.size)
        val fries = state.items.find { it.name == "Peri Peri Fries" }
        val americano = state.items.find { it.name == "Americano" }
        assertNotNull(fries)
        assertNotNull(americano)
        assertEquals(1, fries?.quantity)
        assertEquals(3, americano?.quantity)
        assertEquals(99.0 + (3 * 79.0), state.total, 1e-6)
    }

    @Test
    fun `trailing do with unit counter word counts as quantity two`() {
        val state = engine.processUtterance("peri peri fries do plate aur americano teen", VoiceOrderState())

        val fries = state.items.find { it.name == "Peri Peri Fries" }
        val americano = state.items.find { it.name == "Americano" }
        assertEquals(2, fries?.quantity)
        assertEquals(3, americano?.quantity)
    }

    @Test
    fun `deepgram punctuation extracts quantities before and after item names`() {
        val prefixPunct = engine.processUtterance("Two, Americano.", VoiceOrderState())
        assertEquals(1, prefixPunct.items.size)
        assertEquals("Americano", prefixPunct.items[0].name)
        assertEquals(2, prefixPunct.items[0].quantity)
        assertEquals(158.0, prefixPunct.total, 1e-6)

        val suffixPunct = engine.processUtterance("Fries, 2.", VoiceOrderState())
        assertEquals(1, suffixPunct.items.size)
        assertEquals("Salted Fries", suffixPunct.items[0].name)
        assertEquals(2, suffixPunct.items[0].quantity)
        assertEquals(178.0, suffixPunct.total, 1e-6)

        val combined = engine.processUtterance("Two, Americano. Fries, 2.", VoiceOrderState())
        assertEquals(2, combined.items.size)
        assertEquals(2, combined.items.find { it.name == "Americano" }?.quantity)
        assertEquals(2, combined.items.find { it.name == "Salted Fries" }?.quantity)
        assertEquals(336.0, combined.total, 1e-6)
    }

    @Test
    fun `N more additive updates work standalone and with item names`() {
        var state = engine.processUtterance("one americano", VoiceOrderState())
        assertEquals(1, state.items.single().quantity)

        state = engine.processUtterance("2 more", state)
        assertEquals(3, state.items.single().quantity)
        assertEquals(237.0, state.total, 1e-6)

        state = engine.processUtterance("ek aur", state)
        assertEquals(4, state.items.single().quantity)

        state = engine.processUtterance("2 more americano", state)
        assertEquals(6, state.items.single().quantity)
        assertEquals(474.0, state.total, 1e-6)
    }

    @Test
    fun `double triple and hindi roman and devanagari numerals parse accurately`() {
        var state = engine.processUtterance("one americano", VoiceOrderState())
        state = engine.processUtterance("double", state)
        assertEquals(2, state.items.single().quantity)

        state = engine.processUtterance("triple", state)
        assertEquals(3, state.items.single().quantity)

        val inlineModifiers = engine.processUtterance("double caramel frappe and triple salted fries", VoiceOrderState())
        assertEquals(2, inlineModifiers.items.find { it.name == "Caramel Frappe" }?.quantity)
        assertEquals(3, inlineModifiers.items.find { it.name == "Salted Fries" }?.quantity)

        val devanagariState = engine.processUtterance("दो americano", VoiceOrderState())
        assertEquals(2, devanagariState.items.single().quantity)

        val hinglishState = engine.processUtterance("paanch bombay sandwich", VoiceOrderState())
        assertEquals("Bombay", hinglishState.items.single().name)
        assertEquals(5, hinglishState.items.single().quantity)
    }

    @Test
    fun `modifier priceDelta is included in unit price itemTotal and order total`() {
        var state = engine.processUtterance("two paneer sandwich extra cheese", VoiceOrderState())

        assertEquals(1, state.items.size)
        val extraCheeseItem = state.items.single()
        assertEquals("Paneer Sandwich", extraCheeseItem.name)
        assertEquals(2, extraCheeseItem.quantity)
        assertEquals(134.0, extraCheeseItem.unitPrice, 1e-6)
        assertEquals(268.0, extraCheeseItem.itemTotal, 1e-6)
        assertEquals(15.0, extraCheeseItem.modifiers.single().priceDelta, 1e-6)
        assertEquals(268.0, state.total, 1e-6)

        state = engine.processUtterance("one paneer sandwich", state)
        assertEquals(2, state.items.size)
        assertEquals(387.0, state.total, 1e-6)
    }

    @Test
    fun `boundary safe reset confirm and remove keywords ignore substring overlaps`() {
        var state = engine.processUtterance("one americano", VoiceOrderState())
        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, state.state)

        // "basil" contains "bas" (confirm keyword) and must NOT confirm
        state = engine.processUtterance("for basil", state)
        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, state.state)
        assertEquals(1, state.items.size)

        // "McDonald" contains "do" and must NOT reset/confirm/remove
        state = engine.processUtterance("one peri peri fries for McDonald", state)
        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, state.state)
        assertEquals(2, state.items.size)

        // Substring containing "clear" or "drop" must NOT reset or remove items
        state = engine.processUtterance("unclear raindrop takeaway", state)
        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, state.state)
        assertEquals(2, state.items.size)
        assertEquals("📦 Parcel / Takeaway", state.orderNotes)

        // "clear notes" clears notes without wiping cart items
        state = engine.processUtterance("clear notes", state)
        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, state.state)
        assertEquals(2, state.items.size)
        assertNull(state.orderNotes)

        // Custom menu item "Lemon Drop" matches item directly without triggering "drop" removal
        val customMenu = defaultMenuEntities + MenuItemEntity(
            id = 99,
            name = "Lemon Drop",
            category = "COOLERS",
            price = 120.0,
            isAvailable = true
        )
        MenuCatalog.syncWithRoomMenuItems(customMenu)
        val lemonDropState = engine.processUtterance("two lemon drop", VoiceOrderState())
        assertEquals(VoiceStateMachineEnum.ORDER_BUILDING, lemonDropState.state)
        assertEquals("Lemon Drop", lemonDropState.items.single().name)
        assertEquals(2, lemonDropState.items.single().quantity)
        assertEquals(240.0, lemonDropState.total, 1e-6)
    }

    @Test
    fun `indian rto plate regex captures valid plates and ignores to 1234 and car parking 2`() {
        val validPlateNotes = engine.extractOrderNotes("mh 12 ab 1234 takeaway", null)
        assertNotNull(validPlateNotes)
        assertTrue(validPlateNotes!!.contains("MH 12 AB 1234"))
        assertTrue(validPlateNotes.contains("Parcel / Takeaway"))

        val bharatSeriesNotes = engine.extractOrderNotes("22 bh 1234 aa", null)
        assertNotNull(bharatSeriesNotes)
        assertTrue(bharatSeriesNotes!!.contains("22 BH 1234 AA"))

        val falsePlateNotes = engine.extractOrderNotes("one americano to 1234", null)
        assertNull(falsePlateNotes)

        val carParkingState = engine.processUtterance("one americano car parking 2", VoiceOrderState())
        assertEquals(1, carParkingState.items.single().quantity)
        assertEquals("🅿️ Parking Delivery", carParkingState.orderNotes)

        val explicitCarState = engine.processUtterance("one americano car 4567", VoiceOrderState())
        assertEquals(1, explicitCarState.items.single().quantity)
        assertEquals("🚗 Car #4567", explicitCarState.orderNotes)
    }

    @Test
    fun `menu catalog syncs with exact normalized keys without substring bleeding`() {
        val pizzaOnlyMenu = listOf(
            MenuItemEntity(id = 1, name = "Bombay Pizza", category = "PIZZA", price = 199.0, isAvailable = true),
            MenuItemEntity(id = 2, name = "Americano", category = "ICED COFFEE", price = 79.0, isAvailable = true),
            MenuItemEntity(id = 3, name = "Secret Discontinued Drink", category = "SPECIAL", price = 150.0, isAvailable = false)
        )
        MenuCatalog.syncWithRoomMenuItems(pizzaOnlyMenu)

        // Unavailable items are filtered out
        assertNull(MenuCatalog.findBestMatch("Secret Discontinued Drink"))

        // "Bombay Pizza" must NOT inherit "bombay" / "bombay masala" aliases from default "bombay"
        assertNull(MenuCatalog.findBestMatch("bombay masala"))
        val bombayPizza = MenuCatalog.findBestMatch("Bombay Pizza")
        assertNotNull(bombayPizza)
        assertEquals("Bombay Pizza", bombayPizza?.name)

        // With "Bombay Sandwich" present, exact normalized key matches "bombay" aliases
        val customMenu = pizzaOnlyMenu + MenuItemEntity(
            id = 4, name = "Bombay Sandwich", category = "SANDWICH", price = 89.0, isAvailable = true
        )
        MenuCatalog.syncWithRoomMenuItems(customMenu)
        val bombayMasala = MenuCatalog.findBestMatch("bombay masala")
        assertNotNull(bombayMasala)
        assertEquals("Bombay Sandwich", bombayMasala?.name)

        // Exact phonetic alias matching
        assertEquals("Americano", MenuCatalog.findBestMatch("america no")?.name)
        assertNull(MenuCatalog.findBestMatch("x"))
    }
}
