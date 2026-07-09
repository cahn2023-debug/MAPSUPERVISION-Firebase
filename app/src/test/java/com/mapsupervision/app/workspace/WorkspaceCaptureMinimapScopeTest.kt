package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCaptureMinimapScopeTest {

    @Test
    fun `selected node returns only itself and connected routes`() {
        val nodeA = node("n1", "N1")
        val nodeB = node("n2", "N2")
        val nodeC = node("n3", "N3")
        val routeAB = route("r1", "R1", "N1", "N2")
        val routeAC = route("r2", "R2", "N1", "N3")
        val unrelatedRoute = route("r3", "R3", "N2", "N3")

        val scope = buildCaptureMinimapScope(
            targetCode = "N1",
            nodes = listOf(nodeA, nodeB, nodeC),
            routes = listOf(routeAB, routeAC, unrelatedRoute)
        )

        assertEquals(setOf("N1"), scope.nodes.map { it.code }.toSet())
        assertEquals(setOf("R1", "R2"), scope.routes.map { it.code }.toSet())
    }

    @Test
    fun `selected route returns only itself and endpoint nodes`() {
        val nodeA = node("n1", "N1")
        val nodeB = node("n2", "N2")
        val nodeC = node("n3", "N3")
        val routeAB = route("r1", "R1", "N1", "N2")
        val unrelatedRoute = route("r2", "R2", "N2", "N3")

        val scope = buildCaptureMinimapScope(
            targetCode = "R1",
            nodes = listOf(nodeA, nodeB, nodeC),
            routes = listOf(routeAB, unrelatedRoute)
        )

        assertEquals(setOf("N1", "N2"), scope.nodes.map { it.code }.toSet())
        assertEquals(setOf("R1"), scope.routes.map { it.code }.toSet())
    }

    @Test
    fun `unrelated project geometry is excluded when target resolves`() {
        val nodeA = node("n1", "N1")
        val nodeB = node("n2", "N2")
        val nodeC = node("n3", "N3")
        val routeAB = route("r1", "R1", "N1", "N2")
        val unrelatedRoute = route("r2", "R2", "N2", "N3")

        val scope = buildCaptureMinimapScope(
            targetCode = "R1",
            nodes = listOf(nodeA, nodeB, nodeC),
            routes = listOf(routeAB, unrelatedRoute)
        )

        assertTrue(scope.nodes.none { it.code == "N3" })
        assertTrue(scope.routes.none { it.code == "R2" })
    }

    @Test
    fun `unresolved target yields empty scope`() {
        val scope = buildCaptureMinimapScope(
            targetCode = "UNKNOWN",
            nodes = listOf(node("n1", "N1")),
            routes = listOf(route("r1", "R1", "N1", "N2"))
        )

        assertTrue(scope.nodes.isEmpty())
        assertTrue(scope.routes.isEmpty())
    }

    private fun node(id: String, code: String) = GisNode(
        id = id,
        projectId = "project-1",
        code = code,
        contractor = "contractor",
        latitude = 10.0,
        longitude = 106.0
    )

    private fun route(id: String, code: String, start: String, end: String) = GisRoute(
        id = id,
        projectId = "project-1",
        code = code,
        contractor = "contractor",
        startNodeCode = start,
        endNodeCode = end
    )
}
