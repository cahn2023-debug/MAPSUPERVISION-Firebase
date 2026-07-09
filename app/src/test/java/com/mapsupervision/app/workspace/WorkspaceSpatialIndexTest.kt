package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceSpatialIndexTest {

    @Test
    fun nearest_node_returns_closest_match_within_radius() {
        val index = WorkspaceSpatialIndex.from(
            listOf(
                GisNode(
                    id = "n1",
                    projectId = "p1",
                    code = "N-1",
                    contractor = "A",
                    latitude = 10.0,
                    longitude = 106.0,
                    mapNumberLabel = "",
                    workVolumeSummary = ""
                ),
                GisNode(
                    id = "n2",
                    projectId = "p1",
                    code = "N-2",
                    contractor = "A",
                    latitude = 10.0008,
                    longitude = 106.0008,
                    mapNumberLabel = "",
                    workVolumeSummary = ""
                )
            )
        )

        val nearest = index.nearestNode(10.0007, 106.0007, radiusMeters = 200.0)

        assertEquals("N-2", nearest?.code)
    }

    @Test
    fun nearest_node_returns_null_outside_radius() {
        val index = WorkspaceSpatialIndex.from(
            listOf(
                GisNode(
                    id = "n1",
                    projectId = "p1",
                    code = "N-1",
                    contractor = "A",
                    latitude = 10.0,
                    longitude = 106.0,
                    mapNumberLabel = "",
                    workVolumeSummary = ""
                )
            )
        )

        val nearest = index.nearestNode(11.0, 107.0, radiusMeters = 100.0)

        assertNull(nearest)
    }
}
