package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.SitePhoto
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    suspend fun add(photo: SitePhoto): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<SitePhoto>>
    suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>>
    fun observeByProject(projectId: String): Flow<List<SitePhoto>>
}
