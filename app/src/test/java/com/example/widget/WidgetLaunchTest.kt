package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.TodoApp
import com.example.data.AppDatabase
import com.example.data.TodoRepository
import com.example.ui.TodoViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetLaunchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var factory: TodoViewModelFactory
    private var listId = 0
    private var taskId = 0

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val prefs = context.getSharedPreferences("test_widget_launch_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository.insertList("汇总内清单", 0xFF2196F3L, showInSummary = true)
        listId = repository.insertList("独立清单", 0xFF4CAF50L, showInSummary = false).toInt()
        taskId = repository.insertTask(listId, "卡片中的待办", "卡片备注").toInt()
        factory = TodoViewModelFactory(repository, prefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun parse_readsOnlyWidgetIntents() {
        val task = WidgetLaunch.parse(WidgetLaunch.openTask(context, 3, 7))
        assertEquals(AppLaunchRequest(3, 7), task)
        assertEquals(AppLaunchRequest(SUMMARY_LIST_ID, null), WidgetLaunch.parse(WidgetLaunch.openList(context, SUMMARY_LIST_ID)))
        assertNull(WidgetLaunch.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(WidgetLaunch.parse(null))
    }

    @Test
    fun editTask_opensPopupInsteadOfApp() {
        val intent = WidgetLaunch.editTask(context, 7)
        assertEquals(WidgetEditTaskActivity::class.java.name, intent.component?.className)
        assertEquals(7, intent.getIntExtra(WidgetLaunch.EXTRA_TASK_ID, -1))
        // 不同任务使用不同 data URI，避免 PendingIntent 被系统合并
        assertTrue(intent.data != WidgetLaunch.editTask(context, 8).data)
        // 不是应用主界面的打开请求
        assertNull(WidgetLaunch.parse(intent))
    }

    @Test
    fun taskRequest_selectsListAndOpensDetail() {
        lateinit var navController: NavHostController
        var handled = 0
        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(
                factory = factory,
                navController = nav,
                launchRequest = AppLaunchRequest(listId, taskId),
                onLaunchRequestHandled = { handled++ }
            )
        }
        composeTestRule.waitForIdle()

        assertEquals("detail/{taskId}", navController.currentDestination?.route)
        assertEquals(taskId, navController.currentBackStackEntry?.arguments?.getInt("taskId"))
        composeTestRule.onNodeWithText("卡片备注").assertIsDisplayed()
        assertTrue(handled >= 1)

        // 返回首页后停留在卡片对应的清单
        composeTestRule.runOnIdle { navController.popBackStack() }
        composeTestRule.waitForIdle()
        assertEquals("home", navController.currentDestination?.route)
        composeTestRule.onNodeWithText("卡片中的待办").assertIsDisplayed()
    }

    @Test
    fun listRequest_staysOnHomeWithSelectedList() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(
                factory = factory,
                navController = nav,
                launchRequest = AppLaunchRequest(listId, null)
            )
        }
        composeTestRule.waitForIdle()

        assertEquals("home", navController.currentDestination?.route)
        // 独立清单不参与汇总，能看到其待办说明已切换到该清单
        composeTestRule.onNodeWithText("卡片中的待办").assertIsDisplayed()
    }
}
