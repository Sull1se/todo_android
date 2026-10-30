package com.example.widget

import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDataTest {

    private val work = TaskList(id = 1, name = "工作", themeColor = 0xFF2196F3L, displayOrder = 0, showInSummary = false)
    private val life = TaskList(id = 2, name = "生活", themeColor = 0xFF4CAF50L, displayOrder = 1, showInSummary = true)
    private val home = TaskList(id = 3, name = "家务", themeColor = 0xFFFF9800L, displayOrder = 2, showInSummary = true)
    private val lists = listOf(work, life, home)

    @Test
    fun resolveWidgetListId_fallsBackToSummaryWhenListMissing() {
        assertEquals(SUMMARY_LIST_ID, resolveWidgetListId(lists, SUMMARY_LIST_ID))
        assertEquals(2, resolveWidgetListId(lists, 2))
        assertEquals(SUMMARY_LIST_ID, resolveWidgetListId(lists, 99))
        assertEquals(SUMMARY_LIST_ID, resolveWidgetListId(emptyList(), 2))
    }

    @Test
    fun buildWidgetContent_keepsOnlyIncompleteTasksInPageOrder() {
        val tasks = listOf(
            Task(id = 10, listId = 2, title = "插旗项", content = "", isFlagged = true, displayOrder = 0),
            Task(id = 11, listId = 2, title = "原位已完成", content = "", isCompleted = true, displayOrder = 1),
            Task(id = 12, listId = 3, title = "普通项", content = "备注", displayOrder = 2),
            Task(id = 13, listId = 2, title = "底部已完成", content = "", isCompleted = true, displayOrder = 3)
        )

        val content = buildWidgetContent(lists, SUMMARY_LIST_ID, tasks)

        assertEquals(SUMMARY_LIST_ID, content.listId)
        assertEquals(SUMMARY_TITLE, content.title)
        assertNull(content.themeColor)
        assertEquals(listOf(10, 12), content.tasks.map { it.id })
        assertEquals(true, content.tasks[0].isFlagged)
        assertEquals(0xFFFF9800L, content.tasks[1].themeColor)
        assertEquals("备注", content.tasks[1].content)
    }

    @Test
    fun buildWidgetContent_usesListNameAndColorForListPage() {
        val content = buildWidgetContent(lists, 1, listOf(Task(id = 20, listId = 1, title = "A", content = "")))

        assertEquals(1, content.listId)
        assertEquals("工作", content.title)
        assertEquals(0xFF2196F3L, content.themeColor)
    }

    @Test
    fun resolveQuickAddDefaultListId_usesWidgetList() {
        assertEquals(1, resolveQuickAddDefaultListId(lists, widgetListId = 1))
        assertEquals(3, resolveQuickAddDefaultListId(lists, widgetListId = 3))
    }

    @Test
    fun resolveQuickAddDefaultListId_summaryOrMissingListPreselectsNothing() {
        // 与应用内汇总页新建一致：不预选清单
        assertNull(resolveQuickAddDefaultListId(lists, SUMMARY_LIST_ID))
        // 卡片绑定清单已删除，卡片回退显示汇总
        assertNull(resolveQuickAddDefaultListId(lists, widgetListId = 99))
        assertNull(resolveQuickAddDefaultListId(emptyList(), SUMMARY_LIST_ID))
    }

    @Test
    fun buildWidgetContent_mergedListShowsFirstTwoNonBlankLines() {
        val merged = TaskList(id = 4, name = "随记", displayOrder = 3, contentMode = TaskContentMode.MERGED)
        val tasks = listOf(
            Task(id = 30, listId = 4, title = "买菜", content = "\n\n西红柿\n鸡蛋"),
            Task(id = 31, listId = 1, title = "默认项", content = "\n备注")
        )

        val content = buildWidgetContent(lists + merged, SUMMARY_LIST_ID, tasks)

        assertEquals("买菜", content.tasks[0].title)
        assertEquals("西红柿", content.tasks[0].content)
        // 默认格式的清单显示不变
        assertEquals("\n备注", content.tasks[1].content)
    }
}
