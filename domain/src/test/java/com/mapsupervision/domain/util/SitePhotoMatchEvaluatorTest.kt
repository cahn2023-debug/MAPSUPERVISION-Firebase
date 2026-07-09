package com.mapsupervision.domain.util

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MediaType
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePhotoMatchEvaluatorTest {

    @Test
    fun `node within 30 meters is matched`() {
        val node = GisNode(
            id = "node-1",
            projectId = "project-1",
            code = "NODE-1",
            contractor = "TNP",
            latitude = 21.0,
            longitude = 105.0
        )
        val photo = sitePhoto(
            objectCode = node.code,
            latitude = 21.00018,
            longitude = 105.0,
            matchedNodeId = node.id
        )

        val evaluation = evaluateSitePhotoMatch(photo, listOf(node), emptyList())

        assertEquals(PhotoTargetKind.NODE, evaluation.targetKind)
        assertTrue(evaluation.isMatched)
        assertTrue((evaluation.distanceMeters ?: 0.0) < 30.0)
    }

    @Test
    fun `node above 30 meters is unmatched`() {
        val node = GisNode(
            id = "node-1",
            projectId = "project-1",
            code = "NODE-1",
            contractor = "TNP",
            latitude = 21.0,
            longitude = 105.0
        )
        val photo = sitePhoto(
            objectCode = node.code,
            latitude = 21.00031,
            longitude = 105.0,
            matchedNodeId = node.id
        )

        val evaluation = evaluateSitePhotoMatch(photo, listOf(node), emptyList())

        assertFalse(evaluation.isMatched)
        assertTrue((evaluation.distanceMeters ?: 0.0) > 30.0)
    }

    @Test
    fun `route near polyline is matched`() {
        val route = GisRoute(
            id = "route-1",
            projectId = "project-1",
            code = "ROUTE-1",
            contractor = "TNP",
            startNodeCode = "A",
            endNodeCode = "B",
            points = listOf(
                21.0 to 105.0,
                21.0 to 105.001
            )
        )
        val photo = sitePhoto(
            objectCode = route.code,
            latitude = 21.0001,
            longitude = 105.0005,
            matchedRouteId = route.id
        )

        val evaluation = evaluateSitePhotoMatch(photo, emptyList(), listOf(route))

        assertEquals(PhotoTargetKind.ROUTE, evaluation.targetKind)
        assertTrue(evaluation.isMatched)
        assertTrue((evaluation.distanceMeters ?: 0.0) < 30.0)
    }

    @Test
    fun `route far from polyline is unmatched`() {
        val route = GisRoute(
            id = "route-1",
            projectId = "project-1",
            code = "ROUTE-1",
            contractor = "TNP",
            startNodeCode = "A",
            endNodeCode = "B",
            points = listOf(
                21.0 to 105.0,
                21.0 to 105.001
            )
        )
        val photo = sitePhoto(
            objectCode = route.code,
            latitude = 21.001,
            longitude = 105.0005,
            matchedRouteId = route.id
        )

        val evaluation = evaluateSitePhotoMatch(photo, emptyList(), listOf(route))

        assertFalse(evaluation.isMatched)
        assertTrue((evaluation.distanceMeters ?: 0.0) > 30.0)
    }

    @Test
    fun `missing gps is unmatched`() {
        val node = GisNode(
            id = "node-1",
            projectId = "project-1",
            code = "NODE-1",
            contractor = "TNP",
            latitude = 21.0,
            longitude = 105.0
        )
        val photo = sitePhoto(
            objectCode = node.code,
            latitude = null,
            longitude = null,
            matchedNodeId = node.id
        )

        val evaluation = evaluateSitePhotoMatch(photo, listOf(node), emptyList())

        assertFalse(evaluation.isMatched)
        assertNull(evaluation.distanceMeters)
    }

    private fun sitePhoto(
        objectCode: String,
        latitude: Double?,
        longitude: Double?,
        matchedNodeId: String? = null,
        matchedRouteId: String? = null
    ): SitePhoto {
        return SitePhoto(
            id = "photo-1",
            projectId = "project-1",
            objectCode = objectCode,
            filePath = "D:/photo.jpg",
            thumbnailPath = "D:/thumb.jpg",
            latitude = latitude,
            longitude = longitude,
            locationAccuracyM = 3f,
            isGpsMocked = false,
            locationStatus = PhotoLocationStatus.OK,
            engineer = "Field",
            capturedAtEpochMs = 1_000L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0L,
            matchedNodeId = matchedNodeId,
            matchedRouteId = matchedRouteId,
            syncStatus = SitePhotoSyncStatus.PENDING
        )
    }
}
