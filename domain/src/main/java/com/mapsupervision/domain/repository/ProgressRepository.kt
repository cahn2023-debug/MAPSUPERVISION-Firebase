package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.NodeProgress
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    suspend fun upsert(progress: NodeProgress): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<NodeProgress>>
    fun observeByProject(projectId: String): Flow<List<NodeProgress>>
}
