package com.mapsupervision.reporting.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.ReportDraftPayload
import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.ReportWorkspaceSnapshot
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.usecase.GenerateReportUseCase
import com.mapsupervision.reporting.docx.DocxReportGenerator
import com.mapsupervision.reporting.pdf.PdfReportGenerator
import com.mapsupervision.storage.ProjectPackageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ReportingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeProjectRepository: ActiveProjectRepository,
    private val photoRepository: PhotoRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val aiFacade: AIFacade,
    private val generateReportUseCase: GenerateReportUseCase,
    private val pdfReportGenerator: PdfReportGenerator,
    private val docxReportGenerator: DocxReportGenerator,
    private val projectPackageService: ProjectPackageService,
    private val projectRepository: ProjectRepository,
    private val projectSyncRepository: ProjectSyncRepository
) : ViewModel() {
    private val _lastReportPath = MutableStateFlow<String?>(null)
    val lastReportPath: StateFlow<String?> = _lastReportPath.asStateFlow()

    private val _lastWordReportPath = MutableStateFlow<String?>(null)
    val lastWordReportPath: StateFlow<String?> = _lastWordReportPath.asStateFlow()

    private val _lastPackagePath = MutableStateFlow<String?>(null)
    val lastPackagePath: StateFlow<String?> = _lastPackagePath.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _reportSnapshot = MutableStateFlow(ReportingSnapshot.Empty)
    val reportSnapshot: StateFlow<ReportingSnapshot> = _reportSnapshot.asStateFlow()

    private var refreshJob: Job? = null
    private var aiReportJob: Job? = null
    private var lastDraftRequest: ReportDraftRequest? = null

    init {
        observeActiveProject()
        observeProjectSync()
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refreshReportData()
            }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest { event ->
                val activeProjectId = activeProjectRepository.activeProjectId.value
                if (event.projectId == null || event.projectId == activeProjectId) {
                    refreshReportData()
                }
            }
        }
    }

    fun refreshReportData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
            if (projectId.isNullOrBlank()) {
                clearReportingState()
                return@launch
            }

        val snapshot = loadReportingSnapshot(projectId)
        replaceReportingSnapshot(snapshot)
    }
    }

    fun requestReportDraft(projectId: String, filterNodeCode: String? = null) {
        val currentSnapshot = reportSnapshot.value
        val currentRequest = ReportDraftRequest(
            projectId = projectId,
            snapshotVersion = if (currentSnapshot.isForProject(projectId)) currentSnapshot.version else -1L
        )
        if (lastDraftRequest == currentRequest && (currentSnapshot.aiDraft != null || aiReportJob?.isActive == true)) {
            return
        }

        aiReportJob?.cancel()
        lastDraftRequest = currentRequest
        aiReportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = ensureReportingSnapshot(projectId)
                val request = ReportDraftRequest(projectId, snapshot.version)
                if (snapshot.aiDraft != null && lastDraftRequest == request) {
                    return@launch
                }
                val draft = generateAiReportDraft(snapshot, null)
                lastDraftRequest = request
                _reportSnapshot.value = _reportSnapshot.value.withDraft(draft)
            } catch (e: Exception) {
                AppLogger.e(e, "Failed to generate AI report draft")
            }
        }
    }

    fun cancelReportDraft() {
        aiReportJob?.cancel()
        aiReportJob = null
    }

    fun updatePhotoOffset(photo: SitePhoto, offsetMinutes: Int) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val offsetMs = offsetMinutes.toLong() * 60_000L
            val updated = photo.copy(
                matchingTimeOffsetMs = offsetMs,
                matchedAtEpochMs = photo.capturedAtEpochMs + offsetMs
            )
            viewModelScope.launch(Dispatchers.IO) {
                photoRepository.add(updated)
            }
            if (photo.projectId == projectId) {
                val current = _reportSnapshot.value
                if (current.isForProject(projectId)) {
                    _reportSnapshot.value = current.copy(
                        photos = current.photos.map { if (it.id == photo.id) updated else it }
                    )
                }
            }
        }
    }

    private fun launchExport(action: suspend () -> Unit) {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportPdf(filterContractor: String? = null) {
        launchExport {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launchExport
            val snapshot = ensureReportingSnapshot(projectId)
            val activeDraft = resolveActiveDraft(snapshot, null)
            val exportContent = buildReportExportContent(snapshot, filterContractor, activeDraft)
            val file = pdfReportGenerator.exportProjectSummary(
                context,
                exportContent.targetId,
                exportContent.lines,
                exportContent.workVolumeRows,
                exportContent.photos,
                exportContent.dailyLogLines
            )
            _lastReportPath.value = file.absolutePath
        }
    }

    fun exportWord(filterContractor: String? = null) {
        launchExport {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launchExport
            val snapshot = ensureReportingSnapshot(projectId)
            val activeDraft = resolveActiveDraft(snapshot, null)
            val exportContent = buildReportExportContent(snapshot, filterContractor, activeDraft)
            val filteredDailyLogs = snapshot.dailyLogs
            val file = docxReportGenerator.exportProjectSummary(
                context,
                exportContent.targetId,
                exportContent.lines,
                exportContent.workVolumeRows,
                exportContent.photos,
                filteredDailyLogs
            )
            _lastWordReportPath.value = file.absolutePath
        }
    }

    fun exportPackageZip() {
        launchExport {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launchExport
            val projects = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
            val slug = projects.firstOrNull { it.id == projectId }?.slug ?: projectId
            val zip = projectPackageService.exportProjectZip(slug)
            _lastPackagePath.value = zip.absolutePath
        }
    }

    fun clearExportPaths() {
        _lastReportPath.value = null
        _lastWordReportPath.value = null
        _lastPackagePath.value = null
    }

    override fun onCleared() {
        refreshJob?.cancel()
        aiReportJob?.cancel()
        super.onCleared()
    }

    private suspend fun loadReportingSnapshot(projectId: String): ReportingSnapshot = coroutineScope {
        val reportSnapshot = generateReportUseCase(projectId) ?: return@coroutineScope ReportingSnapshot.Empty
        val workspace = reportSnapshot.workspace
        val photos = workspace.sitePhotos
        val progress = workspace.constructionProgress
        val nodes = workspace.designNodes
        val routes = workspace.designRoutes
        val dailyLogs = workspace.dailyLogs
        val workVolumeRows = withContext(Dispatchers.Default) { buildMaterialReportRows(nodes, routes, workspace.workVolumeRows) }

        ReportingSnapshot(
            projectId = projectId,
            projectName = reportSnapshot.projectName,
            version = System.nanoTime(),
            loadedAtEpochMs = System.currentTimeMillis(),
            nodes = nodes,
            routes = routes,
            photos = photos,
            progress = progress,
            workVolumeRowsRaw = workspace.workVolumeRows,
            workVolumeRows = workVolumeRows,
            dailyLogs = dailyLogs
        )
    }

    private suspend fun ensureReportingSnapshot(projectId: String): ReportingSnapshot {
        val current = reportSnapshot.value
        if (current.isForProject(projectId) && current.version > 0L) {
            return current
        }
        val snapshot = loadReportingSnapshot(projectId)
        cacheReportingSnapshot(snapshot)
        return snapshot
    }

    private fun replaceReportingSnapshot(snapshot: ReportingSnapshot) {
        aiReportJob?.cancel()
        aiReportJob = null
        lastDraftRequest = null
        _reportSnapshot.value = snapshot.withDraft(null)
    }

    private fun cacheReportingSnapshot(snapshot: ReportingSnapshot) {
        val currentDraft = reportSnapshot.value.aiDraft
        val preservedDraft = currentDraft.takeIf { reportSnapshot.value.isForProject(snapshot.projectId.orEmpty()) }
        _reportSnapshot.value = snapshot.withDraft(preservedDraft)
    }

    private fun clearReportingState() {
        aiReportJob?.cancel()
        aiReportJob = null
        lastDraftRequest = null
        _reportSnapshot.value = ReportingSnapshot.Empty
    }

    private suspend fun resolveActiveDraft(
        snapshot: ReportingSnapshot,
        filterNodeCode: String?
    ): ReportDraftResult {
        val request = ReportDraftRequest(snapshot.projectId.orEmpty(), snapshot.version)
        if (snapshot.aiDraft != null && lastDraftRequest == request) {
            return snapshot.aiDraft
        }
        aiReportJob?.takeIf { it.isActive && lastDraftRequest == request }?.join()
        val currentAfterWait = reportSnapshot.value
        if (currentAfterWait.aiDraft != null && lastDraftRequest == request) {
            return currentAfterWait.aiDraft
        }
        val draft = generateAiReportDraft(snapshot, null)
        lastDraftRequest = request
        _reportSnapshot.value = _reportSnapshot.value.withDraft(draft)
        return draft
    }

    private suspend fun generateAiReportDraft(
        snapshot: ReportingSnapshot,
        filterNodeCode: String? = null
    ): ReportDraftResult = withContext(Dispatchers.Default) {
        val photos = snapshot.photos
        val progress = snapshot.progress
        val gisNodes = snapshot.nodes
        val logs = snapshot.dailyLogs

        val totalNodesCount = gisNodes.size
        val inProgressCount = progress.count { it.actual > 0f && it.actual < 100f }
        val photoNodesSummary = photos.map { it.objectCode }.distinct().sorted().joinToString(", ")
        val delayed = progress.count { it.delayed }
        val avg = if (progress.isEmpty()) 0f else progress.map { it.actual }.average().toFloat()

        val nodesSummary = progress.joinToString("\n") { p ->
            val matchingGis = gisNodes.find { it.code == p.nodeCode }
            "- Nút/Tuyến [${p.nodeCode}]: Kế hoạch ${"%.1f".format(p.planned)}%, thực tế đạt ${"%.1f".format(p.actual)}%${if (p.delayed) " [CHẬM TIẾN ĐỘ]" else ""}. Nhà thầu: ${matchingGis?.contractor ?: "Chưa rõ"}."
        }

        val delayedNodesByContractor = progress.filter { it.delayed }.groupBy { p ->
            gisNodes.find { it.code == p.nodeCode }?.contractor ?: "Chưa rõ"
        }
        val contractorsSummary = if (delayedNodesByContractor.isEmpty()) {
            "- Tất cả các nhà thầu đều đảm bảo đúng tiến độ thi công theo kế hoạch."
        } else {
            delayedNodesByContractor.entries.joinToString("\n") { (contractor, nodes) ->
                "- Nhà thầu [$contractor] đang chậm tiến độ tại ${nodes.size} điểm: ${nodes.joinToString(", ") { it.nodeCode }}. Yêu cầu bổ sung nhân sự, tăng ca để bù tiến độ bị chậm trễ."
            }
        }

        val notesSummary = logs.filter { it.note.isNotBlank() }.joinToString("\n") { log ->
            "- Hạng mục [${log.workItem}]${log.nodeCode?.let { " ($it)" } ?: ""}: ${log.note}"
        }

        val result = aiFacade.execute<ReportDraftResult>(
            ReportDraftPayload(
                projectId = snapshot.projectName.ifBlank { snapshot.projectId.orEmpty() },
                totalNodes = totalNodesCount,
                delayedNodes = delayed,
                avgActualProgress = avg,
                totalPhotos = photos.size,
                nodesSummary = nodesSummary,
                contractorsSummary = contractorsSummary,
                notesSummary = notesSummary,
                inProgressNodes = inProgressCount,
                photoNodesSummary = photoNodesSummary
            )
        ).result

        result
    }

}

private data class ReportDraftRequest(
    val projectId: String,
    val snapshotVersion: Long
)

data class MaterialReportRow(
    val workName: String,
    val nodeCount: Int = 0,
    val routeCount: Int = 0,
    val totalPlannedQty: Float,
    val totalActualQty: Float,
    val completionPercent: Float,
    val isTotal: Boolean = false
)


