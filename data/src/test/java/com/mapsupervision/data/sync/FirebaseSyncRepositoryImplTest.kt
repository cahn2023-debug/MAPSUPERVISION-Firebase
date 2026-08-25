package com.mapsupervision.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.ProjectDeletionSqlGuards
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.EventOutboxEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import com.mapsupervision.domain.model.MediaType
import com.mapsupervision.storage.ProjectStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FirebaseSyncRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var scopedDatabaseProvider: ProjectScopedDatabaseProvider
    private lateinit var repository: FirebaseSyncRepositoryImpl
    private lateinit var fakeClient: TestDriveMediaUploadClient
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = Files.createTempDirectory("sync-repo-test").toFile()
        
        sharedDatabase = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            
        storageManager = ProjectStorageManager(context)
        scopedDatabaseProvider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        
        repository = FirebaseSyncRepositoryImpl(context, sharedDatabase, scopedDatabaseProvider)
        repository.firebaseRuntime = TestFirebaseRuntime(context)
        repository.enforceAccessChecks = false
        
        fakeClient = TestDriveMediaUploadClient()
        repository.driveMediaUploadClient = fakeClient
    }

    @After
    fun tearDown() {
        sharedDatabase.close()
        tempDir.deleteRecursively()
    }

    private suspend fun insertProject(projectId: String) {
        sharedDatabase.projectDao().upsert(
            ProjectEntity(
                id = projectId,
                name = "Test Project",
                slug = "test-project",
                isArchived = false,
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L,
                storageMode = ProjectStorageMode.LEGACY_SHARED,
                projectDbPath = "",
                mediaStorageFolderId = "drive-folder-123",
                mediaStorageFolderUrl = "https://drive.google.com/drive/folders/drive-folder-123"
            )
        )
    }

    @Test
    fun uploadPendingMedia_withPendingPhoto_uploadsAndUpdatesRemoteUrl() = runBlocking {
        val projectId = "proj-1"
        insertProject(projectId)

        val photoFile = File(tempDir, "original.jpg").apply { writeText("original content") }
        val thumbFile = File(tempDir, "thumb.jpg").apply { writeText("thumb content") }

        val photo = SitePhotoEntity(
            id = "photo-1",
            projectId = projectId,
            objectCode = "NODE-1",
            tagCodesCsv = "",
            filePath = photoFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            latitude = 21.0,
            longitude = 105.0,
            locationAccuracyM = 5.0f,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.OK,
            engineer = "Engineer 1",
            capturedAtEpochMs = 2000L,
            matchedAtEpochMs = 2050L,
            matchingTimeOffsetMs = 50L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = "Hanoi",
            captureNote = "test note",
            matchedNodeId = null,
            matchedRouteId = null,
            updatedAtEpochMs = 2000L,
            syncStatus = SitePhotoSyncStatus.PENDING,
            remoteUrl = null,
            lastSyncAttemptEpochMs = null,
            isDeleted = false,
            deletedAtEpochMs = null
        )
        sharedDatabase.sitePhotoDao().upsert(photo)

        val result = repository.uploadPendingMedia(projectId)
        assertTrue(result is com.mapsupervision.core.result.AppResult.Success)
        
        val batchResult = (result as com.mapsupervision.core.result.AppResult.Success).data
        assertEquals(1, batchResult.uploadedMedia)
        assertEquals(0, batchResult.failed)

        val updatedPhoto = sharedDatabase.sitePhotoDao().byProjectIncludingDeleted(projectId).find { it.id == "photo-1" }
        assertNotNull(updatedPhoto)
        assertEquals(SitePhotoSyncStatus.DONE, updatedPhoto!!.syncStatus)
        assertEquals("https://drive.google.com/uc?export=view&id=drive-file-1", updatedPhoto.remoteUrl)
        assertEquals(null, updatedPhoto.syncErrorMessage)
        assertNotNull(updatedPhoto.lastSyncAttemptEpochMs)

        assertNotNull(fakeClient.lastRequest)
        val req = fakeClient.lastRequest!!
        assertEquals("photo-1", req.photoId)
        assertEquals("Test Project", req.projectName)
        assertEquals("drive-folder-123", req.rootFolderId)
        assertEquals(DriveMediaObjectType.NODE, req.objectType)
        assertEquals("NODE-1", req.objectCode)
        assertEquals(MediaType.IMAGE, req.mediaType)
        assertEquals("Hanoi", req.address)
        assertEquals("test note", req.captureNote)
    }

    @Test
    fun uploadPendingMedia_skipsExistingDonePhotoWithRemoteUrl() = runBlocking {
        val projectId = "proj-1"
        insertProject(projectId)

        val photo = SitePhotoEntity(
            id = "photo-1",
            projectId = projectId,
            objectCode = "NODE-1",
            tagCodesCsv = "",
            filePath = File(tempDir, "photo.jpg").apply { writeText("original") }.absolutePath,
            thumbnailPath = "",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
            engineer = "Engineer 1",
            capturedAtEpochMs = 2000L,
            matchedAtEpochMs = 0L,
            matchingTimeOffsetMs = 0L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = null,
            captureNote = null,
            matchedNodeId = null,
            matchedRouteId = null,
            updatedAtEpochMs = 2000L,
            syncStatus = SitePhotoSyncStatus.DONE,
            remoteUrl = "https://already-uploaded.com",
            lastSyncAttemptEpochMs = 2100L,
            isDeleted = false,
            deletedAtEpochMs = null
        )
        sharedDatabase.sitePhotoDao().upsert(photo)

        val result = repository.uploadPendingMedia(projectId)
        assertTrue(result is com.mapsupervision.core.result.AppResult.Success)
        
        val batchResult = (result as com.mapsupervision.core.result.AppResult.Success).data
        assertEquals(0, batchResult.uploadedMedia)
        assertEquals(0, batchResult.failed)
        assertTrue(fakeClient.lastRequest == null)
    }

    @Test
    fun uploadPendingMedia_rejectsDeletedProjectBeforeTouchingLocalRows() = runBlocking {
        val projectId = "proj-deleted"
        insertProject(projectId)
        sharedDatabase.projectDao().markRemoteDeletion(
            projectId = projectId,
            requestId = "delete-request-1",
            completedAtEpochMs = 3_000L,
            updatedAtEpochMs = 3_000L
        )

        val result = repository.uploadPendingMedia(projectId)

        assertTrue(result is com.mapsupervision.core.result.AppResult.Error)
        assertTrue((result as com.mapsupervision.core.result.AppResult.Error).throwable.message.orEmpty().contains("locked", ignoreCase = true))
        assertTrue(fakeClient.lastRequest == null)
    }

    @Test
    fun sqliteDeletionGuard_rejectsBusinessWritesForDeletedProject() = runBlocking {
        val projectId = "proj-guarded"
        insertProject(projectId)
        ProjectDeletionSqlGuards.install(sharedDatabase.openHelper.writableDatabase)
        sharedDatabase.projectDao().markRemoteDeletion(projectId, "request-guarded", 3_000L, 3_000L)

        val failure = runCatching {
            sharedDatabase.eventOutboxDao().upsert(
                EventOutboxEntity(
                    id = "event-guarded",
                    projectId = projectId,
                    eventType = "project.updated",
                    payloadJson = "{}"
                )
            )
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("locked", ignoreCase = true))
    }

    @Test
    fun uploadPendingMedia_onApiFailure_marksFailedAndPreservesFilePath() = runBlocking {
        val projectId = "proj-1"
        insertProject(projectId)
        fakeClient.shouldFail = true

        val localPath = File(tempDir, "photo.jpg").apply { writeText("original") }.absolutePath
        val photo = SitePhotoEntity(
            id = "photo-fail",
            projectId = projectId,
            objectCode = "NODE-1",
            tagCodesCsv = "",
            filePath = localPath,
            thumbnailPath = "",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
            engineer = "Engineer 1",
            capturedAtEpochMs = 2000L,
            matchedAtEpochMs = 0L,
            matchingTimeOffsetMs = 0L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = null,
            captureNote = null,
            matchedNodeId = null,
            matchedRouteId = null,
            updatedAtEpochMs = 2000L,
            syncStatus = SitePhotoSyncStatus.PENDING,
            remoteUrl = null,
            lastSyncAttemptEpochMs = null,
            isDeleted = false,
            deletedAtEpochMs = null
        )
        sharedDatabase.sitePhotoDao().upsert(photo)

        val result = repository.uploadPendingMedia(projectId)
        assertTrue(result is com.mapsupervision.core.result.AppResult.Success)
        
        val batchResult = (result as com.mapsupervision.core.result.AppResult.Success).data
        assertEquals(0, batchResult.uploadedMedia)
        assertEquals(1, batchResult.failed)

        val updatedPhoto = sharedDatabase.sitePhotoDao().byProjectIncludingDeleted(projectId).find { it.id == "photo-fail" }
        assertNotNull(updatedPhoto)
        assertEquals(SitePhotoSyncStatus.FAILED, updatedPhoto!!.syncStatus)
        assertTrue(updatedPhoto.syncErrorMessage?.contains("Simulated API upload failure") == true)
        assertEquals(localPath, updatedPhoto.filePath)
    }

    @Test
    fun uploadPendingMedia_resolvesObjectTypeCorrectly() = runBlocking {
        val projectId = "proj-1"
        insertProject(projectId)

        sharedDatabase.gisRouteDao().upsert(
            GisRouteEntity(
                id = "route-1",
                projectId = projectId,
                code = "ROUTE-ABC",
                contractor = "Contractor 1",
                startNodeCode = "N-1",
                endNodeCode = "N-2",
                points = emptyList(),
                importedFileId = null,
                startNodeId = null,
                endNodeId = null
            )
        )

        val photoFile = File(tempDir, "photo.jpg").apply { writeText("original") }
        
        val photoA = SitePhotoEntity(
            id = "photo-a",
            projectId = projectId,
            objectCode = "SOME-CODE",
            tagCodesCsv = "",
            filePath = photoFile.absolutePath,
            thumbnailPath = "",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
            engineer = "Engineer 1",
            capturedAtEpochMs = 2000L,
            matchedAtEpochMs = 0L,
            matchingTimeOffsetMs = 0L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = null,
            captureNote = null,
            matchedNodeId = null,
            matchedRouteId = "route-1",
            updatedAtEpochMs = 2000L,
            syncStatus = SitePhotoSyncStatus.PENDING,
            remoteUrl = null,
            lastSyncAttemptEpochMs = null,
            isDeleted = false,
            deletedAtEpochMs = null
        )
        
        val photoB = SitePhotoEntity(
            id = "photo-b",
            projectId = projectId,
            objectCode = "route-abc",
            tagCodesCsv = "",
            filePath = photoFile.absolutePath,
            thumbnailPath = "",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
            engineer = "Engineer 1",
            capturedAtEpochMs = 2000L,
            matchedAtEpochMs = 0L,
            matchingTimeOffsetMs = 0L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = null,
            captureNote = null,
            matchedNodeId = null,
            matchedRouteId = null,
            updatedAtEpochMs = 2000L,
            syncStatus = SitePhotoSyncStatus.PENDING,
            remoteUrl = null,
            lastSyncAttemptEpochMs = null,
            isDeleted = false,
            deletedAtEpochMs = null
        )

        val photoC = SitePhotoEntity(
            id = "photo-c",
            projectId = projectId,
            objectCode = "NODE-XYZ",
            tagCodesCsv = "",
            filePath = photoFile.absolutePath,
            thumbnailPath = "",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
            engineer = "Engineer 1",
            capturedAtEpochMs = 2000L,
            matchedAtEpochMs = 0L,
            matchingTimeOffsetMs = 0L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = null,
            captureNote = null,
            matchedNodeId = null,
            matchedRouteId = null,
            updatedAtEpochMs = 2000L,
            syncStatus = SitePhotoSyncStatus.PENDING,
            remoteUrl = null,
            lastSyncAttemptEpochMs = null,
            isDeleted = false,
            deletedAtEpochMs = null
        )

        sharedDatabase.sitePhotoDao().upsert(photoA)
        repository.uploadPendingMedia(projectId)
        assertEquals(DriveMediaObjectType.ROUTE, fakeClient.lastRequest!!.objectType)

        sharedDatabase.sitePhotoDao().upsert(photoB)
        repository.uploadPendingMedia(projectId)
        assertEquals(DriveMediaObjectType.ROUTE, fakeClient.lastRequest!!.objectType)

        sharedDatabase.sitePhotoDao().upsert(photoC)
        repository.uploadPendingMedia(projectId)
        assertEquals(DriveMediaObjectType.NODE, fakeClient.lastRequest!!.objectType)
    }

    @Test
    fun mergeEnvelopeRow_projectsTableDoesNotInjectProjectIdColumn() {
        val row = mergeEnvelopeRow(
            FirebaseSyncTableCatalog.byTableName("projects"),
            com.mapsupervision.domain.repository.SyncEnvelope(
                id = "proj-1",
                projectId = "proj-1",
                tableName = "projects",
                data = mapOf(
                    "name" to "Test Project",
                    "mediaStorageFolderId" to "drive-folder-123"
                ),
                updatedAtEpochMs = 2000L,
                isDeleted = false,
                sourceDeviceId = "remote-device",
                lastSyncedAtEpochMs = 2000L
            )
        )

        assertEquals("proj-1", row["id"])
        assertEquals("drive-folder-123", row["mediaStorageFolderId"])
        assertFalse(row.containsKey("projectId"))
    }

    @Test
    fun mergeEnvelopeRow_projectChildTableKeepsProjectIdColumn() {
        val row = mergeEnvelopeRow(
            FirebaseSyncTableCatalog.byTableName("site_photos"),
            com.mapsupervision.domain.repository.SyncEnvelope(
                id = "photo-1",
                projectId = "proj-1",
                tableName = "site_photos",
                data = mapOf(
                    "objectCode" to "NODE-1"
                ),
                updatedAtEpochMs = 2000L,
                isDeleted = false,
                sourceDeviceId = "remote-device",
                lastSyncedAtEpochMs = 2000L
            )
        )

        assertEquals("photo-1", row["id"])
        assertEquals("proj-1", row["projectId"])
    }

    @Test
    fun remotePhotoWithOlderTimestampDoesNotReplaceLocalTagChange() {
        assertFalse(shouldApplyRemoteRow(localUpdatedAtEpochMs = 2_000L, remoteUpdatedAtEpochMs = 1_999L))
        assertTrue(shouldApplyRemoteRow(localUpdatedAtEpochMs = 2_000L, remoteUpdatedAtEpochMs = 2_001L))
        assertTrue(shouldApplyRemoteRow(localUpdatedAtEpochMs = null, remoteUpdatedAtEpochMs = 1L))
    }

    @Test
    fun applyRemoteRows_withExistingProjectAndChildRows_doesNotViolateForeignKeyConstraint() = runBlocking {
        val projectId = "proj-1"
        sharedDatabase.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        insertProject(projectId)

        // Insert a child node and a child photo and a photo tag referencing them
        val node = com.mapsupervision.data.db.entity.GisNodeEntity(
            id = "node-1",
            projectId = projectId,
            code = "N-1",
            contractor = "Contractor 1",
            latitude = 21.0,
            longitude = 105.0,
            mapNumberLabel = "",
            workVolumeSummary = "",
            updatedAtEpochMs = 1000L
        )
        sharedDatabase.gisNodeDao().upsert(node)

        val photo = SitePhotoEntity(
            id = "photo-1",
            projectId = projectId,
            objectCode = "N-1",
            tagCodesCsv = "",
            filePath = "photo.jpg",
            thumbnailPath = "",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
            engineer = "Engineer 1",
            capturedAtEpochMs = 1000L,
            matchedAtEpochMs = 0L,
            matchingTimeOffsetMs = 0L,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            durationMs = 0,
            address = null,
            captureNote = null,
            matchedNodeId = "node-1",
            matchedRouteId = null,
            updatedAtEpochMs = 1000L,
            syncStatus = SitePhotoSyncStatus.DONE,
            remoteUrl = "https://photo.url",
            lastSyncAttemptEpochMs = null,
            isDeleted = false,
            deletedAtEpochMs = null
        )
        sharedDatabase.sitePhotoDao().upsert(photo)

        val photoTag = com.mapsupervision.data.db.entity.PhotoTagEntity(
            id = "tag-1",
            projectId = projectId,
            photoId = "photo-1",
            tagCode = "TAG_OK",
            createdAtEpochMs = 1000L
        )
        sharedDatabase.photoTagDao().upsertAll(listOf(photoTag))

        // Remote project update envelope
        val projectEnvelope: com.mapsupervision.domain.repository.SyncEnvelope<Map<String, Any?>> = com.mapsupervision.domain.repository.SyncEnvelope(
            id = projectId,
            projectId = projectId,
            tableName = "projects",
            data = mapOf<String, Any?>(
                "name" to "Updated Project Name",
                "slug" to "test-project",
                "isArchived" to false,
                "createdAtEpochMs" to 1000L,
                "updatedAtEpochMs" to 5000L,
                "storageMode" to "LEGACY_SHARED",
                "projectDbPath" to ""
            ),
            updatedAtEpochMs = 5000L,
            isDeleted = false,
            sourceDeviceId = "remote-device",
            lastSyncedAtEpochMs = 5000L
        )

        val projectTable = FirebaseSyncTableCatalog.byTableName("projects")
        val appliedProjects = repository.applyRemoteRowsForTest(projectId, projectTable, listOf(projectEnvelope))
        assertEquals(1, appliedProjects)

        val updatedProject = sharedDatabase.projectDao().get(projectId)
        assertNotNull(updatedProject)
        assertEquals("Updated Project Name", updatedProject!!.name)

        // Remote site_photo update envelope (parent of photoTag with NO_ACTION onDelete)
        val photoEnvelope: com.mapsupervision.domain.repository.SyncEnvelope<Map<String, Any?>> = com.mapsupervision.domain.repository.SyncEnvelope(
            id = "photo-1",
            projectId = projectId,
            tableName = "site_photos",
            data = mapOf<String, Any?>(
                "objectCode" to "N-1",
                "tagCodesCsv" to "TAG_OK",
                "filePath" to "photo.jpg",
                "thumbnailPath" to "",
                "isGpsMocked" to false,
                "locationStatus" to "MISSING",
                "engineer" to "Engineer 1",
                "capturedAtEpochMs" to 1000L,
                "matchedAtEpochMs" to 0L,
                "matchingTimeOffsetMs" to 0L,
                "mediaType" to "IMAGE",
                "mimeType" to "image/jpeg",
                "durationMs" to 0L,
                "matchedNodeId" to "node-1",
                "updatedAtEpochMs" to 6000L,
                "syncStatus" to "DONE",
                "remoteUrl" to "https://photo-updated.url",
                "isDeleted" to false
            ),
            updatedAtEpochMs = 6000L,
            isDeleted = false,
            sourceDeviceId = "remote-device",
            lastSyncedAtEpochMs = 6000L
        )

        val photoTable = FirebaseSyncTableCatalog.byTableName("site_photos")
        val appliedPhotos = repository.applyRemoteRowsForTest(projectId, photoTable, listOf(photoEnvelope))
        assertEquals(1, appliedPhotos)

        val updatedPhoto = sharedDatabase.sitePhotoDao().byProjectIncludingDeleted(projectId).find { it.id == "photo-1" }
        assertNotNull(updatedPhoto)
        assertEquals("https://photo-updated.url", updatedPhoto!!.remoteUrl)
    }

    private class TestFirebaseRuntime(context: Context) : FirebaseRuntime(context) {
        override fun authConfigured(): Boolean = true
        override suspend fun getFirebaseToken(): String = "test-token"
    }

    private class TestDriveMediaUploadClient : DriveMediaUploadClient() {
        var lastRequest: DriveMediaUploadRequest? = null
        var uploadResultUrl: String = "https://drive.google.com/uc?export=view&id=drive-file-1"
        var shouldFail: Boolean = false

        override fun upload(baseUrl: String, request: DriveMediaUploadRequest): String {
            if (shouldFail) {
                error("Simulated API upload failure")
            }
            lastRequest = request
            return uploadResultUrl
        }
    }
}
