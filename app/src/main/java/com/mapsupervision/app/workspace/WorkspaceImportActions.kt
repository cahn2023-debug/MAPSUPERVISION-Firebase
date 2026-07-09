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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.roundToLong

fun WorkspaceViewModel.retryFailedImports() {
    val uris = emptyList<android.net.Uri>()
    if (uris.isEmpty()) {
        _state.value = _state.value.copy(
            importUi = _state.value.importUi.copy(message = "Không có file lỗi để thử lại")
        )

        return
    }
    importDesignFiles(uris)
}

fun WorkspaceViewModel.onOpenPicker() {}
fun WorkspaceViewModel.onPickerEmpty() {}

fun WorkspaceViewModel.updateWorkVolumeProgress(nodeCode: String, workName: String, progress: String) {
    // Update in-memory state immediately for responsive UI
    val key = "${nodeCode}_${workName}"
    val stateSnapshot = _state.value
    val indexes = ensureIndexes(stateSnapshot)
    val node = indexes.nodesById[nodeCode] ?: indexes.nodesByCode[nodeCode]
    val current = stateSnapshot.workVolumeProgress.toMutableMap()
    current[key] = progress
    if (node != null) {
        current["${node.id}_${workName}"] = progress
        current["${node.code}_${workName}"] = progress
    }

    val updatedRows = stateSnapshot.workVolumeRows.toMutableList()
    val existingIndex = updatedRows.indexOfFirst { row ->
        (row.nodeCode == nodeCode || row.nodeCode == node?.id || row.nodeCode == node?.code) &&
            row.workName.equals(workName, ignoreCase = true)
    }
    val resolvedUnit = if (existingIndex >= 0) {
        updatedRows[existingIndex].unit
    } else {
        resolveWorkTemplateUnit(workName, stateSnapshot.workCategories, stateSnapshot.workVolumeRows)
    }
    val updatedRow = WorkVolumeProgress(
        id = if (existingIndex >= 0) updatedRows[existingIndex].id else UUID.randomUUID().toString(),
        projectId = stateSnapshot.activeProjectId.orEmpty(),
        nodeCode = nodeCode,
        workName = if (existingIndex >= 0) updatedRows[existingIndex].workName else workName,
        plannedQty = extractPlannedQty(node, workName),
        actualQty = progress.toFloatOrNull() ?: 0f,
        updatedAtEpochMs = System.currentTimeMillis(),
        unit = resolvedUnit
    )
    if (existingIndex >= 0) {
        updatedRows[existingIndex] = updatedRow
    } else {
        updatedRows += updatedRow
    }
    _state.value = stateSnapshot.copy(
        workVolumeProgress = current,
        workVolumeRows = updatedRows,
        dashboard = buildDashboard(stateSnapshot.designNodes, stateSnapshot.designRoutes, stateSnapshot.constructionProgress, updatedRows)
    )

    // Persist to DB (debounced to reduce write churn while typing)
    workVolumeProgressPersistJobs[key]?.cancel()
    workVolumeProgressPersistJobs[key] = viewModelScope.launch {
        delay(450)
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val actualQty = progress.toFloatOrNull() ?: 0f
        workVolumeProgressRepository.upsert(
            WorkVolumeProgress(
                id = updatedRow.id,
                projectId = projectId,
                nodeCode = nodeCode,
                workName = workName,
                plannedQty = extractPlannedQty(node, workName),
                actualQty = actualQty,
                updatedAtEpochMs = System.currentTimeMillis(),
                unit = updatedRow.unit
            )
        )
        markProjectChanged(projectId, "material_progress_updated")
    }
}

fun WorkspaceViewModel.deleteImportedFile(fileId: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId
        importedFileRepository.deleteById(fileId)
        if (projectId != null) {
            markProjectChanged(projectId, "imported_file_deleted")
        }
    }
}

fun WorkspaceViewModel.combineImportedFiles(
    file1: ImportedFile,
    file2: ImportedFile,
    mergedNodes: List<GisNode>,
    mergedRoutes: List<GisRoute>
) {
    viewModelScope.launch {
        val projectId = file1.projectId
        val mergedFileId = java.util.UUID.randomUUID().toString()
        
        val mergedFile = ImportedFile(
            id = mergedFileId,
            projectId = projectId,
            fileName = "Gộp: ${file1.fileName} & ${file2.fileName}",
            fileType = "xlsx",
            storedPath = "",
            summary = "Gộp từ ${file1.fileName} và ${file2.fileName}. Bao gồm ${mergedNodes.size} nút giao, ${mergedRoutes.size} tuyến.",
            importedAtEpochMs = System.currentTimeMillis()
        )
        
        val updatedNodes = mergedNodes.map { it.copy(importedFileId = mergedFileId) }
        val updatedRoutes = mergedRoutes.map { it.copy(importedFileId = mergedFileId) }
        
        importedFileRepository.upsert(mergedFile)
        gisRepository.upsertNodes(updatedNodes)
        gisRepository.upsertRoutes(updatedRoutes)
        
        importedFileRepository.deleteById(file1.id)
        importedFileRepository.deleteById(file2.id)
        markProjectChanged(projectId, "imported_files_combined")
    }
}


fun WorkspaceViewModel.importDesignFiles(uris: List<Uri>) {
    viewModelScope.launch {
        val startedAtMs = System.currentTimeMillis()
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        if (projectId == null) {
            _state.value = _state.value.copy(
                importUi = _state.value.importUi.copy(
                    status = ImportStatus.FAILED,
                    message = "Chưa có dự án active. Hãy mở menu ở tab Bản đồ và chọn Open project trước khi upload."
                )
            )
            return@launch
        }
        val baselineNodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        val baselineRoutes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        AppLogger.d(
            "import.baseline project=$projectId nodes=${baselineNodes.size} routes=${baselineRoutes.size} nodeSig=${identitySignature(baselineNodes.asSequence().map { it.id })} routeSig=${identitySignature(baselineRoutes.asSequence().map { it.id })}"
        )

        AppLogger.d("import.start project=$projectId uris=${uris.size}")
        _state.value = _state.value.copy(
            importUi = ImportUiState(
                status = ImportStatus.IMPORTING,
                totalFiles = uris.size,
                processedFiles = 0,
                successFiles = 0,
                failedFiles = 0,
                message = "Đang phân tích 0/${uris.size} file..."
            )
        )

        var imported = 0
        var failedCount = 0
        val firstFailures = ArrayList<String>(2)
        val recentFailures = ArrayDeque<String>(5)
        val recentRetryableFailures = ArrayDeque<ImportFailure>(20)
        val flushThreshold = 750
        val pendingImportedFileUpserts = ArrayList<ImportedFile>(flushThreshold)
        val nodes = _state.value.designNodes.toMutableList()
        val routes = _state.value.designRoutes.toMutableList()
        val pendingNodeUpserts = ArrayList<GisNode>(flushThreshold)
        val pendingRouteUpserts = ArrayList<GisRoute>(flushThreshold)
        val seenNodeIds = HashSet<String>(nodes.size * 2 + 1)
        val seenRouteIds = HashSet<String>(routes.size * 2 + 1)
        nodes.forEach { seenNodeIds += it.id }
        routes.forEach { seenRouteIds += it.id }
        val nodeByCode = HashMap<String, GisNode>(nodes.size * 2)
        val nodeByName = HashMap<String, GisNode>(nodes.size * 2)
        val nodeByCoord = HashMap<Long, GisNode>(nodes.size * 2)
        for (node in nodes) {
            val codeKey = normalizeCode(node.code)
            nodeByCode[codeKey] = node
            val nameKey = DedupSignalPolicy.effectiveNameKey(
                codeKey = codeKey,
                nameLikeKey = normalizeName(node.code)
            )
            if (nameKey.isNotBlank()) {
                nodeByName[nameKey] = node
            }
            nodeByCoord[coordBucketKey(node.latitude, node.longitude)] = node
        }
        val codeAlias = HashMap<String, String>(flushThreshold)
        val existingRouteKeys = buildRouteKeySet(routes)
        val progressUpdateInterval = 5
        var lastProgressUpdateAtMs = 0L
        var lastPublishedProcessed = -1
        var lastPublishedFailed = -1
        var parseTotalMs = 0L
        var dedupTotalMs = 0L
        var flushTotalMs = 0L
        var dedupIncomingNodesTotal = 0
        var dedupIncomingRoutesTotal = 0
        var dedupStrongMatchesTotal = 0
        var dedupWeakMatchesTotal = 0
        var dedupCoordOnlyRejectedTotal = 0
        var dedupSkipSelfRoutesTotal = 0
        var dedupSkipDuplicateRoutesTotal = 0
        var dedupNodeIdCollisionTotal = 0
        var dedupRouteIdCollisionTotal = 0
        var riskHighFiles = 0
        var riskMediumFiles = 0
        var riskLowFiles = 0
        
        val accumulatedWarnings = mutableListOf<String>()

        fun buildImportUiState(
            status: ImportStatus,
            processedFiles: Int,
            message: String
        ): ImportUiState {
            val failuresSnapshot = if (recentFailures.isEmpty()) emptyList() else recentFailures.toList()
            val retryableFailuresSnapshot = if (recentRetryableFailures.isEmpty()) emptyList() else recentRetryableFailures.toList()
            return ImportUiState(
                status = status,
                totalFiles = uris.size,
                processedFiles = processedFiles,
                successFiles = imported,
                failedFiles = failedCount,
                failures = failuresSnapshot,
                retryableFailures = retryableFailuresSnapshot,
                warnings = accumulatedWarnings.toList(),
                message = message
            )
        }

        suspend fun flushPendingGeometry() {
            val flushStartedAtMs = System.currentTimeMillis()
            if (pendingImportedFileUpserts.isNotEmpty()) {
                val fileResult = importedFileRepository.upsertAll(pendingImportedFileUpserts)
                if (fileResult is AppResult.Error) {
                    throw RuntimeException("E_DB: ${fileResult.throwable.message}", fileResult.throwable)
                }
                pendingImportedFileUpserts.clear()
            }
            if (pendingNodeUpserts.isNotEmpty()) {
                val nodeResult = gisRepository.upsertNodes(pendingNodeUpserts)
                if (nodeResult is AppResult.Error) {
                    throw RuntimeException("E_DB: ${nodeResult.throwable.message}", nodeResult.throwable)
                }
                pendingNodeUpserts.clear()
            }
            if (pendingRouteUpserts.isNotEmpty()) {
                val routeResult = gisRepository.upsertRoutes(pendingRouteUpserts)
                if (routeResult is AppResult.Error) {
                    throw RuntimeException("E_DB: ${routeResult.throwable.message}", routeResult.throwable)
                }
                pendingRouteUpserts.clear()
            }
            flushTotalMs += (System.currentTimeMillis() - flushStartedAtMs)
        }

        uris.forEachIndexed { index, uri ->
            val timestamp = System.currentTimeMillis()
            val importedId = UUID.randomUUID().toString()
            runCatching {
                AppLogger.d("import.uri uri=$uri")
                val parseStartedAtMs = System.currentTimeMillis()
                val draft = withContext(Dispatchers.IO) { importService.importFile(projectId, uri.toString()) }
                parseTotalMs += (System.currentTimeMillis() - parseStartedAtMs)
                AppLogger.d("import.parsed file=${draft.fileName} type=${draft.fileType}")

                val importedFile = ImportedFile(
                    id = importedId,
                    projectId = projectId,
                    fileName = draft.fileName,
                    fileType = draft.fileType,
                    storedPath = draft.storedPath,
                    summary = draft.summary,
                    importedAtEpochMs = timestamp
                )

                pendingImportedFileUpserts += importedFile
                val dedupStartedAtMs = System.currentTimeMillis()
                
                // Local AI: Discrepancy Analysis before merge
                draft.suggestedNodes.forEach { incoming ->
                    val existing = nodeByCode[normalizeCode(incoming.code)]
                    if (existing != null) {
                        val dist = com.mapsupervision.domain.util.Haversine.distanceInMeters(
                            incoming.latitude, incoming.longitude, existing.latitude, existing.longitude
                        )
                        if (dist > 50.0) {
                            accumulatedWarnings.add("Trạm ${incoming.code} lệch tọa độ ${dist.toInt()}m so với thiết kế.")
                        }
                        if (incoming.contractor.isNotBlank() && existing.contractor.isNotBlank() &&
                            !incoming.contractor.equals(existing.contractor, ignoreCase = true)
                        ) {
                            if (com.mapsupervision.domain.util.StringSimilarity.isSimilar(incoming.contractor, existing.contractor, 0.8)) {
                                accumulatedWarnings.add("Trạm ${incoming.code} sai chính tả nhà thầu: '${incoming.contractor}' vs '${existing.contractor}' (đã gộp).")
                            }
                        }
                    }
                }

                val merged = withContext(Dispatchers.Default) {
                    deduplicateWithIndexes(
                        projectId = projectId,
                        incomingNodes = draft.suggestedNodes.map { it.copy(importedFileId = importedId) },
                        incomingRoutes = draft.suggestedRoutes.map { it.copy(importedFileId = importedId) },
                        nodeByCode = nodeByCode,
                        nodeByName = nodeByName,
                        nodeByCoord = nodeByCoord,
                        codeAlias = codeAlias,
                        existingRouteKeys = existingRouteKeys
                    )
                }
                val preMergeNodeCount = nodes.size
                val preMergeRouteCount = routes.size
                dedupTotalMs += (System.currentTimeMillis() - dedupStartedAtMs)
                dedupIncomingNodesTotal += draft.suggestedNodes.size
                dedupIncomingRoutesTotal += draft.suggestedRoutes.size
                dedupStrongMatchesTotal += merged.stats.strongMatches
                dedupWeakMatchesTotal += merged.stats.weakMatches
                dedupCoordOnlyRejectedTotal += merged.stats.coordOnlyRejected
                dedupSkipSelfRoutesTotal += merged.stats.skippedSelfRoutes
                dedupSkipDuplicateRoutesTotal += merged.stats.skippedDuplicateRoutes
                val fileQuality = dedupQualitySnapshot(
                    incomingNodes = draft.suggestedNodes.size,
                    strongMatches = merged.stats.strongMatches,
                    weakMatches = merged.stats.weakMatches,
                    coordOnlyRejected = merged.stats.coordOnlyRejected,
                    incomingRoutes = draft.suggestedRoutes.size,
                    skippedSelfRoutes = merged.stats.skippedSelfRoutes,
                    skippedDuplicateRoutes = merged.stats.skippedDuplicateRoutes
                )
                val fileRiskSummary = DedupRiskSummaryFormatter.summarizeSingleRisk(fileQuality.risk)
                val fileRiskByFile = DedupRiskSummaryFormatter.format(fileRiskSummary)
                val fileBatchBundle = DedupBatchDecisionAdvisor.bundleFromSummaryText(fileRiskByFile)
                when (fileQuality.risk) {
                    "high" -> riskHighFiles++
                    "medium" -> riskMediumFiles++
                    else -> riskLowFiles++
                }
                var nodeIdCollisionInFile = 0
                var routeIdCollisionInFile = 0
                val safeNodesToInsert = if (merged.nodesToInsert.isEmpty()) {
                    emptyList()
                } else {
                    merged.nodesToInsert.map { node ->
                        if (seenNodeIds.add(node.id)) {
                            node
                        } else {
                            nodeIdCollisionInFile++
                            node.copy(id = UUID.randomUUID().toString()).also { seenNodeIds += it.id }
                        }
                    }
                }
                val safeRoutesToInsert = if (merged.routesToInsert.isEmpty()) {
                    emptyList()
                } else {
                    merged.routesToInsert.map { route ->
                        if (seenRouteIds.add(route.id)) {
                            route
                        } else {
                            routeIdCollisionInFile++
                            route.copy(id = UUID.randomUUID().toString()).also { seenRouteIds += it.id }
                        }
                    }
                }
                dedupNodeIdCollisionTotal += nodeIdCollisionInFile
                dedupRouteIdCollisionTotal += routeIdCollisionInFile
                pendingNodeUpserts += safeNodesToInsert
                nodes.addAll(safeNodesToInsert)
                pendingRouteUpserts += safeRoutesToInsert
                routes.addAll(safeRoutesToInsert)
                val postMergeNodeCount = nodes.size
                val postMergeRouteCount = routes.size
                AppLogger.d(
                    "dedup.invariant file=${draft.fileName} preNodes=$preMergeNodeCount postNodes=$postMergeNodeCount preRoutes=$preMergeRouteCount postRoutes=$postMergeRouteCount deletedNodes=0 deletedRoutes=0"
                )
                if (postMergeNodeCount < preMergeNodeCount || postMergeRouteCount < preMergeRouteCount) {
                    throw IllegalStateException(
                        "E_DEDUP_INVARIANT: dedup reduced in-memory counts unexpectedly"
                    )
                }
                AppLogger.d(
                    "dedup.file file=${draft.fileName} inNodes=${draft.suggestedNodes.size} " +
                        "newNodes=${merged.nodesToInsert.size} dupNodes=${merged.duplicateNodes} " +
                        "matchCode=${merged.stats.codeMatches} matchName=${merged.stats.nameMatches} " +
                        "matchCoord=${merged.stats.coordMatches} matchMulti=${merged.stats.multiSignalMatches} " +
                        "matchStrong=${merged.stats.strongMatches} matchWeak=${merged.stats.weakMatches} " +
                        "coordOnlyRejected=${merged.stats.coordOnlyRejected} " +
                        "idCollisionNodes=$nodeIdCollisionInFile idCollisionRoutes=$routeIdCollisionInFile " +
                        "score=${fileQuality.score}/100 risk=${fileQuality.risk} action=${fileQuality.action} note=${fileQuality.actionNote} " +
                        "$fileRiskByFile batchDecision=${fileBatchBundle.decision} batchPriority=${fileBatchBundle.priority} " +
                        "diag=[${fileQuality.diagnostics}] " +
                        "newRoutes=${safeRoutesToInsert.size} skipSelfRoute=${merged.stats.skippedSelfRoutes} " +
                        "skipDupRoute=${merged.stats.skippedDuplicateRoutes}"
                )
                if (pendingImportedFileUpserts.size + pendingNodeUpserts.size + pendingRouteUpserts.size >= flushThreshold) {
                    flushPendingGeometry()
                }

                imported++
                AppLogger.d("import.db_saved file=${draft.fileName}")
            }.onFailure { ex ->
                val label = uri.lastPathSegment ?: "file"
                val reason = when {
                    ex.message?.startsWith("E_URI") == true -> ex.message!!
                    ex.message?.startsWith("E_COPY") == true -> ex.message!!
                    ex.message?.startsWith("E_PARSE") == true -> ex.message!!
                    ex.message?.startsWith("E_DB") == true -> ex.message!!
                    else -> "E_PARSE: ${ex.message ?: "unknown error"}"
                }
                val failureLine = "$label: $reason"
                failedCount++
                if (firstFailures.size < 2) firstFailures += failureLine
                recentFailures += failureLine
                if (recentFailures.size > 5) recentFailures.removeFirst()
                recentRetryableFailures += ImportFailure(uri = uri, fileLabel = label, reason = reason)
                if (recentRetryableFailures.size > 20) recentRetryableFailures.removeFirst()
                AppLogger.e(ex, "import.failed file=$label")
            }

            val processed = imported + failedCount
            val nowMs = System.currentTimeMillis()
            val reachedInterval = (index + 1) % progressUpdateInterval == 0
            val reachedTimeSlice = nowMs - lastProgressUpdateAtMs >= 250L
            val isFinalIteration = index == uris.lastIndex
            if (reachedInterval || reachedTimeSlice || isFinalIteration) {
                val shouldPublish = processed != lastPublishedProcessed || failedCount != lastPublishedFailed || isFinalIteration
                if (shouldPublish) {
                    lastProgressUpdateAtMs = nowMs
                    lastPublishedProcessed = processed
                    lastPublishedFailed = failedCount
                    _state.value = _state.value.copy(
                        importUi = buildImportUiState(
                            status = ImportStatus.IMPORTING,
                            processedFiles = processed,
                            message = "Đang phân tích $processed/${uris.size} file..."
                        )
                    )
                }
            }
            if (recentFailures.size > 5) recentFailures.removeFirst()
        }

        val finalStatus = when {
            imported == 0 && failedCount > 0 -> ImportStatus.FAILED
            failedCount > 0 -> ImportStatus.PARTIAL_FAILED
            else -> ImportStatus.DONE
        }
        val finalMessage = when (finalStatus) {
            ImportStatus.DONE -> "Đã import $imported/${uris.size} file"
            ImportStatus.PARTIAL_FAILED -> "Import $imported/${uris.size} file, lỗi $failedCount"
            ImportStatus.FAILED -> "Import thất bại: ${firstFailures.joinToString(" | ")}"
            else -> "Hoàn tất import"
        } + if (finalStatus == ImportStatus.DONE || finalStatus == ImportStatus.PARTIAL_FAILED) {
            val quality = dedupQualitySnapshot(
                incomingNodes = dedupIncomingNodesTotal,
                strongMatches = dedupStrongMatchesTotal,
                weakMatches = dedupWeakMatchesTotal,
                coordOnlyRejected = dedupCoordOnlyRejectedTotal,
                incomingRoutes = dedupIncomingRoutesTotal,
                skippedSelfRoutes = dedupSkipSelfRoutesTotal,
                skippedDuplicateRoutes = dedupSkipDuplicateRoutesTotal
            )
            val riskSummary = DedupRiskSummaryFormatter.summarize(
                high = riskHighFiles,
                medium = riskMediumFiles,
                low = riskLowFiles
            )
            val riskByFile = DedupRiskSummaryFormatter.format(riskSummary)
            val batchBundle = DedupBatchDecisionAdvisor.bundleFromSummaryText(riskByFile)
            " | " + DedupAiSummaryFormatter.format(
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
        } else {
            ""
        }

        runCatching {
            val flushStartedAtMs = System.currentTimeMillis()
            flushPendingGeometry()
            AppLogger.d("perf.import.flushFinal ms=${System.currentTimeMillis() - flushStartedAtMs}")
        }.onFailure { ex ->
            val reason = if (ex.message?.startsWith("E_DB") == true) ex.message!! else "E_DB: ${ex.message ?: "flush geometry failed"}"
            val failureLine = "batch_flush: $reason"
            failedCount++
            if (firstFailures.size < 2) firstFailures += failureLine
            recentFailures += failureLine
            if (recentFailures.size > 5) recentFailures.removeFirst()
        }

        var refreshedNodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data ?: baselineNodes
        var refreshedRoutes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data ?: baselineRoutes
        val safetyIssues = detectGeometrySafetyIssues(
            baselineNodes = baselineNodes,
            baselineRoutes = baselineRoutes,
            refreshedNodes = refreshedNodes,
            refreshedRoutes = refreshedRoutes,
            normalizeCode = ::normalizeCode,
            coordBucketKey = ::coordBucketKey
        )
        val hasDropByCount = refreshedNodes.size < baselineNodes.size || refreshedRoutes.size < baselineRoutes.size
        val hasDropByIdentity = safetyIssues.hasDropByIdentity
        val hasBaselineDrift = safetyIssues.hasBaselineDrift
        if (hasDropByCount || hasDropByIdentity || hasBaselineDrift) {
            AppLogger.e(
                IllegalStateException("import_detected_data_drop"),
                "import.drop_detected project=$projectId baselineNodes=${baselineNodes.size} baselineRoutes=${baselineRoutes.size} refreshedNodes=${refreshedNodes.size} refreshedRoutes=${refreshedRoutes.size} baselineNodeSig=${identitySignature(baselineNodes.asSequence().map { it.id })} baselineRouteSig=${identitySignature(baselineRoutes.asSequence().map { it.id })} refreshedNodeSig=${identitySignature(refreshedNodes.asSequence().map { it.id })} refreshedRouteSig=${identitySignature(refreshedRoutes.asSequence().map { it.id })} missingNodeIds=${safetyIssues.missingBaselineNodeIds.take(5)} missingRouteIds=${safetyIssues.missingBaselineRouteIds.take(5)} driftNodeIds=${safetyIssues.driftedBaselineNodeIds.take(5)} driftRouteIds=${safetyIssues.driftedBaselineRouteIds.take(5)}"
            )
            // Safety net: import/dedup must not delete existing geometry.
            // Re-upsert baseline snapshot if post-import counts are unexpectedly smaller.
            gisRepository.upsertNodes(baselineNodes)
            gisRepository.upsertRoutes(baselineRoutes)
            refreshedNodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data ?: baselineNodes
            refreshedRoutes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data ?: baselineRoutes
            AppLogger.d(
                "import.drop_recovered project=$projectId nodes=${refreshedNodes.size} routes=${refreshedRoutes.size}"
            )
        }
        val (mergedNodes, mergedRoutes) = mergeGeometryPreferBaseline(
            baselineNodes = baselineNodes,
            baselineRoutes = baselineRoutes,
            refreshedNodes = refreshedNodes,
            refreshedRoutes = refreshedRoutes
        )
        if (mergedNodes.size != refreshedNodes.size || mergedRoutes.size != refreshedRoutes.size) {
            refreshedNodes = mergedNodes
            refreshedRoutes = mergedRoutes
            gisRepository.upsertNodes(refreshedNodes)
            gisRepository.upsertRoutes(refreshedRoutes)
            AppLogger.d(
                "import.baseline_union_applied project=$projectId nodes=${refreshedNodes.size} routes=${refreshedRoutes.size} nodeSig=${identitySignature(refreshedNodes.asSequence().map { it.id })} routeSig=${identitySignature(refreshedRoutes.asSequence().map { it.id })}"
            )
        }
        _state.value = _state.value.copy(
            mapUi = _state.value.mapUi.copy(
                filterContractor = null,
                searchQuery = ""
            ),
            importUi = buildImportUiState(
                status = finalStatus,
                processedFiles = uris.size,
                message = finalMessage
            )
        )
        if (refreshedNodes.isNotEmpty()) {
            GisMapBridgeRegistry.bridge?.fitToObjects()
        }
        markProjectChanged(projectId, "design_import_completed")
        AppLogger.d("perf.import.total totalMs=${System.currentTimeMillis() - startedAtMs} files=${uris.size}")
        AppLogger.d("perf.import.breakdown parseMs=$parseTotalMs dedupMs=$dedupTotalMs flushMs=$flushTotalMs")
        AppLogger.d("dedup.id_collision_summary nodes=$dedupNodeIdCollisionTotal routes=$dedupRouteIdCollisionTotal")
        AppLogger.d("import.post project=$projectId nodes=${refreshedNodes.size} routes=${refreshedRoutes.size} nodeSig=${identitySignature(refreshedNodes.asSequence().map { it.id })} routeSig=${identitySignature(refreshedRoutes.asSequence().map { it.id })}")
    }
}


