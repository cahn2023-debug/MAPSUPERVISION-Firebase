package com.mapsupervision.data.db

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProjectScopedDatabaseProviderTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("project-scoped-db-test").toFile()
        storageManager = object : ProjectStorageManager(context) {
            override fun scopedProjectDbRootDirectory(projectSlug: String): File {
                return File(tempDir, "scoped-private/$projectSlug")
            }
        }
        sharedDatabase = createDatabase(File(tempDir, "shared.sqlite"))
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `databaseFor seeds empty scoped db from shared db`() = runBlocking {
        val project = projectEntity("project-1", File(tempDir, "scoped/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.importedFileDao().upsert(
            ImportedFileEntity(
                id = "file-1",
                projectId = project.id,
                fileName = "nodes.kml",
                fileType = "KML",
                storedPath = "/tmp/nodes.kml",
                summary = "seed",
                importedAtEpochMs = 100L
            )
        )
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "node-1",
                projectId = project.id,
                code = "N-1",
                contractor = "Nam Ky",
                latitude = 21.028,
                longitude = 105.854,
                mapNumberLabel = "229",
                workVolumeSummary = "Cable",
                importedFileId = "file-1"
            )
        )
        sharedDatabase.gisRouteDao().upsert(
            GisRouteEntity(
                id = "route-1",
                projectId = project.id,
                code = "R-1",
                contractor = "Nam Ky",
                startNodeCode = "N-1",
                endNodeCode = "N-2",
                points = emptyList(),
                importedFileId = "file-1"
            )
        )
        sharedDatabase.nodeProgressDao().upsert(
            NodeProgressEntity(
                id = "progress-1",
                projectId = project.id,
                nodeId = "node-1",
                planned = 10f,
                actual = 3f,
                remain = 7f,
                delayed = false,
                updatedAtEpochMs = 200L
            )
        )

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertNotNull(scopedDatabase.projectDao().get(project.id))
        assertEquals(1, scopedDatabase.importedFileDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.gisNodeDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.gisRouteDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.nodeProgressDao().byProject(project.id).size)
    }

    @Test
    fun `databaseFor syncs legacy scoped db into shared db`() = runBlocking {
        val scopedFile = File(tempDir, "legacy/project.sqlite")
        val project = projectEntity("project-sync", scopedFile)
        sharedDatabase.projectDao().upsert(project)

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.importedFileDao().upsert(
                ImportedFileEntity(
                    id = "file-sync",
                    projectId = project.id,
                    fileName = "nodes.kml",
                    fileType = "KML",
                    storedPath = "/tmp/nodes.kml",
                    summary = "seed",
                    importedAtEpochMs = 100L
                )
            )
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "node-sync",
                    projectId = project.id,
                    code = "N-SYNC",
                    contractor = "Nam Ky",
                    latitude = 21.028,
                    longitude = 105.854,
                    mapNumberLabel = "229",
                    workVolumeSummary = "Cable",
                    importedFileId = "file-sync"
                )
            )
            scopedSeed.sitePhotoDao().upsert(
                SitePhotoEntity(
                    id = "photo-sync",
                    projectId = project.id,
                    objectCode = "NODE-SYNC",
                    tagCodesCsv = "",
                    matchedNodeId = "node-sync",
                    matchedRouteId = null,
                    filePath = "/tmp/photo.jpg",
                    thumbnailPath = "/tmp/photo-thumb.jpg",
                    latitude = null,
                    longitude = null,
                    locationAccuracyM = null,
                    isGpsMocked = false,
                    locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
                    engineer = "Field",
                    capturedAtEpochMs = 600L,
                    matchedAtEpochMs = 0L,
                    matchingTimeOffsetMs = 0L
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertEquals(1, scopedDatabase.importedFileDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.gisNodeDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.sitePhotoDao().byProject(project.id).size)

        assertEquals(1, sharedDatabase.importedFileDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.gisNodeDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.sitePhotoDao().byProject(project.id).size)
    }

    @Test
    fun `databaseFor remaps route node foreign keys when syncing scoped db into shared db`() = runBlocking {
        val scopedFile = File(tempDir, "legacy-remap-route/project.sqlite")
        val project = projectEntity("project-remap-route", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.importedFileDao().upsert(
            ImportedFileEntity(
                id = "file-remap",
                projectId = project.id,
                fileName = "design.kml",
                fileType = "KML",
                storedPath = "/tmp/design.kml",
                summary = "seed",
                importedAtEpochMs = 100L
            )
        )
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "shared-start",
                projectId = project.id,
                code = "N-START",
                contractor = "Shared",
                latitude = 0.0,
                longitude = 0.0,
                mapNumberLabel = "1",
                workVolumeSummary = ""
            )
        )
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "shared-end",
                projectId = project.id,
                code = "N-END",
                contractor = "Shared",
                latitude = 1.0,
                longitude = 1.0,
                mapNumberLabel = "2",
                workVolumeSummary = ""
            )
        )

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.importedFileDao().upsert(
                ImportedFileEntity(
                    id = "file-remap",
                    projectId = project.id,
                    fileName = "design.kml",
                    fileType = "KML",
                    storedPath = "/tmp/design.kml",
                    summary = "seed",
                    importedAtEpochMs = 100L
                )
            )
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-start",
                    projectId = project.id,
                    code = "N-START",
                    contractor = "Scoped",
                    latitude = 10.0,
                    longitude = 10.0,
                    mapNumberLabel = "10",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-end",
                    projectId = project.id,
                    code = "N-END",
                    contractor = "Scoped",
                    latitude = 11.0,
                    longitude = 11.0,
                    mapNumberLabel = "11",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.gisRouteDao().upsert(
                GisRouteEntity(
                    id = "scoped-route",
                    projectId = project.id,
                    code = "R-1",
                    contractor = "Scoped",
                    startNodeCode = "N-START",
                    endNodeCode = "N-END",
                    points = emptyList(),
                    importedFileId = "file-remap",
                    startNodeId = "scoped-start",
                    endNodeId = "scoped-end"
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val sharedRoute = sharedDatabase.gisRouteDao().byProject(project.id).single()
        assertEquals("shared-start", sharedRoute.startNodeId)
        assertEquals("shared-end", sharedRoute.endNodeId)
        assertEquals("file-remap", sharedRoute.importedFileId)
    }

    @Test
    fun `databaseFor nulls route node foreign keys when target node codes are missing`() = runBlocking {
        val scopedFile = File(tempDir, "legacy-remap-route-null/project.sqlite")
        val project = projectEntity("project-remap-route-null", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.importedFileDao().upsert(
            ImportedFileEntity(
                id = "existing-target-file",
                projectId = project.id,
                fileName = "existing.kml",
                fileType = "KML",
                storedPath = "/tmp/existing.kml",
                summary = "existing",
                importedAtEpochMs = 50L
            )
        )
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "shared-other-node",
                projectId = project.id,
                code = "N-OTHER",
                contractor = "Shared",
                latitude = 0.0,
                longitude = 0.0,
                mapNumberLabel = "0",
                workVolumeSummary = ""
            )
        )

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.importedFileDao().upsert(
                ImportedFileEntity(
                    id = "missing-file",
                    projectId = project.id,
                    fileName = "scoped.kml",
                    fileType = "KML",
                    storedPath = "/tmp/scoped.kml",
                    summary = "scoped",
                    importedAtEpochMs = 100L
                )
            )
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-start-missing",
                    projectId = project.id,
                    code = "N-MISSING-START",
                    contractor = "Scoped",
                    latitude = 10.0,
                    longitude = 10.0,
                    mapNumberLabel = "10",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-end-missing",
                    projectId = project.id,
                    code = "N-MISSING-END",
                    contractor = "Scoped",
                    latitude = 11.0,
                    longitude = 11.0,
                    mapNumberLabel = "11",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.gisRouteDao().upsert(
                GisRouteEntity(
                    id = "scoped-route-missing",
                    projectId = project.id,
                    code = "R-MISSING",
                    contractor = "Scoped",
                    startNodeCode = "N-MISSING-START",
                    endNodeCode = "N-MISSING-END",
                    points = emptyList(),
                    importedFileId = "missing-file",
                    startNodeId = "scoped-start-missing",
                    endNodeId = "scoped-end-missing"
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val sharedRoute = sharedDatabase.gisRouteDao().byProject(project.id).single()
        assertNull(sharedRoute.startNodeId)
        assertNull(sharedRoute.endNodeId)
        assertNull(sharedRoute.importedFileId)
        assertEquals("N-MISSING-START", sharedRoute.startNodeCode)
        assertEquals("N-MISSING-END", sharedRoute.endNodeCode)
    }

    @Test
    fun `databaseFor remaps auxiliary foreign keys to existing shared node and route ids`() = runBlocking {
        val scopedFile = File(tempDir, "legacy-remap-aux/project.sqlite")
        val project = projectEntity("project-remap-aux", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "shared-node",
                projectId = project.id,
                code = "NODE-1",
                contractor = "Shared",
                latitude = 0.0,
                longitude = 0.0,
                mapNumberLabel = "1",
                workVolumeSummary = ""
            )
        )
        sharedDatabase.gisRouteDao().upsert(
            GisRouteEntity(
                id = "shared-route",
                projectId = project.id,
                code = "ROUTE-1",
                contractor = "Shared",
                startNodeCode = "NODE-1",
                endNodeCode = "NODE-1",
                points = emptyList(),
                startNodeId = "shared-node",
                endNodeId = "shared-node"
            )
        )

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-node",
                    projectId = project.id,
                    code = "NODE-1",
                    contractor = "Scoped",
                    latitude = 1.0,
                    longitude = 1.0,
                    mapNumberLabel = "2",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.gisRouteDao().upsert(
                GisRouteEntity(
                    id = "scoped-route",
                    projectId = project.id,
                    code = "ROUTE-1",
                    contractor = "Scoped",
                    startNodeCode = "NODE-1",
                    endNodeCode = "NODE-1",
                    points = emptyList(),
                    startNodeId = "scoped-node",
                    endNodeId = "scoped-node"
                )
            )
            scopedSeed.nodeProgressDao().upsert(
                NodeProgressEntity(
                    id = "progress-remap",
                    projectId = project.id,
                    planned = 10f,
                    actual = 5f,
                    remain = 5f,
                    delayed = false,
                    updatedAtEpochMs = 100L,
                    nodeId = "scoped-node"
                )
            )
            scopedSeed.dailyLogDao().upsert(
                DailyLogEntity(
                    id = "daily-remap",
                    projectId = project.id,
                    workItem = "Inspect",
                    manpower = 1,
                    note = "note",
                    createdAtEpochMs = 200L,
                    weather = "",
                    temperature = 20.0,
                    dateEpochDay = 20240703L,
                    volume = 1.0,
                    unit = "m",
                    categoryName = "",
                    batchGroupId = "",
                    photoMatchOffsetMinutes = 0,
                    nodeId = "scoped-node",
                    routeId = "scoped-route"
                )
            )
            scopedSeed.noteDao().insert(
                NoteEntity(
                    id = "note-remap",
                    projectId = project.id,
                    content = "hello",
                    createdAtEpochMs = 300L,
                    objectNodeId = "scoped-node",
                    objectRouteId = "scoped-route"
                )
            )
            scopedSeed.taskDao().upsert(
                TaskEntity(
                    id = "task-remap",
                    projectId = project.id,
                    title = "Task",
                    description = "desc",
                    status = "TODO",
                    createdAtEpochMs = 400L,
                    objectNodeId = "scoped-node",
                    objectRouteId = "scoped-route"
                )
            )
            scopedSeed.sitePhotoDao().upsert(
                SitePhotoEntity(
                    id = "photo-remap",
                    projectId = project.id,
                    objectCode = "ROUTE-1",
                    tagCodesCsv = "",
                    filePath = "/tmp/photo.jpg",
                    thumbnailPath = "/tmp/photo.jpg",
                    latitude = null,
                    longitude = null,
                    locationAccuracyM = null,
                    isGpsMocked = false,
                    locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
                    engineer = "Field",
                    capturedAtEpochMs = 500L,
                    matchedAtEpochMs = 500L,
                    matchingTimeOffsetMs = 0L,
                    matchedNodeId = "scoped-node",
                    matchedRouteId = "scoped-route"
                )
            )
            scopedSeed.workPlanDao().insert(
                WorkPlanEntity(
                    id = "plan-remap",
                    projectId = project.id,
                    title = "Plan",
                    description = "desc",
                    plannedDateEpochDay = 20240703L,
                    nodeCode = "NODE-1",
                    routeCode = "ROUTE-1",
                    taskId = null,
                    sourceRawInput = "raw",
                    createdAtEpochMs = 600L,
                    quantity = 1.0,
                    unit = "m",
                    batchGroupId = "batch",
                    nodeId = "scoped-node",
                    routeId = "scoped-route"
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertEquals("shared-node", sharedDatabase.nodeProgressDao().byProject(project.id).single().nodeId)
        with(sharedDatabase.dailyLogDao().byProject(project.id).single()) {
            assertEquals("shared-node", nodeId)
            assertEquals("shared-route", routeId)
        }
        with(sharedDatabase.noteDao().byProject(project.id).single()) {
            assertEquals("shared-node", objectNodeId)
            assertEquals("shared-route", objectRouteId)
        }
        with(sharedDatabase.taskDao().byProject(project.id).single()) {
            assertEquals("shared-node", objectNodeId)
            assertEquals("shared-route", objectRouteId)
        }
        with(sharedDatabase.sitePhotoDao().byProject(project.id).single()) {
            assertEquals("shared-node", matchedNodeId)
            assertEquals("shared-route", matchedRouteId)
        }
    }

    @Test
    fun `databaseFor heals legacy public project db path to scoped private path`() = runBlocking {
        val legacyFile = File(tempDir, "legacy-public/project.sqlite")
        val project = projectEntity("project-heal", legacyFile)
        sharedDatabase.projectDao().upsert(project)

        createDatabase(legacyFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.importedFileDao().upsert(
                ImportedFileEntity(
                    id = "file-heal",
                    projectId = project.id,
                    fileName = "nodes.kml",
                    fileType = "KML",
                    storedPath = "/tmp/nodes.kml",
                    summary = "seed",
                    importedAtEpochMs = 100L
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val healedPath = storageManager.scopedProjectDbFile(project.slug).absolutePath
        assertEquals(healedPath, sharedDatabase.projectDao().get(project.id)?.projectDbPath)
        assertTrue(File(healedPath).exists())
        assertEquals(1, scopedDatabase.importedFileDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.importedFileDao().byProject(project.id).size)
    }

    @Test
    fun `databaseFor hydrates auxiliary tables asynchronously after core seed`() = runBlocking {
        val project = projectEntity("project-aux", File(tempDir, "staged/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.importedFileDao().upsert(
            ImportedFileEntity(
                id = "file-aux",
                projectId = project.id,
                fileName = "nodes.kml",
                fileType = "KML",
                storedPath = "/tmp/nodes.kml",
                summary = "seed",
                importedAtEpochMs = 100L
            )
        )
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "node-aux",
                projectId = project.id,
                code = "N-AUX",
                contractor = "Nam Ky",
                latitude = 21.028,
                longitude = 105.854,
                mapNumberLabel = "229",
                workVolumeSummary = "Cable",
                importedFileId = "file-aux"
            )
        )
        sharedDatabase.workVolumeProgressDao().upsert(
            MaterialProgressEntity(
                id = "progress-aux",
                projectId = project.id,
                nodeCode = "N-AUX",
                materialName = "Cable",
                plannedQty = 10f,
                actualQty = 2f,
                updatedAtEpochMs = 200L,
                unit = "m"
            )
        )
        sharedDatabase.dailyLogDao().upsert(
            com.mapsupervision.data.db.entity.DailyLogEntity(
                id = "daily-aux",
                projectId = project.id,
                workItem = "Inspect",
                manpower = 1,
                note = "core",
                createdAtEpochMs = 300L,
                weather = "",
                temperature = 20.0,
                dateEpochDay = 20240621L,
                volume = 1.0,
                unit = "m",
                categoryName = "",
                batchGroupId = "",
                photoMatchOffsetMinutes = 0,
                nodeId = "node-aux",
                routeId = null
            )
        )
        sharedDatabase.workCategoryDao().upsert(
            WorkCategoryEntity(
                id = "category-aux",
                projectId = project.id,
                name = "Cable",
                unit = "m",
                createdAtEpochMs = 400L
            )
        )
        sharedDatabase.noteDao().insert(
            NoteEntity(
                id = "note-aux",
                projectId = project.id,
                objectNodeId = "node-aux",
                content = "aux note",
                createdAtEpochMs = 500L
            )
        )
        sharedDatabase.sitePhotoDao().upsert(
            SitePhotoEntity(
                id = "photo-aux",
                projectId = project.id,
                objectCode = "NODE-AUX",
                tagCodesCsv = "",
                matchedNodeId = "node-aux",
                matchedRouteId = null,
                filePath = "/tmp/photo.jpg",
                thumbnailPath = "/tmp/photo-thumb.jpg",
                latitude = null,
                longitude = null,
                locationAccuracyM = null,
                isGpsMocked = false,
                locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
                engineer = "Field",
                capturedAtEpochMs = 600L,
                matchedAtEpochMs = 0L,
                matchingTimeOffsetMs = 0L
            )
        )
        sharedDatabase.materialDeclarationDao().insert(
            MaterialDeclarationEntity(
                id = "declaration-aux",
                projectId = project.id,
                workName = "Cable",
                materialName = "Cable drum",
                ratio = 1f,
                unit = "m",
                createdAtEpochMs = 700L,
                workCategoryId = "category-aux"
            )
        )
        sharedDatabase.materialHandoverDao().upsert(
            MaterialHandoverEntity(
                id = "handover-aux",
                projectId = project.id,
                nodeCode = "N-AUX",
                nodeId = "node-aux",
                workName = "Cable",
                materialName = "Cable drum",
                contractor = "Nam Ky",
                quantity = 2f,
                unit = "m",
                handoverDateEpochDay = 20240621L,
                note = "handover",
                createdAtEpochMs = 800L,
                materialDeclarationId = "declaration-aux",
                workCategoryId = "category-aux"
            )
        )

        println("DEBUG TEST: notes in sharedDatabase: ${sharedDatabase.noteDao().byProject(project.id).size}")
        println("DEBUG TEST: photos in sharedDatabase: ${sharedDatabase.sitePhotoDao().byProject(project.id).size}")

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertEquals(1, scopedDatabase.importedFileDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.gisNodeDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.workVolumeProgressDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.dailyLogDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.workCategoryDao().byProject(project.id).size)
        assertEquals(0, scopedDatabase.noteDao().byProject(project.id).size)
        assertEquals(0, scopedDatabase.sitePhotoDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.materialDeclarationDao().getByProject(project.id).size)
        assertEquals(1, scopedDatabase.materialHandoverDao().byProject(project.id).size)

        val startTime = System.currentTimeMillis()
        while (scopedDatabase.noteDao().byProject(project.id).isEmpty() && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
        }

        assertEquals(1, scopedDatabase.noteDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.sitePhotoDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.materialDeclarationDao().getByProject(project.id).size)
        assertEquals(1, scopedDatabase.materialHandoverDao().byProject(project.id).size)
    }

    @Test
    fun `databaseFor skips seed when scoped db already has project data`() = runBlocking {
        val scopedFile = File(tempDir, "existing/project.sqlite")
        val project = projectEntity("project-2", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "shared-node",
                projectId = project.id,
                code = "SHARED",
                contractor = "Shared",
                latitude = 21.0,
                longitude = 105.0,
                mapNumberLabel = "1",
                workVolumeSummary = ""
            )
        )

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-node",
                    projectId = project.id,
                    code = "SCOPED",
                    contractor = "Scoped",
                    latitude = 20.0,
                    longitude = 106.0,
                    mapNumberLabel = "2",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val nodes = scopedDatabase.gisNodeDao().byProject(project.id)
        assertEquals(1, nodes.size)
        assertEquals("SCOPED", nodes.single().code)
    }

    @Test
    fun `databaseFor ensures scoped project row exists before photo inserts`() = runBlocking {
        val scopedFile = File(tempDir, "orphaned/project.sqlite")
        val project = projectEntity("project-4", scopedFile)
        sharedDatabase.projectDao().upsert(project)

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "orphan-node",
                    projectId = project.id,
                    code = "ORPHAN",
                    contractor = "Scoped",
                    latitude = 20.0,
                    longitude = 106.0,
                    mapNumberLabel = "2",
                    workVolumeSummary = ""
                )
            )
            scopedSeed.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
            scopedSeed.openHelper.writableDatabase.execSQL("DELETE FROM projects WHERE id = '${project.id}'")
            scopedSeed.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertNotNull(scopedDatabase.projectDao().get(project.id))
        assertTrue(scopedDatabase.gisNodeDao().byProject(project.id).any { it.code == "ORPHAN" })
    }

    @Test
    fun `databaseFor skips seed when shared db has no legacy project data`() = runBlocking {
        val project = projectEntity("project-3", File(tempDir, "empty/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertNull(scopedDatabase.gisNodeDao().findByCode(project.id, "N-1"))
        assertEquals(0, scopedDatabase.gisRouteDao().byProject(project.id).size)
        assertEquals(0, scopedDatabase.importedFileDao().byProject(project.id).size)
    }

    @Test
    fun `databaseFor migrates legacy scoped db from version 24 to 46`() = runBlocking {
        val scopedFile = File(tempDir, "legacy-v24/project.sqlite")
        val project = projectEntity("project-legacy-24", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        createLegacyVersion24Database(scopedFile)

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val version = scopedDatabase.openHelper.readableDatabase.version
        assertEquals(46, version)
        scopedDatabase.openHelper.readableDatabase.query("PRAGMA table_info(`work_plan`)").use { cursor ->
            var found = false
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "plannedDateEpochDay") {
                    found = true
                    break
                }
            }
            assertEquals(true, found)
        }
        assertTrue(tableExists(scopedDatabase, "material_handover"))
        assertTrue(tableExists(scopedDatabase, "material_declaration"))
        assertTrue(tableExists(scopedDatabase, "rag_document_embedding"))
    }

    @Test
    fun `databaseFor migrates legacy scoped db from version 23 to 46`() = runBlocking {
        val scopedFile = File(tempDir, "legacy-v23/project.sqlite")
        val project = projectEntity("project-legacy-23", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        createLegacyDatabaseFromSchema(scopedFile, 23)

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val version = scopedDatabase.openHelper.readableDatabase.version
        assertEquals(46, version)
        assertTrue(tableExists(scopedDatabase, "material_handover"))
        assertTrue(tableExists(scopedDatabase, "material_declaration"))
        assertTrue(tableExists(scopedDatabase, "rag_document_embedding"))
    }

    @Test
    fun `cleanup scheduler only runs while scoped holders exist`() = runBlocking {
        val scopedFile = File(tempDir, "scheduler/project.sqlite")
        val project = projectEntity("project-scheduler", scopedFile)
        sharedDatabase.projectDao().upsert(project)

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        assertEquals(false, provider.isCleanupSchedulerRunningForTest())

        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!
        assertEquals(true, provider.isCleanupSchedulerRunningForTest())

        provider.markAllDatabasesIdleForTest(lastAccessEpochMs = 0L)
        val hasOpenHolders = provider.runIdleCleanupForTest(nowEpochMs = 10 * 60 * 1000L)

        assertEquals(false, hasOpenHolders)
        assertEquals(false, provider.isCleanupSchedulerRunningForTest())
    }

    @Test
    fun `databaseFor reuses existing scoped holder for repeated access`() = runBlocking {
        val scopedFile = File(tempDir, "holder-reuse/project.sqlite")
        val project = projectEntity("project-holder-reuse", scopedFile)
        sharedDatabase.projectDao().upsert(project)

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        val firstDatabase = provider.databaseFor(project.id)
        val secondDatabase = provider.databaseFor(project.id)

        openedDatabases += firstDatabase!!
        assertSame(firstDatabase, secondDatabase)
    }

    private fun createDatabase(file: File): MapSupervisionDatabase {
        file.parentFile?.mkdirs()
        return Room.databaseBuilder(context, MapSupervisionDatabase::class.java, file.absolutePath)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private fun createLegacyVersion24Database(dbFile: File) {
        createLegacyDatabaseFromSchema(dbFile, 24)
    }

    private fun createLegacyDatabaseFromSchema(
        dbFile: File,
        schemaVersion: Int,
        dbVersion: Int = schemaVersion
    ) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(schemaVersion)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)

                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(indexSql)
                    }
                }
            }
            db.setVersion(dbVersion)
        }
    }

    private fun loadSchema(version: Int): JSONObject {
        val candidates = listOf(
            File("data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json"),
            File("../data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json"),
            File("schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
        )
        val schemaFile = candidates.firstOrNull { it.exists() }
            ?: error("Missing Room schema file for version $version")
        return JSONObject(schemaFile.readText(Charsets.UTF_8))
    }

    private fun tableExists(database: MapSupervisionDatabase, tableName: String): Boolean {
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun projectEntity(projectId: String, scopedFile: File) = ProjectEntity(
        id = projectId,
        name = projectId,
        slug = projectId,
        isArchived = false,
        createdAtEpochMs = 1L,
        storageMode = ProjectStorageMode.PROJECT_DB,
        projectDbPath = scopedFile.absolutePath
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

