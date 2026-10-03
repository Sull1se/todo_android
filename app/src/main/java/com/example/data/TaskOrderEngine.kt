package com.example.data

import kotlin.math.min

/**
 * 待办排序与分区纯业务逻辑引擎。
 * 排序范围说明：
 * - 汇总范围 S：所有 showInSummary == true 的清单任务。
 * - 独立范围 L(id)：showInSummary == false 的单一清单任务。
 *
 * 每个范围规范顺序为 M (主区域) + C (底部已完成区域)。
 * C 区域满足：task.isCompleted == true 且 所属清单 completionMode == 1。
 * C 区域任务 isFlagged 必为 false，且禁止拖拽、插旗或跨清单移动。
 */
object TaskOrderEngine {

    /**
     * 判断任务是否属于底部已完成区域 C
     */
    fun isBottomCompleted(task: Task, list: TaskList?): Boolean {
        return task.isCompleted && (list?.completionMode == 1)
    }

    /**
     * 将一个范围内的任务稳定分为 (主区域 M, 底部已完成区域 C)
     * 同时保证 C 区域任务 isFlagged 归一化为 false。
     */
    fun partitionScope(tasks: List<Task>, lists: List<TaskList>): Pair<List<Task>, List<Task>> {
        val listMap = lists.associateBy { it.id }
        val sorted = tasks.sortedWith(compareBy({ it.displayOrder }, { it.timestamp }, { it.id }))

        val mList = mutableListOf<Task>()
        val cList = mutableListOf<Task>()

        for (task in sorted) {
            val list = listMap[task.listId]
            if (isBottomCompleted(task, list)) {
                cList.add(if (task.isFlagged) task.copy(isFlagged = false) else task)
            } else {
                mList.add(task)
            }
        }
        return Pair(mList, cList)
    }

    /**
     * 重新计算一个范围内的规范 displayOrder (0..n-1)，并返回完整任务列表
     */
    fun reindexScope(mTasks: List<Task>, cTasks: List<Task>): List<Task> {
        val result = ArrayList<Task>(mTasks.size + cTasks.size)
        var order = 0
        for (t in mTasks) {
            result.add(if (t.displayOrder != order) t.copy(displayOrder = order) else t)
            order++
        }
        for (t in cTasks) {
            val normalized = if (t.isFlagged) t.copy(isFlagged = false) else t
            result.add(if (normalized.displayOrder != order) normalized.copy(displayOrder = order) else normalized)
            order++
        }
        return result
    }

    /**
     * 在主区域 M 内拖拽重排
     */
    fun reorderM(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        movedTaskId: Int,
        targetTaskId: Int
    ): List<Task>? {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val fromIndex = mTasks.indexOfFirst { it.id == movedTaskId }
        val toIndex = mTasks.indexOfFirst { it.id == targetTaskId }
        if (fromIndex == -1 || toIndex == -1) return null

        val mutableM = mTasks.toMutableList()
        val moved = mutableM.removeAt(fromIndex)
        mutableM.add(toIndex, moved)

        return reindexScope(mutableM, cTasks)
    }

    data class ToggleFlagResult(
        val updatedTasks: List<Task>,
        val taskId: Int,
        val newFlagged: Boolean,
        val oldMIndex: Int
    )

    /**
     * 切换主区域任务的旗帜状态
     * 规则：插旗置顶 (M 首位)，取消插旗置底 (M 末尾)。C 区域任务禁止插旗。
     */
    fun toggleFlag(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        taskId: Int
    ): ToggleFlagResult {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val index = mTasks.indexOfFirst { it.id == taskId }
        if (index == -1) {
            if (cTasks.any { it.id == taskId }) {
                throw IllegalStateException("底部已完成待办禁止插旗")
            }
            throw IllegalArgumentException("任务 $taskId 不在当前排序范围主区域中")
        }

        val task = mTasks[index]
        val willBeFlagged = !task.isFlagged
        val mutableM = mTasks.toMutableList()
        mutableM.removeAt(index)

        if (willBeFlagged) {
            mutableM.add(0, task.copy(isFlagged = true))
        } else {
            mutableM.add(task.copy(isFlagged = false))
        }

        val reindexed = reindexScope(mutableM, cTasks)
        return ToggleFlagResult(
            updatedTasks = reindexed,
            taskId = taskId,
            newFlagged = willBeFlagged,
            oldMIndex = index
        )
    }

    data class CompleteTaskResult(
        val updatedTasks: List<Task>,
        val taskId: Int,
        val movedToBottom: Boolean,
        val oldMIndex: Int,
        val oldFlagged: Boolean
    )

    /**
     * 标记任务完成
     * - 若所属清单 completionMode == 1: 清旗，移入 C 末尾，生成撤销数据。
     * - 若所属清单 completionMode == 0: 保持原位，旗帜不变。
     */
    fun completeTask(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        taskId: Int
    ): CompleteTaskResult {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val mIndex = mTasks.indexOfFirst { it.id == taskId }

        if (mIndex == -1) {
            // 已在 C 中，重复完成为无操作
            if (cTasks.any { it.id == taskId }) {
                return CompleteTaskResult(
                    updatedTasks = reindexScope(mTasks, cTasks),
                    taskId = taskId,
                    movedToBottom = false,
                    oldMIndex = -1,
                    oldFlagged = false
                )
            }
            throw IllegalArgumentException("任务 $taskId 未在当前排序范围找到")
        }

        val task = mTasks[mIndex]
        val list = lists.find { it.id == task.listId }
        val mode = list?.completionMode ?: 0

        return if (mode == 1) {
            val mutableM = mTasks.toMutableList()
            mutableM.removeAt(mIndex)
            val mutableC = cTasks.toMutableList()
            mutableC.add(task.copy(isCompleted = true, isFlagged = false))

            CompleteTaskResult(
                updatedTasks = reindexScope(mutableM, mutableC),
                taskId = taskId,
                movedToBottom = true,
                oldMIndex = mIndex,
                oldFlagged = task.isFlagged
            )
        } else {
            val mutableM = mTasks.toMutableList()
            mutableM[mIndex] = task.copy(isCompleted = true)
            CompleteTaskResult(
                updatedTasks = reindexScope(mutableM, cTasks),
                taskId = taskId,
                movedToBottom = false,
                oldMIndex = mIndex,
                oldFlagged = task.isFlagged
            )
        }
    }

    /**
     * 普通取消完成 (非撤销动作)
     * - 若原在 C 中: 移除并追加到对应 M 末尾，旗帜必为 false。
     * - 若原在 M 中: 保持原位，isCompleted = false。
     */
    fun uncompleteTask(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        taskId: Int
    ): List<Task> {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val cIndex = cTasks.indexOfFirst { it.id == taskId }

        if (cIndex != -1) {
            val task = cTasks[cIndex]
            val mutableC = cTasks.toMutableList()
            mutableC.removeAt(cIndex)
            val mutableM = mTasks.toMutableList()
            mutableM.add(task.copy(isCompleted = false, isFlagged = false))
            return reindexScope(mutableM, mutableC)
        }

        val mIndex = mTasks.indexOfFirst { it.id == taskId }
        if (mIndex != -1) {
            val task = mTasks[mIndex]
            val mutableM = mTasks.toMutableList()
            mutableM[mIndex] = task.copy(isCompleted = false)
            return reindexScope(mutableM, cTasks)
        }

        throw IllegalArgumentException("任务 $taskId 未在当前排序范围找到")
    }

    /**
     * 撤销完成：恢复完成前的未完成状态、原旗帜和原 M 索引
     */
    fun undoComplete(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        taskId: Int,
        originalMIndex: Int,
        originalFlagged: Boolean
    ): List<Task> {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val mutableC = cTasks.toMutableList()
        val cIndex = mutableC.indexOfFirst { it.id == taskId }
        val task = if (cIndex != -1) {
            mutableC.removeAt(cIndex)
        } else {
            // 如果已经被移入 M 或其他位置，从 M 中提取
            val mIdx = mTasks.indexOfFirst { it.id == taskId }
            if (mIdx != -1) {
                val mutableM = mTasks.toMutableList()
                val t = mutableM.removeAt(mIdx)
                val targetIndex = min(originalMIndex, mutableM.size).coerceAtLeast(0)
                mutableM.add(targetIndex, t.copy(isCompleted = false, isFlagged = originalFlagged))
                return reindexScope(mutableM, cTasks)
            }
            throw IllegalArgumentException("任务 $taskId 未在当前范围中找到")
        }

        val mutableM = mTasks.toMutableList()
        val targetIndex = min(originalMIndex, mutableM.size).coerceAtLeast(0)
        mutableM.add(targetIndex, task.copy(isCompleted = false, isFlagged = originalFlagged))

        return reindexScope(mutableM, mutableC)
    }

    /**
     * 撤销插旗/取消插旗：恢复原旗帜和原 M 索引
     */
    fun undoFlag(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        taskId: Int,
        originalMIndex: Int,
        originalFlagged: Boolean
    ): List<Task> {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val mIndex = mTasks.indexOfFirst { it.id == taskId }
        if (mIndex == -1) {
            throw IllegalArgumentException("任务 $taskId 未在主区域找到")
        }

        val mutableM = mTasks.toMutableList()
        val task = mutableM.removeAt(mIndex)
        val targetIndex = min(originalMIndex, mutableM.size).coerceAtLeast(0)
        mutableM.add(targetIndex, task.copy(isFlagged = originalFlagged))

        return reindexScope(mutableM, cTasks)
    }

    /**
     * 清单完成模式变更关联调整
     * - 0 -> 1: 将该清单已完成项按当前顺序从 M 提取，清旗，追加到对应 C 尾部。
     * - 1 -> 0: 将该清单 C 中的项按当前顺序移到对应 M 尾部，保留已完成状态（旗帜仍为 false）。
     */
    fun handleListCompletionModeChange(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        listId: Int,
        newMode: Int
    ): List<Task> {
        val (oldM, oldC) = partitionScope(scopeTasks, lists)

        return if (newMode == 1) {
            val newM = mutableListOf<Task>()
            val toAppendC = mutableListOf<Task>()
            for (t in oldM) {
                if (t.listId == listId && t.isCompleted) {
                    toAppendC.add(t.copy(isFlagged = false))
                } else {
                    newM.add(t)
                }
            }
            val newC = oldC + toAppendC
            reindexScope(newM, newC)
        } else {
            val newC = mutableListOf<Task>()
            val toAppendM = mutableListOf<Task>()
            for (t in oldC) {
                if (t.listId == listId) {
                    toAppendM.add(t.copy(isFlagged = false))
                } else {
                    newC.add(t)
                }
            }
            val newM = oldM + toAppendM
            reindexScope(newM, newC)
        }
    }

    /**
     * 清单展示设置变更 (showInSummary 切换)
     * 支持一次性处理多个清单状态变化 (加入或退出汇总)。
     */
    fun handleSummaryDisplayChanges(
        allTasks: List<Task>,
        allLists: List<TaskList>,
        newVisibilityMap: Map<Int, Boolean>
    ): List<Task> {
        val updatedLists = allLists.map {
            if (newVisibilityMap.containsKey(it.id)) it.copy(showInSummary = newVisibilityMap[it.id]!!) else it
        }
        val currentSLists = allLists.filter { it.showInSummary }.map { it.id }.toSet()
        val targetSLists = updatedLists.filter { it.showInSummary }.map { it.id }.toSet()

        val leavingListIds = currentSLists - targetSLists
        val joiningListIds = targetSLists - currentSLists

        if (leavingListIds.isEmpty() && joiningListIds.isEmpty()) {
            return allTasks
        }

        // 当前汇总任务
        val sTasks = allTasks.filter { it.listId in currentSLists }
        val (sM, sC) = partitionScope(sTasks, allLists)

        // 1. 先处理所有退出清单
        val remainingSM = sM.filter { it.listId !in leavingListIds }
        val remainingSC = sC.filter { it.listId !in leavingListIds }

        // 退出清单各自独立形成 L(id)
        val leavingTasksResult = mutableListOf<Task>()
        for (listId in leavingListIds) {
            val listM = sM.filter { it.listId == listId }
            val listC = sC.filter { it.listId == listId }
            leavingTasksResult.addAll(reindexScope(listM, listC))
        }

        // 2. 处理所有加入清单
        // 加入清单按 Tab 顺序排列
        val sortedJoiningLists = allLists.filter { it.id in joiningListIds }
            .sortedWith(compareBy({ it.displayOrder }, { it.timestamp }, { it.id }))

        val allJoiningF = mutableListOf<Task>()
        val allJoiningU = mutableListOf<Task>()
        val allJoiningC = mutableListOf<Task>()

        for (list in sortedJoiningLists) {
            val listTasks = allTasks.filter { it.listId == list.id }
            val (lM, lC) = partitionScope(listTasks, allLists)
            val flagged = lM.filter { it.isFlagged }
            val unflagged = lM.filter { !it.isFlagged }

            allJoiningF.addAll(flagged)
            allJoiningU.addAll(unflagged)
            allJoiningC.addAll(lC)
        }

        // 拼接新的 S: F + 原 S.M + U, C 追加到 S.C 尾部
        val newSM = allJoiningF + remainingSM + allJoiningU
        val newSC = remainingSC + allJoiningC
        val sResult = reindexScope(newSM, newSC)

        // 未发生变动的隐藏清单任务保持不变
        val untouchedHiddenTasks = allTasks.filter {
            it.listId !in currentSLists && it.listId !in targetSLists
        }

        return sResult + leavingTasksResult + untouchedHiddenTasks
    }

    /**
     * 跨清单移动待办任务
     */
    fun moveTasksToList(
        allTasks: List<Task>,
        allLists: List<TaskList>,
        taskIds: Set<Int>,
        targetListId: Int
    ): List<Task> {
        val targetList = allLists.find { it.id == targetListId }
            ?: throw IllegalArgumentException("目标清单 $targetListId 不存在")
        val listMap = allLists.associateBy { it.id }

        val movingTasks = allTasks.filter { it.id in taskIds }
        for (t in movingTasks) {
            val list = listMap[t.listId]
            if (isBottomCompleted(t, list)) {
                throw IllegalArgumentException("请先取消完成收至底部的待办")
            }
        }

        if (movingTasks.isEmpty()) return allTasks

        val isTargetInSummary = targetList.showInSummary
        val remainingTasks = allTasks.filter { it.id !in taskIds }.toMutableList()

        val allInSummary = movingTasks.all { listMap[it.listId]?.showInSummary == true }

        if (allInSummary && isTargetInSummary) {
            // 同在 S 范围内移动
            val updatedAllTasks = allTasks.map { t ->
                if (t.id in taskIds) {
                    if (t.isCompleted && targetList.completionMode == 1) {
                        t.copy(listId = targetListId, isFlagged = false)
                    } else {
                        t.copy(listId = targetListId)
                    }
                } else t
            }
            val (sTasks, otherTasks) = updatedAllTasks.partition { listMap[it.listId]?.showInSummary == true }
            val (sM, sC) = partitionScope(sTasks, allLists)
            return reindexScope(sM, sC) + otherTasks
        } else {
            // 跨范围移动
            val sourceListIds = movingTasks.map { it.listId }.toSet()
            val targetScopeTasks = if (isTargetInSummary) {
                remainingTasks.filter { listMap[it.listId]?.showInSummary == true }
            } else {
                remainingTasks.filter { it.listId == targetListId }
            }

            val (targetM, targetC) = partitionScope(targetScopeTasks, allLists)

            val movedToTargetMHead = mutableListOf<Task>()
            val movedToTargetMTail = mutableListOf<Task>()
            val movedToTargetC = mutableListOf<Task>()

            for (t in movingTasks) {
                val updatedT = t.copy(listId = targetListId)
                if (updatedT.isCompleted && targetList.completionMode == 1) {
                    movedToTargetC.add(updatedT.copy(isFlagged = false))
                } else if (updatedT.isFlagged) {
                    movedToTargetMHead.add(updatedT)
                } else {
                    movedToTargetMTail.add(updatedT)
                }
            }

            val newTargetM = movedToTargetMHead + targetM + movedToTargetMTail
            val newTargetC = targetC + movedToTargetC
            val reindexedTarget = reindexScope(newTargetM, newTargetC)

            val otherTasks = remainingTasks.filter { t ->
                if (isTargetInSummary) {
                    listMap[t.listId]?.showInSummary != true && t.listId !in sourceListIds
                } else {
                    t.listId != targetListId && t.listId !in sourceListIds
                }
            }

            val reindexedSources = mutableListOf<Task>()
            for (srcId in sourceListIds) {
                val srcList = listMap[srcId] ?: continue
                if (isTargetInSummary && srcList.showInSummary) continue
                val srcTasks = remainingTasks.filter { it.listId == srcId }
                val (srcM, srcC) = partitionScope(srcTasks, allLists)
                reindexedSources.addAll(reindexScope(srcM, srcC))
            }
            if (movingTasks.any { listMap[it.listId]?.showInSummary == true } && !isTargetInSummary) {
                val remainingSTasks = remainingTasks.filter { listMap[it.listId]?.showInSummary == true }
                val (sM, sC) = partitionScope(remainingSTasks, allLists)
                reindexedSources.addAll(reindexScope(sM, sC))
            }

            return reindexedTarget + reindexedSources + otherTasks
        }
    }

    /**
     * 新建待办落位
     */
    fun insertTask(
        scopeTasks: List<Task>,
        lists: List<TaskList>,
        newTask: Task
    ): List<Task> {
        val (mTasks, cTasks) = partitionScope(scopeTasks, lists)
        val mutableM = mTasks.toMutableList()
        if (newTask.isFlagged) {
            mutableM.add(0, newTask)
        } else {
            mutableM.add(newTask)
        }
        return reindexScope(mutableM, cTasks)
    }
}
