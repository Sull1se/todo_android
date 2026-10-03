package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val testDbName = "migration_test_db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
    }

    @Test
    fun migrate5To6_preservesAllData_andClearsCFlagged_andSetsNonNullableListId() = runBlocking {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. 创建版本 5 原始 SQLite 数据库
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`themeColor` INTEGER NOT NULL, " +
                "`displayOrder` INTEGER NOT NULL, " +
                "`completionMode` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL)"
        )
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`listId` INTEGER, " +
                "`title` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`isCompleted` INTEGER NOT NULL, " +
                "`displayOrder` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isFlagged` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
        sqliteDb.version = 5

        // 清单 1: completionMode = 0 (保持原位)
        sqliteDb.execSQL("INSERT INTO task_lists (id, name, themeColor, displayOrder, completionMode, timestamp) VALUES (1, '清单保持原位', 100, 0, 0, 1000)")
        // 清单 2: completionMode = 1 (收至底部)
        sqliteDb.execSQL("INSERT INTO task_lists (id, name, themeColor, displayOrder, completionMode, timestamp) VALUES (2, '清单收至底部', 200, 1, 1, 2000)")

        // 任务 1: 清单 1 已完成且已插旗 (M 项：保持原位模式的已完成项，旗帜必须保留)
        sqliteDb.execSQL("INSERT INTO tasks (id, listId, title, content, isCompleted, displayOrder, timestamp, isFlagged) VALUES (1, 1, '任务 1', '内容 1', 1, 0, 3000, 1)")
        // 任务 2: 清单 2 已完成且已插旗 (C 项：收至底部模式的已完成项，旗帜必须被清除为 0)
        sqliteDb.execSQL("INSERT INTO tasks (id, listId, title, content, isCompleted, displayOrder, timestamp, isFlagged) VALUES (2, 2, '任务 2', '内容 2', 1, 0, 4000, 1)")
        // 任务 3: 清单 2 未完成且已插旗 (M 项：收至底部模式的未完成项，旗帜必须保留)
        sqliteDb.execSQL("INSERT INTO tasks (id, listId, title, content, isCompleted, displayOrder, timestamp, isFlagged) VALUES (3, 2, '任务 3', '内容 3', 0, 1, 5000, 1)")

        sqliteDb.close()

        // 2. 使用生产 AppDatabase 打开并自动执行 MIGRATION_5_6 升级到版本 6
        val appDatabase = AppDatabase.databaseBuilder(context, testDbName).build()
        val dao = appDatabase.todoDao()

        // 3. 验证清单数据及新增 showInSummary 字段
        val lists = dao.getAllTaskLists().first()
        assertEquals(2, lists.size)
        val list1 = lists.find { it.id == 1 }!!
        assertEquals("清单保持原位", list1.name)
        assertTrue("新清单默认参与汇总", list1.showInSummary)

        val list2 = lists.find { it.id == 2 }!!
        assertEquals("清单收至底部", list2.name)
        assertTrue(list2.showInSummary)

        // 4. 验证 AppMetadata 初始化状态
        val metadata = dao.getAppMetadata()
        assertNotNull("迁移后 metadata 记录必须存在", metadata)
        assertFalse("迁移后 metadata 默认初始化为 false", metadata!!.summaryOrderInitialized)

        // 5. 验证待办数据与旗帜清理规则
        val tasks = dao.getAllTasks().first()
        assertEquals(3, tasks.size)

        val task1 = tasks.find { it.id == 1 }!!
        assertEquals(1, task1.listId)
        assertTrue(task1.isCompleted)
        assertTrue("保持原位模式已完成项保留旗帜", task1.isFlagged)

        val task2 = tasks.find { it.id == 2 }!!
        assertEquals(2, task2.listId)
        assertTrue(task2.isCompleted)
        assertFalse("收至底部模式已完成项（C 项）的旗帜在迁移中必须被清除", task2.isFlagged)

        val task3 = tasks.find { it.id == 3 }!!
        assertEquals(2, task3.listId)
        assertFalse(task3.isCompleted)
        assertTrue("未完成项的旗帜必须完整保留", task3.isFlagged)

        appDatabase.close()
    }

    @Test
    fun migrate5To6_withNullListId_failsAndPreservesOriginalDatabase() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. 创建包含 NULL listId 的非法旧数据库
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`themeColor` INTEGER NOT NULL, " +
                "`displayOrder` INTEGER NOT NULL, " +
                "`completionMode` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL)"
        )
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`listId` INTEGER, " +
                "`title` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`isCompleted` INTEGER NOT NULL, " +
                "`displayOrder` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isFlagged` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
        sqliteDb.version = 5

        sqliteDb.execSQL("INSERT INTO task_lists (id, name, themeColor, displayOrder, completionMode, timestamp) VALUES (1, '默认清单', 100, 0, 0, 1000)")
        sqliteDb.execSQL("INSERT INTO tasks (id, listId, title, content, isCompleted, displayOrder, timestamp, isFlagged) VALUES (1, NULL, '孤立待办', '无归属', 0, 0, 2000, 0)")
        sqliteDb.close()

        // 2. 执行迁移，应严格抛出 IllegalStateException，绝不能静默丢弃任务
        try {
            val appDatabase = AppDatabase.databaseBuilder(context, testDbName).build()
            runBlocking {
                appDatabase.todoDao().getAllTaskLists().first()
            }
            fail("发现未归属清单的待办事项，必须让事务失败并保留原数据库")
        } catch (e: Exception) {
            val hasMsg = (e.message?.contains("无归属清单") == true ||
                e.message?.contains("listId IS NULL") == true ||
                e.cause?.message?.contains("无归属清单") == true ||
                e.cause?.message?.contains("listId IS NULL") == true)
            assertTrue("异常或 cause 应包含无归属清单提示: ${e.message}, cause: ${e.cause?.message}", hasMsg)
        }

        // 3. 验证原数据库完整无损保留
        val verifyDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = verifyDb.rawQuery("SELECT title, listId FROM tasks WHERE id = 1", null)
        assertTrue("原有孤立待办依然完好保存在 SQLite 中，不得被清空或丢失", cursor.moveToFirst())
        assertEquals("孤立待办", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        cursor.close()
        verifyDb.close()
    }

    @Test
    fun missingMigrationPath_failsCleanly_withoutDestructiveDataWipe() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`themeColor` INTEGER NOT NULL, " +
                "`displayOrder` INTEGER NOT NULL, " +
                "`completionMode` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL)"
        )
        sqliteDb.execSQL("INSERT INTO task_lists (id, name, themeColor, displayOrder, completionMode, timestamp) VALUES (1, '重要不可删清单', 0, 0, 0, 1000)")
        sqliteDb.version = 3
        sqliteDb.close()

        try {
            val appDatabase = AppDatabase.databaseBuilder(context, testDbName).build()
            runBlocking {
                appDatabase.todoDao().getAllTaskLists().first()
            }
            fail("应抛出缺少迁移路径异常，而非静默执行或破坏性重建")
        } catch (e: IllegalStateException) {
            assertTrue("异常信息应包含迁移相关提示", e.message?.contains("migration", ignoreCase = true) == true)
        }

        val verifyDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = verifyDb.rawQuery("SELECT name FROM task_lists WHERE id = 1", null)
        assertTrue("原有业务数据必须依然保留在 SQLite 中，不得被清空", cursor.moveToFirst())
        assertEquals("重要不可删清单", cursor.getString(0))
        cursor.close()
        verifyDb.close()
    }

    @Test
    fun freshV6_andMigratedV6_haveCompatibleColumnDefinitions() = runBlocking {
        // 1. 创建全新的 v6 数据库
        val freshDbName = "fresh_v6_db"
        context.deleteDatabase(freshDbName)
        val freshDb = AppDatabase.databaseBuilder(context, freshDbName).build()
        freshDb.todoDao().insertTaskList(TaskList(name = "Fresh List"))
        freshDb.todoDao().insertTask(Task(listId = 1, title = "Fresh Task", content = ""))

        val freshColumns = mutableMapOf<String, String>()
        freshDb.openHelper.readableDatabase.query("PRAGMA table_info(tasks)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                freshColumns[name] = type
            }
        }
        freshDb.close()
        context.deleteDatabase(freshDbName)

        // 2. 检查由 4 经由 5 升级到 6 的字段集合
        val migratedDbName = "migrated_v6_db"
        context.deleteDatabase(migratedDbName)
        val dbFile = context.getDatabasePath(migratedDbName)
        dbFile.parentFile?.mkdirs()
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.execSQL("CREATE TABLE `task_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `themeColor` INTEGER NOT NULL, `displayOrder` INTEGER NOT NULL, `completionMode` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
        sqliteDb.execSQL("CREATE TABLE `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `displayOrder` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqliteDb.execSQL("CREATE INDEX `index_tasks_listId` ON `tasks` (`listId`)")
        sqliteDb.version = 4
        sqliteDb.execSQL("INSERT INTO task_lists (id, name, themeColor, displayOrder, completionMode, timestamp) VALUES (1, '清单 1', 0, 0, 0, 1000)")
        sqliteDb.execSQL("INSERT INTO tasks (id, listId, title, content, isCompleted, displayOrder, timestamp) VALUES (1, 1, '任务 1', '', 0, 0, 1000)")
        sqliteDb.close()

        val migratedDb = AppDatabase.databaseBuilder(context, migratedDbName).build()
        migratedDb.todoDao().getAllTasks().first() // 触发升级 4 -> 5 -> 6

        val migratedColumns = mutableMapOf<String, String>()
        migratedDb.openHelper.readableDatabase.query("PRAGMA table_info(tasks)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                migratedColumns[name] = type
            }
        }
        migratedDb.close()
        context.deleteDatabase(migratedDbName)

        // 3. 断言两者的关键列一致
        assertEquals(freshColumns.keys, migratedColumns.keys)
        for ((col, type) in freshColumns) {
            assertEquals("列 $col 的类型在全新建库与升级建库之间应一致", type, migratedColumns[col])
        }
    }
}
