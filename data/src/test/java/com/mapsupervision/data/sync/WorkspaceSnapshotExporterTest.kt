package com.mapsupervision.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.storage.ProjectStorageManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WorkspaceSnapshotExporterTest {
    private lateinit var context: Context
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var scopedDatabaseProvider: ProjectScopedDatabaseProvider
    private lateinit var exporter: WorkspaceSnapshotExporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sharedDatabase = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageManager = ProjectStorageManager(context)
        scopedDatabaseProvider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        exporter = WorkspaceSnapshotExporter(sharedDatabase, scopedDatabaseProvider)
    }

    @After
    fun tearDown() {
        sharedDatabase.close()
    }

    @Test
    fun exportProjectSnapshotJson_packagesProjectAndPublicCollections() = runBlocking {
        val projectId = "project-269"
        val project = ProjectEntity(
            id = projectId,
            name = "Dự án 269 - 2026",
            slug = "269-2026",
            isArchived = false,
            createdAtEpochMs = 1000L,
            metadataVersion = 3,
            updatedAtEpochMs = 2000L,
            storageMode = ProjectStorageMode.LEGACY_SHARED,
            projectDbPath = "",
            mediaStorageProvider = "GOOGLE_DRIVE",
            mediaStorageFolderId = "drive-folder-269",
            mediaStorageFolderUrl = "https://drive.google.com/folders/269",
            mediaStorageUpdatedAtEpochMs = 2000L,
            isDeleted = false,
            deletedAtEpochMs = null,
            cloudDataConfirmed = true
        )
        sharedDatabase.projectDao().upsert(project)

        val node = GisNodeEntity(
            id = "node-1",
            projectId = projectId,
            code = "N01",
            contractor = "NhaThauA",
            latitude = 10.77,
            longitude = 106.69,
            mapNumberLabel = "T01",
            workVolumeSummary = "100m",
            updatedAtEpochMs = 2100L,
            isDeleted = false
        )
        sharedDatabase.gisNodeDao().upsert(node)

        val log = DailyLogEntity(
            id = "log-1",
            projectId = projectId,
            workItem = "Kiem tra cot cap",
            manpower = 5,
            note = "Tot",
            createdAtEpochMs = 2200L,
            weather = "Nang",
            temperature = 32.0,
            dateEpochDay = 20260826L,
            volume = 10.0,
            unit = "m",
            categoryName = "Cap quang",
            batchGroupId = "batch-1",
            photoMatchOffsetMinutes = 0,
            updatedAtEpochMs = 2200L,
            isDeleted = false
        )
        sharedDatabase.dailyLogDao().upsert(log)

        val jsonStr = exporter.exportProjectSnapshotJson(projectId)
        assertNotNull(jsonStr)

        val root = JSONObject(jsonStr!!)
        assertTrue(root.has("project"))
        assertTrue(root.has("collections"))
        assertTrue(root.has("updatedAtEpochMs"))

        val projObj = root.getJSONObject("project")
        assertEquals(projectId, projObj.getString("id"))
        assertEquals("Dự án 269 - 2026", projObj.getString("name"))

        val cols = root.getJSONObject("collections")
        assertTrue(cols.has("gis_node"))
        assertTrue(cols.has("daily_log"))

        val nodesArr = cols.getJSONArray("gis_node")
        assertEquals(1, nodesArr.length())
        assertEquals("N01", nodesArr.getJSONObject(0).getString("code"))

        val logsArr = cols.getJSONArray("daily_log")
        assertEquals(1, logsArr.length())
        assertEquals("Kiem tra cot cap", logsArr.getJSONObject(0).getString("workItem"))
    }
}
