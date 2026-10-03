package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskOrderEngineTest {

    private val listA = TaskList(id = 1, name = "A", displayOrder = 0, completionMode = 1, showInSummary = true)
    private val listB = TaskList(id = 2, name = "B", displayOrder = 1, completionMode = 0, showInSummary = true)
    private val listH = TaskList(id = 3, name = "H", displayOrder = 2, completionMode = 1, showInSummary = false)

    @Test
    fun s01_reorderTasksInSummaryScope() {
        val a1 = Task(id = 101, listId = 1, title = "A1", content = "", displayOrder = 0)
        val b1 = Task(id = 201, listId = 2, title = "B1", content = "", displayOrder = 1)
        val a2 = Task(id = 102, listId = 1, title = "A2", content = "", displayOrder = 2)
        val b2 = Task(id = 202, listId = 2, title = "B2", content = "", displayOrder = 3)
        val sTasks = listOf(a1, b1, a2, b2)
        val lists = listOf(listA, listB, listH)

        // 把 B2 拖到 A1 前
        val reordered = TaskOrderEngine.reorderM(sTasks, lists, movedTaskId = 202, targetTaskId = 101)!!
        assertEquals(listOf(202, 101, 201, 102), reordered.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), reordered.map { it.displayOrder })

        // A 独立页投影
        val aTasks = reordered.filter { it.listId == 1 }
        assertEquals(listOf(101, 102), aTasks.map { it.id })
        // B 独立页投影
        val bTasks = reordered.filter { it.listId == 2 }
        assertEquals(listOf(202, 201), bTasks.map { it.id })
    }

    @Test
    fun s04_toggleFlagInSummary_placesAtHeadThenTail() {
        val a1 = Task(id = 101, listId = 1, title = "A1", content = "", displayOrder = 0)
        val b1 = Task(id = 201, listId = 2, title = "B1", content = "", displayOrder = 1)
        val a2 = Task(id = 102, listId = 1, title = "A2", content = "", displayOrder = 2)
        val b2 = Task(id = 202, listId = 2, title = "B2", content = "", displayOrder = 3)
        val sTasks = listOf(a1, b1, a2, b2)
        val lists = listOf(listA, listB)

        // 插旗 A2 -> 置顶
        val flagRes = TaskOrderEngine.toggleFlag(sTasks, lists, taskId = 102)
        assertTrue(flagRes.newFlagged)
        assertEquals(listOf(102, 101, 201, 202), flagRes.updatedTasks.map { it.id })
        assertTrue(flagRes.updatedTasks.first { it.id == 102 }.isFlagged)

        // 取消插旗 A2 -> 置底
        val unflagRes = TaskOrderEngine.toggleFlag(flagRes.updatedTasks, lists, taskId = 102)
        assertFalse(unflagRes.newFlagged)
        assertEquals(listOf(101, 201, 202, 102), unflagRes.updatedTasks.map { it.id })
        assertFalse(unflagRes.updatedTasks.last { it.id == 102 }.isFlagged)
    }

    @Test
    fun s07_joinSummaryList_stablePartitionAndMerge() {
        val a1 = Task(id = 101, listId = 1, title = "A1", content = "", displayOrder = 0)
        val b1 = Task(id = 201, listId = 2, title = "B1", content = "", displayOrder = 1)
        val a2 = Task(id = 102, listId = 1, title = "A2", content = "", displayOrder = 2)

        // 隐藏清单 C: [C1未旗, C2旗, C3旗, C4未旗]
        val listC = TaskList(id = 4, name = "C", displayOrder = 3, completionMode = 0, showInSummary = false)
        val c1 = Task(id = 401, listId = 4, title = "C1", content = "", isFlagged = false, displayOrder = 0)
        val c2 = Task(id = 402, listId = 4, title = "C2", content = "", isFlagged = true, displayOrder = 1)
        val c3 = Task(id = 403, listId = 4, title = "C3", content = "", isFlagged = true, displayOrder = 2)
        val c4 = Task(id = 404, listId = 4, title = "C4", content = "", isFlagged = false, displayOrder = 3)

        val allTasks = listOf(a1, b1, a2, c1, c2, c3, c4)
        val allLists = listOf(listA, listB, listC)

        // 将清单 C 改为在汇总展示
        val result = TaskOrderEngine.handleSummaryDisplayChanges(
            allTasks = allTasks,
            allLists = allLists,
            newVisibilityMap = mapOf(4 to true)
        )

        // 预期拼接: [C2, C3, A1, B1, A2, C1, C4]
        val sTasks = result.filter { it.listId in listOf(1, 2, 4) }
        assertEquals(listOf(402, 403, 101, 201, 102, 401, 404), sTasks.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), sTasks.map { it.displayOrder })

        // 再次隐藏 C
        val hiddenResult = TaskOrderEngine.handleSummaryDisplayChanges(
            allTasks = result,
            allLists = allLists.map { if (it.id == 4) it.copy(showInSummary = true) else it },
            newVisibilityMap = mapOf(4 to false)
        )
        val cScope = hiddenResult.filter { it.listId == 4 }
        assertEquals(listOf(402, 403, 401, 404), cScope.map { it.id })
    }

    @Test
    fun c01_completionModeBehavior() {
        val aFlagged = Task(id = 101, listId = 1, title = "A1", content = "", isFlagged = true, displayOrder = 0)
        val bFlagged = Task(id = 201, listId = 2, title = "B1", content = "", isFlagged = true, displayOrder = 1)
        val tasks = listOf(aFlagged, bFlagged)
        val lists = listOf(listA, listB) // A is mode 1 (move to bottom), B is mode 0 (keep position)

        // 完成 A 项 -> 清旗并进入 C 区域
        val resA = TaskOrderEngine.completeTask(tasks, lists, taskId = 101)
        assertTrue(resA.movedToBottom)
        val taskAAfter = resA.updatedTasks.find { it.id == 101 }!!
        assertTrue(taskAAfter.isCompleted)
        assertFalse("收至底部项旗帜必须被清除", taskAAfter.isFlagged)
        assertEquals(1, taskAAfter.displayOrder) // 落在末尾 (C 区域)

        // 完成 B 项 -> 模式 0 保持原位与旗帜
        val resB = TaskOrderEngine.completeTask(tasks, lists, taskId = 201)
        assertFalse(resB.movedToBottom)
        val taskBAfter = resB.updatedTasks.find { it.id == 201 }!!
        assertTrue(taskBAfter.isCompleted)
        assertTrue("保持原位模式的旗帜应保留", taskBAfter.isFlagged)
        assertEquals(1, taskBAfter.displayOrder)
    }

    @Test
    fun c03_uncompleteTaskAppendsToTailOfM() {
        val a1 = Task(id = 101, listId = 1, title = "A1", content = "", displayOrder = 0)
        val b1 = Task(id = 201, listId = 2, title = "B1", content = "", displayOrder = 1)
        val a2 = Task(id = 102, listId = 1, title = "A2", content = "", displayOrder = 2)
        val b2 = Task(id = 202, listId = 2, title = "B2", content = "", displayOrder = 3)
        // A3 位于底部 C 区域
        val a3 = Task(id = 103, listId = 1, title = "A3", content = "", isCompleted = true, isFlagged = false, displayOrder = 4)

        val sTasks = listOf(a1, b1, a2, b2, a3)
        val lists = listOf(listA, listB)

        // 普通取消完成 A3
        val result = TaskOrderEngine.uncompleteTask(sTasks, lists, taskId = 103)
        // 必须追加至整个 S.M 尾部: [A1, B1, A2, B2, A3]
        assertEquals(listOf(101, 201, 102, 202, 103), result.map { it.id })
        val a3After = result.find { it.id == 103 }!!
        assertFalse(a3After.isCompleted)
        assertFalse(a3After.isFlagged)
        assertEquals(4, a3After.displayOrder)
    }

    @Test
    fun c05_undoCompleteRestoresOriginalIndexAndFlag() {
        val a1 = Task(id = 101, listId = 1, title = "A1", content = "", displayOrder = 0)
        val a2 = Task(id = 102, listId = 1, title = "A2", content = "", isFlagged = true, displayOrder = 1)
        val a3 = Task(id = 103, listId = 1, title = "A3", content = "", displayOrder = 2)
        val sTasks = listOf(a1, a2, a3)
        val lists = listOf(listA)

        // 1. 完成 A2 (原 M 索引 1, 原旗帜 true)
        val completeRes = TaskOrderEngine.completeTask(sTasks, lists, taskId = 102)
        assertTrue(completeRes.movedToBottom)
        assertEquals(1, completeRes.oldMIndex)
        assertTrue(completeRes.oldFlagged)

        // 2. 撤销完成
        val undoRes = TaskOrderEngine.undoComplete(
            scopeTasks = completeRes.updatedTasks,
            lists = lists,
            taskId = 102,
            originalMIndex = completeRes.oldMIndex,
            originalFlagged = completeRes.oldFlagged
        )

        assertEquals(listOf(101, 102, 103), undoRes.map { it.id })
        val a2Restored = undoRes.find { it.id == 102 }!!
        assertFalse(a2Restored.isCompleted)
        assertTrue("撤销完成应恢复原始插旗", a2Restored.isFlagged)
        assertEquals(1, a2Restored.displayOrder)
    }

    @Test
    fun u03_undoFlagAfterDeletingAnotherItem() {
        val a = Task(id = 1, listId = 1, title = "A", content = "", displayOrder = 0)
        val b = Task(id = 2, listId = 1, title = "B", content = "", displayOrder = 1)
        val c = Task(id = 3, listId = 1, title = "C", content = "", displayOrder = 2)
        val d = Task(id = 4, listId = 1, title = "D", content = "", displayOrder = 3)
        val tasks = listOf(a, b, c, d)
        val lists = listOf(listA)

        // 1. C 插旗置顶 -> [C, A, B, D]
        val flagRes = TaskOrderEngine.toggleFlag(tasks, lists, taskId = 3)
        assertEquals(listOf(3, 1, 2, 4), flagRes.updatedTasks.map { it.id })
        assertEquals(2, flagRes.oldMIndex)

        // 2. 删除 A -> [C, B, D]
        val afterDeleteA = flagRes.updatedTasks.filter { it.id != 1 }

        // 3. 撤销 C 的插旗 -> 原索引为 2 -> [B, D, C]
        val undoRes = TaskOrderEngine.undoFlag(
            scopeTasks = afterDeleteA,
            lists = lists,
            taskId = 3,
            originalMIndex = flagRes.oldMIndex,
            originalFlagged = false
        )
        assertEquals(listOf(2, 4, 3), undoRes.map { it.id })
    }

    @Test
    fun bottomCompletedItems_cannotBeFlaggedOrMoved() {
        val cItem = Task(id = 101, listId = 1, title = "C", content = "", isCompleted = true, isFlagged = false, displayOrder = 0)
        val lists = listOf(listA, listB)

        try {
            TaskOrderEngine.toggleFlag(listOf(cItem), lists, taskId = 101)
            fail("底部已完成项禁止插旗")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("禁止插旗") == true)
        }

        try {
            TaskOrderEngine.moveTasksToList(listOf(cItem), lists, setOf(101), targetListId = 2)
            fail("底部已完成项禁止跨清单移动")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("收至底部的待办") == true)
        }
    }
}
