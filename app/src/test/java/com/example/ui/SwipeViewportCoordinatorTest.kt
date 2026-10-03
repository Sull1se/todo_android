package com.example.ui

import androidx.compose.foundation.lazy.LazyListState
import com.example.data.Task
import com.example.data.TaskList
import com.example.data.TaskOrderEngine
import com.example.data.UndoFlagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class SwipeViewportCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var listState: LazyListState
    private lateinit var coordinator: SwipeViewportCoordinator
    private val errorMessages = mutableListOf<String>()

    private val testLists = listOf(
        TaskList(id = 1, name = "测试清单", themeColor = 0xFF123456L, showInSummary = false)
    )

    private val task1 = Task(id = 101, listId = 1, title = "Task 1", content = "", isFlagged = true, displayOrder = 0)
    private val task2 = Task(id = 102, listId = 1, title = "Task 2", content = "", isFlagged = false, displayOrder = 1)
    private val task3 = Task(id = 103, listId = 1, title = "Task 3", content = "", isFlagged = false, displayOrder = 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listState = LazyListState()
        coordinator = SwipeViewportCoordinator(testScope, listState) { err ->
            errorMessages.add(err)
        }
        coordinator.initFrameIfNeeded(1, listOf(task1, task2, task3), testLists)
    }

    @After
    fun tearDown() {
        coordinator.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun l06_realSettlement_doesNotRelyOn400msFakeTimer() = testScope.runTest {
        // L06: 验证不依赖 400ms 兜底伪回位，只有真正回位事件才进入协调
        val initialTasks = listOf(task1, task2, task3)
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        coordinator.onSwipeToggleFlag(task1, 1) { onComplete ->
            onComplete(Result.success(toggleResult))
        }
        testScheduler.runCurrent()

        // 此时数据库结果已返回，但卡片尚未回位，状态应保持在 AwaitResultAndPageAndCard
        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coordinator.coordinatorState)

        // 推进 400ms 时间，验证不再有旧的 400ms 强制回位兜底
        testScheduler.advanceTimeBy(400)
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coordinator.coordinatorState)

        // 此时发送真正的回位事件，且提供最新的 DB tasks 匹配
        coordinator.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        coordinator.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()

        // 成功进入消费等待阶段
        assertEquals(CoordinatorState.AwaitConsumed, coordinator.coordinatorState)
        assertNotNull(coordinator.viewportCommand)
    }

    @Test
    fun l07_cardDispose_cancelsCoordination_withoutPretendingSettled() = testScope.runTest {
        // L07: 手势中卡片 Dispose 报告不可继续观测，转中断/刷新，不冒充正常完成
        val initialTasks = listOf(task1, task2, task3)
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        coordinator.onSwipeToggleFlag(task1, 1) { onComplete ->
            onComplete(Result.success(toggleResult))
        }
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coordinator.coordinatorState)

        // 卡片被提前 Dispose
        coordinator.onSwipeCardDisposed(task1.id)
        testScheduler.runCurrent()

        // 状态应重置为 Idle，且命令被清理
        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l08_unflagAtTrueTop_generatesPinPositionZeroZero() = testScope.runTest {
        // L08: 顶部取消第 0 项，生成 PinPosition(0, 0)，保持 APP-012 视口锚定
        val initialTasks = listOf(task1, task2, task3)
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        // listState 处于 (0, 0)
        coordinator.onSwipeToggleFlag(task1, 1) { onComplete ->
            onComplete(Result.success(toggleResult))
        }
        testScheduler.runCurrent()

        coordinator.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        coordinator.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()

        val command = coordinator.viewportCommand
        assertNotNull(command)
        assertEquals(ViewportIntent.PinPosition(0, 0), command?.intent)
    }

    @Test
    fun l09_intentClassification_flagGeneratesAnimateTop_andUnflagKeepKey() = testScope.runTest {
        // L09: 非顶部插旗生成 AnimateTop；未移出首可见项的取消插旗生成 KeepKey
        val initialTasks = listOf(
            task1.copy(isFlagged = false, displayOrder = 0),
            task2.copy(isFlagged = false, displayOrder = 1),
            task3.copy(isFlagged = false, displayOrder = 2)
        )
        coordinator.initFrameIfNeeded(1, initialTasks, testLists)

        // 1. 对 task2 插旗（从非首位插旗）
        val flagResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task2.id)
        coordinator.onSwipeToggleFlag(task2, 1) { onComplete ->
            onComplete(Result.success(flagResult))
        }
        testScheduler.runCurrent()

        coordinator.onPageDataUpdated(1, flagResult.updatedTasks, testLists)
        coordinator.onSwipeCardSettled(task2.id)
        testScheduler.runCurrent()

        val flagCommand = coordinator.viewportCommand
        assertNotNull(flagCommand)
        assertTrue(flagCommand?.intent is ViewportIntent.AnimateTop)

        // 重置
        coordinator.reset()
        testScheduler.runCurrent()

        // 2. 取消插旗，首可见项不是被操作项 -> 生成 KeepKey
        // 设 task1 处于首位，task2 在第二位，均已插旗；取消 task2 插旗，首可见项为 task1 != task2
        val task2Flagged = task2.copy(isFlagged = true, displayOrder = 1)
        val flaggedTasks = listOf(
            task1.copy(isFlagged = true, displayOrder = 0),
            task2Flagged,
            task3.copy(isFlagged = false, displayOrder = 2)
        )
        coordinator.onPageDataUpdated(1, flaggedTasks, testLists)
        val unflagResult = TaskOrderEngine.toggleFlag(flaggedTasks, testLists, task2Flagged.id)

        coordinator.onSwipeToggleFlag(task2Flagged, 1) { onComplete ->
            onComplete(Result.success(unflagResult))
        }
        testScheduler.runCurrent()

        coordinator.onPageDataUpdated(1, unflagResult.updatedTasks, testLists)
        coordinator.onSwipeCardSettled(task2.id)
        testScheduler.runCurrent()

        assertNotNull(coordinator.viewportCommand)
        assertEquals(ViewportIntent.KeepKey, coordinator.viewportCommand?.intent)
    }

    @Test
    fun l10_commandConsumption_advancesStateToAwaitLayout() = testScope.runTest {
        // L10: 只有新命令被消费后才推进到 AwaitLayout
        val initialTasks = listOf(task1, task2, task3)
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        coordinator.onSwipeToggleFlag(task1, 1) { onComplete ->
            onComplete(Result.success(toggleResult))
        }
        testScheduler.runCurrent()

        coordinator.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        coordinator.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitConsumed, coordinator.coordinatorState)
        val cmd = coordinator.viewportCommand!!

        // SideEffect 消费该命令
        coordinator.onCommandConsumed(cmd.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        testScheduler.runCurrent()

        // 状态顺利推进到 AwaitLayout
        assertEquals(CoordinatorState.AwaitLayout, coordinator.coordinatorState)
    }

    @Test
    fun l11_timeout_releasesWithoutAutomaticScroll() = testScope.runTest {
        // L11: 布局超时 2000ms 后释放状态，不触发后续滚动
        val initialTasks = listOf(task1, task2, task3)
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        coordinator.onSwipeToggleFlag(task1, 1) { onComplete ->
            onComplete(Result.success(toggleResult))
        }
        testScheduler.runCurrent()

        coordinator.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        coordinator.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()

        val cmd = coordinator.viewportCommand!!
        coordinator.onCommandConsumed(cmd.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitLayout, coordinator.coordinatorState)

        // 推进 2500ms，触发超时 (withTimeoutOrNull(2000L))
        testScheduler.advanceTimeBy(2500)
        testScheduler.runCurrent()

        // 超时后回归 Idle，未执行滚动
        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l12_userInterruption_cancelsActiveCoordination() = testScope.runTest {
        // L12: 用户接管/滚动/中断使旧 epoch 失效，退出协调
        val initialTasks = listOf(task1, task2, task3)
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        coordinator.onSwipeToggleFlag(task1, 1) { onComplete ->
            onComplete(Result.success(toggleResult))
        }
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coordinator.coordinatorState)

        // 用户触发中断（例如滑动列表或切换清单）
        coordinator.onUserInterruption()
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l13_undo_doubleOrderA_dbThenCallback_emitsPinPosition() = testScope.runTest {
        // L13: 顺序A：Room 数据先发射，事务回调后到达 -> 成功生成 PinPosition 视口命令并进入 AwaitConsumed
        val initialTasks = listOf(task1, task2, task3)
        // task1 原为 flagged，撤销时变为未插旗并移回对应 M 索引
        val undoneTasks = TaskOrderEngine.undoFlag(initialTasks, testLists, task1.id, originalMIndex = 2, originalFlagged = false)
        val flagResult = UndoFlagResult(taskId = task1.id, updatedTasks = undoneTasks, lists = testLists)
        val undoRecord = UndoRecord(
            eventId = 1L,
            taskId = task1.id,
            taskTitle = task1.title,
            listId = task1.listId,
            originalMIndex = 2,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var commandCallback: ((UndoActionResult) -> Unit)? = null
        coordinator.onUndoFlag(undoRecord, 1) { cb ->
            commandCallback = cb
        }
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitUndoResultAndPage, coordinator.coordinatorState)

        // 1. Room 数据先到达
        coordinator.onPageDataUpdated(1, undoneTasks, testLists)
        testScheduler.runCurrent()
        // 尚未收到回调，保持等待
        assertEquals(CoordinatorState.AwaitUndoResultAndPage, coordinator.coordinatorState)

        // 2. 事务回调随后到达
        commandCallback?.invoke(
            UndoActionResult.Applied(
                eventId = undoRecord.eventId,
                taskId = undoRecord.taskId,
                actionType = UndoActionType.FLAG,
                flagResult = flagResult
            )
        )
        testScheduler.runCurrent()

        // 成功匹配并发布 PinPosition 命令
        assertEquals(CoordinatorState.AwaitConsumed, coordinator.coordinatorState)
        val cmd = coordinator.viewportCommand
        assertNotNull(cmd)
        assertTrue(cmd?.intent is ViewportIntent.PinPosition)
        coordinator.onCommandConsumed(cmd!!.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        testScheduler.runCurrent()
        coordinator.reset()
    }

    @Test
    fun l14_undo_doubleOrderB_callbackThenDb_emitsPinPosition() = testScope.runTest {
        // L14: 顺序B：事务回调先到达，Room 数据后发射 -> 成功匹配生成 PinPosition 命令
        val initialTasks = listOf(task1, task2, task3)
        val undoneTasks = TaskOrderEngine.undoFlag(initialTasks, testLists, task1.id, originalMIndex = 2, originalFlagged = false)
        val flagResult = UndoFlagResult(taskId = task1.id, updatedTasks = undoneTasks, lists = testLists)
        val undoRecord = UndoRecord(
            eventId = 2L,
            taskId = task1.id,
            taskTitle = task1.title,
            listId = task1.listId,
            originalMIndex = 2,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var commandCallback: ((UndoActionResult) -> Unit)? = null
        coordinator.onUndoFlag(undoRecord, 1) { cb ->
            commandCallback = cb
        }
        testScheduler.runCurrent()

        // 1. 回调先到达
        commandCallback?.invoke(
            UndoActionResult.Applied(
                eventId = undoRecord.eventId,
                taskId = undoRecord.taskId,
                actionType = UndoActionType.FLAG,
                flagResult = flagResult
            )
        )
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitUndoResultAndPage, coordinator.coordinatorState)

        // 2. Room 数据后发射
        coordinator.onPageDataUpdated(1, undoneTasks, testLists)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitConsumed, coordinator.coordinatorState)
        val cmd = coordinator.viewportCommand
        assertNotNull(cmd)
        assertTrue(cmd?.intent is ViewportIntent.PinPosition)
        coordinator.onCommandConsumed(cmd!!.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        testScheduler.runCurrent()
        coordinator.reset()
    }

    @Test
    fun l15_undo_invalidatedRecord_exitsCoordination() = testScope.runTest {
        // L15: 记录已失效（无操作） -> 优雅回归 Idle，不冻结帧
        val undoRecord = UndoRecord(
            eventId = 3L,
            taskId = task1.id,
            taskTitle = task1.title,
            listId = task1.listId,
            originalMIndex = 0,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var commandCallback: ((UndoActionResult) -> Unit)? = null
        coordinator.onUndoFlag(undoRecord, 1) { cb ->
            commandCallback = cb
        }
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitUndoResultAndPage, coordinator.coordinatorState)

        // 回调返回 Invalidated
        commandCallback?.invoke(UndoActionResult.Invalidated(undoRecord.eventId))
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l16_undo_failedResult_showsErrorAndResets() = testScope.runTest {
        // L16: 事务失败 -> 调用 onShowError 并释放协调器回到 Idle
        val undoRecord = UndoRecord(
            eventId = 4L,
            taskId = task1.id,
            taskTitle = task1.title,
            listId = task1.listId,
            originalMIndex = 0,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var commandCallback: ((UndoActionResult) -> Unit)? = null
        coordinator.onUndoFlag(undoRecord, 1) { cb ->
            commandCallback = cb
        }
        testScheduler.runCurrent()

        val expectedError = "数据库写入冲突"
        commandCallback?.invoke(UndoActionResult.Failed(undoRecord.eventId, RuntimeException(expectedError)))
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertTrue(errorMessages.contains(expectedError))
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l17_undo_timeout2500ms_releasesStateWithoutHanging() = testScope.runTest {
        // L17: 2500ms 超时兜底保护 -> 自动回归 Idle 并刷新最新帧
        val undoRecord = UndoRecord(
            eventId = 5L,
            taskId = task1.id,
            taskTitle = task1.title,
            listId = task1.listId,
            originalMIndex = 0,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        coordinator.onUndoFlag(undoRecord, 1) {
            // 模拟既不回调也未更新 DB
        }
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitUndoResultAndPage, coordinator.coordinatorState)

        // 推进 2500ms
        testScheduler.advanceTimeBy(2500)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l18_undo_taskNotInCurrentPage_doesNotCoordinateViewport() = testScope.runTest {
        // L18: 被撤销项属于清单 2（不属于当前正在查看的清单 1），只执行撤销数据不发视口命令
        val undoRecordForList2 = UndoRecord(
            eventId = 6L,
            taskId = 999,
            taskTitle = "Other List Task",
            listId = 2,
            originalMIndex = 0,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var commandExecuted = false
        coordinator.onUndoFlag(undoRecordForList2, 1) {
            commandExecuted = true
        }
        testScheduler.runCurrent()

        assertTrue(commandExecuted)
        // 协调器不应进入等待视口状态，仍为 Idle
        assertEquals(CoordinatorState.Idle, coordinator.coordinatorState)
        assertNull(coordinator.viewportCommand)
    }

    @Test
    fun l19_undo_preservesMiddleNonZeroOffset() = testScope.runTest {
        // L19: 中段非零 offset 捕获与 PinPosition 交付
        val midListState = LazyListState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 120)
        val midCoordinator = SwipeViewportCoordinator(testScope, midListState) {}
        midCoordinator.initFrameIfNeeded(1, listOf(task1, task2, task3), testLists)

        val initialTasks = listOf(task1, task2, task3)
        val undoneTasks = TaskOrderEngine.undoFlag(initialTasks, testLists, task1.id, originalMIndex = 2, originalFlagged = false)
        val flagResult = UndoFlagResult(taskId = task1.id, updatedTasks = undoneTasks, lists = testLists)
        val undoRecord = UndoRecord(
            eventId = 7L,
            taskId = task1.id,
            taskTitle = task1.title,
            listId = task1.listId,
            originalMIndex = 2,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var commandCallback: ((UndoActionResult) -> Unit)? = null
        midCoordinator.onUndoFlag(undoRecord, 1) { cb ->
            commandCallback = cb
        }
        testScheduler.runCurrent()

        commandCallback?.invoke(
            UndoActionResult.Applied(
                eventId = undoRecord.eventId,
                taskId = undoRecord.taskId,
                actionType = UndoActionType.FLAG,
                flagResult = flagResult
            )
        )
        midCoordinator.onPageDataUpdated(1, undoneTasks, testLists)
        testScheduler.runCurrent()

        val cmd = midCoordinator.viewportCommand
        assertNotNull(cmd)
        val pin = cmd?.intent as ViewportIntent.PinPosition
        assertEquals(1, pin.index)
        assertEquals(120, pin.offset)
        midCoordinator.onCommandConsumed(cmd.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        testScheduler.runCurrent()
        midCoordinator.reset()
    }

    @Test
    fun l20_undo_clampsIndexForShortOrEmptyList() = testScope.runTest {
        // L20: 空列表或短列表边界钳制，不请求非法越界索引
        val emptyCoordinator = SwipeViewportCoordinator(testScope, listState) {}
        emptyCoordinator.initFrameIfNeeded(1, emptyList(), testLists)

        val undoRecord = UndoRecord(
            eventId = 8L,
            taskId = 101,
            taskTitle = "Task",
            listId = 1,
            originalMIndex = 0,
            originalFlagged = false,
            originalCompleted = false,
            actionType = UndoActionType.FLAG
        )

        var executed = false
        emptyCoordinator.onUndoFlag(undoRecord, 1) { cb ->
            executed = true
            cb(UndoActionResult.Applied(8L, 101, UndoActionType.FLAG, UndoFlagResult(101, emptyList(), testLists)))
        }
        testScheduler.runCurrent()
        emptyCoordinator.onPageDataUpdated(1, emptyList(), testLists)
        testScheduler.runCurrent()

        assertTrue(executed)
        // 空列表不发出非法的 PinPosition 命令，安全回 Idle
        assertEquals(CoordinatorState.Idle, emptyCoordinator.coordinatorState)
        assertNull(emptyCoordinator.viewportCommand)
        emptyCoordinator.reset()
    }

    @Test
    fun l501_userInterruption_afterPageDataUpdated_publishesLatestFrameImmediately() = testScope.runTest {
        // L501: 扣住回位，最新页面已到，再中断；覆盖三类页面与两种改旗方向
        val listSummaryA = TaskList(id = 1, name = "清单A(汇总)", themeColor = 0xFF111111L, showInSummary = true)
        val listSummaryB = TaskList(id = 2, name = "清单B(汇总)", themeColor = 0xFF222222L, showInSummary = true)
        val listHiddenC = TaskList(id = 3, name = "清单C(隐藏)", themeColor = 0xFF333333L, showInSummary = false)
        val allTestLists = listOf(listSummaryA, listSummaryB, listHiddenC)

        data class Scenario(
            val name: String,
            val pageId: Int,
            val initialTasks: List<Task>,
            val targetTaskId: Int,
            val isFlagging: Boolean
        )

        val tA1 = Task(id = 101, listId = 1, title = "Task A1", content = "", isFlagged = false, displayOrder = 0)
        val tA2 = Task(id = 102, listId = 1, title = "Task A2", content = "", isFlagged = false, displayOrder = 1)
        val tA3 = Task(id = 103, listId = 1, title = "Task A3", content = "", isFlagged = false, displayOrder = 2)
        val tA1Flagged = tA1.copy(isFlagged = true)

        val tB1 = Task(id = 201, listId = 2, title = "Task B1", content = "", isFlagged = false, displayOrder = 0)
        val tC1 = Task(id = 301, listId = 3, title = "Task C1", content = "", isFlagged = false, displayOrder = 0)
        val tC2 = Task(id = 302, listId = 3, title = "Task C2", content = "", isFlagged = false, displayOrder = 1)

        val scenarios = listOf(
            Scenario("清单A插旗", 1, listOf(tA1, tA2, tA3), tA2.id, isFlagging = true),
            Scenario("清单A取消插旗", 1, listOf(tA1Flagged, tA2, tA3), tA1Flagged.id, isFlagging = false),
            Scenario("隐藏清单C插旗", 3, listOf(tC1, tC2), tC2.id, isFlagging = true),
            Scenario("隐藏清单C取消插旗", 3, listOf(tC1.copy(isFlagged = true), tC2), tC1.id, isFlagging = false),
            Scenario("汇总页插旗", -1, listOf(tA1, tB1), tB1.id, isFlagging = true),
            Scenario("汇总页取消插旗", -1, listOf(tA1.copy(isFlagged = true), tB1), tA1.id, isFlagging = false)
        )

        for (scenario in scenarios) {
            val coord = SwipeViewportCoordinator(testScope, listState) {}
            coord.onPageSelected(scenario.pageId)
            coord.onPageDataUpdated(scenario.pageId, scenario.initialTasks, allTestLists)
            testScheduler.runCurrent()

            val targetTask = scenario.initialTasks.first { it.id == scenario.targetTaskId }
            val toggleResult = TaskOrderEngine.toggleFlag(scenario.initialTasks, allTestLists, targetTask.id)

            coord.onSwipeToggleFlag(targetTask, scenario.pageId) { onComplete ->
                onComplete(Result.success(toggleResult))
            }
            testScheduler.runCurrent()

            val expectedPageTasks = if (scenario.pageId == -1) {
                toggleResult.updatedTasks
            } else {
                toggleResult.updatedTasks.filter { it.listId == scenario.pageId }
            }
            coord.onPageDataUpdated(scenario.pageId, expectedPageTasks, allTestLists)
            testScheduler.runCurrent()

            assertEquals("${scenario.name}: 此时应在等待回位", CoordinatorState.AwaitResultAndPageAndCard, coord.coordinatorState)

            coord.onUserInterruption()
            testScheduler.runCurrent()

            assertEquals("${scenario.name}: 中断后应回归 Idle", CoordinatorState.Idle, coord.coordinatorState)
            assertNull("${scenario.name}: 中断后不应保留视口命令", coord.viewportCommand)

            val frame = coord.currentFrame
            assertNotNull("${scenario.name}: 当前帧不应为空", frame)
            assertEquals("${scenario.name}: 页面ID应正确", scenario.pageId, frame?.pageId)

            val (expectedM, _) = TaskOrderEngine.partitionScope(expectedPageTasks, allTestLists)
            val updatedTarget = frame?.displayTasks?.find { it.id == targetTask.id }
                ?: frame?.completedTasks?.find { it.id == targetTask.id }
            assertNotNull("${scenario.name}: 目标任务应在帧中", updatedTarget)
            assertEquals(
                "${scenario.name}: 目标任务旗帜状态应立即更新为最新",
                scenario.isFlagging,
                updatedTarget?.isFlagged
            )

            val actualMIds = frame?.displayTasks?.map { it.id }
            val expectedMIds = expectedM.map { it.id }
            assertEquals("${scenario.name}: M区顺序应与最新排序结果一致", expectedMIds, actualMIds)

            coord.reset()
        }
    }

    @Test
    fun l502_interruptionFirst_thenLatePageAndCallback_preservesIdleAndDoesNotScroll() = testScope.runTest {
        // L502: 中断先发生，随后依次交付页面和旧回调；再换序为回调先、页面后
        val initialTasks = listOf(
            task1.copy(isFlagged = false, displayOrder = 0),
            task2.copy(isFlagged = false, displayOrder = 1)
        )
        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)

        // 顺序 1：中断 -> 页面更新 -> 回调 -> 回位
        val coord1 = SwipeViewportCoordinator(testScope, listState) {}
        coord1.onPageSelected(1)
        coord1.onPageDataUpdated(1, initialTasks, testLists)
        testScheduler.runCurrent()

        var callback1: ((Result<TaskOrderEngine.ToggleFlagResult>) -> Unit)? = null
        coord1.onSwipeToggleFlag(task1, 1) { cb -> callback1 = cb }
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coord1.coordinatorState)

        coord1.onUserInterruption()
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord1.coordinatorState)

        coord1.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord1.coordinatorState)
        assertNull(coord1.viewportCommand)
        assertTrue(coord1.currentFrame?.displayTasks?.find { it.id == task1.id }?.isFlagged == true)

        callback1?.invoke(Result.success(toggleResult))
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord1.coordinatorState)
        assertNull(coord1.viewportCommand)

        coord1.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord1.coordinatorState)
        assertNull(coord1.viewportCommand)
        coord1.reset()

        // 顺序 2：中断 -> 回调 -> 回位 -> 页面更新
        val coord2 = SwipeViewportCoordinator(testScope, listState) {}
        coord2.onPageSelected(1)
        coord2.onPageDataUpdated(1, initialTasks, testLists)
        testScheduler.runCurrent()

        var callback2: ((Result<TaskOrderEngine.ToggleFlagResult>) -> Unit)? = null
        coord2.onSwipeToggleFlag(task1, 1) { cb -> callback2 = cb }
        testScheduler.runCurrent()

        coord2.onUserInterruption()
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord2.coordinatorState)

        callback2?.invoke(Result.success(toggleResult))
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord2.coordinatorState)

        coord2.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord2.coordinatorState)

        coord2.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.Idle, coord2.coordinatorState)
        assertNull(coord2.viewportCommand)
        assertTrue(coord2.currentFrame?.displayTasks?.find { it.id == task1.id }?.isFlagged == true)
        coord2.reset()
    }

    @Test
    fun l503_pageSwitchBeforeCompletion_clearsOldFrameAndPreventsOldEventsFromAlteringNewPage() = testScope.runTest {
        // L503: 换页前有未完成左滑/撤销；切到汇总、另一清单、空清单，再投递旧事件
        val list1 = TaskList(id = 1, name = "清单1", themeColor = 0xFF111111L, showInSummary = true)
        val list2 = TaskList(id = 2, name = "清单2", themeColor = 0xFF222222L, showInSummary = false)
        val allLists = listOf(list1, list2)

        val page1Tasks = listOf(task1.copy(listId = 1, isFlagged = false))
        val page2Tasks = listOf(task2.copy(listId = 2, isFlagged = true))

        val coord = SwipeViewportCoordinator(testScope, listState) {}
        coord.onPageSelected(1)
        coord.onPageDataUpdated(1, page1Tasks, allLists)
        testScheduler.runCurrent()

        var oldSwipeCallback: ((Result<TaskOrderEngine.ToggleFlagResult>) -> Unit)? = null
        coord.onSwipeToggleFlag(page1Tasks[0], 1) { cb -> oldSwipeCallback = cb }
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coord.coordinatorState)

        coord.onPageSelected(2)
        testScheduler.runCurrent()
        assertNull("切换后新页数据未就绪前，currentFrame 应清空", coord.currentFrame)
        assertEquals(CoordinatorState.Idle, coord.coordinatorState)

        val oldToggleResult = TaskOrderEngine.toggleFlag(page1Tasks, allLists, page1Tasks[0].id)
        oldSwipeCallback?.invoke(Result.success(oldToggleResult))
        coord.onSwipeCardSettled(page1Tasks[0].id)
        coord.onPageDataUpdated(1, oldToggleResult.updatedTasks, allLists)
        testScheduler.runCurrent()

        assertNull(coord.currentFrame)
        assertEquals(CoordinatorState.Idle, coord.coordinatorState)

        coord.onPageDataUpdated(2, page2Tasks, allLists)
        testScheduler.runCurrent()

        assertNotNull(coord.currentFrame)
        assertEquals(2, coord.currentFrame?.pageId)
        assertEquals(page2Tasks.map { it.id }, coord.currentFrame?.displayTasks?.map { it.id })

        coord.onPageSelected(3)
        coord.onPageDataUpdated(3, emptyList(), allLists)
        testScheduler.runCurrent()
        assertEquals(3, coord.currentFrame?.pageId)
        assertTrue(coord.currentFrame?.displayTasks?.isEmpty() == true)

        coord.onPageSelected(-1)
        val summaryTasks = page1Tasks.filter { it.listId == 1 }
        coord.onPageDataUpdated(-1, summaryTasks, allLists)
        testScheduler.runCurrent()
        assertEquals(-1, coord.currentFrame?.pageId)
        assertEquals(summaryTasks.map { it.id }, coord.currentFrame?.displayTasks?.map { it.id })

        coord.reset()
    }

    @Test
    fun l504_extraDataUpdatesDuringCoordination_areNotLostOnInterruptionOrCompletion() = testScope.runTest {
        // L504: 在 AwaitConsumed、AwaitLayout、Placing、Scrolling 期间接收额外同页字段/排序更新后中断或正常结束
        val initialTasks = listOf(
            task1.copy(isFlagged = false, displayOrder = 0, title = "Original 1"),
            task2.copy(isFlagged = false, displayOrder = 1, title = "Original 2")
        )
        val coord = SwipeViewportCoordinator(testScope, listState) {}
        coord.onPageSelected(1)
        coord.onPageDataUpdated(1, initialTasks, testLists)
        testScheduler.runCurrent()

        val toggleResult = TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)
        coord.onSwipeToggleFlag(task1, 1) { cb -> cb(Result.success(toggleResult)) }
        testScheduler.runCurrent()

        coord.onPageDataUpdated(1, toggleResult.updatedTasks, testLists)
        coord.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitConsumed, coord.coordinatorState)

        val modifiedTasks = toggleResult.updatedTasks.map {
            if (it.id == task2.id) it.copy(title = "Updated Title 2") else it
        }
        coord.onPageDataUpdated(1, modifiedTasks, testLists)
        testScheduler.runCurrent()

        val cmd = coord.viewportCommand!!
        coord.onCommandConsumed(cmd.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitLayout, coord.coordinatorState)
        testScheduler.advanceTimeBy(2500)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.Idle, coord.coordinatorState)
        val finalTask2 = coord.currentFrame?.displayTasks?.find { it.id == task2.id }
        assertEquals("Updated Title 2", finalTask2?.title)

        val task1Flagged = task1.copy(isFlagged = true, displayOrder = 0)
        val unflagResult = TaskOrderEngine.toggleFlag(listOf(task1Flagged, task2), testLists, task1Flagged.id)
        coord.onSwipeToggleFlag(task1Flagged, 1) { cb -> cb(Result.success(unflagResult)) }
        testScheduler.runCurrent()

        coord.onPageDataUpdated(1, unflagResult.updatedTasks, testLists)
        coord.onSwipeCardSettled(task1Flagged.id)
        testScheduler.runCurrent()

        val cmd2 = coord.viewportCommand!!
        coord.onCommandConsumed(cmd2.pageId, cmd2.pageEpoch, cmd2.actionId, cmd2.generation)
        testScheduler.runCurrent()
        assertEquals(CoordinatorState.AwaitLayout, coord.coordinatorState)

        val furtherTasks = unflagResult.updatedTasks.map {
            if (it.id == task1.id) it.copy(title = "Task 1 New Title") else it
        }
        coord.onPageDataUpdated(1, furtherTasks, testLists)
        testScheduler.runCurrent()

        coord.onUserInterruption()
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.Idle, coord.coordinatorState)
        assertNull(coord.viewportCommand)
        assertEquals("Task 1 New Title", coord.currentFrame?.displayTasks?.find { it.id == task1.id }?.title)

        coord.reset()
    }

    @Test
    fun l505_interruptionFollowedByNewActionOrDrag_preservesNewActionAndDragFrame() = testScope.runTest {
        // L505: 中断后立刻开始新左滑/撤销/拖拽，再调度旧 job 的 finally、超时和回调；包含连续相同任务操作
        val initialTasks = listOf(
            task1.copy(isFlagged = false, displayOrder = 0),
            task2.copy(isFlagged = false, displayOrder = 1)
        )
        val coord = SwipeViewportCoordinator(testScope, listState) {}
        coord.onPageSelected(1)
        coord.onPageDataUpdated(1, initialTasks, testLists)
        testScheduler.runCurrent()

        var callbackA: ((Result<TaskOrderEngine.ToggleFlagResult>) -> Unit)? = null
        coord.onSwipeToggleFlag(task1, 1) { cb -> callbackA = cb }
        testScheduler.runCurrent()

        coord.onUserInterruption()
        testScheduler.runCurrent()

        val toggleResultB = TaskOrderEngine.toggleFlag(initialTasks, testLists, task2.id)
        var callbackB: ((Result<TaskOrderEngine.ToggleFlagResult>) -> Unit)? = null
        coord.onSwipeToggleFlag(task2, 1) { cb -> callbackB = cb }
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coord.coordinatorState)

        callbackA?.invoke(Result.success(TaskOrderEngine.toggleFlag(initialTasks, testLists, task1.id)))
        coord.onSwipeCardSettled(task1.id)
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitResultAndPageAndCard, coord.coordinatorState)

        coord.onPageDataUpdated(1, toggleResultB.updatedTasks, testLists)
        coord.onSwipeCardSettled(task2.id)
        callbackB?.invoke(Result.success(toggleResultB))
        testScheduler.runCurrent()

        assertEquals(CoordinatorState.AwaitConsumed, coord.coordinatorState)
        assertNotNull(coord.viewportCommand)

        coord.reset()
        testScheduler.runCurrent()

        coord.onPageSelected(1)
        coord.onPageDataUpdated(1, initialTasks, testLists)
        testScheduler.runCurrent()

        coord.onSwipeToggleFlag(task1, 1) { /* 悬挂 */ }
        testScheduler.runCurrent()

        coord.onUserInterruption()
        testScheduler.runCurrent()

        val reorderedTasks = listOf(
            initialTasks[1].copy(displayOrder = 0),
            initialTasks[0].copy(displayOrder = 1)
        )
        val pageTasks = PageTasks(pageId = 1, tasks = reorderedTasks, revision = 99L)
        coord.onDragFrame(1001L, pageTasks, testLists)
        testScheduler.runCurrent()

        assertEquals(reorderedTasks.map { it.id }, coord.currentFrame?.displayTasks?.map { it.id })

        testScheduler.advanceTimeBy(3000)
        testScheduler.runCurrent()

        assertEquals(reorderedTasks.map { it.id }, coord.currentFrame?.displayTasks?.map { it.id })

        coord.reset()
    }
}
