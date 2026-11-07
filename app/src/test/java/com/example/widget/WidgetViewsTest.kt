package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 卡片 RemoteViews 的内容与外观：在 Robolectric 中展开后检查各视图。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetViewsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sampleContent = WidgetContent(
        listId = 5,
        title = "工作",
        themeColor = 0xFF2196F3L,
        tasks = listOf(
            WidgetTaskItem(id = 1, title = "插旗任务", content = "", isFlagged = true, themeColor = 0xFF2196F3L),
            WidgetTaskItem(id = 2, title = "普通任务", content = "任务备注", isFlagged = false, themeColor = 0xFF2196F3L)
        )
    )

    private fun inflate(views: android.widget.RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun buildRoot(content: WidgetContent, widthDp: Int, themeMode: ThemeMode = ThemeMode.SYSTEM): View =
        inflate(WidgetViews.build(context, 1, content, themeMode, widthDp))

    private fun row(item: WidgetTaskItem, showListColor: Boolean = false, colors: WidgetColors? = null): View =
        inflate(WidgetViews.taskRow(context, item, showListColor, colors))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()
    private fun View.visible(id: Int): Boolean = findViewById<View>(id).visibility == View.VISIBLE

    @Test
    fun compactSize_showsHeaderAndControlsWithoutCount() {
        val root = buildRoot(sampleContent, widthDp = 160)

        assertEquals("工作", root.text(R.id.widget_title))
        assertTrue(root.visible(R.id.widget_list_dot))
        assertEquals("新建待办", root.findViewById<View>(R.id.widget_add).contentDescription)
        assertFalse(root.visible(R.id.widget_count))
        assertTrue(root.visible(R.id.widget_task_list))
        assertFalse(root.visible(R.id.widget_empty))
    }

    @Test
    fun wideSize_showsCount() {
        val root = buildRoot(sampleContent, widthDp = 320)

        assertTrue(root.visible(R.id.widget_count))
        assertEquals("2 项", root.text(R.id.widget_count))
    }

    @Test
    fun firstRow_dropsTopPadding_andReusedRowRestoresIt() {
        val item = sampleContent.tasks[1]
        val first = inflate(WidgetViews.taskRow(context, item, false, null, isFirst = true))
        assertEquals(0, first.findViewById<View>(R.id.widget_row_root).paddingTop)
        assertEquals(0, first.findViewById<View>(R.id.widget_row_text).paddingTop)

        // 桌面复用首行视图显示其他行时，须恢复布局中的默认上内边距
        WidgetViews.taskRow(context, item, false, null).reapply(context, first)
        val density = context.resources.displayMetrics.density
        assertEquals((1 * density).toInt(), first.findViewById<View>(R.id.widget_row_root).paddingTop)
        assertEquals((6 * density).toInt(), first.findViewById<View>(R.id.widget_row_text).paddingTop)
    }

    @Test
    fun summary_hidesListDot() {
        val root = buildRoot(sampleContent.copy(listId = SUMMARY_LIST_ID, title = SUMMARY_TITLE, themeColor = null), 160)

        assertEquals(SUMMARY_TITLE, root.text(R.id.widget_title))
        assertFalse(root.visible(R.id.widget_list_dot))
    }

    @Test
    fun emptyList_showsPlaceholder() {
        val root = buildRoot(sampleContent.copy(tasks = emptyList()), widthDp = 160)

        assertTrue(root.visible(R.id.widget_empty))
        assertEquals("暂无待办", root.text(R.id.widget_empty))
        assertFalse(root.visible(R.id.widget_task_list))
    }

    @Test
    fun rowWithoutNotes_showsTitleAndFlagOnly() {
        val view = row(sampleContent.tasks[0])

        assertEquals("插旗任务", view.text(R.id.widget_row_title))
        assertTrue(view.visible(R.id.widget_row_flag))
        assertEquals("已插旗", view.findViewById<View>(R.id.widget_row_flag).contentDescription)
        assertFalse(view.visible(R.id.widget_row_notes))
        assertFalse(view.visible(R.id.widget_row_bar))
    }

    @Test
    fun rowWithNotes_showsNotesAndSummaryShowsListColor() {
        // 备注行不受卡片宽度影响（3.4.3 起），行构建不再接收宽度
        val view = row(sampleContent.tasks[1], showListColor = true)

        assertTrue(view.visible(R.id.widget_row_notes))
        assertEquals("任务备注", view.text(R.id.widget_row_notes))
        // 3.5.0 起标题 1 行 + 次要信息最多 2 行，共 3 行
        assertEquals(1, view.findViewById<TextView>(R.id.widget_row_title).maxLines)
        assertEquals(2, view.findViewById<TextView>(R.id.widget_row_notes).maxLines)
        assertFalse(view.visible(R.id.widget_row_flag))
        assertTrue(view.visible(R.id.widget_row_bar))
    }

    @Test
    fun rows_renderUncheckedCheckBox() {
        // 勾选后任务直接从卡片移除，卡片上不出现已勾选状态
        val check = row(sampleContent.tasks[0]).findViewById<View>(R.id.widget_row_check)

        assertTrue(check is CheckBox)
        assertFalse((check as CheckBox).isChecked)
        assertEquals("标记完成", check.contentDescription)
    }

    @Test
    fun forcedTheme_usesFixedColors() {
        val root = buildRoot(sampleContent, 160, ThemeMode.DARK)
        assertEquals(DarkWidgetColors.text, root.findViewById<TextView>(R.id.widget_title).currentTextColor)

        val lightRow = row(sampleContent.tasks[0], colors = LightWidgetColors)
        assertEquals(LightWidgetColors.text, lightRow.findViewById<TextView>(R.id.widget_row_title).currentTextColor)
    }

    @Test
    @Config(sdk = [30, 34], qualifiers = "night")
    fun lightToSystem_reapplyRestoresNightColors() {
        assertReappliedSystemTheme(ThemeMode.LIGHT, DarkWidgetColors)
    }

    @Test
    @Config(sdk = [30, 34], qualifiers = "notnight")
    fun darkToSystem_reapplyRestoresDayColors() {
        assertReappliedSystemTheme(ThemeMode.DARK, LightWidgetColors)
    }

    @Test
    fun systemTheme_resolvesColorsUsingHostConfiguration() {
        val views = WidgetViews.build(context, 1, sampleContent, ThemeMode.SYSTEM, 320)
        val taskViews = WidgetViews.taskRow(context, sampleContent.tasks[1], false, null)
        listOf(false, true, false).forEach { night ->
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val host = context.createConfigurationContext(config)
            val expected = if (night) DarkWidgetColors else LightWidgetColors
            val root = views.apply(host, FrameLayout(host))
            val task = taskViews.apply(host, FrameLayout(host))

            assertEquals(expected.text, root.findViewById<TextView>(R.id.widget_title).currentTextColor)
            assertEquals(host.getColor(R.color.widget_background), (root.background as GradientDrawable).color!!.defaultColor)
            assertEquals(expected.text, task.findViewById<TextView>(R.id.widget_row_title).currentTextColor)
            assertEquals(expected.checkOutline, task.findViewById<CheckBox>(R.id.widget_row_check).buttonTintList!!.defaultColor)
            assertImageColor(root, R.id.widget_add_icon, expected.addIcon)
        }
    }

    private fun assertReappliedSystemTheme(forcedMode: ThemeMode, expected: WidgetColors) {
        val root = buildRoot(sampleContent, 320, forcedMode)
        WidgetViews.build(context, 1, sampleContent, ThemeMode.SYSTEM, 320).reapply(context, root)

        assertEquals(expected.text, root.findViewById<TextView>(R.id.widget_title).currentTextColor)
        assertEquals(expected.secondaryText, root.findViewById<TextView>(R.id.widget_count).currentTextColor)
        assertEquals(expected.secondaryText, root.findViewById<TextView>(R.id.widget_empty).currentTextColor)
        assertEquals(context.getColor(R.color.widget_background), (root.background as GradientDrawable).color!!.defaultColor)
        val addBackground = root.findViewById<View>(R.id.widget_add).background as InsetDrawable
        assertEquals(context.getColor(R.color.widget_add_background), (addBackground.drawable as GradientDrawable).color!!.defaultColor)
        assertImageColor(root, R.id.widget_add_icon, expected.addIcon)

        val item = sampleContent.tasks[1]
        val task = row(item, colors = widgetColors(forcedMode))
        WidgetViews.taskRow(context, item, false, null).reapply(context, task)

        assertEquals(expected.text, task.findViewById<TextView>(R.id.widget_row_title).currentTextColor)
        assertEquals(expected.secondaryText, task.findViewById<TextView>(R.id.widget_row_notes).currentTextColor)
        assertImageColor(task, R.id.widget_row_flag, expected.flag)
        val check = task.findViewById<View>(R.id.widget_row_check)
        if (check is CheckBox) {
            assertEquals(expected.checkOutline, check.buttonTintList!!.defaultColor)
            assertEquals(expected.checked, check.buttonTintList!!.getColorForState(intArrayOf(android.R.attr.state_checked), 0))
            assertFalse(check.isChecked)
        } else {
            assertImageColor(task, R.id.widget_row_check, expected.checkOutline)
        }
    }

    private fun assertImageColor(root: View, id: Int, expected: Int) {
        val filter = root.findViewById<ImageView>(id).colorFilter as PorterDuffColorFilter
        assertEquals(expected, shadowOf(filter).color)
    }

    @Test
    fun systemThemeResources_matchForcedPalettes() {
        // 跟随系统时由颜色资源按昼夜解析，色值必须与强制浅色 / 深色配色一致
        fun colorsFor(night: Boolean): Context {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            return context.createConfigurationContext(config)
        }
        listOf(false to LightWidgetColors, true to DarkWidgetColors).forEach { (night, palette) ->
            val res = colorsFor(night)
            assertEquals(palette.text, ContextCompat.getColor(res, R.color.widget_text))
            assertEquals(palette.secondaryText, ContextCompat.getColor(res, R.color.widget_secondary_text))
            assertEquals(palette.checkOutline, ContextCompat.getColor(res, R.color.widget_check_outline))
            assertEquals(palette.checked, ContextCompat.getColor(res, R.color.widget_checked))
            assertEquals(palette.addIcon, ContextCompat.getColor(res, R.color.widget_add_icon))
            assertEquals(palette.flag, ContextCompat.getColor(res, R.color.widget_flag))
        }
    }
}
