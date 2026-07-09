package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import com.mapsupervision.domain.model.CaptureStampMapNode
import com.mapsupervision.domain.model.CaptureStampMapRoute
import com.mapsupervision.domain.model.CaptureStampMapScene
import com.mapsupervision.domain.model.CaptureStamp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoStampRendererTest {

    @Test
    fun `loadMutableNormalizedBitmap rotates image and writeBitmap resets exif`() {
        val tempFile = File.createTempFile("photo-orientation", ".jpg")
        tempFile.deleteOnExit()

        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        tempFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        bitmap.recycle()

        ExifInterface(tempFile.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val normalized = PhotoStampRenderer.loadMutableNormalizedBitmap(tempFile)
        assertNotNull(normalized)
        assertEquals(20, normalized!!.width)
        assertEquals(40, normalized.height)

        PhotoStampRenderer.writeBitmap(tempFile, normalized, 90)

        val savedOrientation = ExifInterface(tempFile.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        assertEquals(ExifInterface.ORIENTATION_NORMAL, savedOrientation)
    }

    @Test
    fun `fixed zoom keeps camera centered even with scoped map data`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val scene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            bearingDeg = 25f,
            nodes = listOf(
                CaptureStampMapNode(code = "A", latitude = 21.0280, longitude = 105.8340),
                CaptureStampMapNode(code = "B", latitude = 21.0315, longitude = 105.8410)
            ),
            routes = listOf(
                CaptureStampMapRoute(
                    code = "R1",
                    points = listOf(
                        21.0265 to 105.8325,
                        21.0290 to 105.8365,
                        21.0315 to 105.8410
                    )
                )
            )
        )

        val viewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = rect,
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 25f,
            borderWidth = 6f,
            outerDotRadius = 20f,
            mapScene = scene
        )
        val (cameraX, cameraY) = PhotoStampRenderer.getCanvasCoords(
            21.0280,
            105.8340,
            viewport.frame,
            rect,
            512,
            viewport.zoom
        )

        assertEquals(PhotoStampRenderer.MINIMAP_MAX_ZOOM, viewport.zoom)
        assertEquals(rect.centerX(), cameraX, 0.5f)
        assertEquals(rect.centerY(), cameraY, 0.5f)
    }

    @Test
    fun `resolveMinimapViewport keeps camera cone away from minimap edges`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val scene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            bearingDeg = 80f,
            nodes = listOf(
                CaptureStampMapNode(code = "A", latitude = 21.0282, longitude = 105.8342)
            )
        )

        val viewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = rect,
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 80f,
            borderWidth = 6f,
            outerDotRadius = 20f,
            mapScene = scene
        )
        val (cameraX, cameraY) = PhotoStampRenderer.getCanvasCoords(
            21.0280,
            105.8340,
            viewport.frame,
            rect,
            512,
            viewport.zoom
        )
        val coneLen = rect.width() * 0.42f * 0.8f

        assertTrue(cameraX - rect.left >= coneLen - 1f)
        assertTrue(rect.right - cameraX >= coneLen - 1f)
        assertTrue(cameraY - rect.top >= coneLen - 1f)
        assertTrue(rect.bottom - cameraY >= coneLen - 1f)
    }

    @Test
    fun `fixed zoom keeps camera marker centered in minimap`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val scene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            bearingDeg = 30f,
            nodes = listOf(
                CaptureStampMapNode(code = "A", latitude = 21.0315, longitude = 105.8410)
            )
        )

        val viewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = rect,
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 30f,
            borderWidth = 6f,
            outerDotRadius = 20f,
            mapScene = scene
        )
        val (cameraX, cameraY) = PhotoStampRenderer.getCanvasCoords(
            21.0280,
            105.8340,
            viewport.frame,
            rect,
            512,
            viewport.zoom
        )

        assertEquals(rect.centerX(), cameraX, 0.5f)
        assertEquals(rect.centerY(), cameraY, 0.5f)
    }

    @Test
    fun `build content keeps minimap coordinates when only scoped map exists`() {
        val stamp = CaptureStamp(
            timestampMs = 1000L,
            latitude = null,
            longitude = null,
            address = "",
            note = "",
            bearingDeg = 15f,
            mapScene = CaptureStampMapScene(
                centerLatitude = 21.0280,
                centerLongitude = 105.8340,
                cameraLatitude = 21.0282,
                cameraLongitude = 105.8342,
                bearingDeg = 15f,
                nodes = listOf(CaptureStampMapNode(code = "N1", latitude = 21.0280, longitude = 105.8340))
            )
        )

        val content = PhotoStampLayoutCalculator.buildContent(
            stamp = stamp,
            missingLocationText = "Khong co vi tri"
        )

        assertEquals(21.0282, content.latitude)
        assertEquals(105.8342, content.longitude)
        assertNotNull(content.coordinateText)
    }
}
