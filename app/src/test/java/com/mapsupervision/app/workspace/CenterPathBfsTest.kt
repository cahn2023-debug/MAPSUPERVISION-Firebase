package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class CenterPathBfsTest {

    @Test
    fun `returns empty string when inputs are blank`() {
        assertEquals("", buildCenterPathSummary("", "CENTER", emptyList()))
        assertEquals("", buildCenterPathSummary("NODE", "", emptyList()))
        assertEquals("", buildCenterPathSummary("NODE", null, emptyList()))
    }

    @Test
    fun `returns Diem trung tam when node is center`() {
        assertEquals("Điểm trung tâm", buildCenterPathSummary("CENTER", "CENTER", emptyList()))
        assertEquals("Điểm trung tâm", buildCenterPathSummary("center", "CENTER", emptyList()))
    }

    @Test
    fun `returns Chua co duong ve trung tam when path does not exist`() {
        val routes = listOf(
            route("N1", "N2"),
            route("N2", "N3")
        )
        assertEquals("Chưa có đường kết nối về trung tâm", buildCenterPathSummary("N1", "N4", routes))
    }

    @Test
    fun `returns direct path when directly connected`() {
        val routes = listOf(
            route("N1", "N2")
        )
        assertEquals("Đường về trung tâm: N1 -> N2", buildCenterPathSummary("N1", "N2", routes))
    }

    @Test
    fun `returns multi-hop path when connected through multiple segments`() {
        val routes = listOf(
            route("N1", "N2"),
            route("N2", "N3"),
            route("N3", "N4")
        )
        assertEquals("Đường về trung tâm: N1 -> N2 -> N3 -> N4", buildCenterPathSummary("N1", "N4", routes))
    }

    @Test
    fun `picks shortest path in graph using BFS`() {
        val routes = listOf(
            route("N1", "N2"),
            route("N2", "N4"), // Short path: N1 -> N2 -> N4
            route("N1", "N3"),
            route("N3", "N5"),
            route("N5", "N4")  // Long path: N1 -> N3 -> N5 -> N4
        )
        assertEquals("Đường về trung tâm: N1 -> N2 -> N4", buildCenterPathSummary("N1", "N4", routes))
    }

    @Test
    fun `handles cycles in route graph`() {
        val routes = listOf(
            route("N1", "N2"),
            route("N2", "N3"),
            route("N3", "N1"),
            route("N3", "N4")
        )
        assertEquals("Đường về trung tâm: N1 -> N3 -> N4", buildCenterPathSummary("N1", "N4", routes))
    }

    private fun route(start: String, end: String): GisRoute = GisRoute(
        id = java.util.UUID.randomUUID().toString(),
        projectId = "test-project",
        code = "R-$start-$end",
        contractor = "CTR",
        startNodeCode = start,
        endNodeCode = end
    )
}
