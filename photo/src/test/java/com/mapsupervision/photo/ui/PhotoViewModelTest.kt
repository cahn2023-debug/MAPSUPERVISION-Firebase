package com.mapsupervision.photo.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.PhotoQualityPayload
import com.mapsupervision.ai.core.PhotoQualityResult
import com.mapsupervision.ai.core.DiscrepancyCheckPayload
import com.mapsupervision.ai.core.DiscrepancyResult
import com.mapsupervision.ai.core.ImportMappingPayload
import com.mapsupervision.ai.core.ImportMappingResult
import com.mapsupervision.ai.core.OpsRecommendationPayload
import com.mapsupervision.ai.core.OpsRecommendationResult
import com.mapsupervision.ai.core.ReportDraftPayload
import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.ai.core.TimelineSummaryPayload
import com.mapsupervision.ai.core.TimelineSummaryResult
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncEvent
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.ai.core.repository.AiRepository
import com.mapsupervision.photo.location.PhotoLocationProvider
import com.mapsupervision.photo.worker.PhotoPipelineService
import com.mapsupervision.storage.ProjectStorageManager
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.PhotoDailyLogDataResult
import com.mapsupervision.domain.service.PhotoMaterialDataResult
import com.mapsupervision.domain.service.PhotoOcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
class PhotoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    private val photoRepository = FakePhotoRepository()
    private val activeProjectRepository = FakeActiveProjectRepository("proj-1")
    private val gisRepository = FakeGisRepository()
    private val projectRepository = FakeProjectRepository()
    private val projectSyncRepository = FakeProjectSyncRepository()
    private lateinit var locationProvider: PhotoLocationProvider
    private lateinit var photoPipelineService: PhotoPipelineService
    private lateinit var storageManager: FakeProjectStorageManager
    private lateinit var aiFacade: AIFacade

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        storageManager = FakeProjectStorageManager(context)
        photoPipelineService = object : PhotoPipelineService(context, storageManager, FakePhotoOcrService()) {
            override fun importFromGallery(
                storageRef: com.mapsupervision.domain.model.ProjectStorageRef,
                capturedAt: Long,
                locationLabel: String?,
                note: String?,
                folderType: CaptureFolderType,
                objectCode: String,
                sourceUri: String
            ): File {
                println("DEBUG: PhotoPipelineService.importFromGallery overridden called!")
                val file = File(context.cacheDir, "imported.mp4")
                file.writeText("video")
                return file
            }
        }
        locationProvider = object : PhotoLocationProvider(context) {
            override suspend fun lastKnownLocation(): PhotoLocationSnapshot {
                return PhotoLocationSnapshot(10.0, 100.0, 5f, false, com.mapsupervision.domain.model.PhotoLocationStatus.OK)
            }
        }
        aiFacade = FakeAiFacade()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testImportFromGallery_routesPhotoOrVideoCorrectly() = runTest(dispatcher) {
        val viewModel = PhotoViewModel(
            context = context,
            photoRepository = photoRepository,
            activeProjectRepository = activeProjectRepository,
            gisRepository = gisRepository,
            projectRepository = projectRepository,
            projectSyncRepository = projectSyncRepository,
            locationProvider = locationProvider,
            photoPipelineService = photoPipelineService,
            aiFacade = aiFacade,
            storageManager = storageManager
        )

        val mockUri = Uri.parse("content://media/external/video/media/1")
        val provider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
            override fun getType(uri: Uri): String {
                println("DEBUG: contentResolver.getType called, returning video/mp4")
                return "video/mp4"
            }
            override fun insert(uri: Uri, values: android.content.ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider)

        println("DEBUG: starting importFromGallery call")
        viewModel.importFromGallery(listOf(mockUri), "node-1", "Engineer")

        // Wait for background IO thread to complete the import
        val startTime = System.currentTimeMillis()
        while (storageManager.scannedFiles.isEmpty() && System.currentTimeMillis() - startTime < 3000) {
            advanceUntilIdle()
            Thread.sleep(50)
        }
        println("DEBUG: wait finished. Scanned files count: ${storageManager.scannedFiles.size}")
        if (storageManager.scannedFiles.isEmpty()) {
            println("DEBUG: photoRepository saved list size: ${photoRepository.saved.size}")
        }

        // Verify that storageManager.scanFile was called on the imported file
        val scanned = storageManager.scannedFiles.single()
        assertEquals("imported.mp4", scanned.name)

        // Verify video saved in photo repository with correct media type and thumbnailPath
        val saved = photoRepository.saved.single()
        assertEquals(com.mapsupervision.domain.model.MediaType.VIDEO, saved.mediaType)
        assertEquals(scanned.absolutePath, saved.filePath)
        assertEquals(scanned.absolutePath, saved.thumbnailPath)
    }
}

private class FakePhotoRepository : PhotoRepository {
    val saved = mutableListOf<SitePhoto>()

    override suspend fun add(photo: SitePhoto): AppResult<Unit> {
        saved += photo
        return AppResult.Success(Unit)
    }

    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> {
        return AppResult.Success(saved.filter { it.projectId == projectId })
    }

    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> {
        return AppResult.Success(saved.filter { it.projectId == projectId && it.objectCode == objectCode })
    }

    override fun observeByProject(projectId: String): Flow<List<SitePhoto>> {
        return flowOf(saved.filter { it.projectId == projectId })
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

private class FakeGisRepository : GisRepository {
    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<com.mapsupervision.domain.model.GisNode>> {
        return AppResult.Success(emptyList())
    }

    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<com.mapsupervision.domain.model.GisRoute>> {
        return AppResult.Success(emptyList())
    }

    override suspend fun upsertNode(node: com.mapsupervision.domain.model.GisNode): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun upsertRoute(route: com.mapsupervision.domain.model.GisRoute): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun upsertNodes(nodes: List<com.mapsupervision.domain.model.GisNode>): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun upsertRoutes(routes: List<com.mapsupervision.domain.model.GisRoute>): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<com.mapsupervision.domain.model.GisNode>, routes: List<com.mapsupervision.domain.model.GisRoute>): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<com.mapsupervision.domain.model.GisNode?> {
        return AppResult.Success(null)
    }

    override fun observeNodes(projectId: String, query: String): Flow<List<com.mapsupervision.domain.model.GisNode>> {
        return flowOf(emptyList())
    }

    override fun observeRoutes(projectId: String, query: String): Flow<List<com.mapsupervision.domain.model.GisRoute>> {
        return flowOf(emptyList())
    }
}

private class FakeProjectRepository : ProjectRepository {
    override suspend fun create(name: String, customPath: String?): AppResult<Project> {
        return AppResult.Success(Project("proj-1", name, "proj-slug", false, 100L))
    }

    override suspend fun list(includeArchived: Boolean): AppResult<List<Project>> {
        return AppResult.Success(listOf(Project("proj-1", "Test Project", "proj-slug", false, 100L)))
    }

    override suspend fun clone(projectId: String, newName: String): AppResult<Project> {
        return AppResult.Success(Project(projectId, newName, "proj-slug", false, 100L))
    }

    override suspend fun archive(projectId: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun importProject(project: Project): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun clearProject(projectId: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun touch(projectId: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }

    override suspend fun updateStoragePath(projectId: String, newPath: String): AppResult<Unit> {
        return AppResult.Success(Unit)
    }
}

private class FakeProjectSyncRepository : ProjectSyncRepository {
    override val events: SharedFlow<ProjectSyncEvent> = MutableSharedFlow()
    override suspend fun notifyProjectChanged(projectId: String?, reason: String) = Unit
}

private class FakeProjectStorageManager(context: Context) : ProjectStorageManager(context) {
    val scannedFiles = mutableListOf<File>()

    override fun scanFile(file: File) {
        scannedFiles += file
    }
}

private class FakePhotoOcrService : PhotoOcrService {
    override suspend fun extractMaterialData(imageUri: String): PhotoMaterialDataResult {
        return PhotoMaterialDataResult(true, null, null, null, null)
    }

    override suspend fun extractDailyLogData(imageUri: String): PhotoDailyLogDataResult {
        return PhotoDailyLogDataResult(true, null, null, null, null)
    }
}

private class FakeAiFacade : AIFacade {
    override suspend fun <T : com.mapsupervision.ai.core.AiResult> execute(payload: com.mapsupervision.ai.core.AiPayload): com.mapsupervision.ai.core.AiDecision<T> {
        val result = PhotoQualityResult(100, emptyList(), "ok", false)
        @Suppress("UNCHECKED_CAST")
        return com.mapsupervision.ai.core.AiDecision(
            capability = payload.capability,
            result = result as T,
            confidence = 100,
            source = com.mapsupervision.ai.core.AiDecisionSource.RULE_BASED,
            reason = "test"
        )
    }
}

private class FakeAiRepository : AiRepository {
    override suspend fun suggestMapping(payload: ImportMappingPayload): ImportMappingResult {
        return ImportMappingResult("", "", "", "", emptyList(), true)
    }

    override suspend fun detectDiscrepancies(payload: DiscrepancyCheckPayload): DiscrepancyResult {
        return DiscrepancyResult(emptyList(), emptyList())
    }

    override suspend fun summarizeDaily(payload: TimelineSummaryPayload): TimelineSummaryResult {
        return TimelineSummaryResult(
            summary = "",
            issueHighlights = emptyList(),
            recommendedActions = emptyList()
        )
    }

    override suspend fun photoQualityCheck(payload: PhotoQualityPayload): PhotoQualityResult {
        return PhotoQualityResult(100, emptyList(), "ok", false)
    }

    override suspend fun reportDraft(payload: ReportDraftPayload): ReportDraftResult {
        return ReportDraftResult("", "", emptyList())
    }

    override suspend fun operationRecommendations(payload: OpsRecommendationPayload): OpsRecommendationResult {
        return OpsRecommendationResult(emptyList(), 1)
    }
}

