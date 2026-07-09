package com.mapsupervision.app.workspace

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.app.GemmaModelDownloadWorker
import com.mapsupervision.ai.model.mediapipe.GemmaChatController
import com.mapsupervision.ai.model.mediapipe.GemmaDownloadState
import com.mapsupervision.ai.model.mediapipe.GemmaLiteRtChatService
import com.mapsupervision.ai.model.mediapipe.GemmaModelManager
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.ChatActionParser
import com.mapsupervision.domain.ai.ChatAssistantPayload
import com.mapsupervision.domain.ai.ChatAssistantResult
import com.mapsupervision.domain.ai.ChatActionType
import com.mapsupervision.domain.ai.DailyLogDraft
import com.mapsupervision.domain.ai.DailyLogDateResolver
import com.mapsupervision.domain.ai.ChatPendingAction
import com.mapsupervision.domain.ai.ChatClarificationPrompt
import com.mapsupervision.domain.ai.ChatIntentOption
import com.mapsupervision.domain.ai.SitePhotoUpdateDraft
import com.mapsupervision.domain.ai.ReportDraftDbSaveDraft
import com.mapsupervision.domain.ai.GemmaDeviceSnapshot
import com.mapsupervision.domain.ai.GemmaModelInfo
import com.mapsupervision.domain.ai.GemmaModelSelection
import com.mapsupervision.domain.ai.GemmaModelStatus
import com.mapsupervision.ai.agent.SummaryAggregator
import com.mapsupervision.ai.core.rag.RagBuildRequest
import com.mapsupervision.ai.core.rag.RagBuildResult
import com.mapsupervision.ai.core.rag.RagChatAnswerFormatter
import com.mapsupervision.ai.core.rag.RagContextBuilder
import com.mapsupervision.ai.core.rag.RagQueryDomain
import com.mapsupervision.domain.model.ChatHistoryMessage
import com.mapsupervision.domain.model.AiActionLog
import com.mapsupervision.domain.model.WorkspaceSnapshot
import com.mapsupervision.domain.repository.ChatHistoryRepository
import com.mapsupervision.domain.repository.AiActionLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class GemmaChatMessage(
    val role: String,
    val text: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

data class GemmaChatUiState(
    val isOpen: Boolean = false,
    val input: String = "",
    val messages: List<GemmaChatMessage> = emptyList(),
    val pendingAction: ChatPendingAction? = null,
    val selectedModel: GemmaModelInfo? = null,
    val availableModels: List<GemmaModelInfo> = emptyList(),
    val modelSelection: GemmaModelSelection = GemmaModelSelection(false, null, "unknown"),
    val modelStatus: GemmaModelStatus = GemmaModelStatus.NOT_DOWNLOADED,
    val showModelPicker: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadMessage: String = "",
    val downloadFailureCode: String = "",
    val downloadFailureReason: String = "",
    val downloadHttpCode: Int = 0,
    val showCellularWarning: Boolean = false,
    val isBusy: Boolean = false,
    val lastError: String = "",
    val chatReady: Boolean = false,
    val chatStatus: String = "",
    val clarificationPrompt: ChatClarificationPrompt? = null
)

@HiltViewModel
class GemmaChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiOrchestrator: AiOrchestrator,
    private val modelManager: GemmaModelManager,
    private val deviceSnapshotProvider: GemmaDeviceSnapshotProvider,
    private val chatController: GemmaChatController,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val aiActionLogRepository: AiActionLogRepository,
    private val summaryAggregator: SummaryAggregator,
    private val ragContextBuilder: RagContextBuilder
) : ViewModel() {
    companion object {
        private const val CHAT_HISTORY_LIMIT = 6
        private const val CONTEXT_CHAR_LIMIT = 1500
    }

    private val _uiState = MutableStateFlow(GemmaChatUiState())
    val uiState: StateFlow<GemmaChatUiState> = _uiState.asStateFlow()
    private var activeProjectId: String? = null
    private var activeSendJob: Job? = null

    init {
        viewModelScope.launch {
            modelManager.observeDownloadState().collectLatest(::applyDownloadState)
        }
    }

    fun open(projectId: String?) {
        activeProjectId = projectId
        loadHistory(projectId)
        refreshModelState()
        _uiState.update { it.copy(isOpen = true) }
    }

    fun close() {
        activeSendJob?.cancel()
        activeSendJob = null
        viewModelScope.launch {
            chatController.cancelGeneration()
            chatController.close()
        }
        _uiState.update {
            it.copy(
                isOpen = false,
                isBusy = false,
                pendingAction = null,
                clarificationPrompt = null,
                chatReady = false,
                chatStatus = ""
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun clearChatHistory() {
        activeSendJob?.cancel()
        activeSendJob = null
        viewModelScope.launch {
            chatController.resetConversation()
        }
        val safeProj = activeProjectId
        if (!safeProj.isNullOrBlank()) {
            viewModelScope.launch {
                chatHistoryRepository.clearByProject(safeProj)
            }
        }
        _uiState.update { it.copy(messages = emptyList(), pendingAction = null, isBusy = false) }
    }

    fun reloadHistory() {
        val safeProj = activeProjectId
        if (!safeProj.isNullOrBlank()) {
            loadHistory(safeProj)
        }
    }

    fun refreshModelState(snapshot: GemmaDeviceSnapshot = deviceSnapshotProvider.snapshot()) {
        val selection = modelManager.selectModel(snapshot)
        val selected = _uiState.value.selectedModel
            ?.takeIf { current -> selection.candidates.any { it.downloadFileName == current.downloadFileName } }
            ?: selection.selected
        _uiState.update {
            it.copy(
                selectedModel = selected,
                availableModels = selection.candidates,
                modelSelection = selection,
                modelStatus = selected?.let(modelManager::status) ?: GemmaModelStatus.UNSUPPORTED,
                showModelPicker = false,
                lastError = if (!selection.supported) selection.reason else it.lastError
            )
        }
        applyDownloadState(modelManager.currentDownloadState())
    }

    fun openModelPicker() {
        refreshModelState()
        _uiState.update { it.copy(showModelPicker = true) }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    fun selectModel(model: GemmaModelInfo) {
        _uiState.update {
            it.copy(
                selectedModel = model,
                modelStatus = modelManager.status(model),
                showModelPicker = false,
                chatReady = false,
                chatStatus = "",
                lastError = ""
            )
        }
        applyDownloadState(modelManager.currentDownloadState())
    }

    fun downloadSelectedModel() {
        val model = _uiState.value.selectedModel ?: return
        if (isCellularNetwork()) {
            _uiState.update { it.copy(showCellularWarning = true, lastError = "") }
            return
        }
        startDownload(model)
    }

    fun confirmCellularDownload() {
        val model = _uiState.value.selectedModel ?: return
        _uiState.update { it.copy(showCellularWarning = false) }
        startDownload(model)
    }

    fun dismissCellularWarning() {
        _uiState.update { it.copy(showCellularWarning = false) }
    }

    fun deleteModel() {
        val model = _uiState.value.selectedModel ?: return
        GemmaModelDownloadWorker.cancel(context)
        modelManager.delete(model)
        modelManager.clearDownloadState()
        viewModelScope.launch {
            chatController.resetConversation()
        }
        refreshModelState()
    }

    fun cancelDownload() {
        GemmaModelDownloadWorker.cancel(context)
    }

    fun sendMessage(
        contextSummary: String,
        normalizationContext: String,
        canonicalUserMessage: String,
        projectId: String?,
        tab: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?,
        workspaceSnapshot: WorkspaceSnapshot? = null
    ) {
        val text = _uiState.value.input.trim()
        if (text.isBlank()) return
        val safeProjectId = projectId ?: activeProjectId
        activeProjectId = safeProjectId

        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                input = "",
                messages = it.messages + GemmaChatMessage("user", text),
                isBusy = true,
                clarificationPrompt = null,
                lastError = ""
            )
        }

        activeSendJob?.cancel()
        activeSendJob = viewModelScope.launch {
            try {
                persistMessage(safeProjectId, "user", text)
                val ragResult = buildRagContext(
                    projectId = safeProjectId,
                    query = canonicalUserMessage.ifBlank { text },
                    workspaceSnapshot = workspaceSnapshot,
                    selectedNodeCode = selectedNodeCode,
                    selectedRouteCode = selectedRouteCode
                )
                val ragBlock = ragResult?.block
                val ragPrompt = ragBlock?.toPromptBlock().orEmpty()
                val enrichedContextSummary = trimContext(mergeContext(contextSummary, ragPrompt))
                val enrichedNormalizationContext = mergeNormalization(
                    normalizationContext,
                    ragBlock?.resolvedRefs.orEmpty()
                )

                // 1. Fast path check: Try parsing locally first
                val fastResult = ChatActionParser.parse(
                    message = canonicalUserMessage.ifBlank { text },
                    contextSummary = enrichedContextSummary,
                    selectedNodeCode = selectedNodeCode,
                    normalizationContext = enrichedNormalizationContext,
                    selectedRouteCode = selectedRouteCode
                )
                val fastClarificationPrompt = fastResult.clarificationPrompt
                if (fastClarificationPrompt != null) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            clarificationPrompt = fastClarificationPrompt,
                            messages = it.messages + GemmaChatMessage("assistant", fastResult.answer)
                        )
                    }
                    persistMessage(safeProjectId, "assistant", fastResult.answer)
                    return@launch
                }

                val fastPendingAction = fastResult.pendingAction
                val deterministicAnswer = formatDeterministicReadAnswer(
                    query = canonicalUserMessage.ifBlank { text },
                    workspaceSnapshot = workspaceSnapshot,
                    ragResult = ragResult,
                    selectedNodeCode = selectedNodeCode,
                    selectedRouteCode = selectedRouteCode
                )
                if (deterministicAnswer != null && (fastPendingAction == null || fastPendingAction.type == ChatActionType.GENERATE_SUMMARY)) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            pendingAction = null,
                            messages = it.messages + GemmaChatMessage("assistant", deterministicAnswer)
                        )
                    }
                    persistMessage(safeProjectId, "assistant", deterministicAnswer)
                    return@launch
                }

                if (fastPendingAction != null && fastResult.writeDisposition != com.mapsupervision.domain.ai.WriteDisposition.REJECT) {
                    val finalPendingAction = if (fastPendingAction.type == ChatActionType.GENERATE_SUMMARY) null else fastPendingAction
                    val summaryReq = fastPendingAction.summaryRequest
                    val displayAnswer = if (fastPendingAction.type == ChatActionType.GENERATE_SUMMARY && summaryReq != null) {
                        formatSummaryMarkdown(summaryAggregator.aggregate(summaryReq))
                    } else {
                        fastResult.answer
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            pendingAction = finalPendingAction,
                            messages = it.messages + GemmaChatMessage("assistant", displayAnswer)
                        )
                    }
                    persistMessage(safeProjectId, "assistant", displayAnswer)
                    if (finalPendingAction != null) {
                        logAction(safeProjectId, text, fastResult, "DRAFT_CREATED")
                    }
                    return@launch
                }

                // 2. Heavy path (Model/Cloud)
                val finalResult: ChatAssistantResult = try {
                    val model = _uiState.value.selectedModel
                    if (model != null && modelManager.isModelDownloadComplete(model) && modelManager.canInitializeLiteRt(model)) {
                        val init = chatController.initialize(model)
                        _uiState.update { state ->
                            state.copy(
                                chatReady = init.ready,
                                chatStatus = init.warning.ifBlank { init.message },
                                lastError = if (init.ready) "" else init.message
                            )
                        }
                        if (!init.ready) {
                            throw GemmaLiteRtChatService.DiagnosticFailure("INIT_FAILED", init.message)
                        }
                        val llmReplyText = chatController.sendPrompt(
                            model = model,
                            history = _uiState.value.messages
                                .dropLast(1)
                                .takeLast(CHAT_HISTORY_LIMIT)
                                .map { GemmaLiteRtChatService.ChatMessage(it.role, it.text) },
                            contextSummary = enrichedContextSummary,
                            normalizationContext = enrichedNormalizationContext,
                            retrievedContext = ragPrompt,
                            currentTab = tab,
                            selectedNodeCode = selectedNodeCode,
                            selectedRouteCode = selectedRouteCode,
                            userMessage = canonicalUserMessage.ifBlank { text }
                        ).text

                        ChatActionParser.parseLlmResponse(
                            llmResponse = llmReplyText,
                            selectedNodeCode = selectedNodeCode,
                            normalizationContext = normalizationContext,
                            selectedRouteCode = selectedRouteCode
                        )
                    } else {
                        val orchestratorDecision = aiOrchestrator.execute<ChatAssistantResult>(
                            ChatAssistantPayload(
                                projectId = safeProjectId,
                                currentTab = tab,
                                message = canonicalUserMessage.ifBlank { text },
                                contextSummary = enrichedContextSummary,
                                normalizationContext = enrichedNormalizationContext,
                                retrievedContext = ragPrompt,
                                selectedNodeCode = selectedNodeCode,
                                selectedRouteCode = selectedRouteCode
                            )
                        )
                        orchestratorDecision.result
                    }
                } catch (error: Throwable) {
                    val errorMsg = error.message ?: "Không thể xử lý yêu cầu."
                    _uiState.update { state -> state.copy(lastError = error.message.orEmpty(), chatReady = false) }
                    ChatAssistantResult(answer = errorMsg)
                }

                val finalResultPendingAction = finalResult.pendingAction
                val finalPendingAction = if (finalResultPendingAction?.type == ChatActionType.GENERATE_SUMMARY) null else finalResultPendingAction
                val finalSummaryReq = finalResultPendingAction?.summaryRequest
                val displayAnswer = if (finalResultPendingAction?.type == ChatActionType.GENERATE_SUMMARY && finalSummaryReq != null) {
                    formatSummaryMarkdown(summaryAggregator.aggregate(finalSummaryReq))
                } else {
                    finalResult.answer
                }

                val finalClarificationPrompt = finalResult.clarificationPrompt
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingAction = finalPendingAction,
                        clarificationPrompt = finalClarificationPrompt,
                        messages = it.messages + GemmaChatMessage("assistant", displayAnswer)
                    )
                }
                persistMessage(safeProjectId, "assistant", displayAnswer)
                if (finalPendingAction != null) {
                    logAction(safeProjectId, text, finalResult, "DRAFT_CREATED")
                }
            } finally {
                activeSendJob = null
                _uiState.update { state -> state.copy(isBusy = false) }
            }
        }
    }

    private fun formatSummaryMarkdown(rows: List<com.mapsupervision.domain.ai.SummaryRow>): String {
        if (rows.isEmpty()) return "Không tìm thấy dữ liệu tổng hợp theo yêu cầu."
        return buildString {
            append("### Kết quả tổng hợp:\n\n")
            append("| Nhóm | Tổng số node | Đã hoàn thành | Tiến độ TB | Số node trễ | Tổng khối lượng |\n")
            append("| --- | --- | --- | --- | --- | --- |\n")
            rows.forEach { row ->
                append("| ${row.groupKey} | ${row.totalNodes} | ${row.completedNodes} | ${String.format(java.util.Locale.US, "%.1f%%", row.avgProgress)} | ${row.delayedCount} | ${String.format(java.util.Locale.US, "%.1f", row.totalVolume)} |\n")
            }
        }
    }

    fun selectClarificationOption(
        option: ChatIntentOption,
        normalizationContext: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ) {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == "user" }?.text ?: ""
        if (lastUserMessage.isBlank()) return
        _uiState.update { it.copy(clarificationPrompt = null, isBusy = true) }
        viewModelScope.launch {
            try {
                val safeProj = activeProjectId ?: "P1"
                val result = ChatActionParser.parse(
                    message = lastUserMessage,
                    selectedNodeCode = selectedNodeCode,
                    normalizationContext = normalizationContext,
                    selectedRouteCode = selectedRouteCode,
                    explicitAction = option.type
                )
                val pending = result.pendingAction
                val finalPendingAction = if (pending?.type == ChatActionType.GENERATE_SUMMARY) null else pending
                val summaryReq = pending?.summaryRequest
                val displayAnswer = if (pending?.type == ChatActionType.GENERATE_SUMMARY && summaryReq != null) {
                    formatSummaryMarkdown(summaryAggregator.aggregate(summaryReq))
                } else {
                    result.answer
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingAction = finalPendingAction,
                        messages = it.messages + GemmaChatMessage("assistant", displayAnswer)
                    )
                }
                persistMessage(safeProj, "assistant", displayAnswer)
                if (finalPendingAction != null) {
                    logAction(safeProj, lastUserMessage, result, "DRAFT_CREATED")
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun logAction(projectId: String?, rawInput: String, result: ChatAssistantResult, status: String) {
        val pending = result.pendingAction ?: return
        val safeProj = projectId ?: activeProjectId ?: "P1"
        aiActionLogRepository.log(
            AiActionLog(
                id = pending.actionId,
                projectId = safeProj,
                rawInput = rawInput,
                actionType = pending.type.name,
                draftJson = pending.draftJson,
                confidence = result.confidence?.overallConfidence ?: 100,
                status = status,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun confirmPendingAction(
        workspaceViewModel: WorkspaceViewModel
    ) {
        val action = _uiState.value.pendingAction ?: return
        val safeProj = activeProjectId ?: "P1"
        viewModelScope.launch {
            // 1. Log CONFIRMED
            aiActionLogRepository.log(
                AiActionLog(
                    id = action.actionId,
                    projectId = safeProj,
                    rawInput = "",
                    actionType = action.type.name,
                    draftJson = action.draftJson,
                    confidence = 100,
                    status = "CONFIRMED",
                    timestamp = System.currentTimeMillis()
                )
            )

            val success = runCatching {
                when (action.type) {
                    ChatActionType.UPDATE_CONSTRUCTION_PROGRESS -> {
                        val draft = action.constructionProgress ?: return@launch
                        workspaceViewModel.addConstructionProgress(draft.nodeCode, draft.planned, draft.actual)
                    }
                    ChatActionType.ADD_DAILY_LOG -> {
                        val draft = action.dailyLog ?: return@launch
                        workspaceViewModel.addDailyLog(
                            workItem = draft.workItem,
                            manpower = draft.manpower,
                            note = draft.note,
                            weather = draft.weather,
                            temperature = draft.temperature,
                            nodeCode = draft.nodeCode,
                            routeCode = draft.routeCode,
                            dateEpochDay = draft.dateEpochDay,
                            volume = draft.volume,
                            unit = draft.unit,
                            categoryName = draft.categoryName
                        )
                    }
                    ChatActionType.UPDATE_SITE_PHOTO -> {
                        val draft = action.sitePhotoUpdate ?: return@launch
                        workspaceViewModel.updateSitePhoto(
                            photoId = draft.photoId,
                            tagCodesCsv = draft.tagCodesCsv,
                            matchedNodeCode = draft.matchedNodeCode,
                            lat = draft.latitude,
                            lon = draft.longitude
                        )
                    }
                    ChatActionType.SAVE_REPORT_DRAFT -> {
                        val draft = action.reportDraftSave ?: return@launch
                        workspaceViewModel.saveReportDraft(
                            title = draft.title,
                            executiveSummary = draft.executiveSummary,
                            riskSection = draft.riskSection,
                            recommendedActions = draft.recommendedActions
                        )
                    }
                    ChatActionType.ADD_NOTE -> {
                        val draft = action.noteDraft ?: return@launch
                        workspaceViewModel.addNote(draft.objectCode, draft.content)
                    }
                    ChatActionType.ADD_TASK -> {
                        val draft = action.taskDraft ?: return@launch
                        workspaceViewModel.addTask(draft.objectCode, draft.title)
                    }
                    ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS -> {
                        val draft = action.materialOrVolumeProgress ?: return@launch
                        workspaceViewModel.updateWorkVolumeProgress(draft.nodeCode, draft.materialName, draft.actualQty.toString())
                    }
                    ChatActionType.ADD_WORK_PLAN -> {
                        val draft = action.workPlan ?: return@launch
                        workspaceViewModel.addWorkPlanWithTask(
                            plannedDateEpochDay = draft.plannedDateEpochDay,
                            title = draft.title,
                            description = draft.description,
                            nodeCode = draft.nodeCode,
                            routeCode = draft.routeCode,
                            sourceRawInput = draft.title
                        )
                    }
                    ChatActionType.GENERATE_SUMMARY -> {
                        // Summary requests are read-only
                    }
                }
            }.isSuccess

            // 2. Log COMMITTED / FAILED
            val finalStatus = if (success) "COMMITTED" else "FAILED"
            aiActionLogRepository.log(
                AiActionLog(
                    id = action.actionId,
                    projectId = safeProj,
                    rawInput = "",
                    actionType = action.type.name,
                    draftJson = action.draftJson,
                    confidence = 100,
                    status = finalStatus,
                    timestamp = System.currentTimeMillis()
                )
            )

            _uiState.update { it.copy(pendingAction = null) }
        }
    }

    fun dismissPendingAction() {
        val action = _uiState.value.pendingAction
        if (action != null) {
            val safeProj = activeProjectId ?: "P1"
            viewModelScope.launch {
                aiActionLogRepository.log(
                    AiActionLog(
                        id = action.actionId,
                        projectId = safeProj,
                        rawInput = "",
                        actionType = action.type.name,
                        draftJson = action.draftJson,
                        confidence = 100,
                        status = "REJECTED",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        _uiState.update { it.copy(pendingAction = null) }
    }

    fun updatePendingDailyLogDraft(transform: (DailyLogDraft) -> DailyLogDraft) {
        _uiState.update { state ->
            val action = state.pendingAction ?: return@update state
            val draft = action.dailyLog ?: return@update state
            val updated = transform(draft)
            state.copy(
                pendingAction = action.copy(
                    dailyLog = updated,
                    draftJson = buildDailyLogDraftJson(updated)
                )
            )
        }
    }

    private fun buildDailyLogDraftJson(draft: DailyLogDraft): String {
        val nodeCode = draft.nodeCode
        val routeCode = draft.routeCode
        return buildString {
            append("{")
            append("\"workItem\":\"").append(escapeJson(draft.workItem)).append("\",")
            append("\"manpower\":").append(draft.manpower).append(",")
            append("\"note\":\"").append(escapeJson(draft.note)).append("\",")
            append("\"weather\":\"").append(escapeJson(draft.weather)).append("\",")
            append("\"temperature\":").append(draft.temperature).append(",")
            append("\"nodeCode\":")
            if (nodeCode == null) append("null") else append("\"").append(escapeJson(nodeCode)).append("\"")
            append(",")
            append("\"routeCode\":")
            if (routeCode == null) append("null") else append("\"").append(escapeJson(routeCode)).append("\"")
            append(",")
            append("\"dateEpochDay\":").append(draft.dateEpochDay).append(",")
            append("\"date\":\"").append(escapeJson(DailyLogDateResolver.formatEpochDay(draft.dateEpochDay))).append("\",")
            append("\"volume\":").append(draft.volume).append(",")
            append("\"unit\":\"").append(escapeJson(draft.unit)).append("\",")
            append("\"categoryName\":\"").append(escapeJson(draft.categoryName)).append("\"")
            append("}")
        }
    }

    fun updatePendingWorkPlanDraft(transform: (com.mapsupervision.domain.ai.WorkPlanDraft) -> com.mapsupervision.domain.ai.WorkPlanDraft) {
        _uiState.update { state ->
            val action = state.pendingAction ?: return@update state
            val draft = action.workPlan ?: return@update state
            val updated = transform(draft)
            state.copy(
                pendingAction = action.copy(
                    workPlan = updated,
                    draftJson = buildWorkPlanDraftJson(updated)
                )
            )
        }
    }

    private fun buildWorkPlanDraftJson(draft: com.mapsupervision.domain.ai.WorkPlanDraft): String {
        val nodeCode = draft.nodeCode
        val routeCode = draft.routeCode
        return buildString {
            append("{")
            append("\"plannedDateEpochDay\":").append(draft.plannedDateEpochDay).append(",")
            append("\"plannedDate\":\"").append(escapeJson(DailyLogDateResolver.formatEpochDay(draft.plannedDateEpochDay))).append("\",")
            append("\"title\":\"").append(escapeJson(draft.title)).append("\",")
            append("\"description\":\"").append(escapeJson(draft.description)).append("\",")
            append("\"nodeCode\":")
            if (nodeCode == null) append("null") else append("\"").append(escapeJson(nodeCode)).append("\"")
            append(",")
            append("\"routeCode\":")
            if (routeCode == null) append("null") else append("\"").append(escapeJson(routeCode)).append("\"")
            append("}")
        }
    }

    private fun startDownload(model: GemmaModelInfo) {
        _uiState.update {
            it.copy(
                isBusy = false,
                chatReady = false,
                chatStatus = "",
                lastError = "",
                downloadFailureCode = "",
                downloadFailureReason = "",
                downloadHttpCode = 0
            )
        }
        GemmaModelDownloadWorker.start(context, model.downloadFileName)
    }

    private fun applyDownloadState(state: GemmaDownloadState) {
        val selectedModel = _uiState.value.selectedModel
        if (selectedModel == null) return
        if (state != GemmaDownloadState.Idle && modelIdOf(state) != selectedModel.downloadFileName) return

        _uiState.update { current ->
            when (state) {
                GemmaDownloadState.Idle -> current.copy(
                    downloadProgress = 0,
                    downloadMessage = "",
                    downloadFailureCode = "",
                    downloadFailureReason = "",
                    downloadHttpCode = 0,
                    modelStatus = modelManager.status(selectedModel)
                )
                is GemmaDownloadState.Running -> current.copy(
                    downloadProgress = progressOf(state.bytesDownloaded, state.totalBytes),
                    downloadMessage = if (state.warningMessage.isNotBlank()) {
                        state.warningMessage
                    } else {
                        "Đang tải ${progressOf(state.bytesDownloaded, state.totalBytes)}%"
                    },
                    downloadFailureCode = state.warningCode,
                    downloadFailureReason = state.warningMessage,
                    downloadHttpCode = state.httpCode,
                    modelStatus = GemmaModelStatus.DOWNLOADING,
                    chatReady = false,
                    chatStatus = ""
                )
                is GemmaDownloadState.Completed -> {
                    current.copy(
                        downloadProgress = 100,
                        downloadMessage = "Tải xong",
                        downloadFailureCode = "",
                        downloadFailureReason = "",
                        downloadHttpCode = 0,
                        modelStatus = if (modelManager.isModelDownloadComplete(selectedModel)) GemmaModelStatus.READY else GemmaModelStatus.LOAD_FAILED,
                        chatStatus = "",
                        lastError = ""
                    )
                }
                is GemmaDownloadState.Failed -> current.copy(
                    downloadProgress = progressOf(state.bytesDownloaded, state.totalBytes),
                    downloadMessage = "Tải thất bại",
                    downloadFailureCode = state.errorCode,
                    downloadFailureReason = state.message,
                    downloadHttpCode = state.httpCode,
                    modelStatus = GemmaModelStatus.LOAD_FAILED,
                    lastError = state.message,
                    chatReady = false
                )
            }
        }
    }

    private fun progressOf(downloaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun trimContext(contextSummary: String): String {
        val normalized = contextSummary.trim()
        if (normalized.length <= CONTEXT_CHAR_LIMIT) return normalized
        return normalized.take(CONTEXT_CHAR_LIMIT)
    }

    private suspend fun buildRagContext(
        projectId: String?,
        query: String,
        workspaceSnapshot: WorkspaceSnapshot?,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ): RagBuildResult? {
        val safeProjectId = projectId?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            ragContextBuilder.build(
                RagBuildRequest(
                    projectId = safeProjectId,
                    query = query,
                    workspaceSnapshot = workspaceSnapshot,
                    selectedNodeCode = selectedNodeCode,
                    selectedRouteCode = selectedRouteCode
                )
            )
        }.getOrNull()
    }

    private fun formatDeterministicReadAnswer(
        query: String,
        workspaceSnapshot: WorkspaceSnapshot?,
        ragResult: RagBuildResult?,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ): String? {
        if (isWriteLikeQuery(query)) return null
        val domain = ragResult?.queryDomain ?: RagQueryDomain.infer(query)
        if (domain == RagQueryDomain.GENERAL) return null
        return RagChatAnswerFormatter.format(
            query = query,
            snapshot = workspaceSnapshot,
            domain = domain,
            selectedNodeCode = selectedNodeCode,
            selectedRouteCode = selectedRouteCode
        )
    }

    private fun isWriteLikeQuery(query: String): Boolean {
        val normalized = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(java.util.Locale.US)
        return listOf(
            "cap nhat",
            "them",
            "tao",
            "lap ke hoach",
            "ghi nhat ky",
            "luu",
            "sua",
            "xoa"
        ).any { normalized.contains(it) }
    }

    private fun mergeContext(base: String, extra: String): String {
        return listOf(base.trim(), extra.trim()).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun mergeNormalization(base: String, extra: String): String {
        val normalizedExtra = extra.trim().let { value ->
            if (value.isBlank()) "" else if (value.startsWith("resolved_refs=")) value else "resolved_refs=$value"
        }
        return listOf(base.trim(), normalizedExtra).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun loadHistory(projectId: String?) {
        if (projectId.isNullOrBlank()) {
            _uiState.update { it.copy(messages = emptyList(), pendingAction = null) }
            return
        }
        viewModelScope.launch {
            val result = chatHistoryRepository.listRecentByProject(projectId, 50)
            val messages = (result as? com.mapsupervision.core.result.AppResult.Success)?.data.orEmpty()
                .map { ChatHistoryMessage -> GemmaChatMessage(ChatHistoryMessage.role, ChatHistoryMessage.text, ChatHistoryMessage.id) }
            _uiState.update { it.copy(messages = messages, pendingAction = null) }
        }
    }

    private suspend fun persistMessage(projectId: String?, role: String, text: String) {
        if (projectId.isNullOrBlank()) return
        chatHistoryRepository.append(
            ChatHistoryMessage(
                id = java.util.UUID.randomUUID().toString(),
                projectId = projectId,
                role = role,
                text = text,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun modelIdOf(state: GemmaDownloadState): String? = when (state) {
        GemmaDownloadState.Idle -> null
        is GemmaDownloadState.Running -> state.modelId
        is GemmaDownloadState.Completed -> state.modelId
        is GemmaDownloadState.Failed -> state.modelId
    }

    private fun isCellularNetwork(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}

