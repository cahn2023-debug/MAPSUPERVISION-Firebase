package com.mapsupervision.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = hiltViewModel()) {
    val progress by viewModel.progress.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val aiSummary by viewModel.aiSummary.collectAsState()
    val aiHighlights by viewModel.aiHighlights.collectAsState()
    var node by remember { mutableStateOf("N-001") }
    var planned by remember { mutableStateOf("100") }
    var actual by remember { mutableStateOf("40") }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tiến độ + Nhật ký hàng ngày", style = MaterialTheme.typography.titleLarge)
        if (aiSummary.isNotBlank()) {
            Text("AI: $aiSummary", style = MaterialTheme.typography.bodyMedium)
        }
        aiHighlights.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = node, onValueChange = { node = it }, label = { Text("Node (Nút)") })
            OutlinedTextField(value = planned, onValueChange = { planned = it }, label = { Text("Kế hoạch") })
            OutlinedTextField(value = actual, onValueChange = { actual = it }, label = { Text("Thực tế") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                viewModel.addProgress(node, planned.toFloatOrNull() ?: 0f, actual.toFloatOrNull() ?: 0f)
            }) { Text("Lưu tiến độ") }
            Button(onClick = { viewModel.addDailyLog("Thi công tại hiện trường", 8, "Không có trở ngại") }) { Text("Thêm nhật ký") }
            Button(onClick = { viewModel.refresh() }) { Text("Làm mới") }
        }
        Text("Tiến độ", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(progress) { p -> Text("${p.nodeCode}: ${p.actual}/${p.planned}, còn lại ${p.remain}") }
        }
        Text("Nhật ký hàng ngày", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs) { l -> Text("${l.workItem} | nhân lực ${l.manpower} | ${l.note}") }
        }
    }
}
