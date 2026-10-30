package com.example.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ExternalTaskChanges
import com.example.data.TodoRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetTaskActionsTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository

    private var bottomListId = 0 // completionMode = 1，参与汇总
    private var keepListId = 0   // completionMode = 0，不参与汇总

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
        WidgetDataAccess.setRepositoryForTesting(repository)
        context.getSharedPreferences("todo_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        bottomListId = repository.insertList("收底清单", 0xFF2196F3L, showInSummary = true).toInt()
        keepListId = repository.insertList("原位清单", 0xFF4CAF50L, showInSummary = false).toInt()
        val bottomList = repository.getAllTaskListsSnapshot().first { it.id == bottomListId }
        repository.updateList(bottomList.copy(completionMode = 1))
    }

    @After
    fun tearDown() {
        WidgetDataAccess.setRepositoryForTesting(null)
        database.close()
    }

    @Test
    fun complete_movesTaskToBottomClearsFlagAndNotifiesApp() = runBlocking {
        val flaggedId = repository.insertTask(bottomListId, "插旗待办", "", isFlagged = true).toInt()
        repository.insertTask(bottomListId, "普通待办", "")

        val notified = async(start = CoroutineStart.UNDISPATCHED) { ExternalTaskChanges.taskIds.first() }
        val completed = WidgetTaskActions.complete(context, flaggedId)

        assertTrue(completed)
        assertEquals(flaggedId, notified.await())
        val task = repository.getTaskById(flaggedId)!!
        assertTrue(task.isCompleted)
        assertFalse(task.isFlagged)
        assertTrue(WidgetCompletionState.pendingIds.value.isEmpty())

        val content = WidgetDataAccess.loadContent(context, SUMMARY_LIST_ID)
        assertEquals(listOf("普通待办"), content.tasks.map { it.title })
    }

    @Test
    fun complete_keepPositionListHidesCompletedTaskFromWidget() = runBlocking {
        val firstId = repository.insertTask(keepListId, "第一项", "").toInt()
        repository.insertTask(keepListId, "第二项", "")

        assertTrue(WidgetTaskActions.complete(context, firstId))

        val task = repository.getTaskById(firstId)!!
        assertTrue(task.isCompleted)
        assertEquals(0, task.displayOrder) // 保持原位
        val content = WidgetDataAccess.loadContent(context, keepListId)
        assertEquals(listOf("第二项"), content.tasks.map { it.title })
    }

    @Test
    fun complete_ignoresCompletedOrMissingTasks() = runBlocking {
        val taskId = repository.insertTask(keepListId, "已完成", "").toInt()
        repository.completeTask(taskId)
        val before = repository.getAllTasksSnapshot()

        assertFalse(WidgetTaskActions.complete(context, taskId))
        assertFalse(WidgetTaskActions.complete(context, 9999))

        assertEquals(before, repository.getAllTasksSnapshot())
        assertTrue(WidgetCompletionState.pendingIds.value.isEmpty())
    }

    @Test
    fun complete_repeatedTapWhilePendingIsIgnored() = runBlocking {
        val taskId = repository.insertTask(keepListId, "连点", "").toInt()

        // 模拟首次点击仍在写库
        assertTrue(WidgetCompletionState.begin(taskId))
        try {
            assertFalse(WidgetTaskActions.complete(context, taskId))
            assertFalse(repository.getTaskById(taskId)!!.isCompleted)
        } finally {
            WidgetCompletionState.end(taskId)
        }

        assertTrue(WidgetTaskActions.complete(context, taskId))
        assertTrue(repository.getTaskById(taskId)!!.isCompleted)
    }

    @Test
    fun quickAdd_insertsIntoChosenList() = runBlocking {
        repository.insertTask(keepListId, "已有", "")

        WidgetTaskActions.quickAdd(context, keepListId, "卡片新建", "备注", isFlagged = false)
        WidgetTaskActions.quickAdd(context, keepListId, "卡片插旗", "", isFlagged = true)

        val content = WidgetDataAccess.loadContent(context, keepListId)
        assertEquals(listOf("卡片插旗", "已有", "卡片新建"), content.tasks.map { it.title })
    }

    @Test
    fun loadContent_deletedListFallsBackToSummary() = runBlocking {
        repository.insertTask(bottomListId, "汇总可见", "")
        repository.insertTask(keepListId, "隐藏清单项", "")

        val hidden = WidgetDataAccess.loadContent(context, keepListId)
        assertEquals("原位清单", hidden.title)
        assertEquals(listOf("隐藏清单项"), hidden.tasks.map { it.title })

        repository.deleteList(keepListId)
        val fallback = WidgetDataAccess.loadContent(context, keepListId)
        assertEquals(SUMMARY_LIST_ID, fallback.listId)
        assertEquals(SUMMARY_TITLE, fallback.title)
        assertEquals(listOf("汇总可见"), fallback.tasks.map { it.title })
    }

    @Test
    fun saveEdit_updatesTextAndFlagsToTopAndNotifiesApp() = runBlocking {
        repository.insertTask(keepListId, "第一项", "")
        val taskId = repository.insertTask(keepListId, "旧标题", "旧备注").toInt()

        val notified = async(start = CoroutineStart.UNDISPATCHED) { ExternalTaskChanges.taskIds.first() }
        assertTrue(WidgetTaskActions.saveEdit(context, taskId, "新标题", "新备注", isFlagged = true))

        assertEquals(taskId, notified.await())
        val task = repository.getTaskById(taskId)!!
        assertEquals("新标题", task.title)
        assertEquals("新备注", task.content)
        assertTrue(task.isFlagged)
        assertEquals(listOf("新标题", "第一项"), WidgetDataAccess.loadContent(context, keepListId).tasks.map { it.title })
    }

    @Test
    fun saveEdit_missingTaskReturnsFalse() = runBlocking {
        assertFalse(WidgetTaskActions.saveEdit(context, 9999, "标题", "", isFlagged = false))
    }

    @Test
    fun delete_removesTaskFromWidget() = runBlocking {
        val taskId = repository.insertTask(keepListId, "要删除", "").toInt()
        repository.insertTask(keepListId, "保留", "")

        WidgetTaskActions.delete(context, taskId)

        assertEquals(null, repository.getTaskById(taskId))
        assertEquals(listOf("保留"), WidgetDataAccess.loadContent(context, keepListId).tasks.map { it.title })
    }
}
