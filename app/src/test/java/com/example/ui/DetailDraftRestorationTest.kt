package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.TodoApp
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskList
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
class DetailDraftRestorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var factory: TodoViewModelFactory

    private var listId: Int = 1
    private var testTaskId: Int = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = context.getSharedPreferences("test_detail_draft_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        repository.insertList("测试清单", 0xFF2196F3L, showInSummary = false)
        val lists = repository.allLists.first()
        listId = lists.first().id

        testTaskId = repository.insertTask(listId, "原始待办标题", "原始详细内容", false).toInt()
        factory = TodoViewModelFactory(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDraftRestoration_preservesDraftsAfterRestoreAndRepeatedRestore() {
        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var navController: NavHostController

        restorationTester.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        // 1. 进入详情页
        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)

        // 2. 修改标题、备注、旗帜
        composeTestRule.onNodeWithText("原始待办标题").performTextReplacement("草稿新标题")
        composeTestRule.onNodeWithText("原始详细内容").performTextReplacement("草稿新备注\n多行输入内容")
        composeTestRule.onNodeWithContentDescription("插旗").performClick()
        composeTestRule.waitForIdle()

        // 3. 第一次触发保存状态恢复
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        // 断言草稿保留，数据库未写
        composeTestRule.onNodeWithText("草稿新标题").assertIsDisplayed()
        composeTestRule.onNodeWithText("草稿新备注\n多行输入内容").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("取消插旗").assertIsDisplayed()

        var dbTask = runBlocking { repository.getTaskById(testTaskId) }
        assertNotNull(dbTask)
        assertEquals("原始待办标题", dbTask!!.title)
        assertEquals("原始详细内容", dbTask.content)
        assertFalse(dbTask.isFlagged)

        // 4. 第二次触发保存状态恢复（反复恢复）
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        composeTestRule.onNodeWithText("草稿新标题").assertIsDisplayed()
        composeTestRule.onNodeWithText("草稿新备注\n多行输入内容").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("取消插旗").assertIsDisplayed()

        // 5. 点击保存，精确写入数据库并退出到 home
        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitForRoute(navController, "home")
        assertEquals("home", navController.currentDestination?.route)
        dbTask = composeTestRule.waitForTask(repository, testTaskId) { it.title == "草稿新标题" }
        assertNotNull(dbTask)
        assertEquals("草稿新标题", dbTask!!.title)
        assertEquals("草稿新备注\n多行输入内容", dbTask.content)
        assertTrue(dbTask.isFlagged)

        // 6. 再次进入详情，显示已保存值
        composeTestRule.waitForText("草稿新标题")
        composeTestRule.onNodeWithText("草稿新标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        composeTestRule.onNodeWithText("草稿新标题").assertIsDisplayed()
        composeTestRule.onNodeWithText("草稿新备注\n多行输入内容").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("取消插旗").assertIsDisplayed()
    }

    @Test
    fun testDraftRestoration_cancelDiscardsDraftAndRestoresDbValue() {
        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var navController: NavHostController

        restorationTester.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        composeTestRule.onNodeWithText("原始待办标题").performTextReplacement("放弃的标题")
        composeTestRule.onNodeWithText("原始详细内容").performTextReplacement("放弃的内容")
        composeTestRule.onNodeWithContentDescription("插旗").performClick()
        composeTestRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        // 点击取消（Close 图标）：有未保存修改，先弹出“放弃修改？”确认（规范 4.4 决定 3）
        composeTestRule.onNodeWithContentDescription("取消").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("放弃修改？").assertIsDisplayed()
        // 选择继续编辑：留在详情页，草稿保留
        composeTestRule.onNodeWithText("继续编辑").performClick()
        composeTestRule.waitForIdle()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)
        composeTestRule.onNodeWithText("放弃的标题").assertIsDisplayed()
        // 再次取消并确认放弃
        composeTestRule.onNodeWithContentDescription("取消").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("放弃").performClick()
        composeTestRule.waitForIdle()
        assertEquals("home", navController.currentDestination?.route)

        // 数据库不变
        val dbTask = runBlocking { repository.getTaskById(testTaskId) }
        assertNotNull(dbTask)
        assertEquals("原始待办标题", dbTask!!.title)
        assertEquals("原始详细内容", dbTask.content)
        assertFalse(dbTask.isFlagged)

        // 再次进入详情，依然是旧值
        composeTestRule.waitForText("原始待办标题")
        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        composeTestRule.onNodeWithText("原始待办标题").assertIsDisplayed()
        composeTestRule.onNodeWithText("原始详细内容").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("插旗").assertIsDisplayed()
    }

    @Test
    fun testDraftRestoration_emptyTitleRestoredDisablesSave() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val nav = rememberNavController()
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        // 清空标题
        composeTestRule.onNodeWithText("原始待办标题").performTextClearance()
        composeTestRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        // 恢复后标题仍为空，保存按钮被禁用
        composeTestRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun testDraftRestoration_taskIsolation_draftsNotLeaked() {
        runBlocking {
            repository.insertTask(listId, "第二项待办", "内容B", false).toInt()
        }

        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var navController: NavHostController

        restorationTester.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        // 打开 Task A 并修改
        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        composeTestRule.onNodeWithText("原始待办标题").performTextReplacement("A专属草稿")
        composeTestRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        composeTestRule.onNodeWithText("A专属草稿").assertIsDisplayed()

        // 取消退出并确认放弃修改
        composeTestRule.onNodeWithContentDescription("取消").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("放弃").performClick()
        composeTestRule.waitForIdle()

        // 打开 Task B -> 草稿不串用
        composeTestRule.waitForText("第二项待办")
        composeTestRule.onNodeWithText("第二项待办").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        composeTestRule.onNodeWithText("第二项待办").assertIsDisplayed()
        composeTestRule.onNodeWithText("内容B").assertIsDisplayed()
    }

    @Test
    fun testDraftRestoration_bottomCompletedTaskCannotBeFlagged() {
        runBlocking {
            val completedListId = database.todoDao().insertTaskList(
                TaskList(id = 0, name = "自动归底清单", themeColor = 0xFFE91E63L, displayOrder = 2, completionMode = 1, showInSummary = true)
            ).toInt()
            database.todoDao().insertTask(
                Task(id = 0, listId = completedListId, title = "已完成待办", content = "已完成备注", isCompleted = true, isFlagged = false, displayOrder = 0)
            )
        }

        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val nav = rememberNavController()
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        // 进入自动归底清单，展开已完成并打开该任务
        composeTestRule.waitForText("自动归底清单")
        composeTestRule.onNodeWithText("自动归底清单").performClick()
        composeTestRule.waitForIdle()
        // 点击“已完成（1）”折叠头展开
        composeTestRule.waitForText("已完成（1）")
        composeTestRule.onNodeWithText("已完成（1）").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("已完成待办")
        composeTestRule.onNodeWithText("已完成待办").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        // 底部已完成任务禁止插旗，验证无插旗按钮
        composeTestRule.onNodeWithContentDescription("插旗").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("取消插旗").assertDoesNotExist()

        // 修改备注
        composeTestRule.onNodeWithText("已完成备注").performTextReplacement("更新后的已完成备注")
        composeTestRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        // 恢复后仍不可插旗，草稿备注保留
        composeTestRule.onNodeWithContentDescription("插旗").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("取消插旗").assertDoesNotExist()
        composeTestRule.onNodeWithText("更新后的已完成备注").assertIsDisplayed()
    }

    @Test
    fun testDraftRestoration_deletedTaskSafelyExitsToHome() {
        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var navController: NavHostController

        restorationTester.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)

        // 在数据库中物理删除该任务
        runBlocking {
            database.todoDao().deleteTask(testTaskId)
        }

        // 触发恢复 -> LaunchedEffect 发现 task 为 null，安全退出到 home
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForRoute(navController, "home")

        assertEquals("home", navController.currentDestination?.route)
    }

    @Test
    fun testDraftRestoration_longMultiLineContentPreserved() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val nav = rememberNavController()
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForText("原始待办标题")

        composeTestRule.onNodeWithText("原始待办标题").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        val longContent = buildString {
            append("第一行长文本开始\n")
            for (i in 1..200) {
                append("第 $i 行: 这里是一段较长的多行测试说明，包含中文字符与标点符号。$i\n")
            }
            append("最后一行结束")
        }

        composeTestRule.onNodeWithText("原始详细内容").performTextReplacement(longContent)
        composeTestRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.waitForDetailEditable()

        composeTestRule.onNodeWithText(longContent).assertIsDisplayed()
    }
}
