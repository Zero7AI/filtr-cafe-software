package com.example.kitchen.data.dao

import androidx.room.*
import com.example.kitchen.data.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' ORDER BY lastOrderTimestamp DESC LIMIT 10")
    suspend fun searchCustomers(query: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("""
        UPDATE customers 
        SET totalOrders = totalOrders + 1, 
            totalSpent = totalSpent + :amount, 
            lastOrderTimestamp = :timestamp,
            contactNumber = CASE WHEN :contact != '' THEN :contact ELSE contactNumber END
        WHERE LOWER(name) = LOWER(:name)
    """)
    suspend fun incrementOrderStats(name: String, amount: Double, timestamp: Long, contact: String = "")
}
