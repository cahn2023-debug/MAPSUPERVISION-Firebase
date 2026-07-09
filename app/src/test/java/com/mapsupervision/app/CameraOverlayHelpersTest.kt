package com.mapsupervision.app

import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoPipelineService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

class CameraOverlayHelpersTest {

    @Test
    fun `image flash mapping follows selected mode`() {
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_AUTO, resolveImageCaptureFlashMode(CameraFlashMode.AUTO))
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_OFF, resolveImageCaptureFlashMode(CameraFlashMode.OFF))
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_ON, resolveImageCaptureFlashMode(CameraFlashMode.ON))
    }

    @Test
    fun `video torch only turns off for flash off`() {
        assertTrue(resolveVideoTorchEnabled(CameraFlashMode.AUTO))
        assertTrue(resolveVideoTorchEnabled(CameraFlashMode.ON))
        assertFalse(resolveVideoTorchEnabled(CameraFlashMode.OFF))
    }

    @Test
    fun `clampZoomRatio respects min and max`() {
        assertEquals(1f, clampZoomRatio(0.4f, 1f, 4f))
        assertEquals(2.5f, clampZoomRatio(2.5f, 1f, 4f))
        assertEquals(4f, clampZoomRatio(9f, 1f, 4f))
    }

    @Test
    fun `photo capture session blocks double start until finished`() {
        val session = PhotoCaptureSession()

        assertTrue(session.tryBeginCapture())
        assertFalse(session.tryBeginCapture())

        session.finishCapture()

        assertTrue(session.tryBeginCapture())
    }

    @Test
    fun `buildCaptureStamp keeps only device location inputs`() {
        val location = PhotoLocationSnapshot(
            latitude = 10.12345,
            longitude = 106.98765,
            accuracyM = 3.5f,
            isMock = false,
            status = PhotoLocationStatus.OK
        )

        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = location,
            bearingDeg = 87.6f
        )

        assertEquals(1234L, stamp.timestampMs)
        assertEquals(10.12345, stamp.latitude)
        assertEquals(106.98765, stamp.longitude)
        assertEquals("", stamp.address)
        assertEquals("", stamp.note)
        assertEquals(87.6f, stamp.bearingDeg)
        assertNull(stamp.objectContext)
        assertNull(stamp.mapScene)
    }

    @Test
    fun `preview stamp render key stays stable for same input`() {
        val location = PhotoLocationSnapshot(
            latitude = 10.123456,
            longitude = 106.987654,
            accuracyM = 3.5f,
            isMock = false,
            status = PhotoLocationStatus.OK
        )
        val viewport = com.mapsupervision.photo.worker.AspectCropRect(0, 0, 720, 1280)
        val tileKey = roundedLocationKey(location.latitude, location.longitude)

        val first = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = viewport,
            location = location,
            tileKey = tileKey,
            bearing = 45f
        )
        val second = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = viewport,
            location = location.copy(accuracyM = 9f),
            tileKey = tileKey,
            bearing = 45f
        )

        assertEquals(first, second)
    }

    @Test
    fun `preview stamp render key changes when tile changes`() {
        val base = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = com.mapsupervision.photo.worker.AspectCropRect(0, 0, 720, 1280),
            location = null,
            tileKey = null,
            bearing = 0f
        )
        val changed = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = com.mapsupervision.photo.worker.AspectCropRect(0, 0, 720, 1280),
            location = null,
            tileKey = roundedLocationKey(10.0, 11.0),
            bearing = 0f
        )

        assertFalse(base == changed)
    }

    @Test
    fun `post process recorded video exports before save when stamp enabled`() = runBlocking {
        val order = mutableListOf<String>()
        val contextFile = File.createTempFile("camera-video", ".mp4").apply { writeText("raw") }
        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = null,
            bearingDeg = 0f
        )

        val pipeline = object : IPhotoPipelineService {
            override fun createCaptureOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String
            ) = error("unused")

            override fun createCaptureVideoOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String
            ) = error("unused")

            override fun importFromGallery(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                sourceUri: String
            ) = error("unused")
            override fun createThumbnail(storageRef: com.mapsupervision.domain.model.ProjectStorageRef, sourceFile: File) = error("unused")
            override fun applyStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, ratio: com.mapsupervision.domain.model.CameraAspectRatio, tileBitmap: Any?) = error("unused")
            override suspend fun exportVideoStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, tileBitmap: Any?) {
                order += "export"
                file.writeText("stamped")
            }
        }

        val saved = postProcessRecordedVideo(
            videoFile = contextFile,
            stampEnabled = true,
            stampAtRecordStart = stamp,
            tileBitmap = null,
            photoPipelineService = pipeline,
            setProcessingVideoStamp = { },
            onSavePhoto = {
                order += "save"
                true
            },
            onPhotoCaptured = { order += "captured" }
        )

        assertTrue(saved)
        assertEquals(listOf("export", "save", "captured"), order)
        assertEquals("stamped", contextFile.readText())
    }

    @Test
    fun `post process recorded video skips export when stamp disabled`() = runBlocking {
        val order = mutableListOf<String>()
        val contextFile = File.createTempFile("camera-video", ".mp4").apply { writeText("raw") }

        val pipeline = object : IPhotoPipelineService {
            override fun createCaptureOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String
            ) = error("unused")

            override fun createCaptureVideoOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String
            ) = error("unused")

            override fun importFromGallery(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                sourceUri: String
            ) = error("unused")
            override fun createThumbnail(storageRef: com.mapsupervision.domain.model.ProjectStorageRef, sourceFile: File) = error("unused")
            override fun applyStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, ratio: com.mapsupervision.domain.model.CameraAspectRatio, tileBitmap: Any?) = error("unused")
            override suspend fun exportVideoStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, tileBitmap: Any?) {
                order += "export"
            }
        }

        val saved = postProcessRecordedVideo(
            videoFile = contextFile,
            stampEnabled = false,
            stampAtRecordStart = null,
            tileBitmap = null,
            photoPipelineService = pipeline,
            setProcessingVideoStamp = { },
            onSavePhoto = {
                order += "save"
                true
            },
            onPhotoCaptured = { order += "captured" }
        )

        assertTrue(saved)
        assertEquals(listOf("save", "captured"), order)
    }

    @Test
    fun `preview stamp render key changes when bearing changes`() {
        val base = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = com.mapsupervision.photo.worker.AspectCropRect(0, 0, 720, 1280),
            location = null,
            tileKey = null,
            bearing = 0f
        )
        val changed = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = com.mapsupervision.photo.worker.AspectCropRect(0, 0, 720, 1280),
            location = null,
            tileKey = null,
            bearing = 45f
        )

        assertFalse(base == changed)
    }

    @Test
    fun `buildCaptureStamp with GIS records populates camera coordinates`() {
        val location = PhotoLocationSnapshot(
            latitude = 10.12345,
            longitude = 106.98765,
            accuracyM = 3.5f,
            isMock = false,
            status = PhotoLocationStatus.OK
        )
        val node = com.mapsupervision.domain.model.GisNode(
            id = "node1",
            projectId = "project1",
            code = "N1",
            contractor = "contractor1",
            latitude = 10.123,
            longitude = 106.987
        )

        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = location,
            bearingDeg = 87.6f,
            nodes = listOf(node)
        )

        val mapScene = stamp.mapScene
        org.junit.Assert.assertNotNull(mapScene)
        assertEquals(10.12345, mapScene!!.cameraLatitude)
        assertEquals(106.98765, mapScene.cameraLongitude)
        assertEquals(10.12345, mapScene.centerLatitude)
        assertEquals(106.98765, mapScene.centerLongitude)
    }
}
