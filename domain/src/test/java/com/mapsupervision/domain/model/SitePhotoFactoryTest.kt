package com.mapsupervision.domain.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import com.mapsupervision.domain.model.createStoredSitePhoto

class SitePhotoFactoryTest {

    @Test
    fun `createStoredSitePhoto reuses original file for thumbnailPath`() {
        val file = File("D:/photos/capture.jpg")
        val thumb = File("D:/photos/thumbs/capture_thumb.jpg")

        val photo = createStoredSitePhoto(
            projectId = "p1",
            objectCode = "NODE-01",
            file = file,
            thumbnailFile = thumb,
            location = PhotoLocationSnapshot(
                latitude = 10.0,
                longitude = 106.0,
                accuracyM = 8f,
                status = PhotoLocationStatus.OK
            ),
            engineer = "Field",
            capturedAtEpochMs = 1234L
        )

        assertEquals(file.absolutePath, photo.filePath)
        assertEquals(thumb.absolutePath, photo.thumbnailPath)
        assertEquals(1234L, photo.capturedAtEpochMs)
    }

    @Test
    fun `createStoredSitePhoto persists statusTag note and address`() {
        val file = File("D:/photos/capture.jpg")
        val thumb = File("D:/photos/thumbs/capture_thumb.jpg")

        val photo = createStoredSitePhoto(
            projectId = "p1",
            objectCode = "NODE-01",
            file = file,
            thumbnailFile = thumb,
            location = PhotoLocationSnapshot(
                latitude = 10.0,
                longitude = 106.0,
                accuracyM = 8f,
                status = PhotoLocationStatus.OK
            ),
            engineer = "Field",
            capturedAtEpochMs = 1234L,
            statusTag = "Thi công",
            captureNote = "Hố ga đã hoàn thiện",
            address = "123 Đường ABC"
        )

        assertEquals("Thi công", photo.statusTag)
        assertEquals("Hố ga đã hoàn thiện", photo.captureNote)
        assertEquals("123 Đường ABC", photo.address)
    }
}
