package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceCaptureTargetTest {

    @Test
    fun `resolveCaptureTargetCode returns null without selection`() {
        assertNull(resolveCaptureTargetCode(MapUiState()))
    }

    @Test
    fun `resolveCaptureTargetCode prefers selected node`() {
        val state = MapUiState(
            selectedNode = GisNode(
                id = "node-1",
                projectId = "project-1",
                code = "NODE-01",
                contractor = "Contractor",
                latitude = 10.0,
                longitude = 106.0
            ),
            selectedRoute = GisRoute(
                id = "route-1",
                projectId = "project-1",
                code = "ROUTE-01",
                contractor = "Contractor",
                startNodeCode = "NODE-01",
                endNodeCode = "NODE-02"
            )
        )

        assertEquals("NODE-01", resolveCaptureTargetCode(state))
    }

    @Test
    fun `resolveCaptureTargetCode falls back to route`() {
        val state = MapUiState(
            selectedRoute = GisRoute(
                id = "route-1",
                projectId = "project-1",
                code = "ROUTE-01",
                contractor = "Contractor",
                startNodeCode = "NODE-01",
                endNodeCode = "NODE-02"
            )
        )

        assertEquals("ROUTE-01", resolveCaptureTargetCode(state))
    }
}
