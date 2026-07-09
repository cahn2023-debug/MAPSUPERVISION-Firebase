package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ChatHistoryMessage
import kotlinx.coroutines.flow.Flow

interface ChatHistoryRepository {
    suspend fun append(message: ChatHistoryMessage): AppResult<Unit>
    suspend fun listByProject(projectId: String): AppResult<List<ChatHistoryMessage>>
    suspend fun listRecentByProject(projectId: String, limit: Int): AppResult<List<ChatHistoryMessage>>
    fun observeByProject(projectId: String): Flow<List<ChatHistoryMessage>>
    suspend fun clearByProject(projectId: String): AppResult<Unit>
}
