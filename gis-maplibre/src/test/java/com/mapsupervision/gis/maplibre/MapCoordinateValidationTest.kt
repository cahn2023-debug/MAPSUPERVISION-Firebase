package com.mapsupervision.gis.maplibre

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCoordinateValidationTest {
    @Test
    fun valid_coordinate_is_renderable() {
        assertTrue(isValidCoordinate(21.02851, 105.80482))
        assertTrue(isRenderableNode(GisNode("n1", "p1", "N-001", "A", 21.02851, 105.80482)))
    }

    @Test
    fun swapped_coordinate_is_still_renderable_after_normalization() {
        assertFalse(isValidCoordinate(105.80482, 21.02851))
        assertTrue(isRenderableNode(GisNode("n1", "p1", "N-001", "A", 105.80482, 21.02851)))

        val normalized = normalizeCoordinatePair(105.80482, 21.02851)

        assertEquals(21.02851, normalized?.latitude ?: 0.0, 0.00001)
        assertEquals(105.80482, normalized?.longitude ?: 0.0, 0.00001)
        assertTrue(normalized?.swapped == true)
    }

    @Test
    fun truly_out_of_range_coordinate_is_not_renderable() {
        assertFalse(isValidCoordinate(21.02851, 181.0))
        assertFalse(isRenderableNode(GisNode("n1", "p1", "N-001", "A", 200.0, 181.0)))
    }

    @Test
    fun summarize_coordinates_counts_valid_and_invalid_nodes() {
        val nodes = listOf(
            GisNode("n1", "p1", "N-001", "A", 21.02851, 105.80482),
            GisNode("n2", "p1", "N-002", "A", 10.0, 106.0),
            GisNode("n3", "p1", "N-003", "A", 120.0, 106.0)
        )

        val summary = summarizeCoordinates(nodes)

        assertEquals(2, summary.validCount)
        assertEquals(1, summary.invalidCount)
        assertEquals("10.00000..21.02851", summary.latRangeText)
        assertEquals("105.80482..106.00000", summary.lonRangeText)
    }

    @Test
    fun map_bounds_include_route_points_without_vertex_nodes() {
        val routes = listOf(
            GisRoute(
                id = "r1",
                projectId = "p1",
                code = "R-001",
                contractor = "A",
                startNodeCode = "",
                endNodeCode = "",
                points = listOf(10.0 to 106.0, 10.1 to 106.1, 10.2 to 106.2)
            )
        )

        val points = renderCoordinatesForMapObjects(nodes = emptyList(), routes = routes)

        assertEquals(3, points.size)
        assertEquals(10.0, points.first().latitude, 0.000001)
        assertEquals(106.0, points.first().longitude, 0.000001)
    }
}
