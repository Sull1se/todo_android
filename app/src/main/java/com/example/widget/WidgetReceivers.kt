package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.example.ui.ThemeMode
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * 所有卡片入口共用的接收器：系统请求更新、调整尺寸时立即渲染。
 * 渲染在 goAsync 期间完成，进程此时处于接收广播状态，不会被后台冻结打断。
 */
abstract class BaseTodoWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        // 应用更新后桌面仍保留旧版画面（可能引用已变化的资源），主动全部重绘
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            renderAsync(context, WidgetRenderer.allWidgetIds(context))
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        renderAsync(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetDataAccess.removeWidgetListIds(context, appWidgetIds)
        WidgetRenderer.forget(appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetDataAccess.remapWidgetListIds(context, oldWidgetIds, newWidgetIds)
    }

    private fun renderAsync(context: Context, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        WidgetRefresher.start(appContext)
        val pendingResult: PendingResult? = goAsync()
        WidgetRefresher.scope.launch {
            try {
                WidgetRenderer.render(appContext, appWidgetIds)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

/**
 * 唯一的卡片入口，可自由调整大小（2×2 起，最大约 4×3）。沿用 3.1.0 的类名，保留已放置的卡片。
 * 3.2.0 / 3.3.0 的 4 个固定尺寸入口已在 3.4.0 移除。
 */
class TodoWidgetReceiver : BaseTodoWidgetReceiver()

object WidgetRenderer {
    private const val TAG = "TodoWidget"
    private const val RENDER_TIMEOUT_MILLIS = 8_000L

    val receiverClasses: List<Class<out BaseTodoWidgetReceiver>> = listOf(TodoWidgetReceiver::class.java)

    /** 最近一次推送给某张卡片的画面依据；相同则跳过重绘，勾选时据此免读库立即重绘。 */
    private data class RenderedState(
        val requestedListId: Int,
        /** 读库所得的内容，未去掉正在完成的任务。 */
        val content: WidgetContent,
        val hiddenPendingIds: Set<Int>,
        val themeMode: ThemeMode,
        val widthDp: Int?
    )

    // 同一张卡片串行渲染，避免较早开始的渲染晚于新渲染推送而覆盖最新内容；不同卡片互不等待
    private val widgetLocks = ConcurrentHashMap<Int, Mutex>()
    private val lastRendered = ConcurrentHashMap<Int, RenderedState>()
    private val pushCounts = ConcurrentHashMap<Int, Int>()

    /** 测试用：某张卡片被实际推送的次数。 */
    @androidx.annotation.VisibleForTesting
    fun pushCount(appWidgetId: Int): Int = pushCounts[appWidgetId] ?: 0

    fun allWidgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return receiverClasses
            .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).asList() }
            .toIntArray()
    }

    /**
     * 刷新全部卡片。[force] 为 false 时只推送画面有变化的卡片；
     * [useCache] 为 true 时沿用卡片当前显示的内容（只移除刚勾选的任务），不读数据库。
     */
    suspend fun renderAll(context: Context, force: Boolean = false, useCache: Boolean = false) {
        render(context, allWidgetIds(context), force, useCache)
    }

    suspend fun render(
        context: Context,
        appWidgetIds: IntArray,
        force: Boolean = true,
        useCache: Boolean = false
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val themeMode = WidgetDataAccess.themeMode(appContext)
        // 在调用方的调度器上并发；进程级作用域为多线程，多张卡片同时组合
        coroutineScope {
            for (appWidgetId in appWidgetIds.distinct()) {
                launch {
                    try {
                        withTimeout(RENDER_TIMEOUT_MILLIS) {
                            widgetLocks.getOrPut(appWidgetId) { Mutex() }.withLock {
                                renderOne(appContext, manager, appWidgetId, themeMode, force, useCache)
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException &&
                            t !is kotlinx.coroutines.TimeoutCancellationException
                        ) throw t
                        Log.w(TAG, "render widget $appWidgetId failed", t)
                    }
                }
            }
        }
    }

    /** 测试间清空渲染缓存（Robolectric 每个用例会重新从 1 分配卡片编号）。 */
    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        lastRendered.clear()
        pushCounts.clear()
        widgetLocks.clear()
    }

    fun forget(appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            lastRendered.remove(it)
            pushCounts.remove(it)
            widgetLocks.remove(it)
        }
    }

    private suspend fun renderOne(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        themeMode: ThemeMode,
        force: Boolean,
        useCache: Boolean
    ) {
        val startedAt = SystemClock.uptimeMillis()
        val requestedListId = WidgetDataAccess.widgetListId(context, appWidgetId)
        val previous = lastRendered[appWidgetId]
        val content = if (useCache && previous != null && previous.requestedListId == requestedListId) {
            previous.content
        } else {
            WidgetDataAccess.loadContent(context, requestedListId)
        }
        // 已点勾选、正在写库的任务直接从卡片移除，不等写库完成
        val pendingIds = WidgetCompletionState.pendingIds.value
        val hiddenPendingIds = content.tasks.mapNotNullTo(HashSet()) { task -> task.id.takeIf { it in pendingIds } }
        val widthDp = portraitWidthDp(manager.getAppWidgetOptions(appWidgetId))
        val state = RenderedState(requestedListId, content, hiddenPendingIds, themeMode, widthDp)
        if (!force && state == previous) return

        val displayed = if (hiddenPendingIds.isEmpty()) content else {
            content.copy(tasks = content.tasks.filterNot { it.id in hiddenPendingIds })
        }
        val loadedAt = SystemClock.uptimeMillis()
        // 只按竖屏宽度生成一份，不再为横屏另算
        val views = WidgetViews.build(context, appWidgetId, displayed, themeMode, widthDp)
        val builtAt = SystemClock.uptimeMillis()
        manager.updateAppWidget(appWidgetId, views)
        lastRendered[appWidgetId] = state
        pushCounts.merge(appWidgetId, 1, Int::plus)
        Log.d(
            TAG,
            "push widget=$appWidgetId cache=$useCache load=${loadedAt - startedAt}ms " +
                "build=${builtAt - loadedAt}ms update=${SystemClock.uptimeMillis() - builtAt}ms"
        )
    }

    /** 竖屏下卡片的实际宽度（取最小宽度）；桌面尚未上报尺寸时返回 null。 */
    private fun portraitWidthDp(options: Bundle): Int? =
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 }
}
