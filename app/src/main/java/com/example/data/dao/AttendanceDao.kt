package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AttendanceLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM staff_attendance_logs WHERE monthKey = :monthKey ORDER BY dateKey DESC, updatedAt DESC")
    fun getLogsForMonth(monthKey: String): Flow<List<AttendanceLogEntity>>

    @Query("SELECT * FROM staff_attendance_logs WHERE monthKey = :monthKey ORDER BY dateKey DESC, updatedAt DESC")
    suspend fun getLogsForMonthSync(monthKey: String): List<AttendanceLogEntity>

    @Query("SELECT * FROM staff_attendance_logs ORDER BY dateKey DESC, updatedAt DESC")
    fun getAllLogs(): Flow<List<AttendanceLogEntity>>

    @Query("SELECT * FROM staff_attendance_logs ORDER BY dateKey DESC, updatedAt DESC")
    suspend fun getAllLogsSync(): List<AttendanceLogEntity>

    @Query("SELECT * FROM staff_attendance_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: String): AttendanceLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: AttendanceLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<AttendanceLogEntity>)
}
