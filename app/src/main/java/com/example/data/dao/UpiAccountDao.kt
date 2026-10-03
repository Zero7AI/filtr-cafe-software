package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.entity.UpiAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpiAccountDao {
    @Query("SELECT * FROM upi_accounts ORDER BY isDefault DESC, id ASC")
    fun getAllUpiAccounts(): Flow<List<UpiAccountEntity>>

    @Query("SELECT * FROM upi_accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultUpiAccount(): UpiAccountEntity?

    @Query("SELECT COUNT(*) FROM upi_accounts")
    suspend fun getAccountCount(): Int

    @Query("SELECT * FROM upi_accounts ORDER BY id ASC LIMIT 1")
    suspend fun getFirstAccount(): UpiAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: UpiAccountEntity): Long

    @Update
    suspend fun update(account: UpiAccountEntity)

    @Delete
    suspend fun delete(account: UpiAccountEntity)

    @Query("UPDATE upi_accounts SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE upi_accounts SET isDefault = 1 WHERE id = :accountId")
    suspend fun markDefaultFlag(accountId: Int)

    @Transaction
    suspend fun setDefaultAccount(accountId: Int) {
        clearDefaultFlags()
        markDefaultFlag(accountId)
    }
}
