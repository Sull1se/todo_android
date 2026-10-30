package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.TaskContentMode

/** 新建列表与列表设置中的“待办格式”选项。 */
@Composable
fun ContentModeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text("待办格式", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        ContentModeOption("标题 + 详细信息", TaskContentMode.TITLE_AND_DETAIL, selected, onSelect)
        ContentModeOption("合并为信息", TaskContentMode.MERGED, selected, onSelect)
    }
}

@Composable
private fun ContentModeOption(label: String, mode: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Text(label)
    }
}
