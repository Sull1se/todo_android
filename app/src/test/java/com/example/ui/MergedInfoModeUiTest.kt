package com.example.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskList
import com.example.widget.WidgetTaskEditDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 合并信息格式在新建弹窗、列表卡片与桌面编辑弹窗中的表现。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MergedInfoModeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultList = TaskList(id = 1, name = "默认清单", displayOrder = 0)
    private val mergedList = TaskList(id = 2, name = "随记", displayOrder = 1, contentMode = TaskContentMode.MERGED)

    @Test
    fun quickAdd_mergedListUsesSingleInfoFieldAndSplitsOnSave() {
        var saved: List<Any>? = null
        composeTestRule.setContent {
            QuickAddTaskDialog(
                lists = listOf(defaultList, mergedList),
                initialListId = mergedList.id,
                onCreateListClick = {},
                onDismiss = {},
                onConfirm = { listId, title, content, isFlagged -> saved = listOf(listId, title, content, isFlagged) }
            )
        }

        composeTestRule.onNodeWithText("标题").assertDoesNotExist()
        composeTestRule.onNodeWithText("信息").performTextInput("买菜\n\n西红柿")
        composeTestRule.onNodeWithText("保存").performClick()

        assertEquals(listOf(mergedList.id, "买菜", "\n西红柿", false), saved)
    }

    @Test
    fun quickAdd_switchingToMergedListCarriesTitleAndDetailIntoInfo() {
        composeTestRule.setContent {
            QuickAddTaskDialog(
                lists = listOf(defaultList, mergedList),
                initialListId = defaultList.id,
                onCreateListClick = {},
                onDismiss = {},
                onConfirm = { _, _, _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("标题").performTextInput("写周报")
        composeTestRule.onNodeWithText("详细信息").performTextInput("周五前")
        composeTestRule.onNodeWithText("默认清单").performClick()
        composeTestRule.onNodeWithText("随记").performClick()

        composeTestRule.onNode(hasText("写周报\n周五前")).assertExists()
        composeTestRule.onNodeWithText("标题").assertDoesNotExist()
    }

    @Test
    fun taskCard_mergedListShowsFirstTwoNonBlankLines() {
        composeTestRule.setContent {
            TaskCard(
                task = Task(id = 1, listId = mergedList.id, title = "买菜", content = "\n\n西红柿\n鸡蛋"),
                listThemeColor = mergedList.themeColor,
                isMergedInfo = true,
                isSelected = false,
                isSelectionMode = false,
                onToggleTask = {},
                onClick = {}
            )
        }

        composeTestRule.onNodeWithText("买菜").assertExists()
        composeTestRule.onNodeWithText("西红柿").assertExists()
        composeTestRule.onNode(hasText("鸡蛋", substring = true)).assertDoesNotExist()
    }

    @Test
    fun widgetEditDialog_mergedListEditsInfoAndSplitsOnSave() {
        var saved: Triple<String, String, Boolean>? = null
        composeTestRule.setContent {
            WidgetTaskEditDialog(
                task = Task(id = 5, listId = mergedList.id, title = "买菜", content = "西红柿"),
                list = mergedList,
                onDismiss = {},
                onSave = { title, content, isFlagged -> saved = Triple(title, content, isFlagged) },
                onDelete = {}
            )
        }

        composeTestRule.onNode(hasText("买菜\n西红柿")).performTextReplacement("买水果\n苹果\n香蕉")
        composeTestRule.onNodeWithContentDescription("插旗").performClick()
        composeTestRule.onNodeWithText("保存").performClick()

        assertEquals(Triple("买水果", "苹果\n香蕉", true), saved)
    }

    @Test
    fun widgetEditDialog_deleteRequiresConfirmation() {
        var deleted = false
        composeTestRule.setContent {
            WidgetTaskEditDialog(
                task = Task(id = 5, listId = defaultList.id, title = "旧任务", content = ""),
                list = defaultList,
                onDismiss = {},
                onSave = { _, _, _ -> },
                onDelete = { deleted = true }
            )
        }

        composeTestRule.onNodeWithText("旧任务").assertExists()
        composeTestRule.onNodeWithContentDescription("删除").performClick()
        assertTrue(!deleted)
        composeTestRule.onNodeWithText("删除").performClick()
        assertTrue(deleted)
    }
}
