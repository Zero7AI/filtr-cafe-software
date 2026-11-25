package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.FiltrDatabase
import com.example.data.entity.KitchenInventoryItemEntity
import com.example.domain.kitchen.KitchenCountingMode
import com.example.domain.kitchen.KitchenInventorySeedCatalog
import com.example.domain.kitchen.KitchenSection
import com.example.domain.kitchen.KitchenShiftType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitchenInventoryIntegrationTest {

    private lateinit var db: FiltrDatabase
    private lateinit var repository: FiltrRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FiltrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FiltrRepository(
            menuDao = db.menuDao(),
            orderDao = db.orderDao(),
            upiAccountDao = db.upiAccountDao(),
            inventoryDao = db.inventoryDao(),
            customerDao = db.customerDao(),
            fixedCostDao = db.fixedCostDao(),
            checklistDao = db.checklistDao(),
            kitchenInventoryDao = db.kitchenInventoryDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `opening closing save resume duplicate guard and history preservation`() = runTest {
        val dao = db.kitchenInventoryDao()
        dao.insertItems(KitchenInventorySeedCatalog.defaultItems())
        val items = dao.getAllItemsSync()
        assertEquals(47, items.size)

        val fries = items.first { it.id == "df_french_fries" }
        val iceCream = items.first { it.id == "df_ice_cream" }
        val paneer = items.first { it.id == "rf_paneer" }

        // 1. Yesterday closing check submitted (historical record + future opening reference)
        repository.ensureKitchenShiftStarted("2026-11-24", KitchenShiftType.CLOSING, "Rohan")
        repository.saveKitchenItemEntry(
            dateKey = "2026-11-24",
            shiftType = KitchenShiftType.CLOSING,
            item = paneer,
            sealedQty = 0,
            openedQty = 0,
            openedRemainingQty = 450.0,
            wastageQty = 50.0,
            wastageReason = "Trimmed",
            checkedBy = "Rohan"
        )
        val yestSubmit = repository.submitKitchenShift("2026-11-24", KitchenShiftType.CLOSING, "Rohan", "Night clean")
        assertTrue(yestSubmit.isSuccess)

        // 2. Today opening starts -> gets yesterday closing as reference
        val (prevShift, prevEntries) = repository.getPreviousClosingReference("2026-11-25")
        assertNotNull(prevShift)
        assertEquals("2026-11-24", prevShift?.dateKey)
        assertEquals(450.0, prevEntries.first { it.itemId == paneer.id }.totalUsableQty, 0.001)

        // 3. Auto-save & resume incomplete opening check
        repository.ensureKitchenShiftStarted("2026-11-25", KitchenShiftType.OPENING, "Asha")
        repository.saveKitchenItemEntry(
            dateKey = "2026-11-25",
            shiftType = KitchenShiftType.OPENING,
            item = fries,
            sealedQty = 2,
            openedQty = 1,
            openedRemainingQty = 500.0,
            wastageQty = 0.0,
            wastageReason = "",
            checkedBy = "Asha"
        )
        // Simulate leaving & resuming later
        val inProgressEntries = dao.getEntriesForShiftSync("2026-11-25_OPENING")
        assertEquals(1, inProgressEntries.size)
        assertEquals(5500.0, inProgressEntries.first().totalUsableQty, 0.001)

        // Resume with percentage-based Ice Cream check
        repository.saveKitchenItemEntry(
            dateKey = "2026-11-25",
            shiftType = KitchenShiftType.OPENING,
            item = iceCream,
            sealedQty = 1,
            openedQty = 1,
            openedRemainingQty = 50.0,
            wastageQty = 0.0,
            wastageReason = "",
            checkedBy = "Asha"
        )
        val openSubmit = repository.submitKitchenShift("2026-11-25", KitchenShiftType.OPENING, "Asha", "Morning ok")
        assertTrue(openSubmit.isSuccess)

        // Duplicate submission prevented
        val duplicateOpen = repository.submitKitchenShift("2026-11-25", KitchenShiftType.OPENING, "Asha", "")
        assertTrue(duplicateOpen.isFailure)

        // Editing submitted shift entry directly is blocked
        val blockedEdit = repository.saveKitchenItemEntry(
            dateKey = "2026-11-25",
            shiftType = KitchenShiftType.OPENING,
            item = fries,
            sealedQty = 0,
            openedQty = 0,
            openedRemainingQty = 0.0,
            wastageQty = 0.0,
            wastageReason = "",
            checkedBy = "Asha"
        )
        assertTrue(blockedEdit.isFailure)

        // 4. Today closing check runs independently without overwriting opening or yesterday closing
        repository.ensureKitchenShiftStarted("2026-11-25", KitchenShiftType.CLOSING, "Rohan")
        repository.saveKitchenItemEntry(
            dateKey = "2026-11-25",
            shiftType = KitchenShiftType.CLOSING,
            item = fries,
            sealedQty = 0,
            openedQty = 1,
            openedRemainingQty = 1200.0,
            wastageQty = 300.0,
            wastageReason = "Spillage",
            checkedBy = "Rohan"
        )
        val closeSubmit = repository.submitKitchenShift("2026-11-25", KitchenShiftType.CLOSING, "Rohan", "")
        assertTrue(closeSubmit.isSuccess)

        // Verify all 3 shifts remain distinct in history
        val allShifts = dao.getAllShiftsSync()
        assertEquals(3, allShifts.size)
        assertEquals("ORANGE", dao.getEntriesForShiftSync("2026-11-25_CLOSING").first().stockStatus)
        assertEquals(5500.0, dao.getEntriesForShiftSync("2026-11-25_OPENING").first { it.itemId == fries.id }.totalUsableQty, 0.001)
    }

    @Test
    fun `manager can configure thresholds and add items to Outside Freezer`() = runTest {
        val dao = db.kitchenInventoryDao()
        dao.insertItems(KitchenInventorySeedCatalog.defaultItems())

        val outsideItem = KitchenInventoryItemEntity(
            id = "custom_out_milk",
            name = "UHT Milk Tetrapak",
            iconEmoji = "🥛",
            sectionId = KitchenSection.OUTSIDE_FREEZER.id,
            countingMode = KitchenCountingMode.SEALED_AND_ML.id,
            unitLabel = "ml",
            containerLabel = "cartons",
            packSize = 1000.0,
            minStockThreshold = 4000.0,
            targetStockLevel = 10000.0,
            orderIndex = 1,
            isDefault = false,
            isActive = true
        )
        val saveRes = repository.saveKitchenCatalogItem(outsideItem)
        assertTrue(saveRes.isSuccess)

        val activeItems = repository.activeKitchenItems.first()
        assertEquals(48, activeItems.size)
        assertEquals(1, activeItems.count { it.sectionId == KitchenSection.OUTSIDE_FREEZER.id })
    }
}
