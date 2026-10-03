package com.example.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.SnackbarData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.ui.platform.LocalContext
import com.example.data.transfer.BackupData
import com.example.data.transfer.DataTransferManager
import com.example.ui.components.DataManagementDialog
import com.example.ui.components.ImportPreviewDialog
import com.example.ui.components.UndoMessageHost
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.example.ui.theme.FlagFilledIcon
import com.example.ui.theme.FlagOutlinedIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.Task
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.data.TaskList
import com.example.data.TaskOrderEngine
import com.example.ui.components.UndoMessageHost
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.shadow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import sh.calvin.reorderable.ReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: TodoViewModel,
    onTaskClick: (Int) -> Unit
) {
    val pageTasks by viewModel.pageTasks.collectAsState()
    val lists by viewModel.allLists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val isListsLoaded by viewModel.isListsLoaded.collectAsState()
    val undoRecords by viewModel.undoRecords.collectAsState()
    val isInitialized by viewModel.isInitialized.collectAsState()

    val currentSelectedList = lists?.find { it.id == selectedListId }

    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var showCleanCompletedDialog by rememberSaveable { mutableStateOf(false) }

    var showAddListDialog by rememberSaveable { mutableStateOf(false) }
    var listSettingsDialogId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showQuickAddTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveTasksDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val transferManager = remember(context) { DataTransferManager(context) }
    var showAppSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showDataManagementDialog by rememberSaveable { mutableStateOf(false) }
    var previewBackupData by remember { mutableStateOf<BackupData?>(null) }
    var previewTempFile by remember { mutableStateOf<File?>(null) }
    var existingCounts by remember { mutableStateOf(Pair(0, 0)) }
    var isImporting by remember { mutableStateOf(false) }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val listState = rememberLazyListState()
    val coordinator = remember(listState, scope) {
        SwipeViewportCoordinator(scope, listState) { err ->
            scope.launch { snackbarHostState.showSnackbar(err) }
        }
    }

    val listColorMap = remember(lists) {
        lists?.associate { it.id to it.themeColor } ?: emptyMap()
    }

    val activeFrame = coordinator.currentFrame?.takeIf { it.pageId == selectedListId }
        ?: SwipeViewportCoordinator.buildPageFrame(
            pageId = selectedListId,
            generation = 0L,
            rawTasks = if (pageTasks.pageId == selectedListId) pageTasks.tasks else emptyList(),
            lists = lists ?: emptyList()
        )
    val displayTasks = activeFrame.displayTasks
    val completedTasks = activeFrame.completedTasks

    LaunchedEffect(Unit) {
        if (pageTasks.pageId == selectedListId) {
            coordinator.initFrameIfNeeded(selectedListId, pageTasks.tasks, lists ?: emptyList())
        }
    }

    LaunchedEffect(selectedListId) {
        coordinator.onPageSelected(selectedListId)
        listState.scrollToItem(0)
    }

    LaunchedEffect(pageTasks, lists, selectedListId) {
        if (pageTasks.pageId == selectedListId) {
            coordinator.onPageDataUpdated(pageTasks.pageId, pageTasks.tasks, lists ?: emptyList())
        }
    }

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) {
            coordinator.onUserInterruption()
        }
    }

    val isAnyDialogOpen = showAddListDialog ||
        showMoveTasksDialog ||
        showQuickAddTaskDialog ||
        listSettingsDialogId != null ||
        showAppSettingsDialog ||
        showDataManagementDialog ||
        previewBackupData != null ||
        showCleanCompletedDialog

    LaunchedEffect(isAnyDialogOpen) {
        if (isAnyDialogOpen) {
            coordinator.onUserInterruption()
        }
    }

    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                coordinator.onUserInterruption()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val versionName = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
                    } catch (_: Throwable) {
                        "1.5.0"
                    }
                    val backup = viewModel.exportBackup(versionName)
                    val result = transferManager.exportToUri(backup, uri)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("已成功导出 ${backup.lists.size} 个清单，${backup.tasks.size} 个待办")
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "导出失败"
                        snackbarHostState.showSnackbar("导出失败: $err")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导出失败: ${e.message}")
                }
            }
        }
    }

    val triggerExport = {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        exportLauncher.launch("todo-backup-$timestamp.json")
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val result = transferManager.readAndValidateFromUri(uri)
                    if (result.isSuccess) {
                        val (backup, tempFile) = result.getOrThrow()
                        previewBackupData = backup
                        previewTempFile = tempFile
                        existingCounts = viewModel.getCurrentDataCounts()
                    } else {
                        transferManager.cleanTempFile(previewTempFile)
                        previewBackupData = null
                        previewTempFile = null
                        val err = result.exceptionOrNull()?.message ?: "文件解析校验失败"
                        snackbarHostState.showSnackbar("导入校验失败: $err")
                    }
                } catch (e: Exception) {
                    transferManager.cleanTempFile(previewTempFile)
                    previewBackupData = null
                    previewTempFile = null
                    snackbarHostState.showSnackbar("导入校验失败: ${e.message}")
                }
            }
        }
    }

    // Auto-select "All tasks" if we somehow selected a list that's been deleted, or first launch
    LaunchedEffect(lists, selectedListId, isListsLoaded) {
        val currentLists = lists
        if (isListsLoaded && currentLists != null) {
            if (selectedListId != -1 && currentLists.none { it.id == selectedListId }) {
                viewModel.selectList(-1)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) { data ->
                SwipeableSnackbar(data)
            }
        },
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("已选择 ${selectedTaskIds.size} 项", fontWeight = FontWeight.Medium) },
                        navigationIcon = {
                            TextButton(onClick = { viewModel.exitSelectionMode() }) {
                                Text("退出", fontWeight = FontWeight.Medium)
                            }
                        },
                        actions = {
                            val allCurrentPageTaskIds = remember(displayTasks, completedTasks) {
                                (displayTasks + completedTasks).map { it.id }
                            }
                            TextButton(onClick = { viewModel.selectAllTasks(allCurrentPageTaskIds) }) {
                                Text("全选", fontWeight = FontWeight.Medium)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text("Todo", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        actions = {
                            IconButton(onClick = { showAppSettingsDialog = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "应用设置")
                            }
                        }
                    )
                }

                val tabs = remember(lists) {
                    buildList {
                        add(Pair(-1, "汇总"))
                        lists?.forEach { add(Pair(it.id, it.name)) }
                        add(Pair(-2, "+ 新建列表"))
                    }
                }

                val selectedTabIndex = tabs.indexOfFirst { it.first == selectedListId }.takeIf { it >= 0 } ?: 0

                val tabsState = rememberLazyListState()
                val reorderableTabsState = rememberReorderableLazyListState(tabsState) { from, to ->
                    val fromKey = from.key as? Int ?: return@rememberReorderableLazyListState
                    val toKey = to.key as? Int ?: return@rememberReorderableLazyListState
                    if (fromKey <= 0 || toKey <= 0) return@rememberReorderableLazyListState
                    val currentLists = lists ?: return@rememberReorderableLazyListState
                    val fromListIndex = currentLists.indexOfFirst { it.id == fromKey }
                    val toListIndex = currentLists.indexOfFirst { it.id == toKey }
                    if (fromListIndex >= 0 && toListIndex >= 0 && fromListIndex != toListIndex) {
                        viewModel.reorderLists(fromListIndex, toListIndex)
                    }
                }

                LazyRow(
                    state = tabsState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tabs, key = { _, tab -> tab.first }) { index, tab ->
                        val isSelected = selectedTabIndex == index
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        val borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline
                        val isDragEnabled = tab.first > 0

                        ReorderableItem(
                            state = reorderableTabsState,
                            key = tab.first,
                            enabled = isDragEnabled
                        ) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "tabElevation")
                            val scale by animateFloatAsState(if (isDragging) 1.05f else 1.0f, label = "tabScale")
                            val itemAlpha by animateFloatAsState(if (isDragging) 0.95f else 1.0f, label = "tabAlpha")

                            val dragModifier = if (isDragging) {
                                Modifier
                                    .shadow(elevation, shape = RoundedCornerShape(percent = 50))
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = itemAlpha
                                    }
                            } else {
                                Modifier
                            }

                            Box(
                                modifier = Modifier
                                    .then(dragModifier)
                                    .longPressDraggableHandle(
                                        enabled = isDragEnabled,
                                        onDragStarted = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            viewModel.saveListOrder()
                                        }
                                    )
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(percent = 50))
                                    .clickable(
                                        onClick = {
                                            if (tab.first == -2) {
                                                showAddListDialog = true
                                            } else {
                                                viewModel.selectList(tab.first)
                                            }
                                        }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tab.first == -2) {
                                    Icon(Icons.Filled.Add, contentDescription = "新建列表", tint = contentColor, modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        text = tab.second,
                                        color = contentColor,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize
                                    )
                                }
                            }
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showQuickAddTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Add, "添加待办", modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                if (showDeleteConfirmDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("确认删除") },
                        text = { Text("是否确定删除所选的 ${selectedTaskIds.size} 个待办事项？") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteSelectedTasks()
                                    showDeleteConfirmDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("删除")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消") }
                        }
                    )
                }

                BottomAppBar(
                    actions = {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.markSelectedTasksStatus(true) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "标为完成")
                            Text("标记完成", fontSize = 10.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.markSelectedTasksStatus(false) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = "标为未完成")
                            Text("标记未完成", fontSize = 10.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showMoveTasksDialog = true }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = "移动到")
                            Text("移动到", fontSize = 10.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDeleteConfirmDialog = true }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            Text("删除", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    ) { padding ->
        val canDrag = if (selectedListId == -1) {
            true
        } else {
            currentSelectedList != null && !currentSelectedList.showInSummary
        }
        val reorderableListState = rememberReorderableLazyListState(listState) { from, to ->
            val fromTaskId = from.key as? Int ?: return@rememberReorderableLazyListState
            val toTaskId = to.key as? Int ?: return@rememberReorderableLazyListState
            if (canDrag) {
                val updated = viewModel.reorderTasks(fromTaskId, toTaskId)
                if (updated != null) {
                    coordinator.onDragFrame(viewModel.activeDragSession?.token ?: 0L, updated, lists ?: emptyList())
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Options row above the tasks
                if (!isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val incompleteCount = displayTasks.count { !it.isCompleted }
                        Text(
                            "$incompleteCount 个未完成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            IconButton(onClick = { listSettingsDialogId = selectedListId }) {
                                Icon(Icons.Filled.Settings, contentDescription = "清单设置")
                            }
                            IconButton(onClick = { viewModel.isSelectionMode.value = true }) {
                                Icon(Icons.Filled.List, contentDescription = "多选")
                            }
                        }
                    }
                }

                val activeFrame = coordinator.currentFrame?.takeIf { it.pageId == selectedListId }
                    ?: SwipeViewportCoordinator.buildPageFrame(
                        pageId = selectedListId,
                        generation = 0L,
                        rawTasks = if (pageTasks.pageId == selectedListId) pageTasks.tasks else emptyList(),
                        lists = lists ?: emptyList()
                    )

                TaskListContent(
                    pageFrame = activeFrame,
                    viewportCommand = coordinator.viewportCommand,
                    listState = listState,
                    reorderableListState = reorderableListState,
                    selectedTaskIds = selectedTaskIds,
                    listColorMap = listColorMap,
                    isSelectionMode = isSelectionMode,
                    canDrag = canDrag,
                    showCompleted = showCompleted,
                    hapticFeedback = hapticFeedback,
                    onCommandConsumed = { pageId, epoch, actionId, gen ->
                        coordinator.onCommandConsumed(pageId, epoch, actionId, gen)
                    },
                    onToggleFlag = { task ->
                        coordinator.onSwipeToggleFlag(
                            task = task,
                            currentPageId = selectedListId,
                            executeCommand = { onComplete ->
                                viewModel.toggleTaskFlag(task, onComplete)
                            }
                        )
                    },
                    onCardSettled = { taskId ->
                        coordinator.onSwipeCardSettled(taskId)
                    },
                    onCardDisposed = { taskId ->
                        coordinator.onSwipeCardDisposed(taskId)
                    },
                    onDragStarted = { taskId ->
                        coordinator.onUserInterruption()
                        viewModel.startDragSession(taskId)
                    },
                    onSaveTaskOrder = {
                        if (canDrag) {
                            viewModel.saveTaskOrder()
                        }
                    },
                    onDragCancel = {
                        if (canDrag) {
                            viewModel.cancelDrag()
                        }
                    },
                    onToggleTaskCompletion = { task ->
                        viewModel.toggleTaskCompletion(task)
                    },
                    onUncompleteTask = { task ->
                        viewModel.uncompleteTask(task)
                    },
                    onToggleTaskSelection = { taskId ->
                        viewModel.toggleTaskSelection(taskId)
                    },
                    onTaskClick = onTaskClick,
                    onToggleShowCompleted = { showCompleted = !showCompleted },
                    onCleanCompletedClick = { showCleanCompletedDialog = true }
                )
            }

            UndoMessageHost(
                records = undoRecords,
                isPaused = isAnyDialogOpen,
                onUndoClick = { record ->
                    if (record.actionType == UndoActionType.FLAG) {
                        coordinator.onUndoFlag(
                            record = record,
                            currentPageId = selectedListId,
                            executeCommand = { onResult ->
                                viewModel.undoAction(record, onResult)
                            }
                        )
                    } else {
                        viewModel.undoAction(record)
                    }
                },
                onDismiss = { viewModel.dismissUndoRecord(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isSelectionMode) 8.dp else 80.dp)
            )
        }

        if (showCleanCompletedDialog) {
            val candidateCount = completedTasks.size
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCleanCompletedDialog = false },
                title = { Text("清理已完成待办") },
                text = { Text("删除收至底部的 $candidateCount 项已完成待办？此操作无法撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.cleanCompletedInCurrentScope { deletedCount ->
                                scope.launch {
                                    if (deletedCount > 0) {
                                        snackbarHostState.showSnackbar("已清理 $deletedCount 项已完成待办")
                                    } else {
                                        snackbarHostState.showSnackbar("没有可清理的待办")
                                    }
                                }
                            }
                            showCleanCompletedDialog = false
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showCleanCompletedDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        if (showAddListDialog) {
            AddListDialog(
                lists = lists,
                onDismiss = { showAddListDialog = false },
                onConfirm = { name, color ->
                    viewModel.addList(name, color)
                    showAddListDialog = false
                }
            )
        }

        if (showMoveTasksDialog) {
            MoveTasksDialog(
                lists = lists,
                onDismiss = { showMoveTasksDialog = false },
                onMove = { listId ->
                    viewModel.moveSelectedTasksToList(listId) { errorMsg ->
                        scope.launch { snackbarHostState.showSnackbar(errorMsg) }
                    }
                    showMoveTasksDialog = false
                }
            )
        }

        if (showQuickAddTaskDialog) {
            QuickAddTaskDialog(
                lists = lists ?: emptyList(),
                initialListId = if (selectedListId > 0) selectedListId else null,
                onCreateListClick = { showAddListDialog = true },
                onDismiss = { showQuickAddTaskDialog = false },
                onConfirm = { listId, title, content, isFlagged ->
                    viewModel.addTask(listId, title, content, isFlagged)
                    showQuickAddTaskDialog = false
                }
            )
        }

        if (listSettingsDialogId != null && lists != null) {
            if (listSettingsDialogId == -1) {
                SummarySettingsDialog(
                    lists = lists ?: emptyList(),
                    onDismiss = { listSettingsDialogId = null },
                    onConfirm = { newVisibilityMap ->
                        viewModel.updateSummaryVisibility(newVisibilityMap)
                        listSettingsDialogId = null
                    }
                )
            } else {
                val list = lists!!.find { it.id == listSettingsDialogId }
                if (list != null) {
                    ListSettingsDialog(
                        currentName = list.name,
                        currentColor = list.themeColor,
                        currentCompletionMode = list.completionMode,
                        onDismiss = { listSettingsDialogId = null },
                        onConfirmRename = { newName, newColor, newCompletionMode ->
                            viewModel.updateListInfo(list.id, newName, newColor, newCompletionMode)
                            listSettingsDialogId = null
                        },
                        onDelete = {
                            viewModel.deleteList(list.id)
                            listSettingsDialogId = null
                        }
                    )
                } else {
                    listSettingsDialogId = null
                }
            }
        }

        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        if (showAppSettingsDialog) {
            AppSettingsDialog(
                currentThemeMode = themeMode,
                onThemeModeSelect = { viewModel.setThemeMode(it) },
                onDataManagementClick = {
                    showAppSettingsDialog = false
                    showDataManagementDialog = true
                },
                onDismissRequest = { showAppSettingsDialog = false }
            )
        }

        if (showDataManagementDialog) {
            DataManagementDialog(
                onDismissRequest = { showDataManagementDialog = false },
                onExportClick = {
                    showDataManagementDialog = false
                    triggerExport()
                },
                onImportClick = {
                    showDataManagementDialog = false
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }
            )
        }

        if (previewBackupData != null) {
            ImportPreviewDialog(
                backupData = previewBackupData!!,
                existingListCount = existingCounts.first,
                existingTaskCount = existingCounts.second,
                isLoading = isImporting,
                onDismissRequest = {
                    if (!isImporting) {
                        transferManager.cleanTempFile(previewTempFile)
                        previewBackupData = null
                        previewTempFile = null
                    }
                },
                onPreExportClick = {
                    triggerExport()
                },
                onConfirmImport = { isOverwrite ->
                    scope.launch {
                        isImporting = true
                        try {
                            val data = previewBackupData!!
                            viewModel.importBackup(data, isOverwrite)
                            transferManager.cleanTempFile(previewTempFile)
                            val listsCount = data.lists.size
                            val tasksCount = data.tasks.size
                            previewBackupData = null
                            previewTempFile = null
                            snackbarHostState.showSnackbar(
                                if (isOverwrite) "覆盖导入完成：已载入 $listsCount 个清单，$tasksCount 个待办"
                                else "追加导入完成：已新增 $listsCount 个清单，$tasksCount 个待办"
                            )
                        } catch (e: Exception) {
                            transferManager.cleanTempFile(previewTempFile)
                            previewBackupData = null
                            previewTempFile = null
                            snackbarHostState.showSnackbar("导入失败: ${e.message}")
                        } finally {
                            isImporting = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun AppSettingsDialog(
    currentThemeMode: ThemeMode,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onDataManagementClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("应用设置", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "主题外观",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val modes = listOf(
                    Triple(ThemeMode.SYSTEM, "跟随系统", "根据系统深浅色设置自动切换"),
                    Triple(ThemeMode.LIGHT, "浅色模式", "保持浅色外观"),
                    Triple(ThemeMode.DARK, "深色模式", "OLED 纯黑深色外观")
                )

                modes.forEach { (mode, title, desc) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onThemeModeSelect(mode) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = { onThemeModeSelect(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.Medium)
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "数据管理",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDataManagementClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("数据导入与导出")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun AddListDialog(
    lists: List<TaskList>?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Long) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isTextFieldReady by remember { mutableStateOf(false) }

    // High contrast, non-vivid colors
    val themeColors = listOf(
        0xFFE53935L, 0xFF1E88E5L, 0xFF43A047L, 0xFFFFB300L, 0xFF8E24AAL, 0xFF00ACC1L
    )
    val usedColors = lists?.map { it.themeColor } ?: emptyList()
    val defaultColor = themeColors.firstOrNull { !usedColors.contains(it) } ?: themeColors[0]
    var selectedColor by remember { mutableStateOf(defaultColor) }

    LaunchedEffect(isTextFieldReady) {
        if (isTextFieldReady) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建列表") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("列表名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned {
                            if (!isTextFieldReady) {
                                isTextFieldReady = true
                            }
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("选择主题色", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(themeColors.size) { index ->
                        val color = themeColors[index]
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selectedColor = color }
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, "Selected", modifier = Modifier.align(Alignment.Center).size(20.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text, selectedColor) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(50)
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveTasksDialog(
    lists: List<TaskList>?,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到...") },
        text = {
            if (lists.isNullOrEmpty()) {
                Text("没有可用的列表")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(lists.size) { index ->
                        val list = lists[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMove(list.id) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(list.themeColor), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(list.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun QuickAddTaskDialog(
    lists: List<TaskList>,
    initialListId: Int?,
    onCreateListClick: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (listId: Int, title: String, content: String, isFlagged: Boolean) -> Unit
) {
    if (lists.isEmpty()) {
        EmptyListPromptDialog(
            onDismiss = onDismiss,
            onCreateListClick = {
                onDismiss()
                onCreateListClick()
            }
        )
    } else {
        QuickAddTaskFormDialog(
            lists = lists,
            initialListId = initialListId,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}

@Composable
private fun EmptyListPromptDialog(
    onDismiss: () -> Unit,
    onCreateListClick: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提示") },
        text = { Text("当前没有可用清单，请先创建清单。") },
        confirmButton = {
            Button(
                onClick = onCreateListClick,
                shape = RoundedCornerShape(50)
            ) {
                Text("创建清单")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun QuickAddTaskFormDialog(
    lists: List<TaskList>,
    initialListId: Int?,
    onDismiss: () -> Unit,
    onConfirm: (listId: Int, title: String, content: String, isFlagged: Boolean) -> Unit
) {
    var selectedListId by rememberSaveable { mutableStateOf(initialListId) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var isFlagged by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isTextFieldReady by remember { mutableStateOf(false) }

    LaunchedEffect(isTextFieldReady) {
        if (isTextFieldReady) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("新建待办")
                IconButton(onClick = { isFlagged = !isFlagged }) {
                    Icon(
                        imageVector = if (isFlagged) FlagFilledIcon else FlagOutlinedIcon,
                        contentDescription = if (isFlagged) "取消插旗" else "插旗置顶",
                        tint = if (isFlagged) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned {
                            if (!isTextFieldReady) {
                                isTextFieldReady = true
                            }
                        }
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("详细信息") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("选择归属清单", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))

                var expanded by remember { mutableStateOf(false) }
                val currentListName = lists.find { it.id == selectedListId }?.name ?: "请选择清单"

                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentListName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedListId != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        }
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        lists.forEach { list ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(list.themeColor))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(list.name)
                                    }
                                },
                                onClick = {
                                    selectedListId = list.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val listId = selectedListId
                    if (title.isNotBlank() && listId != null) {
                        onConfirm(listId, title, content, isFlagged)
                    }
                },
                enabled = title.isNotBlank() && selectedListId != null,
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
}

@Composable
fun ListSettingsDialog(
    currentName: String,
    currentColor: Long,
    currentCompletionMode: Int,
    onDismiss: () -> Unit,
    onConfirmRename: (String, Long, Int) -> Unit,
    onDelete: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(currentName) }
    val themeColors = listOf(
        0xFFE53935L, 0xFF1E88E5L, 0xFF43A047L, 0xFFFFB300L, 0xFF8E24AAL, 0xFF00ACC1L
    )
    var selectedColor by remember { mutableStateOf(currentColor) }
    var selectedCompletionMode by rememberSaveable { mutableStateOf(currentCompletionMode) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("列表设置") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("列表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("选择主题色", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(themeColors.size) { index ->
                        val color = themeColors[index]
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selectedColor = color }
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, "Selected", modifier = Modifier.align(Alignment.Center).size(20.dp), tint = Color.White)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("待办完成模式", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedCompletionMode == 0,
                        onClick = { selectedCompletionMode = 0 }
                    )
                    Text("保持原位", modifier = Modifier.clickable { selectedCompletionMode = 0 })
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.material3.RadioButton(
                        selected = selectedCompletionMode == 1,
                        onClick = { selectedCompletionMode = 1 }
                    )
                    Text("收至底部", modifier = Modifier.clickable { selectedCompletionMode = 1 })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirmRename(text, selectedColor, selectedCompletionMode) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(50)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            var showDeleteConfirm by remember { mutableStateOf(false) }
            if (showDeleteConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("确认删除") },
                    text = { Text("是否确定删除该列表？包含在其中的待办事项也将被全部删除。") },
                    confirmButton = {
                        Button(onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                    }
                )
            }
            Row {
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("删除")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
fun SummarySettingsDialog(
    lists: List<TaskList>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Int, Boolean>) -> Unit
) {
    var tempVisibilityMap by remember(lists) {
        mutableStateOf(lists.associate { it.id to it.showInSummary })
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("汇总设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("汇总展示的清单", style = MaterialTheme.typography.labelLarge)
                    if (lists.isNotEmpty()) {
                        val allSelected = lists.all { tempVisibilityMap[it.id] == true }
                        TextButton(
                            onClick = {
                                val target = !allSelected
                                tempVisibilityMap = lists.associate { it.id to target }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (allSelected) "全不选" else "全选",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Text(
                    "取消勾选的清单待办将不在“汇总”页中展示，但在其独立清单页中保留",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (lists.isEmpty()) {
                    Text(
                        "暂无清单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lists.forEach { list ->
                            val isChecked = tempVisibilityMap[list.id] ?: true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        tempVisibilityMap = tempVisibilityMap + (list.id to !isChecked)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(list.themeColor))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = list.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        tempVisibilityMap = tempVisibilityMap + (list.id to checked)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tempVisibilityMap) },
                shape = RoundedCornerShape(50)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            androidx.compose.material3.FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SwipeableSnackbar(data: SnackbarData) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(true) }

    if (visible) {
        androidx.compose.material3.Snackbar(
            snackbarData = data,
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .graphicsLayer { alpha = 1f - (offset.getDistance() / 500f).coerceIn(0f, 1f) }
                .pointerInput(data) {
                    detectDragGestures(
                        onDragEnd = {
                            if (offset.getDistance() > 300f) {
                                visible = false
                                data.dismiss()
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
        )
    }
}

@Composable
private fun TaskListContent(
    pageFrame: PageFrame,
    viewportCommand: ViewportCommand?,
    listState: LazyListState,
    reorderableListState: ReorderableLazyListState,
    selectedTaskIds: Set<Int>,
    listColorMap: Map<Int, Long>,
    isSelectionMode: Boolean,
    canDrag: Boolean,
    showCompleted: Boolean,
    hapticFeedback: HapticFeedback,
    onCommandConsumed: (Int, Long, Long, Long) -> Unit,
    onToggleFlag: (Task) -> Unit,
    onCardSettled: (Int) -> Unit,
    onCardDisposed: (Int) -> Unit,
    onDragStarted: (Int) -> Unit,
    onSaveTaskOrder: () -> Unit,
    onDragCancel: (Int) -> Unit,
    onToggleTaskCompletion: (Task) -> Unit,
    onUncompleteTask: (Task) -> Unit,
    onToggleTaskSelection: (Int) -> Unit,
    onTaskClick: (Int) -> Unit,
    onToggleShowCompleted: () -> Unit,
    onCleanCompletedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastConsumedActionId by remember { mutableStateOf(-1L) }

    SideEffect {
        val cmd = viewportCommand
        if (cmd != null && cmd.generation == pageFrame.generation && cmd.actionId != lastConsumedActionId) {
            lastConsumedActionId = cmd.actionId
            when (val intent = cmd.intent) {
                is ViewportIntent.PinPosition -> {
                    listState.requestScrollToItem(intent.index, intent.offset)
                }
                is ViewportIntent.KeepKey, is ViewportIntent.AnimateTop -> {
                    // Compose key anchoring preserves position without resetting animators
                }
            }
            onCommandConsumed(cmd.pageId, cmd.pageEpoch, cmd.actionId, cmd.generation)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        items(pageFrame.displayTasks, key = { it.id }) { task ->
            val isSelected = selectedTaskIds.contains(task.id)
            val listColor = listColorMap[task.listId] ?: 0xFFD3E3FDL

            ReorderableItem(
                reorderableListState,
                key = task.id,
                animateItemModifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = androidx.compose.animation.core.tween(180)
                )
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "taskElevation")
                val scale by animateFloatAsState(if (isDragging) 1.03f else 1.0f, label = "taskScale")
                val itemAlpha by animateFloatAsState(if (isDragging) 0.92f else 1.0f, label = "taskAlpha")

                val dragModifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .shadow(elevation, shape = RoundedCornerShape(24.dp))
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                        }
                } else {
                    Modifier
                }

                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is DragInteraction.Cancel -> {
                                onDragCancel(task.id)
                            }
                            else -> {}
                        }
                    }
                }

                SwipeableTaskItem(
                    task = task,
                    isSelectionMode = isSelectionMode,
                    enableSwipeToFlag = true,
                    onToggleFlag = { onToggleFlag(task) },
                    onCardSettled = onCardSettled,
                    onCardDisposed = onCardDisposed,
                    modifier = Modifier
                        .then(dragModifier)
                        .longPressDraggableHandle(
                            enabled = !isSelectionMode && canDrag,
                            interactionSource = interactionSource,
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDragStarted(task.id)
                            },
                            onDragStopped = {
                                if (canDrag) {
                                    onSaveTaskOrder()
                                }
                            }
                        )
                ) {
                    TaskCard(
                        task = task,
                        listThemeColor = listColor,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onToggleTask = { onToggleTaskCompletion(task) },
                        onClick = {
                            if (isSelectionMode) {
                                onToggleTaskSelection(task.id)
                            } else {
                                onTaskClick(task.id)
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (pageFrame.completedTasks.isNotEmpty()) {
            item(key = "completed_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleShowCompleted() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (showCompleted) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                            contentDescription = "Toggle Completed",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "已完成 (${pageFrame.completedTasks.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isSelectionMode) {
                        TextButton(
                            onClick = onCleanCompletedClick
                        ) {
                            Text("清理", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (showCompleted) {
                items(pageFrame.completedTasks, key = { it.id }) { task ->
                    val isSelected = selectedTaskIds.contains(task.id)
                    val listColor = listColorMap[task.listId] ?: 0xFFD3E3FDL

                    SwipeableTaskItem(
                        task = task,
                        isSelectionMode = isSelectionMode,
                        enableSwipeToFlag = false,
                        onToggleFlag = {},
                        onCardSettled = {},
                        onCardDisposed = {}
                    ) {
                        TaskCard(
                            task = task,
                            listThemeColor = listColor,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onToggleTask = { onUncompleteTask(task) },
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleTaskSelection(task.id)
                                } else {
                                    onTaskClick(task.id)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskItem(
    task: Task,
    isSelectionMode: Boolean,
    enableSwipeToFlag: Boolean = true,
    onToggleFlag: () -> Unit,
    onCardSettled: (Int) -> Unit = {},
    onCardDisposed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentOnToggleFlag by rememberUpdatedState(onToggleFlag)
    val currentOnCardSettled by rememberUpdatedState(onCardSettled)
    val currentOnCardDisposed by rememberUpdatedState(onCardDisposed)
    val hapticFeedback = LocalHapticFeedback.current
    var hasVibratedForCurrentSwipe by remember { mutableStateOf(false) }
    var hasFiredActionForCurrentSwipe by remember { mutableStateOf(false) }
    var currentGestureToken by remember { mutableStateOf(0L) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (!hasVibratedForCurrentSwipe) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (!hasFiredActionForCurrentSwipe) {
                    hasFiredActionForCurrentSwipe = true
                    currentGestureToken++
                    currentOnToggleFlag()
                }
                false
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.28f }
    )

    val isEndToStart = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
    val isThresholdReached = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

    LaunchedEffect(isEndToStart, isThresholdReached) {
        if (!isEndToStart) {
            hasVibratedForCurrentSwipe = false
            if (!hasFiredActionForCurrentSwipe) {
                hasFiredActionForCurrentSwipe = false
            }
        } else if (isThresholdReached && !hasVibratedForCurrentSwipe) {
            hasVibratedForCurrentSwipe = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(task.isFlagged) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
        hasVibratedForCurrentSwipe = false
    }

    LaunchedEffect(hasFiredActionForCurrentSwipe, currentGestureToken) {
        if (hasFiredActionForCurrentSwipe) {
            snapshotFlow {
                val offset = runCatching { dismissState.requireOffset() }.getOrNull()
                val isSettled = dismissState.currentValue == SwipeToDismissBoxValue.Settled &&
                    dismissState.targetValue == SwipeToDismissBoxValue.Settled
                offset != null && !offset.isNaN() && !offset.isInfinite() && kotlin.math.abs(offset) <= 0.5f && isSettled
            }.first { it }
            hasFiredActionForCurrentSwipe = false
            hasVibratedForCurrentSwipe = false
            currentOnCardSettled(task.id)
        }
    }

    DisposableEffect(task.id) {
        onDispose {
            if (hasFiredActionForCurrentSwipe) {
                currentOnCardDisposed(task.id)
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isSelectionMode && enableSwipeToFlag,
        backgroundContent = {
            if (isEndToStart && dismissState.progress > 0.01f) {
                val isDark = MaterialTheme.colorScheme.background == Color.Black
                val bgColor = if (task.isFlagged) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    if (isDark) com.example.ui.theme.DarkSwipeFlagBg else Color(0xFFFFEBEE)
                }
                val contentColor = if (task.isFlagged) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    if (isDark) com.example.ui.theme.DarkSwipeFlagIcon else Color(0xFFD32F2F)
                }

                val progress = dismissState.progress
                val bgAlpha = (progress * 2.5f).coerceIn(0f, 1f)
                val iconScale = 1f + 0.15f * progress

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(bgColor.copy(alpha = bgAlpha))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (task.isFlagged) "取消插旗" else "插旗置顶",
                            fontWeight = if (isThresholdReached) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = contentColor
                        )
                        Icon(
                            imageVector = if (task.isFlagged) FlagOutlinedIcon else FlagFilledIcon,
                            contentDescription = if (task.isFlagged) "取消插旗" else "插旗",
                            tint = contentColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun TaskCard(
    task: Task,
    listThemeColor: Long,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleTask: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme color stripe
            if (task.listId > 0) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(listThemeColor))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (isSelectionMode) {
                // Checkbox equivalent for selection
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            2.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                IconButton(
                    onClick = onToggleTask,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (task.isCompleted) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(2.dp, com.example.ui.theme.CheckCircleOutlineColor, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    color = if (task.isCompleted) Color(0xFF9E9E9E) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.content,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        color = if (task.isCompleted) Color(0xFF9E9E9E) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (task.isFlagged) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = FlagFilledIcon,
                    contentDescription = "已插旗",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
