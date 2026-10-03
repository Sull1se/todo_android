package com.example.data.transfer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskList
import com.example.data.TodoRepository
import com.example.ui.TodoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import android.net.Uri
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataTransferIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var transferManager: DataTransferManager
    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        transferManager = DataTransferManager(context)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = context.getSharedPreferences("transfer_test_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()
        viewModel = TodoViewModel(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testExportAndOverwriteRoundTrip() = runBlocking {
        // 1. Seed initial data
        val list1Id = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "工作", themeColor = 0xFF111111L, displayOrder = 1, completionMode = 1, showInSummary = true)
        ).toInt()
        val list2Id = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "生活", themeColor = 0xFF222222L, displayOrder = 2, completionMode = 0, showInSummary = false)
        ).toInt()

        database.todoDao().insertTask(
            Task(id = 0, listId = list1Id, title = "工作任务", content = "工作内容", isCompleted = false, isFlagged = true, displayOrder = 1)
        )
        database.todoDao().insertTask(
            Task(id = 0, listId = list2Id, title = "生活任务", content = "生活内容", isCompleted = true, isFlagged = false, displayOrder = 2)
        )
        database.todoDao().insertTask(
            Task(id = 0, listId = list2Id, title = "补充任务", content = "补充内容", isCompleted = false, isFlagged = false, displayOrder = 3)
        )

        // 2. Export
        val backup = repository.exportBackup("2.0.0")
        assertEquals(2, backup.lists.size)
        assertEquals(3, backup.tasks.size)
        assertEquals(2, backup.formatVersion)

        // 3. Clear DB and add noise
        database.todoDao().deleteAllTasks()
        database.todoDao().deleteAllTaskLists()
        database.todoDao().insertTaskList(TaskList(id = 0, name = "临时噪音", displayOrder = 99))

        // 4. Overwrite import
        repository.importBackup(backup, isOverwrite = true)

        // 5. Verify restored data
        val restoredLists = repository.getAllTaskListsSnapshot()
        val restoredTasks = repository.getAllTasksSnapshot()

        assertEquals(2, restoredLists.size)
        assertEquals("工作", restoredLists[0].name)
        assertEquals(1, restoredLists[0].displayOrder)
        assertEquals(1, restoredLists[0].completionMode)
        assertTrue(restoredLists[0].showInSummary)

        assertEquals("生活", restoredLists[1].name)
        assertEquals(2, restoredLists[1].displayOrder)
        assertEquals(0, restoredLists[1].completionMode)
        assertFalse(restoredLists[1].showInSummary)

        assertEquals(3, restoredTasks.size)
        val taskWork = restoredTasks.find { it.title == "工作任务" }
        assertNotNull(taskWork)
        assertEquals("工作内容", taskWork!!.content)
        assertTrue(taskWork.isFlagged)
        assertFalse(taskWork.isCompleted)
        assertEquals(restoredLists[0].id, taskWork.listId) // Foreign key mapped to new list1 ID

        val taskLife = restoredTasks.find { it.title == "生活任务" }
        assertNotNull(taskLife)
        assertEquals("生活内容", taskLife!!.content)
        assertFalse(taskLife.isFlagged)
        assertTrue(taskLife.isCompleted)
        assertEquals(restoredLists[1].id, taskLife.listId) // Foreign key mapped to new list2 ID

        val taskExtra = restoredTasks.find { it.title == "补充任务" }
        assertNotNull(taskExtra)
        assertEquals(restoredLists[1].id, taskExtra!!.listId)
    }

    @Test
    fun testAppendModeOrderingAndIdRemapping() = runBlocking {
        // 1. Initial DB with high display orders
        val origListId = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "原有清单", displayOrder = 10)
        ).toInt()
        val origTaskId = database.todoDao().insertTask(
            Task(id = 0, listId = origListId, title = "原有待办", content = "", displayOrder = 20)
        ).toInt()

        // 2. Prepare backup data with smaller orders
        val backup = BackupData(
            format = BackupData.BACKUP_FORMAT,
            formatVersion = 2,
            exportedAt = "2026-09-12T12:00:00Z",
            sourceAppVersion = "2.0.0",
            lists = listOf(
                TaskListBackupDto(id = 1, name = "导入清单A", themeColor = 0, displayOrder = 1, completionMode = 0, timestamp = 100, showInSummary = true),
                TaskListBackupDto(id = 2, name = "导入清单B", themeColor = 0, displayOrder = 2, completionMode = 0, timestamp = 200, showInSummary = true)
            ),
            tasks = listOf(
                TaskBackupDto(id = 11, listId = 1, title = "导入待办A", content = "", isCompleted = false, isFlagged = false, displayOrder = 5, timestamp = 100),
                TaskBackupDto(id = 12, listId = 2, title = "导入待办B", content = "", isCompleted = false, isFlagged = false, displayOrder = 6, timestamp = 200)
            )
        )

        // 3. Import in append mode
        repository.importBackup(backup, isOverwrite = false)

        // 4. Verify original records untouched
        val allLists = repository.getAllTaskListsSnapshot()
        val allTasks = repository.getAllTasksSnapshot()

        assertEquals(3, allLists.size)
        assertEquals(3, allTasks.size)

        val origList = allLists.find { it.name == "原有清单" }
        assertNotNull(origList)
        assertEquals(origListId, origList!!.id)
        assertEquals(10, origList.displayOrder)

        val origTask = allTasks.find { it.title == "原有待办" }
        assertNotNull(origTask)
        assertEquals(origTaskId, origTask!!.id)
        assertEquals(0, origTask.displayOrder)

        // Verify newly appended records have display orders strictly incremented after maximum for lists
        val newListA = allLists.find { it.name == "导入清单A" }
        val newListB = allLists.find { it.name == "导入清单B" }
        assertNotNull(newListA)
        assertNotNull(newListB)
        assertEquals(11, newListA!!.displayOrder) // base 10 + 1
        assertEquals(12, newListB!!.displayOrder) // base 10 + 2

        val newTaskA = allTasks.find { it.title == "导入待办A" }
        val newTaskB = allTasks.find { it.title == "导入待办B" }
        assertNotNull(newTaskA)
        assertNotNull(newTaskB)
        assertEquals(1, newTaskA!!.displayOrder)
        assertEquals(2, newTaskB!!.displayOrder)

        // Verify foreign keys correctly mapped to new list IDs
        assertEquals(newListA.id, newTaskA.listId)
        assertEquals(newListB.id, newTaskB.listId)
    }

    @Test
    fun testTransactionRollbackOnFailure() = runBlocking {
        // Seed initial data
        val origListId = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "初始数据", displayOrder = 1)
        ).toInt()

        // Backup has an invalid listId mapping to cause failure during insertTasks
        val faultyBackup = BackupData(
            format = BackupData.BACKUP_FORMAT,
            formatVersion = 2,
            exportedAt = "2026-09-12T12:00:00Z",
            sourceAppVersion = "2.0.0",
            lists = listOf(
                TaskListBackupDto(id = 1, name = "新列表", themeColor = 0, displayOrder = 1, completionMode = 0, timestamp = 100, showInSummary = true)
            ),
            tasks = listOf(
                // Note: listId 999 does not exist in lists
                TaskBackupDto(id = 1, listId = 999, title = "无效外键任务", content = "", isCompleted = false, isFlagged = false, displayOrder = 1, timestamp = 100)
            )
        )

        try {
            repository.importBackup(faultyBackup, isOverwrite = true)
        } catch (_: Exception) {
            // Expected to throw
        }

        // Database should be completely untouched by failed transaction
        val listsAfterRollback = repository.getAllTaskListsSnapshot()
        assertEquals(1, listsAfterRollback.size)
        assertEquals(origListId, listsAfterRollback[0].id)
        assertEquals("初始数据", listsAfterRollback[0].name)
    }

    @Test
    fun testViewModelStateResetOnOverwrite() = runBlocking {
        // Set up ViewModel state
        viewModel.selectList(99)
        viewModel.isSelectionMode.value = true
        viewModel.selectedTaskIds.value = setOf(1, 2, 3)

        val emptyBackup = BackupData(
            format = BackupData.BACKUP_FORMAT,
            formatVersion = 2,
            exportedAt = "2026-09-12T12:00:00Z",
            sourceAppVersion = "2.0.0",
            lists = emptyList(),
            tasks = emptyList()
        )

        viewModel.importBackup(emptyBackup, isOverwrite = true)

        // Verify state is reset
        assertEquals(-1, viewModel.selectedListId.value)
        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(viewModel.selectedTaskIds.value.isEmpty())
    }

    @Test
    fun testUriPreValidationRejectsInvalidBackupsAndPreservesDatabaseSnapshot() = runBlocking {
        // Pre-populate database with rich data: lists with different completionMode, showInSummary, tasks with content, flags, completion
        val list1Id = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "工作清单", themeColor = 0xFF111111L, displayOrder = 1, completionMode = 1, showInSummary = true)
        ).toInt()
        val list2Id = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "生活清单", themeColor = 0xFF222222L, displayOrder = 2, completionMode = 0, showInSummary = false)
        ).toInt()

        database.todoDao().insertTask(
            Task(id = 0, listId = list1Id, title = "待办A", content = "备注A", isCompleted = false, isFlagged = true, displayOrder = 1)
        )
        database.todoDao().insertTask(
            Task(id = 0, listId = list2Id, title = "待办B", content = "备注B", isCompleted = true, isFlagged = false, displayOrder = 2)
        )

        val beforeLists = repository.getAllTaskListsSnapshot()
        val beforeTasks = repository.getAllTasksSnapshot()
        assertEquals(2, beforeLists.size)
        assertEquals(2, beforeTasks.size)

        val invalidBackups = listOf(
            // 1. v1 缺 lists
            """{"format":"com.aistudio.todo.backup","formatVersion":1,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","tasks":[]}""",
            // 2. v1 缺 tasks
            """{"format":"com.aistudio.todo.backup","formatVersion":1,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","lists":[]}""",
            // 3. v1 均缺
            """{"format":"com.aistudio.todo.backup","formatVersion":1,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4"}""",
            // 4. v2 缺 lists
            """{"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","tasks":[]}""",
            // 5. v2 缺 tasks
            """{"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","lists":[]}""",
            // 6. v2 均缺
            """{"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4"}"""
        )

        for ((index, jsonContent) in invalidBackups.withIndex()) {
            val tempFile = File.createTempFile("test_invalid_$index", ".json", context.cacheDir)
            tempFile.writeText(jsonContent)
            val uri = Uri.fromFile(tempFile)

            try {
                // Test append branch (only call import on success)
                val resultAppend = transferManager.readAndValidateFromUri(uri)
                assertTrue("Invalid input $index should fail validation", resultAppend.isFailure)
                val errAppend = resultAppend.exceptionOrNull()?.message
                assertNotNull(errAppend)
                assertTrue(errAppend!!.contains("缺少 lists 清单集合") || errAppend.contains("缺少 tasks 待办集合"))
                // Since validation failed, import is not called; verify DB is completely unchanged
                val afterListsAppend = repository.getAllTaskListsSnapshot()
                val afterTasksAppend = repository.getAllTasksSnapshot()
                assertEquals(beforeLists, afterListsAppend)
                assertEquals(beforeTasks, afterTasksAppend)

                // Test overwrite branch (only call import on success)
                val resultOverwrite = transferManager.readAndValidateFromUri(uri)
                assertTrue("Invalid input $index should fail validation", resultOverwrite.isFailure)
                val errOverwrite = resultOverwrite.exceptionOrNull()?.message
                assertNotNull(errOverwrite)
                assertTrue(errOverwrite!!.contains("缺少 lists 清单集合") || errOverwrite.contains("缺少 tasks 待办集合"))
                val afterListsOverwrite = repository.getAllTaskListsSnapshot()
                val afterTasksOverwrite = repository.getAllTasksSnapshot()
                assertEquals(beforeLists, afterListsOverwrite)
                assertEquals(beforeTasks, afterTasksOverwrite)
            } finally {
                tempFile.delete()
            }
        }
    }

    @Test
    fun testExplicitEmptyArraysImportViaUri() = runBlocking {
        val emptyJson = """
            {"format":"com.aistudio.todo.backup","formatVersion":2,"exportedAt":"2026-09-22T00:00:00Z","sourceAppVersion":"2.0.4","lists":[],"tasks":[]}
        """.trimIndent()
        val tempFile = File.createTempFile("test_empty_valid", ".json", context.cacheDir)
        tempFile.writeText(emptyJson)
        val uri = Uri.fromFile(tempFile)

        try {
            val result = transferManager.readAndValidateFromUri(uri)
            assertTrue(result.isSuccess)
            val (backup, returnedTempFile) = result.getOrThrow()
            transferManager.cleanTempFile(returnedTempFile)
            assertEquals(0, backup.lists.size)
            assertEquals(0, backup.tasks.size)

            // Seed DB
            val listId = database.todoDao().insertTaskList(
                TaskList(id = 0, name = "测试", displayOrder = 1)
            ).toInt()
            database.todoDao().insertTask(
                Task(id = 0, listId = listId, title = "测试任务", content = "", displayOrder = 1)
            )
            assertEquals(1, repository.getAllTaskListsSnapshot().size)

            // Overwrite with explicit empty backup clears DB in memory
            viewModel.importBackup(backup, isOverwrite = true)
            assertEquals(0, repository.getAllTaskListsSnapshot().size)
            assertEquals(0, repository.getAllTasksSnapshot().size)
        } finally {
            tempFile.delete()
        }
    }
}
