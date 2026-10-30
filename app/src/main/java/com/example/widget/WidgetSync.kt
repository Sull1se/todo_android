package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.room.InvalidationTracker
import com.example.data.AppDatabase
import com.example.data.ExternalTaskChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 进程内的卡片刷新器：监听 Room 的 tasks / task_lists 表与外观偏好，有改动立即刷新全部卡片。
 * 应用与卡片的所有写入都经同一个数据库单例，因此任一入口的改动都会同步到桌面。
 * 刷新进行中再次收到的改动合并为一次，渲染器只重绘画面有变化的卡片。
 */
object WidgetRefresher {
    private const val TAG = "TodoWidget"
    // 同一次操作常连续提交几笔写入，稍等合并，避免先推送中间状态
    private const val SETTLE_MILLIS = 50L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var started = false
    // SharedPreferences 只弱引用监听器，必须由此处强引用
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            val appContext = context.applicationContext

            scope.launch {
                while (isActive) {
                    requests.receive()
                    delay(SETTLE_MILLIS)
                    requests.tryReceive()
                    refreshNow(appContext)
                }
            }

            AppDatabase.getDatabase(appContext).invalidationTracker.addObserver(
                object : InvalidationTracker.Observer(arrayOf("tasks", "task_lists")) {
                    override fun onInvalidated(tables: Set<String>) {
                        requestRefresh()
                    }
                }
            )

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "appearance_mode") requestRefresh()
            }
            prefsListener = listener
            WidgetDataAccess.appPrefs(appContext).registerOnSharedPreferenceChangeListener(listener)
        }
    }

    fun requestRefresh() {
        requests.trySend(Unit)
    }

    suspend fun refreshNow(context: Context) {
        try {
            WidgetRenderer.renderAll(context.applicationContext)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.w(TAG, "widget refresh failed", t)
        }
    }
}

/** 已点勾选、正在等待写库的任务；渲染器把它们直接从卡片移除，不等写库完成。 */
object WidgetCompletionState {
    private val _pendingIds = MutableStateFlow<Set<Int>>(emptySet())
    val pendingIds: StateFlow<Set<Int>> = _pendingIds.asStateFlow()

    /** 返回 false 表示该任务已在处理中（重复点击）。 */
    fun begin(taskId: Int): Boolean {
        var added = false
        _pendingIds.update { current ->
            if (taskId in current) current else {
                added = true
                current + taskId
            }
        }
        return added
    }

    fun end(taskId: Int) {
        _pendingIds.update { it - taskId }
    }
}

object WidgetTaskActions {
    /**
     * 按任务所属清单的完成模式标记完成。任务已完成或已删除时不做任何写入。
     * 点下后立即沿用卡片当前内容（不读库）重绘、移除该任务，再写库；返回是否实际完成了该任务。
     */
    suspend fun complete(context: Context, taskId: Int): Boolean {
        if (!WidgetCompletionState.begin(taskId)) {
            // 重复点击时桌面可能已把勾选框本地切换，强制按已移除状态重绘
            WidgetRenderer.renderAll(context, force = true, useCache = true)
            return false
        }
        try {
            WidgetRenderer.renderAll(context, useCache = true)
            val repository = WidgetDataAccess.ensureReady(context)
            val task = repository.getTaskById(taskId) ?: return false
            if (task.isCompleted) return false
            repository.completeTask(taskId)
            ExternalTaskChanges.notifyChanged(taskId)
            return true
        } finally {
            WidgetCompletionState.end(taskId)
            WidgetRefresher.refreshNow(context)
        }
    }

    /**
     * 卡片编辑弹窗保存：与应用编辑页相同，先改文字，旗帜有变化时按插旗规则切换
     * （收至底部清单中的已完成项不改旗帜）。任务已删除时返回 false。
     */
    suspend fun saveEdit(
        context: Context,
        taskId: Int,
        title: String,
        content: String,
        isFlagged: Boolean
    ): Boolean {
        val repository = WidgetDataAccess.ensureReady(context)
        val task = repository.getTaskById(taskId) ?: return false
        repository.updateTaskContent(taskId, title, content)
        if (task.isFlagged != isFlagged) {
            val list = repository.getAllTaskListsSnapshot().find { it.id == task.listId }
            if (!(task.isCompleted && list?.completionMode == 1)) {
                repository.toggleTaskFlag(taskId)
            }
        }
        ExternalTaskChanges.notifyChanged(taskId)
        return true
    }

    suspend fun delete(context: Context, taskId: Int) {
        val repository = WidgetDataAccess.ensureReady(context)
        if (repository.getTaskById(taskId) == null) return
        repository.deleteTask(taskId)
        ExternalTaskChanges.notifyChanged(taskId)
    }

    suspend fun quickAdd(
        context: Context,
        listId: Int,
        title: String,
        content: String,
        isFlagged: Boolean
    ): Long {
        val repository = WidgetDataAccess.ensureReady(context)
        return repository.insertTask(listId, title, content, isFlagged)
    }
}

/**
 * 卡片列表项的点击入口（列表模板是广播）：勾选在此直接完成，不经过任何 Activity；
 * 点正文时打开桌面编辑弹窗。桌面以可见窗口发送的广播允许接收方随即启动 Activity。
 */
class WidgetItemActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(WidgetItemActions.EXTRA_TASK_ID, -1)
        if (taskId <= 0) return
        val appContext = context.applicationContext
        when (intent.getStringExtra(WidgetItemActions.EXTRA_ITEM_ACTION)) {
            WidgetItemActions.ITEM_COMPLETE -> {
                Log.d(TAG, "complete received task=$taskId")
                WidgetRefresher.start(appContext)
                val pendingResult = goAsync()
                WidgetRefresher.scope.launch {
                    try {
                        WidgetTaskActions.complete(appContext, taskId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            WidgetItemActions.ITEM_EDIT -> appContext.startActivity(WidgetLaunch.editTask(appContext, taskId))
        }
    }

    private companion object {
        const val TAG = "TodoWidget"
    }
}
