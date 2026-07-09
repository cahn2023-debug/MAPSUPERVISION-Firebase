package com.mapsupervision.photo.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.net.toUri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.mapsupervision.domain.service.PhotoOcrService
import com.mapsupervision.domain.service.PhotoMaterialDataResult
import com.mapsupervision.domain.service.PhotoDailyLogDataResult
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.ProjectStorageRef
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
open class PhotoPipelineService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val storageManager: ProjectStorageManager,
    private val ocrService: PhotoOcrService
) : IPhotoPipelineService {
    companion object {
        private const val mainJpegQuality = 78
        private const val thumbJpegQuality = 72
        private const val thumbLongEdgePx = 320
    }

    override fun createCaptureOutputFile(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        folderType: CaptureFolderType,
        objectCode: String
    ): File {
        val root = storageManager.resolveObjectFolder(storageRef.slug, folderType == CaptureFolderType.ROUTE, objectCode)
        val fileName = storageManager.buildMediaFileName(capturedAt, locationLabel, note, "jpg")
        val out = storageManager.generateUniqueFile(root, fileName.substringBeforeLast("."), "jpg")
        AppLogger.d(
            "photo.pipeline.capture.output projectId=${storageRef.id} objectCode=$objectCode root=${root.absolutePath} file=${out.absolutePath}"
        )
        return out
    }

    override fun createCaptureVideoOutputFile(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        folderType: CaptureFolderType,
        objectCode: String
    ): File {
        val root = storageManager.resolveObjectFolder(storageRef.slug, folderType == CaptureFolderType.ROUTE, objectCode)
        val fileName = storageManager.buildMediaFileName(capturedAt, locationLabel, note, "mp4")
        val out = storageManager.generateUniqueFile(root, fileName.substringBeforeLast("."), "mp4")
        AppLogger.d(
            "photo.pipeline.capture.video.output projectId=${storageRef.id} objectCode=$objectCode root=${root.absolutePath} file=${out.absolutePath}"
        )
        return out
    }

    fun createEmptyPhoto(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        objectCode: String,
        engineer: String,
        folderType: CaptureFolderType = CaptureFolderType.NODE
    ): File {
        val out = createCaptureOutputFile(storageRef, capturedAt, locationLabel, note, folderType, objectCode)
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Thời gian: $time", 32f, 80f, paint)
        canvas.drawText("Đối tượng: $objectCode", 32f, 130f, paint)
        canvas.drawText("Kỹ sư: $engineer", 32f, 180f, paint)
        FileOutputStream(out).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, mainJpegQuality, fos) }
        bitmap.recycle()
        return out
    }

    fun applyWatermark(file: File, objectCode: String, engineer: String) {
        AppLogger.d(
            "photo.pipeline.watermark.start file=${file.absolutePath} objectCode=$objectCode engineer=$engineer"
        )
        val mutable = PhotoStampRenderer.loadMutableNormalizedBitmap(file) ?: run {
            AppLogger.d(
                "photo.pipeline.watermark.decode.fail file=${file.absolutePath} objectCode=$objectCode engineer=$engineer"
            )
            return
        }
        val canvas = Canvas(mutable)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Thời gian: $time", 20f, mutable.height - 120f, paint)
        canvas.drawText("Đối tượng: $objectCode", 20f, mutable.height - 80f, paint)
        canvas.drawText("Kỹ sư: $engineer", 20f, mutable.height - 40f, paint)
        PhotoStampRenderer.writeBitmap(file, mutable, mainJpegQuality)
        AppLogger.d(
            "photo.pipeline.watermark.done file=${file.absolutePath} objectCode=$objectCode engineer=$engineer"
        )
    }

    open override fun importFromGallery(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        folderType: CaptureFolderType,
        objectCode: String,
        sourceUri: String
    ): File {
        AppLogger.d(
            "photo.pipeline.gallery.import.start projectId=${storageRef.id} objectCode=$objectCode sourceUri=$sourceUri"
        )
        val uri = sourceUri.toUri()
        val mimeType = appContext.contentResolver.getType(uri) ?: ""
        val isVideo = mimeType.startsWith("video/") || uri.path?.endsWith(".mp4", ignoreCase = true) == true
        val out = if (isVideo) {
            createCaptureVideoOutputFile(storageRef, capturedAt, locationLabel, note, folderType, objectCode)
        } else {
            createCaptureOutputFile(storageRef, capturedAt, locationLabel, note, folderType, objectCode)
        }
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open gallery input stream" }
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        AppLogger.d(
            "photo.pipeline.gallery.import.done projectId=${storageRef.id} objectCode=$objectCode file=${out.absolutePath}"
        )
        return out
    }

    private fun photoFolder(root: File, folderType: CaptureFolderType, objectCode: String): File {
        val category = when (folderType) {
            CaptureFolderType.NODE -> "photos/Nodes"
            CaptureFolderType.ROUTE -> "photos/Routes"
        }
        return File(File(root, category), storageManager.sanitizeFolderName(objectCode)).apply { mkdirs() }
    }

    private fun videoFolder(root: File, folderType: CaptureFolderType, objectCode: String): File {
        val category = when (folderType) {
            CaptureFolderType.NODE -> "media/videos/Nodes"
            CaptureFolderType.ROUTE -> "media/videos/Routes"
        }
        return File(File(root, category), storageManager.sanitizeFolderName(objectCode)).apply { mkdirs() }
    }

    override fun createThumbnail(storageRef: ProjectStorageRef, sourceFile: File): File {
        AppLogger.d(
            "photo.pipeline.thumbnail.skip.original_used projectId=${storageRef.id} source=${sourceFile.absolutePath}"
        )
        return sourceFile
    }

    /**
     * Extract material data from photo using OCR
     * @param photoFile Photo file to process
     * @return Material data extracted from the photo
     */
    suspend fun extractMaterialDataFromPhoto(photoFile: File): PhotoMaterialDataResult {
        return ocrService.extractMaterialData(photoFile.absolutePath)
    }

    /**
     * Extract daily log data from photo using OCR
     * @param photoFile Photo file to process
     * @return Daily log data extracted from the photo
     */
    suspend fun extractDailyLogDataFromPhoto(photoFile: File): PhotoDailyLogDataResult {
        return ocrService.extractDailyLogData(photoFile.absolutePath)
    }

    override fun applyStamp(
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Any?
    ) {
        AppLogger.d(
            "photo.pipeline.stamp.start file=${file.absolutePath} lat=${stamp.latitude} lng=${stamp.longitude} bearingDeg=${stamp.bearingDeg}"
        )
        val bitmap = tileBitmap as? Bitmap
        PhotoStampRenderer.applyStamp(file, stamp, ratio, bitmap)
        AppLogger.d("photo.pipeline.stamp.done file=${file.absolutePath}")
    }

    @OptIn(markerClass = [UnstableApi::class])
    override suspend fun exportVideoStamp(file: File, stamp: CaptureStamp, tileBitmap: Any?) {
        val bitmap = tileBitmap as? Bitmap
        val (frameWidth, frameHeight) = PhotoStampRenderer.resolveVideoOverlaySize(file)
        val overlayBitmap = PhotoStampRenderer.createStampOverlayBitmap(
            frameWidthPx = frameWidth,
            frameHeightPx = frameHeight,
            stamp = stamp,
            tileBitmap = bitmap
        )
        try {
            exportVideoStampWithTempSwap(file) { tempOutput ->
                val exportDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
                val overlayEffect = OverlayEffect(
                    listOf(BitmapOverlay.createStaticBitmapOverlay(overlayBitmap))
                )
                val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(file.toUri()))
                    .setEffects(Effects(emptyList(), listOf(overlayEffect)))
                    .build()
                val composition = Composition.Builder(
                    EditedMediaItemSequence.Builder(listOf(editedMediaItem)).build()
                ).build()
                withContext(Dispatchers.Main) {
                    val transformer = Transformer.Builder(appContext)
                        .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    exportDeferred.complete(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    exportDeferred.completeExceptionally(exportException)
                                }
                            }
                        )
                        .build()

                    AppLogger.d("photo.pipeline.video.stamp.start file=${file.absolutePath} temp=${tempOutput.absolutePath}")
                    transformer.start(composition, tempOutput.absolutePath)
                }
                kotlinx.coroutines.withTimeout(5 * 60 * 1000) {
                    exportDeferred.await()
                }
            }
            AppLogger.d("photo.pipeline.video.stamp.done file=${file.absolutePath}")
        } catch (e: Throwable) {
            AppLogger.e(e, "photo.pipeline.video.stamp.failed file=${file.absolutePath}")
            throw e
        } finally {
            overlayBitmap.recycle()
        }
    }

    internal suspend fun exportVideoStampWithTempSwap(
        file: File,
        exportToTemp: suspend (File) -> Unit
    ) {
        val tempOutput = buildStampedVideoTempFile(file)
        try {
            exportToTemp(tempOutput)
            replaceFileSafely(tempOutput, file)
        } finally {
            if (tempOutput.exists()) {
                tempOutput.delete()
            }
        }
    }

    internal fun buildStampedVideoTempFile(sourceFile: File): File {
        val parent = sourceFile.parentFile ?: error("Missing parent for ${sourceFile.absolutePath}")
        return File(parent, "${sourceFile.nameWithoutExtension}_stamped.mp4")
    }

    internal fun replaceFileSafely(source: File, destination: File) {
        val backup = File(destination.parentFile, "${destination.nameWithoutExtension}_backup.${destination.extension}")
        if (backup.exists()) {
            backup.delete()
        }
        if (destination.exists() && !destination.renameTo(backup)) {
            throw IllegalStateException("Cannot move original video before replacing it")
        }
        try {
            if (!source.renameTo(destination)) {
                source.copyTo(destination, overwrite = true)
            }
            if (backup.exists()) {
                backup.delete()
            }
        } catch (e: Throwable) {
            AppLogger.e(e, "photo.pipeline.video.replace.failed source=${source.absolutePath} dest=${destination.absolutePath}")
            if (backup.exists()) {
                try {
                    if (destination.exists()) {
                        destination.delete()
                    }
                    backup.renameTo(destination)
                } catch (restoreError: Throwable) {
                    AppLogger.e(restoreError, "photo.pipeline.video.restore.backup.failed")
                }
            }
            throw e
        }
    }
}
