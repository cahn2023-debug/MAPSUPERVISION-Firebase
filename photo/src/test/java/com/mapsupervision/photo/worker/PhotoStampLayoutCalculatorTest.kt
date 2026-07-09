package com.mapsupervision.photo.worker

import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStampLayoutCalculatorTest {

    @Test
    fun `buildContent keeps only time and device coordinates`() {
        val content = PhotoStampLayoutCalculator.buildContent(
            timestampMs = 0L,
            latitude = 10.12345,
            longitude = 106.98765,
            missingLocationText = "Khong co vi tri"
        )

        assertEquals(2, content.rows.size)
        assertEquals(PhotoStampLayoutCalculator.formatTime(0L), content.rows[0].lines.first())
        assertEquals("Vĩ độ: 10.1235\nKinh độ: 106.9877", content.coordinateText)
        assertTrue(content.rows[1].lines.first().contains("10.1235"))
    }

    @Test
    fun `layout keeps minimap fixed even when extra rows are supplied`() {
        val baseRows = listOf(
            PhotoStampRow(PhotoStampLayoutCalculator.timeIcon, listOf("09:30  13/06/2026")),
            PhotoStampRow(PhotoStampLayoutCalculator.locationIcon, listOf("10.1235, 106.9877"))
        )
        val extraRows = baseRows + PhotoStampRow("?", listOf("Extra"))

        val base = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = baseRows,
            textWidth = { it.length * 14f },
            iconWidth = { 26f },
            showMap = true
        )
        val withExtra = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = extraRows,
            textWidth = { it.length * 14f },
            iconWidth = { 26f },
            showMap = true
        )

        assertNotNull(base.mapRect)
        assertEquals(base.mapRect!!.width, withExtra.mapRect!!.width, 0.001f)
        assertEquals(base.mapRect!!.height, withExtra.mapRect!!.height, 0.001f)
        assertEquals(base.mapRect!!.bottom, withExtra.mapRect!!.bottom, 0.001f)
        assertEquals(270f, base.mapRect!!.width, 0.01f)
        assertEquals(270f, base.mapRect!!.height, 0.01f)
        assertEquals(32.4f, base.mapCornerRadius, 0.01f)
    }

    @Test
    fun `same capture stamp builds identical preview and saved content`() {
        val stamp = CaptureStamp(
            timestampMs = 1710000000000L,
            latitude = 10.12345,
            longitude = 106.98765,
            address = "123 Street, District 1",
            note = "Kiem tra overlay",
            bearingDeg = 45f
        )

        val fromStamp = PhotoStampLayoutCalculator.buildContent(stamp, missingLocationText = "Khong co vi tri")
        val manual = PhotoStampLayoutCalculator.buildContent(
            timestampMs = stamp.timestampMs,
            latitude = stamp.latitude,
            longitude = stamp.longitude,
            missingLocationText = "Khong co vi tri",
            address = stamp.address,
            note = stamp.note
        )

        assertEquals(fromStamp.rows, manual.rows)
        assertEquals(fromStamp.coordinateText, manual.coordinateText)
    }

    @Test
    fun `aspect crop helper matches expected bounds`() {
        assertEquals(
            AspectCropRect(240, 0, 1440, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_4_3)
        )
        assertEquals(
            AspectCropRect(0, 0, 1920, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_16_9)
        )
        assertEquals(
            AspectCropRect(420, 0, 1080, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_1_1)
        )
        assertEquals(
            AspectCropRect(0, 0, 1920, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_FULL)
        )
    }
}
