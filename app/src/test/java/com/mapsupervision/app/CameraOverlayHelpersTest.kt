package com.mapsupervision.app

import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.VideoStampTimelineSample
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
    fun `minimap zoom stays within bounds`() {
        assertEquals(19, resolveLatchedMinimapZoom(19, 19))
        assertEquals(17, resolveLatchedMinimapZoom(17, 19))
        assertEquals(18, resolveLatchedMinimapZoom(18, 17))
        assertEquals(15, resolveLatchedMinimapZoom(15, 17))
        assertEquals(14, resolveLatchedMinimapZoom(14, 17))
        assertEquals(14, resolveLatchedMinimapZoom(13, 17))
        assertEquals(19, resolveLatchedMinimapZoom(20, 17))
    }

    @Test
    fun `build capture stamp carries statusTag into stamp`() {
        val location = PhotoLocationSnapshot(latitude = 10.0, longitude = 106.0)
        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = location,
            bearingDeg = 0f,
            statusTag = "Thi công"
        )
        assertEquals("Thi công", stamp.statusTag)
    }

    @Test
    fun `build video stamp timeline sample carries statusTag`() {
        val location = PhotoLocationSnapshot(latitude = 10.0, longitude = 106.0)
        val sample = buildVideoStampTimelineSample(
            recordingStartElapsedMs = 100L,
            nowElapsedMs = 350L,
            location = location,
            address = "",
            note = "",
            bearingDeg = 0f,
            statusTag = "Hoàn trả"
        )
        assertEquals("Hoàn trả", sample.stamp.statusTag)
    }

    @Test
    fun `camera controls layout shows everything on tall screens`() {
        val layout = computeCameraControlsLayout(800)

        assertTrue(layout.showNoteField)
        assertTrue(layout.showZoomBar)
        assertFalse(layout.useCompactSpacing)
    }

    @Test
    fun `camera controls layout hides zoom bar in compact band`() {
        val layout = computeCameraControlsLayout(CAMERA_CONTROLS_FULL_MIN_HEIGHT_DP - 1)

        assertTrue(layout.showNoteField)
        assertFalse(layout.showZoomBar)
        assertTrue(layout.useCompactSpacing)
    }

    @Test
    fun `camera controls layout hides note before zoom is restored`() {
        // Dải compact thấp: ghi chú và zoom đều ẩn — zoom bị ẩn trước ghi chú khi thiếu chỗ (FR-2).
        val midBand = computeCameraControlsLayout(CAMERA_CONTROLS_MINIMAL_MAX_HEIGHT_DP + 10)
        val lowBand = computeCameraControlsLayout(CAMERA_CONTROLS_MINIMAL_MAX_HEIGHT_DP - 10)

        assertTrue(midBand.showNoteField && !midBand.showZoomBar)
        assertFalse(lowBand.showNoteField)
        assertFalse(lowBand.showZoomBar)
    }

    @Test
    fun `camera controls layout minimal mode at very short heights`() {
        val layout = computeCameraControlsLayout(CAMERA_CONTROLS_MINIMAL_MAX_HEIGHT_DP - 1)

        assertFalse(layout.showNoteField)
        assertFalse(layout.showZoomBar)
        assertTrue(layout.useCompactSpacing)
    }

    @Test
    fun `camera controls layout handles zero and negative heights defensively`() {
        val zero = computeCameraControlsLayout(0)
        val negative = computeCameraControlsLayout(-100)

        assertFalse(zero.showNoteField)
        assertFalse(zero.showZoomBar)
        assertFalse(negative.showNoteField)
        assertFalse(negative.showZoomBar)
    }

    @Test
    fun `camera controls layout thresholds are consistent`() {
        // Ngưỡng full phải cao hơn ngưỡng minimal để dải compact tồn tại.
        org.junit.Assert.assertTrue(
            CAMERA_CONTROLS_FULL_MIN_HEIGHT_DP > CAMERA_CONTROLS_MINIMAL_MAX_HEIGHT_DP
        )
        // Ranh giới đúng tại chính ngưỡng.
        val atMinimalBoundary = computeCameraControlsLayout(CAMERA_CONTROLS_MINIMAL_MAX_HEIGHT_DP)
        val atFullBoundary = computeCameraControlsLayout(CAMERA_CONTROLS_FULL_MIN_HEIGHT_DP)
        assertTrue(atMinimalBoundary.showNoteField)
        assertTrue(atFullBoundary.showZoomBar)
    }

    @Test
    fun `camera controls layout adapts smoothly to tablet landscape and portrait heights`() {
        // Tablet 10" Portrait (height ~800-1200dp): Hiển thị đầy đủ mọi điều khiển
        val tabletPortrait = computeCameraControlsLayout(900)
        assertTrue(tabletPortrait.showNoteField)
        assertTrue(tabletPortrait.showZoomBar)
        assertFalse(tabletPortrait.useCompactSpacing)

        // Tablet Landscape hoặc màn hình thấp (height ~400-550dp): Giữ compact, ưu tiên Mode Selector và Shutter
        val tabletLandscapeCompact = computeCameraControlsLayout(500)
        assertTrue(tabletLandscapeCompact.showNoteField)
        assertFalse(tabletLandscapeCompact.showZoomBar)
        assertTrue(tabletLandscapeCompact.useCompactSpacing)

        val smallLandscape = computeCameraControlsLayout(420)
        assertFalse(smallLandscape.showNoteField)
        assertFalse(smallLandscape.showZoomBar)
        assertTrue(smallLandscape.useCompactSpacing)
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
    fun `camera movement path keeps valid points and ignores invalid snapshots`() {
        val path = CameraMovementPath()
        val first = PhotoLocationSnapshot(
            latitude = 10.0,
            longitude = 106.0,
            accuracyM = 3f,
            isMock = false,
            status = PhotoLocationStatus.OK
        )
        val second = first.copy(latitude = 10.1, longitude = 106.1)

        assertEquals(listOf(10.0 to 106.0), path.append(first))
        assertEquals(listOf(10.0 to 106.0), path.append(first.copy(accuracyM = 9f)))
        assertEquals(listOf(10.0 to 106.0, 10.1 to 106.1), path.append(second))
        assertEquals(listOf(10.0 to 106.0, 10.1 to 106.1), path.append(PhotoLocationSnapshot()))
        assertEquals(listOf(10.0 to 106.0, 10.1 to 106.1, 10.2 to 106.2), path.append(second.copy(latitude = 10.2, longitude = 106.2)))
    }

    @Test
    fun `camera movement path resets with a new session`() {
        val firstSession = CameraMovementPath()
        firstSession.append(PhotoLocationSnapshot(latitude = 10.0, longitude = 106.0))

        val secondSession = CameraMovementPath()

        assertEquals(listOf(10.0 to 106.0), firstSession.snapshot())
        assertTrue(secondSession.snapshot().isEmpty())
    }

    @Test
    fun `capture stamp carries latched minimap zoom into timeline state`() {
        val location = PhotoLocationSnapshot(latitude = 10.0, longitude = 106.0)

        val sample = buildVideoStampTimelineSample(
            recordingStartElapsedMs = 100L,
            nowElapsedMs = 350L,
            location = location,
            address = "",
            note = "",
            bearingDeg = 0f,
            movementPath = listOf(10.0 to 106.0, 10.1 to 106.1),
            minimapZoom = 16
        )

        assertEquals(16, sample.stamp.mapScene?.minimapZoom)
        assertEquals(
            listOf(10.0 to 106.0, 10.1 to 106.1),
            sample.stamp.mapScene?.movementPath
        )
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
    fun `build video stamp timeline sample keeps live preview fields`() {
        val location = PhotoLocationSnapshot(
            latitude = 10.12345,
            longitude = 106.98765,
            accuracyM = 2f,
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

        val sample = buildVideoStampTimelineSample(
            recordingStartElapsedMs = 1_000L,
            nowElapsedMs = 1_275L,
            location = location,
            address = "123 Street",
            note = "Video note",
            bearingDeg = 87.6f,
            nodes = listOf(node),
            movementPath = listOf(10.12345 to 106.98765, 10.12355 to 106.98775),
            tileBitmap = "tile"
        )

        assertEquals(275L, sample.elapsedMs)
        assertEquals("tile", sample.tileBitmap)
        assertEquals(10.12345, sample.stamp.latitude)
        assertEquals(106.98765, sample.stamp.longitude)
        assertEquals("123 Street", sample.stamp.address)
        assertEquals("Video note", sample.stamp.note)
        assertEquals(87.6f, sample.stamp.bearingDeg)
        assertEquals(10.12345, sample.stamp.mapScene!!.cameraLatitude)
        assertEquals(106.98765, sample.stamp.mapScene!!.cameraLongitude)
        assertEquals(
            listOf(10.12345 to 106.98765, 10.12355 to 106.98775),
            sample.stamp.mapScene!!.movementPath
        )
    }

    @Test
    fun `build capture stamp keeps movement path separate from design routes`() {
        val location = PhotoLocationSnapshot(latitude = 10.0, longitude = 106.0)
        val movementPath = listOf(10.0 to 106.0, 10.1 to 106.1)

        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = location,
            bearingDeg = 0f,
            movementPath = movementPath
        )
        val mapScene = requireNotNull(stamp.mapScene)

        assertEquals(movementPath, mapScene.movementPath)
        assertTrue(mapScene.nodes.isEmpty())
        assertTrue(mapScene.routes.isEmpty())
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
        val timelineSamples = listOf(
            VideoStampTimelineSample(
                elapsedMs = 0L,
                stamp = stamp,
                tileBitmap = "tile"
            )
        )

        val pipeline = object : IPhotoPipelineService {
            override fun createCaptureOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                statusTag: String?
            ) = error("unused")

            override fun createCaptureVideoOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                statusTag: String?
            ) = error("unused")

            override fun importFromGallery(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                sourceUri: String,
                statusTag: String?
            ) = error("unused")
            override fun createThumbnail(storageRef: com.mapsupervision.domain.model.ProjectStorageRef, sourceFile: File) = error("unused")
            override fun applyStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, ratio: com.mapsupervision.domain.model.CameraAspectRatio, tileBitmap: Any?) = error("unused")
            override suspend fun exportVideoStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, tileBitmap: Any?) {
                order += "legacy-export"
            }
            override suspend fun exportVideoStamp(file: File, samples: List<VideoStampTimelineSample>) {
                order += "timeline-export"
                assertEquals(timelineSamples, samples)
                file.writeText("stamped")
            }
        }

        val saved = postProcessRecordedVideo(
            videoFile = contextFile,
            stampEnabled = true,
            stampAtRecordStart = stamp,
            tileBitmap = null,
            timelineSamples = timelineSamples,
            photoPipelineService = pipeline,
            setProcessingVideoStamp = { },
            onSavePhoto = {
                order += "save"
                true
            },
            onPhotoCaptured = { order += "captured" }
        )

        assertTrue(saved)
        assertEquals(listOf("timeline-export", "save", "captured"), order)
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
                objectCode: String,
                statusTag: String?
            ) = error("unused")

            override fun createCaptureVideoOutputFile(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                statusTag: String?
            ) = error("unused")

            override fun importFromGallery(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                sourceUri: String,
                statusTag: String?
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
            timelineSamples = emptyList(),
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

    @Test
    fun `formatRecordingDuration produces clean MM SS output`() {
        assertEquals("00:00", formatRecordingDuration(0))
        assertEquals("00:05", formatRecordingDuration(5))
        assertEquals("01:25", formatRecordingDuration(85))
        assertEquals("12:03", formatRecordingDuration(723))
    }
}
