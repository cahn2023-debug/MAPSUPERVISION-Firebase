package com.mapsupervision.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
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
class GisRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var database: MapSupervisionDatabase
    private lateinit var repository: GisRepositoryImpl
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = Files.createTempDirectory("gis-repo-test").toFile()
        database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GisRepositoryImpl(
            projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, ProjectStorageManager(context)),
            sharedDatabase = database,
            activeProjectRepository = FakeActiveProjectRepository()
        )
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `replaceImportedGeometry removes old rows and keeps other files`() = runBlocking {
        val projectId = "project-1"
        database.projectDao().upsert(
            com.mapsupervision.data.db.entity.ProjectEntity(
                id = projectId,
                name = "Project 1",
                slug = "project-1",
                isArchived = false,
                createdAtEpochMs = 1000L,
                metadataVersion = 3,
                updatedAtEpochMs = 1000L,
                storageMode = com.mapsupervision.domain.model.ProjectStorageMode.LEGACY_SHARED,
                projectDbPath = ""
            )
        )
        database.importedFileDao().upsert(
            com.mapsupervision.data.db.entity.ImportedFileEntity(
                id = "file-1",
                projectId = projectId,
                fileName = "file1.kml",
                fileType = "KML",
                storedPath = "",
                summary = "",
                importedAtEpochMs = 1000L
            )
        )
        database.importedFileDao().upsert(
            com.mapsupervision.data.db.entity.ImportedFileEntity(
                id = "file-2",
                projectId = projectId,
                fileName = "file2.kml",
                fileType = "KML",
                storedPath = "",
                summary = "",
                importedAtEpochMs = 1000L
            )
        )
        database.gisNodeDao().upsertAll(
            listOf(
                nodeEntity("node-old-1", projectId, "NODE-OLD-1", "file-1"),
                nodeEntity("node-keep-1", projectId, "NODE-KEEP-1", "file-2")
            )
        )
        database.gisRouteDao().upsertAll(
            listOf(
                routeEntity("route-old-1", projectId, "ROUTE-OLD-1", "file-1", "NODE-OLD-1", "NODE-OLD-2"),
                routeEntity("route-keep-1", projectId, "ROUTE-KEEP-1", "file-2", "NODE-KEEP-1", "NODE-KEEP-2")
            )
        )

        val result = repository.replaceImportedGeometry(
            importedFileId = "file-1",
            nodes = listOf(
                GisNode(
                    id = "node-new-1",
                    projectId = projectId,
                    code = "NODE-NEW-1",
                    contractor = "CTR-A",
                    latitude = 10.0,
                    longitude = 106.0,
                    mapNumberLabel = "1",
                    workVolumeSummary = "summary"
                )
            ),
            routes = listOf(
                GisRoute(
                    id = "route-new-1",
                    projectId = projectId,
                    code = "ROUTE-NEW-1",
                    contractor = "CTR-A",
                    startNodeCode = "NODE-NEW-1",
                    endNodeCode = "NODE-KEEP-1"
                )
            )
        )

        assertTrue(result is AppResult.Success)

        val nodes = database.gisNodeDao().byProject(projectId)
        val routes = database.gisRouteDao().byProject(projectId)
        assertEquals(setOf("NODE-NEW-1", "NODE-KEEP-1"), nodes.map { it.code }.toSet())
        assertEquals(setOf("ROUTE-NEW-1", "ROUTE-KEEP-1"), routes.map { it.code }.toSet())
        assertEquals("file-1", nodes.first { it.code == "NODE-NEW-1" }.importedFileId)
        assertEquals("file-2", nodes.first { it.code == "NODE-KEEP-1" }.importedFileId)
    }

    @Test
    fun `upsert and search nodes and routes round trips new network and fiber fields`() = runBlocking {
        val projectId = "project-1"
        database.projectDao().upsert(
            com.mapsupervision.data.db.entity.ProjectEntity(
                id = projectId,
                name = "Project 1",
                slug = "project-1",
                isArchived = false,
                createdAtEpochMs = 1000L,
                metadataVersion = 3,
                updatedAtEpochMs = 1000L,
                storageMode = com.mapsupervision.domain.model.ProjectStorageMode.LEGACY_SHARED,
                projectDbPath = ""
            )
        )

        val node = GisNode(
            id = "node-1",
            projectId = projectId,
            code = "NODE-1",
            contractor = "CTR-A",
            latitude = 10.0,
            longitude = 106.0,
            mapNumberLabel = "1",
            workVolumeSummary = "summary",
            ipAddress = "192.168.1.10",
            subnet = "255.255.255.0",
            gateway = "192.168.1.1",
            signalStatus = com.mapsupervision.domain.model.NodeSignalStatus.HAS_SIGNAL
        )

        val route = GisRoute(
            id = "route-1",
            projectId = projectId,
            code = "ROUTE-1",
            contractor = "CTR-A",
            startNodeCode = "NODE-1",
            endNodeCode = "NODE-2",
            fiberCoreCount = 24,
            fiberConnection = "ODF-A to ODF-B"
        )

        // Save
        repository.upsertNode(node)
        repository.upsertRoute(route)

        // Retrieve
        val searchedNodes = repository.searchNodes(projectId, "NODE-1")
        val searchedRoutes = repository.searchRoutes(projectId, "ROUTE-1")

        assertTrue(searchedNodes is AppResult.Success)
        val retrievedNode = (searchedNodes as AppResult.Success).data.single()
        assertEquals("192.168.1.10", retrievedNode.ipAddress)
        assertEquals("255.255.255.0", retrievedNode.subnet)
        assertEquals("192.168.1.1", retrievedNode.gateway)
        assertEquals(com.mapsupervision.domain.model.NodeSignalStatus.HAS_SIGNAL, retrievedNode.signalStatus)

        assertTrue(searchedRoutes is AppResult.Success)
        val retrievedRoute = (searchedRoutes as AppResult.Success).data.single()
        assertEquals(24, retrievedRoute.fiberCoreCount)
        assertEquals("ODF-A to ODF-B", retrievedRoute.fiberConnection)
    }

    private fun nodeEntity(id: String, projectId: String, code: String, importedFileId: String) =
        com.mapsupervision.data.db.entity.GisNodeEntity(
            id = id,
            projectId = projectId,
            code = code,
            contractor = "CTR",
            latitude = 10.0,
            longitude = 106.0,
            mapNumberLabel = "1",
            workVolumeSummary = "",
            importedFileId = importedFileId
        )

    private fun routeEntity(
        id: String,
        projectId: String,
        code: String,
        importedFileId: String,
        startNodeCode: String,
        endNodeCode: String
    ) = com.mapsupervision.data.db.entity.GisRouteEntity(
        id = id,
        projectId = projectId,
        code = code,
        contractor = "CTR",
        startNodeCode = startNodeCode,
        endNodeCode = endNodeCode,
        points = emptyList(),
        importedFileId = importedFileId
    )

    private class FakeActiveProjectRepository : ActiveProjectRepository {
        private val activeId = MutableStateFlow<String?>(null)

        override val activeProjectId = activeId

        override suspend fun setActive(projectId: String): AppResult<Unit> {
            activeId.value = projectId
            return AppResult.Success(Unit)
        }

        override suspend fun getActive(): AppResult<String?> = AppResult.Success(activeId.value)
    }
}

