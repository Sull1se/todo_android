package com.example.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskList
import com.example.data.TaskText
import com.example.data.TodoRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.ui.ThemeMode

/** 汇总页标识，与应用 `selectedListId = -1` 一致。 */
const val SUMMARY_LIST_ID = -1
const val SUMMARY_TITLE = "汇总"

data class WidgetTaskItem(
    val id: Int,
    val title: String,
    val content: String,
    val isFlagged: Boolean,
    val themeColor: Long
)

data class WidgetContent(
    /** 实际显示的页面；绑定清单已删除时回退为汇总。 */
    val listId: Int,
    val title: String,
    /** 汇总页为 null。 */
    val themeColor: Long?,
    val tasks: List<WidgetTaskItem>
)

/** 绑定清单不存在（已删除或尚未加载）时回退到汇总。 */
fun resolveWidgetListId(lists: List<TaskList>, requestedListId: Int): Int {
    if (requestedListId == SUMMARY_LIST_ID) return SUMMARY_LIST_ID
    return if (lists.any { it.id == requestedListId }) requestedListId else SUMMARY_LIST_ID
}

/**
 * 由页面任务快照生成卡片内容：只保留未完成项，顺序与应用页面一致（插旗项已位于主区域首部）。
 * [pageTasks] 必须是 [listId] 对应页面按 `(displayOrder, timestamp, id)` 排好序的任务。
 */
fun buildWidgetContent(lists: List<TaskList>, listId: Int, pageTasks: List<Task>): WidgetContent {
    val listMap = lists.associateBy { it.id }
    val list = listMap[listId]
    return WidgetContent(
        listId = if (list == null) SUMMARY_LIST_ID else listId,
        title = list?.name ?: SUMMARY_TITLE,
        themeColor = list?.themeColor,
        tasks = pageTasks
            .filter { !it.isCompleted }
            .map {
                // 合并格式清单与应用一致：显示信息跳过空行后的前两行
                val (title, content) = if (listMap[it.listId]?.contentMode == TaskContentMode.MERGED) {
                    TaskText.displayLines(it.title, it.content)
                } else {
                    it.title to it.content
                }
                WidgetTaskItem(
                    id = it.id,
                    title = title,
                    content = content,
                    isFlagged = it.isFlagged,
                    themeColor = listMap[it.listId]?.themeColor ?: 0xFFD3E3FDL
                )
            }
    )
}

/**
 * 快捷新建的默认归属清单，与应用内新建一致：卡片显示具体清单时默认该清单；
 * 显示汇总（含绑定清单已删除而回退汇总）时不预选，由用户在弹窗中选择。
 */
fun resolveQuickAddDefaultListId(lists: List<TaskList>, widgetListId: Int): Int? =
    widgetListId.takeIf { id -> id != SUMMARY_LIST_ID && lists.any { it.id == id } }

/** 卡片进程内的数据入口；与应用共用同一个 Room 单例。 */
object WidgetDataAccess {
    private const val APP_PREFS = "todo_prefs"
    private const val WIDGET_PREFS = "todo_widget_prefs"
    private const val KEY_WIDGET_LIST_ID_PREFIX = "widget_list_id_"

    private val initMutex = Mutex()
    @Volatile
    private var initialized = false
    @Volatile
    private var repository: TodoRepository? = null

    fun repository(context: Context): TodoRepository {
        return repository ?: synchronized(this) {
            repository ?: run {
                val database = AppDatabase.getDatabase(context.applicationContext)
                TodoRepository(database.todoDao(), database).also { repository = it }
            }
        }
    }

    /** 测试注入内存数据库仓储；传 null 恢复使用应用数据库。 */
    @androidx.annotation.VisibleForTesting
    fun setRepositoryForTesting(testRepository: TodoRepository?) {
        synchronized(this) {
            repository = testRepository
            initialized = false
        }
    }

    fun appPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    private fun widgetPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)

    /** 卡片实例绑定的页面；未配置过的卡片显示汇总。 */
    fun widgetListId(context: Context, appWidgetId: Int): Int =
        widgetPrefs(context).getInt(KEY_WIDGET_LIST_ID_PREFIX + appWidgetId, SUMMARY_LIST_ID)

    fun setWidgetListId(context: Context, appWidgetId: Int, listId: Int) {
        widgetPrefs(context).edit { putInt(KEY_WIDGET_LIST_ID_PREFIX + appWidgetId, listId) }
    }

    fun removeWidgetListIds(context: Context, appWidgetIds: IntArray) {
        widgetPrefs(context).edit {
            appWidgetIds.forEach { remove(KEY_WIDGET_LIST_ID_PREFIX + it) }
        }
    }

    /** 备份恢复后系统为卡片分配新编号，按新旧编号迁移绑定。 */
    fun remapWidgetListIds(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = widgetPrefs(context)
        val values = oldIds.map { if (prefs.contains(KEY_WIDGET_LIST_ID_PREFIX + it)) widgetListId(context, it) else null }
        prefs.edit {
            oldIds.forEach { remove(KEY_WIDGET_LIST_ID_PREFIX + it) }
            newIds.forEachIndexed { index, newId ->
                values.getOrNull(index)?.let { putInt(KEY_WIDGET_LIST_ID_PREFIX + newId, it) }
            }
        }
    }

    /**
     * 与 TodoViewModel 启动时相同的一次性排序初始化（幂等，Room 事务保护）。
     * 用户升级后未打开应用就先操作卡片时，保证读写基于规范化的排序范围。
     */
    suspend fun ensureReady(context: Context): TodoRepository {
        val repo = repository(context)
        if (initialized) return repo
        initMutex.withLock {
            if (!initialized) {
                val prefs = appPrefs(context)
                val rawExcluded = prefs.getStringSet("summary_excluded_list_ids", emptySet())
                val oldExcludedIds = rawExcluded?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                repo.initializeSummaryOrderIfNeeded(oldExcludedIds)
                prefs.edit {
                    remove("summary_excluded_list_ids")
                    remove("all_list_completion_mode")
                }
                initialized = true
            }
        }
        return repo
    }

    suspend fun loadContent(context: Context, requestedListId: Int): WidgetContent {
        val repo = ensureReady(context)
        val lists = repo.getAllTaskListsSnapshot()
        val listId = resolveWidgetListId(lists, requestedListId)
        val tasks = if (listId == SUMMARY_LIST_ID) {
            repo.getSummaryTasksSnapshot()
        } else {
            repo.getTasksForListSnapshot(listId)
        }
        return buildWidgetContent(lists, listId, tasks)
    }

    fun themeMode(context: Context): ThemeMode =
        ThemeMode.fromStorage(appPrefs(context).getString("appearance_mode", null))

    fun themeModeFlow(context: Context): Flow<ThemeMode> = callbackFlow {
        val prefs = appPrefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "appearance_mode") {
                trySend(ThemeMode.fromStorage(p.getString("appearance_mode", null)))
            }
        }
        trySend(ThemeMode.fromStorage(prefs.getString("appearance_mode", null)))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
