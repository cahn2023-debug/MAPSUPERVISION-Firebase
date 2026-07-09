package com.mapsupervision.project.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectUiStateTest {
    @Test
    fun defaults_start_empty() {
        val state = ProjectUiState()

        assertTrue(state.projects.isEmpty())
        assertEquals(null, state.activeProjectId)
        assertTrue(state.importedFiles.isEmpty())
        assertEquals("", state.importMessage)
        assertEquals("", state.message)
        assertEquals(null, state.duplicateProjectToResolve)
        assertEquals(null, state.duplicateZipUri)
    }
}
