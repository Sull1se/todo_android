package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
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
