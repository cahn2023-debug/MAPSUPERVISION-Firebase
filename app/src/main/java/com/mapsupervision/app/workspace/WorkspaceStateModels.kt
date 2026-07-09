package com.mapsupervision.app.workspace

import android.net.Uri
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.domain.model.ExcelClassificationMode
import com.mapsupervision.domain.model.NonExcelFieldCandidateSet
import com.mapsupervision.domain.model.NodeSignalStatus

enum class WorkspaceTab {
    MAP,
    PROGRESS,
    DATA,
    REPORTS,
    MATERIALS
}

enum class WorkspaceLayoutMode {
    COMPACT,
    EXPANDED
}

data class WorkspaceDataState(
    val activeProjectId: String? = null,
    val importedFiles: List<ImportedFile> = emptyList(),
    val designNodes: List<GisNode> = emptyList(),
    val designRoutes: List<GisRoute> = emptyList(),
    val constructionProgress: List<NodeProgress> = emptyList(),
    val dashboard: DashboardState = DashboardState(),
    val photoFilterNodeCode: String? = null,
    val selectedNodePhotos: List<SitePhoto> = emptyList(),
    val projectPhotos: List<SitePhoto> = emptyList(),
    val pendingCaptureNodeCode: String? = null,
    val photoSaveCount: Int = 0,
    val workVolumeRows: List<WorkVolumeProgress> = emptyList(),
    val workVolumeProgress: Map<String, String> = emptyMap(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val workCategories: List<WorkCategory> = emptyList(),
    val selectedObjectNotes: List<Note> = emptyList(),
    val selectedObjectTasks: List<Task> = emptyList(),
    val materialHandovers: List<MaterialHandover> = emptyList(),
    val materialDeclarations: List<MaterialDeclaration> = emptyList(),
    val workPlans: List<WorkPlan> = emptyList(),
    val projectTasks: List<Task> = emptyList(),
    val aiNoteSummary: String = "",
    val aiTaskSuggestions: List<String> = emptyList(),
    val isAiLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastRefreshedAtEpochMs: Long = 0L,
    val firebaseSync: FirebaseSyncState = FirebaseSyncState()
)

data class FirebaseSyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAtEpochMs: Long = 0L,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val uploadedMedia: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
    val lastTrigger: String? = null
)

enum class SharedMediaTargetKind {
    NODE,
    ROUTE
}

data class IncomingSharePayload(
    val id: String,
    val uris: List<Uri>,
    val mimeType: String? = null,
    val receivedAtEpochMs: Long = System.currentTimeMillis()
)

data class SharedMediaDraft(
    val selectedProjectId: String? = null,
    val targetKind: SharedMediaTargetKind = SharedMediaTargetKind.NODE,
    val targetCode: String = "",
    val searchQuery: String = "",
    val removedUriStrings: Set<String> = emptySet()
)

data class PendingSharedImport(
    val payload: IncomingSharePayload,
    val draft: SharedMediaDraft = SharedMediaDraft()
)

data class WorkspaceUiState(
    val selectedTab: WorkspaceTab = WorkspaceTab.MAP,
    val layoutMode: WorkspaceLayoutMode = WorkspaceLayoutMode.COMPACT,
    val showReportPreview: Boolean = false,
    val previewNodeCode: String? = null
)

sealed interface WorkspaceEffect {
    data class ShowMessage(val message: String) : WorkspaceEffect
    data class OpenExportedFile(val path: String) : WorkspaceEffect
}

sealed interface WorkspaceAction {
    data class SelectTab(val tab: WorkspaceTab) : WorkspaceAction
    data class UpdateLayoutMode(val mode: WorkspaceLayoutMode) : WorkspaceAction
    data class ShowReportPreview(val nodeCode: String?) : WorkspaceAction
    data class SetPendingSharedImport(val pendingSharedImport: PendingSharedImport?) : WorkspaceAction
    data class UpdatePendingSharedImport(val pendingSharedImport: PendingSharedImport) : WorkspaceAction
    data object DismissReportPreview : WorkspaceAction
    data object ClearPendingSharedImport : WorkspaceAction
    data class ShowMapConfigDialog(val show: Boolean) : WorkspaceAction
    data class UpdateMapDisplayConfig(val nodeSize: Float, val routeWidth: Float) : WorkspaceAction
}

internal data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

internal data class WorkspaceRefreshSnapshot(
    val imports: List<ImportedFile>,
    val nodes: List<GisNode>,
    val routes: List<GisRoute>,
    val progress: List<NodeProgress>,
    val workVolumeRows: List<WorkVolumeProgress>,
    val dailyLogs: List<DailyLog>,
    val workCategories: List<WorkCategory>,
    val materialHandovers: List<MaterialHandover>,
    val materialDeclarations: List<MaterialDeclaration>,
    val projectTasks: List<Task> = emptyList()
)

private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

internal object DedupQualityAdvisor {
    private data class FocusMetrics(
        val strongPct: Int,
        val weakPct: Int,
        val coordRejectPct: Int,
        val selfPct: Int,
        val dupPct: Int,
        val focus: String,
        val confidence: String
    )

    fun label(score: Int): String {
        return when {
            score >= 85 -> "cao"
            score >= 65 -> "trung binh"
            else -> "thap"
        }
    }

    fun hint(
        score: Int,
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): String {
        if (score >= 65) return ""
        val metrics = focusMetrics(
            incomingNodes = incomingNodes,
            strongMatches = strongMatches,
            weakMatches = weakMatches,
            coordOnlyRejected = coordOnlyRejected,
            incomingRoutes = incomingRoutes,
            skippedSelfRoutes = skippedSelfRoutes,
            skippedDuplicateRoutes = skippedDuplicateRoutes
        )
        if (metrics.confidence == "low") {
            return " (gợi ý: mẫu import còn ít, nên đối chiếu thêm trước khi kết luận)"
        }
        return when (metrics.focus) {
            "self" ->
                " (gợi ý: kiểm tra mapping điểm đầu/cuối để tránh tuyến start=end)"
            "weak" ->
                " (gợi ý: chuẩn hóa cột mã/tọa độ để giảm match yếu)"
            "coordReject" ->
                " (gợi ý: đối chiếu mã + nhà thầu cho các điểm gần nhau)"
            "dup" ->
                " (gợi ý: lọc bớt dữ liệu tuyến trùng trước import)"
            else ->
                " (gợi ý: kiểm tra ngẫu nhiên 5-10 dòng đầu vào để đối chiếu)"
        }
    }

    fun diagnostics(
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): String {
        val strongPct = percent(strongMatches, incomingNodes)
        val metrics = focusMetrics(
            incomingNodes = incomingNodes,
            strongMatches = strongMatches,
            weakMatches = weakMatches,
            coordOnlyRejected = coordOnlyRejected,
            incomingRoutes = incomingRoutes,
            skippedSelfRoutes = skippedSelfRoutes,
            skippedDuplicateRoutes = skippedDuplicateRoutes
        )
        return "strong=${strongPct}%, weak=${metrics.weakPct}%, coordReject=${metrics.coordRejectPct}%, self=${metrics.selfPct}%, dup=${metrics.dupPct}%, focus=${metrics.focus}, confidence=${metrics.confidence}"
    }

    fun riskLevel(
        score: Int,
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): String {
        val metrics = focusMetrics(
            incomingNodes = incomingNodes,
            strongMatches = strongMatches,
            weakMatches = weakMatches,
            coordOnlyRejected = coordOnlyRejected,
            incomingRoutes = incomingRoutes,
            skippedSelfRoutes = skippedSelfRoutes,
            skippedDuplicateRoutes = skippedDuplicateRoutes
        )
        if (score < 55) return "high"
        if (metrics.confidence == "low" && score < 75) return "high"
        if (score < 75) return "medium"
        if (metrics.confidence == "medium" && metrics.focus != "stable") return "medium"
        return "low"
    }

    fun actionByRisk(risk: String): String {
        return when (risk) {
            "high" -> "review_required"
            "medium" -> "review_recommended"
            else -> "monitor"
        }
    }

    fun actionNote(action: String): String {
        return when (action) {
            "review_required" -> "đối chiếu thủ công trước khi chốt dữ liệu"
            "review_recommended" -> "kiểm tra mẫu 5-10 bản ghi tiêu biểu"
            else -> "theo dõi và tiếp tục import theo lô"
        }
    }

    private fun percent(part: Int, total: Int): Int {
        if (total <= 0 || part <= 0) return 0
        return ((part * 100.0) / total).toInt().coerceIn(0, 100)
    }

    private fun dominantIssue(
        strongPct: Int,
        weakPct: Int,
        coordRejectPct: Int,
        selfPct: Int,
        dupPct: Int
    ): String {
        if (strongPct >= 70 && weakPct <= 15 && coordRejectPct <= 10 && selfPct <= 10 && dupPct <= 10) {
            return "stable"
        }
        var name = "balanced"
        var max = 0
        if (weakPct > max) {
            max = weakPct
            name = "weak"
        }
        if (coordRejectPct > max) {
            max = coordRejectPct
            name = "coordReject"
        }
        if (selfPct > max) {
            max = selfPct
            name = "self"
        }
        if (dupPct > max) {
            name = "dup"
        }
        return name
    }

    private fun focusMetrics(
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): FocusMetrics {
        val strongPct = percent(strongMatches, incomingNodes)
        val weakPct = percent(weakMatches, incomingNodes)
        val coordRejectPct = percent(coordOnlyRejected, incomingNodes)
        val selfPct = percent(skippedSelfRoutes, incomingRoutes)
        val dupPct = percent(skippedDuplicateRoutes, incomingRoutes)
        val focus = dominantIssue(strongPct, weakPct, coordRejectPct, selfPct, dupPct)
        val sampleSize = incomingNodes + incomingRoutes
        val confidence = when {
            sampleSize < 5 -> "low"
            sampleSize < 10 -> "medium"
            else -> "high"
        }
        return FocusMetrics(
            strongPct = strongPct,
            weakPct = weakPct,
            coordRejectPct = coordRejectPct,
            selfPct = selfPct,
            dupPct = dupPct,
            focus = focus,
            confidence = confidence
        )
    }
}

internal object DedupQualityScorer {
    fun score(
        incomingNodes: Int,
        strongMatches: Int,
        weakMatches: Int,
        coordOnlyRejected: Int,
        incomingRoutes: Int,
        skippedSelfRoutes: Int,
        skippedDuplicateRoutes: Int
    ): Int {
        if (incomingNodes <= 0 && incomingRoutes <= 0) return 100
        val sampleSize = incomingNodes + incomingRoutes
        val strongRate = if (incomingNodes > 0) strongMatches.toDouble() / incomingNodes else 0.0
        val weakRate = if (incomingNodes > 0) weakMatches.toDouble() / incomingNodes else 0.0
        val coordRejectRate = if (incomingNodes > 0) coordOnlyRejected.toDouble() / incomingNodes else 0.0
        val selfRouteRate = if (incomingRoutes > 0) skippedSelfRoutes.toDouble() / incomingRoutes else 0.0
        val dupRouteRate = if (incomingRoutes > 0) skippedDuplicateRoutes.toDouble() / incomingRoutes else 0.0
        val score = 100.0 -
            (weakRate * 25.0) -
            (coordRejectRate * 20.0) -
            (selfRouteRate * 40.0) -
            (dupRouteRate * 15.0) +
            (strongRate * 10.0)
        val bounded = score.toInt().coerceIn(0, 100)
        val confidenceCap = when {
            sampleSize < 5 -> 85
            sampleSize < 10 -> 92
            else -> 100
        }
        return minOf(bounded, confidenceCap)
    }
}

internal object DedupCoordMatchPolicy {
    fun shouldRejectCoordOnlyMatch(
        incomingCodeKey: String,
        canonicalCodeKey: String,
        incomingContractorKey: String,
        canonicalContractorKey: String
    ): Boolean {
        val codeConflict = incomingCodeKey.isNotBlank() &&
            canonicalCodeKey.isNotBlank() &&
            incomingCodeKey != canonicalCodeKey
        val contractorConflict = incomingContractorKey.isNotBlank() &&
            canonicalContractorKey.isNotBlank() &&
            incomingContractorKey != canonicalContractorKey
        return codeConflict && contractorConflict
    }
}

internal object DedupSignalPolicy {
    fun effectiveNameKey(codeKey: String, nameLikeKey: String): String {
        if (nameLikeKey.isBlank()) return ""
        if (nameLikeKey == codeKey) return ""
        return nameLikeKey
    }
}

internal object DedupAiSummaryFormatter {
    data class AiSummary(
        val score: Int,
        val label: String,
        val risk: String,
        val action: String,
        val actionNote: String,
        val diagnostics: String,
        val riskByFile: String,
        val batchDecision: String,
        val batchPriority: Int,
        val batchNote: String,
        val hint: String
    )

    fun format(
        score: Int,
        label: String,
        risk: String,
        action: String,
        actionNote: String,
        diagnostics: String,
        riskByFile: String,
        batchDecision: String,
        batchPriority: Int,
        batchNote: String,
        hint: String
    ): String {
        return "AI dedup score=$score/100 ($label, risk=$risk, action=$action, note=$actionNote, $diagnostics, $riskByFile, batchDecision=$batchDecision, batchPriority=$batchPriority, batchNote=$batchNote)$hint"
    }

    fun parse(text: String): AiSummary? {
        val match = SUMMARY_REGEX.matchEntire(text) ?: return null
        val score = match.groupValues[1].toInt()
        val label = match.groupValues[2]
        val risk = match.groupValues[3]
        val action = match.groupValues[4]
        val actionNote = match.groupValues[5]
        val diagnostics = match.groupValues[6].trim()
        val riskByFile = "riskByFile=${match.groupValues[7]}"
        val batchDecision = match.groupValues[8]
        val batchPriority = match.groupValues[9].toInt()
        val batchNote = match.groupValues[10]
        val hint = match.groupValues[11]

        val parsedRisk = DedupRiskSummaryFormatter.parse(riskByFile) ?: return null
        val expected = DedupBatchDecisionAdvisor.bundleFromSummaryText(riskByFile)
        if (batchDecision != expected.decision) return null
        if (batchPriority != expected.priority) return null
        if (batchNote != expected.note) return null
        if (!DedupRiskSummaryFormatter.isValid(parsedRisk)) return null

        return AiSummary(
            score = score,
            label = label,
            risk = risk,
            action = action,
            actionNote = actionNote,
            diagnostics = diagnostics,
            riskByFile = riskByFile,
            batchDecision = batchDecision,
            batchPriority = batchPriority,
            batchNote = batchNote,
            hint = hint
        )
    }

    private val SUMMARY_REGEX = Regex(
        """AI dedup score=(\d{1,3})/100 \(([^,]+), risk=([^,]+), action=([^,]+), note=([^,]+), (.+), riskByFile=(high:\d+\(\d+%\),medium:\d+\(\d+%\),low:\d+\(\d+%\),batchAction=[a-z_]+), batchDecision=([a-z_]+), batchPriority=(\d+), batchNote=([^)]*)\)(.*)"""
    )
}

internal object DedupRiskSummaryFormatter {
    data class RiskSummary(
        val high: Int,
        val highPct: Int,
        val medium: Int,
        val mediumPct: Int,
        val low: Int,
        val lowPct: Int,
        val batchAction: String
    )

    fun summarize(high: Int, medium: Int, low: Int): RiskSummary {
        val totalRaw = high + medium + low
        val (highPct, mediumPct, lowPct) = if (totalRaw > 0) {
            val total = totalRaw.toDouble()
            val raw = listOf(
                high * 100.0 / total,
                medium * 100.0 / total,
                low * 100.0 / total
            )
            val base = raw.map { it.toInt().coerceIn(0, 100) }.toMutableList()
            var remainder = 100 - base.sum()
            val order = raw
                .mapIndexed { idx, value -> idx to (value - value.toInt()) }
                .sortedByDescending { it.second }
                .map { it.first }
            var cursor = 0
            while (remainder > 0 && cursor < order.size) {
                base[order[cursor]] = (base[order[cursor]] + 1).coerceAtMost(100)
                remainder--
                cursor++
                if (cursor >= order.size && remainder > 0) cursor = 0
            }
            Triple(base[0], base[1], base[2])
        } else {
            Triple(0, 0, 0)
        }
        val batchAction = when {
            high > 0 -> "review_required"
            medium > 0 -> "review_recommended"
            else -> "monitor"
        }
        return RiskSummary(
            high = high,
            highPct = highPct,
            medium = medium,
            mediumPct = mediumPct,
            low = low,
            lowPct = lowPct,
            batchAction = batchAction
        )
    }

    fun format(high: Int, medium: Int, low: Int): String {
        return format(summarize(high, medium, low))
    }

    fun summarizeSingleRisk(risk: String): RiskSummary {
        return when (risk) {
            "high" -> summarize(high = 1, medium = 0, low = 0)
            "medium" -> summarize(high = 0, medium = 1, low = 0)
            else -> summarize(high = 0, medium = 0, low = 1)
        }
    }

    fun format(summary: RiskSummary): String {
        return "riskByFile=high:${summary.high}(${summary.highPct}%),medium:${summary.medium}(${summary.mediumPct}%),low:${summary.low}(${summary.lowPct}%),batchAction=${summary.batchAction}"
    }

    fun parse(text: String): RiskSummary? {
        val match = RISK_SUMMARY_REGEX.matchEntire(text) ?: return null
        val summary = RiskSummary(
            high = match.groupValues[1].toInt(),
            highPct = match.groupValues[2].toInt(),
            medium = match.groupValues[3].toInt(),
            mediumPct = match.groupValues[4].toInt(),
            low = match.groupValues[5].toInt(),
            lowPct = match.groupValues[6].toInt(),
            batchAction = match.groupValues[7]
        )
        return if (isValid(summary)) summary else null
    }

    fun isValid(summary: RiskSummary): Boolean {
        if (summary.high < 0 || summary.medium < 0 || summary.low < 0) return false
        if (summary.highPct !in 0..100 || summary.mediumPct !in 0..100 || summary.lowPct !in 0..100) return false
        val total = summary.high + summary.medium + summary.low
        if (total > 0 && (summary.highPct + summary.mediumPct + summary.lowPct != 100)) return false
        if (total == 0 && (summary.highPct != 0 || summary.mediumPct != 0 || summary.lowPct != 0)) return false
        val expectedAction = when {
            summary.high > 0 -> "review_required"
            summary.medium > 0 -> "review_recommended"
            else -> "monitor"
        }
        if (summary.batchAction != expectedAction) return false
        return true
    }

    private val RISK_SUMMARY_REGEX = Regex(
        """riskByFile=high:(\d+)\((\d+)%\),medium:(\d+)\((\d+)%\),low:(\d+)\((\d+)%\),batchAction=([a-z_]+)"""
    )
}

internal object DedupBatchDecisionAdvisor {
    data class BatchDecisionBundle(
        val decision: String,
        val priority: Int,
        val note: String
    )

    fun decisionFromSummaryText(summaryText: String): String {
        val summary = DedupRiskSummaryFormatter.parse(summaryText) ?: return "review_required"
        return decision(summary)
    }

    fun bundleFromSummaryText(summaryText: String): BatchDecisionBundle {
        val decision = decisionFromSummaryText(summaryText)
        return BatchDecisionBundle(
            decision = decision,
            priority = priority(decision),
            note = note(decision)
        )
    }

    fun decision(summary: DedupRiskSummaryFormatter.RiskSummary): String {
        return when {
            summary.highPct >= 30 -> "critical_review"
            summary.high > 0 -> "review_required"
            summary.mediumPct >= 50 -> "review_recommended"
            else -> "monitor"
        }
    }

    fun note(decision: String): String {
        return when (decision) {
            "critical_review" -> "tam dung cap nhat hien truong va kiem tra toan bo file high"
            "review_required" -> "kiem tra thu cong cac file high truoc khi xac nhan"
            "review_recommended" -> "kiem tra xac suat theo mau dai dien"
            else -> "co the tiep tuc luong import binh thuong"
        }
    }

    fun priority(decision: String): Int {
        return when (decision) {
            "critical_review" -> 3
            "review_required" -> 2
            "review_recommended" -> 1
            else -> 0
        }
    }
}

data class WorkspaceState(
    val activeProjectId: String? = null,
    val importedFiles: List<ImportedFile> = emptyList(),
    val designNodes: List<GisNode> = emptyList(),
    val designRoutes: List<GisRoute> = emptyList(),
    val constructionProgress: List<NodeProgress> = emptyList(),
    val dashboard: DashboardState = DashboardState(),
    val mapUi: MapUiState = MapUiState(),
    val pendingSharedImport: PendingSharedImport? = null,
    val photoFilterNodeCode: String? = null,
    val selectedNodePhotos: List<SitePhoto> = emptyList(),
    val projectPhotos: List<SitePhoto> = emptyList(),
    val pendingCaptureNodeCode: String? = null,
    val photoSaveCount: Int = 0,
    val importUi: ImportUiState = ImportUiState(),
    val excelParserUi: ExcelParserUiState = ExcelParserUiState(),
    val importMappingUi: ImportMappingUiState = ImportMappingUiState(),
    val aiOpsActions: List<String> = emptyList(),
    val aiOpsPriority: Int = 0,
    val workVolumeRows: List<WorkVolumeProgress> = emptyList(),
    val workVolumeProgress: Map<String, String> = emptyMap(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val workCategories: List<WorkCategory> = emptyList(),
    val selectedObjectNotes: List<Note> = emptyList(),
    val selectedObjectTasks: List<Task> = emptyList(),
    val materialHandovers: List<MaterialHandover> = emptyList(),
    val materialDeclarations: List<MaterialDeclaration> = emptyList(),
    val workPlans: List<WorkPlan> = emptyList(),
    val projectTasks: List<Task> = emptyList(),
    val aiNoteSummary: String = "",
    val aiTaskSuggestions: List<String> = emptyList(),
    val isAiLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastRefreshedAtEpochMs: Long = 0L,
    val firebaseSync: FirebaseSyncState = FirebaseSyncState()
) {
    fun toDataState(): WorkspaceDataState = WorkspaceDataState(
        activeProjectId = activeProjectId,
        importedFiles = importedFiles,
        designNodes = designNodes,
        designRoutes = designRoutes,
        constructionProgress = constructionProgress,
        dashboard = dashboard,
        photoFilterNodeCode = photoFilterNodeCode,
        selectedNodePhotos = selectedNodePhotos,
        projectPhotos = projectPhotos,
        pendingCaptureNodeCode = pendingCaptureNodeCode,
        photoSaveCount = photoSaveCount,
        workVolumeRows = workVolumeRows,
        workVolumeProgress = workVolumeProgress,
        dailyLogs = dailyLogs,
        workCategories = workCategories,
        selectedObjectNotes = selectedObjectNotes,
        selectedObjectTasks = selectedObjectTasks,
        materialHandovers = materialHandovers,
        materialDeclarations = materialDeclarations,
        workPlans = workPlans,
        projectTasks = projectTasks,
        aiNoteSummary = aiNoteSummary,
        aiTaskSuggestions = aiTaskSuggestions,
        isAiLoading = isAiLoading,
        isRefreshing = isRefreshing,
        lastRefreshedAtEpochMs = lastRefreshedAtEpochMs,
        firebaseSync = firebaseSync
    )
}

data class DashboardState(
    val totalDesignNodes: Int = 0,
    val totalDesignRoutes: Int = 0,
    val updatedConstructionNodes: Int = 0,
    val completionPercent: Float = 0f,
    val delayedCount: Int = 0,
    val totalPlannedQty: Float = 0f,
    val totalActualQty: Float = 0f,
    val materialCompletionPercent: Float = 0f,
    val nodesWithMaterialEntry: Int = 0
)

enum class ImportStatus {
    IDLE, PICKING, IMPORTING, DONE, PARTIAL_FAILED, FAILED
}

data class ImportFailure(
    val uri: Uri,
    val fileLabel: String,
    val reason: String
)

data class ImportUiState(
    val status: ImportStatus = ImportStatus.IDLE,
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val successFiles: Int = 0,
    val failedFiles: Int = 0,
    val failures: List<String> = emptyList(),
    val retryableFailures: List<ImportFailure> = emptyList(),
    val warnings: List<String> = emptyList(), // Discrepancy Warnings
    val message: String = ""
)

data class ExcelParserUiState(
    val sourceUri: Uri? = null,
    val sourceFileName: String = "",
    val existingFileId: String? = null,
    val headers: List<String> = emptyList(),
    val sampleRows: List<Map<String, String>> = emptyList(),
    val positionColumn: String = "",
    val coordinateColumn: String = "",
    val latitudeColumn: String = "",
    val longitudeColumn: String = "",
    val contractorColumn: String = "",
    val mapNumberColumn: String = "",
    val objectTypeColumn: String = "",
    val ipAddressColumn: String = "",
    val subnetColumn: String = "",
    val gatewayColumn: String = "",
    val signalStatusColumn: String = "",
    val fiberCoreCountColumn: String = "",
    val fiberConnectionColumn: String = "",
    val useTwoColumnCoordinates: Boolean = false,
    val showMappingDialog: Boolean = false,
    val showNumberOnMap: Boolean = true,
    val colorByContractorOnMap: Boolean = true,
    val classificationMode: ExcelClassificationMode = ExcelClassificationMode.AUTO,
    val workVolumeColumnsCsv: String = "",
    val suggestedItemColumns: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val message: String = "",
    val sheets: List<String> = emptyList(),
    val selectedSheet: String = ""
)

data class ImportMappingUiState(
    val sourceUri: Uri? = null,
    val sourceFileName: String = "",
    val existingFileId: String? = null,
    val sourceType: String = "",
    val candidates: NonExcelFieldCandidateSet = NonExcelFieldCandidateSet(
        positionOptions = emptyList(),
        coordinateOptions = emptyList(),
        latitudeOptions = emptyList(),
        longitudeOptions = emptyList(),
        contractorOptions = emptyList(),
        mapNumberOptions = emptyList(),
        objectTypeOptions = emptyList(),
        itemOptions = emptyList(),
        routeLengthOptions = emptyList(),
        ipAddressOptions = emptyList(),
        subnetOptions = emptyList(),
        gatewayOptions = emptyList(),
        signalStatusOptions = emptyList(),
        fiberCoreCountOptions = emptyList(),
        fiberConnectionOptions = emptyList()
    ),
    val positionField: String = "",
    val coordinateField: String = "",
    val contractorField: String = "",
    val mapNumberField: String = "",
    val objectTypeField: String = "",
    val workVolumeFieldsCsv: String = "",
    val routeLengthField: String = "",
    val ipAddressField: String = "",
    val subnetField: String = "",
    val gatewayField: String = "",
    val signalStatusField: String = "",
    val fiberCoreCountField: String = "",
    val fiberConnectionField: String = "",
    val confirmedPositionField: Boolean = false,
    val confirmedCoordinateField: Boolean = false,
    val confirmedContractorField: Boolean = false,
    val confirmedMapNumberField: Boolean = false,
    val confirmedObjectTypeField: Boolean = false,
    val confirmedWorkVolumeFields: Boolean = false,
    val confirmedRouteLengthField: Boolean = false,
    val confirmedIpAddressField: Boolean = false,
    val confirmedSubnetField: Boolean = false,
    val confirmedGatewayField: Boolean = false,
    val confirmedSignalStatusField: Boolean = false,
    val confirmedFiberCoreCountField: Boolean = false,
    val confirmedFiberConnectionField: Boolean = false,
    val showMappingDialog: Boolean = false,
    val isLoading: Boolean = false,
    val message: String = ""
)

data class MapUiState(
    val selectedNode: GisNode? = null,
    val selectedRoute: GisRoute? = null,
    val centerNodeCode: String? = null,
    val status: String = "Nhà thầu",
    val expectedCompletion: String = "-",
    val lastInspection: String = "-",
    val signalStatus: NodeSignalStatus = NodeSignalStatus.UNKNOWN,
    val centerPathSummary: String = "",
    val labelField: GisLabelField = GisLabelField.CODE,
    val showNodes: Boolean = true,
    val showRoutes: Boolean = true,
    val measureEnabled: Boolean = false,
    val measureDistanceText: String = "",   // e.g. "1.23 km" or "456 m"
    val filterContractor: String? = null,
    val filterMaterialType: String? = null,
    val contractorColors: Map<String, String> = emptyMap(), // contractor name -> hex color
    val hiddenContractors: Set<String> = emptySet(),
    val searchQuery: String = "",
    val message: String = "",
    val routeNote: String = "",
    val nodeSizeScale: Float = 1.0f,
    val routeWidthScale: Float = 1.0f,
    val showConfigDialog: Boolean = false
)

internal val GisLabelField.displayName: String
    get() = when (this) {
        GisLabelField.CODE -> "Mã đối tượng"
        GisLabelField.CONTRACTOR -> "Nhà thầu"
        GisLabelField.COORDINATE -> "Tọa độ"
    }

fun dedupeImportedFilesById(files: List<ImportedFile>): List<ImportedFile> {
    val seenIds = mutableSetOf<String>()
    return files.filter { seenIds.add(it.id) }
}

fun isContractorHidden(mapUi: MapUiState, contractor: String): Boolean {
    return mapUi.hiddenContractors.contains(contractor)
}
