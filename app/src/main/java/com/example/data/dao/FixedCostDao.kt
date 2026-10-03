package com.example.data.dao

import androidx.room.*
import com.example.data.entity.FixedCostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FixedCostDao {

    @Query("SELECT * FROM fixed_costs ORDER BY monthKey DESC")
    fun getAllFixedCosts(): Flow<List<FixedCostEntity>>

    @Query("SELECT * FROM fixed_costs WHERE monthKey = :monthKey LIMIT 1")
    suspend fun getFixedCostByMonth(monthKey: String): FixedCostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cost: FixedCostEntity): Long

    @Query("DELETE FROM fixed_costs WHERE monthKey = :monthKey")
    suspend fun deleteByMonth(monthKey: String)
}
