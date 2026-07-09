package com.mapsupervision.domain.repository

import kotlinx.coroutines.flow.SharedFlow

data class ProjectSyncEvent(
    val projectId: String?,
    val reason: String,
    val updatedAtEpochMs: Long
)

interface ProjectSyncRepository {
    val events: SharedFlow<ProjectSyncEvent>

    suspend fun notifyProjectChanged(projectId: String?, reason: String)
}
