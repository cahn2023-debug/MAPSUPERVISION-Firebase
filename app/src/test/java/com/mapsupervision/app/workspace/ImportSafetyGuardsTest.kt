package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class ImportSafetyGuardsTest {

    @Test
    fun detect_geometry_safety_issues_marks_missing_baseline_ids() {
        val baselineNodes = listOf(node("n1", "A", 10.0, 106.0), node("n2", "B", 10.1, 106.1))
        val baselineRoutes = listOf(route("r1", "A", "B"))
        val refreshedNodes = listOf(node("n1", "A", 10.0, 106.0))
        val refreshedRoutes = emptyList<GisRoute>()

        val issues = detectGeometrySafetyIssues(
            baselineNodes = baselineNodes,
            baselineRoutes = baselineRoutes,
            refreshedNodes = refreshedNodes,
            refreshedRoutes = refreshedRoutes,
            normalizeCode = { it.trim().lowercase() },
            coordBucketKey = ::coordBucket
        )

        assertTrue(issues.hasDropByIdentity)
        assertEquals(listOf("n2"), issues.missingBaselineNodeIds)
        assertEquals(listOf("r1"), issues.missingBaselineRouteIds)
    }

    @Test
    fun merge_geometry_prefer_baseline_keeps_original_on_id_conflict() {
        val baselineNodes = listOf(node("n1", "BASE", 10.0, 106.0))
        val baselineRoutes = listOf(route("r1", "BASE_S", "BASE_E"))
        val refreshedNodes = listOf(node("n1", "NEW", 11.0, 107.0), node("n2", "C", 10.2, 106.2))
        val refreshedRoutes = listOf(route("r1", "NEW_S", "NEW_E"), route("r2", "C", "D"))

        val (mergedNodes, mergedRoutes) = mergeGeometryPreferBaseline(
            baselineNodes = baselineNodes,
            baselineRoutes = baselineRoutes,
            refreshedNodes = refreshedNodes,
            refreshedRoutes = refreshedRoutes
        )

        assertEquals(2, mergedNodes.size)
        assertEquals(2, mergedRoutes.size)
        val idN1 = mergedNodes.first { it.id == "n1" }
        assertEquals("BASE", idN1.code)
        assertEquals(10.0, idN1.latitude, 0.000001)
        val idR1 = mergedRoutes.first { it.id == "r1" }
        assertEquals("BASE_S", idR1.startNodeCode)
        assertEquals("BASE_E", idR1.endNodeCode)
    }

    @Test
    fun merge_geometry_prefer_baseline_keeps_all_existing_when_new_file_has_no_routes() {
        val baselineNodes = listOf(
            node("n1", "N-1", 10.0, 106.0),
            node("n2", "N-2", 10.1, 106.1)
        )
        val baselineRoutes = listOf(
            route("r1", "N-1", "N-2")
        )
        // Simulate importing a no-route KML/KMZ file: only points or even empty route list.
        val refreshedNodes = baselineNodes + node("n3", "N-3", 10.2, 106.2)
        val refreshedRoutes = baselineRoutes

        val (mergedNodes, mergedRoutes) = mergeGeometryPreferBaseline(
            baselineNodes = baselineNodes,
            baselineRoutes = baselineRoutes,
            refreshedNodes = refreshedNodes,
            refreshedRoutes = refreshedRoutes
        )

        assertEquals(3, mergedNodes.size)
        assertEquals(1, mergedRoutes.size)
        assertTrue(mergedRoutes.any { it.id == "r1" && it.startNodeCode == "N-1" && it.endNodeCode == "N-2" })
    }
}

private fun node(id: String, code: String, lat: Double, lon: Double): GisNode =
    GisNode(id = id, projectId = "p1", code = code, contractor = "", latitude = lat, longitude = lon)

private fun route(id: String, start: String, end: String): GisRoute =
    GisRoute(id = id, projectId = "p1", code = id, contractor = "", startNodeCode = start, endNodeCode = end)

private fun coordBucket(lat: Double, lon: Double): Long {
    val latR = (lat * 100_000.0).roundToLong()
    val lonR = (lon * 100_000.0).roundToLong()
    return (latR shl 32) xor (lonR and 0xFFFF_FFFFL)
}
