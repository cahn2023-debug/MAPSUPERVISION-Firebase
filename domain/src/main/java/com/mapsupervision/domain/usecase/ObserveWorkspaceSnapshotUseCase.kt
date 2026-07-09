package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.WorkspaceSnapshot
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveWorkspaceSnapshotUseCase @Inject constructor(
    private val observeWorkspaceGeometrySnapshot: ObserveWorkspaceGeometrySnapshotUseCase,
    private val observeWorkspaceProgressSnapshot: ObserveWorkspaceProgressSnapshotUseCase,
    private val observeWorkspaceMediaSnapshot: ObserveWorkspaceMediaSnapshotUseCase,
    private val observeWorkspacePlanningSnapshot: ObserveWorkspacePlanningSnapshotUseCase
) {
    constructor(
        importedFileRepository: ImportedFileRepository,
        gisRepository: GisRepository,
        progressRepository: ProgressRepository,
        workVolumeProgressRepository: WorkVolumeProgressRepository,
        dailyLogRepository: DailyLogRepository,
        workCategoryRepository: WorkCategoryRepository,
        photoRepository: PhotoRepository,
        materialHandoverRepository: MaterialHandoverRepository,
        materialDeclarationRepository: MaterialDeclarationRepository,
        workPlanRepository: com.mapsupervision.domain.repository.WorkPlanRepository,
        taskRepository: TaskRepository
    ) : this(
        observeWorkspaceGeometrySnapshot = ObserveWorkspaceGeometrySnapshotUseCase(
            importedFileRepository = importedFileRepository,
            gisRepository = gisRepository,
            progressRepository = progressRepository
        ),
        observeWorkspaceProgressSnapshot = ObserveWorkspaceProgressSnapshotUseCase(
            workVolumeProgressRepository = workVolumeProgressRepository,
            dailyLogRepository = dailyLogRepository,
            workCategoryRepository = workCategoryRepository
        ),
        observeWorkspaceMediaSnapshot = ObserveWorkspaceMediaSnapshotUseCase(
            photoRepository = photoRepository
        ),
        observeWorkspacePlanningSnapshot = ObserveWorkspacePlanningSnapshotUseCase(
            materialHandoverRepository = materialHandoverRepository,
            materialDeclarationRepository = materialDeclarationRepository,
            workPlanRepository = workPlanRepository,
            taskRepository = taskRepository
        )
    )

    operator fun invoke(projectId: String): Flow<WorkspaceSnapshot> {
        return combine(
            observeWorkspaceGeometrySnapshot(projectId),
            observeWorkspaceProgressSnapshot(projectId),
            observeWorkspaceMediaSnapshot(projectId),
            observeWorkspacePlanningSnapshot(projectId)
        ) { geometry, progress, media, planning ->
            WorkspaceSnapshot(
                projectId = projectId,
                importedFiles = geometry.importedFiles,
                designNodes = geometry.designNodes,
                designRoutes = geometry.designRoutes,
                constructionProgress = geometry.constructionProgress,
                workVolumeRows = progress.workVolumeRows,
                dailyLogs = progress.dailyLogs,
                workCategories = progress.workCategories,
                sitePhotos = media.sitePhotos,
                materialHandovers = planning.materialHandovers,
                materialDeclarations = planning.materialDeclarations,
                workPlans = planning.workPlans,
                projectTasks = planning.projectTasks
            )
        }.distinctUntilChanged()
    }
}

