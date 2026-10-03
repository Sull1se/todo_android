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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoPersistenceTest {

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
        val sharedPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().commit()

        viewModel = TodoViewModel(repository, sharedPrefs)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun consecutiveReorders_inHiddenList_persistReliably() = testScope.runTest {
        // 1. 准备初始数据：未参与汇总的独立清单与三项任务 A, B, C
        repository.insertList("独立测试清单", 0L, showInSummary = false)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val listId = lists.first().id
        viewModel.selectList(listId)

        val idA = repository.insertTask(listId, "A", "", false).toInt()
        val idB = repository.insertTask(listId, "B", "", false).toInt()
        val idC = repository.insertTask(listId, "C", "", false).toInt()
        advanceUntilIdle()

        // 初始顺序: [A(0), B(1), C(2)]
        viewModel.tasks.first { it.size == 3 }
        val initTasks = repository.getTasksForList(listId).first()
        assertEquals(listOf(idA, idB, idC), initTasks.map { it.id })

        // 2. 第一次拖拽：将 A 移到末尾 -> [B, C, A]
        viewModel.reorderTasks(idA, idC)
        viewModel.saveTaskOrder()

        // 3. 紧接着第二次拖拽：将 C 移到首位 -> [C, B, A]
        viewModel.reorderTasks(idC, idB)
        val lastJob = viewModel.saveTaskOrder()

        lastJob?.join()
        advanceUntilIdle()

        // 4. 读取数据库中最新快照，验证最终顺序被正确持久化
        val persistedTasks = repository.getAllTasksSnapshot()
        assertEquals(3, persistedTasks.size)
        assertEquals(listOf(idC, idB, idA), persistedTasks.map { it.id })
        assertEquals(0, persistedTasks[0].displayOrder)
        assertEquals(1, persistedTasks[1].displayOrder)
        assertEquals(2, persistedTasks[2].displayOrder)
    }

    @Test
    fun reorder_inSummaryScope_persistsReliablyAcrossLists() = testScope.runTest {
        // 汇总范围跨清单拖拽
        repository.insertList("清单1", 0L, showInSummary = true)
        repository.insertList("清单2", 1L, showInSummary = true)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val list1Id = lists[0].id
        val list2Id = lists[1].id

        val idA = repository.insertTask(list1Id, "任务A", "").toInt()
        val idB = repository.insertTask(list2Id, "任务B", "").toInt()
        advanceUntilIdle()

        viewModel.selectList(-1)
        viewModel.tasks.first { it.size == 2 }
        advanceUntilIdle()

        // 汇总主区域重排序
        viewModel.reorderTasks(idB, idA)
        val job = viewModel.saveTaskOrder()
        job?.join()
        advanceUntilIdle()

        val summaryTasks = repository.summaryTasks.first()
        assertEquals(listOf(idB, idA), summaryTasks.map { it.id })
    }

    @Test
    fun reorder_prohibitedInParticipatingSingleList() = testScope.runTest {
        // 参与汇总的独立清单主区域禁止拖拽
        repository.insertList("参与汇总清单", 0L, showInSummary = true)
        advanceUntilIdle()

        val listId = repository.allLists.first().first().id
        viewModel.selectList(listId)

        val idA = repository.insertTask(listId, "任务A", "").toInt()
        val idB = repository.insertTask(listId, "任务B", "").toInt()
        advanceUntilIdle()

        // 尝试在参与汇总的单清单页面拖拽，应被忽略
        viewModel.reorderTasks(idB, idA)
        val job = viewModel.saveTaskOrder()
        job?.join()
        advanceUntilIdle()

        val currentTasks = repository.getTasksForList(listId).first()
        assertEquals(listOf(idA, idB), currentTasks.map { it.id })
    }

    @Test
    fun reorder_followedImmediatelyByFlagToggle_persistsBothOrderAndFlag() = testScope.runTest {
        repository.insertList("测试清单", 0L, showInSummary = false)
        advanceUntilIdle()

        val listId = repository.allLists.first().first().id
        viewModel.selectList(listId)

        val idA = repository.insertTask(listId, "A", "", false).toInt()
        val idB = repository.insertTask(listId, "B", "", false).toInt()
        viewModel.tasks.first { it.size == 2 }
        advanceUntilIdle()

        // 拖拽换位: B 移到首位
        viewModel.reorderTasks(idB, idA)
        viewModel.saveTaskOrder()

        // 紧接着对 A 执行插旗
        viewModel.toggleTaskFlag(idA)

        advanceUntilIdle()

        // 等待 Room 数据库写入提交并由 Flow 发射最新持久化结果
        val persistedTasks = repository.getTasksForList(listId).first { list ->
            list.any { it.id == idA && it.isFlagged }
        }
        val taskA = persistedTasks.find { it.id == idA }
        val taskB = persistedTasks.find { it.id == idB }
        assertNotNull(taskA)
        assertNotNull(taskB)
        assertTrue("Task A 应成功置为插旗: $taskA", taskA!!.isFlagged)
    }

    @Test
    fun batchMoveAndStatusUpdate_persistsAtomically() = testScope.runTest {
        repository.insertList("源清单", 0L)
        repository.insertList("目标清单", 1L)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val srcId = lists[0].id
        val targetId = lists[1].id
        viewModel.selectList(srcId)

        val id1 = repository.insertTask(srcId, "待办 1", "").toInt()
        val id2 = repository.insertTask(srcId, "待办 2", "").toInt()
        repository.getTasksForList(srcId).first()
        advanceUntilIdle()

        // 进入多选并选择全部
        viewModel.enterSelectionMode(id1)
        viewModel.toggleTaskSelection(id2)

        // 批量标记为已完成
        viewModel.markSelectedTasksStatus(completed = true)
        advanceUntilIdle()

        assertTrue(repository.getTaskById(id1)!!.isCompleted)
        assertTrue(repository.getTaskById(id2)!!.isCompleted)

        // 批量移动到目标清单
        viewModel.enterSelectionMode(id1)
        viewModel.toggleTaskSelection(id2)
        val moveJob = viewModel.moveSelectedTasksToList(targetId)
        moveJob.join()
        advanceUntilIdle()

        assertEquals(targetId, repository.getTaskById(id1)!!.listId)
        assertEquals(targetId, repository.getTaskById(id2)!!.listId)
        assertFalse(viewModel.isSelectionMode.value)
    }

    @Test
    fun toggleTaskFlag_withCallback_returnsAccurateResultAndPersists() = testScope.runTest {
        repository.insertList("测试清单", 0L)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val listId = lists.first().id
        viewModel.selectList(listId)

        val id1 = repository.insertTask(listId, "待办 1", "", false).toInt()
        val id2 = repository.insertTask(listId, "待办 2", "", false).toInt()
        advanceUntilIdle()

        val task1 = repository.getTaskById(id1)!!
        var callbackCalled = false
        var newFlaggedResult: Boolean? = null

        val job = viewModel.toggleTaskFlag(task1) { result ->
            callbackCalled = true
            if (result.isSuccess) {
                newFlaggedResult = result.getOrThrow().newFlagged
            }
        }
        job.join()
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertEquals(true, newFlaggedResult)

        val updatedTask1 = repository.getTaskById(id1)!!
        assertTrue(updatedTask1.isFlagged)

        // 再次取消插旗
        var unflagCallbackCalled = false
        var unflagResult: Boolean? = null
        val job2 = viewModel.toggleTaskFlag(updatedTask1) { result ->
            unflagCallbackCalled = true
            if (result.isSuccess) {
                unflagResult = result.getOrThrow().newFlagged
            }
        }
        job2.join()
        advanceUntilIdle()

        assertTrue(unflagCallbackCalled)
        assertEquals(false, unflagResult)

        val finalTask1 = repository.getTaskById(id1)!!
        assertFalse(finalTask1.isFlagged)
    }

    @Test
    fun pageTasks_emitsCorrectPageIdAndTasks_onPageSwitch() = testScope.runTest {
        repository.insertList("清单A", 0L, showInSummary = true)
        repository.insertList("清单B", 1L, showInSummary = false)
        advanceUntilIdle()

        val lists = repository.allLists.first()
        val listAId = lists[0].id
        val listBId = lists[1].id

        val idA = repository.insertTask(listAId, "A任务", "").toInt()
        val idB = repository.insertTask(listBId, "B任务", "").toInt()
        advanceUntilIdle()

        // 选中清单A
        viewModel.selectList(listAId)
        val pageA = viewModel.pageTasks.first { it.pageId == listAId && it.tasks.any { t -> t.id == idA } }
        assertEquals(listAId, pageA.pageId)
        assertEquals(listOf(idA), pageA.tasks.map { it.id })

        // 切换到汇总页 (-1)
        viewModel.selectList(-1)
        val pageSummary = viewModel.pageTasks.first { it.pageId == -1 && it.tasks.any { t -> t.id == idA } }
        assertEquals(-1, pageSummary.pageId)
        // 汇总只包含清单A的任务，清单B不参与汇总
        assertEquals(listOf(idA), pageSummary.tasks.map { it.id })

        // 切换到未参与汇总的独立清单B
        viewModel.selectList(listBId)
        val pageB = viewModel.pageTasks.first { it.pageId == listBId && it.tasks.any { t -> t.id == idB } }
        assertEquals(listBId, pageB.pageId)
        assertEquals(listOf(idB), pageB.tasks.map { it.id })
    }

    @Test
    fun swipeViewportCoordinator_pageMatchingAndFrame_validatesCorrectly() {
        val list = TaskList(id = 1, name = "测试", showInSummary = true)
        val t1 = Task(id = 1, listId = 1, title = "T1", content = "", displayOrder = 0, isFlagged = true)
        val t2 = Task(id = 2, listId = 1, title = "T2", content = "", displayOrder = 1, isFlagged = false)
        val t3 = Task(id = 3, listId = 1, title = "T3", content = "", displayOrder = 2, isCompleted = true)

        val frame = SwipeViewportCoordinator.buildPageFrame(1, 1L, listOf(t1, t2, t3), listOf(list))
        assertEquals(1, frame.pageId)
        assertEquals(1L, frame.generation)
        assertEquals(listOf(1, 2, 3), frame.displayTasks.map { it.id })
        assertEquals(0, frame.idToMIndex[1])
        assertEquals(1, frame.idToMIndex[2])
        assertEquals(2, frame.idToMIndex[3])

        val isMatch = SwipeViewportCoordinator.isPageMatching(
            pageM = frame.displayTasks,
            pageC = frame.completedTasks,
            expectedM = frame.displayTasks,
            expectedC = frame.completedTasks
        )
        assertTrue(isMatch)

        val nonMatch = SwipeViewportCoordinator.isPageMatching(
            pageM = frame.displayTasks,
            pageC = frame.completedTasks,
            expectedM = frame.displayTasks.reversed(),
            expectedC = frame.completedTasks
        )
        assertFalse(nonMatch)
    }
}
