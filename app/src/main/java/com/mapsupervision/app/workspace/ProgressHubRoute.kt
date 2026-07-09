package com.mapsupervision.app.workspace

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.resolveEpochDay
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ProgressHubRoute(
    activeProjectId: String?,
    constructionProgress: List<com.mapsupervision.domain.model.NodeProgress>,
    dailyLogs: List<com.mapsupervision.domain.model.DailyLog>,
    dashboardState: DashboardState,
    progressUiState: ProgressUiState,
    workCategories: List<com.mapsupervision.domain.model.WorkCategory>,
    workPlans: List<WorkPlan>,
    projectTasks: List<Task>,
    photos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    activeProjectName: String? = null,
    initialDateMillis: Long? = null,
    onAddConstruction: (String, Float, Float) -> Unit,
    onAddDailyLog: (AddDailyLogRequest) -> Unit,
    onAddWorkCategory: (String, String) -> Unit,
    onAddWorkPlanBatch: suspend (String, List<String>, List<String>, Double, String, String, Long, String?) -> Boolean,
    onFetchWeatherAuto: (String?, String?, (String, Double) -> Unit) -> Unit,
    viewModel: ProgressHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialDateMillis) {
        if (initialDateMillis != null) {
            viewModel.updateSelectedDateMillis(initialDateMillis)
            viewModel.setSubTab(ProgressHubSubTab.DIARY)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = initialDateMillis }
            viewModel.updateCurrentMonth(cal.get(java.util.Calendar.MONTH))
            viewModel.updateCurrentYear(cal.get(java.util.Calendar.YEAR))
        }
    }

    LaunchedEffect(uiState.selectedNodeCodeForLog, uiState.selectedRouteCodeForLog, uiState.selectedDateMillis) {
        onFetchWeatherAuto(uiState.selectedNodeCodeForLog, uiState.selectedRouteCodeForLog) { cond, temp ->
            viewModel.updateWeatherSelection(cond)
            viewModel.updateTemperatureInput(temp.toInt().toString())
        }
    }

    LaunchedEffect(uiState.subTab, uiState.selectedDateMillis, workPlans) {
        if (uiState.subTab != ProgressHubSubTab.DIARY) return@LaunchedEffect
        if (uiState.editingDailyLogId != null) return@LaunchedEffect
        if (uiState.selectedPlanSnapshot != null) return@LaunchedEffect
        if (uiState.workItemInput.isNotBlank()) return@LaunchedEffect
        if (uiState.selectedCategoryName.isNotBlank()) return@LaunchedEffect
        if (uiState.volumeInput.isNotBlank()) return@LaunchedEffect
        if (uiState.unitInput.isNotBlank()) return@LaunchedEffect

        val selectedEpochDay = java.time.Instant.ofEpochMilli(uiState.selectedDateMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()

        val suggestedPlan = workPlans.firstOrNull { it.plannedDateEpochDay == selectedEpochDay }
        if (suggestedPlan != null) {
            viewModel.applyPlanToLog(suggestedPlan)
        }
    }

    ProgressHubScreen(
        activeProjectId = activeProjectId,
        constructionProgress = constructionProgress,
        dailyLogs = dailyLogs,
        workPlans = workPlans,
        dashboardState = dashboardState,
        progressUiState = progressUiState,
        screenUiState = uiState,
        workCategories = workCategories,
        projectTasks = projectTasks,
        photos = photos,
        activeProjectName = activeProjectName,
        onSetSubTab = viewModel::setSubTab,
        onUpdateGroupMode = viewModel::updateGroupMode,
        onUpdateFilterMode = viewModel::updateFilterMode,
        onSelectProgressNode = viewModel::selectProgressNode,
        onDismissProgressNodeSheet = viewModel::dismissProgressNodeSheet,
        onUpdateCurrentMonth = viewModel::updateCurrentMonth,
        onUpdateCurrentYear = viewModel::updateCurrentYear,
        onUpdateSelectedDateMillis = viewModel::updateSelectedDateMillis,
        onUpdateWeatherSelection = viewModel::updateWeatherSelection,
        onUpdateCustomWeather = viewModel::updateCustomWeather,
        onUpdateTemperatureInput = viewModel::updateTemperatureInput,
        onUpdateSelectedNodeCodeForLog = viewModel::updateSelectedNodeCodeForLog,
        onUpdateSelectedRouteCodeForLog = viewModel::updateSelectedRouteCodeForLog,
        onUpdateManpowerInput = viewModel::updateManpowerInput,
        onUpdateWorkItemInput = viewModel::updateWorkItemInput,
        onUpdateNoteInput = viewModel::updateNoteInput,
        onUpdateActualProgressInput = viewModel::updateActualProgressInput,
        onUpdateActualProgressChecked = viewModel::updateActualProgressChecked,
        onUpdateLogFormError = viewModel::updateLogFormError,
        onSetNodeDropdownExpanded = viewModel::setNodeDropdownExpanded,
        onSetRouteDropdownExpanded = viewModel::setRouteDropdownExpanded,
        onUpdateVolumeInput = viewModel::updateVolumeInput,
        onUpdateUnitInput = viewModel::updateUnitInput,
        onAddActualLine = viewModel::addActualLine,
        onRemoveActualLine = viewModel::removeActualLine,
        onUpdateActualLineWorkName = viewModel::updateActualLineWorkName,
        onUpdateActualLineCategoryName = viewModel::updateActualLineCategoryName,
        onUpdateActualLineQuantityInput = viewModel::updateActualLineQuantityInput,
        onUpdateActualLineUnit = viewModel::updateActualLineUnit,
        onSelectWorkTemplate = viewModel::selectWorkTemplate,
        onUpdateSelectedCategoryName = viewModel::updateSelectedCategoryName,
        onSetCategoryDropdownExpanded = viewModel::setCategoryDropdownExpanded,
        onSetShowAddCategoryDialog = viewModel::setShowAddCategoryDialog,
        onUpdateNewCategoryName = viewModel::updateNewCategoryName,
        onUpdateNewCategoryUnit = viewModel::updateNewCategoryUnit,
        onUpdateProgressSheetPlannedInput = viewModel::updateProgressSheetPlannedInput,
        onUpdateProgressSheetActualInput = viewModel::updateProgressSheetActualInput,
        onUpdateProgressSheetValidationError = viewModel::updateProgressSheetValidationError,
        onUpdateProgressSheetNote = viewModel::updateProgressSheetNote,
        onResetLogForm = viewModel::resetLogForm,
        onAddConstruction = onAddConstruction,
        onAddDailyLog = onAddDailyLog,
        onExportDailyLogs = { request ->
            coroutineScope.launch {
                viewModel.updateIsExporting(true)
                viewModel.updateExportValidationError("")
                val result = runCatching {
                    val range = resolveExportRange(request, dailyLogs, workPlans, photos)
                    val filteredLogs = dailyLogs.filter { log ->
                        log.projectId == request.projectId &&
                            log.resolveEpochDay() in range.first..range.last
                    }
                    val filteredPlans = workPlans.filter { plan ->
                        plan.projectId == request.projectId &&
                            plan.plannedDateEpochDay in range.first..range.last
                    }
                    val filteredPhotos = photos.filter { photo ->
                        photo.projectId == request.projectId &&
                            photo.captureEpochDay() in range.first..range.last
                    }
                    val scopeLabel = if (request.scope == DailyLogExportScope.ALL) {
                        "Toàn bộ nhật ký"
                    } else {
                        "${formatEpochDay(range.first)} - ${formatEpochDay(range.last)}"
                    }
                    val routeLabels = progressUiState.routeSelectorOptions.associate { it.key to it.label }

                    when (request.format) {
                        DailyLogExportFormat.PDF -> {
                            DailyLogPdfExporter.export(
                                projectId = request.projectId,
                                scopeLabel = scopeLabel,
                                dailyLogs = filteredLogs,
                                workPlans = filteredPlans,
                                progress = constructionProgress,
                                nodes = progressUiState.nonStructuralNodes,
                                photos = filteredPhotos,
                                routeLabels = routeLabels,
                                includePlan = request.includePlan
                            ).getOrThrow()
                        }

                        DailyLogExportFormat.DOCX -> {
                            DailyLogDocxExporter.export(
                                context = context,
                                projectId = request.projectId,
                                scopeLabel = scopeLabel,
                                dailyLogs = filteredLogs,
                                workPlans = filteredPlans,
                                progress = constructionProgress,
                                nodes = progressUiState.nonStructuralNodes,
                                photos = filteredPhotos,
                                routeLabels = routeLabels,
                                includePlan = request.includePlan
                            )
                        }
                    }
                }
                viewModel.updateIsExporting(false)
                result.fold(
                    onSuccess = { file ->
                        viewModel.setShowExportDialog(false)
                        openExportedFile(context, file)
                    },
                    onFailure = { error ->
                        viewModel.updateExportValidationError(error.message ?: "Không thể xuất nhật ký")
                    }
                )
            }
        },
        onAddWorkCategory = onAddWorkCategory,
        onEditDailyLog = { log ->
            val nodeProgress = log.nodeCode?.let { code ->
                progressUiState.progressByNodeCode[code]
            }
            val initialActual = nodeProgress?.actual?.let {
                if (it > 0f) it.toString() else ""
            } ?: ""
            viewModel.startEditingDailyLog(log, initialActual)
        },
        onSetShowExportDialog = viewModel::setShowExportDialog,
        onUpdateExportScope = viewModel::updateExportScope,
        onUpdateExportStartDateMillis = viewModel::updateExportStartDateMillis,
        onUpdateExportEndDateMillis = viewModel::updateExportEndDateMillis,
        onUpdateExportFormat = viewModel::updateExportFormat,
        onUpdateIncludePlanInExport = viewModel::updateIncludePlanInExport,
        onUpdateExportValidationError = viewModel::updateExportValidationError,

        // Plan actions
        onAddWorkPlanBatch = onAddWorkPlanBatch,
        onUpdateSelectedPlanWorkName = viewModel::updateSelectedPlanWorkName,
        onUpdatePlanUnitInput = viewModel::updatePlanUnitInput,
        onUpdatePlanQuantityInput = viewModel::updatePlanQuantityInput,
        onUpdatePlanNoteInput = viewModel::updatePlanNoteInput,
        onAddSelectedPlanNodeCode = viewModel::addSelectedPlanNodeCode,
        onRemoveSelectedPlanNodeCode = viewModel::removeSelectedPlanNodeCode,
        onAddSelectedPlanRouteCode = viewModel::addSelectedPlanRouteCode,
        onRemoveSelectedPlanRouteCode = viewModel::removeSelectedPlanRouteCode,
        onSetPlanNodeDropdownExpanded = viewModel::setPlanNodeDropdownExpanded,
        onSetPlanRouteDropdownExpanded = viewModel::setPlanRouteDropdownExpanded,
        onSetPlanWorkDropdownExpanded = viewModel::setPlanWorkDropdownExpanded,
        onSetShowAddPlanWorkDialog = viewModel::setShowAddPlanWorkDialog,
        onUpdateNewPlanWorkName = viewModel::updateNewPlanWorkName,
        onUpdateNewPlanWorkUnit = viewModel::updateNewPlanWorkUnit,
        onUpdatePlanFormError = viewModel::updatePlanFormError,
        onSelectPlanWorkTemplate = { name, unit, taskId -> viewModel.selectPlanWorkTemplate(name, unit, taskId) },
        onApplyPlanToLog = viewModel::applyPlanToLog,
        onResetPlanForm = viewModel::resetPlanForm
    )
}

private fun resolveExportRange(
    request: ExportDailyLogRequest,
    dailyLogs: List<com.mapsupervision.domain.model.DailyLog>,
    workPlans: List<WorkPlan>,
    photos: List<com.mapsupervision.domain.model.SitePhoto>
): LongRange {
    return if (request.scope == DailyLogExportScope.DATE_RANGE) {
        val start = request.startEpochDay ?: request.endEpochDay ?: 0L
        val end = request.endEpochDay ?: request.startEpochDay ?: start
        start..end
    } else {
        val allEpochDays = buildList {
            addAll(
                dailyLogs
                    .filter { it.projectId == request.projectId }
                    .map { it.resolveEpochDay() }
            )
            addAll(
                workPlans
                    .filter { it.projectId == request.projectId }
                    .map { it.plannedDateEpochDay }
            )
            addAll(
                photos
                    .filter { it.projectId == request.projectId }
                    .map { it.captureEpochDay() }
            )
        }
            .filter { it > 0L }
            .sorted()

        val start = allEpochDays.firstOrNull() ?: java.time.LocalDate.now().toEpochDay()
        val end = allEpochDays.lastOrNull() ?: start
        start..end
    }
}

private fun formatEpochDay(epochDay: Long): String {
    return java.time.LocalDate.ofEpochDay(epochDay)
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

private fun com.mapsupervision.domain.model.SitePhoto.captureEpochDay(): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = capturedAtEpochMs }
    return java.time.LocalDate.of(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).toEpochDay()
}

private fun openExportedFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
