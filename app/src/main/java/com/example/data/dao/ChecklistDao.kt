package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ChecklistCompletionEntity
import com.example.data.entity.ChecklistTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_tasks ORDER BY sectionId ASC, orderIndex ASC")
    fun getAllTasks(): Flow<List<ChecklistTaskEntity>>

    @Query("SELECT * FROM checklist_tasks ORDER BY sectionId ASC, orderIndex ASC")
    suspend fun getAllTasksSync(): List<ChecklistTaskEntity>

    @Query("SELECT * FROM checklist_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): ChecklistTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ChecklistTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<ChecklistTaskEntity>)

    @Delete
    suspend fun deleteTask(task: ChecklistTaskEntity)

    @Query("DELETE FROM checklist_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("SELECT * FROM checklist_completions WHERE dateKey = :dateKey")
    fun getCompletionsForDate(dateKey: String): Flow<List<ChecklistCompletionEntity>>

    @Query("SELECT * FROM checklist_completions WHERE dateKey = :dateKey")
    suspend fun getCompletionsForDateSync(dateKey: String): List<ChecklistCompletionEntity>

    @Query("SELECT * FROM checklist_completions WHERE dateKey = :dateKey AND taskId = :taskId LIMIT 1")
    suspend fun getCompletion(dateKey: String, taskId: String): ChecklistCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCompletion(completion: ChecklistCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCompletions(completions: List<ChecklistCompletionEntity>)

    @Query("DELETE FROM checklist_completions WHERE dateKey = :dateKey AND taskId = :taskId")
    suspend fun deleteCompletion(dateKey: String, taskId: String)
}
