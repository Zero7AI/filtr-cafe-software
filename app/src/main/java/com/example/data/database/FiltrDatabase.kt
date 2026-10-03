package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CustomerDao
import com.example.data.dao.FixedCostDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.MenuDao
import com.example.data.dao.OrderDao
import com.example.data.dao.UpiAccountDao
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
        InventoryUsageEntity::class, CustomerEntity::class, FixedCostEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class FiltrDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    abstract fun upiAccountDao(): UpiAccountDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun fixedCostDao(): FixedCostDao

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

        fun getDatabase(context: Context, scope: CoroutineScope): FiltrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FiltrDatabase::class.java,
                    "filtr_coffee_db"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
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
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialFixedCosts(database.fixedCostDao())
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
    }
}
