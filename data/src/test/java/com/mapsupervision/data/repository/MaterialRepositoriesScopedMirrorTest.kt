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
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
class MaterialRepositoriesScopedMirrorTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var provider: ProjectScopedDatabaseProvider
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("material-repository-test").toFile()
        storageManager = object : ProjectStorageManager(context) {
            override fun scopedProjectDbRootDirectory(projectSlug: String): File {
                return File(tempDir, "scoped-private/$projectSlug")
            }
        }
        sharedDatabase = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `material writes are mirrored to scoped and shared databases`() = runBlocking {
        val project = projectEntity("project-material")
        sharedDatabase.projectDao().upsert(project)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val declarationRepository = MaterialDeclarationRepositoryImpl(
            sharedDatabase.materialDeclarationDao(),
            provider
        )
        val handoverRepository = MaterialHandoverRepositoryImpl(
            sharedDatabase.materialHandoverDao(),
            provider
        )

        val declaration = MaterialDeclaration(
            id = "declaration-1",
            projectId = project.id,
            workName = "Cable",
            materialName = "Cable drum",
            ratio = 1f,
            unit = "m",
            createdAtEpochMs = 100L
        )
        val declarationResult = declarationRepository.add(declaration)

        assertTrue(declarationResult is AppResult.Success)
        assertEquals(1, scopedDatabase.materialDeclarationDao().getByProject(project.id).size)
        assertEquals(1, sharedDatabase.materialDeclarationDao().getByProject(project.id).size)

        val handover = MaterialHandover(
            id = "handover-1",
            projectId = project.id,
            nodeCode = "N-01",
            workName = "Cable",
            materialName = "Cable drum",
            contractor = "Contractor",
            quantity = 2f,
            unit = "m",
            handoverDateEpochDay = 20240702L,
            note = "handover",
            createdAtEpochMs = 200L,
            materialDeclarationId = declaration.id
        )
        val handoverResult = handoverRepository.add(handover)

        assertTrue(handoverResult is AppResult.Success)
        assertEquals(1, scopedDatabase.materialHandoverDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.materialHandoverDao().byProject(project.id).size)

        assertTrue(handoverRepository.delete(handover) is AppResult.Success)
        assertTrue(declarationRepository.delete(declaration) is AppResult.Success)
        assertEquals(0, scopedDatabase.materialHandoverDao().byProject(project.id).size)
        assertEquals(0, sharedDatabase.materialHandoverDao().byProject(project.id).size)
        assertEquals(0, scopedDatabase.materialDeclarationDao().getByProject(project.id).size)
        assertEquals(0, sharedDatabase.materialDeclarationDao().getByProject(project.id).size)
    }

    @Test
    fun `legacy project material reads fall back to shared database while scoped aux is empty`() = runBlocking {
        val project = projectEntity("legacy-material")
        sharedDatabase.projectDao().upsert(project)

        val declaration = MaterialDeclaration(
            id = "legacy-declaration-1",
            projectId = project.id,
            workName = "Cable",
            materialName = "Cable drum",
            ratio = 1f,
            unit = "m",
            createdAtEpochMs = 100L
        )
        sharedDatabase.materialDeclarationDao().insert(MaterialDeclarationEntity.fromDomain(declaration))
        sharedDatabase.materialHandoverDao().upsert(
            MaterialHandoverEntity.fromDomain(
                MaterialHandover(
                    id = "legacy-handover-1",
                    projectId = project.id,
                    nodeCode = "N-01",
                    workName = "Cable",
                    materialName = "Cable drum",
                    contractor = "Contractor",
                    quantity = 2f,
                    unit = "m",
                    handoverDateEpochDay = 20240702L,
                    note = "handover",
                    createdAtEpochMs = 200L,
                    materialDeclarationId = declaration.id
                )
            )
        )

        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val declarationRepository = MaterialDeclarationRepositoryImpl(
            sharedDatabase.materialDeclarationDao(),
            provider
        )
        val handoverRepository = MaterialHandoverRepositoryImpl(
            sharedDatabase.materialHandoverDao(),
            provider
        )

        assertEquals(1, (declarationRepository.getByProject(project.id) as AppResult.Success).data.size)
        assertEquals(1, declarationRepository.observeByProject(project.id).first().size)
        assertEquals(1, (handoverRepository.byProject(project.id) as AppResult.Success).data.size)
        assertEquals(1, handoverRepository.observeByProject(project.id).first().size)
        assertEquals(1, scopedDatabase.materialDeclarationDao().getByProject(project.id).size)
        assertEquals(1, scopedDatabase.materialHandoverDao().byProject(project.id).size)

        val newHandover = MaterialHandover(
            id = "legacy-handover-2",
            projectId = project.id,
            nodeCode = "N-02",
            workName = "Cable",
            materialName = "Cable drum",
            contractor = "Contractor",
            quantity = 3f,
            unit = "m",
            handoverDateEpochDay = 20240703L,
            note = "handover 2",
            createdAtEpochMs = 300L,
            materialDeclarationId = declaration.id
        )

        assertTrue(handoverRepository.add(newHandover) is AppResult.Success)
        assertEquals(2, scopedDatabase.materialHandoverDao().byProject(project.id).size)
        assertEquals(2, sharedDatabase.materialHandoverDao().byProject(project.id).size)
    }

    @Test
    fun `handover observe emits saved row after add`() = runBlocking {
        val project = projectEntity("handover-observe")
        sharedDatabase.projectDao().upsert(project)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val handoverRepository = MaterialHandoverRepositoryImpl(
            sharedDatabase.materialHandoverDao(),
            provider
        )
        val observed = async {
            withTimeout(5_000L) {
                handoverRepository.observeByProject(project.id).first { it.isNotEmpty() }
            }
        }
        yield()

        val handover = MaterialHandover(
            id = "handover-observe-1",
            projectId = project.id,
            nodeCode = "N-01",
            workName = "Cable",
            materialName = "Cable drum",
            contractor = "Contractor",
            quantity = 2f,
            unit = "m",
            handoverDateEpochDay = 20240702L,
            note = "handover",
            createdAtEpochMs = 200L
        )

        assertTrue(handoverRepository.add(handover) is AppResult.Success)
        assertEquals(1, observed.await().size)
        assertEquals(1, scopedDatabase.materialHandoverDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.materialHandoverDao().byProject(project.id).size)
    }

    @Test
    fun `handover with stale optional links still saves to scoped database`() = runBlocking {
        val project = projectEntity("handover-stale-links")
        sharedDatabase.projectDao().upsert(project)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val handoverRepository = MaterialHandoverRepositoryImpl(
            sharedDatabase.materialHandoverDao(),
            provider
        )
        val handover = MaterialHandover(
            id = "handover-stale-links-1",
            projectId = project.id,
            nodeCode = "N-01",
            nodeId = "missing-node",
            workName = "Cable",
            materialName = "Cable drum",
            contractor = "Contractor",
            quantity = 2f,
            unit = "m",
            handoverDateEpochDay = 20240702L,
            note = "handover",
            createdAtEpochMs = 200L,
            materialDeclarationId = "missing-declaration",
            workCategoryId = "missing-category"
        )

        assertTrue(handoverRepository.add(handover) is AppResult.Success)
        val scopedRows = scopedDatabase.materialHandoverDao().byProject(project.id)
        assertEquals(1, scopedRows.size)
        assertEquals(null, scopedRows.single().nodeId)
        assertEquals(null, scopedRows.single().materialDeclarationId)
        assertEquals(null, scopedRows.single().workCategoryId)
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
