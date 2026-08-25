package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.MediaStatusTag
import kotlinx.coroutines.flow.Flow

interface MediaStatusTagRepository {
    suspend fun add(tag: MediaStatusTag): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<MediaStatusTag>>
    fun observeByProject(projectId: String): Flow<List<MediaStatusTag>>
}
