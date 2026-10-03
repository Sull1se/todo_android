package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM task_lists ORDER BY displayOrder ASC, timestamp ASC, id ASC")
    fun getAllTaskLists(): Flow<List<TaskList>>

    @Query("SELECT MAX(displayOrder) FROM task_lists")
    suspend fun getMaxListDisplayOrder(): Int?

    @Update
    suspend fun updateTaskLists(taskLists: List<TaskList>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskList(taskList: TaskList): Long

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY displayOrder ASC, timestamp ASC, id ASC")
    fun getTasksForList(listId: Int): Flow<List<Task>>

    @Query("SELECT t.* FROM tasks t INNER JOIN task_lists tl ON t.listId = tl.id WHERE tl.showInSummary = 1 ORDER BY t.displayOrder ASC, t.timestamp ASC, t.id ASC")
    fun getSummaryTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY displayOrder ASC, timestamp ASC, id ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?

    @Query("SELECT MAX(displayOrder) FROM tasks")
    suspend fun getMaxDisplayOrder(): Int?

    @Update
    suspend fun updateTaskList(taskList: TaskList)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteTaskList(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Update
    suspend fun updateTasks(tasks: List<Task>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Int)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteTasksByIds(ids: List<Int>)

    @Query("SELECT * FROM task_lists ORDER BY displayOrder ASC, timestamp ASC, id ASC")
    suspend fun getAllTaskListsSnapshot(): List<TaskList>

    @Query("SELECT * FROM tasks ORDER BY displayOrder ASC, timestamp ASC, id ASC")
    suspend fun getAllTasksSnapshot(): List<Task>

    @Query("SELECT t.* FROM tasks t INNER JOIN task_lists tl ON t.listId = tl.id WHERE tl.showInSummary = 1 ORDER BY t.displayOrder ASC, t.timestamp ASC, t.id ASC")
    suspend fun getSummaryTasksSnapshot(): List<Task>

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY displayOrder ASC, timestamp ASC, id ASC")
    suspend fun getTasksForListSnapshot(listId: Int): List<Task>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskLists(taskLists: List<TaskList>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(tasks: List<Task>): List<Long>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM task_lists")
    suspend fun deleteAllTaskLists()

    @Query("SELECT * FROM app_metadata WHERE id = 1")
    suspend fun getAppMetadata(): AppMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAppMetadata(metadata: AppMetadata)
}
