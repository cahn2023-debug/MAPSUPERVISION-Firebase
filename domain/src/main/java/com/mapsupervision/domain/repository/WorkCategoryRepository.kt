package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.WorkCategory
import kotlinx.coroutines.flow.Flow

interface WorkCategoryRepository {
    suspend fun add(category: WorkCategory): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<WorkCategory>>
    fun observeByProject(projectId: String): Flow<List<WorkCategory>>
}
