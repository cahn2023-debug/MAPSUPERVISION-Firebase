package com.mapsupervision.app.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*
@Composable
fun FieldChip(text: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

fun describeNodeByField(node: GisNode, field: GisLabelField): String = when (field) {
    GisLabelField.CODE -> "Mã: ${node.code}"
    GisLabelField.CONTRACTOR -> "Nhà thầu: ${node.contractor}"
    GisLabelField.COORDINATE -> "Tọa độ: ${node.latitude}, ${node.longitude}"
}

fun formatNoteTime(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesAndTasksBottomSheet(
    objectCode: String,
    notes: List<Note>,
    tasks: List<Task>,
    aiSummary: String,
    aiSuggestions: List<String>,
    isAiLoading: Boolean,
    onDismiss: () -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String, String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onToggleTask: (String, String, TaskStatus) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onSummarize: (String) -> Unit,
    onSuggest: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Ghi chú", "Nhiệm vụ")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.extendedColors.panelBackgroundOverlay,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ghi chú & CV: $objectCode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.extendedColors.panelBackground,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).padding(bottom = 16.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    var noteText by remember { mutableStateOf("") }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (aiSummary.isNotBlank() || isAiLoading) {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.extendedColors.panelBackgroundAlt),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tóm tắt ghi chú (AI)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (isAiLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text(aiSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (notes.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Chưa có ghi chú nào. Hãy thêm ghi chú đầu tiên!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                itemsIndexed(notes, key = { index, note -> "${note.id}:$index" }) { _, note ->
                                    ElevatedCard(
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.extendedColors.panelBackgroundAlt),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = formatNoteTime(note.createdAtEpochMs),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(note.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                            }
                                            IconButton(onClick = { onDeleteNote(note.id, objectCode) }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = { Text("Nhập ghi chú mới...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.extendedColors.panelBackground,
                                    unfocusedContainerColor = MaterialTheme.extendedColors.panelBackground,
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        onAddNote(objectCode, noteText)
                                        noteText = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gửi", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (notes.isNotEmpty() && !isAiLoading && aiSummary.isBlank()) {
                            Button(
                                onClick = { onSummarize(objectCode) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.panelBackgroundAlt),
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tóm tắt ghi chú (AI)", fontSize = 13.sp)
                            }
                        }
                    }
                }
                1 -> {
                    var taskTitle by remember { mutableStateOf("") }
                    var filterState by remember { mutableStateOf("ALL") }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("ALL" to "Tất cả", "TODO" to "Chưa làm", "IN_PROGRESS" to "Đang làm", "COMPLETED" to "Hoàn thành").forEach { (key, label) ->
                                val selected = filterState == key
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.panelBackgroundAlt,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { filterState = key }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (aiSuggestions.isNotEmpty() || isAiLoading) {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.extendedColors.panelBackgroundAlt),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Đề xuất nhiệm vụ tiếp theo", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (isAiLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            aiSuggestions.forEach { suggestion ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.extendedColors.panelBackground, RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            onAddTask(objectCode, suggestion)
                                                        }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(suggestion, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val filteredTasks = tasks.filter {
                            when (filterState) {
                                "TODO" -> it.status == TaskStatus.TODO
                                "IN_PROGRESS" -> it.status == TaskStatus.IN_PROGRESS
                                "COMPLETED" -> it.status == TaskStatus.COMPLETED
                                else -> true
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredTasks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Không có nhiệm vụ nào.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                }
                            } else {
                                itemsIndexed(filteredTasks, key = { index, task -> "${task.id}:$index" }) { _, task ->
                                    val isCompleted = task.status == TaskStatus.COMPLETED
                                    val isProgress = task.status == TaskStatus.IN_PROGRESS

                                    ElevatedCard(
                                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF262D3D)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = isCompleted,
                                                    onCheckedChange = { onToggleTask(task.id, objectCode, task.status) },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Color(0xFFF5A623),
                                                        uncheckedColor = Color(0xFF94A3B8)
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = task.title,
                                                        fontSize = 13.sp,
                                                        color = if (isCompleted) Color(0xFF94A3B8) else Color(0xFFF8FAFC),
                                                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                when {
                                                                    isCompleted -> Color(0x333B82F6)
                                                                    isProgress -> Color(0x33F97316)
                                                                    else -> Color(0x3364748B)
                                                                },
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                isCompleted -> "Hoàn thành"
                                                                isProgress -> "Đang làm"
                                                                else -> "Chưa làm"
                                                            },
                                                            fontSize = 9.sp,
                                                            color = when {
                                                                isCompleted -> Color(0xFF60A5FA)
                                                                isProgress -> Color(0xFFFB923C)
                                                                else -> Color(0xFF94A3B8)
                                                            },
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { onDeleteTask(task.id, objectCode) }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Xóa", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = taskTitle,
                                onValueChange = { taskTitle = it },
                                placeholder = { Text("Nhập công việc mới...", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFF5A623),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (taskTitle.isNotBlank()) {
                                        onAddTask(objectCode, taskTitle)
                                        taskTitle = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(Color(0xFFF5A623), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "Thêm", tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (notes.isNotEmpty() && !isAiLoading && aiSuggestions.isEmpty()) {
                            Button(
                                onClick = { onSuggest(objectCode) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Đề xuất nhiệm vụ tiếp theo", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NodePhotoViewerDialog(
    photos: List<com.mapsupervision.domain.model.SitePhoto>,
    onDismiss: () -> Unit
) {
    var fullscreenPhoto by remember { mutableStateOf<com.mapsupervision.domain.model.SitePhoto?>(null) }
    val darkBgColor = Color(0xFF0F172A)
    val cardBgColor = Color(0xFF1E293B)
    val textColor = Color(0xFFF8FAFC)
    val secondaryTextColor = Color(0xFF94A3B8)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(darkBgColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (photos.isEmpty()) "Ảnh hiện trường" else "Ảnh hiện trường (${photos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (photos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có ảnh nào cho điểm này", color = secondaryTextColor)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val rows = photos.chunked(3)
                        itemsIndexed(rows, key = { index, rowPhotos -> "${rowPhotos.firstOrNull()?.id ?: "row"}:$index" }) { _, rowPhotos ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowPhotos.forEach { photo ->
                                    MapPhotoThumb(
                                        photo = photo,
                                        modifier = Modifier.weight(1f),
                                        onClick = { fullscreenPhoto = photo }
                                    )
                                }
                                repeat(3 - rowPhotos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fullscreenPhoto?.let { photo ->
        MapPhotoFullscreenDialog(photo = photo, onDismiss = { fullscreenPhoto = null })
    }
}

@Composable
fun MapPhotoThumb(
    photo: com.mapsupervision.domain.model.SitePhoto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SitePhotoThumb(
        photo = photo,
        modifier = modifier.aspectRatio(1f),
        onClick = onClick
    )
}

@Composable
fun MapPhotoFullscreenDialog(
    photo: com.mapsupervision.domain.model.SitePhoto,
    onDismiss: () -> Unit
) {
    val displayFile = java.io.File(
        if (photo.mediaType == com.mapsupervision.domain.model.MediaType.VIDEO) {
            photo.thumbnailPath.ifBlank { photo.filePath }
        } else {
            photo.filePath
        }
    )
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        ) {
            if (displayFile.exists()) {
                Image(
                    painter = coil.compose.rememberAsyncImagePainter(displayFile),
                    contentDescription = photo.objectCode,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (photo.mediaType == com.mapsupervision.domain.model.MediaType.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            try {
                                val videoFile = java.io.File(photo.filePath)
                                val authority = "${context.packageName}.fileprovider"
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, videoFile)
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/mp4")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Phát Video",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Đối tượng: ${photo.objectCode}", color = Color.White, fontWeight = FontWeight.Bold)
                val ts = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(photo.capturedAtEpochMs))
                Text("Thời gian: $ts", color = Color.White, fontSize = 12.sp)
                if (photo.latitude != null && photo.longitude != null) {
                    Text(
                        "Tọa độ: ${"%.6f".format(photo.latitude)}, ${"%.6f".format(photo.longitude)}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = Color.White)
            }
        }
    }
}
