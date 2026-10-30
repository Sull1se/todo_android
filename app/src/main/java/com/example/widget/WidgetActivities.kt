package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.data.TaskList
import com.example.ui.QuickAddTaskDialog
import com.example.ui.ThemeMode
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun WidgetDialogTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeMode by remember { WidgetDataAccess.themeModeFlow(context) }
        .collectAsState(initial = WidgetDataAccess.themeMode(context))
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MyApplicationTheme(darkTheme = isDark, content = content)
}

/**
 * 添加卡片时选择显示的清单；Android 12+ 可长按卡片重新进入。默认选中“汇总”。
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 用户返回或取消时，系统不添加卡片
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        WidgetRefresher.start(this)

        val currentListId = WidgetDataAccess.widgetListId(this, appWidgetId)

        setContent {
            WidgetDialogTheme {
                val lists by remember { WidgetDataAccess.repository(this@WidgetConfigActivity).allLists }
                    .collectAsState(initial = null)
                lists?.let { loaded ->
                    WidgetListPickerDialog(
                        lists = loaded,
                        initialListId = resolveWidgetListId(loaded, currentListId),
                        onDismiss = { finish() },
                        onConfirm = { listId -> confirm(listId) }
                    )
                }
            }
        }
    }

    private fun confirm(listId: Int) {
        val appContext = applicationContext
        val widgetIds = intArrayOf(appWidgetId)
        lifecycleScope.launch {
            WidgetDataAccess.setWidgetListId(appContext, appWidgetId, listId)
            // 配置页仍在前台时完成首帧渲染
            WidgetRenderer.render(appContext, widgetIds)
            setResult(RESULT_OK, resultIntent())
            finish()
            // 部分桌面在收到配置结果后才创建卡片视图，稍后再推送一次
            WidgetRefresher.scope.launch {
                delay(POST_CONFIG_RENDER_DELAY_MILLIS)
                WidgetRenderer.render(appContext, widgetIds)
            }
        }
    }

    private companion object {
        const val POST_CONFIG_RENDER_DELAY_MILLIS = 800L
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
fun WidgetListPickerDialog(
    lists: List<TaskList>,
    initialListId: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedListId by rememberSaveable { mutableIntStateOf(initialListId) }
    val options = listOf<Pair<Int, Long?>>(SUMMARY_LIST_ID to null) + lists.map { it.id to it.themeColor }
    val names = lists.associate { it.id to it.name }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("卡片显示的清单") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(options, key = { it.first }) { (id, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedListId = id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedListId == id, onClick = { selectedListId = id })
                        if (color != null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (id == SUMMARY_LIST_ID) SUMMARY_TITLE else names[id].orEmpty(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedListId) }, shape = RoundedCornerShape(50)) {
                Text("确定")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                Text("取消")
            }
        }
    )
}

/**
 * 卡片“＋”打开的透明快捷新建窗口，复用应用的新建待办弹窗；保存后直接回到桌面。
 */
class WidgetQuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetRefresher.start(this)
        val widgetListId = intent?.getIntExtra(WidgetLaunch.EXTRA_LIST_ID, SUMMARY_LIST_ID) ?: SUMMARY_LIST_ID
        val appContext = applicationContext

        setContent {
            WidgetDialogTheme {
                val lists by remember { WidgetDataAccess.repository(appContext).allLists }
                    .collectAsState(initial = null)
                lists?.let { loaded ->
                    val defaultListId = remember { resolveQuickAddDefaultListId(loaded, widgetListId) }
                    QuickAddTaskDialog(
                        lists = loaded,
                        initialListId = defaultListId,
                        onCreateListClick = {
                            startActivity(
                                Intent(appContext, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        onDismiss = { finish() },
                        onConfirm = { listId, title, content, isFlagged ->
                            // 写入放在进程级作用域，窗口立即关闭也不会中断保存
                            WidgetRefresher.scope.launch {
                                WidgetTaskActions.quickAdd(appContext, listId, title, content, isFlagged)
                                WidgetRefresher.refreshNow(appContext)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }
}
