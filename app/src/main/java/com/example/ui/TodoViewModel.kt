package com.example.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppMetadata
import com.example.data.Task
import com.example.data.TaskList
import com.example.data.TaskOrderEngine
import com.example.data.TodoRepository
import com.example.data.UndoFlagResult
import com.example.data.transfer.BackupData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PageTasks(
    val pageId: Int,
    val tasks: List<Task>,
    val revision: Long = 0L
)

data class DragSession(
    val token: Long,
    val pageId: Int,
    val scopeId: Int,
    val baseTasks: List<Task>,
    val orderedMIds: List<Int>,
    val revision: Long,
    val hasMoved: Boolean = false
)

data class PendingDragCommit(
    val token: Long,
    val pageId: Int,
    val orderedMIds: List<Int>,
    val revision: Long
)

sealed class UndoActionResult {
    data class Applied(
        val eventId: Long,
        val taskId: Int,
        val actionType: UndoActionType,
        val flagResult: UndoFlagResult? = null
    ) : UndoActionResult()
    data class Invalidated(val eventId: Long) : UndoActionResult()
    data class Failed(val eventId: Long, val cause: Throwable) : UndoActionResult()
}

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return when (value) {
                LIGHT.storageValue -> LIGHT
                DARK.storageValue -> DARK
                else -> SYSTEM
            }
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoViewModel(
    private val repository: TodoRepository,
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromStorage(sharedPrefs.getString("appearance_mode", null))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("appearance_mode", mode.storageValue).apply()
    }

    val selectedListId = MutableStateFlow(sharedPrefs.getInt("last_list_id", -1))

    val isSelectionMode = MutableStateFlow(false)
    val selectedTaskIds = MutableStateFlow<Set<Int>>(emptySet())
    val isListsLoaded = MutableStateFlow(false)
    val isInitialized = MutableStateFlow(false)

    private val _allLists = MutableStateFlow<List<TaskList>?>(null)
    val allLists: StateFlow<List<TaskList>?> = _allLists

    private val _pageTasks = MutableStateFlow(PageTasks(sharedPrefs.getInt("last_list_id", -1), emptyList(), 0L))
    val pageTasks: StateFlow<PageTasks> = _pageTasks

    val tasks: StateFlow<List<Task>> = _pageTasks.map { it.tasks }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    val undoController = UndoController()
    val undoRecords: StateFlow<List<UndoRecord>> = undoController.undoRecords

    private var dragSessionCounter = 0L
    var activeDragSession by mutableStateOf<DragSession?>(null)
        private set
    var latestPendingCommit by mutableStateOf<PendingDragCommit?>(null)
        private set

    val latestDbTasksByPage = mutableMapOf<Int, List<Task>>()
    val writeMutex = Mutex()

    fun launchCoordinatedWrite(block: suspend () -> Unit): Job {
        return viewModelScope.launch {
            writeMutex.withLock {
                block()
            }
        }
    }

    init {
        // 1. 执行首次 Room 5->6 状态初始化与旧偏好迁移
        viewModelScope.launch {
            writeMutex.withLock {
                val rawExcluded = sharedPrefs.getStringSet("summary_excluded_list_ids", emptySet())
                val oldExcludedIds = rawExcluded?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                repository.initializeSummaryOrderIfNeeded(oldExcludedIds)

                // 移除已废弃的 SharedPreferences 键
                sharedPrefs.edit()
                    .remove("summary_excluded_list_ids")
                    .remove("all_list_completion_mode")
                    .apply()

                isInitialized.value = true
            }
        }

        // 2. 监听全部清单
        viewModelScope.launch {
            repository.allLists.collect { lists ->
                _allLists.value = lists
                isListsLoaded.value = true
            }
        }

        // 3. 监听当前选定视图任务
        viewModelScope.launch {
            selectedListId.flatMapLatest { listId ->
                val sourceFlow = if (listId == -1) {
                    repository.summaryTasks
                } else {
                    repository.getTasksForList(listId)
                }
                sourceFlow.map { PageTasks(listId, it) }
            }.collect { pageData ->
                onDbTasksEmitted(pageData.pageId, pageData.tasks)
            }
        }
    }

    private fun onDbTasksEmitted(pageId: Int, dbTasks: List<Task>) {
        latestDbTasksByPage[pageId] = dbTasks
        if (pageId != selectedListId.value) return

        val session = activeDragSession
        val pending = latestPendingCommit
        val lists = _allLists.value ?: emptyList()

        if (session != null) {
            val (sessionM, sessionC) = TaskOrderEngine.partitionScope(session.baseTasks, lists)
            val (dbM, dbC) = TaskOrderEngine.partitionScope(dbTasks, lists)
            val sessionMIds = sessionM.map { it.id }.toSet()
            val dbMIds = dbM.map { it.id }.toSet()
            val sessionCIds = sessionC.map { it.id }.toSet()
            val dbCIds = dbC.map { it.id }.toSet()

            if (sessionMIds != dbMIds || sessionCIds != dbCIds) {
                // 范围成员或分区变动，取消尚未提交的临时会话
                activeDragSession = null
                val rev = _pageTasks.value.revision + 1
                _pageTasks.value = PageTasks(pageId, dbTasks, rev)
            } else {
                // 成员相同，合并非排序字段
                val dbMap = dbTasks.associateBy { it.id }
                val merged = _pageTasks.value.tasks.map { t ->
                    dbMap[t.id]?.let { dbT ->
                        t.copy(title = dbT.title, content = dbT.content, timestamp = dbT.timestamp)
                    } ?: t
                }
                _pageTasks.value = _pageTasks.value.copy(tasks = merged)
            }
        } else if (pending != null) {
            val (pendingM, _) = TaskOrderEngine.partitionScope(_pageTasks.value.tasks, lists)
            val (dbM, _) = TaskOrderEngine.partitionScope(dbTasks, lists)
            if (pendingM.map { it.id }.toSet() != dbM.map { it.id }.toSet()) {
                latestPendingCommit = null
                val rev = _pageTasks.value.revision + 1
                _pageTasks.value = PageTasks(pageId, dbTasks, rev)
            }
        } else {
            val rev = _pageTasks.value.revision + 1
            _pageTasks.value = PageTasks(pageId, dbTasks, rev)
        }
    }

    fun selectList(listId: Int) {
        if (selectedListId.value != listId) {
            cancelDrag()
            exitSelectionMode()
            selectedListId.value = listId
            sharedPrefs.edit().putInt("last_list_id", listId).apply()
        }
    }

    fun addList(name: String, themeColor: Long, showInSummary: Boolean = true) {
        launchCoordinatedWrite {
            repository.insertList(name, themeColor, showInSummary)
        }
    }

    fun updateListInfo(listId: Int, newName: String, newThemeColor: Long, newCompletionMode: Int) {
        launchCoordinatedWrite {
            val list = _allLists.value?.find { it.id == listId }
            if (list != null) {
                undoController.invalidateList(listId)
                repository.updateList(
                    list.copy(
                        name = newName,
                        themeColor = newThemeColor,
                        completionMode = newCompletionMode
                    )
                )
            }
        }
    }

    fun deleteList(listId: Int) {
        launchCoordinatedWrite {
            undoController.invalidateList(listId)
            repository.deleteList(listId)
            if (selectedListId.value == listId) {
                selectList(-1)
            }
        }
    }

    fun updateSummaryVisibility(newVisibilityMap: Map<Int, Boolean>) {
        launchCoordinatedWrite {
            newVisibilityMap.keys.forEach { undoController.invalidateList(it) }
            repository.updateSummaryVisibility(newVisibilityMap)
        }
    }

    fun addTask(listId: Int, title: String, content: String, isFlagged: Boolean = false) {
        launchCoordinatedWrite {
            repository.insertTask(listId, title, content, isFlagged)
        }
    }

    fun toggleTaskFlag(
        task: Task,
        onComplete: ((Result<TaskOrderEngine.ToggleFlagResult>) -> Unit)? = null
    ): Job {
        return launchCoordinatedWrite {
            try {
                undoController.invalidateTask(task.id)
                val result = repository.toggleTaskFlag(task.id)
                val actionType = if (result.newFlagged) UndoActionType.FLAG else UndoActionType.UNFLAG
                val record = UndoRecord(
                    eventId = undoController.nextEventId(),
                    taskId = task.id,
                    taskTitle = task.title,
                    listId = task.listId,
                    originalMIndex = result.oldMIndex,
                    originalFlagged = !result.newFlagged,
                    originalCompleted = task.isCompleted,
                    actionType = actionType
                )
                undoController.pushRecord(record)
                onComplete?.invoke(Result.success(result))
            } catch (t: Throwable) {
                onComplete?.invoke(Result.failure(t))
            }
        }
    }

    fun completeTask(task: Task) {
        launchCoordinatedWrite {
            undoController.invalidateTask(task.id)
            val result = repository.completeTask(task.id)
            if (result.movedToBottom) {
                val record = UndoRecord(
                    eventId = undoController.nextEventId(),
                    taskId = task.id,
                    taskTitle = task.title,
                    listId = task.listId,
                    originalMIndex = result.oldMIndex,
                    originalFlagged = result.oldFlagged,
                    originalCompleted = false,
                    actionType = UndoActionType.COMPLETE
                )
                undoController.pushRecord(record)
            }
        }
    }

    fun uncompleteTask(task: Task) {
        launchCoordinatedWrite {
            undoController.invalidateTask(task.id)
            repository.uncompleteTask(task.id)
        }
    }

    fun undoAction(
        record: UndoRecord,
        onResult: ((UndoActionResult) -> Unit)? = null
    ): Job {
        return launchCoordinatedWrite {
            val removed = undoController.removeRecord(record.eventId)
            if (removed == null) {
                onResult?.invoke(UndoActionResult.Invalidated(record.eventId))
                return@launchCoordinatedWrite
            }
            try {
                when (removed.actionType) {
                    UndoActionType.COMPLETE -> {
                        repository.undoComplete(removed.taskId, removed.originalMIndex, removed.originalFlagged)
                        onResult?.invoke(
                            UndoActionResult.Applied(
                                eventId = removed.eventId,
                                taskId = removed.taskId,
                                actionType = removed.actionType
                            )
                        )
                    }
                    UndoActionType.FLAG, UndoActionType.UNFLAG -> {
                        val flagResult = repository.undoFlag(removed.taskId, removed.originalMIndex, removed.originalFlagged)
                        onResult?.invoke(
                            UndoActionResult.Applied(
                                eventId = removed.eventId,
                                taskId = removed.taskId,
                                actionType = removed.actionType,
                                flagResult = flagResult
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onResult?.invoke(UndoActionResult.Failed(removed.eventId, t))
            }
        }
    }

    fun dismissUndoRecord(eventId: Long) {
        undoController.removeRecord(eventId)
    }

    private var isDraggingList = false

    fun reorderLists(fromIndex: Int, toIndex: Int) {
        val currentLists = _allLists.value?.toMutableList() ?: return
        if (fromIndex !in currentLists.indices || toIndex !in currentLists.indices) return

        val movedList = currentLists.removeAt(fromIndex)
        currentLists.add(toIndex, movedList)

        _allLists.value = currentLists
        isDraggingList = true
    }

    fun saveListOrder(): Job? {
        val snapshot = _allLists.value?.toList() ?: return null
        isDraggingList = false
        return launchCoordinatedWrite {
            val reordered = snapshot.mapIndexed { index, list ->
                if (list.displayOrder != index) list.copy(displayOrder = index) else list
            }
            _allLists.value = reordered
            if (reordered.isNotEmpty()) {
                repository.updateLists(reordered)
            }
        }
    }

    fun toggleTaskFlag(taskId: Int) {
        val task = _pageTasks.value.tasks.find { it.id == taskId }
        if (task != null) {
            toggleTaskFlag(task)
        } else {
            launchCoordinatedWrite {
                val dbTask = repository.getTaskById(taskId) ?: return@launchCoordinatedWrite
                toggleTaskFlag(dbTask)
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        if (task.isCompleted) {
            uncompleteTask(task)
        } else {
            completeTask(task)
        }
    }

    fun updateTaskDetail(taskId: Int, title: String, content: String, isFlagged: Boolean) {
        launchCoordinatedWrite {
            val task = repository.getTaskById(taskId) ?: return@launchCoordinatedWrite
            repository.updateTaskContent(taskId, title, content)
            if (task.isFlagged != isFlagged) {
                // 如果在 C 中，禁止修改旗帜
                val list = _allLists.value?.find { it.id == task.listId }
                if (task.isCompleted && list?.completionMode == 1) {
                    // 禁止修改
                    return@launchCoordinatedWrite
                }
                toggleTaskFlag(task)
            }
        }
    }

    fun startDragSession(taskId: Int): Long? {
        if (isSelectionMode.value) return null
        val pageId = selectedListId.value
        if (_pageTasks.value.pageId != pageId) return null
        val lists = _allLists.value ?: return null
        if (pageId != -1) {
            val list = lists.find { it.id == pageId }
            if (list?.showInSummary == true) {
                // 参与汇总单清单主区域禁止拖拽
                return null
            }
        }
        val (mTasks, _) = TaskOrderEngine.partitionScope(_pageTasks.value.tasks, lists)
        if (mTasks.none { it.id == taskId }) {
            // C 区域任务或非当前页任务禁止拖拽
            return null
        }

        if (activeDragSession != null) {
            cancelDrag()
        }

        val token = ++dragSessionCounter
        val session = DragSession(
            token = token,
            pageId = pageId,
            scopeId = pageId,
            baseTasks = _pageTasks.value.tasks,
            orderedMIds = mTasks.map { it.id },
            revision = _pageTasks.value.revision,
            hasMoved = false
        )
        activeDragSession = session
        return token
    }

    fun reorderTasks(fromTaskId: Int, toTaskId: Int): PageTasks? {
        if (activeDragSession == null) {
            startDragSession(fromTaskId) ?: return null
        }
        val session = activeDragSession ?: return null
        val lists = _allLists.value ?: return null

        val fromIndex = session.orderedMIds.indexOf(fromTaskId)
        val toIndex = session.orderedMIds.indexOf(toTaskId)
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return null

        val mutableM = session.orderedMIds.toMutableList()
        val moved = mutableM.removeAt(fromIndex)
        mutableM.add(toIndex, moved)

        val (baseM, baseC) = TaskOrderEngine.partitionScope(session.baseTasks, lists)
        val idMap = baseM.associateBy { it.id }
        val reorderedM = mutableM.mapNotNull { idMap[it] }
        if (reorderedM.size != mutableM.size) return null

        val reindexed = TaskOrderEngine.reindexScope(reorderedM, baseC)
        val newRev = _pageTasks.value.revision + 1
        val newPageTasks = PageTasks(session.pageId, reindexed, newRev)

        _pageTasks.value = newPageTasks
        activeDragSession = session.copy(
            orderedMIds = mutableM,
            revision = newRev,
            hasMoved = true
        )
        return newPageTasks
    }

    fun cancelDrag(token: Long? = null) {
        val session = activeDragSession ?: return
        if (token != null && session.token != token) return
        activeDragSession = null
        val realTasks = latestDbTasksByPage[session.pageId] ?: session.baseTasks
        val newRev = _pageTasks.value.revision + 1
        _pageTasks.value = PageTasks(session.pageId, realTasks, newRev)
    }

    fun saveTaskOrder(token: Long? = null): Job? {
        val session = activeDragSession
        if (session == null || (token != null && session.token != token)) {
            return null
        }
        activeDragSession = null
        if (!session.hasMoved) {
            return null
        }

        val pending = PendingDragCommit(
            token = session.token,
            pageId = session.pageId,
            orderedMIds = session.orderedMIds,
            revision = session.revision
        )
        latestPendingCommit = pending

        return launchCoordinatedWrite {
            try {
                val result = repository.commitScopeReorder(pending.pageId, pending.orderedMIds)
                result.onSuccess { committedTasks ->
                    if (activeDragSession == null && latestPendingCommit?.token == pending.token) {
                        latestPendingCommit = null
                        val newRev = _pageTasks.value.revision + 1
                        _pageTasks.value = PageTasks(pending.pageId, committedTasks, newRev)
                    }
                }.onFailure {
                    if (latestPendingCommit?.token == pending.token) {
                        latestPendingCommit = null
                        val realTasks = latestDbTasksByPage[pending.pageId] ?: session.baseTasks
                        val newRev = _pageTasks.value.revision + 1
                        _pageTasks.value = PageTasks(pending.pageId, realTasks, newRev)
                    }
                }
            } catch (_: Throwable) {
                if (latestPendingCommit?.token == pending.token) {
                    latestPendingCommit = null
                    val realTasks = latestDbTasksByPage[pending.pageId] ?: session.baseTasks
                    val newRev = _pageTasks.value.revision + 1
                    _pageTasks.value = PageTasks(pending.pageId, realTasks, newRev)
                }
            }
        }
    }

    fun saveTaskOrder(movedTaskId: Int, targetTaskId: Int): Job? {
        val currentScopeId = selectedListId.value
        val lists = _allLists.value ?: return null
        if (currentScopeId != -1) {
            val list = lists.find { it.id == currentScopeId }
            if (list?.showInSummary == true) {
                cancelDrag()
                return null
            }
        }

        cancelDrag()
        undoController.invalidateTask(movedTaskId)

        return launchCoordinatedWrite {
            repository.reorderScopeTasks(
                scopeListId = currentScopeId,
                movedTaskId = movedTaskId,
                targetTaskId = targetTaskId
            )
        }
    }

    fun cleanCompletedInCurrentScope(onResult: (Int) -> Unit) {
        launchCoordinatedWrite {
            val count = repository.cleanCompletedInScope(selectedListId.value)
            onResult(count)
        }
    }

    suspend fun getTaskById(taskId: Int): Task? {
        return repository.getTaskById(taskId)
    }

    fun updateTaskContent(taskId: Int, title: String, content: String) {
        launchCoordinatedWrite {
            repository.updateTaskContent(taskId, title, content)
        }
    }

    fun deleteTask(taskId: Int) {
        launchCoordinatedWrite {
            undoController.invalidateTask(taskId)
            repository.deleteTask(taskId)
        }
    }

    fun toggleTaskSelection(taskId: Int) {
        val current = selectedTaskIds.value.toMutableSet()
        if (current.contains(taskId)) {
            current.remove(taskId)
        } else {
            current.add(taskId)
        }
        selectedTaskIds.value = current
    }

    fun selectAllTasks(taskIds: List<Int>) {
        if (selectedTaskIds.value.containsAll(taskIds) && selectedTaskIds.value.size == taskIds.size && taskIds.isNotEmpty()) {
            selectedTaskIds.value = emptySet()
        } else {
            selectedTaskIds.value = taskIds.toSet()
        }
    }

    fun enterSelectionMode(taskId: Int) {
        cancelDrag()
        isSelectionMode.value = true
        selectedTaskIds.value = setOf(taskId)
    }

    fun exitSelectionMode() {
        isSelectionMode.value = false
        selectedTaskIds.value = emptySet()
    }

    fun deleteSelectedTasks() {
        launchCoordinatedWrite {
            val ids = selectedTaskIds.value
            ids.forEach {
                undoController.invalidateTask(it)
                repository.deleteTask(it)
            }
            selectedTaskIds.value = emptySet()
            isSelectionMode.value = false
        }
    }

    fun moveSelectedTasksToList(targetListId: Int, onError: (String) -> Unit = {}): Job {
        return launchCoordinatedWrite {
            try {
                val ids = selectedTaskIds.value
                ids.forEach { undoController.invalidateTask(it) }
                repository.moveTasksToList(ids, targetListId)
                exitSelectionMode()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "移动失败")
            }
        }
    }

    fun markSelectedTasksStatus(completed: Boolean) {
        launchCoordinatedWrite {
            val ids = selectedTaskIds.value
            val currentTasks = _pageTasks.value.tasks
            val targetTasks = currentTasks.filter { it.id in ids }
            for (task in targetTasks) {
                if (task.isCompleted != completed) {
                    if (completed) {
                        val res = repository.completeTask(task.id)
                        if (res.movedToBottom) {
                            val record = UndoRecord(
                                eventId = undoController.nextEventId(),
                                taskId = task.id,
                                taskTitle = task.title,
                                listId = task.listId,
                                originalMIndex = res.oldMIndex,
                                originalFlagged = res.oldFlagged,
                                originalCompleted = false,
                                actionType = UndoActionType.COMPLETE
                            )
                            undoController.pushRecord(record)
                        }
                    } else {
                        repository.uncompleteTask(task.id)
                    }
                }
            }
            selectedTaskIds.value = emptySet()
            isSelectionMode.value = false
        }
    }

    suspend fun exportBackup(versionName: String): BackupData {
        return writeMutex.withLock {
            repository.exportBackup(versionName)
        }
    }

    suspend fun importBackup(backup: BackupData, isOverwrite: Boolean): Int {
        return writeMutex.withLock {
            val unflaggedCount = repository.importBackup(backup, isOverwrite)
            if (isOverwrite) {
                undoController.clearAll()
                selectedListId.value = -1
                sharedPrefs.edit().putInt("last_list_id", -1).apply()
                isSelectionMode.value = false
                selectedTaskIds.value = emptySet()
            }
            unflaggedCount
        }
    }

    suspend fun getCurrentDataCounts(): Pair<Int, Int> {
        return writeMutex.withLock {
            val lists = repository.getAllTaskListsSnapshot()
            val tasks = repository.getAllTasksSnapshot()
            Pair(lists.size, tasks.size)
        }
    }
}

class TodoViewModelFactory(
    private val repository: TodoRepository,
    private val sharedPrefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(repository, sharedPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
