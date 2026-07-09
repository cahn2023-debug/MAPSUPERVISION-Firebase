package com.mapsupervision.app.workspace

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mapsupervision.app.resolveIncomingShareMediaType
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*
import com.mapsupervision.domain.model.MediaType
import com.mapsupervision.domain.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareImportSheet(
    pendingSharedImport: PendingSharedImport,
    projects: List<Project>,
    activeProjectId: String?,
    workspaceViewModel: WorkspaceViewModel,
    projectViewModel: com.mapsupervision.project.ui.ProjectViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val draft = pendingSharedImport.draft
    val selectedProjectId = draft.selectedProjectId ?: activeProjectId ?: projects.firstOrNull()?.id
    val visibleUris = pendingSharedImport.payload.uris.filterNot { draft.removedUriStrings.contains(it.toString()) }
    val visibleMediaItems = remember(visibleUris, pendingSharedImport.payload.mimeType) {
        visibleUris.map { uri ->
            uri to resolveIncomingShareMediaType(
                context = context,
                uri = uri,
                fallbackMimeType = pendingSharedImport.payload.mimeType
            )
        }
    }
    val activeProjectMatches = selectedProjectId != null && selectedProjectId == activeProjectId
    val query = draft.searchQuery.trim()
    val normalizedQuery = normalizeMapSearchText(query)
    val indexes = workspaceViewModel.ensureIndexes()
    val nodeOptions = if (draft.targetKind == SharedMediaTargetKind.NODE) {
        indexes.progressUi.nodeSelectorOptions.filter { option ->
            query.isBlank() ||
                normalizeMapSearchText(option.label).contains(normalizedQuery) ||
                normalizeMapSearchText(option.key).contains(normalizedQuery)
        }
    } else {
        emptyList()
    }
    val routeOptions = if (draft.targetKind == SharedMediaTargetKind.ROUTE) {
        indexes.progressUi.routeSelectorOptions.filter { option ->
            query.isBlank() ||
                normalizeMapSearchText(option.label).contains(normalizedQuery) ||
                normalizeMapSearchText(option.key).contains(normalizedQuery)
        }
    } else {
        emptyList()
    }
    val targetOptions = when (draft.targetKind) {
        SharedMediaTargetKind.NODE -> nodeOptions
        SharedMediaTargetKind.ROUTE -> routeOptions
    }
    val canConfirm = activeProjectMatches &&
        draft.targetCode.isNotBlank() &&
        visibleUris.isNotEmpty() &&
        targetOptions.any { it.key == draft.targetCode }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nhận media chia sẻ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    Text(
                        text = "Chọn dự án, review media và gán vào vị trí/tuyến trước khi lưu.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng")
                }
            }

            SectionTitle(text = "1. Chọn dự án")
            if (projects.isEmpty()) {
                Text(
                    text = "Chưa có dự án nào để nhận media.",
                    color = colors.error,
                    fontSize = 13.sp
                )
            } else {
                LazyRowChips(
                    items = projects,
                    selectedProjectId = selectedProjectId,
                    onSelect = { project ->
                        workspaceViewModel.dispatch(
                            WorkspaceAction.UpdatePendingSharedImport(
                                pendingSharedImport.copy(
                                    draft = pendingSharedImport.draft.copy(
                                        selectedProjectId = project.id,
                                        targetCode = "",
                                        searchQuery = ""
                                    )
                                )
                            )
                        )
                        projectViewModel.switchProject(project.id)
                    }
                )
            }

            SectionTitle(text = "2. Review media")
            if (visibleUris.isEmpty()) {
                Text(
                    text = "Không còn media nào trong batch này.",
                    color = colors.error,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = "${visibleUris.size} media sẽ được lưu vào cùng một đích.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleMediaItems, key = { it.first.toString() }) { (uri, mediaType) ->
                        SharedMediaReviewItem(
                            uri = uri,
                            mediaType = mediaType,
                            onRemove = {
                                workspaceViewModel.dispatch(
                                    WorkspaceAction.UpdatePendingSharedImport(
                                        pendingSharedImport.copy(
                                            draft = pendingSharedImport.draft.copy(
                                                removedUriStrings = pendingSharedImport.draft.removedUriStrings + uri.toString()
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    }
                }
            }

            SectionTitle(text = "3. Chọn vị trí / tuyến")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.targetKind == SharedMediaTargetKind.NODE,
                    onClick = {
                        workspaceViewModel.dispatch(
                            WorkspaceAction.UpdatePendingSharedImport(
                                pendingSharedImport.copy(
                                    draft = draft.copy(targetKind = SharedMediaTargetKind.NODE, targetCode = "", searchQuery = "")
                                )
                            )
                        )
                    },
                    label = { Text("Vị trí") },
                    leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = extendedColors.mapAccent.copy(alpha = 0.18f),
                        selectedLabelColor = colors.onBackground
                    )
                )
                FilterChip(
                    selected = draft.targetKind == SharedMediaTargetKind.ROUTE,
                    onClick = {
                        workspaceViewModel.dispatch(
                            WorkspaceAction.UpdatePendingSharedImport(
                                pendingSharedImport.copy(
                                    draft = draft.copy(targetKind = SharedMediaTargetKind.ROUTE, targetCode = "", searchQuery = "")
                                )
                            )
                        )
                    },
                    label = { Text("Tuyến") },
                    leadingIcon = { Icon(Icons.Outlined.Route, contentDescription = null) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = extendedColors.mapAccent.copy(alpha = 0.18f),
                        selectedLabelColor = colors.onBackground
                    )
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    workspaceViewModel.dispatch(
                        WorkspaceAction.UpdatePendingSharedImport(
                            pendingSharedImport.copy(draft = draft.copy(searchQuery = value))
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Tìm mã hoặc tên...") }
            )

            if (!activeProjectMatches && selectedProjectId != null) {
                Text(
                    text = "Đang tải dữ liệu của dự án đã chọn...",
                    color = colors.tertiary,
                    fontSize = 12.sp
                )
            }

            if (targetOptions.isEmpty()) {
                Text(
                    text = if (draft.targetKind == SharedMediaTargetKind.NODE) {
                        "Dự án này chưa có vị trí phù hợp để gán media."
                    } else {
                        "Dự án này chưa có tuyến phù hợp để gán media."
                    },
                    color = colors.error,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(targetOptions, key = { it.key }) { option ->
                        val isSelected = draft.targetCode == option.key
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) extendedColors.mapAccent else colors.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    workspaceViewModel.dispatch(
                                        WorkspaceAction.UpdatePendingSharedImport(
                                            pendingSharedImport.copy(
                                                draft = draft.copy(targetCode = option.key)
                                            )
                                        )
                                    )
                                },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(extendedColors.panelBackgroundAlt),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (draft.targetKind == SharedMediaTargetKind.NODE) Icons.Outlined.Place else Icons.Outlined.Route,
                                        contentDescription = null,
                                        tint = extendedColors.mapAccent
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = option.key,
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (visibleUris.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = {
                            val targetCode = draft.targetCode
                            val projectId = selectedProjectId ?: return@Button
                            workspaceViewModel.importSharedMedia(
                                projectId = projectId,
                                objectCode = targetCode,
                                uris = visibleUris
                            )
                            workspaceViewModel.dispatch(WorkspaceAction.ClearPendingSharedImport)
                        },
                        enabled = canConfirm,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.mapAccent,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Text("Lưu media")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

@Composable
private fun LazyRowChips(
    items: List<Project>,
    selectedProjectId: String?,
    onSelect: (Project) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { project ->  
            val isSelected = selectedProjectId == project.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(project) },
                label = {
                    Text(
                        text = project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun SharedMediaReviewItem(
    uri: Uri,
    mediaType: MediaType?,
    onRemove: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isVideo = mediaType == MediaType.VIDEO
    ElevatedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    fallback = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("Video", color = Color.White, fontSize = 8.sp)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uri.lastPathSegment ?: uri.toString(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isVideo) "Video sẽ được lưu vào đích đã chọn." else "Ảnh sẽ được lưu vào đích đã chọn.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Bỏ media")
            }
        }
    }
}
