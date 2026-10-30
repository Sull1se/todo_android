package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 清单待办格式的存储与切换。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListContentModeTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun list(id: Int) = repository.getAllTaskListsSnapshot().first { it.id == id }

    @Test
    fun insertList_storesChosenContentMode() = runBlocking {
        val defaultId = repository.insertList("默认", 0L).toInt()
        val mergedId = repository.insertList("合并", 0L, contentMode = TaskContentMode.MERGED).toInt()

        assertEquals(TaskContentMode.TITLE_AND_DETAIL, list(defaultId).contentMode)
        assertEquals(TaskContentMode.MERGED, list(mergedId).contentMode)
    }

    @Test
    fun switchToMerged_keepsTasksUnchanged() = runBlocking {
        val listId = repository.insertList("清单", 0L).toInt()
        val taskId = repository.insertTask(listId, "标题", "\n详细\n\n信息\n", false).toInt()

        repository.updateList(list(listId).copy(contentMode = TaskContentMode.MERGED))

        val task = repository.getTaskById(taskId)!!
        assertEquals("标题", task.title)
        assertEquals("\n详细\n\n信息\n", task.content)
    }

    @Test
    fun switchBackToDefault_firstLineStaysTitleAndDetailIsTrimmed() = runBlocking {
        val listId = repository.insertList("清单", 0L, contentMode = TaskContentMode.MERGED).toInt()
        val (title, content) = TaskText.split("买菜\n\n西红柿\n\n鸡蛋")
        val taskId = repository.insertTask(listId, title, content, false).toInt()
        val untouchedId = repository.insertTask(listId, "单行", "", false).toInt()

        repository.updateList(list(listId).copy(contentMode = TaskContentMode.TITLE_AND_DETAIL))

        val task = repository.getTaskById(taskId)!!
        assertEquals("买菜", task.title)
        assertEquals("西红柿\n\n鸡蛋", task.content)
        assertEquals("", repository.getTaskById(untouchedId)!!.content)
    }
}
