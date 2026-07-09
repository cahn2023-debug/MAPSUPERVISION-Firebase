package com.mapsupervision.domain.usecase

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ReportWorkspaceSnapshot
import com.mapsupervision.domain.repository.ProjectRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class GenerateReportUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val observeWorkspaceSnapshotUseCase: ObserveWorkspaceSnapshotUseCase
) {
    suspend operator fun invoke(projectId: String): ReportWorkspaceSnapshot? = withContext(Dispatchers.IO) {
        val project = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
            .firstOrNull { it.id == projectId }
            ?: return@withContext null
        val workspace = observeWorkspaceSnapshotUseCase(projectId).firstOrNull() ?: return@withContext null
        ReportWorkspaceSnapshot(
            projectId = projectId,
            projectName = project.name,
            projectSlug = project.slug,
            workspace = workspace
        )
    }
}
