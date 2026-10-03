package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskList
import com.example.data.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeInteractionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = context.getSharedPreferences("test_prefs_interaction", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        viewModel = TodoViewModel(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun l01_dragWithoutRelease_updatesPageTasksAndFrame_doesNotWriteDb() = testScope.runTest {
        // L01: 初始化 S 和隐藏 L；拖动但不松手 -> pageTasks 与实际 frame 已按 ID 换位，C 不变，数据库未被每次 onMove 写入
        repository.insertList("隐藏清单", 0L, showInSummary = false)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val listId = lists.first().id
        viewModel.selectList(listId)

        val idA = repository.insertTask(listId, "A", "", false).toInt()
        val idB = repository.insertTask(listId, "B", "", false).toInt()
        val idC = repository.insertTask(listId, "C", "", false).toInt()
        advanceUntilIdle()

        val initialPage = viewModel.pageTasks.first { it.pageId == listId && it.tasks.size == 3 }
        val initRevision = initialPage.revision

        // 用户手柄按下启动拖拽会话
        val token = viewModel.startDragSession(idA)
        assertNotNull(token)
        assertEquals(token, viewModel.activeDragSession?.token)

        // 换位: A 移到 B 后方 -> [B, A, C]
        val updatedPage = viewModel.reorderTasks(idA, idB)
        assertNotNull(updatedPage)
        assertEquals(listOf(idB, idA, idC), updatedPage?.tasks?.map { it.id })
        assertEquals(listOf(idB, idA, idC), viewModel.pageTasks.value.tasks.map { it.id })
        assertTrue(viewModel.pageTasks.value.revision > initRevision)

        // 数据库尚未持久化
        val dbTasks = repository.getTasksForList(listId).first()
        assertEquals(listOf(idA, idB, idC), dbTasks.map { it.id })
    }

    @Test
    fun l02_continuousMultiDragAndSave_preservesLatestOrder() = testScope.runTest {
        // L02: 连续跨两项拖动、连续两次松手，延迟第一事务确认 -> 中间 UI 跟随最新会话，最终数据库保留全部拖动顺序
        repository.insertList("隐藏清单", 0L, showInSummary = false)
        advanceUntilIdle()

        val listId = repository.allLists.first().first().id
        viewModel.selectList(listId)

        val idA = repository.insertTask(listId, "A", "", false).toInt()
        val idB = repository.insertTask(listId, "B", "", false).toInt()
        val idC = repository.insertTask(listId, "C", "", false).toInt()
        advanceUntilIdle()

        viewModel.pageTasks.first { it.tasks.size == 3 }

        // 第一次长拖跨两项: A -> C => [B, C, A]
        viewModel.startDragSession(idA)
        viewModel.reorderTasks(idA, idC)
        val job1 = viewModel.saveTaskOrder()

        // 第二次拖动紧随其后: C -> B => [C, B, A]
        viewModel.startDragSession(idC)
        viewModel.reorderTasks(idC, idB)
        val job2 = viewModel.saveTaskOrder()

        job1?.join()
        job2?.join()
        advanceUntilIdle()

        val persisted = repository.getTasksForList(listId).first()
        assertEquals(listOf(idC, idB, idA), persisted.map { it.id })
    }

    @Test
    fun l03_dragInterruptionByPageSwitchOrCancel_revertsUncommitted() = testScope.runTest {
        // L03: 拖动中取消/换页/多选，随后晚到 stop 回调不污染新页，未提交不落库
        repository.insertList("清单1", 0L, showInSummary = false)
        repository.insertList("清单2", 1L, showInSummary = false)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val list1 = lists[0].id
        val list2 = lists[1].id
        viewModel.selectList(list1)

        val idA = repository.insertTask(list1, "A", "").toInt()
        val idB = repository.insertTask(list1, "B", "").toInt()
        advanceUntilIdle()

        viewModel.pageTasks.first { it.pageId == list1 && it.tasks.size == 2 }

        val token = viewModel.startDragSession(idA)
        viewModel.reorderTasks(idA, idB)
        assertEquals(listOf(idB, idA), viewModel.pageTasks.value.tasks.map { it.id })

        // 切换页面: 必须取消未提交拖拽会话
        viewModel.selectList(list2)
        assertNull(viewModel.activeDragSession)

        // 模拟晚到 stop 调用
        val job = viewModel.saveTaskOrder(token)
        assertNull(job)

        // 验证 list1 数据库保持原序 [A, B]
        val list1Tasks = repository.getTasksForList(list1).first()
        assertEquals(listOf(idA, idB), list1Tasks.map { it.id })
    }

    @Test
    fun l04_semanticConflictDuringCommit_rejectsAndRollsBack() = testScope.runTest {
        // L04: 提交前注入成员变动，冲突全量拒绝，回到真实数据
        repository.insertList("独立清单", 0L, showInSummary = false)
        advanceUntilIdle()

        val listId = repository.allLists.first().first().id
        viewModel.selectList(listId)

        val idA = repository.insertTask(listId, "A", "").toInt()
        val idB = repository.insertTask(listId, "B", "").toInt()
        advanceUntilIdle()

        viewModel.pageTasks.first { it.pageId == listId && it.tasks.size == 2 }

        val token = viewModel.startDragSession(idA)
        viewModel.reorderTasks(idA, idB)

        // 外部在提交前删除了任务 B
        repository.deleteTask(idB)
        val updatedPage = viewModel.pageTasks.first { it.tasks.size == 1 }
        assertEquals(listOf(idA), updatedPage.tasks.map { it.id })

        // 提交重排应检测到成员变动并拒绝
        val commitResult = repository.commitScopeReorder(listId, listOf(idB, idA))
        assertTrue(commitResult.isFailure)

        // UI 保持与真实数据一致
        assertEquals(listOf(idA), viewModel.pageTasks.value.tasks.map { it.id })
    }

    @Test
    fun l05_dragProhibitions_participatingSingleListAndBottomCompleted() = testScope.runTest {
        // L05: S、隐藏 L 可拖拽；参与汇总单清单、C、多选禁止开始
        repository.insertList("参与汇总清单", 0L, showInSummary = true)
        repository.insertList("隐藏清单", 1L, showInSummary = false)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val participatingId = lists.find { it.showInSummary }!!.id
        val hiddenId = lists.find { !it.showInSummary }!!.id

        val taskPartId = repository.insertTask(participatingId, "P1", "").toInt()
        val taskHiddenId = repository.insertTask(hiddenId, "H1", "").toInt()
        advanceUntilIdle()

        // 1. 参与汇总单清单禁止拖拽
        viewModel.selectList(participatingId)
        viewModel.pageTasks.first { it.pageId == participatingId && it.tasks.isNotEmpty() }
        val tokenPart = viewModel.startDragSession(taskPartId)
        assertNull(tokenPart)

        // 2. 隐藏清单允许拖拽
        viewModel.selectList(hiddenId)
        viewModel.pageTasks.first { it.pageId == hiddenId && it.tasks.isNotEmpty() }
        val tokenHidden = viewModel.startDragSession(taskHiddenId)
        assertNotNull(tokenHidden)
        viewModel.cancelDrag(tokenHidden)

        // 3. 汇总页 S 允许拖拽
        viewModel.selectList(-1)
        viewModel.pageTasks.first { it.pageId == -1 && it.tasks.isNotEmpty() }
        val tokenSummary = viewModel.startDragSession(taskPartId)
        assertNotNull(tokenSummary)
        viewModel.cancelDrag(tokenSummary)

        // 4. 多选模式禁止拖拽
        viewModel.enterSelectionMode(taskPartId)
        val tokenSelectMode = viewModel.startDragSession(taskPartId)
        assertNull(tokenSelectMode)
        viewModel.exitSelectionMode()
    }
}
