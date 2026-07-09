package com.mapsupervision.app.workspace

import com.mapsupervision.domain.repository.*
import com.mapsupervision.domain.usecase.ObserveWorkspaceSnapshotUseCase
import com.mapsupervision.domain.service.ProjectStorageMigrationService
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.data.sync.DomainEventOutboxWriter
import javax.inject.Inject

class WorkspaceUseCases @Inject constructor(
    val activeProjectRepository: ActiveProjectRepository,
    val projectRepository: ProjectRepository,
    val projectSyncRepository: ProjectSyncRepository,
    val gisRepository: GisRepository,
    val observeWorkspaceSnapshot: ObserveWorkspaceSnapshotUseCase,
    val migrationService: ProjectStorageMigrationService,
    val domainEventBus: DomainEventBus,
    val domainEventOutboxWriter: DomainEventOutboxWriter,
    val firebaseSyncRepository: FirebaseSyncRepository,
    val contractorColorPreferenceRepository: ContractorColorPreferenceRepository,
    val firebaseAccessRepository: FirebaseAccessRepository
)

class ImportUseCases @Inject constructor(
    val importService: ImportRepository,
    val importedFileRepository: ImportedFileRepository,
    val importLifecycleRepository: ImportLifecycleRepository
)

class ContentUseCases @Inject constructor(
    val progressRepository: ProgressRepository,
    val workVolumeProgressRepository: WorkVolumeProgressRepository,
    val photoRepository: PhotoRepository,
    val photoPipelineService: IPhotoPipelineService,
    val locationProvider: IPhotoLocationProvider,
    val dailyLogRepository: DailyLogRepository,
    val noteRepository: NoteRepository,
    val taskRepository: TaskRepository,
    val workCategoryRepository: WorkCategoryRepository,
    val workPlanRepository: WorkPlanRepository,
    val weatherService: WeatherService,
    val reportDraftRepository: ReportDraftRepository,
    val materialDeclarationRepository: MaterialDeclarationRepository,
    val materialHandoverRepository: MaterialHandoverRepository
)
