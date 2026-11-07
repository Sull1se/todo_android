package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskList
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
 * UI 与 UX 规范落地的行为验证：已完成区倒序（APP-037）、系统返回退出多选（UX-14）、
 * 完成控件复选框语义与自定义无障碍操作（UX-16/17）、英文资源（UX-22）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiUxSpecTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel
    private var bottomListId = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val prefs = context.getSharedPreferences("test_ui_ux_spec_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        bottomListId = database.todoDao().insertTaskList(
            TaskList(id = 0, name = "归底清单", themeColor = 0xFF1E88E5L, displayOrder = 0, completionMode = 1)
        ).toInt()
        database.todoDao().insertTask(Task(id = 0, listId = bottomListId, title = "进行中", content = "", displayOrder = 0))
        // 已完成区存储顺序：最早完成在前
        listOf("最早完成", "其次完成", "最近完成").forEachIndexed { index, title ->
            database.todoDao().insertTask(
                Task(id = 0, listId = bottomListId, title = title, content = "", isCompleted = true, displayOrder = index + 1)
            )
        }
        viewModel = TodoViewModel(repository, prefs)
        viewModel.selectList(bottomListId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun showHome() {
        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel, onTaskClick = {})
        }
        composeTestRule.waitUntil(5000) {
            runCatching { composeTestRule.onNodeWithText("进行中").fetchSemanticsNode() }.isSuccess
        }
    }

    private fun topOf(text: String): Float =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    @Config(qualifiers = "+h1000dp") // 加高窗口，让已完成区全部项同时进入可见区域
    fun completedSection_showsMostRecentlyCompletedFirst() {
        showHome()
        composeTestRule.onNodeWithText("已完成（3）").performClick()
        composeTestRule.waitForIdle()

        val newest = topOf("最近完成")
        val middle = topOf("其次完成")
        val oldest = topOf("最早完成")
        assertTrue("最近完成应在最上方", newest < middle)
        assertTrue("最早完成应在最下方", middle < oldest)
        // 倒序只影响显示，存储顺序不变
        val stored = runBlocking { repository.getTasksForListSnapshot(bottomListId) }
            .filter { it.isCompleted }
            .map { it.title }
        assertEquals(listOf("最早完成", "其次完成", "最近完成"), stored)
    }

    @Test
    fun systemBack_exitsSelectionModeFirst() {
        showHome()
        val taskId = runBlocking { repository.allTasks.first().first { it.title == "进行中" }.id }
        composeTestRule.runOnUiThread { viewModel.enterSelectionMode(taskId) }
        composeTestRule.waitForIdle()
        assertTrue(viewModel.isSelectionMode.value)

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertFalse(viewModel.isSelectionMode.value)
        assertFalse("返回只退出多选，不结束页面", composeTestRule.activity.isFinishing)
    }

    @Test
    fun taskCard_exposesCheckboxRoleAndCustomActions() {
        showHome()
        val checkbox = composeTestRule.onNode(
            hasContentDescription("完成") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        )
        checkbox.assertIsOff()

        val card = composeTestRule.onNodeWithText("进行中", useUnmergedTree = false).fetchSemanticsNode()
        val labels = generateSequence(card) { it.parent }
            .mapNotNull { node -> node.config.getOrNull(SemanticsActions.CustomActions) }
            .flatten()
            .map { it.label }
            .toList()
        assertTrue("应提供插旗替代操作：$labels", "插旗" in labels)
    }

    @Test
    @Config(qualifiers = "en")
    fun englishResourcesAreProvided() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("New task", context.getString(R.string.new_task))
        assertEquals("Summary", context.getString(R.string.summary))
        assertEquals("Completed (2)", context.getString(R.string.completed_header, 2))
    }

    @Test
    fun chineseResourcesUseGlossaryTerms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("新建待办", context.getString(R.string.new_task))
        assertEquals("已完成（2）", context.getString(R.string.completed_header, 2))
    }
}
