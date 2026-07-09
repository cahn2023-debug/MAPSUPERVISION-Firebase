package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.WorkspacePlanningSnapshot
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkPlanRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveWorkspacePlanningSnapshotUseCase @Inject constructor(
    private val materialHandoverRepository: MaterialHandoverRepository,
    private val materialDeclarationRepository: MaterialDeclarationRepository,
    private val workPlanRepository: WorkPlanRepository,
    private val taskRepository: TaskRepository
) {
    operator fun invoke(projectId: String): Flow<WorkspacePlanningSnapshot> {
        return combine(
            materialHandoverRepository.observeByProject(projectId),
            materialDeclarationRepository.observeByProject(projectId),
            workPlanRepository.observeByProject(projectId),
            taskRepository.observeByProject(projectId)
        ) { handovers, declarations, workPlans, projectTasks ->
            WorkspacePlanningSnapshot(
                projectId = projectId,
                materialHandovers = handovers,
                materialDeclarations = declarations,
                workPlans = workPlans,
                projectTasks = projectTasks
            )
        }.distinctUntilChanged()
    }
}
