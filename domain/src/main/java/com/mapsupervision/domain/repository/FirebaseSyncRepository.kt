package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult

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

interface FirebaseSyncRepository {
    suspend fun pushPending(projectId: String): AppResult<SyncBatchResult>
    suspend fun pullChanges(projectId: String, sinceEpochMs: Long? = null): AppResult<SyncBatchResult>
    suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult>
}
