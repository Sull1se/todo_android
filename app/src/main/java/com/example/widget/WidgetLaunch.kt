package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.MainActivity

/** 由桌面小卡片打开应用的请求：切到 [listId]，[taskId] 非空时再打开该任务详情。 */
data class AppLaunchRequest(val listId: Int, val taskId: Int?)

object WidgetLaunch {
    const val ACTION_OPEN_FROM_WIDGET = "com.example.action.OPEN_FROM_WIDGET"
    const val EXTRA_LIST_ID = "com.example.extra.LIST_ID"
    const val EXTRA_TASK_ID = "com.example.extra.TASK_ID"

    private const val MAIN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP

    // 不同目标使用不同 data URI，避免 PendingIntent 因仅 extras 不同而被系统合并
    fun openList(context: Context, listId: Int): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_FROM_WIDGET)
            .setData("todo-widget://list/$listId".toUri())
            .putExtra(EXTRA_LIST_ID, listId)
            .addFlags(MAIN_FLAGS)

    fun openTask(context: Context, listId: Int, taskId: Int): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_FROM_WIDGET)
            .setData("todo-widget://task/$listId/$taskId".toUri())
            .putExtra(EXTRA_LIST_ID, listId)
            .putExtra(EXTRA_TASK_ID, taskId)
            .addFlags(MAIN_FLAGS)

    /** 点卡片中的待办：在桌面上弹出编辑窗口，不进入应用。 */
    fun editTask(context: Context, taskId: Int): Intent =
        Intent(context, WidgetEditTaskActivity::class.java)
            .setData("todo-widget://edit/$taskId".toUri())
            .putExtra(EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun quickAdd(context: Context, listId: Int): Intent =
        Intent(context, WidgetQuickAddActivity::class.java)
            .setData("todo-widget://add/$listId".toUri())
            .putExtra(EXTRA_LIST_ID, listId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun parse(intent: Intent?): AppLaunchRequest? {
        if (intent?.action != ACTION_OPEN_FROM_WIDGET) return null
        if (!intent.hasExtra(EXTRA_LIST_ID)) return null
        val listId = intent.getIntExtra(EXTRA_LIST_ID, SUMMARY_LIST_ID)
        val taskId = if (intent.hasExtra(EXTRA_TASK_ID)) intent.getIntExtra(EXTRA_TASK_ID, -1) else null
        return AppLaunchRequest(listId, taskId?.takeIf { it > 0 })
    }
}
