package com.mapsupervision.project.ui

import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectStorageMode
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

    @Test
    fun resolveVisibleProjects_keepsLocalProjectsForSignedInUser() {
        val localOnly = sampleProject("local-old", updatedAt = 100)
        val allowed = sampleProject("allowed-new", updatedAt = 50)
        val projects = resolveVisibleProjects(
            allProjects = listOf(localOnly, allowed),
            accessState = FirebaseAccessState(
                session = FirebaseUserSession(uid = "u1", email = "user@example.com"),
                allowedProjectIds = setOf("allowed-new"),
                isInitialized = true
            )
        )

        assertEquals(listOf("allowed-new", "local-old"), projects.map { it.id })
    }
}

private fun sampleProject(id: String, updatedAt: Long) = Project(
    id = id,
    name = id,
    slug = id,
    isArchived = false,
    createdAtEpochMs = updatedAt,
    updatedAtEpochMs = updatedAt,
    storageMode = ProjectStorageMode.PROJECT_DB,
    projectDbPath = ""
)
