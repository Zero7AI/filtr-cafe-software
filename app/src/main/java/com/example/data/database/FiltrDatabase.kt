package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ChecklistDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.FixedCostDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.MenuDao
import com.example.data.dao.OrderDao
import com.example.data.dao.UpiAccountDao
import com.example.data.entity.ChecklistCompletionEntity
import com.example.data.entity.ChecklistTaskEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.FixedCostEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.UpiAccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MenuItemEntity::class, OrderEntity::class, OrderItemEntity::class, UpiAccountEntity::class,
        InventoryItemEntity::class, InventoryOrderEntity::class, InventoryOrderItemEntity::class,
        InventoryUsageEntity::class, CustomerEntity::class, FixedCostEntity::class,
        ChecklistTaskEntity::class, ChecklistCompletionEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class FiltrDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    abstract fun upiAccountDao(): UpiAccountDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun fixedCostDao(): FixedCostDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        @Volatile
        private var INSTANCE: FiltrDatabase? = null

        // Proper migration from v8 (no inventory) to v9 (with inventory tables)
        // This preserves all existing orders, menu items, UPI accounts
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create inventory_items table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL DEFAULT '',
                        `unit` TEXT NOT NULL DEFAULT 'kg',
                        `standardQuantity` REAL NOT NULL DEFAULT 1.0,
                        `defaultPrice` REAL NOT NULL DEFAULT 0.0,
                        `currentStock` REAL NOT NULL DEFAULT 0.0,
                        `isActive` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                // Create inventory_orders table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_orders` (
                        `orderId` TEXT NOT NULL PRIMARY KEY,
                        `timestamp` INTEGER NOT NULL,
                        `supplierName` TEXT NOT NULL DEFAULT '',
                        `totalAmount` REAL NOT NULL DEFAULT 0.0,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `syncStatus` TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """.trimIndent())

                // Create inventory_order_items table with FK to inventory_orders
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_order_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `quantity` REAL NOT NULL DEFAULT 0.0,
                        `unit` TEXT NOT NULL DEFAULT 'kg',
                        `unitPrice` REAL NOT NULL DEFAULT 0.0,
                        `subtotal` REAL NOT NULL DEFAULT 0.0,
                        FOREIGN KEY(`orderId`) REFERENCES `inventory_orders`(`orderId`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_order_items_orderId` ON `inventory_order_items` (`orderId`)")

                // Create inventory_usage table (recipes)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_usage` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menuItemId` INTEGER NOT NULL,
                        `menuItemName` TEXT NOT NULL,
                        `inventoryItemId` INTEGER NOT NULL,
                        `inventoryItemName` TEXT NOT NULL,
                        `quantityUsed` REAL NOT NULL,
                        `unit` TEXT NOT NULL DEFAULT 'g'
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `customers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `contactNumber` TEXT NOT NULL DEFAULT '',
                        `totalOrders` INTEGER NOT NULL DEFAULT 0,
                        `totalSpent` REAL NOT NULL DEFAULT 0.0,
                        `lastOrderTimestamp` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `fixed_costs` (
                        `monthKey` TEXT NOT NULL PRIMARY KEY,
                        `rent` REAL NOT NULL DEFAULT 9000.0,
                        `electricity` REAL NOT NULL DEFAULT 6000.0,
                        `staffSalaries` REAL NOT NULL DEFAULT 30000.0,
                        `staffCount` INTEGER NOT NULL DEFAULT 2,
                        `maidSalary` REAL NOT NULL DEFAULT 2000.0,
                        `miscellaneous` REAL NOT NULL DEFAULT 5000.0,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT OR IGNORE INTO `fixed_costs` 
                    (`monthKey`, `rent`, `electricity`, `staffSalaries`, `staffCount`, `maidSalary`, `miscellaneous`, `notes`, `updatedAt`)
                    VALUES ('2026-09', 9000.0, 6000.0, 30000.0, 2, 2000.0, 5000.0, 'Default September 2026 fixed overheads', $now)
                """.trimIndent())
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `fixed_costs` ADD COLUMN `staffMembersJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `checklist_tasks` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sectionId` TEXT NOT NULL,
                        `sectionName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `isDefault` INTEGER NOT NULL DEFAULT 1,
                        `orderIndex` INTEGER NOT NULL DEFAULT 0,
                        `isWeeklyMondayOnly` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `checklist_completions` (
                        `dateKey` TEXT NOT NULL,
                        `taskId` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `completedTimestamp` INTEGER NOT NULL DEFAULT 0,
                        `completedBy` TEXT NOT NULL DEFAULT 'Staff',
                        `isOutOfStock` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`dateKey`, `taskId`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): FiltrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FiltrDatabase::class.java,
                    "filtr_coffee_db"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration() // Last resort for ancient versions
                .addCallback(FiltrDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class FiltrDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialMenu(database.menuDao())
                    populateInitialUpiAccount(database.upiAccountDao())
                    populateInitialFixedCosts(database.fixedCostDao())
                    populateInitialChecklistTasks(database.checklistDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialFixedCosts(database.fixedCostDao())
                    populateInitialChecklistTasks(database.checklistDao())
                }
            }
        }

        suspend fun populateInitialFixedCosts(fixedCostDao: FixedCostDao) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            val currentMonth = sdf.format(java.util.Date())
            if (fixedCostDao.getFixedCostByMonth(currentMonth) == null) {
                val defaultStaffJson = """[{"name":"Barista 1","salary":15000.0},{"name":"Barista 2","salary":15000.0}]"""
                fixedCostDao.insertOrUpdate(
                    FixedCostEntity(
                        monthKey = currentMonth,
                        rent = 9000.0,
                        electricity = 6000.0,
                        staffSalaries = 30000.0,
                        staffCount = 2,
                        maidSalary = 2000.0,
                        miscellaneous = 5000.0,
                        notes = "Default fixed overheads for $currentMonth",
                        staffMembersJson = defaultStaffJson
                    )
                )
            }
        }

        suspend fun populateInitialUpiAccount(upiAccountDao: UpiAccountDao) {
            if (upiAccountDao.getAccountCount() == 0) {
                upiAccountDao.insert(
                    UpiAccountEntity(
                        vpa = "9907839311@ybl",
                        payeeName = "FILTR Coffee Store",
                        isDefault = true,
                        note = "FILTR Coffee Payment"
                    )
                )
            }
        }

        suspend fun populateInitialMenu(menuDao: MenuDao) {
            val initialItems = listOf(
                // ICED COFFEE
                MenuItemEntity(name = "Americano", category = "ICED COFFEE", price = 79.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Vanila Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Hazelnut Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Caramel Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "Vietnamese Iced Coffee", category = "ICED COFFEE", price = 99.0, description = "Milk + Whipped Cream + Flavour + Coffee"),
                MenuItemEntity(name = "OG Vietnamese", category = "ICED COFFEE", price = 109.0, description = "Milk + Whipped Cream + Flavour + Coffee"),

                // FRAPPE
                MenuItemEntity(name = "Just Frappe", category = "FRAPPE", price = 109.0, description = "Ice Cream + Coffee + Flavour"),
                MenuItemEntity(name = "Vanila Frappe", category = "FRAPPE", price = 129.0, description = "Ice Cream + Coffee + Flavour"),
                MenuItemEntity(name = "Hazelnut Frappe", category = "FRAPPE", price = 129.0, description = "Ice Cream + Coffee + Flavour"),
                MenuItemEntity(name = "Caramel Frappe", category = "FRAPPE", price = 129.0, description = "Ice Cream + Coffee + Flavour"),

                // FRIES
                MenuItemEntity(name = "Salted Fries", category = "FRIES", price = 89.0, description = "Potato + Deep Fry"),
                MenuItemEntity(name = "Peri Peri Fries", category = "FRIES", price = 99.0, description = "Potato + Deep Fry"),

                // SANDWICH
                MenuItemEntity(name = "Bombay", category = "SANDWICH", price = 89.0, description = "Fried Meshed Aaloo + Cheese (Extra Cheese Slice ₹15)"),
                MenuItemEntity(name = "Cheese Corn", category = "SANDWICH", price = 99.0, description = "Sweet Corn + Cheese (Extra Cheese Slice ₹15)"),
                MenuItemEntity(name = "Caramalized Onion", category = "SANDWICH", price = 109.0, description = "Caramel Onion + Cheese (Extra Cheese Slice ₹15)"),
                MenuItemEntity(name = "Paneer Sandwich", category = "SANDWICH", price = 119.0, description = "Paneer + Olive + Onion + Cucumber (Extra Cheese Slice ₹15)")
            )
            menuDao.insertAll(initialItems)
        }

        suspend fun populateInitialChecklistTasks(checklistDao: ChecklistDao) {
            val existing = checklistDao.getAllTasksSync()
            if (existing.isNotEmpty()) return

            val defaultTasks = listOf(
                // 01. OPENING & DAILY PREPARATION
                ChecklistTaskEntity(
                    id = "task_open_1",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Switch ON counter lights & verify all lights are working",
                    description = "Check all bulbs, strip lights, and outdoor brand signage",
                    orderIndex = 1
                ),
                ChecklistTaskEntity(
                    id = "task_open_2",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Check refrigerator and deep freezer are ON & working",
                    description = "Verify cooling temperature display and power supply indicator",
                    orderIndex = 2
                ),
                ChecklistTaskEntity(
                    id = "task_open_3",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Deep Freezer Check: Ice cream, French fries, Ice, Mozzarella cheese, Whipped cream, Sweet corn",
                    description = "Ensure packaging is intact and required stock is readily accessible",
                    orderIndex = 3
                ),
                ChecklistTaskEntity(
                    id = "task_open_4",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Refrigerator Check: Milk, Butter, Paneer, Cucumber, Onion",
                    description = "Take out butter from refrigerator as per daily required quantity",
                    orderIndex = 4
                ),
                ChecklistTaskEntity(
                    id = "task_open_5",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Prepare milk mixture (2 Litres milk + 500g whipped cream)",
                    description = "Follow exact recipe ratio. Mix thoroughly and store cold",
                    orderIndex = 5
                ),
                ChecklistTaskEntity(
                    id = "task_open_6",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Prepare coffee extract (1kg coffee + 5 Litres water)",
                    description = "Brew as per standard extraction SOP",
                    orderIndex = 6
                ),
                ChecklistTaskEntity(
                    id = "task_open_7",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Prepare coffee recipe mixture ready for billing rush",
                    description = "Check consistency and label containers with date/time",
                    orderIndex = 7
                ),
                ChecklistTaskEntity(
                    id = "task_open_8",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Fill all sauce dispenser bottles & seasonings",
                    description = "Chocolate, caramel, peri-peri, mayo and mustard bottles filled",
                    orderIndex = 8
                ),
                ChecklistTaskEntity(
                    id = "task_open_9",
                    sectionId = "01_OPENING",
                    sectionName = "01. Opening & Daily Preparation",
                    title = "Check cucumber and onion cutting / availability",
                    description = "Freshly sliced vegetables ready for sandwich assembly",
                    orderIndex = 9
                ),

                // 02. CLEANING & STORE HYGIENE
                ChecklistTaskEntity(
                    id = "task_clean_1",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Clean main kitchen counter & sandwich preparation area",
                    description = "Sanitize chopping boards, slicers, and prep surfaces",
                    orderIndex = 1
                ),
                ChecklistTaskEntity(
                    id = "task_clean_2",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Clean all shelves & keep all ingredients in proper positions",
                    description = "Organize syrup bottles, coffee beans, cups and lids",
                    orderIndex = 2
                ),
                ChecklistTaskEntity(
                    id = "task_clean_3",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Clean stove, burners, fryer & surrounding area",
                    description = "Wipe off oil splatters and clean drip trays",
                    orderIndex = 3
                ),
                ChecklistTaskEntity(
                    id = "task_clean_4",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Keep dustbins clean, emptied if full & replace liner",
                    description = "Maintain separate wet and dry waste bins",
                    orderIndex = 4
                ),
                ChecklistTaskEntity(
                    id = "task_clean_5",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Sweep & mop the kitchen floor — keep completely dry",
                    description = "Prevent slip hazards around the prep and billing stations",
                    orderIndex = 5
                ),
                ChecklistTaskEntity(
                    id = "task_clean_6",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Outside cleaning: Sweep floor (Jhadu), clean tables & chairs",
                    description = "Sanitize customer dining tables and outside seating area",
                    orderIndex = 6
                ),
                ChecklistTaskEntity(
                    id = "task_clean_7",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Check front entrance / road: Pick up plastics & waste",
                    description = "Keep store perimeter clean and welcoming",
                    orderIndex = 7
                ),
                ChecklistTaskEntity(
                    id = "task_clean_8",
                    sectionId = "02_CLEANING",
                    sectionName = "02. Cleaning & Store Hygiene",
                    title = "Clean all fans & ventilation filters (Every Monday)",
                    description = "Weekly deep clean of wall fans, exhaust fans, and AC vents",
                    isWeeklyMondayOnly = true,
                    orderIndex = 8
                ),

                // 03. ROUTINE STORE CHECKS
                ChecklistTaskEntity(
                    id = "task_routine_1",
                    sectionId = "03_ROUTINE",
                    sectionName = "03. Routine Store Checks",
                    title = "12:00 PM Check — Kitchen, counter & dining tables clean",
                    description = "Afternoon peak preparation: wipe counters and clear plates",
                    orderIndex = 1
                ),
                ChecklistTaskEntity(
                    id = "task_routine_2",
                    sectionId = "03_ROUTINE",
                    sectionName = "03. Routine Store Checks",
                    title = "2:00 PM Check — Check milk/ice stock & sweep dining area",
                    description = "Mid-day replenishment check and dustbin clearance",
                    orderIndex = 2
                ),
                ChecklistTaskEntity(
                    id = "task_routine_3",
                    sectionId = "03_ROUTINE",
                    sectionName = "03. Routine Store Checks",
                    title = "5:00 PM Check — Evening peak preparation & sauce fill",
                    description = "Prep counter for evening coffee orders",
                    orderIndex = 3
                ),
                ChecklistTaskEntity(
                    id = "task_routine_4",
                    sectionId = "03_ROUTINE",
                    sectionName = "03. Routine Store Checks",
                    title = "7:00 PM Check — Wipe tables & pick up outer road waste",
                    description = "Maintain clean customer entrance and outdoor sitting area",
                    orderIndex = 4
                ),
                ChecklistTaskEntity(
                    id = "task_routine_5",
                    sectionId = "03_ROUTINE",
                    sectionName = "03. Routine Store Checks",
                    title = "8:30 PM Check — Kitchen clean & stock levels review",
                    description = "Pre-closing prep and inventory check for night rush",
                    orderIndex = 5
                ),
                ChecklistTaskEntity(
                    id = "task_routine_6",
                    sectionId = "03_ROUTINE",
                    sectionName = "03. Routine Store Checks",
                    title = "9:00 PM Check — Final evening hygiene & prep stations",
                    description = "Clear used cups, wipe coffee machines and steamer wands",
                    orderIndex = 6
                ),

                // 04. CLOSING CHECKLIST
                ChecklistTaskEntity(
                    id = "task_close_1",
                    sectionId = "04_CLOSING",
                    sectionName = "04. Closing Checklist",
                    title = "Store all refrigerator & deep freezer items safely inside",
                    description = "Ice cream, fries, cheese, cream, milk, paneer, butter properly stored",
                    orderIndex = 1
                ),
                ChecklistTaskEntity(
                    id = "task_close_2",
                    sectionId = "04_CLOSING",
                    sectionName = "04. Closing Checklist",
                    title = "CRITICAL: Refrigerator and deep freezer switches remain ON",
                    description = "DO NOT switch off cooling appliances overnight",
                    orderIndex = 2
                ),
                ChecklistTaskEntity(
                    id = "task_close_3",
                    sectionId = "04_CLOSING",
                    sectionName = "04. Closing Checklist",
                    title = "Switch OFF all counter lights, display signage, fans & machines",
                    description = "Espresso grinder, sandwich press, induction stove OFF",
                    orderIndex = 3
                ),
                ChecklistTaskEntity(
                    id = "task_close_4",
                    sectionId = "04_CLOSING",
                    sectionName = "04. Closing Checklist",
                    title = "Kitchen deep cleaned, dustbins emptied & floor mopped dry",
                    description = "Cover all opened sauce containers and dry all utensils",
                    orderIndex = 4
                ),
                ChecklistTaskEntity(
                    id = "task_close_5",
                    sectionId = "04_CLOSING",
                    sectionName = "04. Closing Checklist",
                    title = "Water taps tightly closed & main entrance shutter/door locked",
                    description = "Verify plumbing is safe and cash drawer / premises secured",
                    orderIndex = 5
                )
            )

            checklistDao.insertTasks(defaultTasks)
        }
    }
}
