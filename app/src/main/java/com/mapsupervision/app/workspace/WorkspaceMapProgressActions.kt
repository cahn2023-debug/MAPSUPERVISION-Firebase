package com.mapsupervision.app.workspace

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.ai.AiDecisionSource
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.ImportMappingPayload
import com.mapsupervision.domain.ai.OpsRecommendationPayload
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.NodeSignalStatus
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.ai.NoteSummarizationPayload
import com.mapsupervision.domain.ai.NoteSummarizationResult
import com.mapsupervision.domain.ai.TaskRecommendationPayload
import com.mapsupervision.domain.ai.TaskRecommendationResult
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.createStoredSitePhoto
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.domain.util.StringSimilarity
import com.mapsupervision.storage.importer.UserFileImportService
import com.mapsupervision.storage.importer.ConfirmedFieldFlags
import com.mapsupervision.storage.importer.ExcelColumnMapping
import com.mapsupervision.storage.importer.ExcelClassificationMode
import com.mapsupervision.storage.importer.NonExcelFieldCandidateSet
import com.mapsupervision.storage.importer.NonExcelImportMapping
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.MapLayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.roundToLong

fun WorkspaceViewModel.addConstructionProgress(nodeCode: String, planned: Float, actual: Float) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val normalizedNodeCode = findBestMatchingNodeCode(nodeCode, _state.value.designNodes)
        val nodeId = ensureIndexes().nodesByCode[normalizedNodeCode]?.id
        val remain = (planned - actual).coerceAtLeast(0f)
        val upsertResult = progressRepository.upsert(
            NodeProgress(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                nodeCode = normalizedNodeCode,
                planned = planned,
                actual = actual,
                remain = remain,
                delayed = actual < planned,
                updatedAtEpochMs = System.currentTimeMillis(),
                nodeId = nodeId
            )
        )
        if (upsertResult is AppResult.Error) {
            _state.value = _state.value.copy(
                importUi = _state.value.importUi.copy(
                    message = "Lỗi cập nhật thi công: ${upsertResult.throwable.message}"
                )
            )
            return@launch
        }
        markProjectChanged(projectId, "construction_progress_updated")
        _state.value = _state.value.copy(
            importUi = _state.value.importUi.copy(message = "Đã cập nhật thi công cho node $normalizedNodeCode")
        )
    }
}

fun WorkspaceViewModel.addDailyLog(request: AddDailyLogRequest) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val now = System.currentTimeMillis()

        val normalizedNodeCode = request.nodeCode?.let { findBestMatchingNodeCode(it, _state.value.designNodes) }
        val normalizedRouteCode = request.routeCode?.let { findBestMatchingRouteCode(it, _state.value.designRoutes) }
        val nodeId = normalizedNodeCode?.let { ensureIndexes().nodesByCode[it]?.id }
        val routeId = normalizedRouteCode?.let { ensureIndexes().routesByCode[it]?.id }

        if (normalizedNodeCode.isNullOrBlank() && normalizedRouteCode.isNullOrBlank()) {
            showMessage("Vui lòng chọn vị trí hoặc tuyến liên kết")
            return@launch
        }

        if (normalizedNodeCode.isNullOrBlank() && !normalizedRouteCode.isNullOrBlank()) {
            showMessage("Không thể lưu nhật ký liên kết tuyến mà không chọn vị trí cụ thể")
            return@launch
        }

        val normalizedPlanSnapshot = request.planSnapshot?.let { snapshot ->
            val plannedNodeCode = snapshot.plannedNodeCode?.let { findBestMatchingNodeCode(it, _state.value.designNodes) }
            val plannedRouteCode = snapshot.plannedRouteCode?.let { findBestMatchingRouteCode(it, _state.value.designRoutes) }
            if (plannedRouteCode != null && plannedNodeCode == null && normalizedNodeCode == null) {
                showMessage("Kế hoạch theo tuyến cần chọn node cụ thể trước khi lưu nhật ký")
                return@launch
            }
            DailyLogPlanSnapshotDraft(
                linkedWorkPlanId = snapshot.linkedWorkPlanId,
                plannedWorkName = snapshot.plannedWorkName,
                plannedQuantity = snapshot.plannedQuantity,
                plannedUnit = snapshot.plannedUnit,
                plannedNodeCode = plannedNodeCode ?: normalizedNodeCode,
                plannedRouteCode = plannedRouteCode
            )
        }

        val lines = request.actualLines.mapIndexed { index, line ->
            val normalizedLineNodeCode = line.nodeCode?.let { findBestMatchingNodeCode(it, _state.value.designNodes) } ?: normalizedNodeCode
            val normalizedLineRouteCode = line.routeCode?.let { findBestMatchingRouteCode(it, _state.value.designRoutes) } ?: normalizedRouteCode
            val normalizedCategoryResult = if (line.categoryName.isNotBlank()) {
                findBestMatchingCategory(line.categoryName, _state.value.workCategories, _state.value.workVolumeRows)
            } else if (line.workName.isNotBlank()) {
                findBestMatchingCategory(line.workName, _state.value.workCategories, _state.value.workVolumeRows)
            } else null
            val normalizedCategory = normalizedCategoryResult?.first ?: line.categoryName.ifBlank { line.workName }
            val normalizedUnit = normalizedCategoryResult?.second ?: line.unit
            val normalizedWorkName = line.workName.ifBlank {
                if (index == 0) request.workItem else normalizedCategory
            }
            com.mapsupervision.domain.model.DailyLogLine(
                id = line.id.ifBlank { UUID.randomUUID().toString() },
                projectId = projectId,
                dailyLogId = request.existingId ?: "",
                lineType = if (index == 0 && normalizedPlanSnapshot != null) {
                    com.mapsupervision.domain.model.DailyLogLineType.PLAN_PRIMARY
                } else {
                    com.mapsupervision.domain.model.DailyLogLineType.EXTRA
                },
                workName = normalizedWorkName,
                categoryName = normalizedCategory,
                quantity = line.quantityInput.toDoubleOrNull() ?: 0.0,
                unit = normalizedUnit,
                nodeCode = normalizedLineNodeCode,
                routeCode = normalizedLineRouteCode,
                linkedWorkPlanId = if (index == 0) normalizedPlanSnapshot?.linkedWorkPlanId else null,
                nodeId = normalizedLineNodeCode?.let { ensureIndexes().nodesByCode[it]?.id },
                routeId = normalizedLineRouteCode?.let { ensureIndexes().routesByCode[it]?.id },
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        }

        lines.forEachIndexed { index, line ->
            val lineNodeCode = line.nodeCode ?: return@forEachIndexed
            if (lineNodeCode.isBlank() || line.quantity <= 0.0) return@forEachIndexed
            val existing = _state.value.workVolumeRows.firstOrNull {
                it.nodeCode.equals(lineNodeCode, ignoreCase = true) &&
                    it.workName.equals(line.workName, ignoreCase = true)
            }
            val lineNode = ensureIndexes().nodesByCode[lineNodeCode]
            val fallbackPlannedQty = extractPlannedQty(lineNode, line.categoryName.ifBlank { line.workName })
            val plannedQty = when {
                index == 0 && normalizedPlanSnapshot != null && normalizedPlanSnapshot.plannedQuantity > 0.0 ->
                    normalizedPlanSnapshot.plannedQuantity.toFloat()
                existing != null && existing.plannedQty > 0f -> existing.plannedQty
                else -> fallbackPlannedQty
            }
            val resolvedUnit = when {
                index == 0 && normalizedPlanSnapshot != null && normalizedPlanSnapshot.plannedUnit.isNotBlank() -> normalizedPlanSnapshot.plannedUnit
                line.unit.isNotBlank() -> line.unit
                existing != null && existing.unit.isNotBlank() -> existing.unit
                else -> ""
            }
            workVolumeProgressRepository.upsert(
                WorkVolumeProgress(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    projectId = projectId,
                    nodeCode = lineNodeCode,
                    workName = existing?.workName ?: line.workName,
                    plannedQty = plannedQty,
                    actualQty = line.quantity.toFloat(),
                    updatedAtEpochMs = now,
                    unit = resolvedUnit,
                    nodeId = line.nodeId
                )
            )
        }

        val computedProgress = normalizedPlanSnapshot
            ?.takeIf { it.plannedQuantity > 0.0 }
            ?.let { snapshot ->
                val primaryQty = lines.firstOrNull()?.quantity ?: 0.0
                ((primaryQty / snapshot.plannedQuantity) * 100.0).toFloat().coerceIn(0f, 100f)
            }
        val progressToUpdate = request.actualProgressPercent ?: computedProgress
        if (progressToUpdate != null && !normalizedNodeCode.isNullOrBlank()) {
            val existingProgress = _state.value.constructionProgress.firstOrNull { it.nodeCode == normalizedNodeCode }
            addConstructionProgress(normalizedNodeCode, existingProgress?.planned ?: 100f, progressToUpdate)
        }

        val finalId = if (!request.existingId.isNullOrBlank()) request.existingId else UUID.randomUUID().toString()
        val originalLog = _state.value.dailyLogs.firstOrNull { it.id == finalId }
        val createdAt = originalLog?.createdAtEpochMs ?: now
        val persistedLines = lines.map { line ->
            line.copy(
                dailyLogId = finalId,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = now
            )
        }
        val primaryLine = persistedLines.firstOrNull()

        val log = com.mapsupervision.domain.model.DailyLog(
            id = finalId,
            projectId = projectId,
            workItem = request.workItem,
            manpower = request.manpower,
            note = request.note,
            createdAtEpochMs = createdAt,
            weather = request.weather,
            temperature = request.temperature,
            nodeCode = normalizedNodeCode.takeIf { !it.isNullOrBlank() },
            routeCode = normalizedRouteCode.takeIf { !it.isNullOrBlank() },
            dateEpochDay = request.dateEpochDay,
            volume = primaryLine?.quantity ?: 0.0,
            unit = primaryLine?.unit.orEmpty(),
            categoryName = primaryLine?.categoryName.orEmpty(),
            linkedWorkPlanId = normalizedPlanSnapshot?.linkedWorkPlanId,
            plannedWorkName = normalizedPlanSnapshot?.plannedWorkName.orEmpty(),
            plannedQuantity = normalizedPlanSnapshot?.plannedQuantity ?: 0.0,
            plannedUnit = normalizedPlanSnapshot?.plannedUnit.orEmpty(),
            plannedNodeCode = normalizedPlanSnapshot?.plannedNodeCode,
            plannedRouteCode = normalizedPlanSnapshot?.plannedRouteCode,
            nodeId = nodeId,
            routeId = routeId,
            plannedNodeId = normalizedPlanSnapshot?.plannedNodeCode?.let { ensureIndexes().nodesByCode[it]?.id },
            plannedRouteId = normalizedPlanSnapshot?.plannedRouteCode?.let { ensureIndexes().routesByCode[it]?.id },
            lines = persistedLines
        )
        val result = dailyLogRepository.add(log)
        if (result is AppResult.Error) {
            AppLogger.d("dailylog.add.error project=$projectId msg=${result.throwable.message}")
            return@launch
        }
        markProjectChanged(projectId, "daily_log_added")
    }
}

fun WorkspaceViewModel.addDailyLog(
    workItem: String,
    manpower: Int,
    note: String,
    weather: String = "",
    temperature: Double = 0.0,
    nodeCode: String? = null,
    routeCode: String? = null,
    dateEpochDay: Long = 0L,
    volume: Double = 0.0,
    unit: String = "",
    categoryName: String = "",
    id: String? = null,
    actualProgressPercent: Float? = null
) {
    addDailyLog(
        AddDailyLogRequest(
            workItem = workItem,
            manpower = manpower,
            note = note,
            weather = weather,
            temperature = temperature,
            nodeCode = nodeCode,
            routeCode = routeCode,
            dateEpochDay = dateEpochDay,
            actualLines = listOf(
                DailyLogDraftLine(
                    id = UUID.randomUUID().toString(),
                    workName = workItem,
                    categoryName = categoryName,
                    quantityInput = if (volume > 0.0) volume.toString() else "",
                    unit = unit,
                    nodeCode = nodeCode,
                    routeCode = routeCode
                )
            ),
            existingId = id,
            actualProgressPercent = actualProgressPercent
        )
    )
}

internal fun WorkspaceViewModel.extractPlannedQty(node: GisNode?, workName: String): Float {
    if (node == null) return 100f
    val lines = node.workVolumeSummary.split("\n")
    for (line in lines) {
        val parts = line.split(":", limit = 2)
        if (parts.size == 2 && parts[0].trim().equals(workName, ignoreCase = true)) {
            return parts[1].trim().toFloatOrNull() ?: 100f
        }
    }
    return 100f
}

fun WorkspaceViewModel.addWorkCategory(name: String, unit: String) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val category = WorkCategory(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name,
            unit = unit,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val result = workCategoryRepository.add(category)
        if (result is AppResult.Error) {
            showMessage(result.throwable.message ?: "Không thể lưu hạng mục công việc")
            return@launch
        }
        markProjectChanged(projectId, "work_category_added")
    }
}

fun WorkspaceViewModel.fetchWeatherAuto(
    nodeCode: String?,
    routeCode: String? = null,
    onResult: (String, Double) -> Unit
) {
    viewModelScope.launch {
        val node = nodeCode?.let { ensureIndexes().nodesByCode[it] }
        val coords = node?.let { it.latitude to it.longitude }
            ?: routeCode?.let { rCode ->
                val matchingRouteCode = findBestMatchingRouteCode(rCode, _state.value.designRoutes)
                val route = ensureIndexes().routesByCode[matchingRouteCode]
                if (route != null) {
                    val startNode = ensureIndexes().nodesByCode[route.startNodeCode]
                    val endNode = ensureIndexes().nodesByCode[route.endNodeCode]
                    if (startNode != null && endNode != null) {
                        val midLat = (startNode.latitude + endNode.latitude) / 2.0
                        val midLng = (startNode.longitude + endNode.longitude) / 2.0
                        midLat to midLng
                    } else if (startNode != null) {
                        startNode.latitude to startNode.longitude
                    } else if (endNode != null) {
                        endNode.latitude to endNode.longitude
                    } else {
                        null
                    }
                } else null
            }
            ?: locationProvider.lastKnownLocation().let { snapshot ->
                snapshot.latitude?.let { lat -> snapshot.longitude?.let { lng -> lat to lng } }
            }
            ?: (10.762622 to 106.660172)

        when (val result = weatherService.fetchWeather(coords.first, coords.second)) {
            is AppResult.Success -> onResult(result.data.condition, result.data.temperature)
            is AppResult.Error -> AppLogger.d("weather.fetch.error msg=${result.throwable.message}")
        }
    }
}

fun WorkspaceViewModel.selectMapNode(node: GisNode) {
    val delayed = ensureIndexes().progressByNodeCode[node.code]?.delayed == true
    val centerNodeCode = _state.value.mapUi.centerNodeCode
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            selectedNode = node,
            selectedRoute = null,
            status = if (delayed) "Chậm tiến độ" else "Bình thường",
            expectedCompletion = if (delayed) "Quá hạn" else "Đúng tiến độ",
            lastInspection = "Hôm nay",
            signalStatus = node.signalStatus,
            centerPathSummary = buildCenterPathSummary(node.code, centerNodeCode, _state.value.designRoutes),
            message = ""
        )
    )
    GisMapBridgeRegistry.bridge?.centerOnLocation(node.latitude, node.longitude, 18.0)
}

fun WorkspaceViewModel.clearMapNodeSelection() {
    _state.value = _state.value.copy(mapUi = _state.value.mapUi.copy(selectedNode = null))
}

fun WorkspaceViewModel.setMapCenterNode(node: GisNode?) {
    val centerNodeCode = node?.code
    val selectedNode = _state.value.mapUi.selectedNode
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            centerNodeCode = centerNodeCode,
            centerPathSummary = selectedNode?.let {
                buildCenterPathSummary(it.code, centerNodeCode, _state.value.designRoutes)
            }.orEmpty()
        )
    )
}

fun WorkspaceViewModel.selectMapRoute(route: GisRoute) {
    val indexes = ensureIndexes()
    val selectedRoute = indexes.routesByCode[route.code] ?: indexes.routeRepresentativesByCode[route.code] ?: route
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            selectedRoute = selectedRoute,
            selectedNode = null,
            message = ""
        )
    )
    val startNode = indexes.nodesByCode[selectedRoute.startNodeCode]
    val endNode = indexes.nodesByCode[selectedRoute.endNodeCode]
    if (startNode != null && endNode != null) {
        val midLat = (startNode.latitude + endNode.latitude) / 2.0
        val midLng = (startNode.longitude + endNode.longitude) / 2.0
        GisMapBridgeRegistry.bridge?.centerOnLocation(midLat, midLng, 18.0)
    }
}

fun WorkspaceViewModel.clearMapRouteSelection() {
    _state.value = _state.value.copy(mapUi = _state.value.mapUi.copy(selectedRoute = null, routeNote = ""))
}

fun WorkspaceViewModel.updateRouteNote(note: String) {
    // Obsolete: kept as no-op or delegated to persistent note if needed, but UI will use addNote
}

fun WorkspaceViewModel.loadNotesAndTasks(objectCode: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val notesResult = noteRepository.byObject(projectId, objectCode)
        val tasksResult = taskRepository.byObject(projectId, objectCode)
        val notes = (notesResult as? AppResult.Success)?.data.orEmpty()
        val tasks = (tasksResult as? AppResult.Success)?.data.orEmpty()
        _state.value = _state.value.copy(
            selectedObjectNotes = notes,
            selectedObjectTasks = tasks,
            aiNoteSummary = "",
            aiTaskSuggestions = emptyList()
        )
    }
}

fun WorkspaceViewModel.addNote(objectCode: String, content: String) {
    if (content.isBlank()) return
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val normalizedObjectCode = findBestMatchingNodeCode(objectCode, _state.value.designNodes)
        noteRepository.add(
            Note(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                objectCode = normalizedObjectCode,
                content = content,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
        markProjectChanged(projectId, "note_added")
        loadNotesAndTasks(normalizedObjectCode)
    }
}

fun WorkspaceViewModel.deleteNote(noteId: String, objectCode: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId
        noteRepository.delete(noteId)
        if (projectId != null) {
            markProjectChanged(projectId, "note_deleted")
        }
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.addTask(objectCode: String, title: String) {
    if (title.isBlank()) return
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val normalizedObjectCode = findBestMatchingNodeCode(objectCode, _state.value.designNodes)
        taskRepository.upsert(
            Task(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                objectCode = normalizedObjectCode,
                title = title,
                description = "",
                status = TaskStatus.TODO,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
        markProjectChanged(projectId, "task_added")
        loadNotesAndTasks(normalizedObjectCode)
    }
}

fun WorkspaceViewModel.toggleTaskStatus(taskId: String, objectCode: String, currentStatus: TaskStatus) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val task = _state.value.selectedObjectTasks.firstOrNull { it.id == taskId } ?: return@launch
        val nextStatus = when (currentStatus) {
            TaskStatus.TODO -> TaskStatus.IN_PROGRESS
            TaskStatus.IN_PROGRESS -> TaskStatus.COMPLETED
            TaskStatus.COMPLETED -> TaskStatus.TODO
        }
        taskRepository.upsert(
            task.copy(
                status = nextStatus,
                completedAtEpochMs = if (nextStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else null
            )
        )
        markProjectChanged(projectId, "task_status_updated")
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.deleteTask(taskId: String, objectCode: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId
        taskRepository.delete(taskId)
        if (projectId != null) {
            markProjectChanged(projectId, "task_deleted")
        }
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.summarizeNotes(objectCode: String) {
    viewModelScope.launch {
        val notes = _state.value.selectedObjectNotes.map { it.content }
        if (notes.isEmpty()) {
            _state.value = _state.value.copy(aiNoteSummary = "Chưa có ghi chú nào để tóm tắt.")
            return@launch
        }
        _state.value = _state.value.copy(isAiLoading = true)
        val aiResult = runCatching {
            aiOrchestrator.execute<NoteSummarizationResult>(
                NoteSummarizationPayload(objectCode = objectCode, notes = notes)
            )
        }.getOrNull()

        _state.value = _state.value.copy(
            aiNoteSummary = aiResult?.result?.summary ?: "Không thể kết nối dịch vụ tóm tắt AI.",
            isAiLoading = false
        )
    }
}

fun WorkspaceViewModel.suggestTasks(objectCode: String) {
    viewModelScope.launch {
        val notes = _state.value.selectedObjectNotes.map { it.content }
        val existingTasks = _state.value.selectedObjectTasks.map { it.title }
        _state.value = _state.value.copy(isAiLoading = true)
        val aiResult = runCatching {
            aiOrchestrator.execute<TaskRecommendationResult>(
                TaskRecommendationPayload(objectCode = objectCode, notes = notes, existingTasks = existingTasks)
            )
        }.getOrNull()

        _state.value = _state.value.copy(
            aiTaskSuggestions = aiResult?.result?.suggestedTasks.orEmpty(),
            isAiLoading = false
        )
    }
}

fun WorkspaceViewModel.onMapZoomIn() {
    GisMapBridgeRegistry.bridge?.zoomIn()
}

fun WorkspaceViewModel.onMapZoomOut() {
    GisMapBridgeRegistry.bridge?.zoomOut()
}

fun WorkspaceViewModel.onMapMyLocation() {
    val centered = GisMapBridgeRegistry.bridge?.centerOnMyLocation() == true
    if (!centered) {
        GisMapBridgeRegistry.bridge?.fitToObjects()
    }
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            message = if (centered) "Định vị vị trí của tôi" else "Không có GPS, đã zoom toàn bộ đối tượng"
        )
    )
}

fun WorkspaceViewModel.onMapToggleLayer() {
    val current = _state.value.mapUi
    val newShowNodes = !current.showNodes
    val newShowRoutes = !current.showRoutes
    GisMapBridgeRegistry.bridge?.setLayerVisibility(newShowNodes, newShowRoutes)
    _state.value = _state.value.copy(
        mapUi = current.copy(
            showNodes = newShowNodes,
            showRoutes = newShowRoutes,
            message = "Đã chuyển đổi lớp bản đồ"
        )
    )
}

fun WorkspaceViewModel.onMapBaseMapChanged(type: MapLayerType) {
    GisMapBridgeRegistry.bridge?.setBaseMap(type)
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(message = "Đã đổi lớp nền bản đồ")
    )
}

fun WorkspaceViewModel.onFilterContractorChanged(contractor: String?) {
    val normalized = contractor?.takeIf { it.isNotBlank() }
    AppLogger.d("map.filter change requested contractor=$normalized previous=${_state.value.mapUi.filterContractor}")
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            filterContractor = normalized,
            message = ""
        )
    )
    AppLogger.d("map.filter change applied contractor=${_state.value.mapUi.filterContractor}")
    updateFilteredMapData(FilteredMapUpdateReason.FILTER)
}

fun WorkspaceViewModel.onFilterMaterialTypeChanged(materialType: String?) {
    val normalized = materialType?.takeIf { it.isNotBlank() }
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            filterMaterialType = normalized,
            message = ""
        )
    )
    updateFilteredMapData(FilteredMapUpdateReason.FILTER)
}

fun WorkspaceViewModel.onContractorColorChanged(contractor: String, hexColor: String) {
    val updated = _state.value.mapUi.contractorColors.toMutableMap()
    updated[contractor] = hexColor
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(contractorColors = updated)
    )
    val projectId = _state.value.activeProjectId
    if (projectId != null) {
        saveContractorColor(projectId, contractor, hexColor)
    }
}

fun WorkspaceViewModel.onToggleContractorVisibility(contractor: String, isHidden: Boolean) {
    val hidden = _state.value.mapUi.hiddenContractors.toMutableSet()
    if (isHidden) {
        hidden.add(contractor)
    } else {
        hidden.remove(contractor)
    }
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(hiddenContractors = hidden)
    )
    val projectId = _state.value.activeProjectId
    if (projectId != null) {
        saveContractorVisibility(projectId, contractor, isHidden)
    }
    updateFilteredMapData(FilteredMapUpdateReason.FILTER)
}

fun WorkspaceViewModel.onSearchQueryChanged(query: String) {
    val trimmed = query.trim()
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(searchQuery = trimmed, message = "")
    )
    updateFilteredMapData(FilteredMapUpdateReason.SEARCH)
    if (trimmed.isBlank()) return
    mapSearchJob?.cancel()
    mapSearchJob = viewModelScope.launch {
        try {
            delay(180)
            val stateSnapshot = _state.value
            val indexes = ensureIndexes(stateSnapshot)
            val normalizedQuery = normalizeMapSearchText(trimmed)
            val matchedNode = stateSnapshot.designNodes.firstOrNull { node ->
                indexes.normalizedNodeSearch[node.id]?.matches(normalizedQuery) == true
            }
            if (matchedNode != null) {
                if (stateSnapshot.mapUi.selectedNode?.code != matchedNode.code) {
                    GisMapBridgeRegistry.bridge?.centerOnLocation(matchedNode.latitude, matchedNode.longitude)
                }
                _state.value = _state.value.copy(
                    mapUi = _state.value.mapUi.copy(message = "Tim thay: ${matchedNode.code}")
                )
                return@launch
            }
            val matchedRoute = stateSnapshot.designRoutes.firstOrNull { route ->
                indexes.normalizedRouteSearch[route.code]?.matches(normalizedQuery) == true
            }
            if (matchedRoute != null) {
                val start = indexes.nodesByCode[matchedRoute.startNodeCode]
                val end = indexes.nodesByCode[matchedRoute.endNodeCode]
                if (start != null && end != null && stateSnapshot.mapUi.selectedRoute?.code != matchedRoute.code) {
                    GisMapBridgeRegistry.bridge?.centerOnLocation(
                        (start.latitude + end.latitude) / 2,
                        (start.longitude + end.longitude) / 2
                    )
                }
                _state.value = _state.value.copy(
                    mapUi = _state.value.mapUi.copy(message = "Tim thay tuyen: ${matchedRoute.code}")
                )
                return@launch
            }
            val geocode = geocodeByNominatim(trimmed)
            if (geocode != null) {
                GisMapBridgeRegistry.bridge?.centerOnLocation(geocode.first, geocode.second)
                _state.value = _state.value.copy(
                    mapUi = _state.value.mapUi.copy(message = "Da tim vi tri theo dia chi")
                )
            } else {
                _state.value = _state.value.copy(
                    mapUi = _state.value.mapUi.copy(message = "Khong tim thay ket qua")
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.e(error, "workspace.map.search.failed query=$trimmed")
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Khong the tim kiem")
            )
        }
    }
}

private fun WorkspaceViewModel.normalizeVietnamese(text: String): String {
    return normalizeMapSearchText(text)
}

private fun WorkspaceViewModel.normalizeForMatching(text: String): String {
    val lowercase = text.trim().lowercase(java.util.Locale.US)
        .replace('đ', 'd')
        .replace('Đ', 'd')
    val normalized = java.text.Normalizer.normalize(lowercase, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return normalized.replace(Regex("[^a-z0-9]+"), "")
}

private val CATEGORY_SYNONYMS = mapOf(
    "raicap" to "Kéo cáp",
    "keocap" to "Kéo cáp",
    "luoncap" to "Kéo cáp",
    "lapcap" to "Kéo cáp",
    "cap" to "Kéo cáp",
    "be tong" to "Bê tông",
    "betong" to "Bê tông",
    "domong" to "Móng",
    "dapmong" to "Móng",
    "daomong" to "Móng",
    "bemong" to "Móng",
    "lammong" to "Móng",
    "lapthietbi" to "Thi công lắp đặt",
    "lapdatthietbi" to "Thi công lắp đặt",
    "lapcamera" to "Thi công lắp đặt",
    "laptu" to "Thi công lắp đặt"
)

private fun WorkspaceViewModel.findBestMatchingNodeCode(inputCode: String, nodes: List<GisNode>): String {
    if (inputCode.isBlank()) return inputCode
    val cleanInput = inputCode.trim().lowercase()

    // 1. Exact or case-insensitive match
    val directMatch = nodes.firstOrNull { it.code.trim().equals(inputCode.trim(), ignoreCase = true) }
    if (directMatch != null) return directMatch.code

    // 2. Accent-stripped and non-alphanumeric stripped match
    val normInput = normalizeForMatching(cleanInput)
    val normMatch = nodes.firstOrNull { normalizeForMatching(it.code).equals(normInput, ignoreCase = true) }
    if (normMatch != null) return normMatch.code

    // 3. Numeric matching fallback
    val inputDigits = cleanInput.filter { it.isDigit() }
    if (inputDigits.isNotEmpty()) {
        val digitMatches = nodes.filter { node ->
            val nodeDigits = node.code.filter { it.isDigit() }
            nodeDigits.isNotEmpty() && (nodeDigits == inputDigits || nodeDigits.toIntOrNull() == inputDigits.toIntOrNull())
        }
        if (digitMatches.isNotEmpty()) {
            var bestScore = 0.0
            var bestMatch: GisNode? = null
            digitMatches.forEach { node ->
                val score = StringSimilarity.similarityScore(normalizeForMatching(node.code), normInput)
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = node
                }
            }
            if (bestMatch != null) {
                return bestMatch!!.code
            }
        }
    }

    // 4. Similarity score match (threshold e.g. 0.7)
    var bestScore = 0.0
    var bestMatch: GisNode? = null
    nodes.forEach { node ->
        val score = StringSimilarity.similarityScore(normalizeForMatching(node.code), normInput)
        if (score > bestScore) {
            bestScore = score
            bestMatch = node
        }
    }
    if (bestScore >= 0.7 && bestMatch != null) {
        return bestMatch!!.code
    }
    return inputCode
}

private fun WorkspaceViewModel.findBestMatchingRouteCode(inputCode: String, routes: List<GisRoute>): String {
    if (inputCode.isBlank()) return inputCode
    val cleanInput = inputCode.trim().lowercase()

    // 1. Exact or case-insensitive match
    val directMatch = routes.firstOrNull { it.code.trim().equals(inputCode.trim(), ignoreCase = true) }
    if (directMatch != null) return directMatch.code

    // 2. Accent-stripped and non-alphanumeric stripped match
    val normInput = normalizeForMatching(cleanInput)
    val normMatch = routes.firstOrNull { normalizeForMatching(it.code).equals(normInput, ignoreCase = true) }
    if (normMatch != null) return normMatch.code

    // 3. Similarity score match (threshold e.g. 0.7)
    var bestScore = 0.0
    var bestMatch: GisRoute? = null
    routes.forEach { route ->
        val score = StringSimilarity.similarityScore(normalizeForMatching(route.code), normInput)
        if (score > bestScore) {
            bestScore = score
            bestMatch = route
        }
    }
    if (bestScore >= 0.7 && bestMatch != null) {
        return bestMatch!!.code
    }
    return inputCode
}

private fun WorkspaceViewModel.findBestMatchingCategory(
    inputCategory: String,
    categories: List<WorkCategory>,
    materials: List<WorkVolumeProgress>
): Pair<String, String>? {
    if (inputCategory.isBlank()) return null
    val cleanInput = inputCategory.trim().lowercase()

    // 1. Check synonym dictionary map
    val normInput = normalizeForMatching(cleanInput)
    val synonymTargetName = CATEGORY_SYNONYMS[normInput]
    if (synonymTargetName != null) {
        val matchedCat = categories.firstOrNull { normalizeForMatching(it.name) == normalizeForMatching(synonymTargetName) }
        if (matchedCat != null) return matchedCat.name to matchedCat.unit
    }

    // 2. Check direct unit hint against known categories/materials
    val unitHintTarget = when (normInput) {
        "m3" -> categories.firstOrNull { normalizeForMatching(it.name).contains("betong") }
        "m" -> categories.firstOrNull { normalizeForMatching(it.name).contains("cap") }
        "m2" -> categories.firstOrNull { normalizeForMatching(it.name).contains("sanlap") }
        else -> null
    }
    if (unitHintTarget != null) return unitHintTarget.name to unitHintTarget.unit

    // 3. Check exact match in workCategories
    val directCat = categories.firstOrNull { it.name.trim().equals(inputCategory.trim(), ignoreCase = true) }
    if (directCat != null) return directCat.name to directCat.unit

    // 4. Check exact match in materials
    val directMat = materials.firstOrNull { it.workName.trim().equals(inputCategory.trim(), ignoreCase = true) }
    if (directMat != null) {
        val catUnit = categories.firstOrNull { it.name.trim().equals(directMat.workName.trim(), ignoreCase = true) }?.unit ?: ""
        return directMat.workName to catUnit
    }

    // 5. Normalized matching (ignoring accents, spaces, special chars)
    val normCat = categories.firstOrNull { normalizeForMatching(it.name).equals(normInput, ignoreCase = true) }
    if (normCat != null) return normCat.name to normCat.unit

    val normMat = materials.firstOrNull { normalizeForMatching(it.workName).equals(normInput, ignoreCase = true) }
    if (normMat != null) {
        val catUnit = categories.firstOrNull { it.name.trim().equals(normMat.workName.trim(), ignoreCase = true) }?.unit ?: ""
        return normMat.workName to catUnit
    }

    // 6. Similarity match using StringSimilarity
    var bestScore = 0.0
    var bestCat: WorkCategory? = null
    categories.forEach { cat ->
        val score = StringSimilarity.similarityScore(normalizeForMatching(cat.name), normInput)
        if (score > bestScore) {
            bestScore = score
            bestCat = cat
        }
    }
    
    var bestMatScore = 0.0
    var bestMat: WorkVolumeProgress? = null
    materials.forEach { mat ->
        val score = StringSimilarity.similarityScore(normalizeForMatching(mat.workName), normInput)
        if (score > bestMatScore) {
            bestMatScore = score
            bestMat = mat
        }
    }

    if (bestScore >= 0.7 || bestMatScore >= 0.7) {
        if (bestScore >= bestMatScore && bestCat != null) {
            return bestCat!!.name to bestCat!!.unit
        } else if (bestMat != null) {
            val catUnit = categories.firstOrNull { it.name.trim().equals(bestMat!!.workName.trim(), ignoreCase = true) }?.unit ?: ""
            return bestMat!!.workName to catUnit
        }
    }

    return null
}

private fun WorkspaceViewModel.nodeMatchesQuery(node: GisNode, raw: String, normalized: String): Boolean {
    // Search: mã nút (code) + số thứ tự (mapNumberLabel)
    val fields = listOf(node.code, node.mapNumberLabel)
    return fields.any { field ->
        field.isNotBlank() && (
            field.contains(raw, ignoreCase = true) ||
            normalizeVietnamese(field).contains(normalized, ignoreCase = true)
        )
    }
}

private fun WorkspaceViewModel.routeMatchesQuery(route: GisRoute, raw: String, normalized: String): Boolean {
    // Search: mã tuyến (code) + mã nút đầu/cuối
    val fields = listOf(route.code, route.startNodeCode, route.endNodeCode)
    return fields.any { field ->
        field.isNotBlank() && (
            field.contains(raw, ignoreCase = true) ||
            normalizeVietnamese(field).contains(normalized, ignoreCase = true)
        )
    }
}

fun WorkspaceViewModel.getFilteredDesignNodes(): List<GisNode> {
    val mapUi = _state.value.mapUi
    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeMapSearchText(mapUi.searchQuery)
    val indexes = ensureIndexes()
    return _state.value.designNodes.filter { node ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            node.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedNodeSearch[node.id]?.matches(normalizedQuery) == true
        byContractor && byQuery
    }
}

fun WorkspaceViewModel.getFilteredDesignNodesForMap(): List<GisNode> {
    return buildMapDesignNodes(_state.value, ensureIndexes())
}

fun WorkspaceViewModel.getFilteredDesignRoutes(): List<GisRoute> {
    val mapUi = _state.value.mapUi
    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeMapSearchText(mapUi.searchQuery)
    val indexes = ensureIndexes()
    return _state.value.designRoutes.filter { route ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            route.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedRouteSearch[route.code]?.matches(normalizedQuery) == true
        byContractor && byQuery
    }
}

fun WorkspaceViewModel.getRouteProperties(route: GisRoute): List<Pair<String, String>> {
    val nodesByCode = ensureIndexes().nodesByCode
    val markerIndex = if (route.code.contains("#pm")) route.code.lastIndexOf("_s") else route.code.lastIndexOf("_R")
    val basePrefix = if (markerIndex >= 0) route.code.substring(0, markerIndex) else ""
    val suffixChar = if (route.code.contains("#pm")) "_s" else "_R"

    val points = route.points
    val hasPoints = points.size > 1

    val totalDistM = if (hasPoints) {
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += com.mapsupervision.domain.util.Haversine.distanceInMeters(
                points[i - 1].first, points[i - 1].second,
                points[i].first, points[i].second
            )
        }
        sum
    } else {
        if (basePrefix.isNotBlank()) {
            val lineSegments = _state.value.designRoutes.filter { it.code.startsWith(basePrefix + suffixChar) }
            var sum = 0.0
            lineSegments.forEach { seg ->
                val s = nodesByCode[seg.startNodeCode]
                val e = nodesByCode[seg.endNodeCode]
                if (s != null && e != null) {
                    sum += com.mapsupervision.domain.util.Haversine.distanceInMeters(
                        s.latitude, s.longitude,
                        e.latitude, e.longitude
                    )
                }
            }
            sum
        } else {
            val startNode = nodesByCode[route.startNodeCode]
            val endNode   = nodesByCode[route.endNodeCode]
            if (startNode != null && endNode != null) {
                com.mapsupervision.domain.util.Haversine.distanceInMeters(
                    startNode.latitude, startNode.longitude,
                    endNode.latitude, endNode.longitude
                )
            } else 0.0
        }
    }

    val distanceText = if (totalDistM > 0.0) {
        if (totalDistM >= 1000) "${"%.2f".format(totalDistM / 1000)} km"
        else "${totalDistM.toInt()} m"
    } else ""

    val startCoordText = if (hasPoints) {
        "${"%.6f".format(points.first().first)}, ${"%.6f".format(points.first().second)}"
    } else {
        val startNode = nodesByCode[route.startNodeCode]
        startNode?.let { "${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}" } ?: ""
    }

    val endCoordText = if (hasPoints) {
        "${"%.6f".format(points.last().first)}, ${"%.6f".format(points.last().second)}"
    } else {
        val endNode = nodesByCode[route.endNodeCode]
        endNode?.let { "${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}" } ?: ""
    }

    val cleanRouteCode = if (basePrefix.isNotBlank()) {
        if (route.code.contains("#pm")) basePrefix.substringBefore("#pm") else basePrefix
    } else route.code

    return buildList {
        add("Mã tuyến" to cleanRouteCode)
        if (route.contractor.isNotBlank()) add("Nhà thầu" to route.contractor)
        if (startCoordText.isNotBlank()) add("Tọa độ điểm đầu" to startCoordText)
        if (endCoordText.isNotBlank()) add("Tọa độ điểm cuối" to endCoordText)
        if (distanceText.isNotBlank()) add("Chiều dài tuyến" to distanceText)
        val routeLength = route.designLength.orEmpty()
        if (routeLength.isNotBlank()) add("Chiều dài thiết kế" to routeLength)
        
        route.fiberCoreCount?.let { add("Số core quang" to it.toString()) }
        if (route.fiberConnection.isNotBlank()) add("Sợi kết nối" to route.fiberConnection)
        val startNode = nodesByCode[route.startNodeCode]
        val endNode = nodesByCode[route.endNodeCode]
        collectSummaryProperties(startNode?.workVolumeSummary, endNode?.workVolumeSummary).forEach { (k, v) ->
            add(k to v)
        }
    }
}

internal fun buildCenterPathSummary(
    nodeCode: String,
    centerNodeCode: String?,
    routes: List<GisRoute>
): String {
    val normalizedNodeCode = nodeCode.trim()
    val normalizedCenter = centerNodeCode?.trim().orEmpty()
    if (normalizedNodeCode.isBlank() || normalizedCenter.isBlank()) return ""
    if (normalizedNodeCode.equals(normalizedCenter, ignoreCase = true)) return "Điểm trung tâm"

    val previous = HashMap<String, String>()
    val visited = linkedSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(normalizedNodeCode)
    visited.add(normalizedNodeCode.uppercase())
    var found = false

    while (queue.isNotEmpty() && !found) {
        val current = queue.removeFirst()
        for (route in routes) {
            val start = route.startNodeCode.trim()
            val end = route.endNodeCode.trim()
            if (start.isBlank() || end.isBlank()) continue
            val next = when {
                current.equals(start, ignoreCase = true) -> end
                current.equals(end, ignoreCase = true) -> start
                else -> continue
            }
            val nextKey = next.uppercase()
            if (!visited.add(nextKey)) continue
            previous[nextKey] = current
            if (next.equals(normalizedCenter, ignoreCase = true)) {
                found = true
                break
            }
            queue.add(next)
        }
    }

    if (!found) return "Chưa có đường kết nối về trung tâm"

    val path = mutableListOf(normalizedCenter)
    var cursorKey = normalizedCenter.uppercase()
    while (true) {
        val prev = previous[cursorKey] ?: break
        path.add(prev)
        if (prev.equals(normalizedNodeCode, ignoreCase = true)) break
        cursorKey = prev.uppercase()
    }
    path.reverse()
    return "Đường về trung tâm: ${path.joinToString(" -> ")}"
}

fun WorkspaceViewModel.onMapToggleMeasure() {
    val enabled = !_state.value.mapUi.measureEnabled
    GisMapBridgeRegistry.bridge?.setMeasureEnabled(enabled)
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            measureEnabled = enabled,
            measureDistanceText = "",
            message = if (enabled) "Chạm 2 điểm để đo khoảng cách" else "Tắt đo khoảng cách"
        )
    )
}

fun WorkspaceViewModel.updateMeasureDistance(distanceM: Double) {
    val text = if (distanceM >= 1000)
        "${"%.2f".format(distanceM / 1000)} km"
    else
        "${distanceM.toInt()} m"
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(measureDistanceText = text)
    )
}

fun WorkspaceViewModel.onEnterMapTab() {
    GisMapBridgeRegistry.bridge?.setLayerVisibility(showNodes = true, showRoutes = true)
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            showNodes = true,
            showRoutes = true,
            message = "Đã hiển thị bản đồ"
        )
    )
}

fun WorkspaceViewModel.updateMapLabelField(field: GisLabelField) {
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            labelField = field,
            message = "Hiển thị theo trường: ${field.displayName}"
        )
    )
}

fun WorkspaceViewModel.focusPhotosBySelectedNode() {
    _state.value = _state.value.copy(photoFilterNodeCode = _state.value.mapUi.selectedNode?.code)
}

fun WorkspaceViewModel.loadPhotosForSelectedNode() {
    val targetCode = _state.value.mapUi.selectedNode?.code ?: _state.value.mapUi.selectedRoute?.code ?: return
    val projectId = _state.value.activeProjectId ?: return
    viewModelScope.launch {
        val photos = (photoRepository.byObjectCode(projectId, targetCode) as? AppResult.Success)?.data.orEmpty()
        _state.value = _state.value.copy(selectedNodePhotos = photos)
    }
}

fun WorkspaceViewModel.clearSelectedNodePhotos() {
    _state.value = _state.value.copy(selectedNodePhotos = emptyList())
}

fun WorkspaceViewModel.clearPhotoNodeFilter() {
    _state.value = _state.value.copy(photoFilterNodeCode = null)
}

fun WorkspaceViewModel.getProgressUiState(): ProgressUiState = ensureIndexes().progressUi

fun WorkspaceViewModel.getDataHubUiState(): DataHubUiState = ensureIndexes().dataHubUi

fun WorkspaceViewModel.getSelectedNodeMaterialLines(): List<PreparedMaterialLine> {
    val stateSnapshot = _state.value
    val selectedNode = stateSnapshot.mapUi.selectedNode ?: return emptyList()
    val baseLines = ensureIndexes(stateSnapshot).parsedMaterialsByNodeKey[selectedNode.id].orEmpty()
    return baseLines.map { line ->
        line.copy(actualText = resolveMaterialActualText(stateSnapshot.workVolumeProgress, selectedNode, line.itemName))
    }
}

fun WorkspaceViewModel.getPreviewworkVolumeRows(previewNodeCode: String?): List<com.mapsupervision.reporting.ui.MaterialReportRow> {
    val stateSnapshot = _state.value
    val selectedNode = stateSnapshot.mapUi.selectedNode
    if (previewNodeCode == null || selectedNode == null || selectedNode.code != previewNodeCode) return emptyList()
    val rows = getSelectedNodeMaterialLines()
    if (rows.isEmpty()) return emptyList()
    val workVolumeRows = rows.map { line ->
        val actualQty = line.actualText.toFloatOrNull() ?: 0f
        com.mapsupervision.reporting.ui.MaterialReportRow(
            workName = line.itemName,
            totalPlannedQty = line.plannedQty,
            totalActualQty = actualQty,
            completionPercent = if (line.plannedQty <= 0f) 0f else (actualQty / line.plannedQty) * 100f
        )
    }
    val plannedSum = workVolumeRows.sumOf { it.totalPlannedQty.toDouble() }.toFloat()
    val actualSum = workVolumeRows.sumOf { it.totalActualQty.toDouble() }.toFloat()
    return workVolumeRows + com.mapsupervision.reporting.ui.MaterialReportRow(
        workName = "Tổng",
        totalPlannedQty = plannedSum,
        totalActualQty = actualSum,
        completionPercent = if (plannedSum <= 0f) 0f else (actualSum / plannedSum) * 100f,
        isTotal = true
    )
}

internal fun resolveCaptureTargetCode(mapUi: MapUiState): String? {
    val nodeCode = mapUi.selectedNode?.code?.trim().orEmpty()
    if (nodeCode.isNotBlank()) return nodeCode
    val routeCode = mapUi.selectedRoute?.code?.trim().orEmpty()
    return routeCode.takeIf { it.isNotBlank() }
}

fun WorkspaceViewModel.triggerCapture() {
    val mapUi = _state.value.mapUi
    val selectedNodeCode = mapUi.selectedNode?.code?.trim().orEmpty()
    val selectedRouteCode = mapUi.selectedRoute?.code?.trim().orEmpty()
    val targetCode = resolveCaptureTargetCode(mapUi)
    AppLogger.d(
        "capture.trigger request selectedNode=${selectedNodeCode.isNotBlank()} " +
            "selectedRoute=${selectedRouteCode.isNotBlank()} targetCode=${targetCode.orEmpty()}"
    )
    if (targetCode == null) {
        AppLogger.d("capture.trigger blocked reason=no_selection")
        showMessage("Hãy chọn một nút hoặc tuyến trước khi chụp ảnh")
        return
    }
    AppLogger.d("capture.trigger accepted targetCode=$targetCode")
    _state.value = _state.value.copy(
        pendingCaptureNodeCode = targetCode
    )
}

fun WorkspaceViewModel.clearCaptureRequest() {
    _state.value = _state.value.copy(pendingCaptureNodeCode = null)
}

suspend fun WorkspaceViewModel.savePhoto(file: java.io.File, nodeCode: String): Boolean {
    val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return false
    val targetCode = nodeCode.trim()
    AppLogger.d(
        "capture.save.start projectId=$projectId nodeCode=$targetCode file=${file.absolutePath}"
    )
    if (targetCode.isBlank()) {
        AppLogger.d("capture.save.blocked reason=blank_node_code file=${file.absolutePath}")
        showMessage("Không thể lưu ảnh khi chưa có mã đối tượng")
        return false
    }
    return try {
        val saved = withContext(Dispatchers.IO) {
            val loc = locationProvider.lastKnownLocation()
            AppLogger.d(
                "capture.save.io.start projectId=$projectId nodeCode=$targetCode file=${file.absolutePath}"
            )
            val storageRef = getProjectStorageRef(projectId)
            val thumb = photoPipelineService.createThumbnail(storageRef, file)
            AppLogger.d(
                "capture.save.thumbnail.ok projectId=$projectId nodeCode=$targetCode thumb=${thumb.absolutePath}"
            )
            val isRoute = _state.value.designRoutes.any { it.code == targetCode }
            val matchedNode = if (!isRoute) targetCode else null
            val matchedRoute = if (isRoute) targetCode else null
            val isVideo = file.name.endsWith(".mp4", ignoreCase = true)
            var durationMs = 0L
            if (isVideo) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    AppLogger.e(e, "capture.save.video.duration.fail file=${file.absolutePath}")
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
            val photo = createStoredSitePhoto(
                projectId = projectId,
                objectCode = targetCode,
                file = file,
                thumbnailFile = thumb,
                location = loc,
                engineer = "Field",
                matchedNodeCode = matchedNode,
                matchedRouteCode = matchedRoute,
                mediaType = if (isVideo) com.mapsupervision.domain.model.MediaType.VIDEO else com.mapsupervision.domain.model.MediaType.IMAGE,
                mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                durationMs = durationMs
            )
            val result = photoRepository.add(photo)
            if (result is AppResult.Error) {
                AppLogger.e(
                    result.throwable,
                    "capture.save.repository.fail projectId=$projectId nodeCode=$targetCode file=${file.absolutePath} thumb=${thumb.absolutePath}"
                )
                showMessage("Lưu ảnh thất bại: ${result.throwable.message ?: "không xác định"}")
                false
            } else {
                AppLogger.d(
                    "capture.save.repository.ok projectId=$projectId nodeCode=$targetCode file=${file.absolutePath} thumb=${thumb.absolutePath}"
                )
                true
            }
        }
        if (!saved) return false
        markProjectChanged(projectId, "photo_saved")
        // Increment counter so ReportingScreen knows to refresh
        _state.value = _state.value.copy(photoSaveCount = _state.value.photoSaveCount + 1)
        AppLogger.d(
            "capture.save.done projectId=$projectId nodeCode=$targetCode file=${file.absolutePath} photoSaveCount=${_state.value.photoSaveCount}"
        )
        true
    } catch (error: Throwable) {
        AppLogger.e(
            error,
            "capture.save.fail projectId=$projectId nodeCode=$targetCode file=${file.absolutePath}"
        )
        showMessage(error.message ?: "Không thể lưu ảnh chụp")
        false
    }
}

internal fun WorkspaceViewModel.ensureIndexes(state: WorkspaceState = _state.value): WorkspaceIndexes {
    synchronized(this) {
        if (
            cachedNodesRef !== state.designNodes ||
            cachedRoutesRef !== state.designRoutes ||
            cachedProgressRef !== state.constructionProgress ||
            cachedWorkVolumeRowsRef !== state.workVolumeRows ||
            cachedDailyLogsRef !== state.dailyLogs
        ) {
            cachedIndexes = buildWorkspaceIndexes(state)
            cachedNodesRef = state.designNodes
            cachedRoutesRef = state.designRoutes
            cachedProgressRef = state.constructionProgress
            cachedWorkVolumeRowsRef = state.workVolumeRows
            cachedDailyLogsRef = state.dailyLogs
        }
        return cachedIndexes
    }
}

private suspend fun WorkspaceViewModel.geocodeByNominatim(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&limit=1")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MapSupervision/1.0")
            connectTimeout = 5000
            readTimeout = 5000
        }
        connection.inputStream.bufferedReader().use { reader ->
            val payload = reader.readText()
            val array = JSONArray(payload)
            if (array.length() == 0) return@withContext null
            val item = array.getJSONObject(0)
            val lat = item.optString("lat").toDoubleOrNull() ?: return@withContext null
            val lon = item.optString("lon").toDoubleOrNull() ?: return@withContext null
            lat to lon
        }
    }.getOrNull()
}



internal fun retainRouteEndpointNodes(
    filteredNodes: List<GisNode>,
    filteredRoutes: List<GisRoute>,
    allNodes: List<GisNode>
): List<GisNode> {
    if (filteredRoutes.isEmpty() || allNodes.isEmpty()) return filteredNodes

    val nodesByCode = allNodes.associateBy { it.code.trim().uppercase() }
    val keptById = LinkedHashMap<String, GisNode>(filteredNodes.size + filteredRoutes.size * 2)
    filteredNodes.forEach { node ->
        keptById[node.id] = node
    }
    filteredRoutes.forEach { route ->
        nodesByCode[route.startNodeCode.trim().uppercase()]?.let { node ->
            keptById.putIfAbsent(node.id, node)
        }
        nodesByCode[route.endNodeCode.trim().uppercase()]?.let { node ->
            keptById.putIfAbsent(node.id, node)
        }
    }
    return keptById.values.toList()
}

internal fun buildMapDesignNodes(
    state: WorkspaceState,
    indexes: WorkspaceIndexes
): List<GisNode> {
    val mapUi = state.mapUi
    AppLogger.d("buildMapDesignNodes: filterContractor=${mapUi.filterContractor}, filterMaterialType=${mapUi.filterMaterialType}, searchQuery='${mapUi.searchQuery}', showNodes=${mapUi.showNodes}")
    AppLogger.d("buildMapDesignNodes: total designNodes=${state.designNodes.size}, total designRoutes=${state.designRoutes.size}")
    
    if (mapUi.filterContractor.isNullOrBlank() && mapUi.filterMaterialType.isNullOrBlank() && mapUi.searchQuery.isBlank() && mapUi.hiddenContractors.isEmpty()) {
        AppLogger.d("buildMapDesignNodes: No filter, returning all ${state.designNodes.size} nodes")
        return state.designNodes
    }

    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeMapSearchText(mapUi.searchQuery)
    val lowerMaterialType = mapUi.filterMaterialType?.trim()?.lowercase()

    val filteredNodes = state.designNodes.filter { node ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            node.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byVisibility = !isContractorHidden(mapUi, node.contractor)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedNodeSearch[node.id]?.matches(normalizedQuery) == true
        val byMaterial = lowerMaterialType.isNullOrBlank() || run {
            val nodeMaterials = (indexes.parsedMaterialsByNodeKey[node.id].orEmpty().map { it.itemName } +
                                 indexes.workVolumeRowsByNodeKey[node.id].orEmpty().map { it.workName })
                                 .map { it.trim().lowercase() }
            nodeMaterials.contains(lowerMaterialType)
        }
        byContractor && byVisibility && byQuery && byMaterial
    }

    AppLogger.d("buildMapDesignNodes: final result nodes=${filteredNodes.size}")
    return filteredNodes
}

internal fun normalizeMapSearchText(text: String): String {
    val stripped = java.text.Normalizer
        .normalize(text.lowercase(java.util.Locale.US), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped
        .replace("đ", "d")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun WorkspaceViewModel.collectSummaryProperties(startSummary: String?, endSummary: String?): List<Pair<String, String>> {
    val map = linkedMapOf<String, String>()
    sequenceOf(startSummary, endSummary)
        .filterNotNull()
        .flatMap { it.split('\n').asSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val key = line.substringBefore(':').trim()
            val value = line.substringAfter(':', "").trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                map.putIfAbsent(key, value)
            }
        }
    return map.entries.map { it.key to it.value }
}

internal data class MediaStorageSpec(
    val mediaType: com.mapsupervision.domain.model.MediaType,
    val mimeType: String,
    val durationMs: Long
)

internal fun resolveMediaStorageSpec(
    file: java.io.File,
    sourceMimeType: String? = null
): MediaStorageSpec {
    val normalizedMimeType = sourceMimeType?.trim().orEmpty()
    val isVideo = normalizedMimeType.startsWith("video/") || file.name.endsWith(".mp4", ignoreCase = true)
    val durationMs = if (isVideo) extractVideoDurationMs(file) else 0L
    val resolvedMimeType = when {
        normalizedMimeType.isNotBlank() -> normalizedMimeType
        isVideo -> "video/mp4"
        else -> "image/jpeg"
    }
    return MediaStorageSpec(
        mediaType = if (isVideo) com.mapsupervision.domain.model.MediaType.VIDEO else com.mapsupervision.domain.model.MediaType.IMAGE,
        mimeType = resolvedMimeType,
        durationMs = durationMs
    )
}

internal fun normalizeMediaObjectCode(
    objectCode: String,
    nodes: List<GisNode>,
    routes: List<GisRoute>
): String {
    val normalized = objectCode.trim()
    if (normalized.isBlank()) return ""
    nodes.firstOrNull { it.code.equals(normalized, ignoreCase = true) }?.let { return it.code }
    routes.firstOrNull { it.code.equals(normalized, ignoreCase = true) }?.let { return it.code }
    return normalized
}

internal fun extractVideoDurationMs(file: java.io.File): Long {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
    } catch (e: Exception) {
        AppLogger.e(e, "media.duration.fail file=${file.absolutePath}")
        0L
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

fun WorkspaceViewModel.importMediaFromGallery(
    uris: List<Uri>,
    objectCode: String,
    engineer: String
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val normalizedObjectCode = normalizeMediaObjectCode(objectCode, _state.value.designNodes, _state.value.designRoutes)
        if (normalizedObjectCode.isBlank()) {
            showMessage("Vui lòng nhập mã đối tượng trước khi chọn media")
            return@launch
        }

        val folderType = if (_state.value.designRoutes.any { it.code == normalizedObjectCode }) {
            CaptureFolderType.ROUTE
        } else {
            CaptureFolderType.NODE
        }

        var savedCount = 0
        withContext(Dispatchers.IO) {
            val storageRef = getProjectStorageRef(projectId)
            uris.forEach { uri ->
                runCatching {
                    val sourceMimeType = context.contentResolver.getType(uri)
                    val file = photoPipelineService.importFromGallery(
                        storageRef = storageRef,
                        capturedAt = System.currentTimeMillis(),
                        locationLabel = null,
                        note = null,
                        folderType = folderType,
                        objectCode = normalizedObjectCode,
                        sourceUri = uri.toString()
                    )
                    saveImportedMedia(projectId, normalizedObjectCode, engineer, file, sourceMimeType)
                    savedCount++
                }.onFailure { error ->
                    AppLogger.e(error, "media.import.gallery.fail projectId=$projectId uri=$uri")
                }
            }
        }

        if (savedCount > 0) {
            markProjectChanged(projectId, "gallery_media_imported")
            _state.value = _state.value.copy(photoSaveCount = _state.value.photoSaveCount + savedCount)
        }
        refresh()
    }
}

fun WorkspaceViewModel.updateSitePhoto(
    photoId: String,
    tagCodesCsv: String,
    matchedNodeCode: String?,
    lat: Double?,
    lon: Double?
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val photosResult = photoRepository.byProject(projectId)
        val existingPhoto = (photosResult as? AppResult.Success)?.data?.find { it.id == photoId }
        
        if (existingPhoto == null) {
            _state.value = _state.value.copy(
                importUi = _state.value.importUi.copy(
                    message = "Lỗi: Không tìm thấy ảnh $photoId để cập nhật."
                )
            )
            return@launch
        }
        
        val normalizedNodeCode = matchedNodeCode?.let { findBestMatchingNodeCode(it, _state.value.designNodes) }
        
        val updatedPhoto = existingPhoto.copy(
            tagCodesCsv = tagCodesCsv,
            matchedNodeCode = normalizedNodeCode ?: existingPhoto.matchedNodeCode,
            objectCode = normalizedNodeCode ?: existingPhoto.objectCode,
            latitude = lat ?: existingPhoto.latitude,
            longitude = lon ?: existingPhoto.longitude,
            matchedAtEpochMs = System.currentTimeMillis()
        )
        
        val result = photoRepository.add(updatedPhoto)
        if (result is AppResult.Error) {
            _state.value = _state.value.copy(
                importUi = _state.value.importUi.copy(
                    message = "Lỗi cập nhật ảnh: ${result.throwable.message}"
                )
            )
            return@launch
        }
        
        markProjectChanged(projectId, "photo_updated")
        _state.value = _state.value.copy(
            importUi = _state.value.importUi.copy(message = "Đã cập nhật ảnh $photoId thành công.")
        )
    }
}

private suspend fun WorkspaceViewModel.saveImportedMedia(
    projectId: String,
    objectCode: String,
    engineer: String,
    file: java.io.File,
    sourceMimeType: String?
) {
    val location = locationProvider.lastKnownLocation()
    val storageRef = getProjectStorageRef(projectId)
    val thumb = photoPipelineService.createThumbnail(storageRef, file)
    val isRoute = _state.value.designRoutes.any { it.code == objectCode }
    val matchedNode = if (!isRoute) objectCode else null
    val matchedRoute = if (isRoute) objectCode else null
    val spec = resolveMediaStorageSpec(file, sourceMimeType)
    val photo = createStoredSitePhoto(
        projectId = projectId,
        objectCode = objectCode,
        file = file,
        thumbnailFile = thumb,
        location = location,
        engineer = engineer,
        matchedNodeCode = matchedNode,
        matchedRouteCode = matchedRoute,
        mediaType = spec.mediaType,
        mimeType = spec.mimeType,
        durationMs = spec.durationMs
    )
    val result = photoRepository.add(photo)
    if (result is AppResult.Error) {
        throw IllegalStateException(result.throwable.message ?: "Failed to save media")
    }
}

fun WorkspaceViewModel.saveReportDraft(
    title: String,
    executiveSummary: String,
    riskSection: String,
    recommendedActions: List<String>
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val draft = com.mapsupervision.domain.model.ReportDraft(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            executiveSummary = executiveSummary,
            riskSection = riskSection,
            recommendedActions = recommendedActions,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val result = reportDraftRepository.add(draft)
        if (result is AppResult.Error) {
            _state.value = _state.value.copy(
                importUi = _state.value.importUi.copy(
                    message = "Lỗi lưu bản nháp báo cáo: ${result.throwable.message}"
                )
            )
            return@launch
        }
        
        markProjectChanged(projectId, "report_draft_saved")
        _state.value = _state.value.copy(
            importUi = _state.value.importUi.copy(message = "Đã lưu bản nháp báo cáo thành công.")
        )
    }
}

fun WorkspaceViewModel.addDailyLogBatch(
    workItem: String,
    manpower: Int,
    note: String,
    weather: String = "",
    temperature: Double = 0.0,
    nodeCodes: List<String> = emptyList(),
    dateEpochDay: Long = 0L,
    volume: Double = 0.0,
    unit: String = "",
    categoryName: String = ""
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val now = System.currentTimeMillis()

        val categoryMatch = if (categoryName.isNotBlank()) {
            findBestMatchingCategory(categoryName, _state.value.workCategories, _state.value.workVolumeRows)
        } else null
        val normalizedCategory = categoryMatch?.first ?: categoryName
        val normalizedUnit = categoryMatch?.second ?: unit

        val normalizedNodeCodes = nodeCodes.map { findBestMatchingNodeCode(it, _state.value.designNodes) }

        if (normalizedCategory.isNotBlank() && volume > 0.0) {
            for (nodeCode in normalizedNodeCodes) {
                if (nodeCode.isBlank()) continue
                val node = ensureIndexes().nodesByCode[nodeCode]
                val plannedVolume = extractPlannedQty(node, normalizedCategory)
                val existingMaterials = _state.value.workVolumeRows
                val existing = existingMaterials.firstOrNull {
                    (it.nodeCode == nodeCode || ensureIndexes().nodesById[it.nodeCode]?.code == nodeCode) &&
                        it.workName.equals(normalizedCategory, ignoreCase = true)
                }
                val currentActual = volume.toFloat()
        val newMaterial = WorkVolumeProgress(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    projectId = projectId,
                    nodeCode = nodeCode,
                    workName = existing?.workName ?: normalizedCategory,
                    plannedQty = if (existing != null && existing.plannedQty > 0f) existing.plannedQty else plannedVolume,
                    actualQty = currentActual,
                    updatedAtEpochMs = now,
                    unit = normalizedUnit
                )
                workVolumeProgressRepository.upsert(newMaterial)

                val calculatedProgress = if (newMaterial.plannedQty > 0f) {
                    (newMaterial.actualQty / newMaterial.plannedQty * 100f).coerceIn(0f, 100f)
                } else {
                    100f
                }
                val existingProgress = _state.value.constructionProgress.firstOrNull { it.nodeCode == nodeCode }
                addConstructionProgress(nodeCode, existingProgress?.planned ?: 100f, calculatedProgress)
            }
        }

        val finalLogId = UUID.randomUUID().toString()
        val persistedLines = normalizedNodeCodes
            .filter { it.isNotBlank() }
            .map { nodeCode ->
                com.mapsupervision.domain.model.DailyLogLine(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    dailyLogId = finalLogId,
                    lineType = com.mapsupervision.domain.model.DailyLogLineType.EXTRA,
                    workName = workItem,
                    categoryName = normalizedCategory,
                    quantity = volume,
                    unit = normalizedUnit,
                    nodeCode = nodeCode,
                    linkedWorkPlanId = null,
                    nodeId = ensureIndexes().nodesByCode[nodeCode]?.id,
                    routeId = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            }

        val log = com.mapsupervision.domain.model.DailyLog(
            id = finalLogId,
            projectId = projectId,
            workItem = workItem,
            manpower = manpower,
            note = note,
            createdAtEpochMs = now,
            weather = weather,
            temperature = temperature,
            nodeCode = normalizedNodeCodes.firstOrNull()?.takeIf { it.isNotBlank() },
            dateEpochDay = dateEpochDay,
            volume = volume,
            unit = normalizedUnit,
            categoryName = normalizedCategory,
            batchGroupId = UUID.randomUUID().toString(),
            appliedNodeCodesCsv = normalizedNodeCodes.filter { it.isNotBlank() }.joinToString(","),
            lines = persistedLines
        )
        val result = dailyLogRepository.add(log)
        if (result is AppResult.Error) {
            AppLogger.d("dailylog.addBatch.error project=$projectId msg=${result.throwable.message}")
            return@launch
        }
        markProjectChanged(projectId, "daily_log_batch_added")
    }
}

fun WorkspaceViewModel.addWorkPlan(
    plannedDateEpochDay: Long,
    title: String,
    description: String,
    nodeCode: String?,
    routeCode: String?,
    taskId: String?,
    sourceRawInput: String
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val normalizedNodeCode = nodeCode?.let { findBestMatchingNodeCode(it, _state.value.designNodes) }
        val normalizedRouteCode = routeCode?.let { findBestMatchingRouteCode(it, _state.value.designRoutes) }
        val workPlan = com.mapsupervision.domain.model.WorkPlan(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            description = description,
            plannedDateEpochDay = plannedDateEpochDay,
            nodeCode = normalizedNodeCode,
            routeCode = normalizedRouteCode,
            taskId = taskId,
            sourceRawInput = sourceRawInput,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val result = workPlanRepository.add(workPlan)
        if (result is AppResult.Success) {
            markProjectChanged(projectId, "work_plan_added")
        }
    }
}

fun WorkspaceViewModel.addWorkPlanWithTask(
    plannedDateEpochDay: Long,
    title: String,
    description: String,
    nodeCode: String?,
    routeCode: String?,
    sourceRawInput: String
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val normalizedNodeCode = nodeCode?.let { findBestMatchingNodeCode(it, _state.value.designNodes) }
        val normalizedRouteCode = routeCode?.let { findBestMatchingRouteCode(it, _state.value.designRoutes) }
        val generatedTaskId = java.util.UUID.randomUUID().toString()
        
        // 1. Create task
        taskRepository.upsert(
            Task(
                id = generatedTaskId,
                projectId = projectId,
                objectCode = normalizedNodeCode ?: normalizedRouteCode ?: "",
                title = title,
                description = description,
                status = TaskStatus.TODO,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
        
        // 2. Create work plan
        val workPlan = com.mapsupervision.domain.model.WorkPlan(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            description = description,
            plannedDateEpochDay = plannedDateEpochDay,
            nodeCode = normalizedNodeCode,
            routeCode = normalizedRouteCode,
            taskId = generatedTaskId,
            sourceRawInput = sourceRawInput,
            createdAtEpochMs = System.currentTimeMillis()
        )
        
        val result = workPlanRepository.add(workPlan)
        if (result is AppResult.Success) {
            markProjectChanged(projectId, "work_plan_added")
        }
        
        // Reload notes and tasks to update the UI
        val targetCode = normalizedNodeCode ?: normalizedRouteCode
        if (targetCode != null) {
            loadNotesAndTasks(targetCode)
        }
    }
}

private suspend fun WorkspaceViewModel.getProjectStorageRef(projectId: String): com.mapsupervision.domain.model.ProjectStorageRef {
    val projects = (projectRepository.list(true) as? AppResult.Success)?.data
    val project = projects?.find { it.id == projectId }
    val slug = project?.slug ?: projectId
    return com.mapsupervision.domain.model.ProjectStorageRef(id = projectId, slug = slug)
}

fun buildWorkPlanBatchLocations(
    nodeCodes: List<String>,
    routeCodes: List<String>
): List<Pair<String?, String?>> {
    val result = mutableListOf<Pair<String?, String?>>()
    for (node in nodeCodes) {
        result.add(node to null)
    }
    for (route in routeCodes) {
        result.add(null to route)
    }
    return result
}

fun buildWorkPlanBatchPlans(
    projectId: String,
    title: String,
    taskId: String?,
    note: String,
    dateEpochDay: Long,
    quantity: Double,
    unit: String,
    batchGroupId: String,
    createdAtEpochMs: Long,
    locations: List<Pair<String?, String?>>
): List<com.mapsupervision.domain.model.WorkPlan> {
    return locations.map { (nodeCode, routeCode) ->
        com.mapsupervision.domain.model.WorkPlan(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            description = note,
            plannedDateEpochDay = dateEpochDay,
            nodeCode = nodeCode,
            routeCode = routeCode,
            taskId = taskId,
            sourceRawInput = "",
            createdAtEpochMs = createdAtEpochMs,
            quantity = quantity,
            unit = unit,
            batchGroupId = batchGroupId
        )
    }
}

suspend fun WorkspaceViewModel.addWorkPlanBatch(
    workName: String,
    nodeCodes: List<String>,
    routeCodes: List<String>,
    qty: Double,
    unit: String,
    note: String,
    dateEpochDay: Long,
    taskId: String? = null
): Boolean {
    val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return false
    val batchGroupId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    
    val normalizedNodes = nodeCodes.map { findBestMatchingNodeCode(it, _state.value.designNodes) }
    val normalizedRoutes = routeCodes.map { findBestMatchingRouteCode(it, _state.value.designRoutes) }
    
    val locations = buildWorkPlanBatchLocations(normalizedNodes, normalizedRoutes)
    val plans = buildWorkPlanBatchPlans(
        projectId = projectId,
        title = workName,
        taskId = taskId,
        note = note,
        dateEpochDay = dateEpochDay,
        quantity = qty,
        unit = unit,
        batchGroupId = batchGroupId,
        createdAtEpochMs = now,
        locations = locations
    )
    
    var success = true
    for (plan in plans) {
        val res = workPlanRepository.add(plan)
        if (res is AppResult.Error) {
            success = false
        }
    }
    
    if (plans.isNotEmpty() && success) {
        markProjectChanged(projectId, "work_plans_batch_added")
    }
    
    return success
}

fun WorkspaceViewModel.importSharedMedia(
    projectId: String,
    objectCode: String,
    uris: List<Uri>
) {
    viewModelScope.launch {
        val normalizedObjectCode = normalizeMediaObjectCode(objectCode, _state.value.designNodes, _state.value.designRoutes)
        if (normalizedObjectCode.isBlank()) {
            showMessage("Vui lòng nhập mã đối tượng trước khi chọn media")
            return@launch
        }

        val folderType = if (_state.value.designRoutes.any { it.code == normalizedObjectCode }) {
            CaptureFolderType.ROUTE
        } else {
            CaptureFolderType.NODE
        }

        var savedCount = 0
        withContext(Dispatchers.IO) {
            val storageRef = getProjectStorageRef(projectId)
            uris.forEach { uri ->
                runCatching {
                    val sourceMimeType = context.contentResolver.getType(uri)
                    val file = photoPipelineService.importFromGallery(
                        storageRef = storageRef,
                        capturedAt = System.currentTimeMillis(),
                        locationLabel = null,
                        note = null,
                        folderType = folderType,
                        objectCode = normalizedObjectCode,
                        sourceUri = uri.toString()
                    )
                    saveImportedMedia(projectId, normalizedObjectCode, "SharedImport", file, sourceMimeType)
                    savedCount++
                }.onFailure { error ->
                    AppLogger.e(error, "media.import.shared.fail projectId=$projectId uri=$uri")
                }
            }
        }

        if (savedCount > 0) {
            markProjectChanged(projectId, "shared_media_imported")
            _state.value = _state.value.copy(photoSaveCount = _state.value.photoSaveCount + savedCount)
        }
        refresh()
    }
}

fun WorkspaceViewModel.updateNodeSignalStatus(node: GisNode, newStatus: NodeSignalStatus) {
    viewModelScope.launch {
        val updatedNode = node.copy(signalStatus = newStatus)
        gisRepository.upsertNode(updatedNode)
        val currentUi = _state.value.mapUi
        if (currentUi.selectedNode?.code == node.code) {
            _state.value = _state.value.copy(
                mapUi = currentUi.copy(
                    selectedNode = updatedNode,
                    signalStatus = newStatus
                )
            )
        }
        val projectId = _state.value.activeProjectId
        if (projectId != null) {
            markProjectChanged(projectId, "node_signal_status_updated")
        }
    }
}


