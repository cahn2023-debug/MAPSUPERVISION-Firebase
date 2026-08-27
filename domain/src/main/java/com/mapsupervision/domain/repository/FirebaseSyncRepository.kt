package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ProjectDeletionState

data class SyncEnvelope<T>(
    val id: String,
    val projectId: String,
    val tableName: String,
    val data: T,
    val updatedAtEpochMs: Long,
    val isDeleted: Boolean,
    val sourceDeviceId: String,
    val lastSyncedAtEpochMs: Long
)

data class SyncBatchResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val uploadedMedia: Int = 0,
    val failed: Int = 0
)

data class MediaRestoreResult(
    val requestedCount: Int = 0,
    val restoredCount: Int = 0,
    val failedCount: Int = 0,
    val failedPhotoIds: List<String> = emptyList()
)

interface FirebaseSyncRepository {
    suspend fun pushPending(projectId: String): AppResult<SyncBatchResult>
    suspend fun pullChanges(projectId: String, sinceEpochMs: Long? = null): AppResult<SyncBatchResult>
    suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult>
    suspend fun restoreMissingMedia(
        projectId: String,
        photoIds: List<String> = emptyList()
    ): AppResult<MediaRestoreResult> =
        AppResult.Error(UnsupportedOperationException("Media restore is not supported"))

    suspend fun requestProjectDeletion(
        projectId: String,
        requestId: String,
        typedIdentity: String,
        pendingOutboxCount: Int,
        confirmPendingOutbox: Boolean
    ): AppResult<ProjectDeletionState> =
        AppResult.Error(UnsupportedOperationException("Project deletion is not supported"))

    suspend fun decideProjectCloudDeletion(
        projectId: String,
        requestId: String,
        decision: String,
        typedIdentity: String
    ): AppResult<ProjectDeletionState> =
        AppResult.Error(UnsupportedOperationException("Cloud deletion decision is not supported"))
}
