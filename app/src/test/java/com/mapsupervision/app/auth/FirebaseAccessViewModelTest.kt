package com.mapsupervision.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import com.mapsupervision.domain.model.FirebaseProjectCatalogStatus
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.SyncBatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FirebaseAccessViewModelTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun register_requires_matching_passwords() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.updateAuthMode(isRegisterMode = true)
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("secret123")
        viewModel.updateConfirmPassword("secret456")
        viewModel.register()

        assertEquals("Mật khẩu xác nhận không khớp.", viewModel.uiState.value.error)
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun register_success_shows_verification_message() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.updateAuthMode(isRegisterMode = true)
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("secret123")
        viewModel.updateConfirmPassword("secret123")
        viewModel.register()

        waitUntil {
            viewModel.uiState.value.message.contains("Kiểm tra email", ignoreCase = true)
        }
        assertEquals(1, repository.registerCalls)
        assertEquals(false, viewModel.uiState.value.isRegisterMode)
    }

    @Test
    fun skip_enters_offline_mode() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.enterOfflineMode()

        waitUntil { viewModel.uiState.value.user?.isOffline == true }
        assertTrue(viewModel.uiState.value.user?.isOffline == true)
        assertEquals(1, repository.offlineCalls)
    }

    @Test
    fun signed_in_user_sees_catalog_and_request_updates_pending_status() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("secret123")
        viewModel.signIn()

        waitUntil { viewModel.uiState.value.projectCatalog.size == 1 }
        assertEquals(FirebaseAccessRequestStatus.NOT_REQUESTED, viewModel.accessStatusFor("project-1"))

        viewModel.requestProjectAccess("project-1")

        waitUntil { viewModel.accessStatusFor("project-1") == FirebaseAccessRequestStatus.PENDING }
        assertEquals(1, repository.requestCalls)
    }

    @Test
    fun openOrDownloadProject_imports_and_activates_project() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val projectRepo = FakeProjectRepository()
        val activeRepo = FakeActiveProjectRepository()
        val syncRepo = FakeFirebaseSyncRepository()
        val viewModel = FirebaseAccessViewModel(repository, context, projectRepo, activeRepo, syncRepo)

        val entry = FirebaseProjectCatalogEntry(
            projectId = "project-1",
            projectName = "Project One",
            projectCode = "P-001",
            updatedAtEpochMs = 1000L,
            status = FirebaseProjectCatalogStatus.ACTIVE
        )

        var openCalled = false
        viewModel.openOrDownloadProject(entry) {
            openCalled = true
        }

        assertTrue(openCalled)
        assertEquals("project-1", (activeRepo.getActive() as AppResult.Success).data)
        assertEquals(1, projectRepo.projects.size)
        assertEquals("project-1", projectRepo.projects[0].id)
    }

    @Test
    fun createCloudProject_creates_and_activates() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val projectRepo = FakeProjectRepository()
        val activeRepo = FakeActiveProjectRepository()
        val syncRepo = FakeFirebaseSyncRepository()
        val viewModel = FirebaseAccessViewModel(repository, context, projectRepo, activeRepo, syncRepo)

        var created = false
        viewModel.createCloudProject("New Cloud Proj") {
            created = true
        }

        assertTrue(created)
        assertEquals(1, projectRepo.projects.size)
        assertEquals("New Cloud Proj", projectRepo.projects[0].name)
    }

    @Test
    fun openOrDownloadProject_syncs_name_and_refreshes_local_presence() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val projectRepo = FakeProjectRepository()
        val activeRepo = FakeActiveProjectRepository()
        val syncRepo = FakeFirebaseSyncRepository()
        val viewModel = FirebaseAccessViewModel(repository, context, projectRepo, activeRepo, syncRepo)

        val oldLocalProject = Project(
            id = "project-1",
            name = "project-1",
            slug = "p-001",
            isArchived = false,
            createdAtEpochMs = 500L,
            metadataVersion = 1,
            updatedAtEpochMs = 500L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = "",
            mediaStorageProvider = "GOOGLE_DRIVE",
            mediaStorageFolderId = "",
            mediaStorageFolderUrl = "",
            mediaStorageUpdatedAtEpochMs = 0L,
            isDeleted = false,
            deletedAtEpochMs = null,
            cloudDataConfirmed = false
        )
        projectRepo.importProject(oldLocalProject)

        val entry = FirebaseProjectCatalogEntry(
            projectId = "project-1",
            projectName = "Unified Proper Name",
            projectCode = "p-001",
            updatedAtEpochMs = 1200L,
            status = FirebaseProjectCatalogStatus.ACTIVE
        )

        var opened = false
        viewModel.openOrDownloadProject(entry) {
            opened = true
        }

        assertTrue(opened)
        assertEquals("project-1", (activeRepo.getActive() as AppResult.Success).data)
        assertEquals(1, projectRepo.projects.size)
        assertEquals("Unified Proper Name", projectRepo.projects[0].name)
        assertEquals(1, viewModel.uiState.value.localProjects.size)
        assertEquals("project-1", viewModel.uiState.value.activeProjectId)
    }

    @Test
    fun openOrDownloadProject_fallback_to_projectCode_when_name_blank() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val projectRepo = FakeProjectRepository()
        val activeRepo = FakeActiveProjectRepository()
        val syncRepo = FakeFirebaseSyncRepository()
        val viewModel = FirebaseAccessViewModel(repository, context, projectRepo, activeRepo, syncRepo)

        val entry = FirebaseProjectCatalogEntry(
            projectId = "project-blank-name",
            projectName = "",
            projectCode = "PROJ-CODE-XYZ",
            updatedAtEpochMs = 1000L,
            status = FirebaseProjectCatalogStatus.ACTIVE
        )

        var opened = false
        viewModel.openOrDownloadProject(entry) {
            opened = true
        }

        assertTrue(opened)
        assertEquals("PROJ-CODE-XYZ", projectRepo.projects[0].name)
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
    private val state = MutableStateFlow(FirebaseAccessState(isInitialized = true))
    override val accessState: StateFlow<FirebaseAccessState> = state
    var registerCalls = 0
    var offlineCalls = 0
    var requestCalls = 0

    override suspend fun signIn(email: String, password: String): AppResult<FirebaseUserSession> {
        val session = FirebaseUserSession(uid = "u1", email = email, emailVerified = true)
        state.value = FirebaseAccessState(
            session = session,
            allowedProjectIds = setOf("project-1"),
            permissionsByProject = mapOf("project-1" to ProjectAccess("project-1")),
            isInitialized = true
        )
        return AppResult.Success(session)
    }

    override suspend fun register(email: String, password: String): AppResult<Unit> {
        registerCalls += 1
        return AppResult.Success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession> =
        signIn("google@example.com", "unused")

    override suspend fun enterOfflineMode(): AppResult<FirebaseAccessState> {
        offlineCalls += 1
        val nextState = FirebaseAccessState(
            session = FirebaseUserSession(
                uid = "offline",
                email = "offline@local",
                emailVerified = true,
                isOffline = true
            ),
            isInitialized = true
        )
        state.value = nextState
        return AppResult.Success(nextState)
    }

    override suspend fun signOut(): AppResult<Unit> {
        state.value = FirebaseAccessState(isInitialized = true)
        return AppResult.Success(Unit)
    }

    override suspend fun refreshAccess(): AppResult<FirebaseAccessState> = AppResult.Success(state.value)
    override suspend fun ensureUserProfile(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun listProjectCatalog(
        pageSize: Long,
        startAfterUpdatedAtEpochMs: Long?,
        startAfterProjectId: String?
    ): AppResult<List<FirebaseProjectCatalogEntry>> = AppResult.Success(
        if (startAfterProjectId == null) listOf(
            FirebaseProjectCatalogEntry(
                projectId = "project-1",
                projectName = "Project One",
                projectCode = "P-001",
                updatedAtEpochMs = 100L,
                status = FirebaseProjectCatalogStatus.ACTIVE
            )
        ) else emptyList()
    )

    override suspend fun requestProjectAccess(projectId: String): AppResult<FirebaseProjectAccessRequest> {
        requestCalls += 1
        return AppResult.Success(
            FirebaseProjectAccessRequest(
                requestId = "project-1__u1",
                projectId = projectId,
                userId = "u1",
                status = FirebaseAccessRequestStatus.PENDING,
                requestedAtEpochMs = 100L,
                updatedAtEpochMs = 100L
            )
        )
    }

    override suspend fun getProjectAccessRequest(projectId: String): AppResult<FirebaseProjectAccessRequest?> =
        AppResult.Success(null)

    override fun projectAccess(projectId: String): ProjectAccess? = state.value.permissionsByProject[projectId]
}

private class FakeProjectRepository : ProjectRepository {
    val projects = mutableListOf<Project>()

    override suspend fun create(name: String, customPath: String?): AppResult<Project> {
        val proj = Project(
            id = "proj-${projects.size + 1}",
            name = name,
            slug = name.lowercase().replace(" ", "-"),
            isArchived = false,
            createdAtEpochMs = System.currentTimeMillis(),
            metadataVersion = 3,
            updatedAtEpochMs = System.currentTimeMillis(),
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = "",
            mediaStorageProvider = "GOOGLE_DRIVE",
            mediaStorageFolderId = "",
            mediaStorageFolderUrl = "",
            mediaStorageUpdatedAtEpochMs = 0L,
            isDeleted = false,
            deletedAtEpochMs = null
        )
        projects.add(proj)
        return AppResult.Success(proj)
    }

    override suspend fun list(includeArchived: Boolean): AppResult<List<Project>> = AppResult.Success(projects.toList())

    override suspend fun clone(projectId: String, newName: String): AppResult<Project> =
        create(newName)

    override suspend fun archive(projectId: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun importProject(project: Project): AppResult<Unit> {
        projects.removeAll { it.id == project.id }
        projects.add(project)
        return AppResult.Success(Unit)
    }

    override suspend fun clearProject(projectId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun touch(projectId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun updateStoragePath(projectId: String, newPath: String): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeActiveProjectRepository : ActiveProjectRepository {
    private val activeFlow = MutableStateFlow<String?>("default-proj")
    override val activeProjectId: StateFlow<String?> = activeFlow

    override suspend fun getActive(): AppResult<String> {
        val current = activeFlow.value
        return if (current != null) AppResult.Success(current) else AppResult.Error(IllegalStateException("No active project"))
    }

    override suspend fun setActive(projectId: String): AppResult<Unit> {
        activeFlow.value = projectId
        return AppResult.Success(Unit)
    }
}

private class FakeFirebaseSyncRepository : FirebaseSyncRepository {
    var pushCalls = 0
    var pullCalls = 0

    override suspend fun pushPending(projectId: String): AppResult<SyncBatchResult> {
        pushCalls++
        return AppResult.Success(SyncBatchResult(pushed = 1))
    }

    override suspend fun pullChanges(projectId: String, sinceEpochMs: Long?): AppResult<SyncBatchResult> {
        pullCalls++
        return AppResult.Success(SyncBatchResult(pulled = 1))
    }

    override suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult> =
        AppResult.Success(SyncBatchResult(uploadedMedia = 0))
}
