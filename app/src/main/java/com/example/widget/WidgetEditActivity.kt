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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskList
import com.example.data.TaskText
import com.example.ui.ConfirmDialog
import com.example.ui.DiscardChangesDialog
import com.example.ui.FlagToggleButton
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
                        Toast.makeText(appContext, appContext.getString(R.string.task_not_found), Toast.LENGTH_SHORT).show()
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
    val originalInfo = TaskText.merge(task.title, task.content)
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var content by rememberSaveable(task.id) { mutableStateOf(task.content) }
    var info by rememberSaveable(task.id) { mutableStateOf(originalInfo) }
    var isFlagged by rememberSaveable(task.id) { mutableStateOf(task.isFlagged) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    // 收至底部清单中的已完成项禁止插旗，与应用编辑页一致
    val canFlag = !(task.isCompleted && list?.completionMode == 1)
    val hasText = if (isMerged) info.isNotBlank() else title.isNotBlank()
    // 与应用详情页一致：有修改时，取消、返回与点击遮罩都先确认“放弃修改？”
    val isDirty = isFlagged != task.isFlagged ||
        if (isMerged) info != originalInfo else (title != task.title || content != task.content)
    val requestClose: () -> Unit = {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = requestClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.edit_task), modifier = Modifier.weight(1f))
                if (canFlag) {
                    FlagToggleButton(isFlagged = isFlagged, onToggle = { isFlagged = !isFlagged })
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isMerged) {
                    OutlinedTextField(
                        value = info,
                        onValueChange = { info = it },
                        label = { Text(stringResource(R.string.field_info)) },
                        minLines = 3,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.field_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.field_detail)) },
                        minLines = 2,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (hasText) {
                        val (savedTitle, savedContent) = if (isMerged) TaskText.split(info) else title to content
                        onSave(savedTitle, savedContent, isFlagged)
                    }
                },
                enabled = hasText
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = requestClose) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.delete_task_title),
            message = stringResource(R.string.delete_task_message),
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showDiscardConfirm) {
        DiscardChangesDialog(
            onKeepEditing = { showDiscardConfirm = false },
            onDiscard = {
                showDiscardConfirm = false
                onDismiss()
            }
        )
    }
}
