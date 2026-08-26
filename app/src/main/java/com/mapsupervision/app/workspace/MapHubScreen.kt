package com.mapsupervision.app.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NodeSignalStatus
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Category
import androidx.compose.ui.text.style.TextDecoration
import android.graphics.Color as AndroidColor
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.GisScreen
import com.mapsupervision.gis.ui.MapLayerType
import com.mapsupervision.project.ui.ProjectUiState
import com.mapsupervision.domain.model.FirebaseUserSession
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*
import com.mapsupervision.gis.maplibre.MapBridgeInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHubScreen(
    designNodes: List<GisNode>,
    designRoutes: List<GisRoute>,
    mapUi: MapUiState,
    routeProperties: List<Pair<String, String>>,
    materialProgress: Map<String, String>,
    contractorOptions: List<String>,
    materialTypeOptions: List<String> = emptyList(),
    selectedNodeMaterialLines: List<PreparedMaterialLine>,
    showNumberOnMap: Boolean,
    colorByContractorOnMap: Boolean,
    projectState: ProjectUiState,
    onRefresh: () -> Unit,
    onRefreshProjects: () -> Unit,
    onCreateProject: (String) -> Unit,
    onSwitchProject: (String) -> Unit,
    onCloneProject: (String, String) -> Unit,
    onDeleteProject: (String, String, String, Boolean) -> Unit,
    onAcknowledgeRemoteDeletion: (String, Boolean) -> Unit = { _, _ -> },
    onDecideCloudDeletion: (String, Boolean) -> Unit = { _, _ -> },
    onForceDeleteLocalProject: (String) -> Unit = {},
    onSelectNode: (GisNode) -> Unit,
    onSelectRoute: (GisRoute) -> Unit,
    onSetCenterNode: (GisNode?) -> Unit,
    onUpdateNodeSignalStatus: (GisNode, NodeSignalStatus) -> Unit = { _, _ -> },
    onCloseNodeCard: () -> Unit,
    onCloseRouteCard: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    onMapBaseMapChanged: (MapLayerType) -> Unit,
    onToggleMeasure: () -> Unit,
    onLabelFieldChanged: (GisLabelField) -> Unit,
    onFilterContractorChanged: (String?) -> Unit,
    onFilterMaterialTypeChanged: (String?) -> Unit = {},
    onContractorColorChanged: (String, String) -> Unit,
    onToggleContractorVisibility: (String, Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onUpdateMaterialProgress: (String, String, String) -> Unit,
    onViewPhotos: () -> Unit,
    onCapturePicture: () -> Unit,
    onFileReport: (String) -> Unit,
    onAddRouteNote: (String) -> Unit,
    onMeasureDistance: (Double) -> Unit,
    onToggleConfigDialog: (Boolean) -> Unit = {},
    onUpdateMapDisplayConfig: (Float, Float) -> Unit = { _, _ -> },
    selectedNodePhotos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    onDismissPhotoPopup: () -> Unit = {},
    selectedObjectNotes: List<Note> = emptyList(),
    selectedObjectTasks: List<Task> = emptyList(),
    aiNoteSummary: String = "",
    aiTaskSuggestions: List<String> = emptyList(),
    isAiLoading: Boolean = false,
    onLoadNotesAndTasks: (String) -> Unit = {},
    onAddNote: (String, String) -> Unit = { _, _ -> },
    onDeleteNote: (String, String) -> Unit = { _, _ -> },
    onAddTask: (String, String) -> Unit = { _, _ -> },
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit = { _, _, _ -> },
    onDeleteTask: (String, String) -> Unit = { _, _ -> },
    onSummarizeNotes: (String) -> Unit = {},
    onSuggestTasks: (String) -> Unit = {},
    onExportProject: (com.mapsupervision.domain.model.Project) -> Unit = {},
    onImportProject: (Uri) -> Unit = {},
    onResolveDuplicateProject: (Uri, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onDismissDuplicateDialog: () -> Unit = {},
    onUpdateProjectStoragePath: (String, String) -> Unit = { _, _ -> },
    onUpdateProjectMediaStorage: (String, String) -> Unit = { _, _ -> },
    onRequestProjectAccess: (String) -> Unit = {},
    session: FirebaseUserSession,
    onSignOut: () -> Unit,
    firebaseSyncState: FirebaseSyncState,
    onSyncFirebase: () -> Unit
) {
    val context = LocalContext.current
    val defaultPalette = remember { listOf("#f97316", "#22c55e", "#06b6d4", "#a855f7", "#ef4444", "#f59e0b", "#3b82f6") }
    val extendedColorPalette = remember {
        listOf(
            "#f97316", "#22c55e", "#06b6d4", "#a855f7",
            "#ef4444", "#f59e0b", "#3b82f6", "#ec4899",
            "#14b8a6", "#84cc16", "#f43f5e", "#8b5cf6"
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val zipImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onImportProject(uri)
    }
    var projectName by remember { mutableStateOf("") }
    var deletionProject by remember { mutableStateOf<com.mapsupervision.domain.model.Project?>(null) }
    var deletionIdentity by remember { mutableStateOf("") }
    var deletionPassword by remember { mutableStateOf("") }
    var deletionPendingConfirmed by remember { mutableStateOf(false) }
    var alsoDeleteCloud by remember { mutableStateOf(false) }
    var showContractorMenu by remember { mutableStateOf(false) }
    var showMaterialMenu by remember { mutableStateOf(false) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showNotesAndTasksSheet by remember { mutableStateOf(false) }
    var notesAndTasksObjectCode by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showPhotoPopup by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedProjectForSettings by remember { mutableStateOf<com.mapsupervision.domain.model.Project?>(null) }
    var editedStoragePath by remember { mutableStateOf("") }
    var editedMediaStorageInput by remember { mutableStateOf("") }
    val storageFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val pickedPath = getPathFromTreeUri(uri)
            if (!pickedPath.isNullOrBlank()) {
                editedStoragePath = pickedPath
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val darkBgColor = colors.background
    val cardBgColor = extendedColors.panelBackgroundAlt
    val orangeColor = extendedColors.mapAccent
    val textColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val dangerColor = extendedColors.danger
    val dividerColor = colors.outlineVariant
    val surfaceColor = colors.surface
    val onSurfaceColor = colors.onSurface
    val onPrimaryColor = colors.onPrimary
    var dismissedCloudDecisionProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessingCloudDecision by remember { mutableStateOf(false) }
    val pendingCloudDecisionProject = projectState.projects.firstOrNull {
        it.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_DECISION_PENDING &&
            it.id != dismissedCloudDecisionProjectId
    }?.takeIf { project ->
        session.isAdmin || projectState.catalogItems.firstOrNull { it.projectId == project.id }?.let { it.isProjectAdmin || it.createdByUid == session.uid } == true
    }
    LaunchedEffect(pendingCloudDecisionProject?.id) {
        isProcessingCloudDecision = false
    }
    LaunchedEffect(context) {
        if (GisMapBridgeRegistry.bridge == null) {
            MapBridgeInstaller.install(context.applicationContext)
        }
    }
    LaunchedEffect(
        designNodes.size,
        designNodes.firstOrNull()?.id,
        designNodes.lastOrNull()?.id
    ) {
        if ((designNodes.isNotEmpty() || designRoutes.isNotEmpty()) &&
            mapUi.selectedNode == null &&
            mapUi.selectedRoute == null
        ) {
            GisMapBridgeRegistry.bridge?.fitToObjects()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = darkBgColor,
                drawerContentColor = textColor,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Quản lý dự án", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = orangeColor)

                    val focusManager = LocalFocusManager.current
                    androidx.compose.material3.OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Tên dự án mới", color = secondaryTextColor) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = orangeColor,
                            unfocusedBorderColor = secondaryTextColor,
                            cursorColor = orangeColor
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (projectName.isNotBlank()) {
                                    onCreateProject(projectName)
                                    projectName = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                        ) { Text("Tạo mới", fontWeight = FontWeight.Bold) }

                        OutlinedButton(
                            onClick = onRefreshProjects,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, secondaryTextColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                        ) { Text("Làm mới") }
                    }

                    OutlinedButton(
                        onClick = { zipImportLauncher.launch("application/zip") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, orangeColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeColor)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircle, 
                            contentDescription = null, 
                            modifier = Modifier.size(18.dp),
                            tint = orangeColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nhập dự án (.zip)", fontWeight = FontWeight.Bold)
                    }

                    if (projectState.importMessage.isNotBlank()) {
                        Text(
                            text = projectState.importMessage,
                            color = orangeColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Text("Danh sách dự án", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(projectState.projects, key = { project -> "${project.id}:${project.slug}" }) { p ->
                            val isActive = projectState.activeProjectId == p.id
                            val isRevoked = p.id in projectState.revokedReadOnlyProjectIds
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(p.id) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (!isActive) {
                                                    onSwitchProject(p.id)
                                                    scope.launch { drawerState.close() }
                                                } else {
                                                    scope.launch { drawerState.close() }
                                                }
                                            }
                                        )
                                    },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = when {
                                        isActive -> orangeColor
                                        isRevoked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                        else -> cardBgColor
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column {
                                        Text(
                                            p.name.ifBlank { p.slug },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isActive) onPrimaryColor else textColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Mã: ${p.slug}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isActive) colors.onPrimaryContainer else secondaryTextColor
                                        )
                                        if (p.deletionState != com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE) {
                                            Text(
                                                when (p.deletionState) {
                                                    com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_DECISION_PENDING -> "ĐÃ XÓA LOCAL — CHỜ QUYẾT ĐỊNH CLOUD"
                                                    com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_RETAINED -> "CLOUD ĐƯỢC GIỮ — ĐANG KHÔI PHỤC LOCAL"
                                                    com.mapsupervision.domain.model.ProjectDeletionState.RESTORE_PENDING -> "KHÔI PHỤC LOCAL ĐANG CHỜ RETRY"
                                                    com.mapsupervision.domain.model.ProjectDeletionState.LOCAL_DELETE_FAILED -> "XÓA LOCAL THẤT BẠI — CÓ THỂ RETRY"
                                                    com.mapsupervision.domain.model.ProjectDeletionState.DELETING -> "ĐANG XÓA — project bị khóa"
                                                    com.mapsupervision.domain.model.ProjectDeletionState.DELETE_FAILED -> "XÓA THẤT BẠI — có thể thử lại"
                                                    com.mapsupervision.domain.model.ProjectDeletionState.DELETED -> "ĐÃ XÓA TRÊN CLOUD — chỉ đọc"
                                                    else -> ""
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (p.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.DELETE_FAILED ||
                                                    p.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.LOCAL_DELETE_FAILED
                                                ) dangerColor else secondaryTextColor
                                            )
                                        }
                                    }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        val isCreator = projectState.catalogItems.firstOrNull { it.projectId == p.id }?.createdByUid == session.uid
                                        val isProjectAdmin = projectState.catalogItems.firstOrNull { it.projectId == p.id }?.isProjectAdmin == true
                                        val canDelete = session.isAdmin || isCreator || isProjectAdmin
                                        val deletionLocked = p.deletionState != com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE
                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .background(colors.primaryContainer, MaterialTheme.shapes.small)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("ĐANG HOẠT ĐỘNG", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (isRevoked) {
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("CHỈ ĐỌC (REVOKED)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text("KHÔNG HOẠT ĐỘNG", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (!isRevoked) {
                                                IconButton(
                                                    onClick = { onExportProject(p) },
                                                    enabled = !deletionLocked && p.deletionState != com.mapsupervision.domain.model.ProjectDeletionState.DELETE_FAILED
                                                ) {
                                                     Icon(
                                                         imageVector = Icons.Default.Share,
                                                         contentDescription = "Export Project",
                                                         tint = if (isActive) onPrimaryColor else secondaryTextColor
                                                     )
                                                }
                                                IconButton(
                                                    onClick = { onCloneProject(p.id, "${p.name} - Copy") },
                                                    enabled = p.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE
                                                ) {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Clone", tint = if (isActive) onPrimaryColor else secondaryTextColor)
                                                }
                                                IconButton(enabled = p.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE, onClick = {
                                                    selectedProjectForSettings = p
                                                    editedStoragePath = p.projectDbPath.substringBeforeLast("/db/")
                                                    editedMediaStorageInput = p.mediaStorageFolderUrl.ifBlank { p.mediaStorageFolderId }
                                                    showSettingsDialog = true
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Settings,
                                                        contentDescription = "Cài đặt dự án",
                                                        tint = if (isActive) onPrimaryColor else secondaryTextColor
                                                    )
                                                }
                                            }
                                            if (!isActive) {
                                                if (!isRevoked && canDelete && p.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE) {
                                                    IconButton(onClick = {
                                                        deletionProject = p
                                                        alsoDeleteCloud = false
                                                        deletionIdentity = ""
                                                        deletionPassword = ""
                                                        deletionPendingConfirmed = false
                                                    }) {
                                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = dangerColor)
                                                    }
                                                }
                                                Button(
                                                    onClick = {
                                                        onSwitchProject(p.id)
                                                        scope.launch { drawerState.close() }
                                                    },
                                                    enabled = !deletionLocked,
                                                    shape = MaterialTheme.shapes.small,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                                                ) { Text("Mở", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (projectState.catalogItems.isNotEmpty() || projectState.isCatalogLoading) {
                            item {
                                Spacer(modifier = Modifier.padding(top = 8.dp))
                                Text("Danh mục Firebase đám mây", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)

                                if (projectState.isCatalogLoading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                }
                            }

                            items(projectState.catalogItems, key = { "catalog_${it.projectId}" }) { catItem ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = if (catItem.isRevokedReadOnly) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else cardBgColor
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(catItem.projectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
                                                Text("Mã: ${catItem.projectCode}", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                            }
                                            val badgeText = when (catItem.accessStatus) {
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.APPROVED -> "ĐÃ DUYỆT"
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.PENDING -> "CHỜ DUYỆT"
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REJECTED -> "TỪ CHỐI"
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REVOKED -> "THU HỒI"
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.NOT_REQUESTED -> "CHƯA YÊU CẦU"
                                            }
                                            val badgeBg = when (catItem.accessStatus) {
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.APPROVED -> colors.primaryContainer
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.PENDING -> colors.tertiaryContainer
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REJECTED,
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REVOKED -> MaterialTheme.colorScheme.errorContainer
                                                else -> colors.surfaceVariant
                                            }
                                            val badgeColor = when (catItem.accessStatus) {
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.APPROVED -> colors.onPrimaryContainer
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.PENDING -> colors.onTertiaryContainer
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REJECTED,
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REVOKED -> MaterialTheme.colorScheme.onErrorContainer
                                                else -> colors.onSurfaceVariant
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(badgeBg, MaterialTheme.shapes.small)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            when (catItem.accessStatus) {
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.APPROVED -> {
                                                    if (catItem.isLocalAvailable) {
                                                        Button(
                                                            onClick = {
                                                                onSwitchProject(catItem.projectId)
                                                                scope.launch { drawerState.close() }
                                                            },
                                                            shape = MaterialTheme.shapes.small,
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                                                        ) { Text("Mở", style = MaterialTheme.typography.labelSmall) }
                                                    }
                                                }
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.PENDING -> {
                                                    OutlinedButton(onClick = {}, enabled = false, shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                                        Text("Đang chờ duyệt", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REJECTED,
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REVOKED,
                                                com.mapsupervision.domain.model.FirebaseAccessRequestStatus.NOT_REQUESTED -> {
                                                    val btnLabel = when (catItem.accessStatus) {
                                                        com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REJECTED -> "Gửi lại"
                                                        com.mapsupervision.domain.model.FirebaseAccessRequestStatus.REVOKED -> "Yêu cầu lại"
                                                        else -> "Yêu cầu quyền"
                                                    }
                                                    Button(
                                                        onClick = { onRequestProjectAccess(catItem.projectId) },
                                                        enabled = !catItem.isActionBusy,
                                                        shape = MaterialTheme.shapes.small,
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(if (catItem.isActionBusy) "..." else btnLabel, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Firebase Sync section
                    if (!projectState.activeProjectId.isNullOrBlank()) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = cardBgColor
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSyncFirebase() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (firebaseSyncState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = orangeColor
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.CloudSync,
                                            contentDescription = null,
                                            tint = orangeColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Đồng bộ đám mây",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor
                                        )
                                        val label = when {
                                            firebaseSyncState.isSyncing -> "Đang đồng bộ..."
                                            firebaseSyncState.lastError != null -> "Đồng bộ lỗi"
                                            firebaseSyncState.lastSyncedAtEpochMs > 0L -> "Đã đẩy ${firebaseSyncState.pushed}, tải ${firebaseSyncState.pulled}"
                                            else -> "Đồng bộ đám mây"
                                        }
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = secondaryTextColor
                                        )
                                    }
                                }
                                if (!firebaseSyncState.isSyncing) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Sync",
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // User session / login section
                    val identityLabel = remember(session.email, session.uid, session.isAdmin) {
                        val email = session.email.ifBlank { session.uid }
                        val localPart = email.substringBefore("@").ifBlank { email }
                        buildString {
                            append(if (localPart.length > 18) "${localPart.take(18)}..." else localPart)
                            append(if (session.isAdmin) " | Admin" else " | User")
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = cardBgColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = identityLabel,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                    Text(
                                        text = "Tài khoản đang đăng nhập",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, dangerColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(max = 32.dp)
                            ) {
                                Text("Đăng xuất", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        if (searchExpanded) {
                            val focusManager = LocalFocusManager.current
                            TextField(
                                value = mapUi.searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("Tìm mã hoặc số hiển thị") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MapSupervision", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            onRefreshProjects()
                            scope.launch { drawerState.open() }
                        }) { Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface) }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showContractorMenu = true }) {
                                Icon(
                                    Icons.Outlined.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (mapUi.filterContractor != null) orangeColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showContractorMenu,
                                onDismissRequest = { showContractorMenu = false }
                            ) {
                                // "Tất cả" row
                                val allSelected = mapUi.filterContractor == null
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(Color.Transparent, CircleShape)
                                                    .border(1.dp, if (allSelected) orangeColor else dividerColor, CircleShape)
                                            )
                                            Text(
                                                "Tất cả",
                                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (allSelected) orangeColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = { onFilterContractorChanged(null) }
                                )
                                contractorOptions.forEach { contractor ->
                                    val isSelected = mapUi.filterContractor == contractor
                                    val customHex = mapUi.contractorColors[contractor]
                                    val defaultHex = run {
                                        defaultPalette[Math.abs(contractor.hashCode()) % defaultPalette.size]
                                    }
                                    val hexColor = customHex ?: defaultHex
                                    val swatchColor = try {
                                        Color(AndroidColor.parseColor(hexColor))
                                    } catch (_: Exception) { orangeColor }

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Color swatch – tap to cycle color
                                                val colorPalette = extendedColorPalette
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(swatchColor, CircleShape)
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) colors.onSurface else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable(
                                                            indication = null,
                                                            interactionSource = remember { MutableInteractionSource() }
                                                        ) {
                                                            val currentIdx = colorPalette.indexOf(hexColor)
                                                            val nextHex = colorPalette[(currentIdx + 1) % colorPalette.size]
                                                            onContractorColorChanged(contractor, nextHex)
                                                        }
                                                )
                                                Text(
                                                    contractor,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) swatchColor else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(swatchColor, CircleShape)
                                                    )
                                                }

                                                val isHidden = mapUi.hiddenContractors.contains(contractor)

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable(
                                                        indication = null,
                                                        interactionSource = remember { MutableInteractionSource() }
                                                    ) {
                                                        onToggleContractorVisibility(contractor, false)
                                                    }
                                                ) {
                                                    Checkbox(
                                                        checked = !isHidden,
                                                        onCheckedChange = null,
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = orangeColor,
                                                            uncheckedColor = secondaryTextColor
                                                        ),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Hiện", style = MaterialTheme.typography.bodySmall, color = textColor)
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable(
                                                        indication = null,
                                                        interactionSource = remember { MutableInteractionSource() }
                                                    ) {
                                                        onToggleContractorVisibility(contractor, true)
                                                    }
                                                ) {
                                                    Checkbox(
                                                        checked = isHidden,
                                                        onCheckedChange = null,
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = orangeColor,
                                                            uncheckedColor = secondaryTextColor
                                                        ),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Ẩn", style = MaterialTheme.typography.bodySmall, color = textColor)
                                                }
                                            }
                                        },
                                        onClick = {
                                            onFilterContractorChanged(
                                                if (isSelected) null else contractor
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showMaterialMenu = true }) {
                                Icon(
                                    Icons.Outlined.Category,
                                    contentDescription = "Vật tư",
                                    tint = if (mapUi.filterMaterialType != null) orangeColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showMaterialMenu,
                                onDismissRequest = { showMaterialMenu = false }
                            ) {
                                val allSelected = mapUi.filterMaterialType == null
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(Color.Transparent, CircleShape)
                                                    .border(1.dp, if (allSelected) orangeColor else dividerColor, CircleShape)
                                            )
                                            Text(
                                                "Tất cả",
                                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (allSelected) orangeColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        onFilterMaterialTypeChanged(null)
                                        showMaterialMenu = false
                                    }
                                )
                                materialTypeOptions.forEach { materialType ->
                                    val isSelected = mapUi.filterMaterialType == materialType
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .background(if (isSelected) orangeColor else Color.Transparent, CircleShape)
                                                        .border(1.dp, if (isSelected) orangeColor else dividerColor, CircleShape)
                                                )
                                                Text(
                                                    materialType,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) orangeColor else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onFilterMaterialTypeChanged(
                                                if (isSelected) null else materialType
                                            )
                                            showMaterialMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            if (searchExpanded && mapUi.searchQuery.isNotBlank()) {
                                onRefresh()
                            }
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) {
                                onSearchQueryChanged("")
                            }
                        }) {
                            Icon(
                                if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    GisScreen(
                        nodes = designNodes,
                        routes = designRoutes,
                        showNumberLabels = showNumberOnMap,
                        colorByContractor = colorByContractorOnMap,
                        contractorColors = mapUi.contractorColors,
                        labelField = mapUi.labelField,
                        showNodes = mapUi.showNodes,
                        showRoutes = mapUi.showRoutes,
                        measureEnabled = mapUi.measureEnabled,
                        selectedNode = mapUi.selectedNode,
                        selectedRoute = mapUi.selectedRoute,
                        nodeSizeScale = mapUi.nodeSizeScale,
                        routeWidthScale = mapUi.routeWidthScale,
                        onNodeClick = onSelectNode,
                        onRouteClick = onSelectRoute,
                        onMeasureDistance = onMeasureDistance
                    )
                }
            }


            // Measure distance banner
            if (mapUi.measureEnabled) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp)
                        .widthIn(max = 300.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (mapUi.measureDistanceText.isNotBlank())
                            dangerColor else cardBgColor
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icons.Outlined.Straighten.let {
                            Icon(it, contentDescription = null, tint = onSurfaceColor, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = if (mapUi.measureDistanceText.isNotBlank())
                                "onMeasureDistance"
                            else "Chạm 2 điểm để đo",
                            color = onSurfaceColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Search result / message banner � only show during active search
            if (mapUi.message.isNotBlank() && !mapUi.measureEnabled && mapUi.searchQuery.isNotBlank()) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp)
                        .widthIn(max = 360.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = cardBgColor)
                ) {
                    Text(
                        text = mapUi.message,
                        color = onSurfaceColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 90.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ElevatedCard(shape = RoundedCornerShape(10.dp), colors = CardDefaults.elevatedCardColors(containerColor = surfaceColor)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.Add, contentDescription = "Zoom In", tint = onSurfaceColor) }
                        IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.Remove, contentDescription = "Zoom Out", tint = onSurfaceColor) }
                        IconButton(onClick = { GisMapBridgeRegistry.bridge?.fitToObjects() }) {
                            Icon(Icons.Outlined.ZoomOutMap, contentDescription = "Zoom Fit", tint = onSurfaceColor)
                        }
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(10.dp), colors = CardDefaults.elevatedCardColors(containerColor = surfaceColor)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onMyLocation) { Icon(Icons.Outlined.LocationSearching, contentDescription = "Location", tint = onSurfaceColor) }
                        Box {
                            IconButton(onClick = { showLayerMenu = true }) {
                                Icon(Icons.Outlined.Layers, contentDescription = "Layer", tint = onSurfaceColor)
                            }
                            DropdownMenu(expanded = showLayerMenu, onDismissRequest = { showLayerMenu = false }) {
                                DropdownMenuItem(text = { Text("Đường phố") }, onClick = { onMapBaseMapChanged(MapLayerType.STREET); showLayerMenu = false })
                                DropdownMenuItem(text = { Text("Vệ tinh") }, onClick = { onMapBaseMapChanged(MapLayerType.SATELLITE); showLayerMenu = false })
                                DropdownMenuItem(text = { Text("Vệ tinh + tên đường") }, onClick = { onMapBaseMapChanged(MapLayerType.SATELLITE_LABELS); showLayerMenu = false })
                                DropdownMenuItem(text = { Text("Nền tối") }, onClick = { onMapBaseMapChanged(MapLayerType.DARK); showLayerMenu = false })
                            }
                        }
                        IconButton(onClick = onToggleMeasure) {
                            Icon(
                                Icons.Outlined.Straighten,
                                contentDescription = "Measure",
                                tint = if (mapUi.measureEnabled) dangerColor else onSurfaceColor
                            )
                        }
                        IconButton(onClick = { onToggleConfigDialog(true) }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Cấu hình bản đồ",
                                tint = onSurfaceColor
                            )
                        }
                    }
                }
            }

            // Popup for selected route — floats over map with adaptive height and sticky controls
            val selectedRoute = mapUi.selectedRoute
            if (selectedRoute != null) {
                BackHandler { onCloseRouteCard() }
                // Transparent overlay — click outside the card to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onCloseRouteCard() }
                )
                val screenHeightDp = LocalConfiguration.current.screenHeightDp
                val routeMaxBodyHeight = (screenHeightDp * 0.52f).coerceIn(200f, 480f).dp

                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp, start = 10.dp, end = 10.dp)
                        .widthIn(max = 720.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume clicks inside card */ },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        // 1. STICKY HEADER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tuyến: ${selectedRoute.code}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = onCloseRouteCard, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. SCROLLABLE BODY
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = routeMaxBodyHeight)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val fiberProperties = routeProperties.filter { it.first == "Số core quang" || it.first == "Sợi kết nối" }
                            val generalProperties = routeProperties.filterNot { it.first == "Số core quang" || it.first == "Sợi kết nối" }
                            RouteInfoSection("Thông tin tuyến", generalProperties)
                            RouteFiberSection(fiberProperties)

                            // Note input
                            val focusManager = LocalFocusManager.current
                            Spacer(modifier = Modifier.padding(top = 2.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = mapUi.routeNote,
                                onValueChange = onAddRouteNote,
                                placeholder = { Text("Thêm ghi chú tuyến...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )
                        }

                        // 3. STICKY FOOTER
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onViewPhotos()
                                    showPhotoPopup = true
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            ) { Text("Xem ảnh", fontSize = 11.sp) }
                            Button(
                                onClick = onCapturePicture,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(13.dp))
                                    Text("Chụp ảnh", fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = { onFileReport(selectedRoute.code) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) { Text("Báo cáo", fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    notesAndTasksObjectCode = selectedRoute.code
                                    onLoadNotesAndTasks(selectedRoute.code)
                                    showNotesAndTasksSheet = true
                                },
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(13.dp))
                                    Text("Ghi chú & CV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            val selectedNode = mapUi.selectedNode
            if (selectedNode != null) {
                BackHandler { onCloseNodeCard() }
                // Transparent overlay — click outside the card to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onCloseNodeCard() }
                )
                val screenHeightDp = LocalConfiguration.current.screenHeightDp
                val cardMaxBodyHeight = (screenHeightDp * 0.58f).coerceIn(240f, 520f).dp

                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp, start = 10.dp, end = 10.dp)
                        .widthIn(max = 760.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume clicks inside card */ },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).fillMaxWidth()
                    ) {
                        // 1. STICKY HEADER (Fixed on top of card)
                        NodeIdentityHeader(
                            node = selectedNode,
                            mapUi = mapUi,
                            onSetCenterNode = onSetCenterNode,
                            onCloseNodeCard = onCloseNodeCard
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. SCROLLABLE BODY (Adaptive height)
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .heightIn(max = cardMaxBodyHeight)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NodeIdentityBody(node = selectedNode, mapUi = mapUi)

                            NodeNetworkSection(node = selectedNode, onUpdateNodeSignalStatus = onUpdateNodeSignalStatus)

                            NodeRoutingSection(
                                node = selectedNode,
                                isCenter = mapUi.centerNodeCode == selectedNode.code,
                                centerPathSummary = mapUi.centerPathSummary
                            )

                            // Only show completion/inspection row if data is meaningful
                            if (mapUi.expectedCompletion.isNotBlank() || mapUi.lastInspection.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    if (mapUi.expectedCompletion.isNotBlank()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("DỰ KIẾN HOÀN THÀNH", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(mapUi.expectedCompletion, fontSize = 13.sp, color = if (mapUi.status.contains("Chậm")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    if (mapUi.lastInspection.isNotBlank()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("KIỂM TRA GẦN NHẤT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(mapUi.lastInspection, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Vật tư / khối lượng thi công", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (selectedNodeMaterialLines.isNotEmpty()) {
                                    Text("${selectedNodeMaterialLines.size} hạng mục", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }

                            if (selectedNodeMaterialLines.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Không có dữ liệu vật tư / hạng mục thi công", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Nội dung", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("KL thiết kế", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                                        Text("KL thi công", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                                    selectedNodeMaterialLines.forEachIndexed { index, materialLine ->
                                        val itemName = materialLine.itemName
                                        val itemCount = materialLine.plannedText
                                        val currentValue = materialLine.actualText

                                        if (index > 0) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (index % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f) else Color.Transparent)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = itemName,
                                                modifier = Modifier.weight(0.5f),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 16.sp
                                            )
                                            Text(
                                                text = itemCount.ifBlank { "-" },
                                                modifier = Modifier.weight(0.25f),
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(0.25f)
                                                    .heightIn(min = 34.dp)
                                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val focusManager = LocalFocusManager.current
                                                BasicTextField(
                                                    value = currentValue,
                                                    onValueChange = { newValue ->
                                                        if (newValue.all { it.isDigit() || it == '.' || it == ',' } && newValue.length <= 8) {
                                                            onUpdateMaterialProgress(selectedNode.id, itemName, newValue)
                                                        }
                                                    },
                                                    textStyle = TextStyle(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. STICKY FOOTER (Fixed at bottom of card)
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onViewPhotos(); showPhotoPopup = true },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                            ) { Text("Xem ảnh", fontSize = 11.sp) }
                            Button(
                                onClick = onCapturePicture,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(13.dp))
                                    Text("Chụp ảnh", fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = { onFileReport(selectedNode.code) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                            ) { Text("Báo cáo", fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    notesAndTasksObjectCode = selectedNode.code
                                    onLoadNotesAndTasks(selectedNode.code)
                                    showNotesAndTasksSheet = true
                                },
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(13.dp))
                                    Text("Ghi chú & CV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showNotesAndTasksSheet && notesAndTasksObjectCode.isNotBlank()) {
            NotesAndTasksBottomSheet(
                objectCode = notesAndTasksObjectCode,
                notes = selectedObjectNotes,
                tasks = selectedObjectTasks,
                aiSummary = aiNoteSummary,
                aiSuggestions = aiTaskSuggestions,
                isAiLoading = isAiLoading,
                onDismiss = { showNotesAndTasksSheet = false },
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onAddTask = onAddTask,
                onToggleTask = onToggleTaskStatus,
                onDeleteTask = onDeleteTask,
                onSummarize = onSummarizeNotes,
                onSuggest = onSuggestTasks
            )
        }

        // Duplicate Project Resolution Dialog
        val duplicateProject = projectState.duplicateProjectToResolve
        val duplicateUri = projectState.duplicateZipUri
        if (duplicateProject != null && duplicateUri != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismissDuplicateDialog,
                title = { Text("Trùng lặp dự án", fontWeight = FontWeight.Bold, color = onSurfaceColor) },
                text = {
                    Text(
                        "Dự án với mã '${duplicateProject.slug}' và tên '${duplicateProject.name}' đã tồn tại trong hệ thống. " +
                                "Bạn muốn ghi đè lên dữ liệu cũ hay tạo một dự án mới làm bản sao?",
                        color = secondaryTextColor
                    )
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onResolveDuplicateProject(duplicateUri, false, true) },
                            border = BorderStroke(1.dp, orangeColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeColor)
                        ) {
                            Text("Tạo bản sao", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onResolveDuplicateProject(duplicateUri, true, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = onSurfaceColor)
                        ) {
                            Text("Ghi đè", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onDismissDuplicateDialog,
                        border = BorderStroke(1.dp, dividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor)
                    ) {
                        Text("Hủy")
                    }
                },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                modifier = Modifier
                    .fillMaxWidth(0.97f)
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showSettingsDialog && selectedProjectForSettings != null) {
            val project = selectedProjectForSettings!!
            val isCreator = session?.let { s -> projectState.catalogItems.firstOrNull { it.projectId == project.id }?.createdByUid == s.uid } == true
            val isProjectAdmin = projectState.catalogItems.firstOrNull { it.projectId == project.id }?.isProjectAdmin == true
            val canDelete = session?.isAdmin == true || isCreator || isProjectAdmin
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Cài đặt lưu trữ & dự án", fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Nhập đường dẫn thư mục lưu trữ cho dự án '${project.name}'. Hệ thống sẽ di chuyển toàn bộ cơ sở dữ liệu và file đa phương tiện của dự án này sang vị trí mới.",
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = editedMediaStorageInput,
                            onValueChange = { editedMediaStorageInput = it },
                            label = { Text("Thu muc Google Drive media", color = secondaryTextColor) },
                            placeholder = { Text("https://drive.google.com/drive/folders/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = orangeColor,
                                unfocusedBorderColor = secondaryTextColor,
                                cursorColor = orangeColor
                            )
                        )
                        Text(
                            "Share folder Google Drive cho service account voi quyen writer de dong bo media.",
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.OutlinedTextField(
                                value = editedStoragePath,
                                onValueChange = { editedStoragePath = it },
                                label = { Text("Đường dẫn lưu trữ", color = secondaryTextColor) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedBorderColor = orangeColor,
                                    unfocusedBorderColor = secondaryTextColor,
                                    cursorColor = orangeColor
                                )
                            )
                            OutlinedButton(
                                onClick = { storageFolderPickerLauncher.launch(null) },
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Chọn thư mục")
                            }
                        }
                        if (canDelete && project.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor.copy(alpha = 0.5f))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = dangerColor.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = dangerColor.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Vùng nguy hiểm (Danger Zone)",
                                    color = dangerColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "Xóa vĩnh viễn dữ liệu dự án trên thiết bị và Firebase Cloud. Hành động này không thể hoàn tác.",
                                    color = secondaryTextColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (project.id == projectState.activeProjectId) {
                                    Text(
                                        text = "⚠️ Dự án đang mở. Vui lòng chuyển sang dự án khác trước khi xóa.",
                                        color = orangeColor,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            showSettingsDialog = false
                                            deletionProject = project
                                            deletionIdentity = ""
                                            deletionPassword = ""
                                            deletionPendingConfirmed = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = onPrimaryColor),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Xóa dự án này", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editedStoragePath.isNotBlank()) {
                                onUpdateProjectStoragePath(project.id, editedStoragePath)
                            }
                            if (editedMediaStorageInput.isNotBlank()) {
                                onUpdateProjectMediaStorage(project.id, editedMediaStorageInput)
                            }
                            showSettingsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                    ) {
                        Text("Lưu", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSettingsDialog = false },
                        border = BorderStroke(1.dp, dividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor)
                    ) {
                        Text("Hủy")
                    }
                },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                modifier = Modifier
                    .fillMaxWidth(0.97f)
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        deletionProject?.let { project ->
            val targetIdentifier = project.slug.ifBlank { project.name }
            val isIdentityMatch = deletionIdentity.trim() == project.name || deletionIdentity.trim() == project.slug || deletionIdentity.trim() == project.id
            val isConfirmEnabled = if (alsoDeleteCloud) {
                isIdentityMatch && deletionPassword.isNotBlank()
            } else {
                true
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { deletionProject = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(dangerColor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
                                .border(1.dp, dangerColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = dangerColor, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = if (alsoDeleteCloud) "Xác nhận xóa vĩnh viễn" else "Xóa dự án khỏi máy",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = dangerColor
                            )
                            Text("Dự án: ${project.name}", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!alsoDeleteCloud) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(orangeColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .border(1.dp, orangeColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("ℹ️ Thông tin xóa cục bộ:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = orangeColor)
                                Text("• Dự án sẽ được dọn dẹp và xóa hoàn toàn khỏi bộ nhớ máy này để giải phóng dung lượng.", style = MaterialTheme.typography.bodySmall, color = textColor)
                                Text("• Dữ liệu trên Cloud (Firebase & Drive) vẫn được bảo toàn nguyên vẹn.", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                Text("• Bạn có thể tải lại và đồng bộ ngược lại máy bất cứ lúc nào từ danh mục Cloud.", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(dangerColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .border(1.dp, dangerColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("⚠️ Cảnh báo mất dữ liệu vĩnh viễn:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = dangerColor)
                                Text("• Dữ liệu GIS, công việc, nhật ký thực địa trên Firebase sẽ bị xóa vĩnh viễn.", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                Text("• Dữ liệu Catalog và phân quyền thành viên sẽ bị hủy bỏ.", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                                Text("• Dữ liệu ảnh thực địa trên Google Drive vẫn được giữ nguyên an toàn.", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                            }

                            Text("1. Nhập chính xác tên hoặc mã dự án ($targetIdentifier):", style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = deletionIdentity,
                                onValueChange = { deletionIdentity = it },
                                placeholder = { Text(targetIdentifier) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                isError = deletionIdentity.isNotBlank() && !isIdentityMatch,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("2. Mật khẩu tài khoản (xác thực lại quyền Admin):", style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = deletionPassword,
                                onValueChange = { deletionPassword = it },
                                placeholder = { Text("Nhập mật khẩu tài khoản...") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deletionPendingConfirmed = !deletionPendingConfirmed }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = deletionPendingConfirmed,
                                    onCheckedChange = { deletionPendingConfirmed = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tôi xác nhận chấp nhận hủy bỏ các thay đổi chưa đồng bộ (nếu có)", style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                            }
                        }

                        if (session.isAdmin) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { alsoDeleteCloud = !alsoDeleteCloud }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = alsoDeleteCloud,
                                    onCheckedChange = { alsoDeleteCloud = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đồng thời xóa vĩnh viễn dữ liệu trên Cloud (Quyền Admin)", style = MaterialTheme.typography.bodySmall, color = dangerColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (alsoDeleteCloud) {
                                onDeleteProject(project.id, deletionIdentity, deletionPassword, deletionPendingConfirmed)
                            } else {
                                onForceDeleteLocalProject(project.id)
                            }
                            deletionProject = null
                        },
                        enabled = isConfirmEnabled,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = onPrimaryColor)
                    ) {
                        Text(if (alsoDeleteCloud) "Xác nhận Xóa Cloud" else "Xác nhận Xóa khỏi máy", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { deletionProject = null },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, dividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor)
                    ) {
                        Text("Hủy")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        pendingCloudDecisionProject?.let { project ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    if (!isProcessingCloudDecision) {
                        dismissedCloudDecisionProjectId = project.id
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = "Cloud Sync",
                        tint = orangeColor,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Quyết định dữ liệu Cloud",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Dự án \"${project.name}\" đã được xóa khỏi bộ nhớ thiết bị này.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                        Text(
                            text = "Bạn có muốn giữ dữ liệu trên Cloud và khôi phục lại bản local, hay bắt đầu xóa vĩnh viễn dữ liệu Cloud? Media Google Drive vẫn được giữ nguyên an toàn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isProcessingCloudDecision = true
                            onDecideCloudDeletion(project.id, true)
                        },
                        enabled = !isProcessingCloudDecision,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = orangeColor,
                            contentColor = onPrimaryColor
                        )
                    ) {
                        Text("Giữ Cloud & Khôi phục", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                dismissedCloudDecisionProjectId = project.id
                            },
                            enabled = !isProcessingCloudDecision,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Để sau", color = secondaryTextColor)
                        }
                        OutlinedButton(
                            onClick = {
                                isProcessingCloudDecision = true
                                onDecideCloudDeletion(project.id, false)
                            },
                            enabled = !isProcessingCloudDecision,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, dangerColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)
                        ) {
                            Text("Xóa Cloud", color = dangerColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Photo viewer popup
        if (showPhotoPopup) {
            NodePhotoViewerDialog(
                photos = selectedNodePhotos,
                onDismiss = {
                    showPhotoPopup = false
                    onDismissPhotoPopup()
                }
            )
        }

        // Map configuration BottomSheet
        if (mapUi.showConfigDialog) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var localNodeScale by remember(mapUi.nodeSizeScale) { mutableStateOf(mapUi.nodeSizeScale) }
            var localRouteScale by remember(mapUi.routeWidthScale) { mutableStateOf(mapUi.routeWidthScale) }

            ModalBottomSheet(
                onDismissRequest = { onToggleConfigDialog(false) },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cấu hình hiển thị bản đồ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Node size slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kích thước các nút (Node)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1fx", localNodeScale),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = localNodeScale,
                            onValueChange = {
                                localNodeScale = it
                                onUpdateMapDisplayConfig(localNodeScale, localRouteScale)
                            },
                            valueRange = 0.5f..2.5f,
                            steps = 20
                        )
                    }

                    // Route thickness slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chiều dày đường vẽ (Route)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1fx", localRouteScale),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = localRouteScale,
                            onValueChange = {
                                localRouteScale = it
                                onUpdateMapDisplayConfig(localNodeScale, localRouteScale)
                            },
                            valueRange = 0.5f..2.5f,
                            steps = 20
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onToggleConfigDialog(false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Đóng", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun getPathFromTreeUri(uri: android.net.Uri): String? {
    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        val volumeId = split.firstOrNull().orEmpty()
        val relativePath = split.getOrNull(1).orEmpty()

        if (volumeId.equals("primary", ignoreCase = true)) {
            android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
        } else {
            "/storage/$volumeId/$relativePath"
        }
    } catch (_: Exception) {
        uri.path
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
}

@Composable
private fun NodeIdentityHeader(
    node: GisNode,
    mapUi: MapUiState,
    onSetCenterNode: (GisNode?) -> Unit,
    onCloseNodeCard: () -> Unit
) {
    val isCenter = mapUi.centerNodeCode == node.code
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Mã: ${node.code}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (node.mapNumberLabel.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Số hiệu: ${node.mapNumberLabel}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isCenter) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF97316), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Điểm trung tâm",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (mapUi.status.isNotBlank()) {
            Text(
                text = mapUi.status,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isCenter) "Bỏ trung tâm" else "Đặt trung tâm",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        if (isCenter) onSetCenterNode(null) else onSetCenterNode(node)
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            IconButton(onClick = onCloseNodeCard, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NodeIdentityBody(
    node: GisNode,
    mapUi: MapUiState
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (node.contractor.isNotBlank()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NHÀ THẦU", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(node.contractor, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("TỌA ĐỘ GPS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                    "%.6f, %.6f".format(node.latitude, node.longitude),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SignalStatusBadge(status: NodeSignalStatus) {
    val (label, color, textColor) = when (status) {
        NodeSignalStatus.HAS_SIGNAL -> Triple("Có tín hiệu", Color(0xFF22C55E), Color(0xFFFFFFFF))
        NodeSignalStatus.NO_SIGNAL -> Triple("Không tín hiệu", Color(0xFFEF4444), Color(0xFFFFFFFF))
        NodeSignalStatus.UNKNOWN -> Triple("Chưa rõ", Color(0xFF94A3B8), Color(0xFFFFFFFF))
    }
    Text(
        text = label,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun NodeNetworkSection(
    node: GisNode,
    onUpdateNodeSignalStatus: (GisNode, NodeSignalStatus) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isExpanded) 8.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Thu hẹp" else "Mở rộng",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "THÔNG TIN MẠNG",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isOnline = node.signalStatus == NodeSignalStatus.HAS_SIGNAL
                Text(
                    text = if (isOnline) "Trực tuyến" else "Ngoại tuyến",
                    color = if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.Switch(
                    checked = isOnline,
                    onCheckedChange = { checked ->
                        onUpdateNodeSignalStatus(
                            node,
                            if (checked) NodeSignalStatus.HAS_SIGNAL else NodeSignalStatus.NO_SIGNAL
                        )
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF22C55E),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NetworkInfoCell("IP", networkValue(node.ipAddress), Modifier.weight(1f))
                NetworkInfoCell("Subnet", networkValue(node.subnet), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            NetworkInfoCell("Gateway", networkValue(node.gateway), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NodeRoutingSection(
    node: GisNode,
    isCenter: Boolean,
    centerPathSummary: String
) {
    if (isCenter) return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text("ĐƯỜNG VỀ TRUNG TÂM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        val message = if (centerPathSummary.isNotBlank()) {
            shortenCenterPath(centerPathSummary)
        } else {
            "Chưa có đường kết nối về trung tâm"
        }
        Text(
            text = message,
            fontSize = 13.sp,
            color = if (centerPathSummary.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RouteFiberSection(properties: List<Pair<String, String>>) {
    if (properties.none { it.second.isNotBlank() }) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Thông tin tuyến quang", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        properties.forEach { (key, value) ->
            if (value.isNotBlank()) {
                val label = when (key) {
                    "Số core quang" -> "Số core quang"
                    "Sợi kết nối" -> "Sợi kết nối"
                    else -> key
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$label:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 132.dp)
                    )
                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun NetworkInfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

private fun networkValue(value: String): String =
    value.takeIf { it.isNotBlank() } ?: "Chưa cấu hình"

private fun shortenCenterPath(value: String): String {
    if (!value.startsWith("Đường về trung tâm:")) return value
    val prefix = "Đường về trung tâm: "
    val nodes = value.removePrefix(prefix).split(" -> ")
    if (nodes.size <= 5) return value
    return prefix + listOf(nodes.first(), nodes[1], "...", nodes[nodes.lastIndex - 1], nodes.last()).joinToString(" -> ")
}

@Composable
private fun RouteInfoSection(
    title: String,
    properties: List<Pair<String, String>>
) {
    if (properties.none { it.second.isNotBlank() }) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        properties.forEach { (key, value) ->
            if (value.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$key:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 132.dp)
                    )
                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
