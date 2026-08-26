package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.resolveEpochDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class DailyLogHelperTest {

    @Test
    fun `resolveEpochDay returns dateEpochDay when it is not zero`() {
        val log = DailyLog(
            id = "log-1",
            projectId = "p-1",
            workItem = "Bê tông",
            manpower = 5,
            note = "",
            createdAtEpochMs = 1718000000000L,
            dateEpochDay = 20000L
        )
        assertEquals(20000L, log.resolveEpochDay())
    }

    @Test
    fun `resolveEpochDay falls back to createdAtEpochMs when dateEpochDay is zero`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 22, 12, 0, 0)
        }
        val expectedEpoch = LocalDate.of(2026, 6, 22).toEpochDay()
        
        val log = DailyLog(
            id = "log-2",
            projectId = "p-1",
            workItem = "Bê tông",
            manpower = 5,
            note = "",
            createdAtEpochMs = cal.timeInMillis,
            dateEpochDay = 0L
        )
        assertEquals(expectedEpoch, log.resolveEpochDay())
    }

    @Test
    fun `photos filter by selected epoch day only includes photos captured on that day`() {
        val cal1 = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 26, 10, 0, 0)
        }
        val cal2 = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 25, 15, 30, 0)
        }
        val epoch26 = LocalDate.of(2026, 8, 26).toEpochDay()
        val epoch25 = LocalDate.of(2026, 8, 25).toEpochDay()

        val photo1 = com.mapsupervision.domain.model.SitePhoto(
            id = "photo-1",
            projectId = "p-1",
            objectCode = "HG01",
            filePath = "/storage/photo1.jpg",
            thumbnailPath = "/storage/photo1_thumb.jpg",
            latitude = 21.0,
            longitude = 105.8,
            locationAccuracyM = 5.0f,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.OK,
            engineer = "Engineer 1",
            capturedAtEpochMs = cal1.timeInMillis,
            matchedNodeCode = "HG01"
        )
        val photo2 = com.mapsupervision.domain.model.SitePhoto(
            id = "photo-2",
            projectId = "p-1",
            objectCode = "HG02",
            filePath = "/storage/photo2.jpg",
            thumbnailPath = "/storage/photo2_thumb.jpg",
            latitude = 21.0,
            longitude = 105.8,
            locationAccuracyM = 5.0f,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.OK,
            engineer = "Engineer 1",
            capturedAtEpochMs = cal2.timeInMillis,
            matchedNodeCode = "HG02"
        )
        val allPhotos = listOf(photo1, photo2)

        val filteredPhotos26 = allPhotos.filter { photo ->
            val photoCal = Calendar.getInstance().apply { timeInMillis = photo.capturedAtEpochMs }
            val photoEpoch = LocalDate.of(
                photoCal.get(Calendar.YEAR),
                photoCal.get(Calendar.MONTH) + 1,
                photoCal.get(Calendar.DAY_OF_MONTH)
            ).toEpochDay()
            photoEpoch == epoch26
        }

        assertEquals(1, filteredPhotos26.size)
        assertEquals("photo-1", filteredPhotos26.first().id)

        val filteredPhotos25 = allPhotos.filter { photo ->
            val photoCal = Calendar.getInstance().apply { timeInMillis = photo.capturedAtEpochMs }
            val photoEpoch = LocalDate.of(
                photoCal.get(Calendar.YEAR),
                photoCal.get(Calendar.MONTH) + 1,
                photoCal.get(Calendar.DAY_OF_MONTH)
            ).toEpochDay()
            photoEpoch == epoch25
        }

        assertEquals(1, filteredPhotos25.size)
        assertEquals("photo-2", filteredPhotos25.first().id)

        val epochOther = LocalDate.of(2026, 8, 20).toEpochDay()
        val filteredPhotosEmpty = allPhotos.filter { photo ->
            val photoCal = Calendar.getInstance().apply { timeInMillis = photo.capturedAtEpochMs }
            val photoEpoch = LocalDate.of(
                photoCal.get(Calendar.YEAR),
                photoCal.get(Calendar.MONTH) + 1,
                photoCal.get(Calendar.DAY_OF_MONTH)
            ).toEpochDay()
            photoEpoch == epochOther
        }
        assertEquals(0, filteredPhotosEmpty.size)
    }
}
