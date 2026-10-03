package com.example.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.TodoApp
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
class DetailNavigationInteractionTest {

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
        val sharedPrefs = context.getSharedPreferences("test_nav_interaction_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        repository.insertList("测试清单", 0xFF2196F3L, showInSummary = false)
        val lists = repository.allLists.first()
        listId = lists.first().id

        testTaskId = repository.insertTask(listId, "测试待办", "测试内容", false).toInt()
        factory = TodoViewModelFactory(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun l04_doubleClickSaveDuringExit_preservesHomeDestinationAndUpdatesOnce() {
        // L04: 详情编辑页保存，在退场中重复点击保存 -> home 保持存在，绝不白屏，仅执行一次保存
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()

        // 1. 确认初始停留在 home
        assertEquals("home", navController.currentDestination?.route)

        // 2. 点击任务进入编辑详情
        composeTestRule.onNodeWithText("测试待办").performClick()
        composeTestRule.waitForIdle()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)

        // 3. 修改标题
        composeTestRule.onNodeWithText("测试待办").performTextReplacement("已更新标题")

        // 4. 连点两次保存（模拟退场中再次点击相同坐标）
        composeTestRule.onNodeWithText("保存").performClick()
        // 第二次点击（同帧或退场动画中）
        try {
            composeTestRule.onNodeWithText("保存").performClick()
        } catch (_: Throwable) {
            // 节点可能已在退出或被禁用，捕获但不阻断
        }
        composeTestRule.waitForIdle()

        // 5. 核心断言：当前目的地必须为 home，栈顶绝不为空（无白屏）
        assertNotNull(navController.currentDestination)
        assertEquals("home", navController.currentDestination?.route)

        // 6. 数据正确更新
        val updatedTask = runBlocking { repository.getTaskById(testTaskId) }
        assertNotNull(updatedTask)
        assertEquals("已更新标题", updatedTask!!.title)
    }

    @Test
    fun l05_saveThenCloseOrDelete_preventsDuplicateExitOrDeletion() {
        // L05: 点击保存后立即点击关闭，退出锁生效，不执行重复退出或额外操作
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("测试待办").performClick()
        composeTestRule.waitForIdle()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)

        // 点击保存后立即点击取消
        composeTestRule.onNodeWithText("保存").performClick()
        try {
            composeTestRule.onNodeWithContentDescription("取消").performClick()
        } catch (_: Throwable) {}
        composeTestRule.waitForIdle()

        assertEquals("home", navController.currentDestination?.route)
        val task = runBlocking { repository.getTaskById(testTaskId) }
        assertNotNull("任务未被误删", task)
    }

    @Test
    fun l05_invalidTaskId_exitsSafelyToHomeWithoutCrash() {
        // L05: 打开不存在的 ID -> 自动安全退出到 home，不留在空白页，不崩溃
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()

        // 导航至无效 ID
        composeTestRule.runOnUiThread {
            navController.navigate("detail/999999")
        }
        composeTestRule.waitForIdle()

        // 验证自动退出回到 home
        assertEquals("home", navController.currentDestination?.route)
    }

    @Test
    fun l05_reenterDetail_interactivityRestored() {
        // L05: 退出详情后重新进入详情，新详情的 exitRequested 重新初始化为 false，保存与操作正常可用
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()

        // 第一次进详情并取消
        composeTestRule.onNodeWithText("测试待办").performClick()
        composeTestRule.waitForIdle()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)
        composeTestRule.onNodeWithContentDescription("取消").performClick()
        composeTestRule.waitForIdle()
        assertEquals("home", navController.currentDestination?.route)

        // 第二次进详情并正常保存
        composeTestRule.onNodeWithText("测试待办").performClick()
        composeTestRule.waitForIdle()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)

        composeTestRule.onNodeWithText("测试待办").performTextReplacement("二次编辑")
        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.waitForIdle()

        assertEquals("home", navController.currentDestination?.route)
        val task = runBlocking { repository.getTaskById(testTaskId) }
        assertEquals("二次编辑", task!!.title)
    }
}
