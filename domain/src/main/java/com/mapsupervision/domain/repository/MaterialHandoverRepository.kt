package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.MaterialHandover
import kotlinx.coroutines.flow.Flow

interface MaterialHandoverRepository {
    suspend fun add(handover: MaterialHandover): AppResult<Unit>
    suspend fun delete(handover: MaterialHandover): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<MaterialHandover>>
    fun observeByProject(projectId: String): Flow<List<MaterialHandover>>
}
