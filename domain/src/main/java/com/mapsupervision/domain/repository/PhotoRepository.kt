package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.SitePhoto
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    suspend fun add(photo: SitePhoto): AppResult<Unit>
    suspend fun delete(photo: SitePhoto): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Photo deletion is not supported"))
    suspend fun updateStatusTag(photo: SitePhoto, statusTag: String?): AppResult<SitePhoto> =
        AppResult.Success(photo.copy(statusTag = statusTag?.trim()?.takeIf { it.isNotEmpty() }))
    suspend fun byProject(projectId: String): AppResult<List<SitePhoto>>
    suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>>
    suspend fun listProjectsWithPendingUploads(): AppResult<List<String>>
    fun observeByProject(projectId: String): Flow<List<SitePhoto>>
}
