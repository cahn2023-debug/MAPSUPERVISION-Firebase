package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ReportDraft
import kotlinx.coroutines.flow.Flow

interface ReportDraftRepository {
    suspend fun add(draft: ReportDraft): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<ReportDraft>>
    fun observeByProject(projectId: String): Flow<List<ReportDraft>>
    suspend fun delete(id: String): AppResult<Unit>
}
