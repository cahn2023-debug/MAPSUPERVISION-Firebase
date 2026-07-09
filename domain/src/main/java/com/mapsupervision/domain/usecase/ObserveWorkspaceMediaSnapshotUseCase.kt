package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.WorkspaceMediaSnapshot
import com.mapsupervision.domain.repository.PhotoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveWorkspaceMediaSnapshotUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    operator fun invoke(projectId: String): Flow<WorkspaceMediaSnapshot> {
        return photoRepository.observeByProject(projectId)
            .map { photos -> WorkspaceMediaSnapshot(projectId = projectId, sitePhotos = photos) }
            .distinctUntilChanged()
    }
}
