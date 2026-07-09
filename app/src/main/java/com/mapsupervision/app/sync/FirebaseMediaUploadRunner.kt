package com.mapsupervision.app.sync

import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.PhotoRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FirebaseMediaUploadRunOutcome {
    data class Success(
        val projectCount: Int,
        val uploadedMedia: Int,
        val failedMedia: Int
    ) : FirebaseMediaUploadRunOutcome

    data class Retry(
        val projectCount: Int,
        val uploadedMedia: Int,
        val failedMedia: Int,
        val reason: String
    ) : FirebaseMediaUploadRunOutcome

    data class Failure(
        val projectCount: Int,
        val uploadedMedia: Int,
        val failedMedia: Int,
        val reason: String
    ) : FirebaseMediaUploadRunOutcome
}

@Singleton
open class FirebaseMediaUploadRunner @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val firebaseSyncRepository: FirebaseSyncRepository
) {
    open suspend fun run(): FirebaseMediaUploadRunOutcome {
        val pendingProjects = when (val result = photoRepository.listProjectsWithPendingUploads()) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                val reason = result.throwable.message ?: "Failed to list pending upload projects."
                return classifyFailure(
                    reason = reason,
                    projectCount = 0,
                    uploadedMedia = 0,
                    failedMedia = 0
                )
            }
        }

        if (pendingProjects.isEmpty()) {
            return FirebaseMediaUploadRunOutcome.Success(
                projectCount = 0,
                uploadedMedia = 0,
                failedMedia = 0
            )
        }

        var uploadedMedia = 0
        var failedMedia = 0
        var firstTemporaryReason: String? = null
        var firstPermanentReason: String? = null

        pendingProjects.forEach { projectId ->
            when (val result = firebaseSyncRepository.uploadPendingMedia(projectId)) {
                is AppResult.Success -> {
                    uploadedMedia += result.data.uploadedMedia
                    failedMedia += result.data.failed
                    if (result.data.failed > 0 && firstTemporaryReason == null) {
                        firstTemporaryReason = "Some uploads still failed for project $projectId."
                    }
                }

                is AppResult.Error -> {
                    val reason = result.throwable.message ?: "Upload pending media failed for $projectId."
                    AppLogger.e(result.throwable, "firebase.media_auto_upload.failed projectId=$projectId")
                    if (isPermanentFailure(reason)) {
                        if (firstPermanentReason == null) firstPermanentReason = reason
                    } else if (firstTemporaryReason == null) {
                        firstTemporaryReason = reason
                    }
                }
            }
        }

        val projectCount = pendingProjects.size
        return when {
            firstTemporaryReason != null -> FirebaseMediaUploadRunOutcome.Retry(
                projectCount = projectCount,
                uploadedMedia = uploadedMedia,
                failedMedia = failedMedia,
                reason = firstTemporaryReason!!
            )

            firstPermanentReason != null -> FirebaseMediaUploadRunOutcome.Failure(
                projectCount = projectCount,
                uploadedMedia = uploadedMedia,
                failedMedia = failedMedia,
                reason = firstPermanentReason!!
            )

            else -> FirebaseMediaUploadRunOutcome.Success(
                projectCount = projectCount,
                uploadedMedia = uploadedMedia,
                failedMedia = failedMedia
            )
        }
    }

    private fun classifyFailure(
        reason: String,
        projectCount: Int,
        uploadedMedia: Int,
        failedMedia: Int
    ): FirebaseMediaUploadRunOutcome = if (isPermanentFailure(reason)) {
        FirebaseMediaUploadRunOutcome.Failure(projectCount, uploadedMedia, failedMedia, reason)
    } else {
        FirebaseMediaUploadRunOutcome.Retry(projectCount, uploadedMedia, failedMedia, reason)
    }

    internal fun isPermanentFailure(reason: String): Boolean {
        val normalized = reason.lowercase()
        return "missing" in normalized ||
            "incomplete" in normalized ||
            "not configured" in normalized ||
            "permission" in normalized ||
            "not signed in" in normalized ||
            "id token" in normalized ||
            "firebase config" in normalized ||
            "google_service_account" in normalized ||
            "google_drive_root_folder_id" in normalized
    }
}
