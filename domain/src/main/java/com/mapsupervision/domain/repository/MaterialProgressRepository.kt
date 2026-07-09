package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.WorkVolumeProgress
import kotlinx.coroutines.flow.Flow

interface WorkVolumeProgressRepository {
    suspend fun upsert(progress: WorkVolumeProgress): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<WorkVolumeProgress>>
    fun observeByProject(projectId: String): Flow<List<WorkVolumeProgress>>
}

typealias MaterialProgressRepository = WorkVolumeProgressRepository
