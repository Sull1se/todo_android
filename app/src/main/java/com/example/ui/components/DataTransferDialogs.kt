package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.transfer.BackupData

@Composable
fun DataManagementDialog(
    onDismissRequest: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("数据管理", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "支持将全部清单和待办导出为本地 JSON 单文件，或从备份文件导入数据。\n\n提示：备份文件为明文，包含您的待办详情，请妥善保管。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        onDismissRequest()
                        onExportClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导出全部数据 (JSON)")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onDismissRequest()
                        onImportClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("从 JSON 文件导入")
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
fun ImportPreviewDialog(
    backupData: BackupData,
    existingListCount: Int,
    existingTaskCount: Int,
    isLoading: Boolean,
    onDismissRequest: () -> Unit,
    onPreExportClick: () -> Unit,
    onConfirmImport: (isOverwrite: Boolean) -> Unit
) {
    var isOverwrite by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismissRequest()
            }
        },
        title = {
            Text("导入数据预览", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "文件信息",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "来源版本: ${backupData.sourceAppVersion}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "导出时间: ${backupData.exportedAt}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "文件包含: ${backupData.lists.size} 个清单，${backupData.tasks.size} 个待办",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text("选择导入模式", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                // 追加模式（默认）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { isOverwrite = false }
                ) {
                    RadioButton(
                        selected = !isOverwrite,
                        onClick = { isOverwrite = false },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("追加导入（保留现有数据）", fontWeight = FontWeight.Medium)
                        Text(
                            "文件中的清单和待办将追加到末尾，现有数据不受任何影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 覆盖模式
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { isOverwrite = true }
                ) {
                    RadioButton(
                        selected = isOverwrite,
                        onClick = { isOverwrite = true },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("覆盖导入（清空现有数据）", fontWeight = FontWeight.Medium)
                        Text(
                            "完全替换为文件中的内容，清空当前已有清单与待办。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isOverwrite) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "警告",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "高风险操作提示",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "覆盖导入将完全删除现存的 $existingListCount 个清单和 $existingTaskCount 个待办！此操作不可逆回退。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = onPreExportClick,
                                enabled = !isLoading
                            ) {
                                Text("先导出当前数据备份", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmImport(isOverwrite) },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在导入...")
                } else {
                    Text(if (isOverwrite) "确认覆盖导入" else "确认追加导入")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        }
    )
}
