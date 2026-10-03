package com.example.kitchen.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kitchen.data.dao.CustomerDao
import com.example.kitchen.data.dao.MenuDao
import com.example.kitchen.data.dao.OrderDao
import com.example.kitchen.data.dao.UpiAccountDao
import com.example.kitchen.data.entity.CustomerEntity
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.UpiAccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MenuItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        UpiAccountEntity::class,
        CustomerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KitchenDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    abstract fun upiAccountDao(): UpiAccountDao
    abstract fun customerDao(): CustomerDao

    companion object {
        @Volatile
        private var INSTANCE: KitchenDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): KitchenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KitchenDatabase::class.java,
                    "filtr_kitchen_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(KitchenDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class KitchenDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialMenu(database.menuDao())
                }
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
                MenuItemEntity(name = "Bombay", category = "SANDWICH", price = 89.0, description = "Fried Meshed Aaloo + Cheese"),
                MenuItemEntity(name = "Cheese Corn", category = "SANDWICH", price = 99.0, description = "Sweet Corn + Cheese"),
                MenuItemEntity(name = "Caramalized Onion", category = "SANDWICH", price = 109.0, description = "Caramel Onion + Cheese"),
                MenuItemEntity(name = "Paneer Sandwich", category = "SANDWICH", price = 119.0, description = "Paneer + Olive + Onion + Cucumber")
            )
            menuDao.insertAll(initialItems)
        }
    }
}
