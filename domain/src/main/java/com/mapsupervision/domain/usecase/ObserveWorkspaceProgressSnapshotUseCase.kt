package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.WorkspaceProgressSnapshot
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveWorkspaceProgressSnapshotUseCase @Inject constructor(
    private val workVolumeProgressRepository: WorkVolumeProgressRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val workCategoryRepository: WorkCategoryRepository
) {
    operator fun invoke(projectId: String): Flow<WorkspaceProgressSnapshot> {
        return combine(
            workVolumeProgressRepository.observeByProject(projectId),
            dailyLogRepository.observeByProject(projectId),
            workCategoryRepository.observeByProject(projectId)
        ) { workVolumeRows, dailyLogs, workCategories ->
            WorkspaceProgressSnapshot(
                projectId = projectId,
                workVolumeRows = workVolumeRows,
                dailyLogs = dailyLogs,
                workCategories = workCategories
            )
        }.distinctUntilChanged()
    }
}
