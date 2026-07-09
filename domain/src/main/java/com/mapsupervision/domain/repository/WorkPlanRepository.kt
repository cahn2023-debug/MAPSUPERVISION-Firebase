package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.WorkPlan
import kotlinx.coroutines.flow.Flow

interface WorkPlanRepository {
    suspend fun add(workPlan: WorkPlan): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<WorkPlan>>
    fun observeByProject(projectId: String): Flow<List<WorkPlan>>
}
