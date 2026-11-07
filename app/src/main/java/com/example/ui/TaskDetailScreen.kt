package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import com.example.R
import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskText
import kotlinx.coroutines.flow.first

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
    // 合并格式清单编辑的“信息”，保存时拆回标题与详细信息
    var info by rememberSaveable(taskId) { mutableStateOf("") }
    var isFlagged by rememberSaveable(taskId) { mutableStateOf(false) }
    var draftInitialized by rememberSaveable(taskId) { mutableStateOf(false) }
    var existingTask by remember(taskId) { mutableStateOf<Task?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
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
    val isMerged = taskList?.contentMode == TaskContentMode.MERGED
    val hasText = if (isMerged) info.isNotBlank() else title.isNotBlank()

    // 内容与打开时不同即视为有修改；关闭、系统返回都先确认“放弃修改？”（规范 4.4 决定 3）
    val original = existingTask
    val isDirty = draftInitialized && original != null && (
        isFlagged != original.isFlagged ||
            if (isMerged) {
                info != TaskText.merge(original.title, original.content)
            } else {
                title != original.title || content != original.content
            }
        )
    val requestClose: () -> Unit = {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            handleExit(DetailExitAction.Cancel)
        }
    }

    BackHandler(enabled = isDirty && canInteract) {
        showDiscardConfirm = true
    }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            val task = viewModel.getTaskById(taskId)
            if (task != null) {
                existingTask = task
                if (!draftInitialized) {
                    title = task.title
                    content = task.content
                    info = TaskText.merge(task.title, task.content)
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_task)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { requestClose() },
                        enabled = canInteract
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    // 底部已完成项禁止插旗
                    if (!isTaskInBottomCompleted) {
                        FlagToggleButton(isFlagged = isFlagged, onToggle = { isFlagged = !isFlagged }, enabled = canEdit)
                    }
                    if (taskId != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = canEdit
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (hasText && taskId != null && existingTask != null) {
                                val (savedTitle, savedContent) = if (isMerged) TaskText.split(info) else title to content
                                handleExit(DetailExitAction.Save(taskId, savedTitle, savedContent, isFlagged))
                            }
                        },
                        enabled = canEdit && hasText,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                // 输入法弹出时内容区让出键盘高度，输入框保持可见（规范 7）
                .imePadding()
        ) {
            val horizontalPadding = pageHorizontalPadding(maxWidth)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                if (isMerged) {
                    OutlinedTextField(
                        value = info,
                        onValueChange = { info = it },
                        label = { Text(stringResource(R.string.field_info)) },
                        enabled = canEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.field_title)) },
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        textStyle = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.field_detail)) },
                        enabled = canEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.delete_task_title),
                message = stringResource(R.string.delete_task_message),
                confirmLabel = stringResource(R.string.action_delete),
                isDestructive = true,
                enabled = canInteract,
                onConfirm = {
                    showDeleteConfirm = false
                    taskId?.let { handleExit(DetailExitAction.Delete(it)) }
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

        if (showDiscardConfirm) {
            DiscardChangesDialog(
                onKeepEditing = { showDiscardConfirm = false },
                onDiscard = {
                    showDiscardConfirm = false
                    handleExit(DetailExitAction.Cancel)
                }
            )
        }
    }
}
