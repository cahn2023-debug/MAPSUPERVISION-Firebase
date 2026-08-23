package com.mapsupervision.project.ui

import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import com.mapsupervision.domain.model.FirebaseProjectCatalogStatus
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.model.canRequestAgain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCatalogUiTest {

    @Test
    fun resolveCatalogItems_mapsAllAccessStatusesCorrectly() {
        val entries = listOf(
            FirebaseProjectCatalogEntry("p1", "Project 1", "P01", 1000L, FirebaseProjectCatalogStatus.ACTIVE),
            FirebaseProjectCatalogEntry("p2", "Project 2", "P02", 2000L, FirebaseProjectCatalogStatus.ACTIVE),
            FirebaseProjectCatalogEntry("p3", "Project 3", "P03", 3000L, FirebaseProjectCatalogStatus.ACTIVE),
            FirebaseProjectCatalogEntry("p4", "Project 4", "P04", 4000L, FirebaseProjectCatalogStatus.ACTIVE),
            FirebaseProjectCatalogEntry("p5", "Project 5", "P05", 5000L, FirebaseProjectCatalogStatus.ARCHIVED)
        )
        val localProjects = listOf(sampleProject("p1"), sampleProject("p4"))
        val accessState = FirebaseAccessState(
            session = FirebaseUserSession(uid = "u1", email = "user@mapsupervision.com", isAdmin = false),
            allowedProjectIds = setOf("p3"),
            isInitialized = true
        )
        val requests = mapOf(
            "p1" to FirebaseProjectAccessRequest(requestId = "req1", projectId = "p1", userId = "u1", status = FirebaseAccessRequestStatus.PENDING),
            "p2" to FirebaseProjectAccessRequest(requestId = "req2", projectId = "p2", userId = "u1", status = FirebaseAccessRequestStatus.REJECTED),
            "p3" to FirebaseProjectAccessRequest(requestId = "req3", projectId = "p3", userId = "u1", status = FirebaseAccessRequestStatus.APPROVED),
            "p4" to FirebaseProjectAccessRequest(requestId = "req4", projectId = "p4", userId = "u1", status = FirebaseAccessRequestStatus.REVOKED)
        )

        val items = resolveCatalogItems(entries, localProjects, accessState, requests)

        assertEquals(5, items.size)
        // p1: local, PENDING
        assertEquals("p1", items[0].projectId)
        assertEquals(FirebaseAccessRequestStatus.PENDING, items[0].accessStatus)
        assertTrue(items[0].isLocalAvailable)
        assertFalse(items[0].isRevokedReadOnly)

        // p2: not local, REJECTED
        assertEquals("p2", items[1].projectId)
        assertEquals(FirebaseAccessRequestStatus.REJECTED, items[1].accessStatus)
        assertFalse(items[1].isLocalAvailable)
        assertFalse(items[1].isRevokedReadOnly)

        // p3: not local, APPROVED
        assertEquals("p3", items[2].projectId)
        assertEquals(FirebaseAccessRequestStatus.APPROVED, items[2].accessStatus)
        assertFalse(items[2].isLocalAvailable)
        assertFalse(items[2].isRevokedReadOnly)

        // p4: local, REVOKED -> isRevokedReadOnly MUST be true (D4/D6)
        assertEquals("p4", items[3].projectId)
        assertEquals(FirebaseAccessRequestStatus.REVOKED, items[3].accessStatus)
        assertTrue(items[3].isLocalAvailable)
        assertTrue(items[3].isRevokedReadOnly)

        // p5: not requested
        assertEquals("p5", items[4].projectId)
        assertEquals(FirebaseAccessRequestStatus.NOT_REQUESTED, items[4].accessStatus)
        assertFalse(items[4].isLocalAvailable)
        assertFalse(items[4].isRevokedReadOnly)
        assertEquals(FirebaseProjectCatalogStatus.ARCHIVED, items[4].catalogStatus)
    }

    @Test
    fun resolveCatalogItems_adminSessionGrantsApprovedToAll() {
        val entries = listOf(
            FirebaseProjectCatalogEntry("p1", "Project 1", "P01", 1000L, FirebaseProjectCatalogStatus.ACTIVE),
            FirebaseProjectCatalogEntry("p2", "Project 2", "P02", 2000L, FirebaseProjectCatalogStatus.ACTIVE)
        )
        val accessState = FirebaseAccessState(
            session = FirebaseUserSession(uid = "admin1", email = "admin@mapsupervision.com", isAdmin = true),
            allowedProjectIds = emptySet(),
            isInitialized = true
        )

        val items = resolveCatalogItems(entries, emptyList(), accessState, emptyMap())

        assertTrue(items.all { it.accessStatus == FirebaseAccessRequestStatus.APPROVED })
        assertFalse(items.any { it.isRevokedReadOnly })
    }

    @Test
    fun resolveRevokedReadOnlyProjectIds_identifiesOnlyRevokedLocalProjects() {
        val localProjects = listOf(
            sampleProject("p1"),
            sampleProject("p2"),
            sampleProject("p3")
        )
        val accessState = FirebaseAccessState(
            session = FirebaseUserSession(uid = "u1", email = "engineer@mapsupervision.com", isAdmin = false),
            allowedProjectIds = setOf("p1"),
            isInitialized = true
        )
        val requests = mapOf(
            "p1" to FirebaseProjectAccessRequest(requestId = "r1", projectId = "p1", userId = "u1", status = FirebaseAccessRequestStatus.APPROVED),
            "p2" to FirebaseProjectAccessRequest(requestId = "r2", projectId = "p2", userId = "u1", status = FirebaseAccessRequestStatus.REVOKED),
            "p3" to FirebaseProjectAccessRequest(requestId = "r3", projectId = "p3", userId = "u1", status = FirebaseAccessRequestStatus.PENDING)
        )

        val revokedIds = resolveRevokedReadOnlyProjectIds(localProjects, accessState, requests)

        assertEquals(setOf("p2"), revokedIds)
    }

    @Test
    fun canRequestAgain_decisionD7_lifecycleMatrix() {
        assertTrue(FirebaseAccessRequestStatus.NOT_REQUESTED.canRequestAgain())
        assertFalse(FirebaseAccessRequestStatus.PENDING.canRequestAgain())
        assertFalse(FirebaseAccessRequestStatus.APPROVED.canRequestAgain())
        assertTrue(FirebaseAccessRequestStatus.REJECTED.canRequestAgain())
        assertTrue(FirebaseAccessRequestStatus.REVOKED.canRequestAgain())

        val request = FirebaseProjectAccessRequest(
            requestId = "r1",
            projectId = "p1",
            userId = "u1",
            status = FirebaseAccessRequestStatus.REVOKED
        )
        assertTrue(request.canRequestAgain())
    }

    private fun sampleProject(id: String) = Project(
        id = id,
        name = "Local $id",
        slug = "slug-$id",
        isArchived = false,
        createdAtEpochMs = 1000L,
        updatedAtEpochMs = 1000L,
        storageMode = ProjectStorageMode.PROJECT_DB,
        projectDbPath = "/data/projects/$id/db"
    )
}
