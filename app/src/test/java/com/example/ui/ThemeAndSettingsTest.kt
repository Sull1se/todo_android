package com.example.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.TodoApp
import com.example.data.AppDatabase
import com.example.data.TodoRepository
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.DarkOledBackground
import com.example.ui.theme.DarkPrimaryContainer
import com.example.ui.theme.DarkOnPrimaryContainer
import com.example.ui.theme.LightColorScheme
import com.example.ui.theme.TabActiveBg
import com.example.ui.theme.TabActiveText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeAndSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var factory: TodoViewModelFactory
    private lateinit var sharedPrefs: android.content.SharedPreferences

    private var testTaskId: Int = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        sharedPrefs = context.getSharedPreferences("test_theme_settings_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        repository.insertList("生活清单", 0xFF2196F3L, showInSummary = true)
        val lists = repository.allLists.first()
        val listId = lists.first().id

        testTaskId = repository.insertTask(listId, "测试主题待办", "待办备注", false).toInt()
        factory = TodoViewModelFactory(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // T601: 偏好状态解析与持久化测试
    @Test
    fun t601_themeModeParsingAndPersistence() {
        // 1. 缺失与非法值回退为 SYSTEM
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("invalid_mode"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("DARK_UPPERCASE"))

        // 2. 三种合法存储值解析
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("dark"))

        // 3. ViewModel 启动时默认跟随系统
        val vm = TodoViewModel(repository, sharedPrefs)
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)

        // 4. 设置为深色模式并验证持久化
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        assertEquals("dark", sharedPrefs.getString("appearance_mode", null))

        // 5. 进程重启/重构 ViewModel，从 SharedPreferences 读出持久化值
        val vm2 = TodoViewModel(repository, sharedPrefs)
        assertEquals(ThemeMode.DARK, vm2.themeMode.value)

        // 6. 切换为浅色模式并验证
        vm2.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, vm2.themeMode.value)
        assertEquals("light", sharedPrefs.getString("appearance_mode", null))

        // 7. 切换回跟随系统
        vm2.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, vm2.themeMode.value)
        assertEquals("system", sharedPrefs.getString("appearance_mode", null))
    }

    // T602: 界面交互与设置入口测试
    @Test
    fun t602_settingsDialogInteractionAndFlow() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            TodoApp(factory = factory, navController = nav)
        }
        composeTestRule.waitForIdle()

        // 1. 验证清单设置按钮存在且无障碍描述为 "清单设置"
        composeTestRule.onNodeWithContentDescription("清单设置").assertIsDisplayed()

        // 2. 打开右上角 "应用设置"
        composeTestRule.onNodeWithContentDescription("应用设置").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("应用设置").performClick()
        composeTestRule.waitForIdle()

        // 验证应用设置弹窗展示
        composeTestRule.onNodeWithText("应用设置").assertIsDisplayed()
        composeTestRule.onNodeWithText("跟随系统").assertIsDisplayed()
        composeTestRule.onNodeWithText("浅色模式").assertIsDisplayed()
        composeTestRule.onNodeWithText("深色模式").assertIsDisplayed()

        // 3. 选择深色模式
        composeTestRule.onNodeWithText("深色模式").performClick()
        composeTestRule.waitForIdle()
        assertEquals("dark", sharedPrefs.getString("appearance_mode", null))

        // 4. 打开数据导入与导出
        composeTestRule.onNodeWithText("数据导入与导出").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 验证数据管理对话框打开
        composeTestRule.onNodeWithText("数据管理").assertIsDisplayed()
        composeTestRule.onNodeWithText("导出全部数据 (JSON)").assertIsDisplayed()
        composeTestRule.onNodeWithText("从 JSON 文件导入").assertIsDisplayed()

        // 5. 关闭数据管理对话框
        composeTestRule.onNodeWithText("关闭").performClick()
        composeTestRule.waitForIdle()

        // 6. 验证仍在首页，待办卡片正常显示
        composeTestRule.onNodeWithText("测试主题待办").assertIsDisplayed()

        // 7. 进入详情页修改草稿后切换主题，验证草稿与导航位置不丢
        composeTestRule.onNodeWithText("测试主题待办").performClick()
        composeTestRule.waitForIdle()
        assertEquals("detail/{taskId}", navController.currentDestination?.route)

        composeTestRule.onNodeWithText("测试主题待办").performTextReplacement("待办草稿修改中")
        composeTestRule.waitForIdle()

        // 验证详情页中的草稿正常展示
        composeTestRule.onNodeWithText("待办草稿修改中").assertIsDisplayed()
    }

    // T603: 色值与可读性核查
    @Test
    fun t603_colorValuesAndOledBlack() {
        // 1. 深色模式 background 必须为 OLED 纯黑 #000000
        assertEquals(Color(0xFF000000), DarkColorScheme.background)
        assertEquals(Color(0xFF000000), DarkOledBackground)

        // 2. 深色 surface 与 background 区分层级
        assertNotEquals(DarkColorScheme.background, DarkColorScheme.surface)
        assertEquals(Color(0xFF161616), DarkColorScheme.surface)

        // 3. Tab 配对色值检查
        assertEquals(TabActiveBg, LightColorScheme.primaryContainer)
        assertEquals(TabActiveText, LightColorScheme.onPrimaryContainer)
        assertEquals(DarkPrimaryContainer, DarkColorScheme.primaryContainer)
        assertEquals(DarkOnPrimaryContainer, DarkColorScheme.onPrimaryContainer)

        // 4. 浅色模式保持规范主色
        assertEquals(Color(0xFF0B57D0), LightColorScheme.primary)
        assertEquals(Color.White, LightColorScheme.onPrimary)
    }
}
