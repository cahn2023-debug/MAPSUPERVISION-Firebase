package com.mapsupervision.domain.service

import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.ProjectStorageRef
import java.io.File

enum class CaptureFolderType {
    NODE,
    ROUTE
}

interface IPhotoPipelineService {
    fun createCaptureOutputFile(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        folderType: CaptureFolderType,
        objectCode: String
    ): File

    fun createCaptureVideoOutputFile(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        folderType: CaptureFolderType,
        objectCode: String
    ): File

    fun importFromGallery(
        storageRef: ProjectStorageRef,
        capturedAt: Long,
        locationLabel: String?,
        note: String?,
        folderType: CaptureFolderType,
        objectCode: String,
        sourceUri: String
    ): File

    fun createThumbnail(storageRef: ProjectStorageRef, sourceFile: File): File

    fun applyStamp(
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Any? = null // Platform specific Bitmap (e.g. android.graphics.Bitmap)
    )

    suspend fun exportVideoStamp(
        file: File,
        stamp: CaptureStamp,
        tileBitmap: Any? = null // Platform specific Bitmap (e.g. android.graphics.Bitmap)
    )
}

