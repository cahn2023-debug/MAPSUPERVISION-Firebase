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
    fun `adaptive zoom keeps camera centered even with scoped map data`() {
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

        assertTrue(viewport.zoom in PhotoStampRenderer.MINIMAP_MIN_ZOOM..PhotoStampRenderer.MINIMAP_MAX_ZOOM)
        assertEquals(rect.centerX(), cameraX, 0.5f)
        assertEquals(rect.centerY(), cameraY, 0.5f)
    }

    @Test
    fun `movement path participates in adaptive viewport fitting`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val scene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            movementPath = listOf(
                21.0280 to 105.8340,
                21.0500 to 105.8700
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

        assertTrue(viewport.zoom < PhotoStampRenderer.MINIMAP_MAX_ZOOM)
        assertTrue(viewport.zoom >= PhotoStampRenderer.MINIMAP_MIN_ZOOM)
    }

    @Test
    fun `latched minimap zoom does not restore close zoom after returning`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val scene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            movementPath = listOf(
                21.0280 to 105.8340,
                21.0300 to 105.8390
            ),
            minimapZoom = PhotoStampRenderer.MINIMAP_MIN_ZOOM
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

        assertEquals(PhotoStampRenderer.MINIMAP_MIN_ZOOM, viewport.zoom)
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

    @Test
    fun `resolve stamp tile bitmap keeps preview snapshot for scoped map`() {
        val tile = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val stamp = CaptureStamp(
            timestampMs = 1000L,
            latitude = 21.0280,
            longitude = 105.8340,
            address = "",
            note = "",
            bearingDeg = 15f,
            mapScene = CaptureStampMapScene(
                cameraLatitude = 21.0280,
                cameraLongitude = 105.8340,
                bearingDeg = 15f,
                nodes = listOf(CaptureStampMapNode(code = "N1", latitude = 21.0280, longitude = 105.8340))
            )
        )
        val viewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = RectF(0f, 0f, 270f, 270f),
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 15f,
            borderWidth = 6f,
            outerDotRadius = 20f,
            mapScene = stamp.mapScene
        )

        val resolvedTile = PhotoStampRenderer.resolveStampTileBitmap(
            stamp = stamp,
            tileBitmap = tile,
            viewport = viewport
        )

        assertTrue(resolvedTile === tile)
        tile.recycle()
    }

    @Test
    fun `build content includes status tag row when present`() {
        val stamp = CaptureStamp(
            timestampMs = 1000L,
            latitude = 21.0280,
            longitude = 105.8340,
            address = "Ha Noi",
            note = "",
            bearingDeg = 0f,
            statusTag = "Hiện trạng"
        )
        val content = PhotoStampLayoutCalculator.buildContent(
            stamp = stamp,
            missingLocationText = "Không có vị trí"
        )
        val tagRow = content.rows.find { it.icon == PhotoStampLayoutCalculator.tagIcon }
        assertNotNull(tagRow)
        assertEquals("Hiện trạng", tagRow!!.lines.first())
    }

    @Test
    fun `PhotoStampLayoutCalculator scales dot radiuses by markerScale`() {
        val baseLayout = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = emptyList(),
            textWidth = { 0f },
            iconWidth = { 0f },
            showMap = true,
            markerScale = 1.0f
        )
        val scaledLayout = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = emptyList(),
            textWidth = { 0f },
            iconWidth = { 0f },
            showMap = true,
            markerScale = 1.5f
        )

        assertEquals(baseLayout.mapDotOuterRadius * 1.5f, scaledLayout.mapDotOuterRadius, 0.001f)
        assertEquals(baseLayout.mapDotInnerRadius * 1.5f, scaledLayout.mapDotInnerRadius, 0.001f)
        assertEquals(baseLayout.mapDotCoreRadius * 1.5f, scaledLayout.mapDotCoreRadius, 0.001f)
    }

    @Test
    fun `resolveMinimapViewport respects custom minimap zoom limit`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val scene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            bearingDeg = 0f,
            minimapZoom = 20,
            markerScale = 1.2f,
            fovAngleDeg = 60f,
            fovLengthScale = 1.2f
        )

        val viewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = rect,
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 0f,
            borderWidth = 6f,
            outerDotRadius = 20f,
            mapScene = scene
        )

        assertEquals(20, viewport.zoom)
    }

    @Test
    fun `resolveMinimapViewport adapts with wider FOV angle and longer FOV length`() {
        val rect = RectF(0f, 0f, 270f, 270f)
        val standardScene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            bearingDeg = 45f,
            markerScale = 1.0f,
            fovAngleDeg = 30f,
            fovLengthScale = 1.0f
        )
        val wideLongScene = CaptureStampMapScene(
            cameraLatitude = 21.0280,
            cameraLongitude = 105.8340,
            bearingDeg = 45f,
            markerScale = 1.5f,
            fovAngleDeg = 90f,
            fovLengthScale = 1.5f
        )

        val standardViewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = rect,
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 45f,
            borderWidth = 6f,
            outerDotRadius = 20f,
            mapScene = standardScene
        )
        val wideLongViewport = PhotoStampRenderer.resolveMinimapViewport(
            rect = rect,
            latitude = 21.0280,
            longitude = 105.8340,
            bearingDeg = 45f,
            borderWidth = 6f,
            outerDotRadius = 30f,
            mapScene = wideLongScene
        )

        assertTrue(standardViewport.zoom in PhotoStampRenderer.MINIMAP_MIN_ZOOM..PhotoStampRenderer.MINIMAP_MAX_ZOOM)
        assertTrue(wideLongViewport.zoom in PhotoStampRenderer.MINIMAP_MIN_ZOOM..PhotoStampRenderer.MINIMAP_MAX_ZOOM)
    }
}

