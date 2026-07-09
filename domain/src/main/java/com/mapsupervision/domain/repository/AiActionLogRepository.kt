package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.AiActionLog
import kotlinx.coroutines.flow.Flow

interface AiActionLogRepository {
    suspend fun log(action: AiActionLog): AppResult<Unit>
    suspend fun listByProject(projectId: String): AppResult<List<AiActionLog>>
    suspend fun getById(id: String): AppResult<AiActionLog?>
    fun observeByProject(projectId: String): Flow<List<AiActionLog>>
}
