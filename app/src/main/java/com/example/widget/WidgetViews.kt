package com.example.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.widget.RemoteViewsCompat
import com.example.R
import com.example.ui.ThemeMode

/**
 * 应用强制浅色或深色时的卡片配色，与应用 Color.kt 一致。
 * 跟随系统时不设置颜色，交给布局中带夜间变体的颜色资源（res/values-night/widget_colors.xml），
 * 由桌面按自身昼夜状态解析。
 */
data class WidgetColors(
    @DrawableRes val background: Int,
    @DrawableRes val addBackground: Int,
    @ColorInt val text: Int,
    @ColorInt val secondaryText: Int,
    @ColorInt val checkOutline: Int,
    @ColorInt val checked: Int,
    @ColorInt val addIcon: Int,
    @ColorInt val flag: Int
)

val LightWidgetColors = WidgetColors(
    background = R.drawable.widget_background_light,
    addBackground = R.drawable.widget_add_background_light,
    text = 0xFF1B1B1F.toInt(),
    secondaryText = 0xFF444746.toInt(),
    checkOutline = 0xFF747775.toInt(),
    checked = 0xFF0B57D0.toInt(),
    addIcon = 0xFF041E49.toInt(),
    flag = 0xFFE53935.toInt()
)

val DarkWidgetColors = WidgetColors(
    background = R.drawable.widget_background_dark,
    addBackground = R.drawable.widget_add_background_dark,
    text = 0xFFE2E2E6.toInt(),
    secondaryText = 0xFFC4C7C5.toInt(),
    checkOutline = 0xFF8E918F.toInt(),
    checked = 0xFF8AB4F8.toInt(),
    addIcon = 0xFFD3E3FD.toInt(),
    flag = 0xFFE53935.toInt()
)

/** 跟随系统时返回 null：使用布局默认的昼夜颜色资源。 */
fun widgetColors(mode: ThemeMode): WidgetColors? = when (mode) {
    ThemeMode.SYSTEM -> null
    ThemeMode.LIGHT -> LightWidgetColors
    ThemeMode.DARK -> DarkWidgetColors
}

/**
 * 直接以 RemoteViews 生成卡片画面（3.4.1 起替代 Glance）：构建只需几毫秒，
 * 列表项点击经广播模板交给 [WidgetItemActionReceiver]，勾选时不启动任何 Activity，
 * 桌面因此不会播放“从卡片打开应用”的动画。
 */
object WidgetViews {
    /** 宽度不足 4 列（约 250dp）时使用紧凑布局：隐藏计数；备注行不受宽度影响。 */
    const val WIDE_WIDTH_DP = 250

    fun build(
        context: Context,
        appWidgetId: Int,
        content: WidgetContent,
        themeMode: ThemeMode,
        widthDp: Int?
    ): RemoteViews {
        val colors = widgetColors(themeMode)
        val isWide = (widthDp ?: 0) >= WIDE_WIDTH_DP
        val views = RemoteViews(context.packageName, R.layout.widget_todo)

        if (colors != null) {
            views.setInt(android.R.id.background, "setBackgroundResource", colors.background)
            views.setInt(R.id.widget_add, "setBackgroundResource", colors.addBackground)
            views.setInt(R.id.widget_add_icon, "setColorFilter", colors.addIcon)
            views.setTextColor(R.id.widget_title, colors.text)
            views.setTextColor(R.id.widget_count, colors.secondaryText)
            views.setTextColor(R.id.widget_empty, colors.secondaryText)
        }

        views.setTextViewText(R.id.widget_title, content.title)
        val themeColor = content.themeColor
        if (themeColor != null) {
            views.setViewVisibility(R.id.widget_list_dot, View.VISIBLE)
            views.setInt(R.id.widget_list_dot, "setColorFilter", themeColor.toInt())
        } else {
            views.setViewVisibility(R.id.widget_list_dot, View.GONE)
        }
        if (isWide) {
            views.setViewVisibility(R.id.widget_count, View.VISIBLE)
            views.setTextViewText(R.id.widget_count, "${content.tasks.size} 项")
        } else {
            views.setViewVisibility(R.id.widget_count, View.GONE)
        }

        val openList = activityPendingIntent(context, WidgetLaunch.openList(context, content.listId))
        views.setOnClickPendingIntent(R.id.widget_header_open, openList)
        views.setOnClickPendingIntent(
            R.id.widget_add,
            activityPendingIntent(context, WidgetLaunch.quickAdd(context, content.listId))
        )

        val isEmpty = content.tasks.isEmpty()
        views.setViewVisibility(R.id.widget_task_list, if (isEmpty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, if (isEmpty) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.widget_empty, openList)

        views.setPendingIntentTemplate(R.id.widget_task_list, WidgetItemActions.template(context))
        val showListColor = content.listId == SUMMARY_LIST_ID
        val items = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        content.tasks.forEach { item ->
            items.addItem(item.id.toLong(), taskRow(context, item, showListColor, colors))
        }
        RemoteViewsCompat.setRemoteAdapter(context, views, appWidgetId, R.id.widget_task_list, items.build())
        return views
    }

    fun taskRow(
        context: Context,
        item: WidgetTaskItem,
        showListColor: Boolean,
        colors: WidgetColors?
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_task_row)
        if (colors != null) {
            row.setTextColor(R.id.widget_row_title, colors.text)
            row.setTextColor(R.id.widget_row_notes, colors.secondaryText)
            row.setInt(R.id.widget_row_flag, "setColorFilter", colors.flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                row.setColorStateList(R.id.widget_row_check, "setButtonTintList", checkTint(colors))
            } else {
                row.setInt(R.id.widget_row_check, "setColorFilter", colors.checkOutline)
            }
        }

        row.setTextViewText(R.id.widget_row_title, item.title)
        if (item.content.isNotBlank()) {
            row.setViewVisibility(R.id.widget_row_notes, View.VISIBLE)
            row.setTextViewText(R.id.widget_row_notes, item.content)
        } else {
            row.setViewVisibility(R.id.widget_row_notes, View.GONE)
        }
        if (showListColor) {
            row.setViewVisibility(R.id.widget_row_bar, View.VISIBLE)
            row.setInt(R.id.widget_row_bar, "setColorFilter", item.themeColor.toInt())
        } else {
            row.setViewVisibility(R.id.widget_row_bar, View.GONE)
        }
        row.setViewVisibility(R.id.widget_row_flag, if (item.isFlagged) View.VISIBLE else View.GONE)

        val complete = WidgetItemActions.fillIn(WidgetItemActions.ITEM_COMPLETE, item.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 点下即由桌面本地打勾；行可能被桌面复用，始终按未勾选下发
            row.setCompoundButtonChecked(R.id.widget_row_check, false)
            row.setOnCheckedChangeResponse(
                R.id.widget_row_check,
                RemoteViews.RemoteResponse.fromFillInIntent(complete)
            )
        } else {
            row.setOnClickFillInIntent(R.id.widget_row_check, complete)
        }
        row.setOnClickFillInIntent(
            R.id.widget_row_body,
            WidgetItemActions.fillIn(WidgetItemActions.ITEM_EDIT, item.id)
        )
        return row
    }

    private fun checkTint(colors: WidgetColors) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(colors.checked, colors.checkOutline)
    )

    // 各入口的 Intent 以不同 data URI 区分，不会被系统合并
    private fun activityPendingIntent(context: Context, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** 卡片列表项的点击协议：模板为发往 [WidgetItemActionReceiver] 的广播，每行以 fill-in 补上动作与任务。 */
object WidgetItemActions {
    const val ACTION_ITEM = "com.example.widget.action.LIST_ITEM"
    const val EXTRA_ITEM_ACTION = "com.example.widget.extra.ITEM_ACTION"
    const val EXTRA_TASK_ID = "com.example.widget.extra.TASK_ID"
    const val ITEM_COMPLETE = "complete"
    const val ITEM_EDIT = "edit"

    /** 列表行需要由桌面补充 extras，模板必须可变；目标组件显式指定为应用内接收器。 */
    fun template(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetItemActionReceiver::class.java).setAction(ACTION_ITEM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    fun fillIn(action: String, taskId: Int): Intent =
        Intent()
            .putExtra(EXTRA_ITEM_ACTION, action)
            .putExtra(EXTRA_TASK_ID, taskId)
}
