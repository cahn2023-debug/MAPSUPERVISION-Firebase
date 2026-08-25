package com.mapsupervision.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mapsupervision.app.workspace.IncomingSharePayload
import com.mapsupervision.app.workspace.PendingSharedImport
import com.mapsupervision.app.workspace.MapHubScreen
import com.mapsupervision.app.workspace.ChatDictionaryResolver
import com.mapsupervision.app.workspace.MaterialsHubScreen
import com.mapsupervision.app.workspace.ShareImportSheet
import com.mapsupervision.app.workspace.WorkspaceAction
import com.mapsupervision.app.workspace.WorkspaceEffect
import com.mapsupervision.app.workspace.WorkspaceLayoutMode
import com.mapsupervision.app.workspace.WorkspaceTab
import com.mapsupervision.app.workspace.WorkspaceViewModel
import com.mapsupervision.app.workspace.GemmaChatViewModel
import com.mapsupervision.app.workspace.GemmaChatSheet
import com.mapsupervision.app.workspace.buildChatContextSummary
import com.mapsupervision.app.workspace.buildChatNormalizationSummary
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.app.workspace.DataHubRoute
import com.mapsupervision.app.workspace.ProgressHubRoute
import com.mapsupervision.app.workspace.addConstructionProgress
import com.mapsupervision.app.workspace.addDailyLog
import com.mapsupervision.app.workspace.addWorkPlanBatch
import com.mapsupervision.app.workspace.addNote
import com.mapsupervision.app.workspace.addTask
import com.mapsupervision.app.workspace.addWorkCategory
import com.mapsupervision.app.workspace.clearCaptureRequest
import com.mapsupervision.app.workspace.clearMapNodeSelection
import com.mapsupervision.app.workspace.clearMapRouteSelection
import com.mapsupervision.app.workspace.clearPhotoNodeFilter
import com.mapsupervision.app.workspace.clearSelectedNodePhotos
import com.mapsupervision.app.workspace.setMapCenterNode
import com.mapsupervision.app.workspace.combineImportedFiles
import com.mapsupervision.app.workspace.deleteImportedFile
import com.mapsupervision.app.workspace.deleteNote
import com.mapsupervision.app.workspace.deleteTask
import com.mapsupervision.app.workspace.ensureIndexes
import com.mapsupervision.app.workspace.fetchWeatherAuto
import com.mapsupervision.app.workspace.getDataHubUiState
import com.mapsupervision.app.workspace.getFilteredDesignNodesForMap
import com.mapsupervision.app.workspace.getFilteredDesignRoutes
import com.mapsupervision.app.workspace.getPreviewworkVolumeRows
import com.mapsupervision.app.workspace.getProgressUiState
import com.mapsupervision.app.workspace.getRouteProperties
import com.mapsupervision.app.workspace.getSelectedNodeMaterialLines
import com.mapsupervision.app.workspace.importDesignFiles
import com.mapsupervision.app.workspace.loadExcelPreview
import com.mapsupervision.app.workspace.loadNonExcelPreview
import com.mapsupervision.app.workspace.loadNotesAndTasks
import com.mapsupervision.app.workspace.loadPhotosForSelectedNode
import com.mapsupervision.app.workspace.importMediaFromGallery
import com.mapsupervision.app.workspace.onContractorColorChanged
import com.mapsupervision.app.workspace.onToggleContractorVisibility
import com.mapsupervision.app.workspace.onFilterContractorChanged
import com.mapsupervision.app.workspace.onFilterMaterialTypeChanged
import com.mapsupervision.app.workspace.onMapBaseMapChanged
import com.mapsupervision.app.workspace.onMapMyLocation
import com.mapsupervision.app.workspace.onMapToggleMeasure
import com.mapsupervision.app.workspace.onMapZoomIn
import com.mapsupervision.app.workspace.onMapZoomOut
import com.mapsupervision.app.workspace.onOpenPicker
import com.mapsupervision.app.workspace.onPickerEmpty
import com.mapsupervision.app.workspace.onSearchQueryChanged
import com.mapsupervision.app.workspace.parseExcelToDesign
import com.mapsupervision.app.workspace.parseNonExcelToDesign
import com.mapsupervision.app.workspace.repairImportedGeometry
import com.mapsupervision.app.workspace.retryFailedImports
import com.mapsupervision.app.workspace.savePhoto
import com.mapsupervision.app.workspace.selectMapNode
import com.mapsupervision.app.workspace.selectMapRoute
import com.mapsupervision.app.workspace.setExcelMappingDialogVisible
import com.mapsupervision.app.workspace.setImportMappingDialogVisible
import com.mapsupervision.app.workspace.suggestTasks
import com.mapsupervision.app.workspace.summarizeNotes
import com.mapsupervision.app.workspace.toggleTaskStatus
import com.mapsupervision.app.workspace.triggerCapture
import com.mapsupervision.app.workspace.updateExcelClassificationMode
import com.mapsupervision.app.workspace.updateExcelCoordinateMode
import com.mapsupervision.app.workspace.updateExcelMapping
import com.mapsupervision.app.workspace.updateExcelAllowPartialImport
import com.mapsupervision.app.workspace.toggleExcelConfirmedCustomColumn
import com.mapsupervision.app.workspace.updateExcelDuplicatePolicy
import com.mapsupervision.app.workspace.updateExcelDeduplicationKey
import com.mapsupervision.app.workspace.updateImportMappingUi
import com.mapsupervision.app.workspace.updateMapLabelField
import com.mapsupervision.app.workspace.updateMapVisualOptions
import com.mapsupervision.app.workspace.updateWorkVolumeProgress
import com.mapsupervision.app.workspace.updateMeasureDistance
import com.mapsupervision.app.workspace.updateNodeSignalStatus
import com.mapsupervision.app.workspace.updateRouteNote
import com.mapsupervision.app.workspace.updateSelectedExcelSheet
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.project.ui.ProjectViewModel
import com.mapsupervision.reporting.ui.ReportPreviewDialog
import com.mapsupervision.reporting.ui.ReportingScreen
import com.mapsupervision.reporting.ui.ReportingViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

private data class ShellDestination(
    val tab: WorkspaceTab,
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val shellDestinations = listOf(
    ShellDestination(WorkspaceTab.MAP, "map", "Bản đồ", Icons.Outlined.Map),
    ShellDestination(WorkspaceTab.PROGRESS, "progress", "Tiến độ", Icons.Outlined.Assessment),
    ShellDestination(WorkspaceTab.DATA, "data", "Nhập liệu", Icons.Outlined.CameraAlt),
    ShellDestination(WorkspaceTab.MATERIALS, "materials", "Vật tư", Icons.Outlined.Description),
    ShellDestination(WorkspaceTab.REPORTS, "reports", "Báo cáo", Icons.Outlined.Description)
)

@Composable
fun WorkspaceAppShell(
    photoPipelineService: IPhotoPipelineService,
    locationProvider: IPhotoLocationProvider,
    incomingSharePayload: IncomingSharePayload? = null,
    onIncomingShareConsumed: () -> Unit = {},
    session: FirebaseUserSession,
    onSignOut: () -> Unit
) {
    val workspaceViewModel: WorkspaceViewModel = hiltViewModel()
    val projectViewModel: ProjectViewModel = hiltViewModel()

    val workspaceState by workspaceViewModel.state.collectAsStateWithLifecycle()
    val workspaceUiState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val projectState by projectViewModel.uiState.collectAsStateWithLifecycle()
    val mapDesignNodes by workspaceViewModel.filteredNodesForMap.collectAsStateWithLifecycle()
    val mapDesignRoutes by workspaceViewModel.filteredRoutesForMap.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 840
    var chatSessionActive by rememberSaveable { mutableStateOf(false) }
    var isChatVisible by rememberSaveable { mutableStateOf(false) }
    var chatOpenRequestId by rememberSaveable { mutableStateOf(0) }
    var reportingSessionActive by rememberSaveable { mutableStateOf(false) }
    val chatContextSummary = remember(
        workspaceState.activeProjectId,
        workspaceState.dashboard.totalDesignNodes,
        workspaceState.dashboard.totalDesignRoutes,
        workspaceState.dashboard.completionPercent,
        workspaceState.dashboard.delayedCount,
        workspaceState.dashboard.materialCompletionPercent,
        workspaceState.mapUi.selectedNode?.code,
        workspaceState.mapUi.selectedRoute?.code,
        workspaceState.selectedNodePhotos.size,
        workspaceState.importUi.warnings,
        workspaceState.constructionProgress,
        workspaceState.dailyLogs
    ) {
        buildChatContextSummary(workspaceState)
    }
    val chatNormalizationSummary = remember(
        workspaceState.activeProjectId,
        workspaceState.mapUi.selectedNode?.code,
        workspaceState.mapUi.selectedRoute?.code,
        workspaceState.designNodes,
        workspaceState.designRoutes,
        workspaceState.workCategories
    ) {
        buildChatNormalizationSummary(workspaceState)
    }
    val chatDictionaryResolver: ChatDictionaryResolver = remember(
        workspaceState.activeProjectId,
        workspaceState.designNodes,
        workspaceState.designRoutes,
        workspaceState.workCategories
    ) {
        ChatDictionaryResolver.from(workspaceState)
    }

    LaunchedEffect(isExpanded) {
        workspaceViewModel.dispatch(
            WorkspaceAction.UpdateLayoutMode(
                if (isExpanded) WorkspaceLayoutMode.EXPANDED else WorkspaceLayoutMode.COMPACT
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    LaunchedEffect(workspaceUiState.selectedTab, currentDestination?.route) {
        val currentRoute = currentDestination?.route ?: return@LaunchedEffect
        val targetRoute = shellDestinations.first { it.tab == workspaceUiState.selectedTab }.route
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
                restoreState = true
                popUpTo("map") {
                    saveState = true
                }
            }
        }
    }

    LaunchedEffect(incomingSharePayload?.id) {
        val payload = incomingSharePayload ?: return@LaunchedEffect
        workspaceViewModel.dispatch(
            WorkspaceAction.SetPendingSharedImport(
                PendingSharedImport(payload = payload)
            )
        )
        onIncomingShareConsumed()
    }

    LaunchedEffect(Unit) {
        workspaceViewModel.effects.collectLatest { effect ->
            when (effect) {
                is WorkspaceEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is WorkspaceEffect.OpenExportedFile -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Đã xuất báo cáo: ${File(effect.path).name}",
                        actionLabel = "Mở"
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        openExportedFile(context, effect.path)
                    }
                }
            }
        }
    }

    LaunchedEffect(workspaceUiState.showReportPreview, workspaceUiState.selectedTab) {
        if (workspaceUiState.showReportPreview || workspaceUiState.selectedTab == WorkspaceTab.REPORTS) {
            reportingSessionActive = true
        }
    }

    val navChrome: @Composable () -> Unit = {
        val theme = MaterialTheme.colorScheme
        val glassBg = theme.surface.copy(alpha = 0.75f)
        val borderBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
        )
        val itemColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
            indicatorColor = theme.primaryContainer.copy(alpha = 0.35f),
            selectedIconColor = Color(0xFFFFB074),
            selectedTextColor = Color(0xFFFFB074),
            unselectedIconColor = theme.onSurfaceVariant.copy(alpha = 0.6f),
            unselectedTextColor = theme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        val railItemColors = androidx.compose.material3.NavigationRailItemDefaults.colors(
            indicatorColor = theme.primaryContainer.copy(alpha = 0.35f),
            selectedIconColor = Color(0xFFFFB074),
            selectedTextColor = Color(0xFFFFB074),
            unselectedIconColor = theme.onSurfaceVariant.copy(alpha = 0.6f),
            unselectedTextColor = theme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        if (workspaceUiState.layoutMode == WorkspaceLayoutMode.EXPANDED) {
            NavigationRail(
                containerColor = glassBg,
                modifier = Modifier.background(glassBg).padding(end = 1.dp).background(borderBrush)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                shellDestinations.forEach { destination ->
                    NavigationRailItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { workspaceViewModel.dispatch(WorkspaceAction.SelectTab(destination.tab)) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = railItemColors
                    )
                }
            }
        } else {
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .background(glassBg)
                    .background(borderBrush)
            ) {
                shellDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { workspaceViewModel.dispatch(WorkspaceAction.SelectTab(destination.tab)) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = itemColors
                    )
                }
            }
        }
    }

    val hostContent: @Composable () -> Unit = {
        NavHost(
            navController = navController,
            startDestination = shellDestinations.first().route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable("map") {
                MapHubScreen(
                    designNodes = mapDesignNodes,
                    designRoutes = mapDesignRoutes,
                    mapUi = workspaceState.mapUi,
                    routeProperties = workspaceState.mapUi.selectedRoute?.let { workspaceViewModel.getRouteProperties(it) }.orEmpty(),
                    materialProgress = workspaceState.workVolumeProgress,
                    contractorOptions = workspaceViewModel.getDataHubUiState().contractorOptions.filter { it != "Tất cả" },
                    materialTypeOptions = workspaceViewModel.ensureIndexes().materialTypeOptions,
                    selectedNodeMaterialLines = workspaceViewModel.getSelectedNodeMaterialLines(),
                    showNumberOnMap = workspaceState.excelParserUi.showNumberOnMap,
                    colorByContractorOnMap = workspaceState.excelParserUi.colorByContractorOnMap,
                    projectState = projectState,
                    onRefresh = workspaceViewModel::refresh,
                    onRefreshProjects = projectViewModel::refresh,
                    onSelectNode = workspaceViewModel::selectMapNode,
                    onSelectRoute = workspaceViewModel::selectMapRoute,
                    onSetCenterNode = workspaceViewModel::setMapCenterNode,
                    onUpdateNodeSignalStatus = workspaceViewModel::updateNodeSignalStatus,
                    onUpdateMaterialProgress = workspaceViewModel::updateWorkVolumeProgress,
                    onCloseNodeCard = workspaceViewModel::clearMapNodeSelection,
                    onCloseRouteCard = workspaceViewModel::clearMapRouteSelection,
                    onZoomIn = workspaceViewModel::onMapZoomIn,
                    onZoomOut = workspaceViewModel::onMapZoomOut,
                    onMyLocation = workspaceViewModel::onMapMyLocation,
                    onMapBaseMapChanged = workspaceViewModel::onMapBaseMapChanged,
                    onToggleMeasure = workspaceViewModel::onMapToggleMeasure,
                    onLabelFieldChanged = workspaceViewModel::updateMapLabelField,
                    onFilterContractorChanged = workspaceViewModel::onFilterContractorChanged,
                    onFilterMaterialTypeChanged = workspaceViewModel::onFilterMaterialTypeChanged,
                    onContractorColorChanged = workspaceViewModel::onContractorColorChanged,
                    onToggleContractorVisibility = workspaceViewModel::onToggleContractorVisibility,
                    onSearchQueryChanged = workspaceViewModel::onSearchQueryChanged,
                    onViewPhotos = workspaceViewModel::loadPhotosForSelectedNode,
                    onCapturePicture = workspaceViewModel::triggerCapture,
                    onFileReport = { nodeCode ->
                        workspaceViewModel.dispatch(WorkspaceAction.ShowReportPreview(nodeCode))
                    },
                    onAddRouteNote = workspaceViewModel::updateRouteNote,
                    onMeasureDistance = workspaceViewModel::updateMeasureDistance,
                    onToggleConfigDialog = { show ->
                        workspaceViewModel.dispatch(WorkspaceAction.ShowMapConfigDialog(show))
                    },
                    onUpdateMapDisplayConfig = { nodeSize, routeWidth ->
                        workspaceViewModel.dispatch(WorkspaceAction.UpdateMapDisplayConfig(nodeSize, routeWidth))
                    },
                    onCreateProject = projectViewModel::createProject,
                    onSwitchProject = projectViewModel::switchProject,
                    onCloneProject = projectViewModel::cloneProject,
                    onDeleteProject = { projectId, identity, password, confirmPending ->
                        projectViewModel.requestPermanentDeletion(projectId, identity, password, confirmPending)
                    },
                    onAcknowledgeRemoteDeletion = projectViewModel::acknowledgeRemoteDeletion,
                    onDecideCloudDeletion = projectViewModel::decideCloudDeletion,
                    onForceDeleteLocalProject = projectViewModel::forceDeleteLocalProject,
                    onExportProject = { project -> projectViewModel.exportProject(context, project) },
                    onImportProject = { uri -> projectViewModel.importProject(context, uri) },
                    onResolveDuplicateProject = { uri, overwrite, createCopy ->
                        projectViewModel.importProject(context, uri, overwrite, createCopy)
                    },
                    onDismissDuplicateDialog = projectViewModel::dismissDuplicateDialog,
                    onUpdateProjectStoragePath = projectViewModel::updateProjectStoragePath,
                    onUpdateProjectMediaStorage = projectViewModel::updateProjectMediaStorage,
                    onRequestProjectAccess = projectViewModel::requestAccess,
                    selectedNodePhotos = workspaceState.selectedNodePhotos,
                    onDismissPhotoPopup = workspaceViewModel::clearSelectedNodePhotos,
                    selectedObjectNotes = workspaceState.selectedObjectNotes,
                    selectedObjectTasks = workspaceState.selectedObjectTasks,
                    aiNoteSummary = workspaceState.aiNoteSummary,
                    aiTaskSuggestions = workspaceState.aiTaskSuggestions,
                    isAiLoading = workspaceState.isAiLoading,
                    onLoadNotesAndTasks = workspaceViewModel::loadNotesAndTasks,
                    onAddNote = workspaceViewModel::addNote,
                    onDeleteNote = workspaceViewModel::deleteNote,
                    onAddTask = workspaceViewModel::addTask,
                    onToggleTaskStatus = workspaceViewModel::toggleTaskStatus,
                    onDeleteTask = workspaceViewModel::deleteTask,
                    onSummarizeNotes = workspaceViewModel::summarizeNotes,
                    onSuggestTasks = workspaceViewModel::suggestTasks,
                    session = session,
                    onSignOut = onSignOut,
                    firebaseSyncState = workspaceState.firebaseSync,
                    onSyncFirebase = { workspaceViewModel.syncFirebaseNow(trigger = "manual") }
                )
            }
            composable("progress") {
                ProgressHubRoute(
                    activeProjectId = workspaceState.activeProjectId,
                    constructionProgress = workspaceState.constructionProgress,
                    dailyLogs = workspaceState.dailyLogs,
                    dashboardState = workspaceState.dashboard,
                    progressUiState = workspaceViewModel.getProgressUiState(),
                    workCategories = workspaceState.workCategories,
                    workPlans = workspaceState.workPlans,
                    projectTasks = workspaceState.projectTasks,
                    photos = workspaceState.projectPhotos,
                    activeProjectName = projectState.projects.firstOrNull { it.id == projectState.activeProjectId }?.name,
                    onAddConstruction = workspaceViewModel::addConstructionProgress,
                    onAddDailyLog = { request -> workspaceViewModel.addDailyLog(request) },
                    onAddWorkCategory = workspaceViewModel::addWorkCategory,
                    onAddWorkPlanBatch = workspaceViewModel::addWorkPlanBatch,
                    onFetchWeatherAuto = { nodeCode, routeCode, onResult -> workspaceViewModel.fetchWeatherAuto(nodeCode, routeCode, onResult) }
                )
            }
            composable("data") {
                DataHubRoute(
                    state = workspaceState,
                    dataHubUiState = workspaceViewModel.getDataHubUiState(),
                    onOpenPicker = workspaceViewModel::onOpenPicker,
                    onPickerEmpty = workspaceViewModel::onPickerEmpty,
                    onUploadDesign = workspaceViewModel::importDesignFiles,
                    onLoadNonExcelPreview = { uri, existingFileId -> workspaceViewModel.loadNonExcelPreview(uri, existingFileId) },
                    onUpdateImportMappingUi = workspaceViewModel::updateImportMappingUi,
                    onSetImportMappingDialogVisible = workspaceViewModel::setImportMappingDialogVisible,
                    onParseNonExcelToDesign = workspaceViewModel::parseNonExcelToDesign,
                    onRetryFailedImports = workspaceViewModel::retryFailedImports,
                    onLoadExcelPreview = { uri, existingFileId -> workspaceViewModel.loadExcelPreview(uri, existingFileId) },
                    onUpdateExcelMapping = workspaceViewModel::updateExcelMapping,
                    onUpdateExcelClassificationMode = workspaceViewModel::updateExcelClassificationMode,
                    onSetExcelMappingDialogVisible = workspaceViewModel::setExcelMappingDialogVisible,
                    onUpdateExcelCoordinateMode = workspaceViewModel::updateExcelCoordinateMode,
                    onUpdateMapVisualOptions = workspaceViewModel::updateMapVisualOptions,
                    onParseExcelToDesign = workspaceViewModel::parseExcelToDesign,
                    onAddConstruction = workspaceViewModel::addConstructionProgress,
                    onUpdateWorkVolumeProgress = workspaceViewModel::updateWorkVolumeProgress,
                    onOpenNodeOnMap = { node ->
                        workspaceViewModel.dispatch(WorkspaceAction.SelectTab(WorkspaceTab.MAP))
                        workspaceViewModel.selectMapNode(node)
                    },
                    onOpenRouteOnMap = { route ->
                        workspaceViewModel.dispatch(WorkspaceAction.SelectTab(WorkspaceTab.MAP))
                        workspaceViewModel.selectMapRoute(route)
                    },
                    onDeleteImportedFile = workspaceViewModel::deleteImportedFile,
                    onRepairImportedGeometry = workspaceViewModel::repairImportedGeometry,
                    photoFilterNodeCode = workspaceState.photoFilterNodeCode,
                    onClearPhotoFilter = workspaceViewModel::clearPhotoNodeFilter,
                    onLoadNotesAndTasks = workspaceViewModel::loadNotesAndTasks,
                    onAddNote = workspaceViewModel::addNote,
                    onDeleteNote = workspaceViewModel::deleteNote,
                    onAddTask = workspaceViewModel::addTask,
                    onToggleTaskStatus = workspaceViewModel::toggleTaskStatus,
                    onDeleteTask = workspaceViewModel::deleteTask,
                    onSummarizeNotes = workspaceViewModel::summarizeNotes,
                    onSuggestTasks = workspaceViewModel::suggestTasks,
                    onCombineFiles = workspaceViewModel::combineImportedFiles,
                    onUpdateSelectedExcelSheet = workspaceViewModel::updateSelectedExcelSheet,
                     onToggleAllowPartialImport = workspaceViewModel::updateExcelAllowPartialImport,
                     onToggleConfirmedCustomColumn = workspaceViewModel::toggleExcelConfirmedCustomColumn,
                     onUpdateExcelDuplicatePolicy = workspaceViewModel::updateExcelDuplicatePolicy,
                     onUpdateExcelDeduplicationKey = workspaceViewModel::updateExcelDeduplicationKey,
                     onRefresh = workspaceViewModel::refresh
                )
            }
            composable("reports") {
                ReportingScreen(
                    activeProjectId = projectState.activeProjectId,
                    photoSaveCount = workspaceState.photoSaveCount,
                    workVolumeProgress = workspaceState.workVolumeProgress
                )
            }
            composable("materials") {
                MaterialsHubScreen(
                    state = workspaceState,
                    viewModel = workspaceViewModel,
                    onRefresh = workspaceViewModel::refresh
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (workspaceUiState.layoutMode == WorkspaceLayoutMode.EXPANDED) {
            Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                navChrome()
                Box(modifier = Modifier.weight(1f)) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            hostContent()
                        }
                    }
                }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = navChrome,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    hostContent()
                }
            }
        }

        FloatingChatBubble(
            onClick = {
                chatSessionActive = true
                isChatVisible = true
                chatOpenRequestId += 1
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 86.dp)
        )
    }

    val pendingCapture = workspaceState.pendingCaptureNodeCode
    if (pendingCapture != null) {
        DisposableEffect(pendingCapture) {
            AppLogger.d(
                "camera.overlay.mount pendingCapture=$pendingCapture projectId=${workspaceState.activeProjectId}"
            )
            onDispose {
                AppLogger.d(
                    "camera.overlay.unmount pendingCapture=$pendingCapture projectId=${workspaceState.activeProjectId}"
                )
            }
        }
        val activeId = workspaceState.activeProjectId ?: ""
        val activeProject = projectState.projects.firstOrNull { it.id == activeId }
        val activeSlug = activeProject?.slug ?: activeId
        val minimapScope = remember(pendingCapture, workspaceState.designNodes, workspaceState.designRoutes) {
            com.mapsupervision.app.workspace.buildCaptureMinimapScope(
                targetCode = pendingCapture,
                nodes = workspaceState.designNodes,
                routes = workspaceState.designRoutes
            )
        }
        CameraOverlay(
            nodeCode = pendingCapture,
            projectId = activeId,
            projectSlug = activeSlug,
            photoPipelineService = photoPipelineService,
            locationProvider = locationProvider,
            onPhotoCaptured = workspaceViewModel::refresh,
            onSavePhoto = { file, statusTag, note, address ->
                workspaceViewModel.savePhoto(file, pendingCapture, statusTag, note, address)
            },
            onDismiss = workspaceViewModel::clearCaptureRequest,
            nodes = minimapScope.nodes,
            routes = minimapScope.routes
        )
    }

    if (chatSessionActive) {
        ChatSheetHost(
            isVisible = isChatVisible,
            openRequestId = chatOpenRequestId,
            workspaceViewModel = workspaceViewModel,
            workspaceState = workspaceState,
            workspaceUiState = workspaceUiState,
            chatContextSummary = chatContextSummary,
            chatNormalizationSummary = chatNormalizationSummary,
            chatDictionaryResolver = chatDictionaryResolver,
            onDismiss = { isChatVisible = false }
        )
    }

    if (reportingSessionActive) {
        ReportPreviewHost(
            showDialog = workspaceUiState.showReportPreview,
            projectId = projectState.activeProjectId,
            workspaceViewModel = workspaceViewModel,
            onDismiss = { workspaceViewModel.dispatch(WorkspaceAction.DismissReportPreview) }
        )
    }

    workspaceState.pendingSharedImport?.let { pendingSharedImport ->
        ShareImportSheet(
            pendingSharedImport = pendingSharedImport,
            projects = projectState.projects,
            activeProjectId = workspaceState.activeProjectId,
            workspaceViewModel = workspaceViewModel,
            projectViewModel = projectViewModel,
            onDismiss = { workspaceViewModel.dispatch(WorkspaceAction.ClearPendingSharedImport) }
        )
    }
}

@Composable
private fun ChatSheetHost(
    isVisible: Boolean,
    openRequestId: Int,
    workspaceViewModel: WorkspaceViewModel,
    workspaceState: com.mapsupervision.app.workspace.WorkspaceState,
    workspaceUiState: com.mapsupervision.app.workspace.WorkspaceUiState,
    chatContextSummary: String,
    chatNormalizationSummary: String,
    chatDictionaryResolver: ChatDictionaryResolver,
    onDismiss: () -> Unit
) {
    val chatViewModel: GemmaChatViewModel = hiltViewModel()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(openRequestId) {
        if (isVisible) {
            chatViewModel.open(workspaceState.activeProjectId)
        }
    }

    LaunchedEffect(workspaceState.activeProjectId) {
        if (isVisible && chatState.isOpen) {
            chatViewModel.open(workspaceState.activeProjectId)
        }
    }

    if (!isVisible && !chatState.isOpen) {
        return
    }

    GemmaChatSheet(
        state = chatState,
        contextSummary = chatContextSummary,
        projectId = workspaceState.activeProjectId,
        currentTab = workspaceUiState.selectedTab.name,
        selectedNodeCode = workspaceState.mapUi.selectedNode?.code,
        selectedRouteCode = workspaceState.mapUi.selectedRoute?.code,
        onDismiss = {
            chatViewModel.close()
            onDismiss()
        },
        onDownload = chatViewModel::downloadSelectedModel,
        onConfirmCellularDownload = chatViewModel::confirmCellularDownload,
        onDismissCellularWarning = chatViewModel::dismissCellularWarning,
        onDeleteModel = chatViewModel::deleteModel,
        onCancelDownload = chatViewModel::cancelDownload,
        onOpenModelPicker = chatViewModel::openModelPicker,
        onDismissModelPicker = chatViewModel::dismissModelPicker,
        onSelectModel = chatViewModel::selectModel,
        onInputChange = chatViewModel::updateInput,
        onConfirmPendingAction = { chatViewModel.confirmPendingAction(workspaceViewModel) },
        onDismissPendingAction = chatViewModel::dismissPendingAction,
        onUpdatePendingDailyLogDraft = chatViewModel::updatePendingDailyLogDraft,
        onUpdatePendingWorkPlanDraft = chatViewModel::updatePendingWorkPlanDraft,
        onSelectClarificationOption = { option ->
            val selectedNodeCode = workspaceState.mapUi.selectedNode?.code
            val selectedRouteCode = workspaceState.mapUi.selectedRoute?.code
            val inputHints = chatDictionaryResolver.buildInputHints(
                chatState.input,
                selectedNodeCode,
                selectedRouteCode
            )
            chatViewModel.selectClarificationOption(
                option = option,
                normalizationContext = if (inputHints.isBlank()) chatNormalizationSummary else "$chatNormalizationSummary\n$inputHints",
                selectedNodeCode = selectedNodeCode,
                selectedRouteCode = selectedRouteCode
            )
        },
        onClearHistory = chatViewModel::clearChatHistory,
        onReloadHistory = chatViewModel::reloadHistory,
        onSend = {
            val selectedNodeCode = workspaceState.mapUi.selectedNode?.code
            val selectedRouteCode = workspaceState.mapUi.selectedRoute?.code
            val canonicalUserMessage = chatDictionaryResolver.canonicalizeMessage(
                chatState.input,
                selectedNodeCode,
                selectedRouteCode
            )
            val inputHints = chatDictionaryResolver.buildInputHints(
                chatState.input,
                selectedNodeCode,
                selectedRouteCode
            )
            chatViewModel.sendMessage(
                contextSummary = chatContextSummary,
                normalizationContext = if (inputHints.isBlank()) chatNormalizationSummary else "$chatNormalizationSummary\n$inputHints",
                canonicalUserMessage = canonicalUserMessage,
                projectId = workspaceState.activeProjectId,
                tab = workspaceUiState.selectedTab.name,
                selectedNodeCode = selectedNodeCode,
                selectedRouteCode = selectedRouteCode
            )
        }
    )
}

@Composable
private fun ReportPreviewHost(
    showDialog: Boolean,
    projectId: String?,
    workspaceViewModel: WorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val reportingViewModel: ReportingViewModel = hiltViewModel()
    val reportingSnapshot by reportingViewModel.reportSnapshot.collectAsStateWithLifecycle()
    val lastPdfPath by reportingViewModel.lastReportPath.collectAsStateWithLifecycle()
    val lastWordPath by reportingViewModel.lastWordReportPath.collectAsStateWithLifecycle()
    val reportExporting by reportingViewModel.isExporting.collectAsStateWithLifecycle()

    LaunchedEffect(showDialog, projectId) {
        if (showDialog && !projectId.isNullOrBlank()) {
            reportingViewModel.requestReportDraft(projectId)
        } else {
            reportingViewModel.cancelReportDraft()
        }
    }

    LaunchedEffect(lastPdfPath) {
        if (!lastPdfPath.isNullOrBlank()) {
            workspaceViewModel.onReportExported(lastPdfPath!!)
        }
    }

    LaunchedEffect(lastWordPath) {
        if (!lastWordPath.isNullOrBlank()) {
            workspaceViewModel.onReportExported(lastWordPath!!)
        }
    }

    ReportPreviewDialog(
        showDialog = showDialog,
        onDismiss = onDismiss,
        projectId = projectId ?: "",
        selectedExportFormat = "PDF",
        isExporting = reportExporting,
        onUpdatePhotoOffset = reportingViewModel::updatePhotoOffset,
        onConfirmExport = { format ->
            if (format == "PDF") {
                reportingViewModel.exportPdf()
            } else {
                reportingViewModel.exportWord()
            }
            onDismiss()
        },
        nodes = reportingSnapshot.nodes,
        routes = reportingSnapshot.routes,
        photos = reportingSnapshot.photos,
        workVolumeRows = reportingSnapshot.workVolumeRows,
        aiDraft = reportingSnapshot.aiDraft
    )
}

private fun openExportedFile(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val type = when {
        file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        file.name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Mở báo cáo"))
}

@Composable
private fun BoxScope.FloatingChatBubble(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .size(56.dp)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFB074), // Neon Peach
                        Color(0xFFFF8F00)  // Neon Orange
                    )
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = "Chatbot AI",
            tint = Color(0xFF3D1F00), // OnPrimary dark contrast
            modifier = Modifier.size(28.dp)
        )
    }
}
