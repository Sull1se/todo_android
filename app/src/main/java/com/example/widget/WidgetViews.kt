package com.example.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.core.widget.RemoteViewsCompat
import com.example.R
import com.example.ui.ThemeMode
import kotlin.math.roundToInt

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

    /** 与 widget_task_row / widget_task_row_body 布局中的上下内边距一致。 */
    private const val ROW_PADDING_DP = 1f
    private const val ROW_TEXT_PADDING_DP = 6f

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

        // 桌面会复用旧视图，跟随系统也必须覆盖之前强制设置的配色。
        views.setInt(android.R.id.background, "setBackgroundResource", colors?.background ?: R.drawable.widget_background)
        views.setInt(R.id.widget_add, "setBackgroundResource", colors?.addBackground ?: R.drawable.widget_add_background)
        if (colors != null) {
            views.setInt(R.id.widget_add_icon, "setColorFilter", colors.addIcon)
            views.setTextColor(R.id.widget_title, colors.text)
            views.setTextColor(R.id.widget_count, colors.secondaryText)
            views.setTextColor(R.id.widget_empty, colors.secondaryText)
        } else {
            views.setColorResource(context, R.id.widget_add_icon, "setColorFilter", R.color.widget_add_icon)
            views.setColorResource(context, R.id.widget_title, "setTextColor", R.color.widget_text)
            views.setColorResource(context, R.id.widget_count, "setTextColor", R.color.widget_secondary_text)
            views.setColorResource(context, R.id.widget_empty, "setTextColor", R.color.widget_secondary_text)
        }

        // 汇总标题按当前语言取资源，具体清单显示清单名
        val title = if (content.listId == SUMMARY_LIST_ID) context.getString(R.string.summary) else content.title
        views.setTextViewText(R.id.widget_title, title)
        val themeColor = content.themeColor
        if (themeColor != null) {
            views.setViewVisibility(R.id.widget_list_dot, View.VISIBLE)
            views.setInt(R.id.widget_list_dot, "setColorFilter", themeColor.toInt())
        } else {
            views.setViewVisibility(R.id.widget_list_dot, View.GONE)
        }
        if (isWide) {
            views.setViewVisibility(R.id.widget_count, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_count,
                context.resources.getQuantityString(R.plurals.widget_count, content.tasks.size, content.tasks.size)
            )
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
        content.tasks.forEachIndexed { index, item ->
            items.addItem(item.id.toLong(), taskRow(context, item, showListColor, colors, isFirst = index == 0))
        }
        RemoteViewsCompat.setRemoteAdapter(context, views, appWidgetId, R.id.widget_task_list, items.build())
        return views
    }

    fun taskRow(
        context: Context,
        item: WidgetTaskItem,
        showListColor: Boolean,
        colors: WidgetColors?,
        isFirst: Boolean = false
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_task_row)
        // 首行去掉顶部留白，缩小与标题栏的距离；行会被桌面复用，非首行也须显式恢复默认内边距
        val density = context.resources.displayMetrics.density
        val rowPadding = (ROW_PADDING_DP * density).roundToInt()
        val textPadding = (ROW_TEXT_PADDING_DP * density).roundToInt()
        row.setViewPadding(R.id.widget_row_root, 0, if (isFirst) 0 else rowPadding, 0, rowPadding)
        row.setViewPadding(R.id.widget_row_text, 0, if (isFirst) 0 else textPadding, 0, textPadding)
        if (colors != null) {
            row.setTextColor(R.id.widget_row_title, colors.text)
            row.setTextColor(R.id.widget_row_notes, colors.secondaryText)
            row.setInt(R.id.widget_row_flag, "setColorFilter", colors.flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                row.setColorStateList(R.id.widget_row_check, "setButtonTintList", checkTint(colors))
            } else {
                row.setInt(R.id.widget_row_check, "setColorFilter", colors.checkOutline)
            }
        } else {
            row.setColorResource(context, R.id.widget_row_title, "setTextColor", R.color.widget_text)
            row.setColorResource(context, R.id.widget_row_notes, "setTextColor", R.color.widget_secondary_text)
            row.setColorResource(context, R.id.widget_row_flag, "setColorFilter", R.color.widget_flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                row.setColorStateList(R.id.widget_row_check, "setButtonTintList", R.color.widget_check_tint)
            } else {
                row.setColorResource(context, R.id.widget_row_check, "setColorFilter", R.color.widget_check_outline)
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

    private fun RemoteViews.setColorResource(context: Context, @IdRes viewId: Int, methodName: String, @ColorRes resource: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColor(viewId, methodName, resource)
        } else {
            setInt(viewId, methodName, context.getColor(resource))
        }
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
