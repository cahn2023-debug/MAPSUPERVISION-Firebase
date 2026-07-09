package com.mapsupervision.gis.maplibre

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NodeSignalStatus
import com.mapsupervision.gis.ui.GisLabelField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapRenderKeyTest {
    @Test
    fun render_key_is_stable_for_recreated_lists_with_same_content() {
        val nodes1 = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001")
        )
        val routes1 = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val nodes2 = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001")
        )
        val routes2 = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val key1 = buildMapRenderKey(nodes1, routes1, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#f97316"), 1.0f, 1.0f)
        val key2 = buildMapRenderKey(nodes2, routes2, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#f97316"), 1.0f, 1.0f)

        assertEquals(key1, key2)
    }

    @Test
    fun render_key_changes_when_visual_inputs_change() {
        val nodes = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001")
        )
        val routes = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val key1 = buildMapRenderKey(nodes, routes, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#f97316"), 1.0f, 1.0f)
        val key2 = buildMapRenderKey(nodes, routes, GisLabelField.CONTRACTOR, true, true, mapOf("CTR-A" to "#f97316"), 1.0f, 1.0f)
        val key3 = buildMapRenderKey(nodes, routes, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#22c55e"), 1.0f, 1.0f)

        assertNotEquals(key1, key2)
        assertNotEquals(key1, key3)
    }

    @Test
    fun render_key_changes_when_signal_status_changes() {
        val nodes1 = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001", signalStatus = NodeSignalStatus.UNKNOWN)
        )
        val nodes2 = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001", signalStatus = NodeSignalStatus.HAS_SIGNAL)
        )
        val routes = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val key1 = buildMapRenderKey(nodes1, routes, GisLabelField.CODE, true, true, emptyMap(), 1.0f, 1.0f)
        val key2 = buildMapRenderKey(nodes2, routes, GisLabelField.CODE, true, true, emptyMap(), 1.0f, 1.0f)

        assertNotEquals(key1, key2)
    }

    @Test
    fun render_key_changes_when_filtered_node_route_set_changes() {
        val allNodes = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001"),
            GisNode("node-2", "project-1", "N-002", "CTR-B", 10.1, 106.1, "002")
        )
        val allRoutes = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001"),
            GisRoute("route-2", "project-1", "R-002", "CTR-B", "N-002", "N-002")
        )

        val allKey = buildMapRenderKey(allNodes, allRoutes, GisLabelField.CODE, true, true, emptyMap(), 1.0f, 1.0f)
        val filteredKey = buildMapRenderKey(
            nodes = listOf(allNodes.first()),
            routes = listOf(allRoutes.first()),
            labelField = GisLabelField.CODE,
            showNumberLabels = true,
            colorByContractor = true,
            contractorColors = emptyMap(),
            nodeSizeScale = 1.0f,
            routeWidthScale = 1.0f
        )

        assertNotEquals(allKey, filteredKey)
    }
}
