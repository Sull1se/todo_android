package com.example.ui

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
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
class SwipeRefreshInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel

    private var listAId: Int = 1
    private var listBId: Int = 2
    private var listCId: Int = 3

    private val taskIds = mutableListOf<Int>()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = context.getSharedPreferences("test_swipe_refresh_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        repository.insertList("清单A(汇总)", 0xFF2196F3L, showInSummary = true)
        repository.insertList("清单B(汇总)", 0xFF4CAF50L, showInSummary = true)
        repository.insertList("清单C(隐藏)", 0xFFFF9800L, showInSummary = false)

        val lists = repository.allLists.first()
        listAId = lists.first { it.name == "清单A(汇总)" }.id
        listBId = lists.first { it.name == "清单B(汇总)" }.id
        listCId = lists.first { it.name == "清单C(隐藏)" }.id

        taskIds.clear()
        // 插入 40 项任务覆盖三类清单
        // 1..20 在清单 A
        for (i in 1..20) {
            val title = String.format("Task A%02d", i)
            val isFlag = (i == 1) // 第 1 项已插旗，供取消插旗测试
            val id = repository.insertTask(listAId, title, "备注 A$i", isFlagged = isFlag).toInt()
            taskIds.add(id)
        }
        // 21..30 在清单 B
        for (i in 21..30) {
            val title = String.format("Task B%02d", i)
            val id = repository.insertTask(listBId, title, "备注 B$i", isFlagged = false).toInt()
            taskIds.add(id)
        }
        // 31..40 在清单 C (隐藏清单)
        for (i in 31..40) {
            val title = String.format("Task C%02d", i)
            val id = repository.insertTask(listCId, title, "备注 C$i", isFlagged = false).toInt()
            taskIds.add(id)
        }

        viewModel = TodoViewModel(repository, sharedPrefs)
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
    fun l506_summaryPage_swipeAndImmediateScroll_updatesDisplayWithoutPageSwitch() {
        // L506-1: 汇总页 (-1) 中左滑插旗并立即上下滚动
        viewModel.selectList(-1)
        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                onTaskClick = {}
            )
        }
        composeTestRule.waitForIdle()

        // 选取清单A的可见未插旗任务 Task A02
        val targetTaskId = taskIds[1]
        val initialTask = runBlocking { repository.getTaskById(targetTaskId) }!!
        assertFalse(initialTask.isFlagged)

        // 左滑该项以触发插旗
        composeTestRule.onNodeWithText("Task A02").performTouchInput {
            swipeLeft()
        }

        // 立即进行上下滑动中断自动视口动作
        composeTestRule.onRoot().performTouchInput {
            swipeUp()
        }

        // 验证无需切换页面即可持久化并显示已插旗
        waitForCondition {
            runBlocking { repository.getTaskById(targetTaskId)?.isFlagged == true }
        }

        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        val updatedTask = runBlocking { repository.getTaskById(targetTaskId) }!!
        assertTrue("汇总页左滑后立即滚动，数据库中任务应已插旗", updatedTask.isFlagged)

        // 验证当前页任务集合中 Task A05 处于已插旗状态
        val currentTasks = viewModel.pageTasks.value.tasks
        val targetInList = currentTasks.find { it.id == targetTaskId }
        assertNotNull(targetInList)
        assertTrue("汇总页当前列表中该项应显示为已插旗", targetInList!!.isFlagged)

        // 等待 2500ms 确认没有迟到的自动回顶或异常滚动
        composeTestRule.mainClock.advanceTimeBy(2600L)
        composeTestRule.waitForIdle()
        assertEquals(-1, viewModel.selectedListId.value)
    }

    @Test
    fun l506_summaryIncludedList_swipeUnflagAndImmediateScroll_updatesDisplayWithoutPageSwitch() {
        // L506-2: 参与汇总的清单投影中左滑取消插旗并立即上下滚动
        viewModel.selectList(listAId)
        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                onTaskClick = {}
            )
        }
        composeTestRule.waitForIdle()

        // 选取已插旗的 Task A01
        val targetTaskId = taskIds[0]
        val initialTask = runBlocking { repository.getTaskById(targetTaskId) }!!
        assertTrue(initialTask.isFlagged)

        // 左滑该项以触发取消插旗
        composeTestRule.onNodeWithText("Task A01").performTouchInput {
            swipeLeft()
        }

        // 立即滑动列表触发中断
        composeTestRule.onRoot().performTouchInput {
            swipeUp()
        }

        waitForCondition {
            runBlocking { repository.getTaskById(targetTaskId)?.isFlagged == false }
        }

        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        val updatedTask = runBlocking { repository.getTaskById(targetTaskId) }!!
        assertFalse("参与汇总清单左滑取消插旗后立即滚动，任务应已取消插旗", updatedTask.isFlagged)

        val currentTasks = viewModel.pageTasks.value.tasks
        val targetInList = currentTasks.find { it.id == targetTaskId }
        assertNotNull(targetInList)
        assertFalse("当前页任务列表中该项应为未插旗", targetInList!!.isFlagged)
    }

    @Test
    fun l506_hiddenList_swipeAndImmediateScroll_updatesDisplayWithoutPageSwitch() {
        // L506-3: 隐藏清单 (不参与汇总) 中左滑插旗并立即上下滚动
        viewModel.selectList(listCId)
        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                onTaskClick = {}
            )
        }
        composeTestRule.waitForIdle()

        // 选取隐藏清单中的可见任务 Task C31
        val targetTaskId = taskIds[30]
        val initialTask = runBlocking { repository.getTaskById(targetTaskId) }!!
        assertFalse(initialTask.isFlagged)

        // 左滑该项插旗
        composeTestRule.onNodeWithText("Task C31").performTouchInput {
            swipeLeft()
        }

        // 立即上下滑动中断
        composeTestRule.onRoot().performTouchInput {
            swipeUp()
        }

        waitForCondition {
            runBlocking { repository.getTaskById(targetTaskId)?.isFlagged == true }
        }

        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        val updatedTask = runBlocking { repository.getTaskById(targetTaskId) }!!
        assertTrue("隐藏清单左滑后立即滚动，任务应已插旗", updatedTask.isFlagged)

        val currentTasks = viewModel.pageTasks.value.tasks
        val targetInList = currentTasks.find { it.id == targetTaskId }
        assertNotNull(targetInList)
        assertTrue("隐藏清单当前页任务中应已插旗", targetInList!!.isFlagged)
    }
}
