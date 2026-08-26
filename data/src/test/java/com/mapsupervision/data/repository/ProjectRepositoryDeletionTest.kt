package com.mapsupervision.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.EventOutboxEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProjectRepositoryDeletionTest {

    @Test
    fun requestDeletion_rejects_active_project_and_reports_pending_work() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val active = FakeActiveProjectRepository("project-1")
        val storage = ProjectStorageManager(context)
        val repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            storageManager = storage,
            eventOutboxDao = database.eventOutboxDao(),
            sitePhotoDao = database.sitePhotoDao(),
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, storage),
            activeProjectRepository = active
        )
        try {
            database.projectDao().upsert(
                ProjectEntity(
                    id = "project-1",
                    name = "Project 1",
                    slug = "project-1",
                    isArchived = false,
                    createdAtEpochMs = 1L
                )
            )
            database.eventOutboxDao().upsert(
                EventOutboxEntity(
                    id = "event-1",
                    projectId = "project-1",
                    eventType = "project.updated",
                    payloadJson = "{}"
                )
            )

            assertTrue(repository.requestDeletion("project-1", "request-1") is AppResult.Error)
            assertEquals(1, (repository.pendingDeletionWork("project-1") as AppResult.Success).data)
        } finally {
            database.close()
        }
    }

    @Test
    fun acknowledgeRemoteDeletion_declineKeepsLocalProjectReadOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storage = ProjectStorageManager(context)
        val repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            storageManager = storage,
            eventOutboxDao = database.eventOutboxDao(),
            sitePhotoDao = database.sitePhotoDao(),
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, storage),
            activeProjectRepository = FakeActiveProjectRepository(null)
        )
        try {
            database.projectDao().upsert(
                ProjectEntity(
                    id = "remote-project",
                    name = "Remote project",
                    slug = "remote-project",
                    isArchived = false,
                    createdAtEpochMs = 1L,
                    deletionState = com.mapsupervision.domain.model.ProjectDeletionState.DELETED,
                    deletionRequestId = "remote-request",
                    cloudDeletionCompletedAtEpochMs = 2L
                )
            )

            assertTrue(repository.acknowledgeRemoteDeletion("remote-project", deleteLocal = false) is AppResult.Success)
            val project = database.projectDao().get("remote-project")
            assertTrue(project != null && !project.isDeleted)
            assertEquals(com.mapsupervision.domain.model.ProjectDeletionState.DELETED, project?.deletionState)
        } finally {
            database.close()
        }
    }

    @Test
    fun requestDeletion_neverUploadedPurgesLocallyWithoutCloudDecision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storage = ProjectStorageManager(context)
        val repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            storageManager = storage,
            eventOutboxDao = database.eventOutboxDao(),
            sitePhotoDao = database.sitePhotoDao(),
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, storage),
            activeProjectRepository = FakeActiveProjectRepository(null)
        )
        try {
            database.projectDao().upsert(
                ProjectEntity(
                    id = "local-only",
                    name = "Local only",
                    slug = "local-only",
                    isArchived = false,
                    createdAtEpochMs = 1L
                )
            )

            assertEquals(
                com.mapsupervision.domain.model.ProjectDeletionState.DELETED,
                (repository.requestDeletion("local-only", "local-request") as AppResult.Success).data
            )
            assertTrue(database.projectDao().get("local-only")?.isDeleted == true)
        } finally {
            database.close()
        }
    }

    @Test
    fun requestDeletion_uploadedProjectPerformsImmediateLocalDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storage = ProjectStorageManager(context)
        val repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            storageManager = storage,
            eventOutboxDao = database.eventOutboxDao(),
            sitePhotoDao = database.sitePhotoDao(),
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, storage),
            activeProjectRepository = FakeActiveProjectRepository(null)
        )
        try {
            database.projectDao().upsert(
                ProjectEntity(
                    id = "uploaded",
                    name = "Uploaded",
                    slug = "uploaded",
                    isArchived = false,
                    createdAtEpochMs = 1L,
                    cloudDataConfirmed = true
                )
            )

            assertEquals(
                com.mapsupervision.domain.model.ProjectDeletionState.DELETED,
                (repository.requestDeletion("uploaded", "cloud-request") as AppResult.Success).data
            )
            val project = database.projectDao().get("uploaded")
            assertTrue(project != null && project.isDeleted)
            assertEquals(com.mapsupervision.domain.model.ProjectDeletionState.DELETED, project?.deletionState)
            assertEquals("cloud-request", project?.deletionRequestId)
        } finally {
            database.close()
        }
    }

    @Test
    fun requestDeletion_localFailureIsRetryableAndDoesNotTouchOtherProject() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storage = ProjectStorageManager(context)
        val repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            storageManager = storage,
            eventOutboxDao = database.eventOutboxDao(),
            sitePhotoDao = database.sitePhotoDao(),
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, storage),
            activeProjectRepository = FakeActiveProjectRepository(null)
        )
        try {
            database.projectDao().upsert(
                ProjectEntity("failed", "Failed", "shared", false, 1L)
            )
            database.projectDao().upsert(
                ProjectEntity("other", "Other", "shared", false, 2L)
            )

            assertTrue(repository.requestDeletion("failed", "failed-request") is AppResult.Error)
            assertEquals(
                com.mapsupervision.domain.model.ProjectDeletionState.LOCAL_DELETE_FAILED,
                database.projectDao().get("failed")?.deletionState
            )
            assertEquals(com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE, database.projectDao().get("other")?.deletionState)
        } finally {
            database.close()
        }
    }

    @Test
    fun forcePurgeLocalProject_marks_project_deleted_and_cleans_rows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val storage = ProjectStorageManager(context)
        val repository = ProjectRepositoryImpl(
            projectDao = database.projectDao(),
            storageManager = storage,
            eventOutboxDao = database.eventOutboxDao(),
            sitePhotoDao = database.sitePhotoDao(),
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, storage),
            activeProjectRepository = FakeActiveProjectRepository(null)
        )
        try {
            database.projectDao().upsert(
                ProjectEntity("stuck-proj", "Stuck Project", "stuck-slug", false, 1L, deletionState = com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_DECISION_PENDING)
            )

            assertTrue(repository.forcePurgeLocalProject("stuck-proj") is AppResult.Success)
            val proj = database.projectDao().get("stuck-proj")
            assertTrue(proj != null)
            assertTrue(proj?.isDeleted == true)
            assertEquals(com.mapsupervision.domain.model.ProjectDeletionState.DELETED, proj?.deletionState)
        } finally {
            database.close()
        }
    }

    private class FakeActiveProjectRepository(initial: String?) : ActiveProjectRepository {
        private val state = MutableStateFlow(initial)

        override val activeProjectId: StateFlow<String?> = state

        override suspend fun setActive(projectId: String): AppResult<Unit> {
            state.value = projectId
            return AppResult.Success(Unit)
        }

        override suspend fun getActive(): AppResult<String?> = AppResult.Success(state.value)
    }
}
