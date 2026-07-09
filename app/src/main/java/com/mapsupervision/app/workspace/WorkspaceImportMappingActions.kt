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
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.storage.ProjectStorageManager
import com.mapsupervision.storage.importer.UserFileImportService
import com.mapsupervision.storage.importer.ConfirmedFieldFlags
import com.mapsupervision.storage.importer.ExcelColumnMapping
import com.mapsupervision.storage.importer.ExcelClassificationMode
import com.mapsupervision.storage.importer.ImportedFileDraft
import com.mapsupervision.storage.importer.NonExcelFieldCandidateSet
import com.mapsupervision.storage.importer.NonExcelImportMapping
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.MapLayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.roundToLong
fun WorkspaceViewModel.loadExcelPreview(uri: Uri, existingFileId: String? = null, sheetName: String? = null) {
    viewModelScope.launch {
        AppLogger.d("import.mapping.flow loadExcelPreview.start uri=$uri existingFileId=$existingFileId sheetName=$sheetName")
        _state.value = _state.value.copy(
            excelParserUi = _state.value.excelParserUi.copy(
                isLoading = true,
                message = "Đang đọc cấu trúc Excel..."
            )
        )
        runCatching {
            withContext(Dispatchers.IO) { 
                val preview = importService.inspectExcel(uri.toString(), sheetName)
                
                var pos = preview.suggestedMapping?.positionColumn ?: preview.headers.firstOrNull().orEmpty()
                var lat = preview.suggestedMapping?.latitudeColumn.orEmpty()
                var lon = preview.suggestedMapping?.longitudeColumn.orEmpty()
                var contractor = preview.suggestedMapping?.contractorColumn.orEmpty()
                var items = preview.suggestedMapping?.itemColumns.orEmpty()
                
                try {
                    val mappedRows = preview.sampleRows.take(5).map { row -> preview.headers.map { h -> row[h] ?: "" } }
                    val ai = aiOrchestrator.execute<com.mapsupervision.domain.ai.ImportMappingResult>(
                        ImportMappingPayload(headers = preview.headers, sampleRows = mappedRows, fileType = "xlsx")
                    )
                    val aiResult = ai.result
                    if (aiResult.nodeCodeColumn.isNotBlank() && preview.headers.contains(aiResult.nodeCodeColumn)) {
                        pos = aiResult.nodeCodeColumn
                    }
                    if (aiResult.latitudeColumn.isNotBlank() && preview.headers.contains(aiResult.latitudeColumn)) {
                        lat = aiResult.latitudeColumn
                    }
                    if (aiResult.longitudeColumn.isNotBlank() && preview.headers.contains(aiResult.longitudeColumn)) {
                        lon = aiResult.longitudeColumn
                    }
                    if (aiResult.contractorColumn.isNotBlank() && preview.headers.contains(aiResult.contractorColumn)) {
                        contractor = aiResult.contractorColumn
                    }
                    val validItems = aiResult.itemColumns.filter { preview.headers.contains(it) }
                    if (validItems.isNotEmpty()) {
                        items = validItems
                    }
                    val aiMessage = when {
                        aiResult.requiresManualReview -> "AI cần bạn xác nhận thủ công vì confidence mapping thấp."
                        ai.source == AiDecisionSource.MODEL && ai.confidence >= 70 -> "Đã dùng AI model gợi ý."
                        ai.source == AiDecisionSource.MODEL -> "AI model confidence thấp, nên kiểm tra mapping thủ công."
                        ai.source == AiDecisionSource.FALLBACK -> "AI offline/fallback rule đang được dùng."
                        else -> "AI đang tắt theo feature flag, dùng rule mặc định."
                    }
                    AppLogger.d(
                        "ai.importMapping source=${ai.source} confidence=${ai.confidence} reason=${ai.reason} " +
                            "latencyMs=${ai.latencyMs} warnings=${ai.warnings.joinToString("|")}"
                    )
                    _state.value = _state.value.copy(
                        excelParserUi = _state.value.excelParserUi.copy(message = aiMessage)
                    )
                } catch(e: Exception) {
                    AppLogger.e(e, "AI mapping failed")
                }
                
                preview.copy(
                    suggestedMapping = preview.suggestedMapping?.copy(
                        positionColumn = pos,
                        latitudeColumn = lat,
                        longitudeColumn = lon,
                        contractorColumn = contractor,
                        itemColumns = items
                    ) ?: com.mapsupervision.storage.importer.ExcelColumnMapping(
                        positionColumn = pos,
                        latitudeColumn = lat,
                        longitudeColumn = lon,
                        contractorColumn = contractor,
                        itemColumns = items,
                        classificationMode = com.mapsupervision.storage.importer.ExcelClassificationMode.AUTO
                    )
                )
            }
        }.onSuccess { preview ->
            val headers = preview.headers
            val suggested = preview.suggestedMapping
            val suggestedPosition = suggested?.positionColumn ?: headers.firstOrNull().orEmpty()
            val suggestedCoord = suggested?.coordinateColumn.orEmpty()
            val suggestedLat = suggested?.latitudeColumn.orEmpty()
            val suggestedLon = suggested?.longitudeColumn.orEmpty()
            val suggestedContractor = suggested?.contractorColumn.orEmpty()
            val suggestedMapNumber = suggested?.mapNumberColumn ?: suggestedPosition
            val suggestedObjectType = suggested?.objectTypeColumn.orEmpty()
            val suggestedIpAddress = suggested?.ipAddressColumn.orEmpty()
            val suggestedSubnet = suggested?.subnetColumn.orEmpty()
            val suggestedGateway = suggested?.gatewayColumn.orEmpty()
            val suggestedSignalStatus = suggested?.signalStatusColumn.orEmpty()
            val suggestedFiberCoreCount = suggested?.fiberCoreCountColumn.orEmpty()
            val suggestedFiberConnection = suggested?.fiberConnectionColumn.orEmpty()
            val suggestedItems = suggested?.itemColumns.orEmpty()
            val confidence = preview.suggestedMappingConfidence
            val confidenceLabel = when {
                confidence >= 80 -> "cao"
                confidence >= 60 -> "trung bình"
                confidence > 0 -> "thấp"
                else -> "không có"
            }
            AppLogger.d(
                "import.mapping.flow loadExcelPreview.success headers=${preview.headers.size} " +
                    "showMappingDialog=true file=${preview.fileName}"
            )
            _state.value = _state.value.copy(
                excelParserUi = ExcelParserUiState(
                    sourceUri = uri,
                    sourceFileName = preview.fileName,
                    existingFileId = existingFileId,
                    headers = preview.headers,
                    sampleRows = preview.sampleRows,
                    positionColumn = suggestedPosition,
                    coordinateColumn = suggestedCoord,
                    latitudeColumn = suggestedLat,
                    longitudeColumn = suggestedLon,
                    contractorColumn = suggestedContractor,
                    mapNumberColumn = suggestedMapNumber,
                    objectTypeColumn = suggestedObjectType,
                    ipAddressColumn = suggestedIpAddress,
                    subnetColumn = suggestedSubnet,
                    gatewayColumn = suggestedGateway,
                    signalStatusColumn = suggestedSignalStatus,
                    fiberCoreCountColumn = suggestedFiberCoreCount,
                    fiberConnectionColumn = suggestedFiberConnection,
                    useTwoColumnCoordinates = suggestedLat.isNotBlank() && suggestedLon.isNotBlank(),
                    showMappingDialog = true,
                    workVolumeColumnsCsv = suggestedItems.joinToString(","),
                    suggestedItemColumns = suggestedItems,
                    message = " cột. Đã dùng AI gợi ý. Chọn mapping rồi bấm Parse Excel.",
                    sheets = preview.sheets,
                    selectedSheet = sheetName ?: preview.sheets.firstOrNull().orEmpty()
                )
            )
            AppLogger.d(
                "import.mapping.flow loadExcelPreview.stateApplied showMappingDialog=${_state.value.excelParserUi.showMappingDialog} " +
                    "message=${_state.value.excelParserUi.message}"
            )
        }.onFailure { ex ->
            AppLogger.e(ex, "import.mapping.flow loadExcelPreview.failure uri=$uri sheetName=$sheetName")
            _state.value = _state.value.copy(
                excelParserUi = _state.value.excelParserUi.copy(
                    isLoading = false,
                    message = "Không đọc được Excel: ${ex.message}"
                )
            )
        }
    }
}

fun WorkspaceViewModel.updateSelectedExcelSheet(sheetName: String) {
    val ui = _state.value.excelParserUi
    val uri = ui.sourceUri ?: return
    loadExcelPreview(uri, ui.existingFileId, sheetName)
}

fun WorkspaceViewModel.repairImportedGeometry(file: ImportedFile) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId?.takeUnless { it.isBlank() }
        val ext = file.fileType.lowercase(java.util.Locale.US)
        if (projectId == null || ext !in setOf("kml", "kmz", "geojson", "json")) {
            showMessage("File này không hỗ trợ nhập lại geometry.")
            return@launch
        }

        val sourceFile = File(file.storedPath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            showMessage("Không tìm thấy file nguồn để nhập lại geometry.")
            return@launch
        }

        _state.value = _state.value.copy(
            importMappingUi = _state.value.importMappingUi.copy(
                isLoading = true,
                message = "Đang nhập lại geometry..."
            )
        )

        runCatching {
            withContext(Dispatchers.IO) {
                importService.importNonExcelWithMapping(
                    projectId = projectId,
                    uri = Uri.fromFile(sourceFile).toString(),
                    mapping = NonExcelImportMapping(positionField = "Tự sinh mã"),
                    confirmed = ConfirmedFieldFlags(positionField = true)
                )
            }
        }.onSuccess { draft ->
            val repairedNodes = draft.suggestedNodes.map { it.copy(projectId = projectId, importedFileId = file.id) }
            val repairedRoutes = draft.suggestedRoutes.map { it.copy(projectId = projectId, importedFileId = file.id) }
            when (val result = gisRepository.replaceImportedGeometry(file.id, repairedNodes, repairedRoutes)) {
                is AppResult.Error -> {
                    _state.value = _state.value.copy(
                        importMappingUi = _state.value.importMappingUi.copy(
                            isLoading = false,
                            message = "Nhập lại geometry thất bại: ${result.throwable.message}"
                        )
                    )
                    showMessage("Nhập lại geometry thất bại.")
                }
                is AppResult.Success -> {
                    val stateSnapshot = _state.value
                    val updatedNodes = stateSnapshot.designNodes.filter { it.importedFileId != file.id } + repairedNodes
                    val updatedRoutes = stateSnapshot.designRoutes.filter { it.importedFileId != file.id } + repairedRoutes
                    _state.value = stateSnapshot.copy(
                        designNodes = updatedNodes,
                        designRoutes = updatedRoutes,
                        importMappingUi = stateSnapshot.importMappingUi.copy(
                            isLoading = false,
                            message = "Đã nhập lại geometry: +${repairedNodes.size} node, +${repairedRoutes.size} tuyến"
                        ),
                        dashboard = buildDashboard(
                            updatedNodes,
                            updatedRoutes,
                            stateSnapshot.constructionProgress,
                            stateSnapshot.workVolumeRows
                        )
                    )
                    updateFilteredMapData()
                    if (updatedNodes.isNotEmpty() || updatedRoutes.isNotEmpty()) {
                        GisMapBridgeRegistry.bridge?.fitToObjects()
                    }
                    markProjectChanged(projectId, "design_geometry_repaired")
                    showMessage("Đã nhập lại geometry cho ${file.fileName}.")
                }
            }
        }.onFailure { ex ->
            AppLogger.e(ex, "repairImportedGeometry failed fileId=${file.id}")
            _state.value = _state.value.copy(
                importMappingUi = _state.value.importMappingUi.copy(
                    isLoading = false,
                    message = "Nhập lại geometry thất bại: ${ex.message}"
                )
            )
            showMessage("Nhập lại geometry thất bại.")
        }
    }
}

fun WorkspaceViewModel.loadNonExcelPreview(uri: Uri, existingFileId: String? = null) {
    viewModelScope.launch {
        AppLogger.d("import.mapping.flow loadNonExcelPreview.start uri=$uri existingFileId=$existingFileId")
        _state.value = _state.value.copy(
            importMappingUi = _state.value.importMappingUi.copy(
                sourceUri = uri,
                sourceType = "non_excel",
                isLoading = true,
                message = "Đang đọc metadata file..."
            )
        )
        runCatching {
            withContext(Dispatchers.IO) { importService.inspectNonExcelFields(uri.toString()) }
        }.onSuccess { preview ->
            var suggestedPosition = preview.candidates.positionOptions.firstOrNull().orEmpty()
            var suggestedCoordinate = preview.candidates.coordinateOptions.firstOrNull().orEmpty()
            var suggestedContractor = preview.candidates.contractorOptions.firstOrNull().orEmpty()
            var suggestedMapNumber = preview.candidates.mapNumberOptions.firstOrNull().orEmpty()
            var suggestedObjectType = preview.candidates.objectTypeOptions.firstOrNull().orEmpty()
            var suggestedItems = preview.candidates.itemOptions
            var suggestedRouteLength = preview.candidates.routeLengthOptions.firstOrNull().orEmpty()
            val suggestedIpAddress = preview.candidates.ipAddressOptions.firstOrNull().orEmpty()
            val suggestedSubnet = preview.candidates.subnetOptions.firstOrNull().orEmpty()
            val suggestedGateway = preview.candidates.gatewayOptions.firstOrNull().orEmpty()
            val suggestedSignalStatus = preview.candidates.signalStatusOptions.firstOrNull().orEmpty()
            val suggestedFiberCoreCount = preview.candidates.fiberCoreCountOptions.firstOrNull().orEmpty()
            val suggestedFiberConnection = preview.candidates.fiberConnectionOptions.firstOrNull().orEmpty()
            var aiMessage = "Đã đọc metadata non-Excel. Vui lòng xác nhận ánh xạ."
            runCatching {
                val headers = buildList {
                    addAll(preview.candidates.positionOptions)
                    addAll(preview.candidates.coordinateOptions)
                    addAll(preview.candidates.latitudeOptions)
                    addAll(preview.candidates.longitudeOptions)
                    addAll(preview.candidates.contractorOptions)
                    addAll(preview.candidates.mapNumberOptions)
                    addAll(preview.candidates.objectTypeOptions)
                    addAll(preview.candidates.itemOptions)
                    addAll(preview.candidates.routeLengthOptions)
                    addAll(preview.candidates.ipAddressOptions)
                    addAll(preview.candidates.subnetOptions)
                    addAll(preview.candidates.gatewayOptions)
                    addAll(preview.candidates.signalStatusOptions)
                    addAll(preview.candidates.fiberCoreCountOptions)
                    addAll(preview.candidates.fiberConnectionOptions)
                }.distinct()
                val mappedRows = preview.sampleRows.map { row ->
                    headers.map { h -> row[h] ?: "" }
                }
                val ai = aiOrchestrator.execute<com.mapsupervision.domain.ai.ImportMappingResult>(
                    ImportMappingPayload(
                        headers = headers,
                        sampleRows = mappedRows.ifEmpty { listOf(listOf(preview.summary)) },
                        fileType = preview.fileType.lowercase()
                    )
                )
                val aiResult = ai.result
                if (aiResult.nodeCodeColumn.isNotBlank() && headers.contains(aiResult.nodeCodeColumn)) {
                    suggestedPosition = aiResult.nodeCodeColumn
                }
                if (aiResult.latitudeColumn.isNotBlank() || aiResult.longitudeColumn.isNotBlank()) {
                    suggestedCoordinate = "Geometry coordinates"
                }
                if (aiResult.contractorColumn.isNotBlank() && headers.contains(aiResult.contractorColumn)) {
                    suggestedContractor = aiResult.contractorColumn
                }
                val validItems = aiResult.itemColumns.filter { headers.contains(it) }
                if (validItems.isNotEmpty()) {
                    suggestedItems = validItems
                }
                aiMessage = "Đã dùng AI gợi ý ánh xạ non-Excel. Vui lòng xác nhận vị trí."
            }
            AppLogger.d(
                "import.mapping.flow loadNonExcelPreview.success type=${preview.fileType} " +
                    "showMappingDialog=true file=${preview.fileName}"
            )
            _state.value = _state.value.copy(
                importMappingUi = ImportMappingUiState(
                    sourceUri = uri,
                    sourceFileName = preview.fileName,
                    existingFileId = existingFileId,
                    sourceType = preview.fileType.lowercase(),
                    candidates = preview.candidates,
                    positionField = suggestedPosition,
                    coordinateField = suggestedCoordinate,
                    contractorField = suggestedContractor,
                    mapNumberField = suggestedMapNumber,
                    objectTypeField = suggestedObjectType,
                    workVolumeFieldsCsv = suggestedItems.joinToString(","),
                    routeLengthField = suggestedRouteLength,
                    ipAddressField = suggestedIpAddress,
                    subnetField = suggestedSubnet,
                    gatewayField = suggestedGateway,
                    signalStatusField = suggestedSignalStatus,
                    fiberCoreCountField = suggestedFiberCoreCount,
                    fiberConnectionField = suggestedFiberConnection,
                    confirmedPositionField = suggestedPosition.isNotBlank(),
                    confirmedCoordinateField = suggestedCoordinate.isNotBlank(),
                    // Non-Excel optional fields must be explicitly confirmed by user.
                    // Avoid auto-confirming "UPLOAD" contractor, which can cause coord-only
                    // dedup conflicts and duplicate overlays against existing Excel data.
                    confirmedContractorField = false,
                    confirmedMapNumberField = false,
                    confirmedObjectTypeField = false,
                    confirmedWorkVolumeFields = false,
                    confirmedRouteLengthField = false,
                    confirmedIpAddressField = false,
                    confirmedSubnetField = false,
                    confirmedGatewayField = false,
                    confirmedSignalStatusField = false,
                    confirmedFiberCoreCountField = false,
                    confirmedFiberConnectionField = false,
                    showMappingDialog = true,
                    isLoading = false,
                    message = aiMessage
                )
            )
            AppLogger.d(
                "import.mapping.flow loadNonExcelPreview.stateApplied showMappingDialog=${_state.value.importMappingUi.showMappingDialog} " +
                    "message=${_state.value.importMappingUi.message}"
            )
        }.onFailure { ex ->
            AppLogger.e(ex, "import.mapping.flow loadNonExcelPreview.failure uri=$uri")
            _state.value = _state.value.copy(
                importMappingUi = _state.value.importMappingUi.copy(
                    isLoading = false,
                    showMappingDialog = false,
                    message = "Không đọc được metadata non-Excel: "
                )
            )
        }
    }
}

fun WorkspaceViewModel.updateImportMappingUi(
    positionField: String? = null,
    coordinateField: String? = null,
    contractorField: String? = null,
    mapNumberField: String? = null,
    objectTypeField: String? = null,
    workVolumeFieldsCsv: String? = null,
    routeLengthField: String? = null,
    ipAddressField: String? = null,
    subnetField: String? = null,
    gatewayField: String? = null,
    signalStatusField: String? = null,
    fiberCoreCountField: String? = null,
    fiberConnectionField: String? = null,
    confirmedPositionField: Boolean? = null,
    confirmedCoordinateField: Boolean? = null,
    confirmedContractorField: Boolean? = null,
    confirmedMapNumberField: Boolean? = null,
    confirmedObjectTypeField: Boolean? = null,
    confirmedWorkVolumeFields: Boolean? = null,
    confirmedRouteLengthField: Boolean? = null,
    confirmedIpAddressField: Boolean? = null,
    confirmedSubnetField: Boolean? = null,
    confirmedGatewayField: Boolean? = null,
    confirmedSignalStatusField: Boolean? = null,
    confirmedFiberCoreCountField: Boolean? = null,
    confirmedFiberConnectionField: Boolean? = null
) {
    updateImportMappingUiIfChanged { ui ->
        ui.copy(
            positionField = positionField ?: ui.positionField,
            coordinateField = coordinateField ?: ui.coordinateField,
            contractorField = contractorField ?: ui.contractorField,
            mapNumberField = mapNumberField ?: ui.mapNumberField,
            objectTypeField = objectTypeField ?: ui.objectTypeField,
            workVolumeFieldsCsv = workVolumeFieldsCsv ?: ui.workVolumeFieldsCsv,
            routeLengthField = routeLengthField ?: ui.routeLengthField,
            ipAddressField = ipAddressField ?: ui.ipAddressField,
            subnetField = subnetField ?: ui.subnetField,
            gatewayField = gatewayField ?: ui.gatewayField,
            signalStatusField = signalStatusField ?: ui.signalStatusField,
            fiberCoreCountField = fiberCoreCountField ?: ui.fiberCoreCountField,
            fiberConnectionField = fiberConnectionField ?: ui.fiberConnectionField,
            confirmedPositionField = confirmedPositionField ?: ui.confirmedPositionField,
            confirmedCoordinateField = confirmedCoordinateField ?: ui.confirmedCoordinateField,
            confirmedContractorField = confirmedContractorField ?: ui.confirmedContractorField,
            confirmedMapNumberField = confirmedMapNumberField ?: ui.confirmedMapNumberField,
            confirmedObjectTypeField = confirmedObjectTypeField ?: ui.confirmedObjectTypeField,
            confirmedWorkVolumeFields = confirmedWorkVolumeFields ?: ui.confirmedWorkVolumeFields,
            confirmedRouteLengthField = confirmedRouteLengthField ?: ui.confirmedRouteLengthField,
            confirmedIpAddressField = confirmedIpAddressField ?: ui.confirmedIpAddressField,
            confirmedSubnetField = confirmedSubnetField ?: ui.confirmedSubnetField,
            confirmedGatewayField = confirmedGatewayField ?: ui.confirmedGatewayField,
            confirmedSignalStatusField = confirmedSignalStatusField ?: ui.confirmedSignalStatusField,
            confirmedFiberCoreCountField = confirmedFiberCoreCountField ?: ui.confirmedFiberCoreCountField,
            confirmedFiberConnectionField = confirmedFiberConnectionField ?: ui.confirmedFiberConnectionField
        )
    }
}

fun WorkspaceViewModel.setImportMappingDialogVisible(visible: Boolean) {
    updateImportMappingUiIfChanged { ui -> ui.copy(showMappingDialog = visible) }
}

private fun WorkspaceViewModel.updateImportMappingUiIfChanged(transform: (ImportMappingUiState) -> ImportMappingUiState) {
    val state = _state.value
    val current = state.importMappingUi
    val updated = transform(current)
    if (updated == current) return
    _state.value = state.copy(importMappingUi = updated)
}

fun WorkspaceViewModel.parseNonExcelToDesign() {
    viewModelScope.launch {
        val ui = _state.value.importMappingUi
        val uri = ui.sourceUri
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        if (uri == null || projectId == null) {
            _state.value = _state.value.copy(
                importMappingUi = ui.copy(message = "Thiếu file Excel hoặc chưa chọn project active")
            )
            return@launch
        }
        if (!ui.confirmedPositionField) {
            _state.value = _state.value.copy(
                importMappingUi = ui.copy(message = "Bắt buộc xác nhận dữ liệu vị trí trước khi import")
            )
            return@launch
        }
        _state.value = _state.value.copy(importMappingUi = ui.copy(isLoading = true, message = "Đang import non-Excel..."))
        runCatching {
            withContext(Dispatchers.IO) {
                importService.importNonExcelWithMapping(
                    projectId = projectId,
                    uri = uri.toString(),
                    mapping = NonExcelImportMapping(
                        positionField = ui.positionField,
                        coordinateField = ui.coordinateField.ifBlank { null },
                        contractorField = ui.contractorField.ifBlank { null },
                        mapNumberField = ui.mapNumberField.ifBlank { null },
                        objectTypeField = ui.objectTypeField.ifBlank { null },
                        itemFields = parseworkVolumeColumnsCsv(ui.workVolumeFieldsCsv),
                        routeLengthField = ui.routeLengthField.ifBlank { null },
                        ipAddressField = ui.ipAddressField.ifBlank { null },
                        subnetField = ui.subnetField.ifBlank { null },
                        gatewayField = ui.gatewayField.ifBlank { null },
                        signalStatusField = ui.signalStatusField.ifBlank { null },
                        fiberCoreCountField = ui.fiberCoreCountField.ifBlank { null },
                        fiberConnectionField = ui.fiberConnectionField.ifBlank { null }
                    ),
                    confirmed = ConfirmedFieldFlags(
                        positionField = ui.confirmedPositionField,
                        coordinateField = ui.confirmedCoordinateField,
                        contractorField = ui.confirmedContractorField,
                        mapNumberField = ui.confirmedMapNumberField,
                        objectTypeField = ui.confirmedObjectTypeField,
                        itemFields = ui.confirmedWorkVolumeFields,
                        routeLengthField = ui.confirmedRouteLengthField,
                        ipAddressField = ui.confirmedIpAddressField,
                        subnetField = ui.confirmedSubnetField,
                        gatewayField = ui.confirmedGatewayField,
                        signalStatusField = ui.confirmedSignalStatusField,
                        fiberCoreCountField = ui.confirmedFiberCoreCountField,
                        fiberConnectionField = ui.confirmedFiberConnectionField
                    )
                )
            }
        }.onSuccess { draft ->
            val remapExistingFileId = _state.value.importMappingUi.existingFileId
            viewModelScope.launch {
                runCatching {
                    commitNonExcelImportDraft(projectId = projectId, draft = draft, existingFileId = remapExistingFileId)
                }.onFailure { ex ->
                    updateImportMappingUiIfChanged { ui ->
                        ui.copy(
                            isLoading = false,
                            message = "Import non-Excel thất bại: ${ex.message}"
                        )
                    }
                }
            }
            return@onSuccess
            // For KML/KMZ files, directly import without going through draft override
            val existingFileId = _state.value.importMappingUi.existingFileId
            if (existingFileId != null) {
                // Re-mapping an already-imported file: only update nodes/routes, no new ImportedFile
                runCatching {
                    val existingNodes = _state.value.designNodes.toMutableList()
                    val existingRoutes = _state.value.designRoutes.toMutableList()
                    val merged = withContext(Dispatchers.Default) {
                        deduplicateImportedGeometry(
                            projectId = projectId,
                            incomingNodes = draft.suggestedNodes.map { it.copy(importedFileId = existingFileId) },
                            incomingRoutes = draft.suggestedRoutes.map { it.copy(importedFileId = existingFileId) },
                            existingNodes = existingNodes,
                            existingRoutes = existingRoutes
                        )
                    }
                    gisRepository.upsertNodes(merged.nodesToInsert)
                    gisRepository.upsertRoutes(merged.routesToInsert)
                    existingNodes.addAll(merged.nodesToInsert)
                    existingRoutes.addAll(merged.routesToInsert)
                    updateImportMappingUiIfChanged { ui ->
                        ui.copy(
                            isLoading = false,
                            showMappingDialog = false,
                            message = "Đã cập nhật dữ liệu: +${merged.nodesToInsert.size} node, +${merged.routesToInsert.size} tuyến"
                        )
                    }
                }.onFailure { ex ->
                    updateImportMappingUiIfChanged { ui ->
                        ui.copy(
                            isLoading = false,
                            message = "Cập nhật dữ liệu thất bại: "
                        )
                    }
                }
            } else {
                val importedId = UUID.randomUUID().toString()
                val importedFile = ImportedFile(
                    id = importedId,
                    projectId = projectId,
                    fileName = draft.fileName,
                    fileType = draft.fileType,
                    storedPath = draft.storedPath,
                    summary = draft.summary,
                    importedAtEpochMs = System.currentTimeMillis()
                )
                val existingNodes = _state.value.designNodes.toMutableList()
                val existingRoutes = _state.value.designRoutes.toMutableList()
                val merged = withContext(Dispatchers.Default) {
                    deduplicateImportedGeometry(
                        projectId = projectId,
                        incomingNodes = draft.suggestedNodes.map { it.copy(importedFileId = importedId) },
                        incomingRoutes = draft.suggestedRoutes.map { it.copy(importedFileId = importedId) },
                        existingNodes = existingNodes,
                        existingRoutes = existingRoutes
                    )
                }
                importedFileRepository.upsert(importedFile)
                gisRepository.upsertNodes(merged.nodesToInsert)
                gisRepository.upsertRoutes(merged.routesToInsert)
                existingNodes.addAll(merged.nodesToInsert)
                existingRoutes.addAll(merged.routesToInsert)
                _state.value = _state.value.copy(
                    importMappingUi = _state.value.importMappingUi.copy(
                        sourceUri = null,
                        sourceFileName = "",
                        isLoading = false,
                        showMappingDialog = false,
                        message = "Đã nhập dữ liệu: +${merged.nodesToInsert.size} node, +${merged.routesToInsert.size} tuyến"
                    ),
                    importedFiles = _state.value.importedFiles + importedFile,
                    designNodes = existingNodes,
                    designRoutes = existingRoutes,
                    dashboard = buildDashboard(
                        existingNodes,
                        existingRoutes,
                        _state.value.constructionProgress,
                        emptyList()
                    )
                )
            }
        }.onFailure { ex ->
            updateImportMappingUiIfChanged { ui ->
                ui.copy(
                    isLoading = false,
                    message = "Import non-Excel thất bại: "
                )
            }
        }
    }
}

fun WorkspaceViewModel.updateExcelMapping(
    positionColumn: String? = null,
    coordinateColumn: String? = null,
    latitudeColumn: String? = null,
    longitudeColumn: String? = null,
    contractorColumn: String? = null,
    mapNumberColumn: String? = null,
    objectTypeColumn: String? = null,
    ipAddressColumn: String? = null,
    subnetColumn: String? = null,
    gatewayColumn: String? = null,
    signalStatusColumn: String? = null,
    fiberCoreCountColumn: String? = null,
    fiberConnectionColumn: String? = null,
    workVolumeColumnsCsv: String? = null
) {
    updateExcelParserUiIfChanged { ui ->
        ui.copy(
            positionColumn = positionColumn ?: ui.positionColumn,
            coordinateColumn = coordinateColumn ?: ui.coordinateColumn,
            latitudeColumn = latitudeColumn ?: ui.latitudeColumn,
            longitudeColumn = longitudeColumn ?: ui.longitudeColumn,
            contractorColumn = contractorColumn ?: ui.contractorColumn,
            mapNumberColumn = mapNumberColumn ?: ui.mapNumberColumn,
            objectTypeColumn = objectTypeColumn ?: ui.objectTypeColumn,
            ipAddressColumn = ipAddressColumn ?: ui.ipAddressColumn,
            subnetColumn = subnetColumn ?: ui.subnetColumn,
            gatewayColumn = gatewayColumn ?: ui.gatewayColumn,
            signalStatusColumn = signalStatusColumn ?: ui.signalStatusColumn,
            fiberCoreCountColumn = fiberCoreCountColumn ?: ui.fiberCoreCountColumn,
            fiberConnectionColumn = fiberConnectionColumn ?: ui.fiberConnectionColumn,
            workVolumeColumnsCsv = workVolumeColumnsCsv ?: ui.workVolumeColumnsCsv
        )
    }
}

fun WorkspaceViewModel.updateExcelClassificationMode(mode: ExcelClassificationMode) {
    updateExcelParserUiIfChanged { ui -> ui.copy(classificationMode = mode) }
}

fun WorkspaceViewModel.setExcelMappingDialogVisible(visible: Boolean) {
    updateExcelParserUiIfChanged { ui -> ui.copy(showMappingDialog = visible) }
}

fun WorkspaceViewModel.updateExcelCoordinateMode(useTwoColumn: Boolean) {
    updateExcelParserUiIfChanged { ui -> ui.copy(useTwoColumnCoordinates = useTwoColumn) }
}

fun WorkspaceViewModel.updateMapVisualOptions(showNumberOnMap: Boolean? = null, colorByContractorOnMap: Boolean? = null) {
    updateExcelParserUiIfChanged { ui ->
        ui.copy(
            showNumberOnMap = showNumberOnMap ?: ui.showNumberOnMap,
            colorByContractorOnMap = colorByContractorOnMap ?: ui.colorByContractorOnMap
        )
    }
}

private fun WorkspaceViewModel.updateExcelParserUiIfChanged(transform: (ExcelParserUiState) -> ExcelParserUiState) {
    val state = _state.value
    val current = state.excelParserUi
    val updated = transform(current)
    if (updated == current) return
    _state.value = state.copy(excelParserUi = updated)
}

fun WorkspaceViewModel.parseExcelToDesign() {
    viewModelScope.launch {
        val ui = _state.value.excelParserUi
        val uri = ui.sourceUri
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        if (uri == null || projectId == null) {
            _state.value = _state.value.copy(
                excelParserUi = ui.copy(message = "Thiếu file Excel hoặc chưa chọn project active")
            )
            return@launch
        }
        _state.value = _state.value.copy(
            excelParserUi = ui.copy(isLoading = true, message = "Đang parse Excel...")
        )

        runCatching {
            withContext(Dispatchers.IO) {
                importService.importExcelWithMapping(
                    projectId = projectId,
                    uri = uri.toString(),
                    mapping = ExcelColumnMapping(
                        positionColumn = ui.positionColumn,
                        coordinateColumn = if (ui.useTwoColumnCoordinates) null else ui.coordinateColumn.ifBlank { null },
                        latitudeColumn = if (ui.useTwoColumnCoordinates) ui.latitudeColumn.ifBlank { null } else null,
                        longitudeColumn = if (ui.useTwoColumnCoordinates) ui.longitudeColumn.ifBlank { null } else null,
                        contractorColumn = ui.contractorColumn.ifBlank { null },
                        mapNumberColumn = ui.mapNumberColumn.ifBlank { null },
                        objectTypeColumn = ui.objectTypeColumn.ifBlank { null },
                        ipAddressColumn = ui.ipAddressColumn.ifBlank { null },
                        subnetColumn = ui.subnetColumn.ifBlank { null },
                        gatewayColumn = ui.gatewayColumn.ifBlank { null },
                        signalStatusColumn = ui.signalStatusColumn.ifBlank { null },
                        fiberCoreCountColumn = ui.fiberCoreCountColumn.ifBlank { null },
                        fiberConnectionColumn = ui.fiberConnectionColumn.ifBlank { null },
                        classificationMode = ui.classificationMode,
                        itemColumns = parseworkVolumeColumnsCsv(ui.workVolumeColumnsCsv)
                    ),
                    sheetName = ui.selectedSheet.takeIf { it.isNotBlank() }
                )
            }
        }.onSuccess { draft ->
            val remapExistingFileId = ui.existingFileId
            viewModelScope.launch {
                runCatching {
                    commitExcelImportDraft(
                        projectId = projectId,
                        draft = draft,
                        existingFileId = remapExistingFileId,
                        excelUi = ui
                    )
                }.onFailure { ex ->
                    _state.value = _state.value.copy(
                        excelParserUi = ui.copy(
                            isLoading = false,
                            message = "Parse Excel thất bại: ${ex.message}"
                        )
                    )
                }
            }
            return@onSuccess
            val existingFileId = ui.existingFileId
            val importedId = existingFileId ?: UUID.randomUUID().toString()
            val importedFile = if (existingFileId == null) {
                ImportedFile(
                    id = importedId,
                    projectId = projectId,
                    fileName = draft.fileName,
                    fileType = draft.fileType,
                    storedPath = draft.storedPath,
                    summary = draft.summary,
                    importedAtEpochMs = System.currentTimeMillis()
                ).also { importedFileRepository.upsert(it) }
            } else {
                null
            }
            val existingNodes = _state.value.designNodes.toMutableList()
            val existingRoutes = _state.value.designRoutes.toMutableList()
            val merged = withContext(Dispatchers.Default) {
                deduplicateImportedGeometry(
                    projectId = projectId,
                    incomingNodes = draft.suggestedNodes.map { it.copy(importedFileId = importedId) },
                    incomingRoutes = draft.suggestedRoutes.map { it.copy(importedFileId = importedId) },
                    existingNodes = existingNodes,
                    existingRoutes = existingRoutes
                )
            }
            gisRepository.upsertNodes(merged.nodesToInsert)
            gisRepository.upsertRoutes(merged.routesToInsert)
            existingNodes.addAll(merged.nodesToInsert)
            existingRoutes.addAll(merged.routesToInsert)
            val quality = dedupQualitySnapshot(
                incomingNodes = draft.suggestedNodes.size,
                strongMatches = merged.stats.strongMatches,
                weakMatches = merged.stats.weakMatches,
                coordOnlyRejected = merged.stats.coordOnlyRejected,
                incomingRoutes = draft.suggestedRoutes.size,
                skippedSelfRoutes = merged.stats.skippedSelfRoutes,
                skippedDuplicateRoutes = merged.stats.skippedDuplicateRoutes
            )
            val excelRiskSummary = DedupRiskSummaryFormatter.summarizeSingleRisk(quality.risk)
            val excelRiskByFile = DedupRiskSummaryFormatter.format(excelRiskSummary)
            val excelBatchBundle = DedupBatchDecisionAdvisor.bundleFromSummaryText(excelRiskByFile)
            AppLogger.d(
                "dedup.excel inNodes=${draft.suggestedNodes.size} newNodes=${merged.nodesToInsert.size} " +
                    "dupNodes=${merged.duplicateNodes} matchCode=${merged.stats.codeMatches} " +
                    "matchName=${merged.stats.nameMatches} matchCoord=${merged.stats.coordMatches} " +
                    "matchMulti=${merged.stats.multiSignalMatches} matchStrong=${merged.stats.strongMatches} " +
                    "matchWeak=${merged.stats.weakMatches} coordOnlyRejected=${merged.stats.coordOnlyRejected} " +
                    "score=${quality.score}/100 risk=${quality.risk} action=${quality.action} note=${quality.actionNote} " +
                    "$excelRiskByFile batchDecision=${excelBatchBundle.decision} batchPriority=${excelBatchBundle.priority} " +
                    "diag=[${quality.diagnostics}] " +
                    "newRoutes=${merged.routesToInsert.size} " +
                    "skipSelfRoute=${merged.stats.skippedSelfRoutes} skipDupRoute=${merged.stats.skippedDuplicateRoutes}"
            )
            _state.value = _state.value.copy(
                excelParserUi = _state.value.excelParserUi.copy(
                    sourceUri = null,
                    sourceFileName = "",
                    headers = emptyList(),
                    sampleRows = emptyList(),
                    isLoading = false,
                    showMappingDialog = false,
                    message = run {
                        val quality = dedupQualitySnapshot(
                            incomingNodes = draft.suggestedNodes.size,
                            strongMatches = merged.stats.strongMatches,
                            weakMatches = merged.stats.weakMatches,
                            coordOnlyRejected = merged.stats.coordOnlyRejected,
                            incomingRoutes = draft.suggestedRoutes.size,
                            skippedSelfRoutes = merged.stats.skippedSelfRoutes,
                            skippedDuplicateRoutes = merged.stats.skippedDuplicateRoutes
                        )
                        val riskSummary = DedupRiskSummaryFormatter.summarize(
                            high = if (quality.risk == "high") 1 else 0,
                            medium = if (quality.risk == "medium") 1 else 0,
                            low = if (quality.risk == "low") 1 else 0
                        )
                        val riskByFile = DedupRiskSummaryFormatter.format(riskSummary)
                        val batchBundle = DedupBatchDecisionAdvisor.bundleFromSummaryText(riskByFile)
                        "Excel parsed: +${merged.nodesToInsert.size} node, +${merged.routesToInsert.size} tuyến, trùng ${merged.duplicateNodes} node, " +
                            DedupAiSummaryFormatter.format(
                                score = quality.score,
                                label = quality.label,
                                risk = quality.risk,
                                action = quality.action,
                                actionNote = quality.actionNote,
                                diagnostics = quality.diagnostics,
                                riskByFile = riskByFile,
                                batchDecision = batchBundle.decision,
                                batchPriority = batchBundle.priority,
                                batchNote = batchBundle.note,
                                hint = quality.hint
                            )
                    }
                ),
                importedFiles = if (importedFile != null) _state.value.importedFiles + importedFile else _state.value.importedFiles,
                designNodes = existingNodes,
                designRoutes = existingRoutes,
                dashboard = buildDashboard(
                    existingNodes,
                    existingRoutes,
                    _state.value.constructionProgress,
                    emptyList()
                )
            )
        }.onFailure { ex ->
            _state.value = _state.value.copy(
                excelParserUi = _state.value.excelParserUi.copy(
                    isLoading = false,
                    message = "Parse Excel thất bại: "
                )
            )
        }
    }
}


internal fun WorkspaceViewModel.deduplicateImportedGeometry(
    projectId: String,
    incomingNodes: List<GisNode>,
    incomingRoutes: List<GisRoute>,
    existingNodes: List<GisNode>,
    existingRoutes: List<GisRoute>
): MergeResult {
    val result = WorkspaceImportHelper.deduplicateImportedGeometry(projectId, incomingNodes, incomingRoutes, existingNodes, existingRoutes)
    return MergeResult(
        nodesToInsert = result.nodesToInsert,
        routesToInsert = result.routesToInsert,
        duplicateNodes = result.duplicateNodes,
        stats = DedupStats(
            codeMatches = result.stats.codeMatches,
            nameMatches = result.stats.nameMatches,
            coordMatches = result.stats.coordMatches,
            multiSignalMatches = result.stats.multiSignalMatches,
            strongMatches = result.stats.strongMatches,
            weakMatches = result.stats.weakMatches,
            coordOnlyRejected = result.stats.coordOnlyRejected,
            skippedSelfRoutes = result.stats.skippedSelfRoutes,
            skippedDuplicateRoutes = result.stats.skippedDuplicateRoutes
        )
    )
}

internal fun WorkspaceViewModel.buildRouteKeySet(routes: List<GisRoute>): MutableSet<String> {
    val set = HashSet<String>(routes.size * 2 + 1)
    val normalizedCodeCache = HashMap<String, String>(routes.size * 2 + 1)
    fun normalized(value: String): String =
        normalizedCodeCache.getOrPut(value) { normalizeCode(value) }
    for (route in routes) {
        val start = normalized(route.startNodeCode)
        val end = normalized(route.endNodeCode)
        set += routeKeyNormalized(start, end)
    }
    return set
}

internal fun WorkspaceViewModel.deduplicateWithIndexes(
    projectId: String,
    incomingNodes: List<GisNode>,
    incomingRoutes: List<GisRoute>,
    nodeByCode: MutableMap<String, GisNode>,
    nodeByName: MutableMap<String, GisNode>,
    nodeByCoord: MutableMap<Long, GisNode>,
    codeAlias: MutableMap<String, String>,
    existingRouteKeys: MutableSet<String>
): MergeResult {
    val result = WorkspaceImportHelper.deduplicateWithIndexes(
        projectId, incomingNodes, incomingRoutes, nodeByCode, nodeByName, nodeByCoord, codeAlias, existingRouteKeys
    )
    return MergeResult(
        nodesToInsert = result.nodesToInsert,
        routesToInsert = result.routesToInsert,
        duplicateNodes = result.duplicateNodes,
        stats = DedupStats(
            codeMatches = result.stats.codeMatches,
            nameMatches = result.stats.nameMatches,
            coordMatches = result.stats.coordMatches,
            multiSignalMatches = result.stats.multiSignalMatches,
            strongMatches = result.stats.strongMatches,
            weakMatches = result.stats.weakMatches,
            coordOnlyRejected = result.stats.coordOnlyRejected,
            skippedSelfRoutes = result.stats.skippedSelfRoutes,
            skippedDuplicateRoutes = result.stats.skippedDuplicateRoutes
        )
    )
}

internal fun WorkspaceViewModel.parseworkVolumeColumnsCsv(csv: String): List<String> {
    if (csv.isBlank()) return emptyList()
    val result = ArrayList<String>(8)
    var start = 0
    for (i in csv.indices) {
        if (csv[i] != ',') continue
        val token = csv.substring(start, i).trim()
        if (token.isNotEmpty()) result.add(token)
        start = i + 1
    }
    val tail = csv.substring(start).trim()
    if (tail.isNotEmpty()) result.add(tail)
    return result
}

internal fun WorkspaceViewModel.normalizeCode(code: String): String = com.mapsupervision.domain.ai.CanonicalTextNormalizer.normalizeCode(code)
internal fun WorkspaceViewModel.normalizeName(name: String): String = com.mapsupervision.domain.ai.CanonicalTextNormalizer.normalizeName(name)
internal fun WorkspaceViewModel.coordBucketKey(lat: Double, lon: Double): Long = WorkspaceImportHelper.coordBucketKey(lat, lon)
internal fun WorkspaceViewModel.routeKey(startCode: String, endCode: String): String = WorkspaceImportHelper.routeKey(startCode, endCode)
internal fun WorkspaceViewModel.routeKeyNormalized(a: String, b: String): String = WorkspaceImportHelper.routeKeyNormalized(a, b)

internal fun WorkspaceViewModel.dedupQualityScore(
    incomingNodes: Int,
    strongMatches: Int,
    weakMatches: Int,
    coordOnlyRejected: Int,
    incomingRoutes: Int,
    skippedSelfRoutes: Int,
    skippedDuplicateRoutes: Int
): Int {
    return WorkspaceImportHelper.dedupQualityScore(
        incomingNodes, strongMatches, weakMatches, coordOnlyRejected, incomingRoutes, skippedSelfRoutes, skippedDuplicateRoutes
    )
}

internal fun WorkspaceViewModel.dedupQualitySnapshot(
    incomingNodes: Int,
    strongMatches: Int,
    weakMatches: Int,
    coordOnlyRejected: Int,
    incomingRoutes: Int,
    skippedSelfRoutes: Int,
    skippedDuplicateRoutes: Int
): DedupQualitySnapshot {
    val result = WorkspaceImportHelper.dedupQualitySnapshot(
        incomingNodes, strongMatches, weakMatches, coordOnlyRejected, incomingRoutes, skippedSelfRoutes, skippedDuplicateRoutes
    )
    return DedupQualitySnapshot(
        score = result.score,
        label = result.label,
        risk = result.risk,
        action = result.action,
        actionNote = result.actionNote,
        diagnostics = result.diagnostics,
        hint = result.hint
    )
}

internal fun buildImportedGeometryMessage(
    newNodeCount: Int,
    newRouteCount: Int,
    replacingExistingFile: Boolean
): String {
    return when {
        newNodeCount == 0 && newRouteCount == 0 ->
            "Đã đọc dữ liệu, nhưng tất cả bản ghi đã khớp với bản đồ hiện có nên không tạo thêm đối tượng mới."
        replacingExistingFile ->
            "Đã cập nhật dữ liệu: +$newNodeCount node, +$newRouteCount tuyến"
        else ->
            "Đã nhập dữ liệu: +$newNodeCount node, +$newRouteCount tuyến"
    }
}

private suspend fun WorkspaceViewModel.commitNonExcelImportDraft(
    projectId: String,
    draft: ImportedFileDraft,
    existingFileId: String?
) {
    val stateSnapshot = _state.value
    val importedId = existingFileId ?: UUID.randomUUID().toString()
    val importedFile = if (existingFileId == null) {
        ImportedFile(
            id = importedId,
            projectId = projectId,
            fileName = draft.fileName,
            fileType = draft.fileType,
            storedPath = draft.storedPath,
            summary = draft.summary,
            importedAtEpochMs = System.currentTimeMillis()
        )
    } else {
        null
    }
    val baseNodes = if (existingFileId != null) {
        stateSnapshot.designNodes.filter { it.importedFileId != existingFileId }.toMutableList()
    } else {
        stateSnapshot.designNodes.toMutableList()
    }
    val baseRoutes = if (existingFileId != null) {
        stateSnapshot.designRoutes.filter { it.importedFileId != existingFileId }.toMutableList()
    } else {
        stateSnapshot.designRoutes.toMutableList()
    }
    val merged = withContext(Dispatchers.Default) {
        deduplicateImportedGeometry(
            projectId = projectId,
            incomingNodes = draft.suggestedNodes.map { it.copy(importedFileId = importedId) },
            incomingRoutes = draft.suggestedRoutes.map { it.copy(importedFileId = importedId) },
            existingNodes = baseNodes,
            existingRoutes = baseRoutes
        )
    }

    val saveResult = if (existingFileId != null) {
        gisRepository.replaceImportedGeometry(
            importedFileId = existingFileId,
            nodes = merged.nodesToInsert,
            routes = merged.routesToInsert
        )
    } else {
        if (importedFile != null) {
            when (val importedFileResult = importedFileRepository.upsert(importedFile)) {
                is AppResult.Error -> {
                    updateImportMappingUiIfChanged { ui ->
                        ui.copy(
                            isLoading = false,
                            message = "Import non-Excel thất bại: ${importedFileResult.throwable.message}"
                        )
                    }
                    return
                }
                is AppResult.Success -> Unit
            }
        }
        when (val nodesResult = gisRepository.upsertNodes(merged.nodesToInsert)) {
            is AppResult.Error -> {
                updateImportMappingUiIfChanged { ui ->
                    ui.copy(
                        isLoading = false,
                        message = "Import non-Excel thất bại: ${nodesResult.throwable.message}"
                    )
                }
                return
            }
            is AppResult.Success -> Unit
        }
        when (val routesResult = gisRepository.upsertRoutes(merged.routesToInsert)) {
            is AppResult.Error -> {
                updateImportMappingUiIfChanged { ui ->
                    ui.copy(
                        isLoading = false,
                        message = "Import non-Excel thất bại: ${routesResult.throwable.message}"
                    )
                }
                return
            }
            is AppResult.Success -> Unit
        }
        AppResult.Success(Unit)
    }

    if (saveResult is AppResult.Error) {
        updateImportMappingUiIfChanged { ui ->
            ui.copy(
                isLoading = false,
                message = "Import non-Excel thất bại: ${saveResult.throwable.message}"
            )
        }
        return
    }

    val updatedNodes = baseNodes + merged.nodesToInsert
    val updatedRoutes = baseRoutes + merged.routesToInsert
    _state.value = stateSnapshot.copy(
        mapUi = stateSnapshot.mapUi.copy(
            filterContractor = null,
            searchQuery = "",
            message = ""
        ),
        importMappingUi = stateSnapshot.importMappingUi.copy(
            sourceUri = null,
            sourceFileName = "",
            isLoading = false,
            showMappingDialog = false,
            message = buildImportedGeometryMessage(
                newNodeCount = merged.nodesToInsert.size,
                newRouteCount = merged.routesToInsert.size,
                replacingExistingFile = existingFileId != null
            )
        ),
        designNodes = updatedNodes,
        designRoutes = updatedRoutes,
        dashboard = buildDashboard(
            updatedNodes,
            updatedRoutes,
            stateSnapshot.constructionProgress,
            stateSnapshot.workVolumeRows
        )
    )
    updateFilteredMapData()
    if (updatedNodes.isNotEmpty() || updatedRoutes.isNotEmpty()) {
        GisMapBridgeRegistry.bridge?.fitToObjects()
    }
    markProjectChanged(projectId, if (existingFileId != null) "design_import_remapped" else "design_import_completed")
}

private suspend fun WorkspaceViewModel.commitExcelImportDraft(
    projectId: String,
    draft: ImportedFileDraft,
    existingFileId: String?,
    excelUi: ExcelParserUiState
) {
    val stateSnapshot = _state.value
    val importedId = existingFileId ?: UUID.randomUUID().toString()
    val importedFile = if (existingFileId == null) {
        ImportedFile(
            id = importedId,
            projectId = projectId,
            fileName = draft.fileName,
            fileType = draft.fileType,
            storedPath = draft.storedPath,
            summary = draft.summary,
            importedAtEpochMs = System.currentTimeMillis()
        )
    } else {
        null
    }
    val baseNodes = if (existingFileId != null) {
        stateSnapshot.designNodes.filter { it.importedFileId != existingFileId }.toMutableList()
    } else {
        stateSnapshot.designNodes.toMutableList()
    }
    val baseRoutes = if (existingFileId != null) {
        stateSnapshot.designRoutes.filter { it.importedFileId != existingFileId }.toMutableList()
    } else {
        stateSnapshot.designRoutes.toMutableList()
    }
    val merged = withContext(Dispatchers.Default) {
        deduplicateImportedGeometry(
            projectId = projectId,
            incomingNodes = draft.suggestedNodes.map { it.copy(importedFileId = importedId) },
            incomingRoutes = draft.suggestedRoutes.map { it.copy(importedFileId = importedId) },
            existingNodes = baseNodes,
            existingRoutes = baseRoutes
        )
    }

    val saveResult = if (existingFileId != null) {
        gisRepository.replaceImportedGeometry(
            importedFileId = existingFileId,
            nodes = merged.nodesToInsert,
            routes = merged.routesToInsert
        )
    } else {
        if (importedFile != null) {
            when (val importedFileResult = importedFileRepository.upsert(importedFile)) {
                is AppResult.Error -> {
                    _state.value = stateSnapshot.copy(
                        excelParserUi = excelUi.copy(
                            isLoading = false,
                            message = "Parse Excel thất bại: ${importedFileResult.throwable.message}"
                        )
                    )
                    return
                }
                is AppResult.Success -> Unit
            }
        }
        when (val nodesResult = gisRepository.upsertNodes(merged.nodesToInsert)) {
            is AppResult.Error -> {
                _state.value = stateSnapshot.copy(
                    excelParserUi = excelUi.copy(
                        isLoading = false,
                        message = "Parse Excel thất bại: ${nodesResult.throwable.message}"
                    )
                )
                return
            }
            is AppResult.Success -> Unit
        }
        when (val routesResult = gisRepository.upsertRoutes(merged.routesToInsert)) {
            is AppResult.Error -> {
                _state.value = stateSnapshot.copy(
                    excelParserUi = excelUi.copy(
                        isLoading = false,
                        message = "Parse Excel thất bại: ${routesResult.throwable.message}"
                    )
                )
                return
            }
            is AppResult.Success -> Unit
        }
        AppResult.Success(Unit)
    }

    if (saveResult is AppResult.Error) {
        _state.value = stateSnapshot.copy(
            excelParserUi = excelUi.copy(
                isLoading = false,
                message = "Parse Excel thất bại: ${saveResult.throwable.message}"
            )
        )
        return
    }

    val updatedNodes = baseNodes + merged.nodesToInsert
    val updatedRoutes = baseRoutes + merged.routesToInsert
    val quality = dedupQualitySnapshot(
        incomingNodes = draft.suggestedNodes.size,
        strongMatches = merged.stats.strongMatches,
        weakMatches = merged.stats.weakMatches,
        coordOnlyRejected = merged.stats.coordOnlyRejected,
        incomingRoutes = draft.suggestedRoutes.size,
        skippedSelfRoutes = merged.stats.skippedSelfRoutes,
        skippedDuplicateRoutes = merged.stats.skippedDuplicateRoutes
    )
    val riskSummary = DedupRiskSummaryFormatter.summarizeSingleRisk(quality.risk)
    val riskByFile = DedupRiskSummaryFormatter.format(riskSummary)
    val batchBundle = DedupBatchDecisionAdvisor.bundleFromSummaryText(riskByFile)
    AppLogger.d(
        "dedup.excel inNodes=${draft.suggestedNodes.size} newNodes=${merged.nodesToInsert.size} " +
            "dupNodes=${merged.duplicateNodes} matchCode=${merged.stats.codeMatches} " +
            "matchName=${merged.stats.nameMatches} matchCoord=${merged.stats.coordMatches} " +
            "matchMulti=${merged.stats.multiSignalMatches} matchStrong=${merged.stats.strongMatches} " +
            "matchWeak=${merged.stats.weakMatches} coordOnlyRejected=${merged.stats.coordOnlyRejected} " +
            "score=${quality.score}/100 risk=${quality.risk} action=${quality.action} note=${quality.actionNote} " +
            "$riskByFile batchDecision=${batchBundle.decision} batchPriority=${batchBundle.priority} " +
            "diag=[${quality.diagnostics}] " +
            "newRoutes=${merged.routesToInsert.size} " +
            "skipSelfRoute=${merged.stats.skippedSelfRoutes} skipDupRoute=${merged.stats.skippedDuplicateRoutes}"
    )
    _state.value = stateSnapshot.copy(
        mapUi = stateSnapshot.mapUi.copy(
            filterContractor = null,
            searchQuery = "",
            message = ""
        ),
        excelParserUi = excelUi.copy(
            sourceUri = null,
            sourceFileName = "",
            headers = emptyList(),
            sampleRows = emptyList(),
            isLoading = false,
            showMappingDialog = false,
            message = buildImportedGeometryMessage(
                newNodeCount = merged.nodesToInsert.size,
                newRouteCount = merged.routesToInsert.size,
                replacingExistingFile = existingFileId != null
            )
        ),
        designNodes = updatedNodes,
        designRoutes = updatedRoutes,
        dashboard = buildDashboard(
            updatedNodes,
            updatedRoutes,
            stateSnapshot.constructionProgress,
            stateSnapshot.workVolumeRows
        )
    )
    updateFilteredMapData()
    if (updatedNodes.isNotEmpty() || updatedRoutes.isNotEmpty()) {
        GisMapBridgeRegistry.bridge?.fitToObjects()
    }
    markProjectChanged(projectId, if (existingFileId != null) "design_import_remapped" else "design_import_completed")
}


