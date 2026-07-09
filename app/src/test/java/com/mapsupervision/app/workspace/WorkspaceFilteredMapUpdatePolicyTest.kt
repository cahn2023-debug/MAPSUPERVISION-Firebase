package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilteredMapUpdatePolicyTest {
    @Test
    fun snapshot_updates_apply_immediately_but_filter_and_search_are_debounced() {
        assertEquals(0L, resolveFilteredMapUpdateDelayMs(FilteredMapUpdateReason.SNAPSHOT))
        assertEquals(180L, resolveFilteredMapUpdateDelayMs(FilteredMapUpdateReason.FILTER))
        assertEquals(180L, resolveFilteredMapUpdateDelayMs(FilteredMapUpdateReason.SEARCH))
    }

    @Test
    fun publish_is_skipped_when_filtered_lists_are_unchanged() {
        val previousNodes = listOf(GisNode("n1", "p1", "N-001", "A", 10.0, 106.0))
        val previousRoutes = listOf(GisRoute("r1", "p1", "R-001", "A", "N-001", "N-001"))
        val nextNodes = listOf(GisNode("n1", "p1", "N-001", "A", 10.0, 106.0))
        val nextRoutes = listOf(GisRoute("r1", "p1", "R-001", "A", "N-001", "N-001"))

        assertFalse(
            shouldPublishFilteredMapData(
                previousNodes = previousNodes,
                previousRoutes = previousRoutes,
                nextNodes = nextNodes,
                nextRoutes = nextRoutes
            )
        )
    }

    @Test
    fun publish_runs_when_nodes_or_routes_change() {
        val previousNodes = listOf(GisNode("n1", "p1", "N-001", "A", 10.0, 106.0))
        val previousRoutes = listOf(GisRoute("r1", "p1", "R-001", "A", "N-001", "N-001"))
        val nextNodes = previousNodes + GisNode("n2", "p1", "N-002", "A", 10.1, 106.1)

        assertTrue(
            shouldPublishFilteredMapData(
                previousNodes = previousNodes,
                previousRoutes = previousRoutes,
                nextNodes = nextNodes,
                nextRoutes = previousRoutes
            )
        )

        val nextRoutes = previousRoutes + GisRoute("r2", "p1", "R-002", "A", "N-001", "N-002")
        assertTrue(
            shouldPublishFilteredMapData(
                previousNodes = previousNodes,
                previousRoutes = previousRoutes,
                nextNodes = previousNodes,
                nextRoutes = nextRoutes
            )
        )
    }
}
