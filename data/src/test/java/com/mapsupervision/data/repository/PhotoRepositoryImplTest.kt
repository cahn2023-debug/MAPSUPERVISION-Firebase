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
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.MediaType
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PhotoRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var provider: ProjectScopedDatabaseProvider
    private lateinit var repository: PhotoRepositoryImpl
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("photo-repository-test").toFile()
        storageManager = object : ProjectStorageManager(context) {
            override fun scopedProjectDbRootDirectory(projectSlug: String): File {
                return File(tempDir, "scoped-private/$projectSlug")
            }
        }
        sharedDatabase = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        repository = PhotoRepositoryImpl(sharedDatabase.sitePhotoDao(), provider)
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `add for node persists matched node id and reads back by object code immediately`() = runBlocking {
        val project = projectEntity("project-node", File(tempDir, "node/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "node-1",
                projectId = project.id,
                code = "NODE-1",
                contractor = "TNP",
                latitude = 21.028,
                longitude = 105.854,
                mapNumberLabel = "MN-1",
                workVolumeSummary = ""
            )
        )

        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val result = repository.add(
            sitePhoto(
                projectId = project.id,
                objectCode = "NODE-1",
                matchedNodeCode = "NODE-1"
            )
        )

        assertTrue(result is AppResult.Success)

        val rows = scopedDatabase.sitePhotoDao().byProject(project.id)
        assertEquals(1, rows.size)
        assertEquals("NODE-1", rows.single().objectCode)
        assertEquals("node-1", rows.single().matchedNodeId)
        assertNull(rows.single().matchedRouteId)

        val byObject = repository.byObjectCode(project.id, "NODE-1")
        assertTrue(byObject is AppResult.Success)
        val photo = (byObject as AppResult.Success).data.single()
        assertEquals("NODE-1", photo.objectCode)
        assertEquals("node-1", photo.matchedNodeId)
        assertNull(photo.matchedRouteId)
    }

    @Test
    fun `add for route persists matched route id and reads back by object code immediately`() = runBlocking {
        val project = projectEntity("project-route", File(tempDir, "route/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.gisRouteDao().upsert(
            GisRouteEntity(
                id = "route-1",
                projectId = project.id,
                code = "ROUTE-1",
                contractor = "TNP",
                startNodeCode = "NODE-A",
                endNodeCode = "NODE-B",
                points = listOf(21.028 to 105.854, 21.029 to 105.855)
            )
        )

        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val result = repository.add(
            sitePhoto(
                projectId = project.id,
                objectCode = "ROUTE-1",
                matchedRouteCode = "ROUTE-1",
                mediaType = MediaType.VIDEO,
                mimeType = "video/mp4",
                durationMs = 5_000L
            )
        )

        assertTrue(result is AppResult.Success)

        val rows = scopedDatabase.sitePhotoDao().byProject(project.id)
        assertEquals(1, rows.size)
        assertEquals("ROUTE-1", rows.single().objectCode)
        assertNull(rows.single().matchedNodeId)
        assertEquals("route-1", rows.single().matchedRouteId)

        val byObject = repository.byObjectCode(project.id, "ROUTE-1")
        assertTrue(byObject is AppResult.Success)
        val photo = (byObject as AppResult.Success).data.single()
        assertEquals("ROUTE-1", photo.objectCode)
        assertNull(photo.matchedNodeId)
        assertEquals("route-1", photo.matchedRouteId)
        assertEquals(MediaType.VIDEO, photo.mediaType)
        assertEquals(5_000L, photo.durationMs)
        assertNotNull(photo.thumbnailPath)
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

    private fun sitePhoto(
        projectId: String,
        objectCode: String,
        matchedNodeCode: String? = null,
        matchedRouteCode: String? = null,
        mediaType: MediaType = MediaType.IMAGE,
        mimeType: String = "image/jpeg",
        durationMs: Long = 0L
    ) = SitePhoto(
        id = "photo-$objectCode-$mediaType",
        projectId = projectId,
        objectCode = objectCode,
        matchedNodeCode = matchedNodeCode,
        matchedRouteCode = matchedRouteCode,
        filePath = "D:/captures/$objectCode.${if (mediaType == MediaType.VIDEO) "mp4" else "jpg"}",
        thumbnailPath = "D:/captures/$objectCode-thumb.jpg",
        latitude = 21.028,
        longitude = 105.854,
        locationAccuracyM = 3f,
        isGpsMocked = false,
        locationStatus = PhotoLocationStatus.OK,
        engineer = "Field",
        capturedAtEpochMs = 1_000L,
        mediaType = mediaType,
        mimeType = mimeType,
        durationMs = durationMs,
        syncStatus = SitePhotoSyncStatus.PENDING
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
