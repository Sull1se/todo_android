package com.example.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import com.example.data.Task
import com.example.data.TodoRepository
import kotlinx.coroutines.runBlocking

/**
 * 等待 Room 异步结果反映到界面或数据库。
 *
 * Room 的查询与写入在自己的线程池中执行，`waitForIdle()` 只等待 Compose 与主线程，
 * 不会等待这些线程；在较慢的机器（如 CI）上，只调用 `waitForIdle()` 的断言可能先于数据到达。
 * 这里的等待只延后断言时机，不放宽断言内容；超时即失败。
 */
internal const val ASYNC_WAIT_TIMEOUT_MS = 10_000L

/** 等待至少一个带有该文字的节点出现。 */
internal fun ComposeContentTestRule.waitForText(text: String) {
    waitUntil(ASYNC_WAIT_TIMEOUT_MS) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
}

/** 等待导航到指定路由。 */
internal fun ComposeContentTestRule.waitForRoute(navController: NavHostController, route: String) {
    waitUntil(ASYNC_WAIT_TIMEOUT_MS) { navController.currentDestination?.route == route }
    waitForIdle()
}

/**
 * 等待详情页从数据库读回任务、进入可编辑状态。
 *
 * 详情页的任务对象在打开和状态恢复后都会异步重新读取，读取完成前“保存”与插旗按钮均为禁用。
 * 可编辑以“保存”或插旗按钮任一可用为准：标题为空时“保存”保持禁用，底部已完成任务没有插旗按钮。
 */
internal fun ComposeContentTestRule.waitForDetailEditable() {
    waitUntil(ASYNC_WAIT_TIMEOUT_MS) {
        val saveEnabled = onAllNodes(hasText("保存") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        val flagEnabled = onAllNodes(
            (hasContentDescription("插旗") or hasContentDescription("取消插旗")) and isEnabled()
        ).fetchSemanticsNodes().isNotEmpty()
        saveEnabled || flagEnabled
    }
    waitForIdle()
}

/** 等待数据库中的任务满足条件，并返回该任务。 */
internal fun ComposeContentTestRule.waitForTask(
    repository: TodoRepository,
    taskId: Int,
    condition: (Task) -> Boolean
): Task {
    var matched: Task? = null
    waitUntil(ASYNC_WAIT_TIMEOUT_MS) {
        matched = runBlocking { repository.getTaskById(taskId) }?.takeIf(condition)
        matched != null
    }
    return matched!!
}
