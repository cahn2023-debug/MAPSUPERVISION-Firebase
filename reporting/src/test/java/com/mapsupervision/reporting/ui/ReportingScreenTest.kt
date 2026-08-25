package com.mapsupervision.reporting.ui

import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportingScreenTest {

    @Test
    fun allStatusTagFilter_keepsTaggedAndUntaggedMedia() {
        val photos = listOf(
            samplePhoto("photo-tagged", "node-a", "Thi công"),
            samplePhoto("photo-untagged", "node-b", null)
        )

        val filtered = filterReportPhotosByStatusTag(photos, null)

        assertEquals(photos, filtered)
    }

    @Test
    fun selectedStatusTagFilter_spansObjectsAndPreservesGroupingKeys() {
        val photos = listOf(
            samplePhoto("photo-a", "node-a", "Thi công"),
            samplePhoto("photo-b", "route-b", "Thi công"),
            samplePhoto("photo-c", "node-a", null)
        )

        val filtered = filterReportPhotosByStatusTag(photos, "Thi công")
        val groupedObjectCodes = filtered.groupBy { it.objectCode }.keys

        assertEquals(setOf("node-a", "route-b"), groupedObjectCodes)
        assertTrue(filtered.none { it.statusTag == null })
    }

    private fun samplePhoto(id: String, objectCode: String, statusTag: String?): SitePhoto = SitePhoto(
        id = id,
        projectId = "project-1",
        objectCode = objectCode,
        statusTag = statusTag,
        filePath = "/tmp/$id.jpg",
        thumbnailPath = "/tmp/$id.jpg",
        latitude = null,
        longitude = null,
        locationAccuracyM = null,
        isGpsMocked = false,
        locationStatus = PhotoLocationStatus.MISSING,
        engineer = "Engineer",
        capturedAtEpochMs = 1L
    )
}
