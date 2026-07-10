package com.mapsupervision.app.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.app.sync.FirebaseMediaUploadScheduler
import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.AiDecision
import com.mapsupervision.ai.core.AiDecisionSource
import com.mapsupervision.ai.core.AiPayload
import com.mapsupervision.ai.core.AiResult
import com.mapsupervision.ai.core.OpsRecommendationPayload
import com.mapsupervision.ai.core.OpsRecommendationResult
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportDraft
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.NonExcelFieldPreview
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.model.ProjectStorageRef
import com.mapsupervision.domain.model.ReportDraft
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncEvent
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.repository.ReportDraftRepository
import com.mapsupervision.domain.repository.SyncBatchResult
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.WorkPlanRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.ProjectStorageMigrationService
import com.mapsupervision.domain.service.ProjectStorageMigrationStatus
import com.mapsupervision.domain.service.WeatherData
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.domain.usecase.ObserveWorkspaceSnapshotUseCase
import com.mapsupervision.storage.ProjectStorageManager
import com.mapsupervision.storage.importer.UserFileImportService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkspaceViewModelFirebaseSyncTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun manual_sync_updates_state_and_skips_parallel_run() = runBlocking {
        val firebaseRepo = FakeFirebaseSyncRepository(
            pushGate = CompletableDeferred(),
            pushResult = SyncBatchResult(pushed = 2, uploadedMedia = 1),
            pullResult = SyncBatchResult(pulled = 3)
        )
        val projectSyncRepository = FakeProjectSyncRepository()
        val mediaUploadScheduler = FakeFirebaseMediaUploadScheduler()
        val viewModel = buildViewModel(firebaseRepo, projectSyncRepository, mediaUploadScheduler)

        viewModel.syncFirebaseNow(projectId = "project-1", trigger = "manual")
        waitUntil { firebaseRepo.pushCalls == 1 }

        viewModel.syncFirebaseNow(projectId = "project-1", trigger = "manual")
        assertEquals(1, firebaseRepo.pushCalls)

        firebaseRepo.pushGate?.complete(Unit)
        waitUntil { firebaseRepo.pullCalls == 1 && viewModel.state.value.firebaseSync.lastTrigger == "manual" }

        val syncState = viewModel.state.value.firebaseSync
        assertEquals(false, syncState.isSyncing)
        assertEquals("manual", syncState.lastTrigger)
        assertEquals(2, syncState.pushed)
        assertEquals(3, syncState.pulled)
        assertEquals(1, syncState.uploadedMedia)
        assertEquals(null, syncState.lastError)
    }

    @Test
    fun photo_saved_event_triggers_single_sync_while_previous_job_is_running() = runBlocking {
        val firebaseRepo = FakeFirebaseSyncRepository(
            pushGate = CompletableDeferred(),
            pushResult = SyncBatchResult(pushed = 1, uploadedMedia = 1),
            pullResult = SyncBatchResult(pulled = 1)
        )
        val projectSyncRepository = FakeProjectSyncRepository()
        val mediaUploadScheduler = FakeFirebaseMediaUploadScheduler()
        val viewModel = buildViewModel(firebaseRepo, projectSyncRepository, mediaUploadScheduler)

        viewModel._state.value = viewModel.state.value.copy(activeProjectId = "project-1")
        val baselineScheduled = mediaUploadScheduler.reasons.size
        projectSyncRepository.notifyProjectChanged("project-1", "photo_saved")
        delay(300)
        projectSyncRepository.notifyProjectChanged("project-1", "photo_saved")
        delay(300)
        waitUntil { firebaseRepo.pushCalls == 1 }

        assertEquals(1, firebaseRepo.pushCalls)
        assertEquals("photo_saved", viewModel.state.value.firebaseSync.lastTrigger)
        assertEquals(listOf("photo_saved", "photo_saved"), mediaUploadScheduler.reasons.drop(baselineScheduled))
        assertEquals(listOf("project-1", "project-1"), mediaUploadScheduler.projectIds.drop(baselineScheduled))

        firebaseRepo.pushGate?.complete(Unit)
        waitUntil { firebaseRepo.pullCalls == 1 && !viewModel.state.value.firebaseSync.isSyncing }

        assertEquals(false, viewModel.state.value.firebaseSync.isSyncing)
        assertEquals(1, viewModel.state.value.firebaseSync.pulled)
    }

    private fun buildViewModel(
        firebaseSyncRepository: FakeFirebaseSyncRepository,
        projectSyncRepository: FakeProjectSyncRepository,
        firebaseMediaUploadScheduler: FirebaseMediaUploadScheduler = FakeFirebaseMediaUploadScheduler()
    ): WorkspaceViewModel {
        val activeProjectRepository = FakeActiveProjectRepository("project-1")
        val importedFileRepository = FakeImportedFileRepository()
        val progressRepository = FakeProgressRepository()
        val workVolumeRepository = FakeWorkVolumeProgressRepository()
        val projectRepository = FakeProjectRepository()
        val gisRepository = FakeGisRepository()
        val photoRepository = FakePhotoRepository()
        val dailyLogRepository = FakeDailyLogRepository()
        val noteRepository = FakeNoteRepository()
        val taskRepository = FakeTaskRepository()
        val workCategoryRepository = FakeWorkCategoryRepository()
        val workPlanRepository = FakeWorkPlanRepository()
        val materialDeclarationRepository = FakeMaterialDeclarationRepository()
        val materialHandoverRepository = FakeMaterialHandoverRepository()
        val observeWorkspaceSnapshot = ObserveWorkspaceSnapshotUseCase(
            importedFileRepository = importedFileRepository,
            gisRepository = gisRepository,
            progressRepository = progressRepository,
            workVolumeProgressRepository = workVolumeRepository,
            dailyLogRepository = dailyLogRepository,
            workCategoryRepository = workCategoryRepository,
            photoRepository = photoRepository,
            materialHandoverRepository = materialHandoverRepository,
            materialDeclarationRepository = materialDeclarationRepository,
            workPlanRepository = workPlanRepository,
            taskRepository = taskRepository
        )

        return WorkspaceViewModel(
            context = context,
            activeProjectRepository = activeProjectRepository,
            importedFileRepository = importedFileRepository,
            progressRepository = progressRepository,
            workVolumeProgressRepository = workVolumeRepository,
            projectRepository = projectRepository,
            projectSyncRepository = projectSyncRepository,
            gisRepository = gisRepository,
            importService = UserFileImportService(context, ProjectStorageManager(context)),
            aiOrchestrator = com.mapsupervision.domain.ai.AiOrchestrator(FakeAIFacade()),
            photoRepository = photoRepository,
            photoPipelineService = FakePhotoPipelineService(context.cacheDir),
            locationProvider = FakePhotoLocationProvider(),
            dailyLogRepository = dailyLogRepository,
            noteRepository = noteRepository,
            taskRepository = taskRepository,
            workCategoryRepository = workCategoryRepository,
            workPlanRepository = workPlanRepository,
            weatherService = FakeWeatherService(),
            reportDraftRepository = FakeReportDraftRepository(),
            materialDeclarationRepository = materialDeclarationRepository,
            materialHandoverRepository = materialHandoverRepository,
            firebaseAccessRepository = FakeFirebaseAccessRepository(),
            firebaseSyncRepository = firebaseSyncRepository,
            firebaseMediaUploadScheduler = firebaseMediaUploadScheduler,
            observeWorkspaceSnapshot = observeWorkspaceSnapshot,
            migrationService = FakeProjectStorageMigrationService()
        )
    }
}

private suspend fun waitUntil(condition: () -> Boolean) {
    withTimeout(5_000) {
        while (!condition()) {
            delay(10)
        }
    }
}

private class FakeFirebaseAccessRepository : FirebaseAccessRepository {
    private val session = FirebaseUserSession(
        uid = "user-1",
        email = "sync-test@example.com",
        emailVerified = true,
        isAdmin = false
    )
    private val projectAccess = ProjectAccess(projectId = "project-1")
    override val accessState: StateFlow<FirebaseAccessState> = MutableStateFlow(
        FirebaseAccessState(
            session = session,
            allowedProjectIds = setOf(projectAccess.projectId),
            permissionsByProject = mapOf(projectAccess.projectId to projectAccess)
        )
    )

    override suspend fun signIn(email: String, password: String): AppResult<FirebaseUserSession> = AppResult.Success(session)
    override suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession> = AppResult.Success(session)
    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun refreshAccess(): AppResult<FirebaseAccessState> = AppResult.Success(accessState.value)
    override suspend fun ensureUserProfile(): AppResult<Unit> = AppResult.Success(Unit)
    override fun projectAccess(projectId: String): ProjectAccess? = accessState.value.permissionsByProject[projectId]
}

private class FakeFirebaseSyncRepository(
    val pushGate: CompletableDeferred<Unit>? = null,
    private val pushResult: SyncBatchResult = SyncBatchResult(),
    private val pullResult: SyncBatchResult = SyncBatchResult()
) : FirebaseSyncRepository {
    var pushCalls = 0
    var pullCalls = 0

    override suspend fun pushPending(projectId: String): AppResult<SyncBatchResult> {
        pushCalls += 1
        pushGate?.await()
        return AppResult.Success(pushResult)
    }

    override suspend fun pullChanges(projectId: String, sinceEpochMs: Long?): AppResult<SyncBatchResult> {
        pullCalls += 1
        return AppResult.Success(pullResult)
    }

    override suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult> {
        return AppResult.Success(SyncBatchResult(uploadedMedia = pushResult.uploadedMedia))
    }
}

private class FakeProjectSyncRepository : ProjectSyncRepository {
    private val eventFlow = MutableSharedFlow<ProjectSyncEvent>()
    override val events: SharedFlow<ProjectSyncEvent> = eventFlow

    override suspend fun notifyProjectChanged(projectId: String?, reason: String) {
        eventFlow.emit(ProjectSyncEvent(projectId, reason, System.currentTimeMillis()))
    }
}

private class FakeActiveProjectRepository(
    initialProjectId: String?
) : ActiveProjectRepository {
    private val state = MutableStateFlow(initialProjectId)
    override val activeProjectId: StateFlow<String?> = state

    override suspend fun setActive(projectId: String): AppResult<Unit> {
        state.value = projectId
        return AppResult.Success(Unit)
    }

    override suspend fun getActive(): AppResult<String?> = AppResult.Success(state.value)
}

private class FakeProjectRepository : ProjectRepository {
    override suspend fun create(name: String, customPath: String?): AppResult<Project> = AppResult.Success(sampleProject())
    override suspend fun list(includeArchived: Boolean): AppResult<List<Project>> = AppResult.Success(listOf(sampleProject()))
    override suspend fun clone(projectId: String, newName: String): AppResult<Project> = AppResult.Success(sampleProject())
    override suspend fun archive(projectId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun importProject(project: Project): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun clearProject(projectId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun touch(projectId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun updateStoragePath(projectId: String, newPath: String): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeImportedFileRepository : ImportedFileRepository {
    override suspend fun upsert(file: ImportedFile): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<ImportedFile>> = AppResult.Success(emptyList())
    override suspend fun deleteById(id: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun observeByProject(projectId: String): Flow<List<ImportedFile>> = flowOf(emptyList())
}

private class FakeProgressRepository : ProgressRepository {
    override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<NodeProgress>> = flowOf(emptyList())
}

private class FakeWorkVolumeProgressRepository : WorkVolumeProgressRepository {
    override suspend fun upsert(progress: WorkVolumeProgress): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<WorkVolumeProgress>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<WorkVolumeProgress>> = flowOf(emptyList())
}

private class FakeDailyLogRepository : DailyLogRepository {
    override suspend fun add(log: DailyLog): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<DailyLog>> = flowOf(emptyList())
}

private class FakePhotoRepository : PhotoRepository {
    override suspend fun add(photo: SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
    override suspend fun listProjectsWithPendingUploads(): AppResult<List<String>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flowOf(emptyList())
}

private class FakeFirebaseMediaUploadScheduler : FirebaseMediaUploadScheduler {
    val reasons = mutableListOf<String>()
    val projectIds = mutableListOf<String?>()

    override fun enqueue(reason: String, projectId: String?) {
        reasons += reason
        projectIds += projectId
    }
}

private class FakeNoteRepository : NoteRepository {
    override suspend fun add(note: com.mapsupervision.domain.model.Note): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(noteId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<com.mapsupervision.domain.model.Note>> = AppResult.Success(emptyList())
    override suspend fun byProject(projectId: String): AppResult<List<com.mapsupervision.domain.model.Note>> = AppResult.Success(emptyList())
}

private class FakeTaskRepository : TaskRepository {
    override suspend fun upsert(task: Task): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(taskId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>> = AppResult.Success(emptyList())
    override suspend fun byProject(projectId: String): AppResult<List<Task>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<Task>> = flowOf(emptyList())
}

private class FakeWorkCategoryRepository : WorkCategoryRepository {
    override suspend fun add(category: WorkCategory): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<WorkCategory>> = flowOf(emptyList())
}

private class FakeWorkPlanRepository : WorkPlanRepository {
    override suspend fun add(workPlan: WorkPlan): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<WorkPlan>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<WorkPlan>> = flowOf(emptyList())
}

private class FakeMaterialDeclarationRepository : MaterialDeclarationRepository {
    override suspend fun add(declaration: MaterialDeclaration): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(declaration: MaterialDeclaration): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun getByProject(projectId: String): AppResult<List<MaterialDeclaration>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<MaterialDeclaration>> = flowOf(emptyList())
}

private class FakeMaterialHandoverRepository : MaterialHandoverRepository {
    override suspend fun add(handover: MaterialHandover): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(handover: MaterialHandover): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<MaterialHandover>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<MaterialHandover>> = flowOf(emptyList())
}

private class FakeGisRepository : GisRepository {
    override suspend fun upsertNode(node: GisNode): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<GisNode>, routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = AppResult.Success(emptyList())
    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = AppResult.Success(emptyList())
    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = AppResult.Success(null)
    override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = flowOf(emptyList())
    override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = flowOf(emptyList())
}

private class FakeReportDraftRepository : ReportDraftRepository {
    override suspend fun add(draft: ReportDraft): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<ReportDraft>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<ReportDraft>> = flowOf(emptyList())
    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeWeatherService : WeatherService {
    override suspend fun fetchWeather(latitude: Double, longitude: Double): AppResult<WeatherData> =
        AppResult.Success(WeatherData(condition = "", temperature = 0.0))
}

private class FakeProjectStorageMigrationService : ProjectStorageMigrationService {
    override suspend fun migrateProjectIfNeeded(project: Project): ProjectStorageMigrationStatus {
        return ProjectStorageMigrationStatus(project.id, migrated = false, verified = false, verificationMessage = "")
    }
}

private class FakePhotoLocationProvider : IPhotoLocationProvider {
    override suspend fun lastKnownLocation(): PhotoLocationSnapshot {
        return PhotoLocationSnapshot(0.0, 0.0, 0f, false, PhotoLocationStatus.MISSING)
    }
}

private class FakePhotoPipelineService(
    private val root: File
) : IPhotoPipelineService {
    override fun createCaptureOutputFile(storageRef: ProjectStorageRef, capturedAt: Long, locationLabel: String?, note: String?, folderType: CaptureFolderType, objectCode: String): File {
        return File(root, "capture.jpg")
    }

    override fun createCaptureVideoOutputFile(storageRef: ProjectStorageRef, capturedAt: Long, locationLabel: String?, note: String?, folderType: CaptureFolderType, objectCode: String): File {
        return File(root, "capture.mp4")
    }

    override fun importFromGallery(storageRef: ProjectStorageRef, capturedAt: Long, locationLabel: String?, note: String?, folderType: CaptureFolderType, objectCode: String, sourceUri: String): File {
        return File(root, "gallery.jpg")
    }

    override fun createThumbnail(storageRef: ProjectStorageRef, sourceFile: File): File {
        return File(root, "thumb.jpg")
    }

    override fun applyStamp(file: File, stamp: CaptureStamp, ratio: CameraAspectRatio, tileBitmap: Any?) = Unit

    override suspend fun exportVideoStamp(file: File, stamp: CaptureStamp, tileBitmap: Any?) = Unit
}

private class FakeAIFacade : AIFacade {
    override suspend fun <T : AiResult> execute(payload: AiPayload): AiDecision<T> {
        val result = when (payload) {
            is OpsRecommendationPayload -> OpsRecommendationResult(
                prioritizedActions = emptyList(),
                priority = 0
            )
            else -> OpsRecommendationResult(emptyList(), 0)
        }
        @Suppress("UNCHECKED_CAST")
        return AiDecision(
            capability = payload.capability,
            result = result as T,
            confidence = 100,
            source = AiDecisionSource.RULE_BASED,
            reason = "test"
        )
    }
}

private fun sampleProject(): Project = Project(
    id = "project-1",
    name = "Project 1",
    slug = "project-1",
    isArchived = false,
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 1L,
    storageMode = ProjectStorageMode.LEGACY_SHARED
)
