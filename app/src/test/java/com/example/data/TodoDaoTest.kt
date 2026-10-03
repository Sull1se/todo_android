package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TodoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.todoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryTaskList_andTasks() = runBlocking {
        val listId = dao.insertTaskList(TaskList(name = "测试列表", themeColor = 0xFF123456L, displayOrder = 0)).toInt()
        val taskId = dao.insertTask(Task(listId = listId, title = "测试待办", content = "正文", isCompleted = false, displayOrder = 0)).toInt()

        val lists = dao.getAllTaskLists().first()
        assertEquals(1, lists.size)
        assertEquals("测试列表", lists[0].name)

        val tasks = dao.getTasksForList(listId).first()
        assertEquals(1, tasks.size)
        assertEquals("测试待办", tasks[0].title)
        assertEquals(listId, tasks[0].listId)

        val taskById = dao.getTaskById(taskId)
        assertNotNull(taskById)
        assertEquals("测试待办", taskById!!.title)
    }

    @Test
    fun cascadeDelete_whenListDeleted_associatedTasksAreDeleted() = runBlocking {
        val listId = dao.insertTaskList(TaskList(name = "待删除列表", themeColor = 0L)).toInt()
        dao.insertTask(Task(listId = listId, title = "任务 1", content = ""))
        dao.insertTask(Task(listId = listId, title = "任务 2", content = ""))

        val beforeTasks = dao.getTasksForList(listId).first()
        assertEquals(2, beforeTasks.size)

        // 删除清单
        dao.deleteTaskList(listId)

        val afterLists = dao.getAllTaskLists().first()
        assertTrue(afterLists.isEmpty())

        val afterTasks = dao.getTasksForList(listId).first()
        assertTrue(afterTasks.isEmpty())

        val allTasks = dao.getAllTasks().first()
        assertTrue("级联删除后，属于该清单的任务必须被彻底清理", allTasks.isEmpty())
    }

    @Test
    fun summaryTasksQuery_onlyReturnsTasksFromSummaryVisibleLists() = runBlocking {
        val list1Id = dao.insertTaskList(TaskList(name = "汇总展示清单", themeColor = 0L, showInSummary = true)).toInt()
        val list2Id = dao.insertTaskList(TaskList(name = "隐藏清单", themeColor = 0L, showInSummary = false)).toInt()

        dao.insertTask(Task(listId = list1Id, title = "可见待办", content = "", displayOrder = 0))
        dao.insertTask(Task(listId = list2Id, title = "隐藏待办", content = "", displayOrder = 0))

        val summaryTasks = dao.getSummaryTasks().first()
        assertEquals(1, summaryTasks.size)
        assertEquals("可见待办", summaryTasks[0].title)

        val allTasks = dao.getAllTasks().first()
        assertEquals(2, allTasks.size)
    }

    @Test
    fun appMetadata_readsAndWritesInitializedFlag() = runBlocking {
        val initial = dao.getAppMetadata()
        assertNull(initial)

        dao.setAppMetadata(AppMetadata(id = 1, summaryOrderInitialized = true))
        val updated = dao.getAppMetadata()
        assertNotNull(updated)
        assertTrue(updated!!.summaryOrderInitialized)
    }

    @Test
    fun foreignKeyConstraint_rejectsTaskWithNonExistentListId() = runBlocking {
        try {
            dao.insertTask(Task(listId = 99999, title = "非法外键任务", content = ""))
            fail("应因外键约束失败抛出 SQLiteConstraintException")
        } catch (e: SQLiteConstraintException) {
            // 预期捕获
            assertTrue(e.message?.contains("FOREIGN KEY", ignoreCase = true) == true ||
                       e.message?.contains("constraint", ignoreCase = true) == true)
        }
    }

    @Test
    fun taskOrderQuery_preservesDisplayOrder() = runBlocking {
        val listId = dao.insertTaskList(TaskList(name = "排序测试", themeColor = 0L)).toInt()
        dao.insertTask(Task(listId = listId, title = "任务 C", content = "", displayOrder = 2))
        dao.insertTask(Task(listId = listId, title = "任务 A", content = "", displayOrder = 0))
        dao.insertTask(Task(listId = listId, title = "任务 B", content = "", displayOrder = 1))

        val tasks = dao.getTasksForList(listId).first()
        assertEquals(3, tasks.size)
        assertEquals("任务 A", tasks[0].title)
        assertEquals("任务 B", tasks[1].title)
        assertEquals("任务 C", tasks[2].title)
    }
}
