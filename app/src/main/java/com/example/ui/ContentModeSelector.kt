package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.TaskContentMode

/** 新建清单与清单设置中的“待办格式”选项（整行单选）。 */
@Composable
fun ContentModeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(stringResource(R.string.content_mode), style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.selectableGroup()) {
            RadioOptionRow(
                label = stringResource(R.string.content_title_detail),
                selected = selected == TaskContentMode.TITLE_AND_DETAIL,
                onClick = { onSelect(TaskContentMode.TITLE_AND_DETAIL) }
            )
            RadioOptionRow(
                label = stringResource(R.string.content_merged),
                selected = selected == TaskContentMode.MERGED,
                onClick = { onSelect(TaskContentMode.MERGED) }
            )
        }
    }
}
