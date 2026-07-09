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
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class ImportedFileRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var provider: ProjectScopedDatabaseProvider
    private lateinit var activeProjectRepository: FakeActiveProjectRepository
    private lateinit var repository: ImportedFileRepositoryImpl
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("imported-file-repository-test").toFile()
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
        repository = ImportedFileRepositoryImpl(
            sharedDatabase.importedFileDao(),
            provider,
            activeProjectRepository
        )
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `deleteById removes imported data from shared and scoped databases even when active project differs`() = runBlocking {
        val targetProject = projectEntity("target-project")
        val otherProject = projectEntity("other-project")
        sharedDatabase.projectDao().upsert(targetProject)
        sharedDatabase.projectDao().upsert(otherProject)

        val targetScopedDatabase = provider.databaseFor(targetProject.id)!!
        val otherScopedDatabase = provider.databaseFor(otherProject.id)!!
        openedDatabases += targetScopedDatabase
        openedDatabases += otherScopedDatabase

        val importedFile = importedFile(targetProject.id, "file-1")
        val importedNode = importedNode(targetProject.id, importedFile.id, "node-1")
        seedImportedData(sharedDatabase, importedFile, importedNode)
        seedImportedData(targetScopedDatabase, importedFile, importedNode)

        assertTrue(activeProjectRepository.setActive(otherProject.id) is AppResult.Success)
        assertTrue(repository.deleteById(importedFile.id) is AppResult.Success)

        assertEquals(true, sharedDatabase.importedFileDao().findById(importedFile.id)?.isDeleted)
        assertEquals(true, targetScopedDatabase.importedFileDao().findById(importedFile.id)?.isDeleted)
        assertTrue(sharedDatabase.gisNodeDao().byProject(targetProject.id).isEmpty())
        assertTrue(targetScopedDatabase.gisNodeDao().byProject(targetProject.id).isEmpty())
        assertTrue(otherScopedDatabase.importedFileDao().byProject(otherProject.id).isEmpty())
    }

    private suspend fun seedImportedData(
        database: MapSupervisionDatabase,
        file: ImportedFileEntity,
        node: GisNodeEntity
    ) {
        database.importedFileDao().upsert(file)
        database.gisNodeDao().upsert(node)
    }

    private fun importedFile(projectId: String, fileId: String) = ImportedFileEntity(
        id = fileId,
        projectId = projectId,
        fileName = "design.xlsx",
        fileType = "xlsx",
        storedPath = "D:/imports/design.xlsx",
        summary = "",
        importedAtEpochMs = 1L
    )

    private fun importedNode(projectId: String, fileId: String, nodeId: String) = GisNodeEntity(
        id = nodeId,
        projectId = projectId,
        code = "NODE-1",
        contractor = "Contractor A",
        latitude = 10.0,
        longitude = 106.0,
        mapNumberLabel = "Map 1",
        workVolumeSummary = "",
        importedFileId = fileId
    )

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
