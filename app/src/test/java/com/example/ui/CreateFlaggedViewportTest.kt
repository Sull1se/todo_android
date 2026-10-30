package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 快速新建插旗待办保存后的视口回顶（APP-028）。
 * 通过真实 HomeScreen 新建弹窗走完整保存链路，以节点是否在视口内可见判断视口位置。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateFlaggedViewportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel

    private var listAId: Int = 1
    private var listCId: Int = 3

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = context.getSharedPreferences("test_create_flagged_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        repository.insertList("清单A(汇总)", 0xFF2196F3L, showInSummary = true)
        repository.insertList("清单C(隐藏)", 0xFFFF9800L, showInSummary = false)

        val lists = repository.allLists.first()
        listAId = lists.first { it.name == "清单A(汇总)" }.id
        listCId = lists.first { it.name == "清单C(隐藏)" }.id

        for (i in 1..20) {
            repository.insertTask(listAId, String.format("Task A%02d", i), "备注 A$i", isFlagged = false)
        }
        for (i in 1..5) {
            repository.insertTask(listCId, String.format("Task C%02d", i), "备注 C$i", isFlagged = false)
        }

        viewModel = TodoViewModel(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
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

    private fun isDisplayed(text: String): Boolean =
        runCatching { composeTestRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess

    private fun showHome(pageId: Int) {
        viewModel.selectList(pageId)
        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel, onTaskClick = {})
        }
        composeTestRule.waitForIdle()
    }

    private fun scrollAwayFromTop() {
        repeat(3) {
            composeTestRule.onRoot().performTouchInput { swipeUp() }
            composeTestRule.waitForIdle()
        }
        assertFalse("前置条件：视口应已离开顶部", isDisplayed("Task A01"))
    }

    private fun createViaDialog(
        title: String,
        flagged: Boolean,
        pickListName: String? = null,
        currentListName: String = "请选择清单"
    ) {
        composeTestRule.onNodeWithContentDescription("添加待办").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("标题").performTextInput(title)
        if (flagged) {
            composeTestRule.onNodeWithContentDescription("插旗置顶").performClick()
        }
        if (pickListName != null) {
            // 清单 Tab 也显示同名文本，弹窗与下拉菜单节点在语义树中位于最后
            composeTestRule.onAllNodesWithText(currentListName).onLast().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithText(pickListName).onLast().performClick()
        }
        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.waitForIdle()
    }

    private fun waitForTaskSaved(title: String) {
        waitForCondition {
            runBlocking { repository.allTasks.first().any { it.title == title } }
        }
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun f01_flaggedCreateAtTrueTop_revealsNewTopItem() {
        showHome(listAId)
        assertTrue(isDisplayed("Task A01"))

        createViaDialog("New Flag Top", flagged = true)
        waitForTaskSaved("New Flag Top")

        waitForCondition { isDisplayed("New Flag Top") }
        assertEquals("New Flag Top", viewModel.pageTasks.value.tasks.first { !it.isCompleted && it.isFlagged }.title)
    }

    @Test
    fun f02_flaggedCreateWhileScrolled_scrollsBackToTop() {
        showHome(listAId)
        scrollAwayFromTop()

        createViaDialog("New Flag Scrolled", flagged = true)
        waitForTaskSaved("New Flag Scrolled")

        waitForCondition { isDisplayed("New Flag Scrolled") }
        assertTrue("回顶后原首项应紧随新首项可见", isDisplayed("Task A01"))
    }

    @Test
    fun f03_unflaggedCreate_keepsViewport() {
        showHome(listAId)
        assertTrue(isDisplayed("Task A01"))

        createViaDialog("New Plain", flagged = false)
        waitForTaskSaved("New Plain")

        composeTestRule.mainClock.advanceTimeBy(2600L)
        composeTestRule.waitForIdle()
        assertTrue("未插旗新建不应移动视口", isDisplayed("Task A01"))
        assertFalse("未插旗新建追加在末尾，不应滚动到它", isDisplayed("New Plain"))
    }

    @Test
    fun f04_flaggedCreateOutsideCurrentPage_keepsViewport() {
        // 清单 A 页中新建插旗待办到清单 C，新任务不在当前页，视口保持
        showHome(listAId)
        scrollAwayFromTop()

        createViaDialog(
            "New Flag Other List",
            flagged = true,
            pickListName = "清单C(隐藏)",
            currentListName = "清单A(汇总)"
        )
        waitForTaskSaved("New Flag Other List")
        assertEquals(listCId, runBlocking { repository.allTasks.first().first { it.title == "New Flag Other List" }.listId })

        composeTestRule.mainClock.advanceTimeBy(2600L)
        composeTestRule.waitForIdle()
        assertFalse("新任务不属于当前页，不应回顶", isDisplayed("Task A01"))
        assertFalse(isDisplayed("New Flag Other List"))
    }
}
