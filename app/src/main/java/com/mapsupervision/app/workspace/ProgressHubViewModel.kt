package com.mapsupervision.app.workspace

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.mapsupervision.domain.model.resolveEpochDay

enum class ProgressHubSubTab {
    PROGRESS,
    PLAN,
    DIARY
}

data class ProgressHubScreenUiState(
    val subTab: ProgressHubSubTab = ProgressHubSubTab.PROGRESS,
    val groupMode: String = "Nhà thầu",
    val filterMode: String = "All",
    val selectedNodeCodeForProgress: String? = null,
    val currentMonth: Int,
    val currentYear: Int,
    val selectedDateMillis: Long,
    val weatherSelected: String = "Nắng",
    val customWeather: String = "",
    val temperatureInput: String = "30",
    val selectedNodeCodeForLog: String? = null,
    val selectedRouteCodeForLog: String? = null,
    val manpowerInput: String = "5",
    val workItemInput: String = "",
    val noteInput: String = "",
    val actualProgressInput: String = "",
    val actualProgressChecked: Boolean = false,
    val logFormError: String = "",
    val nodeDropdownExpanded: Boolean = false,
    val routeDropdownExpanded: Boolean = false,
    val volumeInput: String = "",
    val unitInput: String = "",
    val selectedCategoryName: String = "",
    val actualLines: List<DailyLogDraftLine> = listOf(defaultDailyLogDraftLine()),
    val categoryDropdownExpanded: Boolean = false,
    val showAddCategoryDialog: Boolean = false,
    val newCategoryName: String = "",
    val newCategoryUnit: String = "",
    val progressSheetPlannedInput: String = "",
    val progressSheetActualInput: String = "",
    val progressSheetValidationError: String = "",
    val progressSheetNote: String = "",
    val editingDailyLogId: String? = null,
    val selectedPlanForLogId: String? = null,
    val selectedPlanSnapshot: DailyLogPlanSnapshotDraft? = null,
    val isCalendarSyncEnabled: Boolean = false,
    val systemEvents: List<com.mapsupervision.app.sync.SystemEvent> = emptyList(),
    val showExportDialog: Boolean = false,
    val exportScope: DailyLogExportScope = DailyLogExportScope.DATE_RANGE,
    val exportStartDateMillis: Long,
    val exportEndDateMillis: Long,
    val exportFormat: DailyLogExportFormat = DailyLogExportFormat.PDF,
    val includePlanInExport: Boolean = true,
    val isExporting: Boolean = false,
    val exportValidationError: String = "",

    // Plan form state properties
    val selectedPlanWorkName: String = "",
    val selectedPlanTaskId: String? = null,
    val planUnitInput: String = "",
    val planQuantityInput: String = "",
    val planNoteInput: String = "",
    val selectedPlanNodeCodes: List<String> = emptyList(),
    val selectedPlanRouteCodes: List<String> = emptyList(),
    val planNodeDropdownExpanded: Boolean = false,
    val planRouteDropdownExpanded: Boolean = false,
    val planWorkDropdownExpanded: Boolean = false,
    val showAddPlanWorkDialog: Boolean = false,
    val newPlanWorkName: String = "",
    val newPlanWorkUnit: String = "",
    val planFormError: String = ""
)

@HiltViewModel
class ProgressHubViewModel @Inject constructor() : ViewModel() {

    private val now = java.util.Calendar.getInstance()
    private val defaultSelectedDateMillis = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val _uiState = MutableStateFlow(
        ProgressHubScreenUiState(
            currentMonth = now.get(java.util.Calendar.MONTH),
            currentYear = now.get(java.util.Calendar.YEAR),
            selectedDateMillis = defaultSelectedDateMillis,
            exportStartDateMillis = defaultSelectedDateMillis,
            exportEndDateMillis = defaultSelectedDateMillis
        )
    )
    val uiState: StateFlow<ProgressHubScreenUiState> = _uiState.asStateFlow()

    fun setSubTab(subTab: ProgressHubSubTab) = updateState {
        it.copy(subTab = subTab)
    }

    fun updateGroupMode(groupMode: String) = updateState {
        it.copy(groupMode = groupMode)
    }

    fun updateFilterMode(filterMode: String) = updateState {
        it.copy(filterMode = filterMode)
    }

    fun selectProgressNode(nodeCode: String, plannedInput: String, actualInput: String) {
        updateState {
            it.copy(
                selectedNodeCodeForProgress = nodeCode,
                progressSheetPlannedInput = plannedInput,
                progressSheetActualInput = actualInput,
                progressSheetValidationError = "",
                progressSheetNote = ""
            )
        }
    }

    fun dismissProgressNodeSheet() {
        updateState {
            it.copy(
                selectedNodeCodeForProgress = null,
                progressSheetPlannedInput = "",
                progressSheetActualInput = "",
                progressSheetValidationError = "",
                progressSheetNote = ""
            )
        }
    }

    fun updateCurrentMonth(month: Int) = updateState {
        it.copy(currentMonth = month)
    }

    fun updateCurrentYear(year: Int) = updateState {
        it.copy(currentYear = year)
    }

    fun updateSelectedDateMillis(selectedDateMillis: Long) = updateState {
        it.copy(
            selectedDateMillis = selectedDateMillis,
            exportStartDateMillis = if (!it.showExportDialog) selectedDateMillis else it.exportStartDateMillis,
            exportEndDateMillis = if (!it.showExportDialog) selectedDateMillis else it.exportEndDateMillis
        )
    }

    fun updateWeatherSelection(weatherSelected: String) = updateState {
        it.copy(weatherSelected = weatherSelected)
    }

    fun updateCustomWeather(customWeather: String) = updateState {
        it.copy(customWeather = customWeather)
    }

    fun updateTemperatureInput(temperatureInput: String) = updateState {
        it.copy(temperatureInput = temperatureInput)
    }

    fun updateSelectedNodeCodeForLog(nodeCode: String?) = updateState {
        it.copy(selectedNodeCodeForLog = nodeCode)
    }

    fun updateSelectedRouteCodeForLog(routeCode: String?) = updateState {
        it.copy(selectedRouteCodeForLog = routeCode)
    }

    fun updateManpowerInput(manpowerInput: String) = updateState {
        it.copy(manpowerInput = manpowerInput)
    }

    fun updateWorkItemInput(workItemInput: String) = updateState {
        it.copy(workItemInput = workItemInput)
    }

    fun updateNoteInput(noteInput: String) = updateState {
        it.copy(noteInput = noteInput)
    }

    fun updateActualProgressInput(actualProgressInput: String) = updateState {
        it.copy(actualProgressInput = actualProgressInput)
    }

    fun updateActualProgressChecked(actualProgressChecked: Boolean) = updateState {
        it.copy(actualProgressChecked = actualProgressChecked)
    }

    fun updateLogFormError(logFormError: String) = updateState {
        it.copy(logFormError = logFormError)
    }

    fun setNodeDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(nodeDropdownExpanded = expanded)
    }

    fun setRouteDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(routeDropdownExpanded = expanded)
    }

    fun updateVolumeInput(volumeInput: String) = updateState {
        it.copy(volumeInput = volumeInput)
    }

    fun updateUnitInput(unitInput: String) = updateState {
        it.copy(unitInput = unitInput)
    }

    fun addActualLine() = updateState {
        it.copy(actualLines = it.actualLines + defaultDailyLogDraftLine())
    }

    fun removeActualLine(lineId: String) = updateState {
        val updated = it.actualLines.filterNot { line -> line.id == lineId }
        it.copy(actualLines = if (updated.isEmpty()) listOf(defaultDailyLogDraftLine()) else updated)
    }

    fun updateActualLineWorkName(lineId: String, workName: String) = updateState {
        it.copy(actualLines = it.actualLines.map { line ->
            if (line.id == lineId) line.copy(workName = workName) else line
        })
    }

    fun updateActualLineCategoryName(lineId: String, categoryName: String) = updateState {
        it.copy(actualLines = it.actualLines.map { line ->
            if (line.id == lineId) line.copy(categoryName = categoryName) else line
        })
    }

    fun updateActualLineQuantityInput(lineId: String, quantityInput: String) = updateState {
        it.copy(actualLines = it.actualLines.map { line ->
            if (line.id == lineId) line.copy(quantityInput = quantityInput) else line
        })
    }

    fun updateActualLineUnit(lineId: String, unit: String) = updateState {
        it.copy(actualLines = it.actualLines.map { line ->
            if (line.id == lineId) line.copy(unit = unit) else line
        })
    }

    fun selectWorkTemplate(name: String, unit: String) = updateState {
        it.copy(
            selectedCategoryName = name,
            unitInput = unit,
            categoryDropdownExpanded = false,
            logFormError = ""
        )
    }

    fun updateSelectedCategoryName(selectedCategoryName: String) = updateState {
        it.copy(selectedCategoryName = selectedCategoryName)
    }

    fun setCategoryDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(categoryDropdownExpanded = expanded)
    }

    fun setShowAddCategoryDialog(show: Boolean) = updateState {
        it.copy(showAddCategoryDialog = show)
    }

    fun updateNewCategoryName(newCategoryName: String) = updateState {
        it.copy(newCategoryName = newCategoryName)
    }

    fun updateNewCategoryUnit(newCategoryUnit: String) = updateState {
        it.copy(newCategoryUnit = newCategoryUnit)
    }

    fun updateProgressSheetPlannedInput(plannedInput: String) = updateState {
        it.copy(progressSheetPlannedInput = plannedInput)
    }

    fun updateProgressSheetActualInput(actualInput: String) = updateState {
        it.copy(progressSheetActualInput = actualInput)
    }

    fun updateProgressSheetValidationError(validationError: String) = updateState {
        it.copy(progressSheetValidationError = validationError)
    }

    fun updateProgressSheetNote(note: String) = updateState {
        it.copy(progressSheetNote = note)
    }

    // Plan form states & actions
    fun updateSelectedPlanWorkName(name: String) = updateState {
        it.copy(selectedPlanWorkName = name, planFormError = "")
    }

    fun updatePlanUnitInput(unit: String) = updateState {
        it.copy(planUnitInput = unit)
    }

    fun updatePlanQuantityInput(qty: String) = updateState {
        it.copy(planQuantityInput = qty)
    }

    fun updatePlanNoteInput(note: String) = updateState {
        it.copy(planNoteInput = note)
    }

    fun addSelectedPlanNodeCode(code: String) = updateState {
        if (it.selectedPlanNodeCodes.contains(code)) it else it.copy(selectedPlanNodeCodes = it.selectedPlanNodeCodes + code)
    }

    fun removeSelectedPlanNodeCode(code: String) = updateState {
        it.copy(selectedPlanNodeCodes = it.selectedPlanNodeCodes - code)
    }

    fun addSelectedPlanRouteCode(code: String) = updateState {
        if (it.selectedPlanRouteCodes.contains(code)) it else it.copy(selectedPlanRouteCodes = it.selectedPlanRouteCodes + code)
    }

    fun removeSelectedPlanRouteCode(code: String) = updateState {
        it.copy(selectedPlanRouteCodes = it.selectedPlanRouteCodes - code)
    }

    fun setPlanNodeDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(planNodeDropdownExpanded = expanded)
    }

    fun setPlanRouteDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(planRouteDropdownExpanded = expanded)
    }

    fun setPlanWorkDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(planWorkDropdownExpanded = expanded)
    }

    fun setShowAddPlanWorkDialog(show: Boolean) = updateState {
        it.copy(showAddPlanWorkDialog = show)
    }

    fun updateNewPlanWorkName(name: String) = updateState {
        it.copy(newPlanWorkName = name)
    }

    fun updateNewPlanWorkUnit(unit: String) = updateState {
        it.copy(newPlanWorkUnit = unit)
    }

    fun updatePlanFormError(error: String) = updateState {
        it.copy(planFormError = error)
    }

    fun selectPlanWorkTemplate(name: String, unit: String, taskId: String? = null) = updateState {
        it.copy(
            selectedPlanWorkName = name,
            selectedPlanTaskId = taskId,
            planUnitInput = unit,
            planWorkDropdownExpanded = false,
            planFormError = ""
        )
    }

    fun resetPlanForm() = updateState {
        it.copy(
            selectedPlanWorkName = "",
            selectedPlanTaskId = null,
            planUnitInput = "",
            planQuantityInput = "",
            planNoteInput = "",
            selectedPlanNodeCodes = emptyList(),
            selectedPlanRouteCodes = emptyList(),
            planNodeDropdownExpanded = false,
            planRouteDropdownExpanded = false,
            planWorkDropdownExpanded = false,
            planFormError = ""
        )
    }

    fun startEditingDailyLog(log: com.mapsupervision.domain.model.DailyLog, initialActualProgress: String = "") {
        val selectedWeather = when (log.weather) {
            "Nắng", "Mưa", "Nhiều mây", "Giông bão" -> log.weather
            else -> "Nắng"
        }
        val epochDay = log.resolveEpochDay()
        val localDate = java.time.LocalDate.ofEpochDay(epochDay)
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, localDate.year)
            set(java.util.Calendar.MONTH, localDate.monthValue - 1)
            set(java.util.Calendar.DAY_OF_MONTH, localDate.dayOfMonth)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val localMidnight = cal.timeInMillis
        updateState {
            it.copy(
                editingDailyLogId = log.id,
                selectedPlanForLogId = log.linkedWorkPlanId,
                subTab = ProgressHubSubTab.DIARY,
                selectedDateMillis = localMidnight,
                selectedNodeCodeForLog = log.nodeCode,
                selectedRouteCodeForLog = log.routeCode,
                weatherSelected = selectedWeather,
                customWeather = if (selectedWeather == log.weather) "" else log.weather,
                temperatureInput = if (log.temperature > 0.0) log.temperature.toString() else it.temperatureInput,
                manpowerInput = log.manpower.toString(),
                workItemInput = log.workItem,
                noteInput = log.note,
                volumeInput = if (log.volume > 0.0) log.volume.toString() else "",
                unitInput = log.unit,
                selectedCategoryName = log.categoryName,
                actualProgressInput = initialActualProgress,
                actualLines = if (log.lines.isNotEmpty()) {
                    log.lines.map { line ->
                        DailyLogDraftLine(
                            id = line.id,
                            workName = line.workName,
                            categoryName = line.categoryName,
                            quantityInput = if (line.quantity > 0.0) line.quantity.toString() else "",
                            unit = line.unit,
                            nodeCode = line.nodeCode,
                            routeCode = line.routeCode
                        )
                    }
                } else {
                    listOf(
                        DailyLogDraftLine(
                            id = java.util.UUID.randomUUID().toString(),
                            workName = log.workItem,
                            categoryName = log.categoryName,
                            quantityInput = if (log.volume > 0.0) log.volume.toString() else "",
                            unit = log.unit,
                            nodeCode = log.nodeCode,
                            routeCode = log.routeCode
                        )
                    )
                },
                selectedPlanSnapshot = log.linkedWorkPlanId?.let {
                    DailyLogPlanSnapshotDraft(
                        linkedWorkPlanId = it,
                        plannedWorkName = log.plannedWorkName,
                        plannedQuantity = log.plannedQuantity,
                        plannedUnit = log.plannedUnit,
                        plannedNodeCode = log.plannedNodeCode,
                        plannedRouteCode = log.plannedRouteCode
                    )
                },
                logFormError = "",
                routeDropdownExpanded = false,
                nodeDropdownExpanded = false,
                categoryDropdownExpanded = false
            )
        }
    }

    fun resetLogForm() {
        updateState {
            it.copy(
                workItemInput = "",
                noteInput = "",
                manpowerInput = "5",
                actualProgressInput = "",
                volumeInput = "",
                unitInput = "",
                selectedCategoryName = "",
                actualLines = listOf(defaultDailyLogDraftLine()),
                selectedNodeCodeForLog = null,
                selectedRouteCodeForLog = null,
                selectedPlanForLogId = null,
                selectedPlanSnapshot = null,
                routeDropdownExpanded = false,
                nodeDropdownExpanded = false,
                categoryDropdownExpanded = false,
                editingDailyLogId = null,
                logFormError = ""
            )
        }
    }

    fun applyPlanToLog(plan: com.mapsupervision.domain.model.WorkPlan) {
        updateState {
            it.copy(
                selectedPlanForLogId = plan.id,
                selectedPlanSnapshot = DailyLogPlanSnapshotDraft(
                    linkedWorkPlanId = plan.id,
                    plannedWorkName = plan.title,
                    plannedQuantity = plan.quantity,
                    plannedUnit = plan.unit,
                    plannedNodeCode = plan.nodeCode,
                    plannedRouteCode = plan.routeCode
                ),
                workItemInput = plan.title,
                selectedCategoryName = plan.title,
                volumeInput = if (plan.quantity > 0.0) plan.quantity.toString() else "",
                unitInput = plan.unit,
                selectedNodeCodeForLog = plan.nodeCode,
                selectedRouteCodeForLog = plan.routeCode,
                actualLines = listOf(
                    DailyLogDraftLine(
                        id = java.util.UUID.randomUUID().toString(),
                        workName = plan.title,
                        categoryName = plan.title,
                        quantityInput = if (plan.quantity > 0.0) plan.quantity.toString() else "",
                        unit = plan.unit,
                        nodeCode = plan.nodeCode,
                        routeCode = plan.routeCode
                    )
                ),
                noteInput = it.noteInput.ifBlank { plan.description },
                logFormError = "",
                nodeDropdownExpanded = false,
                routeDropdownExpanded = false,
                categoryDropdownExpanded = false
            )
        }
    }

    fun setCalendarSyncEnabled(enabled: Boolean) = updateState {
        it.copy(isCalendarSyncEnabled = enabled)
    }

    fun setShowExportDialog(show: Boolean) = updateState {
        it.copy(
            showExportDialog = show,
            exportStartDateMillis = if (show) it.selectedDateMillis else it.exportStartDateMillis,
            exportEndDateMillis = if (show) it.selectedDateMillis else it.exportEndDateMillis,
            exportValidationError = if (show) "" else it.exportValidationError
        )
    }

    fun updateExportScope(scope: DailyLogExportScope) = updateState {
        it.copy(exportScope = scope, exportValidationError = "")
    }

    fun updateExportStartDateMillis(value: Long) = updateState {
        it.copy(exportStartDateMillis = value, exportValidationError = "")
    }

    fun updateExportEndDateMillis(value: Long) = updateState {
        it.copy(exportEndDateMillis = value, exportValidationError = "")
    }

    fun updateExportFormat(format: DailyLogExportFormat) = updateState {
        it.copy(exportFormat = format)
    }

    fun updateIncludePlanInExport(include: Boolean) = updateState {
        it.copy(includePlanInExport = include)
    }

    fun updateIsExporting(exporting: Boolean) = updateState {
        it.copy(isExporting = exporting)
    }

    fun updateExportValidationError(error: String) = updateState {
        it.copy(exportValidationError = error)
    }

    fun updateSystemEvents(events: List<com.mapsupervision.app.sync.SystemEvent>) = updateState {
        it.copy(systemEvents = events)
    }

    private fun updateState(transform: (ProgressHubScreenUiState) -> ProgressHubScreenUiState) {
        _uiState.update(transform)
    }
}

private fun defaultDailyLogDraftLine() = DailyLogDraftLine(
    id = java.util.UUID.randomUUID().toString()
)
