package com.mapsupervision.app.workspace

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataHubViewModelTest {

    @Test
    fun `updates design tab and notes sheet state`() {
        val viewModel = DataHubViewModel(SavedStateHandle())

        viewModel.setDesignTab(false)
        viewModel.showNotesAndTasks("NG-01")

        val state = viewModel.uiState.value
        assertFalse(state.isDesignTab)
        assertTrue(state.showNotesAndTasksSheet)
        assertEquals("NG-01", state.notesAndTasksObjectCode)

        viewModel.dismissNotesAndTasks()
        assertFalse(viewModel.uiState.value.showNotesAndTasksSheet)
        assertEquals("", viewModel.uiState.value.notesAndTasksObjectCode)
    }
}
