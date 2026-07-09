package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressHubViewModelTest {

    @Test
    fun `select plan sub tab updates state`() {
        val viewModel = ProgressHubViewModel()

        viewModel.setSubTab(ProgressHubSubTab.PLAN)

        assertEquals(ProgressHubSubTab.PLAN, viewModel.uiState.value.subTab)
    }

    @Test
    fun `select progress node initializes sheet inputs`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectProgressNode("N-01", "30", "20")

        val state = viewModel.uiState.value
        assertEquals("N-01", state.selectedNodeCodeForProgress)
        assertEquals("30", state.progressSheetPlannedInput)
        assertEquals("20", state.progressSheetActualInput)
    }

    @Test
    fun `dismiss progress node sheet clears sheet state`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectProgressNode("N-01", "30", "20")
        viewModel.updateProgressSheetValidationError("Invalid")
        viewModel.updateProgressSheetNote("Need follow-up")
        viewModel.dismissProgressNodeSheet()

        val state = viewModel.uiState.value
        assertNull(state.selectedNodeCodeForProgress)
        assertEquals("", state.progressSheetPlannedInput)
        assertEquals("", state.progressSheetActualInput)
        assertEquals("", state.progressSheetValidationError)
        assertEquals("", state.progressSheetNote)
    }

    @Test
    fun `reset log form clears transient inputs`() {
        val viewModel = ProgressHubViewModel()

        viewModel.updateWorkItemInput("Task")
        viewModel.updateNoteInput("Note")
        viewModel.updateSelectedNodeCodeForLog("N-01")
        viewModel.updateSelectedCategoryName("Concrete")
        viewModel.updateUnitInput("m3")
        viewModel.updateVolumeInput("12")
        viewModel.updateManpowerInput("9")
        viewModel.updateLogFormError("Error")
        viewModel.resetLogForm()

        val state = viewModel.uiState.value
        assertEquals("", state.workItemInput)
        assertEquals("", state.noteInput)
        assertNull(state.selectedNodeCodeForLog)
        assertEquals("", state.selectedCategoryName)
        assertEquals("", state.unitInput)
        assertEquals("", state.volumeInput)
        assertEquals(1, state.actualLines.size)
        assertNull(state.selectedPlanForLogId)
        assertEquals("5", state.manpowerInput)
        assertEquals("", state.logFormError)
    }

    @Test
    fun `add and remove plan locations update selected chips`() {
        val viewModel = ProgressHubViewModel()

        viewModel.addSelectedPlanNodeCode("N-01")
        viewModel.addSelectedPlanNodeCode("N-01")
        viewModel.addSelectedPlanRouteCode("R-01")
        viewModel.addSelectedPlanRouteCode("R-02")
        viewModel.removeSelectedPlanRouteCode("R-01")
        viewModel.removeSelectedPlanNodeCode("N-01")

        val state = viewModel.uiState.value
        assertEquals(emptyList<String>(), state.selectedPlanNodeCodes)
        assertEquals(listOf("R-02"), state.selectedPlanRouteCodes)
    }

    @Test
    fun `reset plan form clears transient inputs`() {
        val viewModel = ProgressHubViewModel()

        viewModel.updateSelectedPlanWorkName("Đào rãnh")
        viewModel.updatePlanUnitInput("m3")
        viewModel.updatePlanQuantityInput("12")
        viewModel.updatePlanNoteInput("Theo tuyến chính")
        viewModel.addSelectedPlanNodeCode("N-01")
        viewModel.addSelectedPlanRouteCode("R-01")
        viewModel.setPlanNodeDropdownExpanded(true)
        viewModel.setPlanRouteDropdownExpanded(true)
        viewModel.setPlanWorkDropdownExpanded(true)
        viewModel.updatePlanFormError("Error")
        viewModel.resetPlanForm()

        val state = viewModel.uiState.value
        assertEquals("", state.selectedPlanWorkName)
        assertEquals("", state.planUnitInput)
        assertEquals("", state.planQuantityInput)
        assertEquals("", state.planNoteInput)
        assertEquals(emptyList<String>(), state.selectedPlanNodeCodes)
        assertEquals(emptyList<String>(), state.selectedPlanRouteCodes)
        assertEquals(false, state.planNodeDropdownExpanded)
        assertEquals(false, state.planRouteDropdownExpanded)
        assertEquals(false, state.planWorkDropdownExpanded)
        assertEquals("", state.planFormError)
    }

    @Test
    fun `reset plan form clears selected task id`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectPlanWorkTemplate("Plan task", "m3", "task-1")
        viewModel.resetPlanForm()

        val state = viewModel.uiState.value
        assertEquals("", state.selectedPlanWorkName)
        assertNull(state.selectedPlanTaskId)
    }

    @Test
    fun `apply plan to log pre-fills actual quantity fields`() {
        val viewModel = ProgressHubViewModel()
        val plan = com.mapsupervision.domain.model.WorkPlan(
            id = "plan-1",
            projectId = "p-1",
            title = "Dao ranh",
            description = "Lam trong ngay",
            plannedDateEpochDay = 19700L,
            nodeCode = "N-01",
            routeCode = null,
            taskId = "task-1",
            sourceRawInput = "",
            createdAtEpochMs = 1L,
            quantity = 12.5,
            unit = "m3",
            batchGroupId = "batch-1"
        )

        viewModel.applyPlanToLog(plan)

        val state = viewModel.uiState.value
        assertEquals("plan-1", state.selectedPlanForLogId)
        assertEquals("Dao ranh", state.workItemInput)
        assertEquals("Dao ranh", state.selectedCategoryName)
        assertEquals("12.5", state.volumeInput)
        assertEquals("m3", state.unitInput)
        assertEquals("N-01", state.selectedNodeCodeForLog)
        assertNull(state.selectedRouteCodeForLog)
        assertEquals("Lam trong ngay", state.noteInput)
        assertEquals("plan-1", state.selectedPlanSnapshot?.linkedWorkPlanId)
        assertEquals(1, state.actualLines.size)
        assertEquals("Dao ranh", state.actualLines.first().workName)
        assertEquals("12.5", state.actualLines.first().quantityInput)
    }

    @Test
    fun `add and remove actual lines update draft list`() {
        val viewModel = ProgressHubViewModel()

        val initialLineId = viewModel.uiState.value.actualLines.first().id
        viewModel.addActualLine()
        val addedState = viewModel.uiState.value
        assertEquals(2, addedState.actualLines.size)

        viewModel.removeActualLine(initialLineId)
        val removedState = viewModel.uiState.value
        assertEquals(1, removedState.actualLines.size)
    }

    @Test
    fun `select work template applies template unit`() {
        val viewModel = ProgressHubViewModel()

        viewModel.updateLogFormError("Error")
        viewModel.setCategoryDropdownExpanded(true)
        viewModel.selectWorkTemplate("Bê tông móng", "m3")

        val state = viewModel.uiState.value
        assertEquals("Bê tông móng", state.selectedCategoryName)
        assertEquals("m3", state.unitInput)
        assertEquals(false, state.categoryDropdownExpanded)
        assertEquals("", state.logFormError)
    }

    @Test
    fun `selecting another work template replaces previous unit`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectWorkTemplate("Cáp quang", "m")
        viewModel.selectWorkTemplate("Bê tông móng", "m3")

        val state = viewModel.uiState.value
        assertEquals("Bê tông móng", state.selectedCategoryName)
        assertEquals("m3", state.unitInput)
    }

    @Test
    fun `clearing work template resets category and unit`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectWorkTemplate("Cáp quang", "m")
        viewModel.selectWorkTemplate("", "")

        val state = viewModel.uiState.value
        assertEquals("", state.selectedCategoryName)
        assertEquals("", state.unitInput)
    }

    @Test
    fun `startEditingDailyLog with valid dateEpochDay`() {
        val viewModel = ProgressHubViewModel()
        val log = com.mapsupervision.domain.model.DailyLog(
            id = "log-1",
            projectId = "p-1",
            workItem = "Concrete pouring",
            manpower = 4,
            note = "Smooth",
            createdAtEpochMs = 1718000000000L,
            dateEpochDay = 19700L
        )
        viewModel.startEditingDailyLog(log, "80.0")

        val state = viewModel.uiState.value
        assertEquals("log-1", state.editingDailyLogId)
        assertEquals("80.0", state.actualProgressInput)
        val expectedCal = java.util.Calendar.getInstance().apply {
            val localDate = java.time.LocalDate.ofEpochDay(19700L)
            set(java.util.Calendar.YEAR, localDate.year)
            set(java.util.Calendar.MONTH, localDate.monthValue - 1)
            set(java.util.Calendar.DAY_OF_MONTH, localDate.dayOfMonth)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedCal.timeInMillis, state.selectedDateMillis)
    }

    @Test
    fun `startEditingDailyLog with legacy log fallback date`() {
        val viewModel = ProgressHubViewModel()
        val calDate = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JUNE, 22, 10, 0, 0)
        }
        val log = com.mapsupervision.domain.model.DailyLog(
            id = "log-2",
            projectId = "p-1",
            workItem = "Wiring",
            manpower = 2,
            note = "",
            createdAtEpochMs = calDate.timeInMillis,
            dateEpochDay = 0L
        )
        viewModel.startEditingDailyLog(log, "")

        val state = viewModel.uiState.value
        assertEquals("log-2", state.editingDailyLogId)

        val expectedCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JUNE, 22, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedCal.timeInMillis, state.selectedDateMillis)
    }

    @Test
    fun `resetLogForm clears editingDailyLogId`() {
        val viewModel = ProgressHubViewModel()
        val log = com.mapsupervision.domain.model.DailyLog(
            id = "log-1",
            projectId = "p-1",
            workItem = "Concrete",
            manpower = 4,
            note = "",
            createdAtEpochMs = 1718000000000L,
            dateEpochDay = 19700L
        )
        viewModel.startEditingDailyLog(log)
        viewModel.resetLogForm()

        val state = viewModel.uiState.value
        assertNull(state.editingDailyLogId)
    }
}
