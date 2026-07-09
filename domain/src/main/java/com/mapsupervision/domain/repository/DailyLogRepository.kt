package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.DailyLog
import kotlinx.coroutines.flow.Flow

interface DailyLogRepository {
    suspend fun add(log: DailyLog): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<DailyLog>>
    fun observeByProject(projectId: String): Flow<List<DailyLog>>
}
