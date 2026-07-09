package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.model.WorkspaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceSnapshotAccessFilterTest {

    @Test
    fun `filterForAccess keeps local data when project permissions are not resolved yet`() {
        val snapshot = workspaceSnapshot()

        val filtered = snapshot.filterForAccess(
            FirebaseAccessState(
                session = FirebaseUserSession(
                    uid = "user-1",
                    email = "user@example.com",
                    emailVerified = true,
                    isAdmin = false
                ),
                allowedProjectIds = emptySet(),
                permissionsByProject = emptyMap(),
                isInitialized = true
            )
        )

        assertEquals(1, filtered.designNodes.size)
    }

    @Test
    fun `filterForAccess clears project data when explicit project permission is missing`() {
        val snapshot = workspaceSnapshot()

        val filtered = snapshot.filterForAccess(
            FirebaseAccessState(
                session = FirebaseUserSession(
                    uid = "user-1",
                    email = "user@example.com",
                    emailVerified = true,
                    isAdmin = false
                ),
                allowedProjectIds = setOf("another-project"),
                permissionsByProject = mapOf(
                    "another-project" to ProjectAccess(
                        projectId = "another-project",
                        contractorScope = ContractorScope.ALL
                    )
                ),
                isInitialized = true
            )
        )

        assertEquals(0, filtered.designNodes.size)
    }

    private fun workspaceSnapshot() = WorkspaceSnapshot(
        projectId = "project-1",
        designNodes = listOf(
            GisNode(
                id = "node-1",
                projectId = "project-1",
                code = "NODE-1",
                contractor = "CTR",
                latitude = 10.0,
                longitude = 106.0
            )
        )
    )
}
