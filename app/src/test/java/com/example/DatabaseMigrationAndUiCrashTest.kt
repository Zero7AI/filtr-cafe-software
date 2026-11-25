package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.FiltrDatabase
import com.example.data.entity.AttendanceLogEntity
import com.example.data.entity.KdsItemClaimEntity
import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.data.entity.KitchenInventoryShiftEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
import com.example.domain.attendance.DayAttendanceStatus
import com.example.domain.attendance.GeofenceCheckResult
import com.example.domain.attendance.StoreGeofenceConfig
import com.example.domain.attendance.TeamRollCallRow
import com.example.domain.kds.KdsQueueBuilder
import com.example.domain.kitchen.KitchenInventorySeedCatalog
import com.example.domain.kitchen.KitchenSection
import com.example.domain.kitchen.KitchenShiftType
import com.example.ui.BillingUiState
import com.example.ui.KitchenInventoryDashboardSummary
import com.example.ui.KitchenInventoryViewRoute
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.KdsScreen
import com.example.ui.screens.KitchenInventoryScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.theme.FiltrTheme
import com.example.util.KitchenLocationHelper
import com.example.util.LocationSample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationAndUiCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `room migration 13 to 17 preserves existing data and opens all new tables cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_verify_13_to_17.db"
        context.deleteDatabase(dbName)

        // Create authentic v13 SQLite database with pre-existing cafe data
        val helper = object : SQLiteOpenHelper(context, dbName, null, 13) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `menu_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `price` REAL NOT NULL, `description` TEXT NOT NULL, `isAvailable` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `orders` (`orderId` TEXT NOT NULL, `customerName` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `totalAmount` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `isMade` INTEGER NOT NULL, `isPaid` INTEGER NOT NULL, `isDelivered` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `notes` TEXT NOT NULL, `paymentMode` TEXT NOT NULL DEFAULT 'UNPAID', `completedTimestamp` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`orderId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `order_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `menuItemId` INTEGER NOT NULL, `itemName` TEXT NOT NULL, `itemPrice` REAL NOT NULL, `quantity` INTEGER NOT NULL, `subtotal` REAL NOT NULL, `completedQuantity` INTEGER NOT NULL DEFAULT 0, `completedTimestamp` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`orderId`) REFERENCES `orders`(`orderId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `upi_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vpa` TEXT NOT NULL, `payeeName` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `note` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `unit` TEXT NOT NULL, `standardQuantity` REAL NOT NULL, `defaultPrice` REAL NOT NULL, `currentStock` REAL NOT NULL, `isActive` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_orders` (`orderId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `supplierName` TEXT NOT NULL, `totalAmount` REAL NOT NULL, `notes` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`orderId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_order_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` TEXT NOT NULL, `itemName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `unitPrice` REAL NOT NULL, `subtotal` REAL NOT NULL, FOREIGN KEY(`orderId`) REFERENCES `inventory_orders`(`orderId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_order_items_orderId` ON `inventory_order_items` (`orderId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `menuItemId` INTEGER NOT NULL, `menuItemName` TEXT NOT NULL, `inventoryItemId` INTEGER NOT NULL, `inventoryItemName` TEXT NOT NULL, `quantityUsed` REAL NOT NULL, `unit` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `totalOrders` INTEGER NOT NULL, `totalSpent` REAL NOT NULL, `lastOrderTimestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `fixed_costs` (`monthKey` TEXT NOT NULL, `rent` REAL NOT NULL, `electricity` REAL NOT NULL, `staffSalaries` REAL NOT NULL, `staffCount` INTEGER NOT NULL, `maidSalary` REAL NOT NULL, `miscellaneous` REAL NOT NULL, `notes` TEXT NOT NULL, `staffMembersJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`monthKey`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_tasks` (`id` TEXT NOT NULL, `sectionId` TEXT NOT NULL, `sectionName` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `isWeeklyMondayOnly` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_completions` (`dateKey` TEXT NOT NULL, `taskId` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `completedTimestamp` INTEGER NOT NULL, `completedBy` TEXT NOT NULL, `isOutOfStock` INTEGER NOT NULL, PRIMARY KEY(`dateKey`, `taskId`))")

                db.execSQL("INSERT INTO `menu_items` (`id`, `name`, `category`, `price`, `description`, `isAvailable`) VALUES (1, 'Vietnamese Iced Coffee', 'ICED COFFEE', 99.0, 'Signature brew', 1)")
                db.execSQL("INSERT INTO `orders` (`orderId`, `customerName`, `contactNumber`, `totalAmount`, `timestamp`, `isMade`, `isPaid`, `isDelivered`, `syncStatus`, `notes`, `paymentMode`, `completedTimestamp`) VALUES ('FLT-20261124-0001', 'Walk-In', '', 99.0, 1700000000000, 1, 1, 1, 'SYNCED', '', 'UPI', 1700000005000)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()

        // Strictly validate Room migration 13 -> 17 WITHOUT fallbackToDestructiveMigration
        val migratedDb = Room.databaseBuilder(context, FiltrDatabase::class.java, dbName)
            .addMigrations(
                FiltrDatabase.MIGRATION_13_14,
                FiltrDatabase.MIGRATION_14_15,
                FiltrDatabase.MIGRATION_15_16,
                FiltrDatabase.MIGRATION_16_17
            )
            .allowMainThreadQueries()
            .build()

        try {
            val existingOrders = migratedDb.orderDao().getAllOrdersWithItems().first()
            assertEquals(1, existingOrders.size)
            assertEquals("FLT-20261124-0001", existingOrders.first().order.orderId)

            val existingMenu = migratedDb.menuDao().getAllMenuItems().first()
            assertEquals(1, existingMenu.size)
            assertEquals("Vietnamese Iced Coffee", existingMenu.first().name)

            // Populate & read v14 Kitchen Inventory, v15 Attendance, v16 Staff Roster tables
            migratedDb.kitchenInventoryDao().insertItems(KitchenInventorySeedCatalog.defaultItems())
            assertEquals(47, migratedDb.kitchenInventoryDao().getItemCountSync())

            migratedDb.attendanceDao().upsertRosterMember(
                StaffRosterEntity(
                    id = "rohan",
                    name = "Rohan",
                    roleTitle = "Barista",
                    shiftLabel = "Morning",
                    weeklyOffDayOfWeek = Calendar.SUNDAY,
                    joinedDateKey = "2026-11-01",
                    isActive = true,
                    updatedAt = 1000L
                )
            )
            migratedDb.attendanceDao().upsertLog(
                AttendanceLogEntity(
                    id = "2026-11-25_rohan",
                    dateKey = "2026-11-25",
                    monthKey = "2026-11",
                    staffName = "Rohan",
                    checkInTimestamp = 1700000000000L,
                    checkInDistanceMeters = 18.0,
                    updatedAt = 1000L
                )
            )
            assertEquals(1, migratedDb.attendanceDao().getAllRosterMembersSync().size)
            assertEquals(1, migratedDb.attendanceDao().getAllLogsSync().size)

            // Populate & read v17 kds_item_claims table
            migratedDb.kdsClaimDao().upsertClaim(
                KdsItemClaimEntity(
                    key = "FLT-20261124-0001_1_1",
                    orderId = "FLT-20261124-0001",
                    itemId = 1,
                    itemName = "Vietnamese Iced Coffee",
                    unitIndex = 1,
                    status = "MAKING",
                    staffName = "Rohan",
                    colorHex = "#16A34A",
                    updatedAt = 2000L
                )
            )
            assertEquals(1, migratedDb.kdsClaimDao().getAllClaimsSync().size)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `location helper safely returns failure without crashing when permission missing`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = KitchenLocationHelper.fetchCurrentLocation(context, timeoutMs = 1000L)
        assertTrue(result.isFailure)
    }

    @Test
    fun `kitchen inventory screen renders employee dashboard counting and manager hub safely`() {
        val items = KitchenInventorySeedCatalog.defaultItems()
        val sampleSummary = KitchenInventoryDashboardSummary(
            dateKey = "2026-11-25",
            formattedDate = "Wednesday, 25 Nov 2026",
            staffName = "Rohan",
            totalActiveItems = items.size,
            openingShift = KitchenInventoryShiftEntity(
                id = "2026-11-25_OPENING",
                dateKey = "2026-11-25",
                shiftType = "OPENING",
                status = "IN_PROGRESS",
                staffName = "Rohan"
            ),
            closingShift = null,
            openingCheckedCount = 1,
            closingCheckedCount = 0,
            lowStockCount = 0,
            outOfStockCount = 0,
            openingPendingSections = KitchenSection.entries.filter { it != KitchenSection.OUTSIDE_FREEZER },
            closingPendingSections = KitchenSection.entries.filter { it != KitchenSection.OUTSIDE_FREEZER },
            reorderList = emptyList(),
            varianceRows = emptyList()
        )

        var route by mutableStateOf<KitchenInventoryViewRoute>(
            KitchenInventoryViewRoute.SectionCounting(
                shiftType = KitchenShiftType.OPENING,
                sectionId = KitchenSection.DEEP_FREEZER.id,
                selectedItemId = "df_ice_cream"
            )
        )

        composeTestRule.setContent {
            FiltrTheme {
                KitchenInventoryScreen(
                    isManager = true,
                    summary = sampleSummary,
                    route = route,
                    activeItems = items,
                    allItems = items,
                    entriesToday = emptyList(),
                    allShifts = listOfNotNull(sampleSummary.openingShift),
                    allEntries = listOf(
                        KitchenInventoryEntryEntity(
                            shiftId = "2026-11-25_OPENING",
                            dateKey = "2026-11-25",
                            shiftType = "OPENING",
                            itemId = "df_ice_cream",
                            itemNameSnapshot = "Ice Cream",
                            sectionIdSnapshot = KitchenSection.DEEP_FREEZER.id,
                            unitLabelSnapshot = "tubs",
                            countingModeSnapshot = "SEALED_AND_PERCENTAGE",
                            packSizeSnapshot = 1.0,
                            sealedQty = 2,
                            openedQty = 1,
                            openedRemainingQty = 50.0,
                            totalUsableQty = 2.5,
                            isChecked = true
                        )
                    ),
                    previousClosingShift = null,
                    previousClosingEntries = emptyList(),
                    onNavigate = { route = it },
                    onStartOrResumeShift = {},
                    onOpenFirstUnchecked = { _, _ -> },
                    onSaveItemAndAdvance = { _, _, _, _, _, _, _, _, _ -> },
                    onSubmitShift = { _, _ -> },
                    onSaveItemConfig = { _, _, _, _, _, _, _, _, _, _, _ -> },
                    onRefresh = {}
                )
            }
        }

        // Verify Ice Cream percentage button & Save & Next render cleanly
        composeTestRule.onNodeWithText("50%").assertExists().performClick()
        composeTestRule.onNodeWithText("Save & Next").assertExists()

        // Switch cleanly into Manager Hub and verify sub-tabs render without crashing
        composeTestRule.onNodeWithText("Manager").performClick()
        composeTestRule.onNodeWithText("Configure").performClick()
        composeTestRule.onNodeWithText("Add Item").assertExists()
    }

    @Test
    fun `attendance screen renders staff check-in manager roll call and roster editor safely`() {
        val rohanRoster = StaffRosterEntity(
            id = "rohan",
            name = "Rohan",
            roleTitle = "Barista",
            shiftLabel = "Morning",
            weeklyOffDayOfWeek = Calendar.SUNDAY,
            joinedDateKey = "2026-11-01",
            isActive = true,
            updatedAt = 1000L
        )
        val ashaRoster = StaffRosterEntity(
            id = "asha",
            name = "Asha",
            roleTitle = "Kitchen",
            shiftLabel = "Full Day",
            weeklyOffDayOfWeek = 0,
            joinedDateKey = "2026-11-01",
            isActive = true,
            updatedAt = 1000L
        )
        val rohanLog = AttendanceLogEntity(
            id = "2026-11-25_rohan",
            dateKey = "2026-11-25",
            monthKey = "2026-11",
            staffName = "Rohan",
            checkInTimestamp = 1700000000000L,
            checkInDistanceMeters = 22.0,
            updatedAt = 1000L
        )
        val rohanSummary = AttendanceCalculator.buildMonthlySummary(
            monthKey = "2026-11",
            staffName = "Rohan",
            logs = listOf(rohanLog),
            trackingStartDateKey = "2026-11-01",
            todayDateKey = "2026-11-25",
            weeklyOffDayOfWeek = Calendar.SUNDAY
        )
        val ashaSummary = AttendanceCalculator.buildMonthlySummary(
            monthKey = "2026-11",
            staffName = "Asha",
            logs = emptyList(),
            trackingStartDateKey = "2026-11-01",
            todayDateKey = "2026-11-25"
        )
        val rollCall = listOf(
            TeamRollCallRow(rohanRoster, rohanLog, DayAttendanceStatus.ON_DUTY, rohanSummary),
            TeamRollCallRow(ashaRoster, null, DayAttendanceStatus.ABSENT, ashaSummary)
        )

        composeTestRule.setContent {
            FiltrTheme {
                AttendanceScreen(
                    isManager = true,
                    currentStaffName = "Rohan",
                    storeConfig = StoreGeofenceConfig(12.9716, 77.5946, 100.0, "FILTR Coffee", 1000L),
                    hasLocationPermission = true,
                    isFetchingLocation = false,
                    currentLocation = LocationSample(12.9716, 77.5946, 10f, 1700000000000L),
                    geofenceResult = GeofenceCheckResult(true, 12.0, 100.0),
                    locationError = null,
                    selectedMonthKey = "2026-11",
                    availableStaffNames = listOf("Asha", "Rohan"),
                    selectedManagerStaffName = "Rohan",
                    personalSummary = rohanSummary,
                    managerSummary = rohanSummary,
                    todayPersonalLog = rohanLog,
                    onRequestLocationPermission = {},
                    onRefreshLocationAndSync = {},
                    onCheckIn = {},
                    onCheckOut = {},
                    onSelectMonth = {},
                    onSelectManagerStaff = {},
                    onSetStoreLocationHere = {},
                    onChangeRadiusMeters = {},
                    staffRoster = listOf(ashaRoster, rohanRoster),
                    teamRollCall = rollCall,
                    selectedCheckInStaffName = "Rohan",
                    onSelectCheckInStaff = {},
                    onSaveStaffRosterMember = { _, _, _, _, _, _, _ -> }
                )
            }
        }

        // Staff View renders Check-Out + shared-phone switcher cleanly
        composeTestRule.onNodeWithText("CHECK OUT (END SHIFT)").assertExists()

        // Manager View -> Roll Call -> Roster & GPS -> Add Staff Dialog
        composeTestRule.onNodeWithText("Manager View").performClick()
        composeTestRule.onNodeWithText("Roll Call").assertExists()
        composeTestRule.onNodeWithText("Roster & GPS").performClick()
        composeTestRule.onNodeWithText("Add Staff").performClick()
        composeTestRule.onNodeWithText("Add / Edit Staff Member").assertExists()
    }

    @Test
    fun `kds screen renders fifo queue switches barista and advances two tap claim safely`() {
        val order = OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-20261126-1040",
                customerName = "Aarav",
                contactNumber = "",
                totalAmount = 140.0,
                timestamp = 1_700_000_000_000L,
                isMade = false,
                isPaid = true,
                isDelivered = false,
                syncStatus = "SYNCED",
                notes = "Extra shot",
                paymentMode = "UPI"
            ),
            items = listOf(
                OrderItemEntity(
                    id = 11,
                    orderId = "FLT-20261126-1040",
                    menuItemId = 1,
                    itemName = "Iced Americano",
                    itemPrice = 140.0,
                    quantity = 1,
                    subtotal = 140.0,
                    completedQuantity = 0
                )
            )
        )
        val somesh = StaffRosterEntity(id = "somesh", name = "Somesh", colorHex = "#16A34A")
        val kanha = StaffRosterEntity(id = "kanha", name = "Kanha", colorHex = "#DC2626")
        val rosterMap = mapOf(
            AttendanceCalculator.normalizeStaffKey("Somesh") to somesh,
            AttendanceCalculator.normalizeStaffKey("Kanha") to kanha
        )

        var activeBarista by mutableStateOf("Somesh")
        var claimsMap by mutableStateOf<Map<String, KdsItemClaimEntity>>(emptyMap())

        composeTestRule.setContent {
            FiltrTheme {
                val queue = KdsQueueBuilder.buildFifoPendingQueue(
                    orders = listOf(order),
                    claimsByKey = claimsMap,
                    rosterByNormalizedName = rosterMap
                )
                KdsScreen(
                    queueItems = queue,
                    availableBaristas = listOf("Somesh", "Kanha"),
                    staffRoster = listOf(somesh, kanha),
                    activeBaristaName = activeBarista,
                    paidOnlyFilter = false,
                    isStaffMode = true,
                    onSelectBarista = { activeBarista = it },
                    onTogglePaidOnlyFilter = {},
                    onTapQueueItem = { unit ->
                        val existing = claimsMap[unit.claimKey]
                        claimsMap = if (existing == null) {
                            mapOf(
                                unit.claimKey to KdsItemClaimEntity(
                                    key = unit.claimKey,
                                    orderId = unit.orderId,
                                    itemId = unit.itemId,
                                    itemName = unit.itemName,
                                    unitIndex = unit.unitIndex,
                                    status = "MAKING",
                                    staffName = activeBarista,
                                    colorHex = "#DC2626",
                                    updatedAt = 2_000L
                                )
                            )
                        } else {
                            mapOf(
                                unit.claimKey to existing.copy(status = "MADE", updatedAt = 3_000L)
                            )
                        }
                    },
                    onUnclaimQueueItem = {}
                )
            }
        }

        // Switch active barista to Kanha, Tap 1 claims card, Tap 2 completes queue
        composeTestRule.onNodeWithText("Kanha").performClick()
        composeTestRule.onNodeWithText("Iced Americano").performClick()
        composeTestRule.onNodeWithText("Kanha • Tap Done").assertExists()
        composeTestRule.onNodeWithText("Iced Americano").performClick()
        composeTestRule.onNodeWithText("All orders are prepared!").assertExists()
    }

    @Test
    fun `orders screen displays live making and made barista attribution badges safely`() {
        val now = System.currentTimeMillis()
        val order = OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-20261126-1055",
                customerName = "Walk-in",
                contactNumber = "",
                totalAmount = 240.0,
                timestamp = now,
                isMade = false,
                isPaid = true,
                isDelivered = false,
                syncStatus = "SYNCED",
                notes = "",
                paymentMode = "CASH"
            ),
            items = listOf(
                OrderItemEntity(
                    id = 21,
                    orderId = "FLT-20261126-1055",
                    menuItemId = 1,
                    itemName = "Cold Brew",
                    itemPrice = 120.0,
                    quantity = 2,
                    subtotal = 240.0,
                    completedQuantity = 1
                )
            )
        )
        val claims = mapOf(
            KdsQueueBuilder.buildClaimKey("FLT-20261126-1055", 21, 1) to KdsItemClaimEntity(
                key = KdsQueueBuilder.buildClaimKey("FLT-20261126-1055", 21, 1),
                orderId = "FLT-20261126-1055",
                itemId = 21,
                itemName = "Cold Brew",
                unitIndex = 1,
                status = "MADE",
                staffName = "Somesh",
                colorHex = "#16A34A",
                updatedAt = now
            ),
            KdsQueueBuilder.buildClaimKey("FLT-20261126-1055", 21, 2) to KdsItemClaimEntity(
                key = KdsQueueBuilder.buildClaimKey("FLT-20261126-1055", 21, 2),
                orderId = "FLT-20261126-1055",
                itemId = 21,
                itemName = "Cold Brew",
                unitIndex = 2,
                status = "MAKING",
                staffName = "Kanha",
                colorHex = "#DC2626",
                updatedAt = now
            )
        )

        composeTestRule.setContent {
            FiltrTheme {
                OrdersScreen(
                    state = BillingUiState(statusFilter = "PENDING", ordersDateFilter = "ALL"),
                    ordersList = listOf(order),
                    kdsClaimsByKey = claims,
                    isStaffMode = false,
                    onFilterChanged = {},
                    onToggleMade = {},
                    onTogglePaid = {},
                    onToggleDelivered = {},
                    onSyncOrder = {},
                    onDeleteOrder = {},
                    onViewReceipt = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Made by Somesh ✓").assertExists()
        composeTestRule.onNodeWithText("Making: Kanha").assertExists()
    }
}
