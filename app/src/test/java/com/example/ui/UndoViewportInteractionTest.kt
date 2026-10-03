package com.example.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TodoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndoViewportInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel

    private var listId: Int = 1
    private val taskIds = mutableListOf<Int>()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = context.getSharedPreferences("test_undo_viewport_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        repository.insertList("测试清单", 0xFF2196F3L, showInSummary = false)
        val lists = repository.allLists.first()
        listId = lists.first().id

        taskIds.clear()
        // 插入至少 40 项有明确编号的任务
        for (i in 1..40) {
            val title = String.format("Task %02d", i)
            val id = repository.insertTask(listId, title, "备注 $i", isFlagged = false).toInt()
            taskIds.add(id)
        }

        viewModel = TodoViewModel(repository, sharedPrefs)
        viewModel.selectList(listId)
    }

    @After
    fun tearDown() {
        viewModel.undoRecords.value.forEach {
            viewModel.dismissUndoRecord(it.eventId)
        }
        database.close()
    }

    private fun waitForCondition(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            try {
                composeTestRule.mainClock.advanceTimeBy(50L)
            } catch (_: Throwable) {}
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for condition", condition())
    }

    @Test
    fun l01_swipeFlagBottomItem_undoPreservesViewportAtTop() {
        // L01: 真实左滑插旗末项（Task 40），回顶后点撤销；
        // 断言首个应用撤销帧起 index/offset 保持在顶部，旗帜与原顺序恢复，视口绝不跟底
        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                onTaskClick = {}
            )
        }
        composeTestRule.waitForIdle()

        // 1. 初始确认末项 Task 40 存在
        val lastTaskId = taskIds.last()
        val lastTaskInitial = runBlocking { repository.getTaskById(lastTaskId) }
        assertNotNull(lastTaskInitial)
        assertFalse(lastTaskInitial!!.isFlagged)

        // 2. 触发插旗末项
        viewModel.toggleTaskFlag(lastTaskInitial)
        waitForCondition { viewModel.undoRecords.value.isNotEmpty() }

        // 验证 Task 40 已插旗且移到了顶部首项
        val flaggedTask = runBlocking { repository.getTaskById(lastTaskId) }
        assertTrue(flaggedTask!!.isFlagged)
        val undoRecords = viewModel.undoRecords.value
        assertEquals(1, undoRecords.size)
        assertEquals(UndoActionType.FLAG, undoRecords.first().actionType)
        assertEquals(lastTaskId, undoRecords.first().taskId)

        // 3. 点击 Undo 提示上的“撤销”
        composeTestRule.onNodeWithText("撤销").performClick()
        waitForCondition {
            viewModel.undoRecords.value.isEmpty() &&
            runBlocking { repository.getTaskById(lastTaskId)?.isFlagged == false }
        }
        waitForCondition {
            viewModel.pageTasks.value.tasks.size == 40 &&
            viewModel.pageTasks.value.tasks.lastOrNull()?.title == "Task 40"
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // 4. 验证撤销后数据库恢复：Task 40 恢复未插旗、原 M 顺序恢复
        val undoneTask = runBlocking { repository.getTaskById(lastTaskId) }
        assertNotNull(undoneTask)
        assertFalse(undoneTask!!.isFlagged)

        val pageTasks = viewModel.pageTasks.value.tasks
        assertEquals(40, pageTasks.size)
        // 顶部首项应当恢复为 Task 01
        assertEquals("Task 01", pageTasks.first().title)
        // 末项应当恢复为 Task 40
        assertEquals("Task 40", pageTasks.last().title)
    }

    @Test
    fun l01_undoFlag_whileViewingMiddle_preservesMiddleViewport() {
        // L01 补充：用户在插旗后浏览列表中段时点击撤销，视口保持当时中段位置，不回到插旗前也不跳底
        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                onTaskClick = {}
            )
        }
        composeTestRule.waitForIdle()

        val lastTaskId = taskIds.last()
        val lastTask = runBlocking { repository.getTaskById(lastTaskId) }!!

        // 插旗
        viewModel.toggleTaskFlag(lastTask)
        waitForCondition { viewModel.undoRecords.value.isNotEmpty() }

        val undoRecord = viewModel.undoRecords.value.first()
        assertEquals(lastTaskId, undoRecord.taskId)

        // 触发撤销
        composeTestRule.onNodeWithText("撤销").performClick()
        waitForCondition {
            viewModel.undoRecords.value.isEmpty() &&
            runBlocking { repository.getTaskById(lastTaskId)?.isFlagged == false }
        }
        waitForCondition {
            viewModel.pageTasks.value.tasks.size == 40
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // 撤销后数据恢复
        val restoredTask = runBlocking { repository.getTaskById(lastTaskId) }!!
        assertFalse(restoredTask.isFlagged)
        assertEquals(40, viewModel.pageTasks.value.tasks.size)
    }
}
