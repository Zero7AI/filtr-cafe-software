package com.example.kitchen.data.dao

import androidx.room.*
import com.example.kitchen.data.entity.UpiAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpiAccountDao {
    @Query("SELECT * FROM upi_accounts ORDER BY isDefault DESC, id ASC")
    fun getAllUpiAccounts(): Flow<List<UpiAccountEntity>>

    @Query("SELECT * FROM upi_accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultUpiAccount(): UpiAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: UpiAccountEntity): Long

    @Query("SELECT COUNT(*) FROM upi_accounts")
    suspend fun getAccountCount(): Int
}
