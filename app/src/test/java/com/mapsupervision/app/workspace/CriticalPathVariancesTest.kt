package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Test

class CriticalPathVariancesTest {

    // --- estimatedDelayDays ---

    @Test
    fun `estimatedDelayDays zero variance returns zero`() {
        assertEquals(0, estimatedDelayDays(0f))
    }

    @Test
    fun `estimatedDelayDays 100 percent variance returns 30 days`() {
        assertEquals(30, estimatedDelayDays(100f))
    }

    @Test
    fun `estimatedDelayDays 50 percent variance returns 15 days`() {
        assertEquals(15, estimatedDelayDays(50f))
    }

    @Test
    fun `estimatedDelayDays rounds half up`() {
        // 5 * 30 / 100 = 1.5 → rounds to 2
        assertEquals(2, estimatedDelayDays(5f))
    }

    @Test
    fun `estimatedDelayDays small variance rounds to zero`() {
        // 1 * 30 / 100 = 0.3 → rounds to 0
        assertEquals(0, estimatedDelayDays(1f))
    }

    // --- nodeDisplayName ---

    private fun makeNode(code: String, label: String) = GisNode(
        id = "id",
        projectId = "proj",
        code = code,
        contractor = "",
        latitude = 0.0,
        longitude = 0.0,
        mapNumberLabel = label
    )

    @Test
    fun `nodeDisplayName returns mapNumberLabel when present`() {
        val nodesMap = mapOf("N1" to makeNode("N1", "Nút 1"))
        assertEquals("Nút 1", nodeDisplayName("N1", nodesMap))
    }

    @Test
    fun `nodeDisplayName falls back to nodeCode when label is blank`() {
        val nodesMap = mapOf("N2" to makeNode("N2", ""))
        assertEquals("N2", nodeDisplayName("N2", nodesMap))
    }

    @Test
    fun `nodeDisplayName falls back to nodeCode when node not in map`() {
        val nodesMap = emptyMap<String, GisNode>()
        assertEquals("N3", nodeDisplayName("N3", nodesMap))
    }

    @Test
    fun `nodeDisplayName returns unknown when nodeCode is blank and node not in map`() {
        val nodesMap = emptyMap<String, GisNode>()
        assertEquals("Node không xác định", nodeDisplayName("", nodesMap))
    }

    @Test
    fun `nodeDisplayName returns unknown when nodeCode blank and label blank`() {
        val nodesMap = mapOf("" to makeNode("", ""))
        assertEquals("Node không xác định", nodeDisplayName("", nodesMap))
    }
}
