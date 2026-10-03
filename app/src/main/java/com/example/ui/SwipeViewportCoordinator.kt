package com.example.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.data.Task
import com.example.data.TaskList
import com.example.data.TaskOrderEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PageDataSnapshot(
    val pageId: Int,
    val tasks: List<Task>,
    val lists: List<TaskList>
)

data class PageFrame(
    val pageId: Int,
    val generation: Long,
    val rawTasks: List<Task>,
    val lists: List<TaskList>,
    val displayTasks: List<Task>,
    val completedTasks: List<Task>,
    val idToMIndex: Map<Int, Int>
)

sealed class ViewportIntent {
    data object KeepKey : ViewportIntent()
    data class PinPosition(val index: Int, val offset: Int) : ViewportIntent()
    data class AnimateTop(val initialIndex: Int, val initialOffset: Int) : ViewportIntent()
}

data class ViewportCommand(
    val actionId: Long,
    val pageEpoch: Long,
    val pageId: Int,
    val generation: Long,
    val intent: ViewportIntent
)

data class PendingSwipeIntent(
    val actionId: Long,
    val pageEpoch: Long,
    val pageId: Int,
    val taskId: Int,
    val wasFlagged: Boolean,
    val oldRenderGeneration: Long,
    val wasAtTrueTop: Boolean,
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
    val firstVisibleItemId: Int?,
    val nextNeighborId: Int?,
    val prevNeighborId: Int?,
    val wasFirstItem: Boolean
)

data class PendingUndoIntent(
    val actionId: Long,
    val pageEpoch: Long,
    val pageId: Int,
    val eventId: Long,
    val taskId: Int,
    val capturedIndex: Int,
    val capturedOffset: Int,
    val oldRenderGeneration: Long
)

enum class CoordinatorState {
    Idle,
    AwaitResultAndPageAndCard,
    AwaitUndoResultAndPage,
    ApplyFrame,
    AwaitConsumed,
    AwaitLayout,
    Placing,
    Scrolling
}

class SwipeViewportCoordinator(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val onShowError: (String) -> Unit
) {
    var currentFrame by mutableStateOf<PageFrame?>(null)
        private set

    var viewportCommand by mutableStateOf<ViewportCommand?>(null)
        private set

    private val commandConsumedFlow = MutableStateFlow(false)
    val isCommandConsumed: Boolean
        get() = commandConsumedFlow.value

    var coordinatorState by mutableStateOf(CoordinatorState.Idle)
        private set

    private var currentActionId = 0L
    private var currentPageEpoch = 0L
    private var currentGeneration = 0L
    private var activePageId: Int = -1

    private var pendingIntent: PendingSwipeIntent? = null
    private var pendingResult: TaskOrderEngine.ToggleFlagResult? = null
    private var pendingUndoIntent: PendingUndoIntent? = null
    private var pendingUndoResult: UndoActionResult? = null
    private var cachedPageData: PageDataSnapshot? = null
    private val latestDbTasks: List<Task>
        get() = cachedPageData?.takeIf { it.pageId == activePageId }?.tasks ?: emptyList()
    private val latestLists: List<TaskList>
        get() = cachedPageData?.takeIf { it.pageId == activePageId }?.lists ?: emptyList()

    private var isCardSettledForActiveAction = false

    private var activeJob: Job? = null
    private var timeoutJob: Job? = null

    fun onPageSelected(pageId: Int) {
        currentPageEpoch++
        cancelActiveOperation(resetFrame = false)
        activePageId = pageId
        cachedPageData = null
        currentFrame = null
    }

    fun initFrameIfNeeded(pageId: Int, tasks: List<Task>, lists: List<TaskList>) {
        if (currentFrame == null || currentFrame?.pageId != pageId) {
            activePageId = pageId
            cachedPageData = PageDataSnapshot(pageId, tasks, lists)
            currentFrame = buildPageFrame(pageId, ++currentGeneration, tasks, lists)
        } else if (cachedPageData == null) {
            cachedPageData = PageDataSnapshot(pageId, tasks, lists)
        }
    }

    fun onPageDataUpdated(pageId: Int, tasks: List<Task>, lists: List<TaskList>) {
        if (pageId != activePageId) {
            return
        }
        cachedPageData = PageDataSnapshot(pageId, tasks, lists)

        if (coordinatorState == CoordinatorState.Idle) {
            publishLatestFrameForActivePage()
            return
        }

        if (coordinatorState == CoordinatorState.AwaitResultAndPageAndCard) {
            checkAndApplyCoordination()
        } else if (coordinatorState == CoordinatorState.AwaitUndoResultAndPage) {
            checkAndApplyUndoCoordination()
        }
    }

    private fun publishLatestFrameForActivePage() {
        val snapshot = cachedPageData ?: return
        if (snapshot.pageId == activePageId) {
            currentFrame = buildPageFrame(activePageId, ++currentGeneration, snapshot.tasks, snapshot.lists)
        }
    }

    private fun retireActiveOperation(publishFrame: Boolean = true) {
        if (coordinatorState == CoordinatorState.Idle && pendingIntent == null && pendingUndoIntent == null) {
            return
        }
        currentPageEpoch++
        activeJob?.cancel()
        activeJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        pendingIntent = null
        pendingResult = null
        pendingUndoIntent = null
        pendingUndoResult = null
        viewportCommand = null
        commandConsumedFlow.value = false
        coordinatorState = CoordinatorState.Idle
        if (publishFrame) {
            publishLatestFrameForActivePage()
        }
    }

    fun onDragFrame(sessionToken: Long, pageTasks: PageTasks, lists: List<TaskList>) {
        if (pageTasks.pageId == activePageId) {
            currentFrame = buildPageFrame(pageTasks.pageId, pageTasks.revision, pageTasks.tasks, lists)
        }
    }

    fun onSwipeToggleFlag(
        task: Task,
        currentPageId: Int,
        executeCommand: (onComplete: (Result<TaskOrderEngine.ToggleFlagResult>) -> Unit) -> Unit
    ) {
        val initialFrame = currentFrame ?: return
        if (currentPageId != activePageId) return

        retireActiveOperation(publishFrame = true)
        val frame = currentFrame ?: initialFrame

        val isTrueTop = (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
        val firstVisibleIdx = listState.firstVisibleItemIndex
        val firstVisibleOffset = listState.firstVisibleItemScrollOffset
        val mTasks = frame.displayTasks
        val firstVisibleId = mTasks.getOrNull(firstVisibleIdx)?.id
        val nextNeighborId = mTasks.getOrNull(firstVisibleIdx + 1)?.id
        val prevNeighborId = if (firstVisibleIdx > 0) mTasks.getOrNull(firstVisibleIdx - 1)?.id else null
        val wasFirst = (mTasks.firstOrNull()?.id == task.id)

        val actionId = ++currentActionId
        val epoch = currentPageEpoch

        val intent = PendingSwipeIntent(
            actionId = actionId,
            pageEpoch = epoch,
            pageId = currentPageId,
            taskId = task.id,
            wasFlagged = task.isFlagged,
            oldRenderGeneration = frame.generation,
            wasAtTrueTop = isTrueTop,
            firstVisibleIndex = firstVisibleIdx,
            firstVisibleOffset = firstVisibleOffset,
            firstVisibleItemId = firstVisibleId,
            nextNeighborId = nextNeighborId,
            prevNeighborId = prevNeighborId,
            wasFirstItem = wasFirst
        )

        pendingIntent = intent
        pendingResult = null
        isCardSettledForActiveAction = false
        commandConsumedFlow.value = false
        coordinatorState = CoordinatorState.AwaitResultAndPageAndCard

        // 2.5 秒超时兜底保护
        timeoutJob = scope.launch {
            delay(2500)
            if (pendingIntent?.actionId == actionId && currentPageEpoch == epoch) {
                finishAction(actionId, epoch, publishFrame = true)
            }
        }

        executeCommand { result ->
            scope.launch {
                if (pendingIntent?.actionId != actionId || currentPageEpoch != epoch) {
                    return@launch
                }
                if (result.isSuccess) {
                    pendingResult = result.getOrThrow()
                    checkAndApplyCoordination()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "操作失败"
                    onShowError(err)
                    finishAction(actionId, epoch, publishFrame = true)
                }
            }
        }
    }

    fun onSwipeCardSettled(taskId: Int) {
        if (pendingIntent?.taskId == taskId && coordinatorState == CoordinatorState.AwaitResultAndPageAndCard) {
            isCardSettledForActiveAction = true
            checkAndApplyCoordination()
        }
    }

    fun onSwipeCardDisposed(taskId: Int) {
        val intent = pendingIntent
        if (intent?.taskId == taskId) {
            finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
        }
    }

    fun onUndoFlag(
        record: UndoRecord,
        currentPageId: Int,
        executeCommand: (onResult: (UndoActionResult) -> Unit) -> Unit
    ) {
        retireActiveOperation(publishFrame = true)
        val frame = currentFrame
        val lists = latestLists
        val isBelongToCurrentPage = if (currentPageId == -1) {
            val list = lists.find { it.id == record.listId }
            list?.showInSummary == true
        } else {
            record.listId == currentPageId
        }

        if (frame == null || currentPageId != activePageId || !isBelongToCurrentPage) {
            // 被撤销项不属于当前页面或协调器未准备好时，只执行数据撤销，不施加本页视口命令
            executeCommand { /* 页面不协调视口 */ }
            return
        }

        val capturedIndex = listState.firstVisibleItemIndex
        val capturedOffset = listState.firstVisibleItemScrollOffset
        val oldGen = frame.generation

        val actionId = ++currentActionId
        val epoch = ++currentPageEpoch

        val intent = PendingUndoIntent(
            actionId = actionId,
            pageEpoch = epoch,
            pageId = currentPageId,
            eventId = record.eventId,
            taskId = record.taskId,
            capturedIndex = capturedIndex,
            capturedOffset = capturedOffset,
            oldRenderGeneration = oldGen
        )

        pendingUndoIntent = intent
        pendingUndoResult = null
        commandConsumedFlow.value = false
        coordinatorState = CoordinatorState.AwaitUndoResultAndPage

        timeoutJob = scope.launch {
            delay(2500)
            if (pendingUndoIntent?.actionId == actionId && currentPageEpoch == epoch) {
                finishAction(actionId, epoch, publishFrame = true)
            }
        }

        executeCommand { result ->
            scope.launch {
                if (pendingUndoIntent?.actionId != actionId || currentPageEpoch != epoch) {
                    return@launch
                }
                when (result) {
                    is UndoActionResult.Applied -> {
                        pendingUndoResult = result
                        checkAndApplyUndoCoordination()
                    }
                    is UndoActionResult.Invalidated -> {
                        finishAction(actionId, epoch, publishFrame = true)
                    }
                    is UndoActionResult.Failed -> {
                        val err = result.cause.message ?: "撤销失败"
                        onShowError(err)
                        finishAction(actionId, epoch, publishFrame = true)
                    }
                }
            }
        }
    }

    private fun checkAndApplyUndoCoordination() {
        if (coordinatorState != CoordinatorState.AwaitUndoResultAndPage) return
        val intent = pendingUndoIntent ?: return
        val appliedResult = pendingUndoResult as? UndoActionResult.Applied ?: return
        val flagResult = appliedResult.flagResult ?: return

        val snapshot = cachedPageData ?: return
        if (snapshot.pageId != intent.pageId) {
            return
        }

        val actionId = intent.actionId
        val epoch = intent.pageEpoch

        val updatedTasksInScope = flagResult.updatedTasks
        val expectedPageTasks = if (intent.pageId == -1) {
            val summaryListIds = flagResult.lists.filter { it.showInSummary }.map { it.id }.toSet()
            updatedTasksInScope.filter { it.listId in summaryListIds }
        } else {
            updatedTasksInScope.filter { it.listId == intent.pageId }
        }
        val (expectedM, expectedC) = TaskOrderEngine.partitionScope(expectedPageTasks, snapshot.lists)
        val (dbM, dbC) = TaskOrderEngine.partitionScope(snapshot.tasks, snapshot.lists)

        if (!isPageMatching(dbM, dbC, expectedM, expectedC)) {
            return
        }

        timeoutJob?.cancel()
        timeoutJob = null

        val newGeneration = ++currentGeneration
        val newIdMap = dbM.mapIndexed { idx, t -> t.id to idx }.toMap()
        val newFrame = PageFrame(
            pageId = intent.pageId,
            generation = newGeneration,
            rawTasks = snapshot.tasks,
            lists = snapshot.lists,
            displayTasks = dbM,
            completedTasks = dbC,
            idToMIndex = newIdMap
        )

        val totalItems = dbM.size + if (dbC.isNotEmpty()) 1 + dbC.size else 0
        val command = if (totalItems > 0) {
            val targetIndex = intent.capturedIndex.coerceIn(0, totalItems - 1)
            ViewportCommand(
                actionId = actionId,
                pageEpoch = epoch,
                pageId = intent.pageId,
                generation = newGeneration,
                intent = ViewportIntent.PinPosition(targetIndex, intent.capturedOffset)
            )
        } else {
            null
        }

        currentFrame = newFrame
        viewportCommand = command
        commandConsumedFlow.value = false

        if (command != null) {
            coordinatorState = CoordinatorState.AwaitConsumed
            startUndoLayoutAwait(intent, command, newFrame)
        } else {
            finishAction(actionId, epoch, publishFrame = true)
        }
    }

    private fun startUndoLayoutAwait(intent: PendingUndoIntent, command: ViewportCommand, frame: PageFrame) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                // 1. 等待 SideEffect 消费命令
                val consumed = withTimeoutOrNull(1000L) {
                    commandConsumedFlow.first { it }
                } ?: false

                if (!consumed || pendingUndoIntent?.actionId != intent.actionId || currentPageEpoch != intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                    return@launch
                }

                // 2. 进入 AwaitLayout 状态，等待列表测量并核对可见项与 frame 一致
                coordinatorState = CoordinatorState.AwaitLayout

                val pinIntent = command.intent as? ViewportIntent.PinPosition
                val layoutPassed = withTimeoutOrNull(2000L) {
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        val visible = layout.visibleItemsInfo
                        if (visible.isEmpty()) {
                            return@snapshotFlow frame.displayTasks.isEmpty() && frame.completedTasks.isEmpty()
                        }
                        val mItems = visible.filter { item ->
                            val key = item.key as? Int
                            key != null && frame.idToMIndex.containsKey(key)
                        }
                        if (mItems.isEmpty() && frame.displayTasks.isNotEmpty() && (pinIntent?.index ?: 0) < frame.displayTasks.size) {
                            return@snapshotFlow false
                        }
                        val allMMatch = mItems.all { item ->
                            val key = item.key as Int
                            frame.idToMIndex[key] == item.index
                        }
                        if (!allMMatch) return@snapshotFlow false

                        if (pinIntent != null) {
                            val isExactMatch = listState.firstVisibleItemIndex == pinIntent.index &&
                                listState.firstVisibleItemScrollOffset == pinIntent.offset
                            val isClampedAtEnd = !listState.canScrollForward && listState.firstVisibleItemIndex <= pinIntent.index
                            val isClampedAtStart = !listState.canScrollBackward && pinIntent.index == 0
                            isExactMatch || isClampedAtEnd || isClampedAtStart
                        } else {
                            true
                        }
                    }.first { it }
                } ?: false

                if (!layoutPassed || pendingUndoIntent?.actionId != intent.actionId || currentPageEpoch != intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                    return@launch
                }

                // 3. 进入 Placing 阶段：执行 180 ms 补位位移过渡
                coordinatorState = CoordinatorState.Placing
                delay(180L)

                if (pendingUndoIntent?.actionId != intent.actionId || currentPageEpoch != intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                    return@launch
                }
            } catch (_: CancellationException) {
                // 用户手势或外部打断
            } finally {
                if (pendingUndoIntent?.actionId == intent.actionId && currentPageEpoch == intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                }
            }
        }
    }

    private fun checkAndApplyCoordination() {
        if (coordinatorState != CoordinatorState.AwaitResultAndPageAndCard) return
        val intent = pendingIntent ?: return
        val result = pendingResult ?: return
        if (!isCardSettledForActiveAction) {
            return
        }

        val snapshot = cachedPageData ?: return
        if (snapshot.pageId != intent.pageId) {
            return
        }

        val actionId = intent.actionId
        val epoch = intent.pageEpoch

        val updatedTasksInScope = result.updatedTasks
        val expectedPageTasks = if (intent.pageId == -1) {
            updatedTasksInScope
        } else {
            updatedTasksInScope.filter { it.listId == intent.pageId }
        }
        val (expectedM, expectedC) = TaskOrderEngine.partitionScope(expectedPageTasks, snapshot.lists)
        val (dbM, dbC) = TaskOrderEngine.partitionScope(snapshot.tasks, snapshot.lists)

        if (!isPageMatching(dbM, dbC, expectedM, expectedC)) {
            return
        }

        timeoutJob?.cancel()
        timeoutJob = null

        val newGeneration = ++currentGeneration
        val newIdMap = dbM.mapIndexed { idx, t -> t.id to idx }.toMap()
        val newFrame = PageFrame(
            pageId = intent.pageId,
            generation = newGeneration,
            rawTasks = snapshot.tasks,
            lists = snapshot.lists,
            displayTasks = dbM,
            completedTasks = dbC,
            idToMIndex = newIdMap
        )

        val isUnflag = intent.wasFlagged
        val intentSpec: ViewportIntent

        if (isUnflag) {
            // 取消插旗
            if (intent.wasAtTrueTop && intent.wasFirstItem) {
                intentSpec = ViewportIntent.PinPosition(0, 0)
            } else if (intent.firstVisibleItemId != intent.taskId) {
                intentSpec = ViewportIntent.KeepKey
            } else {
                val nextIdx = intent.nextNeighborId?.let { newIdMap[it] }
                val prevIdx = intent.prevNeighborId?.let { newIdMap[it] }
                val targetIndex = nextIdx ?: prevIdx ?: intent.firstVisibleIndex.coerceAtMost(dbM.lastIndex.coerceAtLeast(0))
                intentSpec = ViewportIntent.PinPosition(targetIndex, intent.firstVisibleOffset)
            }
        } else {
            // 插旗
            if (intent.wasAtTrueTop && intent.wasFirstItem) {
                intentSpec = ViewportIntent.PinPosition(0, 0)
            } else {
                intentSpec = ViewportIntent.AnimateTop(intent.firstVisibleIndex, intent.firstVisibleOffset)
            }
        }

        val command = ViewportCommand(
            actionId = actionId,
            pageEpoch = epoch,
            pageId = intent.pageId,
            generation = newGeneration,
            intent = intentSpec
        )

        currentFrame = newFrame
        viewportCommand = command
        commandConsumedFlow.value = false
        coordinatorState = CoordinatorState.AwaitConsumed

        startLayoutAwait(intent, command, newFrame)
    }

    private fun startLayoutAwait(intent: PendingSwipeIntent, command: ViewportCommand, frame: PageFrame) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                // 1. 等待 SideEffect 消费命令
                val consumed = withTimeoutOrNull(1000L) {
                    commandConsumedFlow.first { it }
                } ?: false

                if (!consumed || pendingIntent?.actionId != intent.actionId || currentPageEpoch != intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                    return@launch
                }

                // 2. 进入 AwaitLayout 状态，等待列表测量并核对可见项与 frame 一致
                coordinatorState = CoordinatorState.AwaitLayout

                val layoutPassed = withTimeoutOrNull(2000L) {
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        val visible = layout.visibleItemsInfo
                        if (visible.isEmpty()) {
                            return@snapshotFlow frame.displayTasks.isEmpty()
                        }
                        val mItems = visible.filter { item ->
                            val key = item.key as? Int
                            key != null && frame.idToMIndex.containsKey(key)
                        }
                        if (mItems.isEmpty() && frame.displayTasks.isNotEmpty()) {
                            return@snapshotFlow false
                        }
                        val allMMatch = mItems.all { item ->
                            val key = item.key as Int
                            frame.idToMIndex[key] == item.index
                        }
                        if (!allMMatch) return@snapshotFlow false

                        when (command.intent) {
                            is ViewportIntent.PinPosition -> {
                                listState.firstVisibleItemIndex == command.intent.index &&
                                    listState.firstVisibleItemScrollOffset == command.intent.offset
                            }
                            else -> true
                        }
                    }.first { it }
                } ?: false

                if (!layoutPassed || pendingIntent?.actionId != intent.actionId || currentPageEpoch != intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                    return@launch
                }

                // 3. 进入 Placing 阶段：执行 180 ms 补位位移过渡
                coordinatorState = CoordinatorState.Placing
                delay(180L)

                if (pendingIntent?.actionId != intent.actionId || currentPageEpoch != intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                    return@launch
                }

                // 4. 若为 AnimateTop 意图且未在顶部，进入 Scrolling 阶段回顶
                if (command.intent is ViewportIntent.AnimateTop) {
                    if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                        coordinatorState = CoordinatorState.Scrolling
                        listState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            } catch (_: CancellationException) {
                // 用户手势或外部打断
            } finally {
                if (pendingIntent?.actionId == intent.actionId && currentPageEpoch == intent.pageEpoch) {
                    finishAction(intent.actionId, intent.pageEpoch, publishFrame = true)
                }
            }
        }
    }

    fun onCommandConsumed(pageId: Int, epoch: Long, actionId: Long, generation: Long) {
        val matchesSwipe = (pendingIntent?.actionId == actionId)
        val matchesUndo = (pendingUndoIntent?.actionId == actionId)
        if (activePageId == pageId && currentPageEpoch == epoch && (matchesSwipe || matchesUndo) && currentGeneration == generation) {
            commandConsumedFlow.value = true
        }
    }

    private fun finishAction(actionId: Long, epoch: Long, publishFrame: Boolean = true) {
        val matchesSwipe = (pendingIntent?.actionId == actionId && pendingIntent?.pageEpoch == epoch)
        val matchesUndo = (pendingUndoIntent?.actionId == actionId && pendingUndoIntent?.pageEpoch == epoch)
        if (!matchesSwipe && !matchesUndo) {
            return
        }

        activeJob?.cancel()
        activeJob = null
        timeoutJob?.cancel()
        timeoutJob = null

        pendingIntent = null
        pendingResult = null
        pendingUndoIntent = null
        pendingUndoResult = null
        viewportCommand = null
        commandConsumedFlow.value = false
        coordinatorState = CoordinatorState.Idle

        if (publishFrame) {
            publishLatestFrameForActivePage()
        }
    }

    fun onUserInterruption() {
        val hadActiveCoordination = coordinatorState != CoordinatorState.Idle ||
            pendingIntent != null ||
            pendingUndoIntent != null

        currentPageEpoch++
        cancelActiveOperation(resetFrame = hadActiveCoordination)
    }

    private fun cancelActiveOperation(resetFrame: Boolean) {
        activeJob?.cancel()
        activeJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        pendingIntent = null
        pendingResult = null
        pendingUndoIntent = null
        pendingUndoResult = null
        viewportCommand = null
        commandConsumedFlow.value = false
        coordinatorState = CoordinatorState.Idle
        if (resetFrame) {
            publishLatestFrameForActivePage()
        }
    }

    fun reset() {
        onUserInterruption()
    }

    companion object {
        fun buildPageFrame(
            pageId: Int,
            generation: Long,
            rawTasks: List<Task>,
            lists: List<TaskList>
        ): PageFrame {
            val (displayTasks, completedTasks) = TaskOrderEngine.partitionScope(rawTasks, lists)
            val idToMIndex = displayTasks.mapIndexed { idx, task -> task.id to idx }.toMap()
            return PageFrame(
                pageId = pageId,
                generation = generation,
                rawTasks = rawTasks,
                lists = lists,
                displayTasks = displayTasks,
                completedTasks = completedTasks,
                idToMIndex = idToMIndex
            )
        }

        fun isPageMatching(
            pageM: List<Task>,
            pageC: List<Task>,
            expectedM: List<Task>,
            expectedC: List<Task>
        ): Boolean {
            if (pageM.size != expectedM.size || pageC.size != expectedC.size) return false
            for (i in pageM.indices) {
                val a = pageM[i]
                val b = expectedM[i]
                if (a.id != b.id ||
                    a.listId != b.listId ||
                    a.displayOrder != b.displayOrder ||
                    a.isFlagged != b.isFlagged ||
                    a.isCompleted != b.isCompleted ||
                    a.timestamp != b.timestamp
                ) {
                    return false
                }
            }
            for (i in pageC.indices) {
                val a = pageC[i]
                val b = expectedC[i]
                if (a.id != b.id ||
                    a.listId != b.listId ||
                    a.displayOrder != b.displayOrder ||
                    a.isFlagged != b.isFlagged ||
                    a.isCompleted != b.isCompleted ||
                    a.timestamp != b.timestamp
                ) {
                    return false
                }
            }
            return true
        }
    }
}
