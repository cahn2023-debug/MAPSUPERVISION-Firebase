package com.mapsupervision.storage

import com.mapsupervision.domain.repository.ProjectSyncEvent
import com.mapsupervision.domain.repository.ProjectSyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class ProjectSyncRepositoryImpl @Inject constructor() : ProjectSyncRepository {
    private val _events = MutableSharedFlow<ProjectSyncEvent>(
        replay = 0,
        extraBufferCapacity = 32
    )

    override val events: SharedFlow<ProjectSyncEvent> = _events.asSharedFlow()

    override suspend fun notifyProjectChanged(projectId: String?, reason: String) {
        _events.emit(
            ProjectSyncEvent(
                projectId = projectId,
                reason = reason,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }
}
