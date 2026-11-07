package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.Task
import com.example.data.TaskContentMode
import com.example.data.TaskList
import com.example.data.TaskText
import com.example.data.transfer.BackupData
import com.example.data.transfer.DataTransferManager
import com.example.data.transfer.JsonBackupCodec
import com.example.ui.components.DataManagementDialog
import com.example.ui.components.ImportPreviewDialog
import com.example.ui.components.UndoMessageHost
import com.example.ui.theme.FlagFilledIcon
import com.example.ui.theme.FlagOutlinedIcon
import com.example.ui.theme.ListColorFallback
import com.example.ui.theme.ListThemePalette
import com.example.ui.theme.TodoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 中等及以上窗口（≥ 600dp）的判断阈值、页面边距与内容最大宽度（UI 与 UX 规范 3.4、7）。 */
internal val MediumWindowWidth = 600.dp
internal val ContentMaxWidth = 840.dp
internal fun pageHorizontalPadding(windowWidth: androidx.compose.ui.unit.Dp) =
    if (windowWidth >= MediumWindowWidth) 24.dp else 16.dp

/** 清单可选主题色的名称（与 [ListThemePalette] 一一对应），供读屏播报。 */
private val ListThemeColorNames = listOf(
    R.string.color_red, R.string.color_blue, R.string.color_green,
    R.string.color_amber, R.string.color_purple, R.string.color_cyan
)

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
    var showDeleteSelectedDialog by rememberSaveable { mutableStateOf(false) }

    var showAddListDialog by rememberSaveable { mutableStateOf(false) }
    var listSettingsDialogId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showQuickAddTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveTasksDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val transferManager = remember(context) { DataTransferManager(context) }
    var showAppSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showDataManagementDialog by rememberSaveable { mutableStateOf(false) }
    // 导入预览：临时文件路径与现有数量可在旋转、分屏或进程重建后恢复，预览数据据此重新解析
    var previewTempPath by rememberSaveable { mutableStateOf<String?>(null) }
    var previewBackupData by remember { mutableStateOf<BackupData?>(null) }
    var existingListCount by rememberSaveable { mutableStateOf(0) }
    var existingTaskCount by rememberSaveable { mutableStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }
    val previewTempFile = previewTempPath?.let { File(it) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    val mergedListIds = remember(lists) {
        lists?.filter { it.contentMode == TaskContentMode.MERGED }?.map { it.id }?.toSet() ?: emptySet()
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

    // 进程重建后按保存的临时文件路径恢复导入预览；文件已不存在时放弃预览
    LaunchedEffect(previewTempPath) {
        val path = previewTempPath
        if (path != null && previewBackupData == null) {
            val restored = withContext(Dispatchers.IO) {
                runCatching { JsonBackupCodec.readBackup(File(path)) }.getOrNull()
            }
            if (restored != null) {
                previewBackupData = restored
            } else {
                transferManager.cleanTempFile(File(path))
                previewTempPath = null
            }
        }
    }

    val isAnyDialogOpen = showAddListDialog ||
        showMoveTasksDialog ||
        showQuickAddTaskDialog ||
        listSettingsDialogId != null ||
        showAppSettingsDialog ||
        showDataManagementDialog ||
        previewTempPath != null ||
        showCleanCompletedDialog ||
        showDeleteSelectedDialog

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

    // 系统返回先退出多选（规范第 5 节返回优先级），对话框由各自的窗口处理返回
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val message = try {
                    val versionName = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
                    } catch (_: Throwable) {
                        "1.5.0"
                    }
                    val backup = viewModel.exportBackup(versionName)
                    val result = transferManager.exportToUri(backup, uri)
                    if (result.isSuccess) {
                        context.getString(R.string.export_success, backup.lists.size, backup.tasks.size)
                    } else {
                        context.getString(R.string.export_failed, result.exceptionOrNull().reason(context))
                    }
                } catch (e: Exception) {
                    context.getString(R.string.export_failed, e.reason(context))
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val triggerExport = {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        exportLauncher.launch("todo-backup-$timestamp.json")
    }

    fun clearPreview() {
        transferManager.cleanTempFile(previewTempFile)
        previewBackupData = null
        previewTempPath = null
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
                        val (listCount, taskCount) = viewModel.getCurrentDataCounts()
                        existingListCount = listCount
                        existingTaskCount = taskCount
                        previewBackupData = backup
                        previewTempPath = tempFile.absolutePath
                    } else {
                        clearPreview()
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.import_invalid, result.exceptionOrNull().reason(context))
                        )
                    }
                } catch (e: Exception) {
                    clearPreview()
                    snackbarHostState.showSnackbar(context.getString(R.string.import_invalid, e.reason(context)))
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                SwipeableSnackbar(data)
            }
        },
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Text(pluralStringResource(R.plurals.selected_count, selectedTaskIds.size, selectedTaskIds.size))
                        },
                        navigationIcon = {
                            TextButton(onClick = { viewModel.exitSelectionMode() }) {
                                Text(stringResource(R.string.action_exit))
                            }
                        },
                        actions = {
                            val allCurrentPageTaskIds = remember(displayTasks, completedTasks) {
                                (displayTasks + completedTasks).map { it.id }
                            }
                            TextButton(onClick = { viewModel.selectAllTasks(allCurrentPageTaskIds) }) {
                                Text(stringResource(R.string.action_select_all))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        actions = {
                            IconButton(onClick = { showAppSettingsDialog = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.app_settings))
                            }
                        }
                    )
                }

                ListTabsRow(
                    lists = lists,
                    selectedListId = selectedListId,
                    hapticFeedback = hapticFeedback,
                    onSelectList = { viewModel.selectList(it) },
                    onAddList = { showAddListDialog = true },
                    onReorderLists = { from, to -> viewModel.reorderLists(from, to) },
                    onSaveListOrder = { viewModel.saveListOrder() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showQuickAddTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.new_task), modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                val hasSelection = selectedTaskIds.isNotEmpty()
                BottomAppBar(
                    actions = {
                        SelectionAction(
                            icon = Icons.Filled.CheckCircle,
                            label = stringResource(R.string.mark_done),
                            enabled = hasSelection,
                            onClick = { viewModel.markSelectedTasksStatus(true) }
                        )
                        SelectionAction(
                            icon = Icons.Filled.Clear,
                            label = stringResource(R.string.mark_undone),
                            enabled = hasSelection,
                            onClick = { viewModel.markSelectedTasksStatus(false) }
                        )
                        SelectionAction(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            label = stringResource(R.string.move_to),
                            enabled = hasSelection,
                            onClick = { showMoveTasksDialog = true }
                        )
                        SelectionAction(
                            icon = Icons.Filled.Delete,
                            label = stringResource(R.string.action_delete),
                            enabled = hasSelection,
                            isDestructive = true,
                            onClick = { showDeleteSelectedDialog = true }
                        )
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val horizontalPadding = pageHorizontalPadding(maxWidth)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
            ) {
                // Options row above the tasks
                if (!isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = horizontalPadding, end = horizontalPadding - 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val incompleteCount = displayTasks.count { !it.isCompleted }
                        Text(
                            pluralStringResource(R.plurals.incomplete_count, incompleteCount, incompleteCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            IconButton(onClick = { listSettingsDialogId = selectedListId }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(
                                        if (selectedListId == -1) R.string.summary_settings else R.string.list_settings
                                    )
                                )
                            }
                            IconButton(onClick = { viewModel.isSelectionMode.value = true }) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.select_tasks))
                            }
                        }
                    }
                }

                val isPageLoaded = isInitialized && isListsLoaded && pageTasks.pageId == selectedListId

                TaskListContent(
                    pageFrame = activeFrame,
                    viewportCommand = coordinator.viewportCommand,
                    listState = listState,
                    reorderableListState = reorderableListState,
                    selectedTaskIds = selectedTaskIds,
                    listColorMap = listColorMap,
                    mergedListIds = mergedListIds,
                    isSelectionMode = isSelectionMode,
                    canDrag = canDrag,
                    showCompleted = showCompleted,
                    showEmptyState = isPageLoaded,
                    horizontalPadding = horizontalPadding,
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
                    onMoveTask = { movedTaskId, targetTaskId ->
                        if (canDrag) {
                            coordinator.onUserInterruption()
                            viewModel.saveTaskOrder(movedTaskId, targetTaskId)
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
                    onCleanCompletedClick = { showCleanCompletedDialog = true },
                    onAddTaskClick = { showQuickAddTaskDialog = true }
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
                    .widthIn(max = ContentMaxWidth)
                    .padding(bottom = if (isSelectionMode) 8.dp else 80.dp)
            )
        }

        if (showDeleteSelectedDialog) {
            val count = selectedTaskIds.size
            ConfirmDialog(
                title = stringResource(R.string.delete_selected_title),
                message = pluralStringResource(R.plurals.delete_selected_message, count, count),
                confirmLabel = stringResource(R.string.action_delete),
                isDestructive = true,
                onConfirm = {
                    viewModel.deleteSelectedTasks()
                    showDeleteSelectedDialog = false
                },
                onDismiss = { showDeleteSelectedDialog = false }
            )
        }

        if (showCleanCompletedDialog) {
            val candidateCount = completedTasks.size
            ConfirmDialog(
                title = stringResource(R.string.clean_completed_title),
                message = pluralStringResource(R.plurals.clean_completed_message, candidateCount, candidateCount),
                confirmLabel = stringResource(R.string.action_clean),
                isDestructive = true,
                onConfirm = {
                    viewModel.cleanCompletedInCurrentScope { deletedCount ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (deletedCount > 0) {
                                    context.resources.getQuantityString(R.plurals.clean_completed_done, deletedCount, deletedCount)
                                } else {
                                    context.getString(R.string.clean_completed_none)
                                }
                            )
                        }
                    }
                    showCleanCompletedDialog = false
                },
                onDismiss = { showCleanCompletedDialog = false }
            )
        }

        if (showAddListDialog) {
            AddListDialog(
                lists = lists,
                onDismiss = { showAddListDialog = false },
                onConfirm = { name, color, contentMode ->
                    viewModel.addList(name, color, contentMode = contentMode)
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
                    val pageIdAtConfirm = selectedListId
                    viewModel.addTask(listId, title, content, isFlagged) { newTaskId ->
                        if (isFlagged) {
                            coordinator.onFlaggedTaskCreated(newTaskId.toInt(), listId, pageIdAtConfirm)
                        }
                    }
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
                        currentContentMode = list.contentMode,
                        onDismiss = { listSettingsDialogId = null },
                        onConfirmRename = { newName, newColor, newCompletionMode, newContentMode ->
                            viewModel.updateListInfo(list.id, newName, newColor, newCompletionMode, newContentMode)
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

        val preview = previewBackupData
        if (preview != null && previewTempPath != null) {
            ImportPreviewDialog(
                backupData = preview,
                existingListCount = existingListCount,
                existingTaskCount = existingTaskCount,
                isLoading = isImporting,
                onDismissRequest = {
                    if (!isImporting) {
                        clearPreview()
                    }
                },
                onPreExportClick = {
                    triggerExport()
                },
                onConfirmImport = { isOverwrite ->
                    scope.launch {
                        isImporting = true
                        val message = try {
                            viewModel.importBackup(preview, isOverwrite)
                            if (isOverwrite) {
                                context.getString(R.string.import_overwrite_done, preview.lists.size, preview.tasks.size)
                            } else {
                                context.getString(R.string.import_append_done, preview.lists.size, preview.tasks.size)
                            }
                        } catch (e: Exception) {
                            context.getString(R.string.import_failed, e.reason(context))
                        } finally {
                            clearPreview()
                            isImporting = false
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }
            )
        }
    }
}

/** 异常的原因文字，缺省时用“未知错误”，不把空值拼进提示。 */
private fun Throwable?.reason(context: android.content.Context): String =
    this?.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_error)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListTabsRow(
    lists: List<TaskList>?,
    selectedListId: Int,
    hapticFeedback: HapticFeedback,
    onSelectList: (Int) -> Unit,
    onAddList: () -> Unit,
    onReorderLists: (Int, Int) -> Unit,
    onSaveListOrder: () -> Unit
) {
    val summaryLabel = stringResource(R.string.summary)
    val newListLabel = stringResource(R.string.new_list)
    val moveLeftLabel = stringResource(R.string.move_left)
    val moveRightLabel = stringResource(R.string.move_right)
    val tabs = remember(lists, summaryLabel) {
        buildList {
            add(Pair(-1, summaryLabel))
            lists?.forEach { add(Pair(it.id, it.name)) }
            add(Pair(-2, ""))
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
            onReorderLists(fromListIndex, toListIndex)
        }
    }

    LazyRow(
        state = tabsState,
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.first }) { index, tab ->
            val isSelected = selectedTabIndex == index
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            val borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
            val isDragEnabled = tab.first > 0
            val pillShape = CircleShape

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
                        .shadow(elevation, shape = pillShape)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                        }
                } else {
                    Modifier
                }

                // 长按拖拽的无障碍替代操作：在自建清单之间左移、右移
                val listIndex = lists?.indexOfFirst { it.id == tab.first } ?: -1
                val reorderActions = if (isDragEnabled && lists != null && listIndex >= 0) {
                    buildList {
                        if (listIndex > 0) {
                            add(CustomAccessibilityAction(moveLeftLabel) {
                                onReorderLists(listIndex, listIndex - 1)
                                onSaveListOrder()
                                true
                            })
                        }
                        if (listIndex < lists.size - 1) {
                            add(CustomAccessibilityAction(moveRightLabel) {
                                onReorderLists(listIndex, listIndex + 1)
                                onSaveListOrder()
                                true
                            })
                        }
                    }
                } else {
                    emptyList()
                }

                val interactionModifier = if (tab.first == -2) {
                    Modifier.clickable(role = Role.Button, onClick = onAddList)
                } else {
                    Modifier
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelectList(tab.first) }
                        )
                        .semantics { customActions = reorderActions }
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
                                onSaveListOrder()
                            }
                        )
                        .heightIn(min = 48.dp)
                        .clip(pillShape)
                        .background(bgColor)
                        .border(1.dp, borderColor, pillShape)
                        .then(interactionModifier)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (tab.first == -2) {
                        Icon(Icons.Filled.Add, contentDescription = newListLabel, tint = contentColor, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = tab.second,
                            color = contentColor,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 底部批量操作：图标 + 12sp 标签的整体按钮，图标不单独朗读。 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val baseColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (enabled) baseColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 统一的确认对话框：确认在末端、取消在其左侧，均为文字按钮；破坏性确认使用 error 色（规范 4.4）。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.action_cancel),
    enabled: Boolean = true
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text(dismissLabel)
            }
        }
    )
}

/** 放弃修改确认（规范 4.4 决定 3）：操作为“继续编辑 / 放弃”。 */
@Composable
fun DiscardChangesDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.discard_title),
        message = stringResource(R.string.discard_message),
        confirmLabel = stringResource(R.string.action_discard),
        dismissLabel = stringResource(R.string.action_keep_editing),
        isDestructive = true,
        onConfirm = onDiscard,
        onDismiss = onKeepEditing
    )
}

/** 单选行：整行可点并作为一个焦点播报（规范第 6 节语义合并）。 */
@Composable
fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 分组标题（如“主题外观”），作为读屏标题。 */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() }
    )
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
        title = { Text(stringResource(R.string.app_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionHeading(stringResource(R.string.theme_section))
                Spacer(modifier = Modifier.height(8.dp))

                val modes = listOf(
                    Triple(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_desc),
                    Triple(ThemeMode.LIGHT, R.string.theme_light, R.string.theme_light_desc),
                    Triple(ThemeMode.DARK, R.string.theme_dark, R.string.theme_dark_desc)
                )

                Column(modifier = Modifier.selectableGroup()) {
                    modes.forEach { (mode, title, desc) ->
                        RadioOptionRow(
                            label = stringResource(title),
                            description = stringResource(desc),
                            selected = currentThemeMode == mode,
                            onClick = { onThemeModeSelect(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                SectionHeading(stringResource(R.string.data_section))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDataManagementClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.data_transfer))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

/**
 * 清单主题色选择：每个色块 48dp 触控区，作为单选组播报颜色名称。
 * 一行放不下全部色块时按行数均分（6 色在窄对话框中为 3×2），避免末尾色块被压扁成胶囊形。
 */
@Composable
private fun ThemeColorPicker(selectedColor: Long, onSelect: (Long) -> Unit) {
    Text(stringResource(R.string.theme_color), style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val slot = 48.dp
        val gap = 2.dp
        val fitPerRow = ((maxWidth + gap) / (slot + gap)).toInt().coerceAtLeast(1)
        val rowCount = (ListThemePalette.size + fitPerRow - 1) / fitPerRow
        val perRow = (ListThemePalette.size + rowCount - 1) / rowCount
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            ListThemePalette.withIndex().chunked(perRow).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowColors.forEach { (index, color) ->
                        ThemeColorSwatch(
                            color = color,
                            name = stringResource(ListThemeColorNames[index]),
                            isSelected = color == selectedColor,
                            onSelect = onSelect
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(color: Long, name: String, isSelected: Boolean, onSelect: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(color) })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun AddListDialog(
    lists: List<TaskList>?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Long, contentMode: Int) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isTextFieldReady by remember { mutableStateOf(false) }

    val usedColors = lists?.map { it.themeColor } ?: emptyList()
    val defaultColor = ListThemePalette.firstOrNull { !usedColors.contains(it) } ?: ListThemePalette[0]
    var selectedColor by rememberSaveable { mutableStateOf(defaultColor) }
    var selectedContentMode by rememberSaveable { mutableStateOf(TaskContentMode.TITLE_AND_DETAIL) }
    val canCreate = text.isNotBlank()

    LaunchedEffect(isTextFieldReady) {
        if (isTextFieldReady) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_list)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.list_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canCreate) onConfirm(text, selectedColor, selectedContentMode)
                    }),
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
                ThemeColorPicker(selectedColor = selectedColor, onSelect = { selectedColor = it })
                Spacer(modifier = Modifier.height(16.dp))
                ContentModeSelector(selected = selectedContentMode, onSelect = { selectedContentMode = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canCreate) onConfirm(text, selectedColor, selectedContentMode) },
                enabled = canCreate
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun MoveTasksDialog(
    lists: List<TaskList>?,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_list_title)) },
        text = {
            if (lists.isNullOrEmpty()) {
                Text(stringResource(R.string.no_lists))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(lists, key = { it.id }) { list ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { onMove(list.id) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ListColorDot(list.themeColor, 16.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** 清单色点：仅作装饰标识，不参与读屏。 */
@Composable
private fun ListColorDot(color: Long, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
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
        ConfirmDialog(
            title = stringResource(R.string.no_lists_title),
            message = stringResource(R.string.no_lists_message),
            confirmLabel = stringResource(R.string.new_list),
            onConfirm = {
                onDismiss()
                onCreateListClick()
            },
            onDismiss = onDismiss
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

@OptIn(ExperimentalMaterial3Api::class)
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
    // 合并格式清单的“信息”；切换到不同格式的清单时与标题、详细信息互相换算
    var info by rememberSaveable { mutableStateOf("") }
    var isFlagged by rememberSaveable { mutableStateOf(false) }
    fun isMergedList(listId: Int?) = lists.find { it.id == listId }?.contentMode == TaskContentMode.MERGED
    val isMerged = isMergedList(selectedListId)
    val canSave = selectedListId != null && (if (isMerged) info.isNotBlank() else title.isNotBlank())
    fun selectList(listId: Int) {
        val wasMerged = isMergedList(selectedListId)
        val nowMerged = isMergedList(listId)
        if (!wasMerged && nowMerged) {
            info = TaskText.merge(title, content)
        } else if (wasMerged && !nowMerged) {
            val (splitTitle, splitContent) = TaskText.split(info)
            title = splitTitle
            content = splitContent
        }
        selectedListId = listId
    }
    fun save() {
        val listId = selectedListId
        if (canSave && listId != null) {
            if (isMerged) {
                val (splitTitle, splitContent) = TaskText.split(info)
                onConfirm(listId, splitTitle, splitContent, isFlagged)
            } else {
                onConfirm(listId, title, content, isFlagged)
            }
        }
    }
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
                Text(stringResource(R.string.new_task), modifier = Modifier.weight(1f))
                FlagToggleButton(isFlagged = isFlagged, onToggle = { isFlagged = !isFlagged })
            }
        },
        text = {
            // 内容可滚动：输入法弹出、窗口变矮时仍能到达所有输入框
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val focusModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned {
                        if (!isTextFieldReady) {
                            isTextFieldReady = true
                        }
                    }
                if (isMerged) {
                    OutlinedTextField(
                        value = info,
                        onValueChange = { info = it },
                        label = { Text(stringResource(R.string.field_info)) },
                        minLines = 3,
                        maxLines = 8,
                        modifier = focusModifier
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.field_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = focusModifier
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.field_detail)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                var expanded by rememberSaveable { mutableStateOf(false) }
                val selectedList = lists.find { it.id == selectedListId }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedList?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.owner_list)) },
                        placeholder = { Text(stringResource(R.string.choose_list)) },
                        leadingIcon = selectedList?.let { list -> { ListColorDot(list.themeColor, 12.dp) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        lists.forEach { list ->
                            DropdownMenuItem(
                                text = { Text(list.name) },
                                leadingIcon = { ListColorDot(list.themeColor, 12.dp) },
                                onClick = {
                                    selectList(list.id)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = canSave) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** 插旗开关按钮（新建与编辑共用）：描述为将要执行的动作，并以选中状态表达当前是否插旗。 */
@Composable
fun FlagToggleButton(isFlagged: Boolean, onToggle: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onToggle, enabled = enabled) {
        Icon(
            imageVector = if (isFlagged) FlagFilledIcon else FlagOutlinedIcon,
            contentDescription = stringResource(if (isFlagged) R.string.unflag else R.string.flag),
            tint = if (isFlagged) TodoTheme.colors.flag else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ListSettingsDialog(
    currentName: String,
    currentColor: Long,
    currentCompletionMode: Int,
    currentContentMode: Int,
    onDismiss: () -> Unit,
    onConfirmRename: (name: String, color: Long, completionMode: Int, contentMode: Int) -> Unit,
    onDelete: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(currentName) }
    var selectedColor by rememberSaveable { mutableStateOf(currentColor) }
    var selectedCompletionMode by rememberSaveable { mutableStateOf(currentCompletionMode) }
    var selectedContentMode by rememberSaveable { mutableStateOf(currentContentMode) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_settings)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.list_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                ThemeColorPicker(selectedColor = selectedColor, onSelect = { selectedColor = it })
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.completion_mode), style = MaterialTheme.typography.labelLarge)
                Column(modifier = Modifier.selectableGroup()) {
                    RadioOptionRow(
                        label = stringResource(R.string.completion_keep),
                        selected = selectedCompletionMode == 0,
                        onClick = { selectedCompletionMode = 0 }
                    )
                    RadioOptionRow(
                        label = stringResource(R.string.completion_bottom),
                        selected = selectedCompletionMode == 1,
                        onClick = { selectedCompletionMode = 1 }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ContentModeSelector(selected = selectedContentMode, onSelect = { selectedContentMode = it })
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                // 危险区：删除清单不作为对话框的第三个按钮（规范 4.4）
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_list))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirmRename(text, selectedColor, selectedCompletionMode, selectedContentMode) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.delete_list_title),
            message = stringResource(R.string.delete_list_message, currentName),
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
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
        title = { Text(stringResource(R.string.summary_settings)) },
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
                    Text(
                        stringResource(R.string.summary_lists_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (lists.isNotEmpty()) {
                        val allSelected = lists.all { tempVisibilityMap[it.id] == true }
                        TextButton(
                            onClick = {
                                val target = !allSelected
                                tempVisibilityMap = lists.associate { it.id to target }
                            }
                        ) {
                            Text(stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all))
                        }
                    }
                }
                Text(
                    stringResource(R.string.summary_lists_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (lists.isEmpty()) {
                    Text(
                        stringResource(R.string.summary_no_lists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column {
                        lists.forEach { list ->
                            val isChecked = tempVisibilityMap[list.id] ?: true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .toggleable(
                                        value = isChecked,
                                        role = Role.Checkbox,
                                        onValueChange = { checked ->
                                            tempVisibilityMap = tempVisibilityMap + (list.id to checked)
                                        }
                                    )
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ListColorDot(list.themeColor, 14.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = list.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Checkbox(checked = isChecked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempVisibilityMap) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun SwipeableSnackbar(data: SnackbarData) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var visible by remember { mutableStateOf(true) }
    val dismissLabel = stringResource(R.string.dismiss_message)

    if (visible) {
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .graphicsLayer { alpha = 1f - (offset.getDistance() / 500f).coerceIn(0f, 1f) }
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(dismissLabel) {
                        visible = false
                        data.dismiss()
                        true
                    })
                }
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
    mergedListIds: Set<Int>,
    isSelectionMode: Boolean,
    canDrag: Boolean,
    showCompleted: Boolean,
    showEmptyState: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    hapticFeedback: HapticFeedback,
    onCommandConsumed: (Int, Long, Long, Long) -> Unit,
    onToggleFlag: (Task) -> Unit,
    onCardSettled: (Int) -> Unit,
    onCardDisposed: (Int) -> Unit,
    onDragStarted: (Int) -> Unit,
    onSaveTaskOrder: () -> Unit,
    onDragCancel: (Int) -> Unit,
    onMoveTask: (movedTaskId: Int, targetTaskId: Int) -> Unit,
    onToggleTaskCompletion: (Task) -> Unit,
    onUncompleteTask: (Task) -> Unit,
    onToggleTaskSelection: (Int) -> Unit,
    onTaskClick: (Int) -> Unit,
    onToggleShowCompleted: () -> Unit,
    onCleanCompletedClick: () -> Unit,
    onAddTaskClick: () -> Unit,
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

    val flagLabel = stringResource(R.string.flag)
    val unflagLabel = stringResource(R.string.unflag)
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val moveTopLabel = stringResource(R.string.move_to_top)
    val expandedLabel = stringResource(R.string.state_expanded)
    val collapsedLabel = stringResource(R.string.state_collapsed)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        if (showEmptyState && pageFrame.displayTasks.isEmpty() && pageFrame.completedTasks.isEmpty()) {
            item(key = "empty_state") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight(0.7f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.empty_tasks),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isSelectionMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onAddTaskClick) {
                            Text(stringResource(R.string.new_task))
                        }
                    }
                }
            }
        }

        val mTasks = pageFrame.displayTasks
        itemsIndexed(mTasks, key = { _, task -> task.id }) { index, task ->
            val isSelected = selectedTaskIds.contains(task.id)
            val listColor = listColorMap[task.listId] ?: ListColorFallback

            // 手势的无障碍等价入口：插旗 / 取消插旗；可拖拽范围内上移、下移、移到顶部
            val actions = if (isSelectionMode) {
                emptyList()
            } else {
                buildList {
                    add(CustomAccessibilityAction(if (task.isFlagged) unflagLabel else flagLabel) {
                        onToggleFlag(task)
                        true
                    })
                    if (canDrag) {
                        if (index > 0) {
                            add(CustomAccessibilityAction(moveUpLabel) {
                                onMoveTask(task.id, mTasks[index - 1].id)
                                true
                            })
                            add(CustomAccessibilityAction(moveTopLabel) {
                                onMoveTask(task.id, mTasks[0].id)
                                true
                            })
                        }
                        if (index < mTasks.size - 1) {
                            add(CustomAccessibilityAction(moveDownLabel) {
                                onMoveTask(task.id, mTasks[index + 1].id)
                                true
                            })
                        }
                    }
                }
            }

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
                        .shadow(elevation, shape = MaterialTheme.shapes.extraLarge)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                        }
                } else {
                    Modifier
                }

                val interactionSource = remember { MutableInteractionSource() }

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
                        isMergedInfo = task.listId in mergedListIds,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        accessibilityActions = actions,
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button, onClick = onToggleShowCompleted)
                            .semantics {
                                heading()
                                stateDescription = if (showCompleted) expandedLabel else collapsedLabel
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showCompleted) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.completed_header, pageFrame.completedTasks.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isSelectionMode) {
                        TextButton(
                            onClick = onCleanCompletedClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.action_clean))
                        }
                    }
                }
            }

            if (showCompleted) {
                // 已完成区倒序显示：最近完成的在最上（APP-037）。存储顺序不变，新完成项仍追加到区域末尾
                items(pageFrame.completedTasks.asReversed(), key = { it.id }) { task ->
                    val isSelected = selectedTaskIds.contains(task.id)
                    val listColor = listColorMap[task.listId] ?: ListColorFallback

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
                            isMergedInfo = task.listId in mergedListIds,
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
                val todoColors = TodoTheme.colors
                val bgColor = if (task.isFlagged) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    todoColors.swipeFlagContainer
                }
                val contentColor = if (task.isFlagged) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    todoColors.onSwipeFlagContainer
                }

                val progress = dismissState.progress
                val bgAlpha = (progress * 2.5f).coerceIn(0f, 1f)
                val iconScale = 1f + 0.15f * progress

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(bgColor.copy(alpha = bgAlpha))
                        .padding(horizontal = 24.dp)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(if (task.isFlagged) R.string.unflag else R.string.flag),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isThresholdReached) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                        Icon(
                            imageVector = if (task.isFlagged) FlagOutlinedIcon else FlagFilledIcon,
                            contentDescription = null,
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
    isMergedInfo: Boolean = false,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
    onToggleTask: () -> Unit,
    onClick: () -> Unit
) {
    val todoColors = TodoTheme.colors
    val selectedLabel = stringResource(R.string.state_selected)
    val notSelectedLabel = stringResource(R.string.state_not_selected)
    val completeLabel = stringResource(R.string.complete_task)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (accessibilityActions.isNotEmpty()) customActions = accessibilityActions
                if (isSelectionMode) {
                    selected = isSelected
                    stateDescription = if (isSelected) selectedLabel else notSelectedLabel
                }
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else todoColors.card,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        // 层级用色调表达，静止卡片不再叠加阴影（规范 3.5）
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 清单色条（装饰，不参与读屏）
            if (task.listId > 0) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(listThemeColor))
                )
            }

            if (isSelectionMode) {
                // 多选指示：卡片整体暴露选中状态，指示本身不单独播报
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
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
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                // 完成控件：48dp 触控区内显示 24dp 圆圈，作为复选框暴露“已选中 / 未选中”状态
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .toggleable(
                            value = task.isCompleted,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 20.dp),
                            role = Role.Checkbox,
                            onValueChange = { onToggleTask() }
                        )
                        .semantics { contentDescription = completeLabel },
                    contentAlignment = Alignment.Center
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
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 合并格式：信息跳过空行，首行作标题，其余行作次要信息；两种格式都最多显示 3 行
            val (firstLine, secondLine) = if (isMergedInfo) {
                TaskText.displayLines(task.title, task.content)
            } else {
                task.title to task.content
            }
            val textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = firstLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) todoColors.completedText else MaterialTheme.colorScheme.onSurface,
                    textDecoration = textDecoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (secondLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.isCompleted) todoColors.completedText else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = textDecoration,
                        maxLines = TaskText.SECONDARY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (task.isFlagged) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = FlagFilledIcon,
                    contentDescription = stringResource(R.string.flagged),
                    tint = todoColors.flag,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
