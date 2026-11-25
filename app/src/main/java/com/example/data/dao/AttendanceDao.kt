package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AttendanceLogEntity
import com.example.data.entity.StaffRosterEntity
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

    // ==================== STAFF ROSTER ====================

    @Query("SELECT * FROM staff_roster ORDER BY isActive DESC, name ASC")
    fun getAllRosterMembers(): Flow<List<StaffRosterEntity>>

    @Query("SELECT * FROM staff_roster WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveRosterMembers(): Flow<List<StaffRosterEntity>>

    @Query("SELECT * FROM staff_roster ORDER BY isActive DESC, name ASC")
    suspend fun getAllRosterMembersSync(): List<StaffRosterEntity>

    @Query("SELECT * FROM staff_roster WHERE id = :id LIMIT 1")
    suspend fun getRosterMemberById(id: String): StaffRosterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRosterMember(member: StaffRosterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRosterMembers(members: List<StaffRosterEntity>)
}
