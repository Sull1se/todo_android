package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.ui.UndoRecord
import kotlinx.coroutines.delay

/** 撤销提示的基准停留时长；开启无障碍服务时按系统建议延长。 */
const val UndoBaseTimeoutMillis = 4000L

@Composable
fun UndoMessageHost(
    records: List<UndoRecord>,
    isPaused: Boolean,
    onUndoClick: (UndoRecord) -> Unit,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) return

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.45f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .zIndex(100f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 最新在最上方（records[0] 为最新）
            for (record in records) {
                key(record.eventId) {
                    UndoMessageCard(
                        record = record,
                        isPaused = isPaused,
                        onUndoClick = { onUndoClick(record) },
                        onDismiss = { onDismiss(record.eventId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UndoMessageCard(
    record: UndoRecord,
    isPaused: Boolean,
    onUndoClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val accessibilityManager = LocalAccessibilityManager.current
    val timeoutMillis = remember(accessibilityManager) {
        accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = UndoBaseTimeoutMillis,
            containsIcons = false,
            containsText = true,
            containsControls = true
        ) ?: UndoBaseTimeoutMillis
    }
    // 独立倒计时，支持在暂停时保留剩余时间
    val remainingHolder = remember(record.eventId) { longArrayOf(timeoutMillis) }

    LaunchedEffect(record.eventId, isPaused) {
        val interval = 100L
        while (remainingHolder[0] > 0) {
            if (!isPaused) {
                delay(interval)
                remainingHolder[0] -= interval
            } else {
                delay(interval)
            }
        }
        onDismiss()
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    val dismissLabel = stringResource(R.string.dismiss_message)

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        content = {
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        // 左滑关闭的无障碍替代操作
                        customActions = listOf(CustomAccessibilityAction(dismissLabel) {
                            onDismiss()
                            true
                        })
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.taskTitle.ifBlank { stringResource(R.string.task_fallback) },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(record.actionType.labelRes),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = onUndoClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_undo))
                    }
                }
            }
        }
    )
}
