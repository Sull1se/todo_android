package com.example.data

import androidx.room.withTransaction
import com.example.data.transfer.BackupData
import com.example.data.transfer.TaskListBackupDto
import com.example.data.transfer.TaskBackupDto
import com.example.data.transfer.currentIsoTimestamp
import kotlinx.coroutines.flow.Flow

data class UndoFlagResult(
    val taskId: Int,
    val updatedTasks: List<Task>,
    val lists: List<TaskList>
)

class TodoRepository(
    private val todoDao: TodoDao,
    private val database: AppDatabase
) {
    val allLists: Flow<List<TaskList>> = todoDao.getAllTaskLists()
    val allTasks: Flow<List<Task>> = todoDao.getAllTasks()
    val summaryTasks: Flow<List<Task>> = todoDao.getSummaryTasks()

    fun getTasksForList(listId: Int): Flow<List<Task>> {
        return todoDao.getTasksForList(listId)
    }

    suspend fun getTaskById(taskId: Int): Task? {
        return todoDao.getTaskById(taskId)
    }

    suspend fun getAllTaskListsSnapshot(): List<TaskList> = todoDao.getAllTaskListsSnapshot()

    suspend fun getAllTasksSnapshot(): List<Task> = todoDao.getAllTasksSnapshot()

    suspend fun getSummaryTasksSnapshot(): List<Task> = todoDao.getSummaryTasksSnapshot()

    suspend fun getTasksForListSnapshot(listId: Int): List<Task> = todoDao.getTasksForListSnapshot(listId)

    /**
     * 首次启动初始化（幂等）：根据旧 SharedPreferences 排除项设置 showInSummary，并对任务按范围规范化排序。
     */
    suspend fun initializeSummaryOrderIfNeeded(oldExcludedIds: Set<Int>): Boolean = database.withTransaction {
        val meta = todoDao.getAppMetadata()
        if (meta?.summaryOrderInitialized == true) {
            return@withTransaction false
        }

        val lists = todoDao.getAllTaskListsSnapshot()
        val existingListIds = lists.map { it.id }.toSet()
        val validExcludedIds = oldExcludedIds.intersect(existingListIds)

        val updatedLists = lists.map {
            if (it.id in validExcludedIds) it.copy(showInSummary = false) else it.copy(showInSummary = true)
        }
        todoDao.updateTaskLists(updatedLists)

        val allTasks = todoDao.getAllTasksSnapshot()
        val listMap = updatedLists.associateBy { it.id }

        // 分别提取 S 与各 L 范围
        val sTasks = allTasks.filter { listMap[it.listId]?.showInSummary == true }
        val (sM, sC) = TaskOrderEngine.partitionScope(sTasks, updatedLists)
        val normalizedSTasks = TaskOrderEngine.reindexScope(sM, sC)

        val hiddenListIds = updatedLists.filter { !it.showInSummary }.map { it.id }
        val normalizedHiddenTasks = mutableListOf<Task>()
        for (hId in hiddenListIds) {
            val hTasks = allTasks.filter { it.listId == hId }
            val (hM, hC) = TaskOrderEngine.partitionScope(hTasks, updatedLists)
            normalizedHiddenTasks.addAll(TaskOrderEngine.reindexScope(hM, hC))
        }

        val allNormalized = normalizedSTasks + normalizedHiddenTasks
        if (allNormalized.isNotEmpty()) {
            todoDao.updateTasks(allNormalized)
        }

        todoDao.setAppMetadata(AppMetadata(id = 1, summaryOrderInitialized = true))
        return@withTransaction true
    }

    suspend fun insertList(name: String, themeColor: Long, showInSummary: Boolean = true): Long = database.withTransaction {
        val maxOrder = todoDao.getMaxListDisplayOrder() ?: -1
        todoDao.insertTaskList(
            TaskList(
                name = name,
                themeColor = themeColor,
                displayOrder = maxOrder + 1,
                showInSummary = showInSummary
            )
        )
    }

    suspend fun updateLists(lists: List<TaskList>) = database.withTransaction {
        todoDao.updateTaskLists(lists)
    }

    suspend fun updateList(taskList: TaskList) = database.withTransaction {
        val oldList = todoDao.getAllTaskListsSnapshot().find { it.id == taskList.id }
        todoDao.updateTaskList(taskList)

        if (oldList != null && oldList.completionMode != taskList.completionMode) {
            val allLists = todoDao.getAllTaskListsSnapshot()
            val scopeTasks = if (taskList.showInSummary) {
                todoDao.getSummaryTasksSnapshot()
            } else {
                todoDao.getTasksForListSnapshot(taskList.id)
            }
            val reordered = TaskOrderEngine.handleListCompletionModeChange(
                scopeTasks = scopeTasks,
                lists = allLists,
                listId = taskList.id,
                newMode = taskList.completionMode
            )
            todoDao.updateTasks(reordered)
        }
    }

    suspend fun deleteList(listId: Int) = database.withTransaction {
        val list = todoDao.getAllTaskListsSnapshot().find { it.id == listId }
        todoDao.deleteTaskList(listId)

        if (list != null && list.showInSummary) {
            val currentLists = todoDao.getAllTaskListsSnapshot()
            val remainingSTasks = todoDao.getSummaryTasksSnapshot()
            val (sM, sC) = TaskOrderEngine.partitionScope(remainingSTasks, currentLists)
            val reordered = TaskOrderEngine.reindexScope(sM, sC)
            if (reordered.isNotEmpty()) {
                todoDao.updateTasks(reordered)
            }
        }
    }

    suspend fun insertTask(
        listId: Int,
        title: String,
        content: String,
        isFlagged: Boolean = false
    ): Long = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val list = allLists.find { it.id == listId }
            ?: throw IllegalArgumentException("清单 $listId 不存在，无法创建待办")

        val scopeTasks = if (list.showInSummary) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(listId)
        }

        val dummyTask = Task(
            id = 0,
            listId = listId,
            title = title,
            content = content,
            isFlagged = isFlagged,
            displayOrder = 0
        )

        val newScopeTasks = TaskOrderEngine.insertTask(scopeTasks, allLists, dummyTask)
        val newTaskId = todoDao.insertTask(dummyTask)

        val updatedScopeTasks = newScopeTasks.map {
            if (it.id == 0) it.copy(id = newTaskId.toInt()) else it
        }
        todoDao.updateTasks(updatedScopeTasks)
        newTaskId
    }

    suspend fun updateTaskContent(taskId: Int, title: String, content: String) = database.withTransaction {
        val existing = todoDao.getTaskById(taskId) ?: return@withTransaction
        todoDao.updateTask(existing.copy(title = title, content = content))
    }

    suspend fun toggleTaskFlag(taskId: Int): TaskOrderEngine.ToggleFlagResult = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val task = todoDao.getTaskById(taskId)
            ?: throw IllegalArgumentException("任务 $taskId 不存在")
        val list = allLists.find { it.id == task.listId }

        val scopeTasks = if (list?.showInSummary == true) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(task.listId)
        }

        val result = TaskOrderEngine.toggleFlag(scopeTasks, allLists, taskId)
        todoDao.updateTasks(result.updatedTasks)
        result
    }

    suspend fun completeTask(taskId: Int): TaskOrderEngine.CompleteTaskResult = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val task = todoDao.getTaskById(taskId)
            ?: throw IllegalArgumentException("任务 $taskId 不存在")
        val list = allLists.find { it.id == task.listId }

        val scopeTasks = if (list?.showInSummary == true) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(task.listId)
        }

        val result = TaskOrderEngine.completeTask(scopeTasks, allLists, taskId)
        todoDao.updateTasks(result.updatedTasks)
        result
    }

    suspend fun uncompleteTask(taskId: Int) = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val task = todoDao.getTaskById(taskId)
            ?: throw IllegalArgumentException("任务 $taskId 不存在")
        val list = allLists.find { it.id == task.listId }

        val scopeTasks = if (list?.showInSummary == true) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(task.listId)
        }

        val updated = TaskOrderEngine.uncompleteTask(scopeTasks, allLists, taskId)
        todoDao.updateTasks(updated)
    }

    suspend fun undoComplete(taskId: Int, originalMIndex: Int, originalFlagged: Boolean) = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val task = todoDao.getTaskById(taskId)
            ?: throw IllegalArgumentException("任务 $taskId 不存在")
        val list = allLists.find { it.id == task.listId }

        val scopeTasks = if (list?.showInSummary == true) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(task.listId)
        }

        val updated = TaskOrderEngine.undoComplete(
            scopeTasks = scopeTasks,
            lists = allLists,
            taskId = taskId,
            originalMIndex = originalMIndex,
            originalFlagged = originalFlagged
        )
        todoDao.updateTasks(updated)
    }

    suspend fun undoFlag(taskId: Int, originalMIndex: Int, originalFlagged: Boolean): UndoFlagResult = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val task = todoDao.getTaskById(taskId)
            ?: throw IllegalArgumentException("任务 $taskId 不存在")
        val list = allLists.find { it.id == task.listId }

        val scopeTasks = if (list?.showInSummary == true) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(task.listId)
        }

        val updated = TaskOrderEngine.undoFlag(
            scopeTasks = scopeTasks,
            lists = allLists,
            taskId = taskId,
            originalMIndex = originalMIndex,
            originalFlagged = originalFlagged
        )
        todoDao.updateTasks(updated)
        UndoFlagResult(
            taskId = taskId,
            updatedTasks = updated,
            lists = allLists
        )
    }

    suspend fun reorderScopeTasks(
        scopeListId: Int, // -1 表示汇总 S, 正数表示某个隐藏清单 L(id)
        movedTaskId: Int,
        targetTaskId: Int
    ): Boolean = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val scopeTasks = if (scopeListId == -1) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            val list = allLists.find { it.id == scopeListId }
            if (list?.showInSummary == true) {
                // 参与汇总的独立清单禁止拖拽
                return@withTransaction false
            }
            todoDao.getTasksForListSnapshot(scopeListId)
        }

        val updated = TaskOrderEngine.reorderM(
            scopeTasks = scopeTasks,
            lists = allLists,
            movedTaskId = movedTaskId,
            targetTaskId = targetTaskId
        ) ?: return@withTransaction false

        todoDao.updateTasks(updated)
        return@withTransaction true
    }

    suspend fun commitScopeReorder(
        scopeListId: Int, // -1 表示汇总 S, 正数表示某个隐藏清单 L(id)
        orderedMIds: List<Int>
    ): Result<List<Task>> = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        if (scopeListId != -1) {
            val list = allLists.find { it.id == scopeListId }
            if (list?.showInSummary == true) {
                return@withTransaction Result.failure(IllegalStateException("参与汇总的独立清单禁止拖拽"))
            }
        }
        val scopeTasks = if (scopeListId == -1) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(scopeListId)
        }
        val (currentM, currentC) = TaskOrderEngine.partitionScope(scopeTasks, allLists)
        val currentMIds = currentM.map { it.id }
        if (orderedMIds.size != currentMIds.size || orderedMIds.toSet() != currentMIds.toSet()) {
            return@withTransaction Result.failure(IllegalStateException("待办列表已发生变动，重排冲突"))
        }
        val map = currentM.associateBy { it.id }
        val reorderedM = orderedMIds.mapNotNull { map[it] }
        if (reorderedM.size != orderedMIds.size) {
            return@withTransaction Result.failure(IllegalStateException("重排任务成员不匹配"))
        }
        val reindexed = TaskOrderEngine.reindexScope(reorderedM, currentC)
        todoDao.updateTasks(reindexed)
        Result.success(reindexed)
    }

    suspend fun updateTasks(tasks: List<Task>) = database.withTransaction {
        todoDao.updateTasks(tasks)
    }

    suspend fun moveTasksToList(taskIds: Set<Int>, targetListId: Int) = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val allTasks = todoDao.getAllTasksSnapshot()

        val updated = TaskOrderEngine.moveTasksToList(
            allTasks = allTasks,
            allLists = allLists,
            taskIds = taskIds,
            targetListId = targetListId
        )
        todoDao.updateTasks(updated)
    }

    suspend fun deleteTask(taskId: Int) = database.withTransaction {
        val task = todoDao.getTaskById(taskId) ?: return@withTransaction
        val allLists = todoDao.getAllTaskListsSnapshot()
        val list = allLists.find { it.id == task.listId }

        todoDao.deleteTask(taskId)

        val remaining = if (list?.showInSummary == true) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(task.listId)
        }
        val (m, c) = TaskOrderEngine.partitionScope(remaining, allLists)
        val reindexed = TaskOrderEngine.reindexScope(m, c)
        if (reindexed.isNotEmpty()) {
            todoDao.updateTasks(reindexed)
        }
    }

    suspend fun cleanCompletedInScope(scopeListId: Int): Int = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val scopeTasks = if (scopeListId == -1) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(scopeListId)
        }

        val listMap = allLists.associateBy { it.id }
        val toDeleteIds = scopeTasks.filter {
            it.isCompleted && listMap[it.listId]?.completionMode == 1
        }.map { it.id }

        if (toDeleteIds.isEmpty()) return@withTransaction 0

        todoDao.deleteTasksByIds(toDeleteIds)

        val remaining = if (scopeListId == -1) {
            todoDao.getSummaryTasksSnapshot()
        } else {
            todoDao.getTasksForListSnapshot(scopeListId)
        }
        val (m, c) = TaskOrderEngine.partitionScope(remaining, allLists)
        val reindexed = TaskOrderEngine.reindexScope(m, c)
        if (reindexed.isNotEmpty()) {
            todoDao.updateTasks(reindexed)
        }
        toDeleteIds.size
    }

    suspend fun updateSummaryVisibility(newVisibilityMap: Map<Int, Boolean>) = database.withTransaction {
        val allLists = todoDao.getAllTaskListsSnapshot()
        val allTasks = todoDao.getAllTasksSnapshot()

        val updatedTasks = TaskOrderEngine.handleSummaryDisplayChanges(
            allTasks = allTasks,
            allLists = allLists,
            newVisibilityMap = newVisibilityMap
        )

        val updatedLists = allLists.map {
            if (newVisibilityMap.containsKey(it.id)) it.copy(showInSummary = newVisibilityMap[it.id]!!) else it
        }

        todoDao.updateTaskLists(updatedLists)
        if (updatedTasks.isNotEmpty()) {
            todoDao.updateTasks(updatedTasks)
        }
    }

    suspend fun exportBackup(versionName: String): BackupData = database.withTransaction {
        val lists = todoDao.getAllTaskListsSnapshot()
        val allTasks = todoDao.getAllTasksSnapshot()

        // 规范导出顺序：S 优先，随后按隐藏清单 Tab 顺序，各范围 displayOrder
        val listMap = lists.associateBy { it.id }
        val sTasks = allTasks.filter { listMap[it.listId]?.showInSummary == true }
            .sortedWith(compareBy({ it.displayOrder }, { it.timestamp }, { it.id }))

        val hiddenLists = lists.filter { !it.showInSummary }
            .sortedWith(compareBy({ it.displayOrder }, { it.timestamp }, { it.id }))

        val hiddenTasks = mutableListOf<Task>()
        for (hl in hiddenLists) {
            val tasksInHl = allTasks.filter { it.listId == hl.id }
                .sortedWith(compareBy({ it.displayOrder }, { it.timestamp }, { it.id }))
            hiddenTasks.addAll(tasksInHl)
        }

        val exportTasks = sTasks + hiddenTasks

        BackupData(
            format = BackupData.BACKUP_FORMAT,
            formatVersion = BackupData.CURRENT_FORMAT_VERSION,
            exportedAt = currentIsoTimestamp(),
            sourceAppVersion = versionName,
            lists = lists.map {
                TaskListBackupDto(
                    id = it.id,
                    name = it.name,
                    themeColor = it.themeColor,
                    displayOrder = it.displayOrder,
                    completionMode = it.completionMode,
                    timestamp = it.timestamp,
                    showInSummary = it.showInSummary
                )
            },
            tasks = exportTasks.map {
                TaskBackupDto(
                    id = it.id,
                    listId = it.listId,
                    title = it.title,
                    content = it.content,
                    isCompleted = it.isCompleted,
                    isFlagged = it.isFlagged,
                    displayOrder = it.displayOrder,
                    timestamp = it.timestamp
                )
            }
        )
    }

    /**
     * 导入备份数据。返回被清旗的收至底部已完成项数量。
     */
    suspend fun importBackup(backup: BackupData, isOverwrite: Boolean): Int = database.withTransaction {
        var unflaggedCount = 0

        if (isOverwrite) {
            todoDao.deleteAllTasks()
            todoDao.deleteAllTaskLists()

            val idMapping = mutableMapOf<Int, Int>()
            val insertedLists = mutableListOf<TaskList>()

            for (listDto in backup.lists) {
                val list = TaskList(
                    id = 0,
                    name = listDto.name,
                    themeColor = listDto.themeColor,
                    displayOrder = listDto.displayOrder,
                    completionMode = listDto.completionMode,
                    timestamp = listDto.timestamp,
                    showInSummary = listDto.showInSummary
                )
                val newId = todoDao.insertTaskList(list).toInt()
                idMapping[listDto.id] = newId
                insertedLists.add(list.copy(id = newId))
            }

            val listMap = insertedLists.associateBy { it.id }
            val remappedTasks = mutableListOf<Task>()

            for (taskDto in backup.tasks) {
                val newListId = idMapping[taskDto.listId]
                    ?: throw IllegalStateException("待办引用的清单 ID ${taskDto.listId} 未能完成重映射")
                val targetList = listMap[newListId]
                val willUnflag = taskDto.isCompleted && targetList?.completionMode == 1 && taskDto.isFlagged
                if (willUnflag) {
                    unflaggedCount++
                }
                remappedTasks.add(
                    Task(
                        id = 0,
                        listId = newListId,
                        title = taskDto.title,
                        content = taskDto.content,
                        isCompleted = taskDto.isCompleted,
                        displayOrder = taskDto.displayOrder,
                        timestamp = taskDto.timestamp,
                        isFlagged = if (taskDto.isCompleted && targetList?.completionMode == 1) false else taskDto.isFlagged
                    )
                )
            }

            // 按各范围稳定规范重排后插入
            val sTasks = remappedTasks.filter { listMap[it.listId]?.showInSummary == true }
            val (sM, sC) = TaskOrderEngine.partitionScope(sTasks, insertedLists)
            val finalSTasks = TaskOrderEngine.reindexScope(sM, sC)

            val hiddenLists = insertedLists.filter { !it.showInSummary }
            val finalHTasks = mutableListOf<Task>()
            for (hl in hiddenLists) {
                val hTasks = remappedTasks.filter { it.listId == hl.id }
                val (hM, hC) = TaskOrderEngine.partitionScope(hTasks, insertedLists)
                finalHTasks.addAll(TaskOrderEngine.reindexScope(hM, hC))
            }

            val allToInsert = finalSTasks + finalHTasks
            if (allToInsert.isNotEmpty()) {
                todoDao.insertTasks(allToInsert)
            }

            // 确保 metadata 保持已初始化
            todoDao.setAppMetadata(AppMetadata(id = 1, summaryOrderInitialized = true))
        } else {
            // 追加模式
            val currentLists = todoDao.getAllTaskListsSnapshot()
            val baseListOrder = todoDao.getMaxListDisplayOrder() ?: -1
            val sortedLists = backup.lists.sortedWith(
                compareBy({ it.displayOrder }, { it.timestamp }, { it.id })
            )

            val idMapping = mutableMapOf<Int, Int>()
            val newlyInsertedLists = mutableListOf<TaskList>()

            for ((index, listDto) in sortedLists.withIndex()) {
                val newDisplayOrder = Math.addExact(baseListOrder, index + 1)
                val list = TaskList(
                    id = 0,
                    name = listDto.name,
                    themeColor = listDto.themeColor,
                    displayOrder = newDisplayOrder,
                    completionMode = listDto.completionMode,
                    timestamp = listDto.timestamp,
                    showInSummary = listDto.showInSummary
                )
                val newId = todoDao.insertTaskList(list).toInt()
                idMapping[listDto.id] = newId
                newlyInsertedLists.add(list.copy(id = newId))
            }

            val allCombinedLists = currentLists + newlyInsertedLists
            val listMap = allCombinedLists.associateBy { it.id }

            val remappedNewTasks = mutableListOf<Task>()
            for (taskDto in backup.tasks) {
                val newListId = idMapping[taskDto.listId]
                    ?: throw IllegalStateException("待办引用的清单 ID ${taskDto.listId} 未能完成重映射")
                val targetList = listMap[newListId]
                val willUnflag = taskDto.isCompleted && targetList?.completionMode == 1 && taskDto.isFlagged
                if (willUnflag) {
                    unflaggedCount++
                }
                remappedNewTasks.add(
                    Task(
                        id = 0,
                        listId = newListId,
                        title = taskDto.title,
                        content = taskDto.content,
                        isCompleted = taskDto.isCompleted,
                        displayOrder = taskDto.displayOrder,
                        timestamp = taskDto.timestamp,
                        isFlagged = if (taskDto.isCompleted && targetList?.completionMode == 1) false else taskDto.isFlagged
                    )
                )
            }

            // 针对新参与汇总的清单：成块合并（F 置于已有 S.M 首部，U 置于尾部，C 置于已有 S.C 尾部）
            val existingSTasks = todoDao.getSummaryTasksSnapshot()
            val (existingSM, existingSC) = TaskOrderEngine.partitionScope(existingSTasks, allCombinedLists)

            val newSTasks = remappedNewTasks.filter { listMap[it.listId]?.showInSummary == true }
            val (newSM, newSC) = TaskOrderEngine.partitionScope(newSTasks, allCombinedLists)

            val newSMFlagged = newSM.filter { it.isFlagged }
            val newSMUnflagged = newSM.filter { !it.isFlagged }

            val combinedSM = newSMFlagged + existingSM + newSMUnflagged
            val combinedSC = existingSC + newSC
            val reindexedS = TaskOrderEngine.reindexScope(combinedSM, combinedSC)

            // 更新已存在的 S 任务（displayOrder 可能微调）
            val (existingToUpdate, newToInsertS) = reindexedS.partition { it.id != 0 }
            if (existingToUpdate.isNotEmpty()) {
                todoDao.updateTasks(existingToUpdate)
            }
            if (newToInsertS.isNotEmpty()) {
                todoDao.insertTasks(newToInsertS)
            }

            // 针对新加入的隐藏清单：各清单独立重排后插入
            val newHiddenLists = newlyInsertedLists.filter { !it.showInSummary }
            for (nhl in newHiddenLists) {
                val hTasks = remappedNewTasks.filter { it.listId == nhl.id }
                val (hM, hC) = TaskOrderEngine.partitionScope(hTasks, allCombinedLists)
                val reindexedH = TaskOrderEngine.reindexScope(hM, hC)
                if (reindexedH.isNotEmpty()) {
                    todoDao.insertTasks(reindexedH)
                }
            }
        }
        unflaggedCount
    }
}
