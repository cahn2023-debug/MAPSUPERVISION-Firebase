package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.WorkspaceGeometrySnapshot
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.ProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveWorkspaceGeometrySnapshotUseCase @Inject constructor(
    private val importedFileRepository: ImportedFileRepository,
    private val gisRepository: GisRepository,
    private val progressRepository: ProgressRepository
) {
    operator fun invoke(projectId: String): Flow<WorkspaceGeometrySnapshot> {
        return combine(
            importedFileRepository.observeByProject(projectId),
            gisRepository.observeNodes(projectId, ""),
            gisRepository.observeRoutes(projectId, ""),
            progressRepository.observeByProject(projectId)
        ) { importedFiles, nodes, routes, progress ->
            WorkspaceGeometrySnapshot(
                projectId = projectId,
                importedFiles = importedFiles,
                designNodes = nodes,
                designRoutes = routes,
                constructionProgress = progress
            )
        }.distinctUntilChanged()
    }
}
