package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.MediaType
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WorkspaceMediaStorageSpecTest {

    @Test
    fun `resolveMediaStorageSpec defaults to image metadata`() {
        val spec = resolveMediaStorageSpec(File("capture.jpg"))

        assertEquals(MediaType.IMAGE, spec.mediaType)
        assertEquals("image/jpeg", spec.mimeType)
        assertEquals(0L, spec.durationMs)
    }

    @Test
    fun `resolveMediaStorageSpec keeps video metadata`() {
        val videoFile = File.createTempFile("capture", ".mp4")
        val spec = resolveMediaStorageSpec(videoFile, "video/mp4")

        assertEquals(MediaType.VIDEO, spec.mediaType)
        assertEquals("video/mp4", spec.mimeType)
    }

    @Test
    fun `normalizeMediaObjectCode prefers matching node then route`() {
        val nodes = listOf(
            GisNode(
                id = "n1",
                projectId = "p1",
                code = "NODE-01",
                contractor = "",
                latitude = 10.0,
                longitude = 106.0,
                workVolumeSummary = ""
            )
        )
        val routes = listOf(
            GisRoute(
                id = "r1",
                projectId = "p1",
                code = "ROUTE-01",
                contractor = "",
                startNodeCode = "NODE-01",
                endNodeCode = "NODE-02",
                designLength = "100m"
            )
        )

        assertEquals("NODE-01", normalizeMediaObjectCode("NODE-01", nodes, routes))
        assertEquals("ROUTE-01", normalizeMediaObjectCode("ROUTE-01", emptyList(), routes))
    }
}

