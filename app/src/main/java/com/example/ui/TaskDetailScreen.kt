package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import com.example.data.Task
import com.example.ui.theme.FlagFilledIcon
import com.example.ui.theme.FlagOutlinedIcon

@Composable
fun AddListDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建列表") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("列表名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

sealed class DetailExitAction {
    data class Save(val taskId: Int, val title: String, val content: String, val isFlagged: Boolean) : DetailExitAction()
    data class Delete(val taskId: Int) : DetailExitAction()
    data object Cancel : DetailExitAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Int?,
    viewModel: TodoViewModel,
    backStackEntry: NavBackStackEntry,
    onExitRequest: (DetailExitAction) -> Unit
) {
    var title by rememberSaveable(taskId) { mutableStateOf("") }
    var content by rememberSaveable(taskId) { mutableStateOf("") }
    var isFlagged by rememberSaveable(taskId) { mutableStateOf(false) }
    var draftInitialized by rememberSaveable(taskId) { mutableStateOf(false) }
    var existingTask by remember(taskId) { mutableStateOf<Task?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var exitRequested by remember(backStackEntry) { mutableStateOf(false) }

    var lifecycleState by remember(backStackEntry) {
        mutableStateOf(backStackEntry.lifecycle.currentState)
    }
    DisposableEffect(backStackEntry) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleState = backStackEntry.lifecycle.currentState
        }
        backStackEntry.lifecycle.addObserver(observer)
        onDispose {
            backStackEntry.lifecycle.removeObserver(observer)
        }
    }

    val isEntryResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val canInteract = !exitRequested && isEntryResumed

    val handleExit: (DetailExitAction) -> Unit = remember(backStackEntry, onExitRequest) {
        { action ->
            if (!exitRequested && backStackEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                exitRequested = true
                onExitRequest(action)
            }
        }
    }

    val lists by viewModel.allLists.collectAsState()
    val taskList = lists?.find { it.id == existingTask?.listId }
    val isTaskInBottomCompleted = existingTask != null && existingTask!!.isCompleted && taskList?.completionMode == 1

    val canEdit = canInteract && existingTask != null && taskList != null && draftInitialized

    LaunchedEffect(taskId) {
        if (taskId != null) {
            val task = viewModel.getTaskById(taskId)
            if (task != null) {
                existingTask = task
                if (!draftInitialized) {
                    title = task.title
                    content = task.content
                    isFlagged = task.isFlagged
                    draftInitialized = true
                }
            } else {
                snapshotFlow { lifecycleState }
                    .first { it.isAtLeast(Lifecycle.State.RESUMED) }
                handleExit(DetailExitAction.Cancel)
            }
        } else {
            snapshotFlow { lifecycleState }
                .first { it.isAtLeast(Lifecycle.State.RESUMED) }
            handleExit(DetailExitAction.Cancel)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑待办", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { handleExit(DetailExitAction.Cancel) },
                        enabled = canInteract
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    // 底部已完成项禁止插旗
                    if (!isTaskInBottomCompleted) {
                        IconButton(
                            onClick = { isFlagged = !isFlagged },
                            enabled = canEdit
                        ) {
                            Icon(
                                imageVector = if (isFlagged) FlagFilledIcon else FlagOutlinedIcon,
                                contentDescription = if (isFlagged) "取消插旗" else "插旗",
                                tint = if (isFlagged) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (taskId != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = canEdit
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() && taskId != null && existingTask != null) {
                                handleExit(DetailExitAction.Save(taskId, title, content, isFlagged))
                            }
                        },
                        enabled = canEdit && title.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("详细信息") },
                enabled = canEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除待办") },
                text = { Text("确定要删除此待办事项吗？此操作无法撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            taskId?.let { handleExit(DetailExitAction.Delete(it)) }
                        },
                        enabled = canInteract
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        enabled = canInteract
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
