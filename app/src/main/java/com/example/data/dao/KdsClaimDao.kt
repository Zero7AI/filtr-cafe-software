package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.KdsItemClaimEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KdsClaimDao {

    @Query("SELECT * FROM kds_item_claims ORDER BY updatedAt DESC")
    fun getAllClaims(): Flow<List<KdsItemClaimEntity>>

    @Query("SELECT * FROM kds_item_claims ORDER BY updatedAt DESC")
    suspend fun getAllClaimsSync(): List<KdsItemClaimEntity>

    @Query("SELECT * FROM kds_item_claims WHERE orderId = :orderId ORDER BY itemId ASC, unitIndex ASC")
    suspend fun getClaimsForOrder(orderId: String): List<KdsItemClaimEntity>

    @Query("SELECT * FROM kds_item_claims WHERE `key` = :key LIMIT 1")
    suspend fun getClaim(key: String): KdsItemClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: KdsItemClaimEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaims(claims: List<KdsItemClaimEntity>)

    @Query("DELETE FROM kds_item_claims WHERE updatedAt < :olderThanMs AND status = 'MADE'")
    suspend fun deleteOldMadeClaims(olderThanMs: Long)
}
