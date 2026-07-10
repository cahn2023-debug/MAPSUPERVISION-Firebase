package com.mapsupervision.app.workspace

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.app.sync.FirebaseMediaUploadScheduler
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.OpsRecommendationPayload
import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.WorkspaceSnapshot
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.domain.repository.SyncBatchResult
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.domain.usecase.ObserveWorkspaceSnapshotUseCase
import com.mapsupervision.storage.importer.UserFileImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class FilteredMapUpdateReason { SNAPSHOT, FILTER, SEARCH }

internal fun resolveFilteredMapUpdateDelayMs(reason: FilteredMapUpdateReason): Long = when (reason) {
    FilteredMapUpdateReason.SNAPSHOT -> 0L
    FilteredMapUpdateReason.FILTER,
    FilteredMapUpdateReason.SEARCH -> 180L
}

internal fun shouldPublishFilteredMapData(
    previousNodes: List<GisNode>,
    previousRoutes: List<GisRoute>,
    nextNodes: List<GisNode>,
    nextRoutes: List<GisRoute>
): Boolean = previousNodes != nextNodes || previousRoutes != nextRoutes

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val activeProjectRepository: ActiveProjectRepository,
    internal val importedFileRepository: ImportedFileRepository,
    internal val progressRepository: ProgressRepository,
    internal val workVolumeProgressRepository: WorkVolumeProgressRepository,
    internal val projectRepository: ProjectRepository,
    internal val projectSyncRepository: ProjectSyncRepository,
    internal val gisRepository: GisRepository,
    internal val importService: UserFileImportService,
    internal val aiOrchestrator: AiOrchestrator,
    internal val photoRepository: PhotoRepository,
    internal val photoPipelineService: IPhotoPipelineService,
    internal val locationProvider: IPhotoLocationProvider,
    internal val dailyLogRepository: DailyLogRepository,
    internal val noteRepository: NoteRepository,
    internal val taskRepository: TaskRepository,
    internal val workCategoryRepository: WorkCategoryRepository,
    internal val workPlanRepository: com.mapsupervision.domain.repository.WorkPlanRepository,
    internal val weatherService: WeatherService,
    internal val reportDraftRepository: com.mapsupervision.domain.repository.ReportDraftRepository,
    internal val materialDeclarationRepository: MaterialDeclarationRepository,
    internal val materialHandoverRepository: MaterialHandoverRepository,
    internal val firebaseAccessRepository: FirebaseAccessRepository,
    internal val firebaseSyncRepository: FirebaseSyncRepository,
    private val firebaseMediaUploadScheduler: FirebaseMediaUploadScheduler,
    private val observeWorkspaceSnapshot: ObserveWorkspaceSnapshotUseCase,
    private val migrationService: com.mapsupervision.domain.service.ProjectStorageMigrationService
) : ViewModel() {

    internal val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<WorkspaceEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<WorkspaceEffect> = _effects.asSharedFlow()

    internal var mapSearchJob: Job? = null
    internal var aiOpsJob: Job? = null
    internal var filteredMapUpdateJob: Job? = null
    internal val workVolumeProgressPersistJobs = mutableMapOf<String, Job>()
    internal var cachedIndexes = WorkspaceIndexes()
    internal var cachedNodesRef: List<GisNode> = emptyList()
    internal var cachedRoutesRef: List<GisRoute> = emptyList()
    internal var cachedProgressRef: List<NodeProgress> = emptyList()
    internal var cachedWorkVolumeRowsRef: List<WorkVolumeProgress> = emptyList()
    internal var cachedDailyLogsRef: List<DailyLog> = emptyList()
    private var lastAiOpsInput: AiOpsInput? = null
    private var lastMigrationProjectId: String? = null
    private var lastRawSnapshot: WorkspaceSnapshot? = null
    internal var lastAutoUploadScheduledProjectId: String? = null
    internal val directCaptureSaveDeduper = DirectCaptureSaveDeduper()
    internal var firebaseSyncJob: Job? = null

    // Keep StateFlows for filtered nodes/routes to avoid filtering on every recomposition in the UI.
    private val _filteredNodesForMap = MutableStateFlow<List<GisNode>>(emptyList())
    val filteredNodesForMap: StateFlow<List<GisNode>> = _filteredNodesForMap.asStateFlow()

    private val _filteredRoutesForMap = MutableStateFlow<List<GisRoute>>(emptyList())
    val filteredRoutesForMap: StateFlow<List<GisRoute>> = _filteredRoutesForMap.asStateFlow()

    private val colorPrefs by lazy {
        context.getSharedPreferences("contractor_colors", Context.MODE_PRIVATE)
    }

    internal fun saveContractorColor(projectId: String, contractor: String, hexColor: String) {
        colorPrefs.edit().putString("${projectId}_$contractor", hexColor).apply()
    }

    internal fun loadContractorColors(projectId: String): Map<String, String> {
        val all = colorPrefs.all as? Map<*, *> ?: return emptyMap()
        val prefix = "${projectId}_"
        return all.entries.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? String ?: return@mapNotNull null
            if (key.startsWith(prefix)) {
                key.substring(prefix.length) to value
            } else {
                null
            }
        }.toMap()
    }

    internal val visibilityPrefs by lazy {
        context.getSharedPreferences("hidden_contractors", Context.MODE_PRIVATE)
    }

    internal fun saveContractorVisibility(projectId: String, contractor: String, isHidden: Boolean) {
        visibilityPrefs.edit().putBoolean("${projectId}_$contractor", isHidden).apply()
    }

    internal fun loadHiddenContractors(projectId: String): Set<String> {
        val all = visibilityPrefs.all as? Map<*, *> ?: return emptySet()
        val prefix = "${projectId}_"
        return all.entries.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? Boolean ?: return@mapNotNull null
            if (key.startsWith(prefix) && value) {
                key.substring(prefix.length)
            } else {
                null
            }
        }.toSet()
    }

    private val mapDisplayPrefs by lazy {
        context.getSharedPreferences("map_display_config", Context.MODE_PRIVATE)
    }

    internal fun saveMapDisplayConfig(projectId: String, nodeSize: Float, routeWidth: Float) {
        mapDisplayPrefs.edit()
            .putFloat("${projectId}_nodeSizeScale", nodeSize)
            .putFloat("${projectId}_routeWidthScale", routeWidth)
            .apply()
    }

    internal fun loadMapDisplayConfig(projectId: String): Pair<Float, Float> {
        val nodeSize = mapDisplayPrefs.getFloat("${projectId}_nodeSizeScale", 1.0f)
        val routeWidth = mapDisplayPrefs.getFloat("${projectId}_routeWidthScale", 1.0f)
        return Pair(nodeSize, routeWidth)
    }

    init {
        observeWorkspace()
        observeProjectSync()
        observeFirebaseAccess()
    }

    fun dispatch(action: WorkspaceAction) {
        when (action) {
            is WorkspaceAction.SelectTab -> selectTab(action.tab)
            is WorkspaceAction.UpdateLayoutMode -> {
                _uiState.value = _uiState.value.copy(layoutMode = action.mode)
            }
            is WorkspaceAction.ShowReportPreview -> {
                _uiState.value = _uiState.value.copy(
                    showReportPreview = true,
                    previewNodeCode = action.nodeCode
                )
            }
            WorkspaceAction.DismissReportPreview -> {
                _uiState.value = _uiState.value.copy(
                    showReportPreview = false,
                    previewNodeCode = null
                )
            }
            is WorkspaceAction.SetPendingSharedImport -> {
                _state.value = _state.value.copy(pendingSharedImport = action.pendingSharedImport)
            }
            is WorkspaceAction.UpdatePendingSharedImport -> {
                _state.value = _state.value.copy(pendingSharedImport = action.pendingSharedImport)
            }
            WorkspaceAction.ClearPendingSharedImport -> {
                _state.value = _state.value.copy(pendingSharedImport = null)
            }
            is WorkspaceAction.ShowMapConfigDialog -> {
                _state.value = _state.value.copy(
                    mapUi = _state.value.mapUi.copy(showConfigDialog = action.show)
                )
            }
            is WorkspaceAction.UpdateMapDisplayConfig -> {
                val current = _state.value
                val projectId = current.activeProjectId
                if (projectId != null) {
                    saveMapDisplayConfig(projectId, action.nodeSize, action.routeWidth)
                }
                _state.value = current.copy(
                    mapUi = current.mapUi.copy(
                        nodeSizeScale = action.nodeSize,
                        routeWidthScale = action.routeWidth
                    )
                )
            }
        }
    }

    fun selectTab(tab: WorkspaceTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == WorkspaceTab.MAP) {
            onEnterMapTab()
        }
    }

    fun onReportExported(path: String) {
        if (path.isBlank()) return
        _effects.tryEmit(WorkspaceEffect.OpenExportedFile(path))
    }

    fun showMessage(message: String) {
        if (message.isBlank()) return
        _effects.tryEmit(WorkspaceEffect.ShowMessage(message))
    }

    fun refresh() {
        val current = _state.value
        _state.value = current.copy(
            isRefreshing = true,
            lastRefreshedAtEpochMs = System.currentTimeMillis()
        )
        if (!current.activeProjectId.isNullOrBlank()) {
            syncFirebaseNow(current.activeProjectId, trigger = "refresh")
            return
        }
        requestAiOpsRefresh(force = true)
    }

    fun syncFirebaseNow(
        projectId: String? = _state.value.activeProjectId,
        trigger: String = "manual"
    ) {
        val resolvedProjectId = projectId?.takeIf { it.isNotBlank() } ?: return
        if (firebaseSyncJob?.isActive == true) {
            AppLogger.d("firebase.sync.skip projectId=$resolvedProjectId trigger=$trigger reason=already_running")
            return
        }

        firebaseSyncJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            updateFirebaseSyncState {
                it.copy(
                    isSyncing = true,
                    lastError = null,
                    lastTrigger = trigger
                )
            }

            val accessState = when (val refreshed = firebaseAccessRepository.refreshAccess()) {
                is AppResult.Success -> refreshed.data
                is AppResult.Error -> firebaseAccessRepository.accessState.value
            }
            val session = accessState.session
            val hasProjectAccess = session?.isAdmin == true ||
                accessState.allowedProjectIds.contains(resolvedProjectId)
            if (session == null || !hasProjectAccess) {
                val errorMessage = if (session == null) {
                    "Can dang nhap Firebase de dong bo."
                } else {
                    "Tai khoan chua duoc cap quyen cho du an nay."
                }
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    firebaseSync = _state.value.firebaseSync.copy(
                        isSyncing = false,
                        lastError = errorMessage,
                        lastTrigger = trigger
                    )
                )
                showMessage(errorMessage)
                return@launch
            }

            val pushResult = firebaseSyncRepository.pushPending(resolvedProjectId)
            val pullResult = firebaseSyncRepository.pullChanges(resolvedProjectId)
            val mergedState = mergeSyncResults(pushResult, pullResult).copy(
                isSyncing = false,
                lastSyncedAtEpochMs = startedAt,
                lastTrigger = trigger
            )

            _state.value = _state.value.copy(
                isRefreshing = false,
                firebaseSync = mergedState
            )

            if (mergedState.lastError.isNullOrBlank()) {
                requestAiOpsRefresh(force = true)
                if (trigger == "manual") {
                    showMessage(
                        "Firebase sync OK: push ${mergedState.pushed}, pull ${mergedState.pulled}, upload ${mergedState.uploadedMedia}"
                    )
                }
            } else {
                firebaseMediaUploadScheduler.enqueue("sync_failed:$trigger", resolvedProjectId)
                AppLogger.e(
                    IllegalStateException(mergedState.lastError),
                    "firebase.sync.failed projectId=$resolvedProjectId trigger=$trigger"
                )
                showMessage("Firebase sync loi: ${mergedState.lastError}")
            }
            if (mergedState.failed > 0) {
                firebaseMediaUploadScheduler.enqueue("sync_partial_failed:$trigger", resolvedProjectId)
            }
        }
    }

    private fun observeWorkspace() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId
                .flatMapLatest { projectId ->
                    if (projectId.isNullOrBlank()) {
                        lastMigrationProjectId = null
                        flowOf<WorkspaceSnapshot?>(null)
                    } else {
                        scheduleProjectMigration(projectId)
                        observeWorkspaceSnapshot(projectId)
                    }
                }
                .collectLatest { snapshot ->
                    if (snapshot == null) {
                        lastAutoUploadScheduledProjectId = null
                        _state.value = WorkspaceState(
                            importUi = ImportUiState(
                                status = ImportStatus.IDLE,
                                message = "Chưa chọn dự án active"
                            )
                        )
                        return@collectLatest
                    }
                    if (lastAutoUploadScheduledProjectId != snapshot.projectId) {
                        lastAutoUploadScheduledProjectId = snapshot.projectId
                        firebaseMediaUploadScheduler.enqueue("workspace_loaded:" + snapshot.projectId, snapshot.projectId)
                    }
                    applySnapshot(snapshot)
                }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.debounce(250).collectLatest { event ->
                val activeProjectId = _state.value.activeProjectId
                if (event.projectId != null && event.projectId != activeProjectId) return@collectLatest
                when (event.reason) {
                    "design_import_completed" -> showMessage("Đã cập nhật dữ liệu thiết kế")
                    "photo_saved" -> {
                        showMessage("Đã lưu ảnh hiện trường")
                        firebaseMediaUploadScheduler.enqueue("photo_saved", activeProjectId)
                        syncFirebaseNow(activeProjectId, trigger = "photo_saved")
                    }
                    "project_imported" -> showMessage("Đã nhập dự án")
                }
            }
        }
    }

    private fun scheduleProjectMigration(projectId: String) {
        if (lastMigrationProjectId == projectId) return
        lastMigrationProjectId = projectId
        viewModelScope.launch(Dispatchers.IO) {
            val projects = (projectRepository.list(true) as? com.mapsupervision.core.result.AppResult.Success)?.data.orEmpty()
            val activeProject = projects.find { it.id == projectId } ?: return@launch
            val migrationStatus = migrationService.migrateProjectIfNeeded(activeProject)
            if (migrationStatus.migrated || migrationStatus.verified) {
                projectSyncRepository.notifyProjectChanged(projectId, "project_migrated")
            }
        }
    }

    internal fun updateFilteredMapData(reason: FilteredMapUpdateReason = FilteredMapUpdateReason.SNAPSHOT) {
        val delayMs = resolveFilteredMapUpdateDelayMs(reason)
        filteredMapUpdateJob?.cancel()
        filteredMapUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                if (delayMs > 0L) delay(delayMs)
                val stateSnapshot = _state.value
                val indexes = ensureIndexes(stateSnapshot)
                val nodes = buildMapDesignNodes(stateSnapshot, indexes)
                val routes = filterRoutes(stateSnapshot.designRoutes, stateSnapshot.mapUi, indexes, nodes)
                if (!shouldPublishFilteredMapData(_filteredNodesForMap.value, _filteredRoutesForMap.value, nodes, routes)) {
                    return@launch
                }
                _filteredNodesForMap.value = nodes
                _filteredRoutesForMap.value = routes
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppLogger.e(error, "workspace.map.filter.failed query=${_state.value.mapUi.searchQuery}")
            }
        }
    }

    private fun observeFirebaseAccess() {
        viewModelScope.launch {
            firebaseAccessRepository.accessState
                .debounce(250)
                .collectLatest { accessState ->
                    val activeProjectId = _state.value.activeProjectId
                    val rawSnapshot = lastRawSnapshot
                    if (!activeProjectId.isNullOrBlank() &&
                        rawSnapshot != null &&
                        rawSnapshot.projectId == activeProjectId
                    ) {
                        applySnapshot(rawSnapshot, accessState)
                    }
                }
        }
    }

    private fun applySnapshot(
        snapshot: WorkspaceSnapshot,
        accessState: FirebaseAccessState = firebaseAccessRepository.accessState.value
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            lastRawSnapshot = snapshot
            val filteredSnapshot = snapshot.filterForAccess(accessState)
            val loadedWorkVolumeProgress = filteredSnapshot.workVolumeRows.associate { row ->
                "${row.nodeCode}_${row.workName}" to row.actualQty.toInt().toString()
            }
            val dashboard = buildDashboard(
                filteredSnapshot.designNodes,
                filteredSnapshot.designRoutes,
                filteredSnapshot.constructionProgress,
                filteredSnapshot.workVolumeRows
            )
            val coordSummary = summarizeNodeCoordinates(filteredSnapshot.designNodes)
            AppLogger.d("map.refresh nodes=${filteredSnapshot.designNodes.size} routes=${filteredSnapshot.designRoutes.size} project=${filteredSnapshot.projectId}")
            AppLogger.d(
                "map.refresh.coords project=${filteredSnapshot.projectId} nodesValid=${coordSummary.validCount} invalidNodes=${coordSummary.invalidCount} " +
                    "latRange=${coordSummary.latRangeText} lonRange=${coordSummary.lonRangeText}"
            )

            val current = _state.value
            val selectedNodeCode = current.mapUi.selectedNode?.code
            val nextSelectedPhotos = if (selectedNodeCode != null && current.selectedNodePhotos.isNotEmpty()) {
                filteredSnapshot.sitePhotos.filter { it.objectCode == selectedNodeCode }
            } else {
                current.selectedNodePhotos
            }

            val savedColors = loadContractorColors(filteredSnapshot.projectId)
            val savedHidden = loadHiddenContractors(filteredSnapshot.projectId)
            val (savedNodeSize, savedRouteWidth) = loadMapDisplayConfig(filteredSnapshot.projectId)

            val nextState = applyWorkspaceSnapshotToState(
                current = current,
                snapshot = filteredSnapshot,
                dashboard = dashboard,
                savedColors = savedColors,
                savedHidden = savedHidden,
                savedNodeSize = savedNodeSize,
                savedRouteWidth = savedRouteWidth,
                loadedWorkVolumeProgress = loadedWorkVolumeProgress,
                nextSelectedPhotos = nextSelectedPhotos,
                selectedMapUi = keepMapSelection(current.mapUi, snapshot.designNodes, snapshot.designRoutes),
                refreshedAtEpochMs = System.currentTimeMillis()
            )

            _state.value = nextState
            updateFilteredMapData()
            requestAiOpsRefresh()
        }
    }

    private fun keepMapSelection(mapUi: MapUiState, nodes: List<GisNode>, routes: List<GisRoute>): MapUiState {
        val selected = mapUi.selectedNode?.let { current ->
            nodes.firstOrNull { it.code == current.code }
        }
        val selectedRoute = mapUi.selectedRoute?.let { current ->
            routes.firstOrNull { it.code == current.code }
                ?: current.takeIf { route -> routes.any { isSameRouteSelection(route, it) } }
        }
        val centerNodeCode = mapUi.centerNodeCode?.takeIf { center ->
            nodes.any { it.code == center }
        }
        return mapUi.copy(
            selectedNode = selected,
            selectedRoute = selectedRoute,
            centerNodeCode = centerNodeCode,
            signalStatus = selected?.signalStatus ?: mapUi.signalStatus,
            centerPathSummary = selected?.let { node ->
                val normalizedCenter = centerNodeCode?.trim().orEmpty()
                when {
                    normalizedCenter.isBlank() -> ""
                    node.code.equals(normalizedCenter, ignoreCase = true) -> "Diem trung tam"
                    else -> mapUi.centerPathSummary
                }
            }.orEmpty()
        )
    }

    private suspend fun runAiOpsRecommendations(state: WorkspaceState) {
        val dashboard = state.dashboard
        val aiOps = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                aiOrchestrator.execute<com.mapsupervision.domain.ai.OpsRecommendationResult>(
                    OpsRecommendationPayload(
                        totalNodes = dashboard.totalDesignNodes,
                        delayedNodes = dashboard.delayedCount,
                        completionPercent = dashboard.completionPercent,
                        importWarnings = state.importUi.warnings.size
                    )
                )
            }.getOrNull()
        } ?: return

        _state.value = _state.value.copy(
            mapUi = _state.value.mapUi.copy(
                message = aiOps.result.prioritizedActions.firstOrNull().orEmpty()
            ),
            aiOpsActions = aiOps.result.prioritizedActions,
            aiOpsPriority = aiOps.result.priority
        )
    }

    private fun requestAiOpsRefresh(force: Boolean = false) {
        val stateSnapshot = _state.value
        val nextInput = AiOpsInput(
            totalNodes = stateSnapshot.dashboard.totalDesignNodes,
            delayedNodes = stateSnapshot.dashboard.delayedCount,
            completionPercent = stateSnapshot.dashboard.completionPercent,
            importWarnings = stateSnapshot.importUi.warnings.size
        )
        if (!force && nextInput == lastAiOpsInput) {
            _state.value = _state.value.copy(isRefreshing = false)
            return
        }
        lastAiOpsInput = nextInput
        aiOpsJob?.cancel()
        aiOpsJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(500)
            runAiOpsRecommendations(stateSnapshot)
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    private fun updateFirebaseSyncState(transform: (FirebaseSyncState) -> FirebaseSyncState) {
        _state.value = _state.value.copy(firebaseSync = transform(_state.value.firebaseSync))
    }

    private fun mergeSyncResults(
        pushResult: AppResult<SyncBatchResult>,
        pullResult: AppResult<SyncBatchResult>
    ): FirebaseSyncState {
        val pushData = (pushResult as? AppResult.Success)?.data
        val pullData = (pullResult as? AppResult.Success)?.data
        val pushError = (pushResult as? AppResult.Error)?.throwable?.message
        val pullError = (pullResult as? AppResult.Error)?.throwable?.message

        return FirebaseSyncState(
            pushed = pushData?.pushed ?: 0,
            pulled = pullData?.pulled ?: 0,
            uploadedMedia = pushData?.uploadedMedia ?: 0,
            failed = (pushData?.failed ?: 0) + (pullData?.failed ?: 0),
            lastError = listOfNotNull(pushError, pullError).takeIf { it.isNotEmpty() }?.joinToString(" | ")
        )
    }

    private fun isSameRouteSelection(selected: GisRoute, candidate: GisRoute): Boolean {
        if (candidate.code == selected.code) return true
        val selectedPrefix = routeSelectionPrefix(selected.code)
        val candidatePrefix = routeSelectionPrefix(candidate.code)
        return selectedPrefix.isNotBlank() && selectedPrefix == candidatePrefix
    }

    private fun routeSelectionPrefix(code: String): String {
        val markerIndex = if (code.contains("#pm")) code.lastIndexOf("_s") else code.lastIndexOf("_R")
        return if (markerIndex >= 0) code.substring(0, markerIndex) else code
    }

    internal suspend fun markProjectChanged(projectId: String, reason: String) {
        projectRepository.touch(projectId)
        projectSyncRepository.notifyProjectChanged(projectId, reason)
    }

    internal fun buildDashboard(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        progress: List<NodeProgress>,
        workVolumeRows: List<WorkVolumeProgress>
    ): DashboardState {
        return WorkspaceProgressHelper.buildDashboard(nodes, routes, progress, workVolumeRows)
    }
}

internal fun applyWorkspaceSnapshotToState(
    current: WorkspaceState,
    snapshot: WorkspaceSnapshot,
    dashboard: DashboardState,
    savedColors: Map<String, String>,
    savedHidden: Set<String>,
    savedNodeSize: Float = 1.0f,
    savedRouteWidth: Float = 1.0f,
    loadedWorkVolumeProgress: Map<String, String>,
    nextSelectedPhotos: List<SitePhoto>,
    selectedMapUi: MapUiState,
    refreshedAtEpochMs: Long
): WorkspaceState {
    return current.copy(
        activeProjectId = snapshot.projectId,
        importedFiles = snapshot.importedFiles,
        designNodes = snapshot.designNodes,
        designRoutes = snapshot.designRoutes,
        constructionProgress = snapshot.constructionProgress,
        dashboard = dashboard,
        mapUi = selectedMapUi.copy(
            contractorColors = savedColors,
            hiddenContractors = savedHidden,
            nodeSizeScale = savedNodeSize,
            routeWidthScale = savedRouteWidth
        ),
        workVolumeRows = snapshot.workVolumeRows,
        workVolumeProgress = loadedWorkVolumeProgress,
        dailyLogs = snapshot.dailyLogs,
        workCategories = snapshot.workCategories,
        materialHandovers = snapshot.materialHandovers,
        materialDeclarations = snapshot.materialDeclarations,
        workPlans = snapshot.workPlans,
        projectTasks = snapshot.projectTasks,
        selectedNodePhotos = nextSelectedPhotos,
        projectPhotos = snapshot.sitePhotos,
        isRefreshing = false,
        lastRefreshedAtEpochMs = refreshedAtEpochMs
    )
}

private data class AiOpsInput(
    val totalNodes: Int,
    val delayedNodes: Int,
    val completionPercent: Float,
    val importWarnings: Int
)

private data class WorkspaceCoordinateSummary(
    val validCount: Int,
    val invalidCount: Int,
    val latRangeText: String,
    val lonRangeText: String
)

private fun summarizeNodeCoordinates(nodes: List<GisNode>): WorkspaceCoordinateSummary {
    val validNodes = nodes.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    val invalidCount = nodes.size - validNodes.size
    if (validNodes.isEmpty()) {
        return WorkspaceCoordinateSummary(0, invalidCount, "n/a", "n/a")
    }
    return WorkspaceCoordinateSummary(
        validCount = validNodes.size,
        invalidCount = invalidCount,
        latRangeText = "%.5f..%.5f".format(validNodes.minOf { it.latitude }, validNodes.maxOf { it.latitude }),
        lonRangeText = "%.5f..%.5f".format(validNodes.minOf { it.longitude }, validNodes.maxOf { it.longitude })
    )
}

internal fun WorkspaceSnapshot.filterForAccess(accessState: FirebaseAccessState): WorkspaceSnapshot {
    val session = accessState.session ?: return this
    if (session.isAdmin) return this
    val hasResolvedProjectPermissions = accessState.allowedProjectIds.isNotEmpty() ||
        accessState.permissionsByProject.isNotEmpty()
    if (!hasResolvedProjectPermissions) {
        return this
    }
    val projectAccess = accessState.permissionsByProject[projectId] ?: return copy(
        designNodes = emptyList(),
        designRoutes = emptyList(),
        constructionProgress = emptyList(),
        workVolumeRows = emptyList(),
        materialHandovers = emptyList()
    )
    return filterForProjectAccess(projectAccess)
}

private fun WorkspaceSnapshot.filterForProjectAccess(projectAccess: ProjectAccess): WorkspaceSnapshot {
    if (projectAccess.contractorScope != ContractorScope.SCOPED || projectAccess.allowedContractors.isEmpty()) {
        return this
    }
    val allowed = projectAccess.allowedContractors
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    if (allowed.isEmpty()) {
        return copy(
            designNodes = emptyList(),
            designRoutes = emptyList(),
            constructionProgress = emptyList(),
            workVolumeRows = emptyList(),
            materialHandovers = emptyList()
        )
    }

    val allowedNodes = designNodes.filter { it.contractor.trim().lowercase() in allowed }
    val allowedRoutes = designRoutes.filter { it.contractor.trim().lowercase() in allowed }
    val allowedNodeCodes = allowedNodes.map { it.code.trim() }.toSet()
    val allowedRouteCodes = allowedRoutes.map { it.code.trim() }.toSet()

    return copy(
        designNodes = allowedNodes,
        designRoutes = allowedRoutes,
        constructionProgress = constructionProgress.filter { it.nodeCode in allowedNodeCodes },
        workVolumeRows = workVolumeRows.filter { it.nodeCode in allowedNodeCodes },
        materialHandovers = materialHandovers.filter { it.contractor.trim().lowercase() in allowed },
        workPlans = workPlans.filter { plan ->
            val nodeAllowed = plan.nodeCode == null || plan.nodeCode in allowedNodeCodes
            val routeAllowed = plan.routeCode == null || plan.routeCode in allowedRouteCodes
            nodeAllowed && routeAllowed
        }
    )
}

internal fun filterRoutes(
    routes: List<GisRoute>,
    mapUi: MapUiState,
    indexes: WorkspaceIndexes,
    liveNodes: List<GisNode>
): List<GisRoute> {
    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeMapSearchText(mapUi.searchQuery)
    val liveNodeCodesUpper = liveNodes.map { it.code.trim().uppercase() }.toSet()
    return routes.filter { route ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            route.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byVisibility = !isContractorHidden(mapUi, route.contractor)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedRouteSearch[route.code]?.matches(normalizedQuery) == true
        val byLiveNodes = routeHasRenderablePolyline(route) ||
            liveNodeCodesUpper.contains(route.startNodeCode.trim().uppercase()) ||
            liveNodeCodesUpper.contains(route.endNodeCode.trim().uppercase()) ||
            (mapUi.searchQuery.isNotBlank() && byQuery)
        byContractor && byVisibility && byQuery && byLiveNodes
    }
}

private fun routeHasRenderablePolyline(route: GisRoute): Boolean {
    var validPointCount = 0
    for ((latitude, longitude) in route.points) {
        val isValid = (latitude in -90.0..90.0 && longitude in -180.0..180.0) ||
            (longitude in -90.0..90.0 && latitude in -180.0..180.0)
        if (isValid) {
            validPointCount += 1
            if (validPointCount >= 2) return true
        }
    }
    return false
}

