package com.mapsupervision.app.workspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.storage.importer.ExcelClassificationMode
import com.mapsupervision.app.workspace.WorkspaceImportHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHubScreen(
    state: WorkspaceState,
    dataHubUiState: DataHubUiState,
    screenUiState: DataHubScreenUiState,
    onSetDesignTab: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateContractorFilter: (String) -> Unit,
    onSetContractorMenuExpanded: (Boolean) -> Unit,
    onUpdateObjectTypeFilter: (String) -> Unit,
    onUpdateSortOrder: (String) -> Unit,
    onSetSortMenuExpanded: (Boolean) -> Unit,
    onShowNotesAndTasks: (String) -> Unit,
    onDismissNotesAndTasks: () -> Unit,
    onOpenPicker: () -> Unit,
    onPickerEmpty: () -> Unit,
    onUploadDesign: (List<Uri>) -> Unit,
    onLoadNonExcelPreview: (Uri, String?) -> Unit,
    onUpdateImportMappingUi: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onSetImportMappingDialogVisible: (Boolean) -> Unit,
    onParseNonExcelToDesign: () -> Unit,
    onRetryFailedImports: () -> Unit,
    onLoadExcelPreview: (Uri, String?) -> Unit,
    onUpdateExcelMapping: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onUpdateExcelClassificationMode: (ExcelClassificationMode) -> Unit,
    onSetExcelMappingDialogVisible: (Boolean) -> Unit,
    onUpdateExcelCoordinateMode: (Boolean) -> Unit,
    onUpdateMapVisualOptions: (Boolean?, Boolean?) -> Unit,
    onParseExcelToDesign: () -> Unit,
    onAddConstruction: (String, Float, Float) -> Unit,
    onUpdateWorkVolumeProgress: (String, String, String) -> Unit,
    onOpenNodeOnMap: (GisNode) -> Unit,
    onOpenRouteOnMap: (GisRoute) -> Unit,
    onDeleteImportedFile: (String) -> Unit,
    onRepairImportedGeometry: (ImportedFile) -> Unit,
    photoFilterNodeCode: String?,
    onClearPhotoFilter: () -> Unit,
    onLoadNotesAndTasks: (String) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String, String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onSummarizeNotes: (String) -> Unit,
    onSuggestTasks: (String) -> Unit,
    onCombineFiles: (ImportedFile, ImportedFile, List<GisNode>, List<GisRoute>) -> Unit,
    onUpdateSelectedExcelSheet: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val extendedColors = MaterialTheme.extendedColors
    val darkBgColor = MaterialTheme.colorScheme.background
    val cardBgColor = extendedColors.panelBackgroundAlt
    val orangeColor = extendedColors.mapAccent
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            onPickerEmpty()
        } else {
            if (uris.size == 1) {
                val uri = uris.first()
                val mimeType = context.contentResolver.getType(uri)?.lowercase(java.util.Locale.US)
                val displayName = context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }.orEmpty()
                val extFromName = displayName.substringAfterLast('.', "").lowercase(java.util.Locale.US)
                val extFromUri = uri.toString().substringAfterLast('.', "").lowercase(java.util.Locale.US)
                val ext = when {
                    extFromName.isNotBlank() -> extFromName
                    extFromUri.length in 2..5 -> extFromUri
                    mimeType?.contains("kmz") == true -> "kmz"
                    mimeType?.contains("kml") == true -> "kml"
                    mimeType?.contains("json") == true -> "json"
                    else -> ""
                }
                val isExcel = mimeType?.contains("excel") == true || mimeType?.contains("spreadsheet") == true ||
                        ext == "xls" || ext == "xlsx"
                val isKmlOrKmz = ext == "kml" || ext == "kmz" ||
                        mimeType?.contains("kml") == true || mimeType?.contains("kmz") == true
                val isGeoJson = ext == "geojson" || ext == "json" || mimeType?.contains("json") == true
                if (isExcel) {
                    onLoadExcelPreview(uri, null)
                } else if (isKmlOrKmz) {
                    // KML/KMZ geometry requires field mapping. Load preview to show mapping dialog.
                    onLoadNonExcelPreview(uri, null)
                } else if (isGeoJson) {
                    onLoadNonExcelPreview(uri, null)
                } else {
                    onUploadDesign(uris)
                }
            } else {
                onUploadDesign(uris)
            }
        }
    }

    if (state.excelParserUi.showMappingDialog) {
        ExcelMappingDialog(
            state = state.excelParserUi,
            onDismiss = { onSetExcelMappingDialogVisible(false) },
            onUpdateExcelMapping = onUpdateExcelMapping,
            onUpdateCoordinateMode = onUpdateExcelCoordinateMode,
            onUpdateMapVisualOptions = onUpdateMapVisualOptions,
            onConfirmParse = onParseExcelToDesign,
            onUpdateSelectedSheet = onUpdateSelectedExcelSheet
        )
    }

    if (state.importMappingUi.showMappingDialog) {
        NonExcelMappingDialog(
            state = state.importMappingUi,
            onDismiss = { onSetImportMappingDialogVisible(false) },
            onUpdateMapping = onUpdateImportMappingUi,
            onConfirmParse = onParseNonExcelToDesign
        )
    }

    Scaffold(
        containerColor = darkBgColor,
        floatingActionButton = {
            if (screenUiState.isDesignTab) {
                FloatingActionButton(
                    onClick = {
                        onOpenPicker()
                        picker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/vnd.google-earth.kml+xml",
                                "application/vnd.google-earth.kmz",
                                "*/*"
                            )
                        )
                    },
                    containerColor = orangeColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = "Tải lên dữ liệu", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    ) { paddingValues ->
        WorkspaceRefreshContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quản lý Danh sách & Dữ liệu",
                        color = textColor,
                        fontSize = if (screenUiState.isDesignTab) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!screenUiState.isDesignTab) {
                        Text(
                            text = "Quản lý thiết kế và cập nhật tiến độ thi công hạ tầng kỹ thuật.",
                            color = secondaryTextColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tab Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isSelected = !screenUiState.isDesignTab
                val buttonModifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .let {
                        if (isSelected) it.background(brush = NeonCyberOrangeGradient)
                        else it.background(Color.White.copy(alpha = 0.05f))
                    }
                
                Box(
                    modifier = buttonModifier.clickable { onSetDesignTab(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Handyman, 
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF3D1F00) else secondaryTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Cập nhật thi công", 
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) Color(0xFF3D1F00) else secondaryTextColor
                        )
                    }
                }
                
                val isDesignSelected = screenUiState.isDesignTab
                val designBtnModifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .let {
                        if (isDesignSelected) it.background(brush = NeonCyberOrangeGradient)
                        else it.background(Color.White.copy(alpha = 0.05f))
                    }
                
                Box(
                    modifier = designBtnModifier.clickable { onSetDesignTab(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Architecture, 
                            contentDescription = null,
                            tint = if (isDesignSelected) Color(0xFF3D1F00) else secondaryTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Nhập thiết kế", 
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isDesignSelected) Color(0xFF3D1F00) else secondaryTextColor
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            ImportStatusBanner(
                importUi = state.importUi,
                onRetryFailedImports = onRetryFailedImports
            )

            DesignTabContent(
                state = state,
                dataHubUiState = dataHubUiState,
                screenUiState = screenUiState,
                cardBgColor = cardBgColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                orangeColor = orangeColor,
                onLoadExcelPreview = onLoadExcelPreview,
                onLoadNonExcelPreview = onLoadNonExcelPreview,
                onDeleteImportedFile = onDeleteImportedFile,
                onUpdateSearchQuery = onUpdateSearchQuery,
                onUpdateContractorFilter = onUpdateContractorFilter,
                onSetContractorMenuExpanded = onSetContractorMenuExpanded,
                onUpdateObjectTypeFilter = onUpdateObjectTypeFilter,
                onUpdateSortOrder = onUpdateSortOrder,
                onSetSortMenuExpanded = onSetSortMenuExpanded,
                isDesignTab = screenUiState.isDesignTab,
                onUpdateWorkVolumeProgress = onUpdateWorkVolumeProgress,
                onOpenNodeOnMap = onOpenNodeOnMap,
                onOpenRouteOnMap = onOpenRouteOnMap,
                onShowNotesAndTasks = onShowNotesAndTasks,
                onDismissNotesAndTasks = onDismissNotesAndTasks,
                onRepairImportedGeometry = onRepairImportedGeometry,
                onLoadNotesAndTasks = onLoadNotesAndTasks,
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onAddTask = onAddTask,
                onToggleTaskStatus = onToggleTaskStatus,
                onDeleteTask = onDeleteTask,
                onSummarizeNotes = onSummarizeNotes,
                onSuggestTasks = onSuggestTasks,
                onCombineFiles = onCombineFiles
            )
        }
        }
    }
}

@Composable
private fun ImportStatusBanner(
    importUi: ImportUiState,
    onRetryFailedImports: () -> Unit
) {
    when (importUi.status) {
        ImportStatus.IDLE -> Unit
        ImportStatus.PICKING -> WorkspaceLoadingState("Đang mở trình chọn dữ liệu...")
        ImportStatus.IMPORTING -> {
            val progressText = if (importUi.totalFiles > 0) {
                "${importUi.processedFiles}/${importUi.totalFiles} file"
            } else {
                "Đang xử lý file"
            }
            WorkspaceLoadingState("Đang import dữ liệu: $progressText")
        }
        ImportStatus.DONE -> {
            AppPanelCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = importUi.message.ifBlank { "Import hoàn tất" },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (importUi.warnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = importUi.warnings.take(2).joinToString("\n"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        ImportStatus.PARTIAL_FAILED,
        ImportStatus.FAILED -> {
            val failureDetail = buildString {
                if (importUi.message.isNotBlank()) append(importUi.message)
                val visibleFailures = importUi.failures.take(2)
                if (visibleFailures.isNotEmpty()) {
                    if (isNotBlank()) append("\n")
                    append(visibleFailures.joinToString("\n"))
                }
            }
            WorkspaceErrorState(
                message = ScreenUiMessage(
                    title = if (importUi.status == ImportStatus.PARTIAL_FAILED) {
                        "Import một phần dữ liệu thất bại"
                    } else {
                        "Import dữ liệu thất bại"
                    },
                    detail = failureDetail.ifBlank { null },
                    actionLabel = "Thử import lại"
                ),
                onRetry = onRetryFailedImports
            )
        }
    }
    if (importUi.status != ImportStatus.IDLE) {
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignTabContent(
    state: WorkspaceState,
    dataHubUiState: DataHubUiState,
    screenUiState: DataHubScreenUiState,
    cardBgColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    orangeColor: Color,
    onLoadExcelPreview: (Uri, String?) -> Unit,
    onLoadNonExcelPreview: (Uri, String?) -> Unit,
    onDeleteImportedFile: (String) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateContractorFilter: (String) -> Unit,
    onSetContractorMenuExpanded: (Boolean) -> Unit,
    onUpdateObjectTypeFilter: (String) -> Unit,
    onUpdateSortOrder: (String) -> Unit,
    onSetSortMenuExpanded: (Boolean) -> Unit,
    isDesignTab: Boolean,
    onUpdateWorkVolumeProgress: (String, String, String) -> Unit,
    onOpenNodeOnMap: (GisNode) -> Unit,
    onOpenRouteOnMap: (GisRoute) -> Unit,
    onRepairImportedGeometry: (ImportedFile) -> Unit,
    onShowNotesAndTasks: (String) -> Unit,
    onDismissNotesAndTasks: () -> Unit,
    onLoadNotesAndTasks: (String) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String, String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onSummarizeNotes: (String) -> Unit,
    onSuggestTasks: (String) -> Unit,
    onCombineFiles: (ImportedFile, ImportedFile, List<GisNode>, List<GisRoute>) -> Unit
) {
    var draggedFile by remember { mutableStateOf<ImportedFile?>(null) }
    var selectedFile by remember { mutableStateOf<ImportedFile?>(null) }
    var fileBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var combineTarget by remember { mutableStateOf<Pair<ImportedFile, ImportedFile>?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    if (combineTarget != null) {
        CombineFilesDialog(
            file1 = combineTarget!!.first,
            file2 = combineTarget!!.second,
            state = state,
            onDismiss = { combineTarget = null },
            onConfirm = { f1, f2, nodes, routes ->
                onCombineFiles(f1, f2, nodes, routes)
                combineTarget = null
            }
        )
    }

    val configuration = LocalConfiguration.current
    val extendedColors = MaterialTheme.extendedColors
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val dangerColor = extendedColors.danger
    val gridMinSize = when {
        configuration.screenWidthDp >= 1200 -> 280.dp
        configuration.screenWidthDp >= 840 -> 320.dp
        else -> 350.dp
    }

    val contractors = dataHubUiState.contractorOptions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dropdown Filter
        Box(modifier = Modifier.weight(0.4f)) {
            OutlinedButton(
                onClick = { onSetContractorMenuExpanded(true) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    text = if (screenUiState.contractorFilter == "Chọn nhà thầu") "Chọn nhà thầu" else screenUiState.contractorFilter,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    fontSize = 12.sp
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = screenUiState.contractorMenuExpanded,
                onDismissRequest = {
                    onSetContractorMenuExpanded(false)
                },
                modifier = Modifier.background(cardBgColor)
            ) {
                contractors.forEach { contractor ->
                    DropdownMenuItem(
                        text = { Text(contractor, color = textColor) },
                        onClick = {
                            onUpdateContractorFilter(contractor)
                            onSetContractorMenuExpanded(false)
                        }
                    )
                }
            }
        }
        
        // Search bar
        val searchFocusManager = LocalFocusManager.current
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        BasicTextField(
            value = screenUiState.searchQuery,
            onValueChange = {
                onUpdateSearchQuery(it)
            },
            modifier = Modifier.weight(0.6f).height(48.dp),
            textStyle = TextStyle(color = textColor, fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { searchFocusManager.clearFocus() }),
            singleLine = true,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = screenUiState.searchQuery,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = { Text("Tìm theo mã/tên nút...", color = secondaryTextColor, fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = secondaryTextColor)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = orangeColor,
                        unfocusedBorderColor = outlineColor,
                        focusedContainerColor = cardBgColor,
                        unfocusedContainerColor = cardBgColor
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = orangeColor,
                                unfocusedBorderColor = outlineColor,
                                focusedContainerColor = cardBgColor,
                                unfocusedContainerColor = cardBgColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                )
            }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Filter row 2: loại đối tượng + sắp xếp ───────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Tất cả", "Vị trí", "Tuyến").forEach { type ->
            val selected = screenUiState.objectTypeFilter == type
            FilterChip(
                selected = selected,
                onClick = {
                    onUpdateObjectTypeFilter(type)
                },
                label = { Text(type, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = orangeColor,
                    containerColor = cardBgColor,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = secondaryTextColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = outlineColor,
                    selectedBorderColor = orangeColor
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = {
                    onSetSortMenuExpanded(true)
                },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(screenUiState.sortOrder, fontSize = 11.sp, color = secondaryTextColor)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = screenUiState.sortMenuExpanded,
                onDismissRequest = {
                    onSetSortMenuExpanded(false)
                },
                modifier = Modifier.background(cardBgColor)
            ) {
                listOf("Mã A→Z", "Mã Z→A").forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = textColor, fontSize = 13.sp) },
                        onClick = {
                            onUpdateSortOrder(option)
                            onSetSortMenuExpanded(false)
                        }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (state.importedFiles.isNotEmpty()) {
        Text("Danh sách file tải lên:", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            lazyItemsIndexed(state.importedFiles, key = { _, file -> file.id }) { _, file ->
                val isDragged = draggedFile?.id == file.id
                val isSelected = selectedFile?.id == file.id
                val isCombineTarget = draggedFile != null && draggedFile?.id != file.id
                val canRepairGeometry = file.fileType.lowercase(java.util.Locale.US) in setOf("kml", "kmz", "geojson", "json")

                Column {
                    GlassmorphicCard(
                        modifier = Modifier
                            .width(200.dp)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                fileBounds = fileBounds + (file.id to bounds)
                            }
                            .pointerInput(file) {
                                detectTapGestures(
                                    onTap = {
                                        selectedFile = if (isSelected) null else file
                                    },
                                    onDoubleTap = {
                                        selectedFile = null
                                        val uri = Uri.fromFile(File(file.storedPath))
                                        val ft = file.fileType.lowercase(java.util.Locale.US)
                                        if (ft == "xlsx" || ft == "xls") {
                                            onLoadExcelPreview(uri, file.id)
                                        } else {
                                            onLoadNonExcelPreview(uri, file.id)
                                        }
                                    },
                                    onLongPress = {
                                        selectedFile = if (isSelected) null else file
                                    }
                                )
                            }
                            .pointerInput(file) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        selectedFile = file
                                        draggedFile = file
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    },
                                    onDragEnd = {
                                        val finalPos = fileBounds[file.id]?.center?.plus(dragOffset)
                                        draggedFile = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero

                                        if (finalPos != null) {
                                            val droppedOn = fileBounds.entries.find { (id, rect) ->
                                                id != file.id && rect.contains(finalPos)
                                            }?.key

                                            if (droppedOn != null) {
                                                val targetFile = state.importedFiles.find { it.id == droppedOn }
                                                if (targetFile != null) {
                                                    selectedFile = null
                                                    combineTarget = Pair(file, targetFile)
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        draggedFile = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    }
                                )
                            }
                            .offset {
                                if (isDragged) androidx.compose.ui.unit.IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt())
                                else androidx.compose.ui.unit.IntOffset.Zero
                            }
                            .border(
                                width = if (isSelected || isDragged) 2.dp else if (isCombineTarget) 1.dp else 0.dp,
                                color = when {
                                    isSelected || isDragged -> orangeColor
                                    isCombineTarget -> orangeColor.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (isSelected) orangeColor else orangeColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = file.fileName,
                                    color = if (isSelected) orangeColor else textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Action bar hiện khi file được chọn (long-press)
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.width(200.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (canRepairGeometry) {
                                IconButton(
                                    onClick = {
                                        selectedFile = null
                                        onRepairImportedGeometry(file)
                                    },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = outlineColor,
                                        contentColor = orangeColor
                                    )
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Nhập lại geometry", modifier = Modifier.size(16.dp))
                                }
                            }
                            // Nút Xóa
                            Button(
                                onClick = {
                                    selectedFile = null
                                    onDeleteImportedFile(file.id)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = dangerColor,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            // Clickable "Gộp file" dropdown
                            var showCombineMenu by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(outlineColor)
                                    .clickable { showCombineMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MergeType,
                                        contentDescription = null,
                                        tint = orangeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gộp file", fontSize = 11.sp, color = orangeColor)
                                }

                                DropdownMenu(
                                    expanded = showCombineMenu,
                                    onDismissRequest = { showCombineMenu = false },
                                    modifier = Modifier.background(cardBgColor)
                                ) {
                                    state.importedFiles.filter { it.id != file.id }.forEach { otherFile ->
                                        DropdownMenuItem(
                                            text = { Text(otherFile.fileName, color = textColor, fontSize = 12.sp) },
                                            onClick = {
                                                showCombineMenu = false
                                                selectedFile = null
                                                combineTarget = Pair(file, otherFile)
                                            }
                                        )
                                    }
                if (state.importedFiles.size <= 1) {
                                        DropdownMenuItem(
                                            text = { Text("Không có file khác để gộp", color = secondaryTextColor, fontSize = 12.sp) },
                                            onClick = { showCombineMenu = false },
                                            enabled = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    val dotColors = listOf(
        dangerColor,
        extendedColors.warning,
        extendedColors.mapAccent,
        MaterialTheme.colorScheme.primary,
        extendedColors.info
    )
    val allItems = remember(
        dataHubUiState.baseDisplayItems,
        screenUiState.contractorFilter,
        screenUiState.searchQuery,
        screenUiState.objectTypeFilter,
        screenUiState.sortOrder
    ) {
        val contractorMatch: (String) -> Boolean = { c ->
            screenUiState.contractorFilter == contractors.firstOrNull().orEmpty() ||
                screenUiState.contractorFilter.startsWith("Ch") ||
                c == screenUiState.contractorFilter
        }
        val codeMatch: (String) -> Boolean = { code ->
            screenUiState.searchQuery.isBlank() || code.contains(screenUiState.searchQuery, ignoreCase = true)
        }

        dataHubUiState.baseDisplayItems
            .asSequence()
            .filter { item ->
                contractorMatch(item.contractor) &&
                    codeMatch(item.code) &&
                    when {
                        screenUiState.objectTypeFilter.startsWith("V") -> !item.isRoute
                        screenUiState.objectTypeFilter.startsWith("Tuy") -> item.isRoute
                        else -> true
                    }
            }
            .toList()
            .let { list ->
                if (screenUiState.sortOrder.trim().endsWith("A")) {
                    list.sortedByDescending { it.code }
                } else {
                    list.sortedBy { it.code }
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = gridMinSize),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        itemsIndexed(allItems, key = { _, item -> item.id }) { index, item ->
            GlassmorphicCard(
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(item.id, item.node, item.route) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        item.node?.let(onOpenNodeOnMap)
                                            ?: item.route?.let(onOpenRouteOnMap)
                                    }
                                )
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColors[index % dotColors.size])
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (item.isRoute) "Tuyến ${item.code}" else "Nút giao NG-${item.code}",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Nhà thầu: ${item.contractor.ifBlank { "Không rõ" }}",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp
                                )
                                if (item.isRoute && item.route != null) {
                                    Text(
                                        text = "${item.route.startNodeCode} → ${item.route.endNodeCode}",
                                        color = secondaryTextColor,
                                        fontSize = 11.sp
                                    )
                                    item.routeDistanceText?.let { distText ->
                                        Text(
                                            text = "Chiều dài: $distText",
                                            color = orangeColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 80.dp)
                        ) {
                            Icon(
                                if (item.isRoute) Icons.Outlined.Route else Icons.Outlined.FormatListNumbered,
                                contentDescription = null,
                                tint = if (item.isRoute) extendedColors.info else dangerColor,
                                modifier = Modifier.size(24.dp)
                            )
                            val displayCode = item.node?.mapNumberLabel?.ifBlank { item.code } ?: item.code
                            Text(
                                text = displayCode,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (item.isRoute) {
                        Text("Tuyến kết nối", color = secondaryTextColor, fontSize = 12.sp)
                    } else {
                        val node = item.node!!
                        val materialLines = item.materialLines
                        var materialsExpanded by remember(item.id) { mutableStateOf(false) }

                        if (materialLines.isEmpty()) {
                            Text("Chưa có dữ liệu công việc/khối lượng công việc", color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            // Header row — tap to expand/collapse
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { materialsExpanded = !materialsExpanded }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Công việc / khối lượng công việc (${materialLines.size})",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (materialsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = if (materialsExpanded) "Thu gọn" else "Mở rộng",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (materialsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    materialLines.forEach { line ->
                                        val itemName = line.itemName.trim()
                                        val itemCount = line.plannedText.trim()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .pointerInput(node.id, node.latitude, node.longitude, itemName) {
                                                        detectTapGestures(
                                                            onDoubleTap = { onOpenNodeOnMap(node) }
                                                        )
                                                    }
                                            ) {
                                                Icon(Icons.Outlined.Info, contentDescription = null, tint = dangerColor, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = itemName, color = textColor, fontSize = 12.sp, maxLines = 1)
                                            }
                                            if (isDesignTab) {
                                                Text(text = itemCount, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val key = "${node.id}_$itemName"
                                                    val currentValue = state.workVolumeProgress[key] ?: ""
                                                    Box(
                                                        modifier = Modifier.border(1.dp, orangeColor, RoundedCornerShape(4.dp)).padding(vertical = 4.dp).width(36.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        val numFocusManager = LocalFocusManager.current
                                                        BasicTextField(
                                                            value = currentValue,
                                                            onValueChange = { newValue ->
                                                                if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                                                                    onUpdateWorkVolumeProgress(node.id, itemName, newValue)
                                                                }
                                                            },
                                                            textStyle = TextStyle(color = orangeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                            keyboardActions = KeyboardActions(onDone = { numFocusManager.clearFocus() }),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                    Text(text = " / $itemCount", color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(outlineColor))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                onLoadNotesAndTasks(item.code)
                                onShowNotesAndTasks(item.code)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, orangeColor),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = orangeColor, modifier = Modifier.size(14.dp))
                                Text("Ghi chú & CV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (screenUiState.showNotesAndTasksSheet && screenUiState.notesAndTasksObjectCode.isNotBlank()) {
        NotesAndTasksBottomSheet(
            objectCode = screenUiState.notesAndTasksObjectCode,
            notes = state.selectedObjectNotes,
            tasks = state.selectedObjectTasks,
            aiSummary = state.aiNoteSummary,
            aiSuggestions = state.aiTaskSuggestions,
            isAiLoading = state.isAiLoading,
            onDismiss = {
                onDismissNotesAndTasks()
            },
            onAddNote = onAddNote,
            onDeleteNote = onDeleteNote,
            onAddTask = onAddTask,
            onToggleTask = onToggleTaskStatus,
            onDeleteTask = onDeleteTask,
            onSummarize = onSummarizeNotes,
            onSuggest = onSuggestTasks
        )
    }
}

