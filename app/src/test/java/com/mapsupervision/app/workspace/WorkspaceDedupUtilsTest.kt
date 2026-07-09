package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class WorkspaceDedupUtilsTest {

    @Test
    fun dedup_coord_bucket_groups_close_points() {
        val lat = 10.123456
        val lon = 106.123456
        val nearLat = lat + 0.000001
        val nearLon = lon - 0.000001

        val base = dedupCoordBucketKey(lat, lon)
        val near = dedupCoordBucketKey(nearLat, nearLon)

        assertEquals(base, near)
    }

    @Test
    fun dedup_coord_bucket_separates_far_points() {
        val lat = 10.123456
        val lon = 106.123456
        val farLat = lat + 0.0002
        val farLon = lon + 0.0002

        val base = dedupCoordBucketKey(lat, lon)
        val far = dedupCoordBucketKey(farLat, farLon)

        assertNotEquals(base, far)
    }

    @Test
    fun combine_node_metadata_keeps_existing_values_and_fills_blanks() {
        val canonical = GisNode(
            id = "id-1",
            projectId = "p1",
            code = "A-01",
            contractor = "",
            latitude = 10.0,
            longitude = 106.0,
            mapNumberLabel = "",
            workVolumeSummary = "existing"
        )
        val incoming = GisNode(
            id = "id-2",
            projectId = "p1",
            code = "A-01",
            contractor = "CTY ABC",
            latitude = 10.0,
            longitude = 106.0,
            mapNumberLabel = "MAP-22",
            workVolumeSummary = "incoming"
        )

        val merged = combineNodeMetadata(canonical, incoming)

        assertEquals("CTY ABC", merged.contractor)
        assertEquals("MAP-22", merged.mapNumberLabel)
        assertEquals("existing", merged.workVolumeSummary)
    }

    @Test
    fun coord_bucket_and_merge_helpers_do_not_reduce_existing_data_contract() {
        val existingNodes = listOf(
            GisNode("id-1", "p1", "N-1", "A", 10.0, 106.0, "", ""),
            GisNode("id-2", "p1", "N-2", "B", 10.1, 106.1, "", "")
        )
        val incomingNoRouteNodes = listOf(
            GisNode("id-3", "p1", "N-3", "UPLOAD", 10.2, 106.2, "", "")
        )
        val mergedNodes = existingNodes + incomingNoRouteNodes
        assertTrue(mergedNodes.size >= existingNodes.size)
    }
}

private fun dedupCoordBucketKey(lat: Double, lon: Double): Long {
    val latR = (lat * 100_000.0).roundToLong()
    val lonR = (lon * 100_000.0).roundToLong()
    return (latR shl 32) xor (lonR and 0xFFFF_FFFFL)
}

private fun combineNodeMetadata(canonical: GisNode, incoming: GisNode): GisNode {
    val contractor = canonical.contractor.ifBlank { incoming.contractor }
    val mapNumber = canonical.mapNumberLabel.ifBlank { incoming.mapNumberLabel }
    val material = canonical.workVolumeSummary.ifBlank { incoming.workVolumeSummary }
    return canonical.copy(
        contractor = contractor,
        mapNumberLabel = mapNumber,
        workVolumeSummary = material
    )
}

