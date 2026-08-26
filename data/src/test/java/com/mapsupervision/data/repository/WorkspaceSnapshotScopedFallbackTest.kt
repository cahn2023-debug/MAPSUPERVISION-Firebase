package com.mapsupervision.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.usecase.ObserveWorkspaceSnapshotUseCase
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WorkspaceSnapshotScopedFallbackTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var provider: ProjectScopedDatabaseProvider
    private lateinit var activeProjectRepository: FakeActiveProjectRepository
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("workspace-snapshot-fallback-test").toFile()
        storageManager = object : ProjectStorageManager(context) {
            override fun scopedProjectDbRootDirectory(projectSlug: String): File {
                return File(tempDir, "scoped-private/$projectSlug")
            }
        }
        sharedDatabase = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        activeProjectRepository = FakeActiveProjectRepository()
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `workspace snapshot falls back to shared data when scoped database is empty`() = runBlocking {
        val project = projectEntity("fallback-project")
        sharedDatabase.projectDao().upsert(project)
        assertTrue(activeProjectRepository.setActive(project.id) is AppResult.Success)

        val scopedDatabase = provider.databaseFor(project.id)!!
        openedDatabases += scopedDatabase

        val importedFile = ImportedFileEntity(
            id = "file-1",
            projectId = project.id,
            fileName = "design.xlsx",
            fileType = "xlsx",
            storedPath = "D:/imports/design.xlsx",
            summary = "summary",
            importedAtEpochMs = 1L
        )
        val node = GisNodeEntity(
            id = "node-1",
            projectId = project.id,
            code = "NODE-1",
            contractor = "CTR",
            latitude = 10.0,
            longitude = 106.0,
            mapNumberLabel = "Map 1",
            workVolumeSummary = "summary",
            importedFileId = importedFile.id
        )
        sharedDatabase.importedFileDao().upsert(importedFile)
        sharedDatabase.gisNodeDao().upsert(node)
        sharedDatabase.nodeProgressDao().upsert(
            NodeProgressEntity(
                id = "progress-1",
                projectId = project.id,
                nodeId = node.id,
                planned = 100f,
                actual = 20f,
                remain = 80f,
                delayed = false,
                updatedAtEpochMs = 2L
            )
        )
        sharedDatabase.workVolumeProgressDao().upsert(
            MaterialProgressEntity(
                id = "material-1",
                projectId = project.id,
                nodeCode = node.code,
                nodeId = node.id,
                materialName = "Cable",
                plannedQty = 100f,
                actualQty = 20f,
                updatedAtEpochMs = 3L,
                unit = "m"
            )
        )
        sharedDatabase.dailyLogDao().upsert(
            DailyLogEntity(
                id = "log-1",
                projectId = project.id,
                workItem = "Pull cable",
                manpower = 4,
                note = "done",
                createdAtEpochMs = 4L,
                weather = "sunny",
                temperature = 30.0,
                dateEpochDay = 20260708L,
                volume = 20.0,
                unit = "m",
                categoryName = "Cable",
                batchGroupId = "batch-1",
                photoMatchOffsetMinutes = 0,
                nodeId = node.id
            )
        )
        sharedDatabase.sitePhotoDao().upsert(
            SitePhotoEntity(
                id = "photo-1",
                projectId = project.id,
                objectCode = node.code,
                tagCodesCsv = "",
                filePath = "D:/photos/photo-1.jpg",
                thumbnailPath = "D:/photos/photo-1.jpg",
                latitude = 10.0,
                longitude = 106.0,
                locationAccuracyM = 2f,
                isGpsMocked = false,
                locationStatus = PhotoLocationStatus.OK,
                engineer = "Field",
                capturedAtEpochMs = 5L,
                matchedAtEpochMs = 5L,
                matchingTimeOffsetMs = 0L,
                matchedNodeId = node.id,
                syncStatus = SitePhotoSyncStatus.PENDING
            )
        )

        val snapshotUseCase = ObserveWorkspaceSnapshotUseCase(
            importedFileRepository = ImportedFileRepositoryImpl(sharedDatabase.importedFileDao(), provider, activeProjectRepository),
            gisRepository = GisRepositoryImpl(provider, sharedDatabase, activeProjectRepository),
            progressRepository = ProgressRepositoryImpl(sharedDatabase.nodeProgressDao(), provider),
            workVolumeProgressRepository = WorkVolumeProgressRepositoryImpl(sharedDatabase.workVolumeProgressDao(), provider),
            dailyLogRepository = DailyLogRepositoryImpl(sharedDatabase.dailyLogDao(), provider, sharedDatabase),
            workCategoryRepository = WorkCategoryRepositoryImpl(sharedDatabase.workCategoryDao(), provider),
            photoRepository = PhotoRepositoryImpl(sharedDatabase.sitePhotoDao(), provider, sharedDatabase.projectDao(), storageManager, com.mapsupervision.storage.DomainEventBusImpl()),
            materialHandoverRepository = MaterialHandoverRepositoryImpl(sharedDatabase.materialHandoverDao(), provider),
            materialDeclarationRepository = MaterialDeclarationRepositoryImpl(sharedDatabase.materialDeclarationDao(), provider),
            workPlanRepository = WorkPlanRepositoryImpl(sharedDatabase.workPlanDao(), provider),
            taskRepository = TaskRepositoryImpl(sharedDatabase.taskDao(), provider, activeProjectRepository)
        )

        val snapshot = snapshotUseCase(project.id).first()

        assertEquals(1, snapshot.importedFiles.size)
        assertEquals(1, snapshot.designNodes.size)
        assertEquals(1, snapshot.constructionProgress.size)
        assertEquals(1, snapshot.workVolumeRows.size)
        assertEquals(1, snapshot.dailyLogs.size)
        assertEquals(1, snapshot.sitePhotos.size)
        assertTrue(scopedDatabase.importedFileDao().byProject(project.id).isEmpty())
    }

    private fun projectEntity(projectId: String) = ProjectEntity(
        id = projectId,
        name = projectId,
        slug = projectId,
        isArchived = false,
        createdAtEpochMs = 1L,
        storageMode = ProjectStorageMode.PROJECT_DB,
        projectDbPath = storageManager.scopedProjectDbFile(projectId).absolutePath
    )

    private class FakeActiveProjectRepository : ActiveProjectRepository {
        private val activeProject = MutableStateFlow<String?>(null)
        override val activeProjectId: StateFlow<String?> = activeProject

        override suspend fun setActive(projectId: String): AppResult<Unit> {
            activeProject.value = projectId
            return AppResult.Success(Unit)
        }

        override suspend fun getActive(): AppResult<String?> = AppResult.Success(activeProject.value)
    }

    private class TestDatabaseContext(base: Context) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File {
            return if (name.contains(File.separatorChar) || name.contains('/')) {
                File(name)
            } else {
                super.getDatabasePath(name)
            }
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            val path = getDatabasePath(name)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(path, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            val path = getDatabasePath(name)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openDatabase(
                path.absolutePath,
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler
            )
        }
    }
}
