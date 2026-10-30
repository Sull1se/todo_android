package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.AppDatabase
import com.example.data.TodoRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager

/**
 * 卡片渲染链路：接收器 / 配置页直接生成并推送 RemoteViews，列表项点击经广播接收器处理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoWidgetRendererTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var shadowManager: ShadowAppWidgetManager
    private var listId = 0
    private var taskId = 0
    private lateinit var repository: TodoRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = TodoRepository(database.todoDao(), database)
        WidgetDataAccess.setRepositoryForTesting(repository)
        WidgetRenderer.resetForTesting()
        context.getSharedPreferences("todo_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        shadowManager = shadowOf(AppWidgetManager.getInstance(context))
        listId = repository.insertList("工作", 0xFF2196F3L, showInSummary = true).toInt()
        taskId = repository.insertTask(listId, "卡片待办", "").toInt()
        Unit
    }

    @After
    fun tearDown() {
        WidgetDataAccess.setRepositoryForTesting(null)
        database.close()
    }

    private fun placeWidget(receiver: Class<out BaseTodoWidgetReceiver>): Int {
        val id = shadowManager.createWidget(receiver, R.layout.widget_loading)
        shadowManager.addBoundWidget(id, AppWidgetProviderInfo().apply {
            provider = ComponentName(context, receiver)
            minWidth = 110
            minHeight = 110
        })
        return id
    }

    // Robolectric 放置卡片时会调用接收器 onUpdate，在后台强制渲染一次；先等它完成，避免与用例中的渲染竞争
    private suspend fun awaitPlacementRender(id: Int) {
        kotlinx.coroutines.withTimeout(5_000) {
            while (WidgetRenderer.pushCount(id) < 1) kotlinx.coroutines.delay(10)
        }
    }

    // 只统计可见文字：空状态提示常驻布局，按可见性切换
    private fun texts(view: View?): List<String> = when {
        view == null || view.visibility != View.VISIBLE -> emptyList()
        view is TextView -> listOf(view.text.toString())
        view is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun widgetTexts(id: Int): List<String> {
        shadowOf(Looper.getMainLooper()).idle()
        return texts(shadowManager.getViewFor(id))
    }

    @Test
    fun renderAll_replacesLoadingLayoutForEveryWidgetEntry() = runBlocking {
        val ids = WidgetRenderer.receiverClasses.map { placeWidget(it) }

        WidgetRenderer.renderAll(context)

        ids.forEach { id -> assertTrue("卡片 $id 未渲染: ${widgetTexts(id)}", SUMMARY_TITLE in widgetTexts(id)) }
    }

    @Test
    fun configuredList_isRenderedAndClearedOnDelete() = runBlocking {
        val id = placeWidget(TodoWidgetReceiver::class.java)
        WidgetDataAccess.setWidgetListId(context, id, listId)

        WidgetRenderer.render(context, intArrayOf(id))
        assertTrue(widgetTexts(id).toString(), "工作" in widgetTexts(id))

        TodoWidgetReceiver().onDeleted(context, intArrayOf(id))
        assertEquals(SUMMARY_LIST_ID, WidgetDataAccess.widgetListId(context, id))
    }

    @Test
    fun restoredWidgets_keepTheirLists() {
        WidgetDataAccess.setWidgetListId(context, 11, listId)
        TodoWidgetReceiver().onRestored(context, intArrayOf(11), intArrayOf(21))

        assertEquals(listId, WidgetDataAccess.widgetListId(context, 21))
        assertEquals(SUMMARY_LIST_ID, WidgetDataAccess.widgetListId(context, 11))
    }

    @Test
    fun providerInfo_declaresSingleResizableEntry() {
        val providers = context.packageManager.queryBroadcastReceivers(
            android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            android.content.pm.PackageManager.GET_META_DATA
        )
        assertEquals(listOf(TodoWidgetReceiver::class.java.name), providers.map { it.activityInfo.name })

        val parser = context.resources.getXml(R.xml.todo_widget_info)
        while (parser.next() != org.xmlpull.v1.XmlPullParser.START_TAG || parser.name != "appwidget-provider") Unit
        val ns = "http://schemas.android.com/apk/res/android"
        val resizeBoth = AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL
        assertEquals(2, parser.getAttributeIntValue(ns, "targetCellWidth", -1))
        assertEquals(2, parser.getAttributeIntValue(ns, "targetCellHeight", -1))
        assertEquals(resizeBoth, parser.getAttributeIntValue(ns, "resizeMode", -1))
    }

    // Robolectric 不展开 RemoteViews 列表项，这里以空状态“暂无待办”是否可见判断推送的内容
    @Test
    fun pendingRender_hidesCheckedTaskWithoutReadingDatabase() = runBlocking {
        val id = placeWidget(TodoWidgetReceiver::class.java)
        awaitPlacementRender(id)
        WidgetRenderer.renderAll(context)
        assertFalse("暂无待办" in widgetTexts(id))

        // 刚勾选、尚未写库：沿用卡片当前内容立即移除该任务
        WidgetCompletionState.begin(taskId)
        try {
            WidgetRenderer.renderAll(context, useCache = true)
            assertTrue(widgetTexts(id).toString(), "暂无待办" in widgetTexts(id))
            // 处理中读库刷新也不会让该任务重新出现
            WidgetRenderer.renderAll(context)
            assertTrue(widgetTexts(id).toString(), "暂无待办" in widgetTexts(id))
        } finally {
            WidgetCompletionState.end(taskId)
        }

        // 写库未发生（如任务已在别处完成失败），结束后按数据库恢复显示
        WidgetRenderer.renderAll(context)
        assertFalse(widgetTexts(id).toString(), "暂无待办" in widgetTexts(id))
    }

    @Test
    fun unchangedWidgets_areNotPushedAgain() = runBlocking {
        val id = placeWidget(TodoWidgetReceiver::class.java)
        awaitPlacementRender(id)
        val afterPlace = WidgetRenderer.pushCount(id)

        WidgetRenderer.renderAll(context)
        assertEquals("内容未变时不应重新推送", afterPlace, WidgetRenderer.pushCount(id))

        repository.deleteTask(taskId)
        WidgetRenderer.renderAll(context)
        assertEquals(afterPlace + 1, WidgetRenderer.pushCount(id))
        assertTrue(widgetTexts(id).toString(), "暂无待办" in widgetTexts(id))
    }

    @Test
    fun itemCompleteBroadcast_completesTaskAndRemovesItFromWidget() = runBlocking {
        val id = placeWidget(TodoWidgetReceiver::class.java)
        awaitPlacementRender(id)

        context.sendBroadcast(itemIntent(WidgetItemActions.ITEM_COMPLETE))
        shadowOf(Looper.getMainLooper()).idle()
        kotlinx.coroutines.withTimeout(5_000) {
            while (repository.getTaskById(taskId)?.isCompleted != true) kotlinx.coroutines.delay(10)
        }
        WidgetRenderer.renderAll(context)
        assertTrue(widgetTexts(id).toString(), "暂无待办" in widgetTexts(id))
    }

    @Test
    fun itemEditBroadcast_opensDesktopEditDialog() {
        context.sendBroadcast(itemIntent(WidgetItemActions.ITEM_EDIT))
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(WidgetEditTaskActivity::class.java.name, started.component?.className)
        assertEquals(taskId, started.getIntExtra(WidgetLaunch.EXTRA_TASK_ID, -1))
    }

    private fun itemIntent(action: String) =
        android.content.Intent(context, WidgetItemActionReceiver::class.java)
            .setAction(WidgetItemActions.ACTION_ITEM)
            .putExtras(WidgetItemActions.fillIn(action, taskId))
}
