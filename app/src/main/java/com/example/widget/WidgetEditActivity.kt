package com.example.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskList
import com.example.data.TaskText
import com.example.ui.theme.FlagFilledIcon
import com.example.ui.theme.FlagOutlinedIcon
import kotlinx.coroutines.launch

/**
 * 点卡片中的待办弹出的透明编辑窗口：功能与应用编辑页一致（文字、插旗、删除），保存后直接回到桌面。
 */
class WidgetEditTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent?.getIntExtra(WidgetLaunch.EXTRA_TASK_ID, -1) ?: -1
        if (taskId <= 0) {
            finish()
            return
        }
        WidgetRefresher.start(this)
        val appContext = applicationContext

        setContent {
            WidgetDialogTheme {
                var loaded by remember { mutableStateOf<Pair<Task, TaskList?>?>(null) }
                LaunchedEffect(taskId) {
                    val repository = WidgetDataAccess.ensureReady(appContext)
                    val task = repository.getTaskById(taskId)
                    if (task == null) {
                        Toast.makeText(appContext, "该待办已不存在", Toast.LENGTH_SHORT).show()
                        WidgetRefresher.refreshNow(appContext)
                        finish()
                        return@LaunchedEffect
                    }
                    loaded = task to repository.getAllTaskListsSnapshot().find { it.id == task.listId }
                }
                loaded?.let { (task, list) ->
                    WidgetTaskEditDialog(
                        task = task,
                        list = list,
                        onDismiss = { finish() },
                        onSave = { title, content, isFlagged ->
                            // 写入放在进程级作用域，窗口立即关闭也不会中断保存
                            WidgetRefresher.scope.launch {
                                WidgetTaskActions.saveEdit(appContext, task.id, title, content, isFlagged)
                                WidgetRefresher.refreshNow(appContext)
                            }
                            finish()
                        },
                        onDelete = {
                            WidgetRefresher.scope.launch {
                                WidgetTaskActions.delete(appContext, task.id)
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

@Composable
fun WidgetTaskEditDialog(
    task: Task,
    list: TaskList?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, isFlagged: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val isMerged = list?.contentMode == TaskContentMode.MERGED
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var content by rememberSaveable(task.id) { mutableStateOf(task.content) }
    var info by rememberSaveable(task.id) { mutableStateOf(TaskText.merge(task.title, task.content)) }
    var isFlagged by rememberSaveable(task.id) { mutableStateOf(task.isFlagged) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    // 收至底部清单中的已完成项禁止插旗，与应用编辑页一致
    val canFlag = !(task.isCompleted && list?.completionMode == 1)
    val hasText = if (isMerged) info.isNotBlank() else title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("编辑待办", modifier = Modifier.weight(1f))
                if (canFlag) {
                    IconButton(onClick = { isFlagged = !isFlagged }) {
                        Icon(
                            imageVector = if (isFlagged) FlagFilledIcon else FlagOutlinedIcon,
                            contentDescription = if (isFlagged) "取消插旗" else "插旗",
                            tint = if (isFlagged) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column {
                if (isMerged) {
                    OutlinedTextField(
                        value = info,
                        onValueChange = { info = it },
                        label = { Text("信息") },
                        minLines = 3,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("详细信息") },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasText) {
                        val (savedTitle, savedContent) = if (isMerged) TaskText.split(info) else title to content
                        onSave(savedTitle, savedContent, isFlagged)
                    }
                },
                enabled = hasText,
                shape = RoundedCornerShape(50)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                Text("取消")
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除待办") },
            text = { Text("确定要删除此待办事项吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showDeleteConfirm = false }, shape = RoundedCornerShape(50)) {
                    Text("取消")
                }
            }
        )
    }
}
