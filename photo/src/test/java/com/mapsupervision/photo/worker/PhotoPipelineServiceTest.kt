package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.Color
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.PhotoDailyLogDataResult
import com.mapsupervision.domain.service.PhotoMaterialDataResult
import com.mapsupervision.domain.service.PhotoOcrService
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking

import com.mapsupervision.domain.model.ProjectStorageRef

@RunWith(RobolectricTestRunner::class)
class PhotoPipelineServiceTest {

    @Test
    fun `capture output and thumbnails stay under public slug project storage`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            ocrService = FakePhotoOcrService()
        )

        val storageRef = ProjectStorageRef("project-1", "project-slug-1")
        val captureFile = service.createCaptureOutputFile(
            storageRef = storageRef,
            capturedAt = 1710000000000L,
            locationLabel = "Address A",
            note = "Test",
            folderType = CaptureFolderType.NODE,
            objectCode = "NODE-01"
        )
        assertTrue(captureFile.absolutePath.contains("MapSupervision"))
        assertTrue(captureFile.absolutePath.contains("project-slug-1"))
        assertTrue(captureFile.absolutePath.contains("Media"))
        assertTrue(captureFile.absolutePath.contains("Media${File.separator}Node${File.separator}NODE-01"))

        val source = File.createTempFile("photo-source", ".jpg").apply {
            outputStream().use { output ->
                Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.BLUE)
                    compress(Bitmap.CompressFormat.JPEG, 90, output)
                    recycle()
                }
            }
        }

        val thumbnail = service.createThumbnail(storageRef, source)
        assertEquals(source.absolutePath, thumbnail.absolutePath)
    }

    @Test
    fun `capture output goes to custom root if custom path is set`() {
        val context = RuntimeEnvironment.getApplication()
        val storageManager = ProjectStorageManager(context)
        val customDir = File(context.cacheDir, "custom-location")
        storageManager.setCustomPath("custom-slug", customDir.absolutePath)
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = storageManager,
            ocrService = FakePhotoOcrService()
        )

        val storageRef = ProjectStorageRef("proj-custom", "custom-slug")
        val captureFile = service.createCaptureOutputFile(
            storageRef = storageRef,
            capturedAt = 1710000000000L,
            locationLabel = "Address A",
            note = "Test",
            folderType = CaptureFolderType.NODE,
            objectCode = "NODE-01"
        )
        assertTrue(captureFile.absolutePath.startsWith(customDir.absolutePath))
    }

    @Test
    fun `capture output filenames differ across millisecond captures without numeric suffix`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            ocrService = FakePhotoOcrService()
        )

        val storageRef = ProjectStorageRef("project-1", "project-slug-1")
        val first = service.createCaptureOutputFile(
            storageRef = storageRef,
            capturedAt = 1710000000000L,
            locationLabel = "Address A",
            note = "Test",
            folderType = CaptureFolderType.NODE,
            objectCode = "NODE-01"
        )
        val second = service.createCaptureOutputFile(
            storageRef = storageRef,
            capturedAt = 1710000000001L,
            locationLabel = "Address A",
            note = "Test",
            folderType = CaptureFolderType.NODE,
            objectCode = "NODE-01"
        )

        assertTrue(first.name != second.name)
        assertTrue(!first.name.contains("_1.jpg"))
        assertTrue(!second.name.contains("_1.jpg"))
    }

    @Test
    fun `buildStampedVideoTempFile keeps export beside source video`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            ocrService = FakePhotoOcrService()
        )

        val source = File(context.cacheDir, "clip.mp4").apply { writeText("video") }
        val temp = service.buildStampedVideoTempFile(source)

        assertTrue(temp.parentFile == source.parentFile)
        assertTrue(temp.name == "clip_stamped.mp4")
    }

    @Test
    fun `route capture output and video files go under route folders`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            ocrService = FakePhotoOcrService()
        )

        val storageRef = ProjectStorageRef("project-1", "project-slug-1")
        val captureFile = service.createCaptureOutputFile(
            storageRef = storageRef,
            capturedAt = 1710000000000L,
            locationLabel = "Address A",
            note = "Test",
            folderType = CaptureFolderType.ROUTE,
            objectCode = "ROUTE A/1"
        )
        val videoFile = service.createCaptureVideoOutputFile(
            storageRef = storageRef,
            capturedAt = 1710000000000L,
            locationLabel = "Address A",
            note = "Test",
            folderType = CaptureFolderType.ROUTE,
            objectCode = "ROUTE A/1"
        )

        val routePath = "Media${File.separator}Route${File.separator}ROUTE-A1"
        assertTrue(captureFile.absolutePath.contains(routePath))
        assertTrue(videoFile.absolutePath.contains(routePath))
    }

    @Test
    fun `sanitizeFolderName trims and replaces invalid filesystem characters`() {
        val context = RuntimeEnvironment.getApplication()
        val storage = ProjectStorageManager(context)

        assertEquals("NODE01", storage.sanitizeFolderName(" NODE/01 "))
        assertEquals("Unnamed", storage.sanitizeFolderName("   "))
    }

    @Test
    fun `replaceFileSafely failure restores original backup`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            ocrService = FakePhotoOcrService()
        )

        val destFile = File(context.cacheDir, "original_dest.mp4").apply { writeText("original video content") }
        val sourceFile = File(context.cacheDir, "non_existent_source.mp4")

        val result = runCatching {
            service.replaceFileSafely(sourceFile, destFile)
        }

        org.junit.Assert.assertTrue(result.isFailure)
        org.junit.Assert.assertTrue(destFile.exists())
        org.junit.Assert.assertEquals("original video content", destFile.readText())
    }

    @Test
    fun `exportVideoStampWithTempSwap swaps in exported temp file`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            ocrService = FakePhotoOcrService()
        )

        val source = File(context.cacheDir, "video-source.mp4").apply { writeText("raw video") }

        service.exportVideoStampWithTempSwap(source) { temp ->
            temp.writeText("stamped video")
        }

        assertTrue(source.exists())
        org.junit.Assert.assertEquals("stamped video", source.readText())
        assertTrue(!File(context.cacheDir, "video-source_stamped.mp4").exists())
        assertTrue(!File(context.cacheDir, "video-source_backup.mp4").exists())
    }

    @Test
    fun `createStampOverlayBitmap keeps provided tile snapshot`() {
        val tile = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val stamp = com.mapsupervision.domain.model.CaptureStamp(
            timestampMs = 1710000000000L,
            latitude = 10.12345,
            longitude = 106.98765,
            address = "123 Street",
            note = "Overlay",
            bearingDeg = 30f
        )

        val overlay = PhotoStampRenderer.createStampOverlayBitmap(
            frameWidthPx = 320,
            frameHeightPx = 240,
            stamp = stamp,
            tileBitmap = tile
        )

        assertTrue(!tile.isRecycled)
        assertEquals(320, overlay.width)
        assertEquals(240, overlay.height)
        assertEquals(204, PhotoStampRenderer.MINIMAP_TILE_ALPHA)
        overlay.recycle()
        tile.recycle()
    }
}

private class FakePhotoOcrService : PhotoOcrService {
    override suspend fun extractMaterialData(imageUri: String): PhotoMaterialDataResult {
        return PhotoMaterialDataResult(true, null, null, null, null)
    }

    override suspend fun extractDailyLogData(imageUri: String): PhotoDailyLogDataResult {
        return PhotoDailyLogDataResult(true, null, null, null, null)
    }
}
