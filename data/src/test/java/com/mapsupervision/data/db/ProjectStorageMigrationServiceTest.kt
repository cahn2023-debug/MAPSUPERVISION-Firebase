package com.mapsupervision.data.db

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProjectStorageMigrationServiceTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var migrationService: ProjectStorageMigrationServiceImpl

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("storage-migration-test").toFile()
        sharedDatabase = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, File(tempDir, "shared.sqlite").absolutePath)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        storageManager = object : ProjectStorageManager(context) {
            override fun privateProjectRootDirectory(projectSlug: String): File {
                return File(tempDir, "private/Projects/$projectSlug")
            }
            override fun projectRootDirectory(projectSlug: String): File {
                return File(tempDir, "public/Projects/$projectSlug")
            }
            override fun scopedProjectDbRootDirectory(projectSlug: String): File {
                return File(tempDir, "scoped-private/$projectSlug")
            }
            override fun privateProjectRoot(projectSlug: String): File {
                return privateProjectRootDirectory(projectSlug).apply { mkdirs() }
            }
            override fun projectRoot(projectSlug: String): File {
                return projectRootDirectory(projectSlug).apply { mkdirs() }
            }
        }
        migrationService = ProjectStorageMigrationServiceImpl(context, sharedDatabase, storageManager)
    }

    @After
    fun tearDown() {
        sharedDatabase.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testMigrateProject_copiesFilesAndUpdatesPaths() = runBlocking {
        val projectId = "proj-1"
        val slug = "proj-slug"
        
        val oldRoot = storageManager.privateProjectRoot(slug)
        val newRoot = storageManager.projectRoot(slug)

        // Seed old database and files
        val oldDbFile = File(oldRoot, "db/project.sqlite")
        oldDbFile.parentFile?.mkdirs()
        
        val projectDb = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, oldDbFile.absolutePath)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            
        val project = ProjectEntity(
            id = projectId,
            name = "Test Project",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = oldDbFile.absolutePath
        )
        
        sharedDatabase.projectDao().upsert(project)
        projectDb.projectDao().upsert(project)

        val node = GisNodeEntity(
            id = "node-1",
            projectId = projectId,
            code = "N-01",
            contractor = "Contractor",
            latitude = 0.0,
            longitude = 0.0,
            mapNumberLabel = "N-01",
            workVolumeSummary = ""
        )
        sharedDatabase.gisNodeDao().upsert(node)
        projectDb.gisNodeDao().upsert(node)

        sharedDatabase.importedFileDao().upsert(
            ImportedFileEntity(
                id = "file-1",
                projectId = projectId,
                fileName = "data.kml",
                fileType = "kml",
                storedPath = File(oldRoot, "imports/data.kml").absolutePath,
                summary = "test",
                importedAtEpochMs = 150L
            )
        )
        
        sharedDatabase.sitePhotoDao().upsert(
            SitePhotoEntity(
                id = "photo-1",
                projectId = projectId,
                objectCode = "NODE-1",
                tagCodesCsv = "",
                matchedNodeId = "node-1",
                matchedRouteId = null,
                filePath = File(oldRoot, "photos/img.jpg").absolutePath,
                thumbnailPath = File(oldRoot, "thumbs/img_thumb.jpg").absolutePath,
                latitude = null,
                longitude = null,
                locationAccuracyM = null,
                isGpsMocked = false,
                locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
                engineer = "Field",
                capturedAtEpochMs = 200L,
                matchedAtEpochMs = 0L,
                matchingTimeOffsetMs = 0L
            )
        )
        
        // Seed photo and thumb on disk
        File(oldRoot, "photos/img.jpg").apply { parentFile?.mkdirs() }.writeText("img")
        File(oldRoot, "thumbs/img_thumb.jpg").apply { parentFile?.mkdirs() }.writeText("thumb")

        projectDb.close()

        // Verify old database exists before migration
        assertTrue(oldDbFile.exists())

        // Verify shared DB has the seeded records before migration
        assertNotNull(sharedDatabase.projectDao().get(projectId))
        assertEquals(1, sharedDatabase.importedFileDao().byProject(projectId).size)
        assertEquals(1, sharedDatabase.sitePhotoDao().byProject(projectId).size)

        // Run migration
        val status = migrationService.migrateProjectEntityIfNeeded(project)

        // Verify old database is deleted
        assertFalse(oldDbFile.exists())
        
        // Verify new files exist
        val newDbFile = storageManager.scopedProjectDbFile(slug)
        assertTrue(newDbFile.exists())

        // Verify paths updated in shared DB
        val updatedProject = sharedDatabase.projectDao().get(projectId)
        assertEquals(newDbFile.absolutePath, updatedProject?.projectDbPath)

        val updatedFile = sharedDatabase.importedFileDao().byProject(projectId).single()
        assertEquals(File(newRoot, "imports/data.kml").absolutePath, updatedFile.storedPath)

        // Verify paths updated in shared DB
        val updatedPhoto = sharedDatabase.sitePhotoDao().byProject(projectId).single()
        println("DEBUG: updatedPhoto.filePath = ${updatedPhoto.filePath}")
        println("DEBUG: updatedPhoto.thumbnailPath = ${updatedPhoto.thumbnailPath}")
        println("DEBUG: newRoot exists = ${newRoot.exists()}")
        println("DEBUG: Files in newRoot recursively:")
        newRoot.walkTopDown().forEach { println("  - ${it.absolutePath}") }

        val expectedFolder = File(newRoot, "Media/Node/N-01")
        assertTrue("Expected photo in Media/Node/node-1 but was ${updatedPhoto.filePath}", updatedPhoto.filePath.startsWith(expectedFolder.absolutePath))
        assertTrue(updatedPhoto.filePath.endsWith(".jpg"))
        assertEquals(updatedPhoto.filePath, updatedPhoto.thumbnailPath)
        assertTrue(File(updatedPhoto.filePath).exists())
        assertTrue(status.migrated)
        assertTrue(status.verified)
        assertEquals(newDbFile.absolutePath, status.projectDbPath)
        assertEquals(1, status.sharedRowCounts["imported_files"])
        assertEquals(1, status.scopedRowCounts["site_photos"])
    }

    @Test
    fun testMigrationIdempotency_doesNothingIfNoLegacyDirectoryExists() = runBlocking {
        val projectId = "proj-2"
        val slug = "proj-slug-2"
        
        val newDbFile = storageManager.scopedProjectDbFile(slug)
        val project = ProjectEntity(
            id = projectId,
            name = "Test Project 2",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = newDbFile.absolutePath
        )
        
        sharedDatabase.projectDao().upsert(project)
        
        // Ensure legacy folder does NOT exist
        val oldRoot = storageManager.privateProjectRoot(slug)
        oldRoot.deleteRecursively()

        // Run migration
        val status = migrationService.migrateProjectEntityIfNeeded(project)

        // Verify DB path remains unchanged
        val updatedProject = sharedDatabase.projectDao().get(projectId)
        assertEquals(newDbFile.absolutePath, updatedProject?.projectDbPath)
        assertFalse(status.migrated)
        assertTrue(status.verified)
        assertFalse(status.projectDbPath.isBlank())
    }

    @Test
    fun testMigrationIdempotency_skipsEmptyLegacyRootsForScopedProject() = runBlocking {
        val projectId = "proj-empty-legacy"
        val slug = "proj-empty-legacy-slug"

        val scopedDbFile = storageManager.scopedProjectDbFile(slug)
        val project = ProjectEntity(
            id = projectId,
            name = "Scoped Project",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = scopedDbFile.absolutePath
        )

        sharedDatabase.projectDao().upsert(project)
        storageManager.privateProjectRoot(slug)
        storageManager.privateProjectRoot(projectId)
        File(storageManager.projectRootDirectory(slug), "photos").mkdirs()
        File(storageManager.projectRootDirectory(slug), "thumbs").mkdirs()

        val status = migrationService.migrateProjectEntityIfNeeded(project)

        val updatedProject = sharedDatabase.projectDao().get(projectId)
        assertEquals(scopedDbFile.absolutePath, updatedProject?.projectDbPath)
        assertFalse(status.migrated)
        assertTrue(status.verified)
    }

    @Test
    fun testMigrationIdempotency_skipsWhenCurrentPublicRootAlreadyHasContent() = runBlocking {
        val projectId = "proj-current-root"
        val slug = "proj-current-root-slug"

        val scopedDbFile = storageManager.scopedProjectDbFile(slug)
        val project = ProjectEntity(
            id = projectId,
            name = "Scoped Project",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = scopedDbFile.absolutePath
        )

        sharedDatabase.projectDao().upsert(project)
        storageManager.privateProjectRoot(slug).deleteRecursively()
        storageManager.privateProjectRoot(projectId).deleteRecursively()

        val currentRoot = storageManager.projectRoot(slug)
        val currentMediaFile = File(currentRoot, "Media/Node/N-01/img.jpg")
        currentMediaFile.parentFile?.mkdirs()
        currentMediaFile.writeText("img")

        Room.databaseBuilder(context, MapSupervisionDatabase::class.java, scopedDbFile.absolutePath)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .close()

        val status = migrationService.migrateProjectEntityIfNeeded(project)

        val updatedProject = sharedDatabase.projectDao().get(projectId)
        assertEquals(scopedDbFile.absolutePath, updatedProject?.projectDbPath)
        assertTrue(currentMediaFile.exists())
        assertFalse(status.migrated)
        assertTrue(status.verified)
        assertEquals(scopedDbFile.absolutePath, status.projectDbPath)
    }

    @Test
    fun testMigrateProject_withProjectIdLegacyFolders_migratesCorrectly() = runBlocking {
        val projectId = "proj-legacy-id"
        val slug = "proj-legacy-slug"
        
        // Seed old database and files in a folder named after projectId
        val oldRoot = File(tempDir, "private/Projects/$projectId")
        val oldDbFile = File(oldRoot, "db/project.sqlite")
        oldDbFile.parentFile?.mkdirs()
        
        val projectDb = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, oldDbFile.absolutePath)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            
        val project = ProjectEntity(
            id = projectId,
            name = "Legacy ID Project",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = oldDbFile.absolutePath
        )
        
        sharedDatabase.projectDao().upsert(project)
        projectDb.projectDao().upsert(project)

        val node = GisNodeEntity(
            id = "node-1",
            projectId = projectId,
            code = "N-01",
            contractor = "Contractor",
            latitude = 0.0,
            longitude = 0.0,
            mapNumberLabel = "N-01",
            workVolumeSummary = ""
        )
        sharedDatabase.gisNodeDao().upsert(node)
        projectDb.gisNodeDao().upsert(node)

        sharedDatabase.sitePhotoDao().upsert(
            SitePhotoEntity(
                id = "photo-legacy",
                projectId = projectId,
                objectCode = "NODE-1",
                tagCodesCsv = "",
                matchedNodeId = "node-1",
                matchedRouteId = null,
                filePath = File(oldRoot, "photos/img.jpg").absolutePath,
                thumbnailPath = File(oldRoot, "thumbs/img_thumb.jpg").absolutePath,
                latitude = null,
                longitude = null,
                locationAccuracyM = null,
                isGpsMocked = false,
                locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
                engineer = "Field",
                capturedAtEpochMs = 200L,
                matchedAtEpochMs = 0L,
                matchingTimeOffsetMs = 0L
            )
        )
        
        // Seed file on disk
        File(oldRoot, "photos/img.jpg").apply { parentFile?.mkdirs() }.writeText("img")
        File(oldRoot, "thumbs/img_thumb.jpg").apply { parentFile?.mkdirs() }.writeText("thumb")

        projectDb.close()

        // Verify legacy folder exists
        assertTrue(oldDbFile.exists())

        // Run migration
        val status = migrationService.migrateProjectEntityIfNeeded(project)

        // Verify old database is deleted
        assertFalse(oldDbFile.exists())
        
        // Verify new files exist in root by slug
        val newRoot = storageManager.projectRoot(slug)
        val newDbFile = storageManager.scopedProjectDbFile(slug)
        assertTrue(newDbFile.exists())

        // Verify paths updated in shared DB
        val updatedProject = sharedDatabase.projectDao().get(projectId)
        assertEquals(newDbFile.absolutePath, updatedProject?.projectDbPath)

        val updatedPhoto = sharedDatabase.sitePhotoDao().byProject(projectId).single()
        val expectedFolder = File(newRoot, "Media/Node/N-01")
        assertTrue(updatedPhoto.filePath.startsWith(expectedFolder.absolutePath))
        assertTrue(updatedPhoto.filePath.endsWith(".jpg"))
        assertEquals(updatedPhoto.filePath, updatedPhoto.thumbnailPath)
        assertTrue(File(updatedPhoto.filePath).exists())
        assertTrue(status.verified)
    }

    @Test
    fun testStandardizeMediaAndCleanupThumbs_removesOldThumbnailsAndStandardizesDb() = runBlocking {
        val projectId = "proj-repair"
        val slug = "proj-repair-slug"

        val project = ProjectEntity(
            id = projectId,
            name = "Repair Project",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = storageManager.scopedProjectDbFile(slug).absolutePath
        )
        sharedDatabase.projectDao().upsert(project)

        val node = GisNodeEntity(
            id = "node-1",
            projectId = projectId,
            code = "N-01",
            contractor = "Contractor",
            latitude = 0.0,
            longitude = 0.0,
            mapNumberLabel = "N-01",
            workVolumeSummary = ""
        )
        sharedDatabase.gisNodeDao().upsert(node)

        val mainFile = File(tempDir, "img.jpg").apply { writeText("original image") }
        val thumbFile = File(tempDir, "img_thumb.jpg").apply { writeText("thumbnail image") }

        sharedDatabase.sitePhotoDao().upsert(
            SitePhotoEntity(
                id = "photo-repair-1",
                projectId = projectId,
                objectCode = "NODE-1",
                tagCodesCsv = "",
                matchedNodeId = "node-1",
                matchedRouteId = null,
                filePath = mainFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                latitude = null,
                longitude = null,
                locationAccuracyM = null,
                isGpsMocked = false,
                locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
                engineer = "Field",
                capturedAtEpochMs = 200L,
                matchedAtEpochMs = 0L,
                matchingTimeOffsetMs = 0L
            )
        )

        assertTrue(thumbFile.exists())

        // Run repair/migration (which triggers standardizeMediaAndCleanupThumbs)
        val status = migrationService.migrateProjectEntityIfNeeded(project)

        val updatedPhoto = sharedDatabase.sitePhotoDao().byProject(projectId).single()
        assertEquals(mainFile.absolutePath, updatedPhoto.thumbnailPath)
        assertFalse(thumbFile.exists()) // Verify physical thumb deleted
        assertTrue(status.verified)
        assertEquals(storageManager.scopedProjectDbFile(slug).absolutePath, status.projectDbPath)
    }

    @Test
    fun testMigrateProject_copiesWorkPlansIntoScopedDatabase() = runBlocking {
        val projectId = "proj-workplan"
        val slug = "proj-workplan-slug"
        val oldRoot = storageManager.privateProjectRoot(slug)
        val oldDbFile = File(oldRoot, "db/project.sqlite").apply { parentFile?.mkdirs() }

        val project = ProjectEntity(
            id = projectId,
            name = "WorkPlan Project",
            slug = slug,
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = oldDbFile.absolutePath
        )
        sharedDatabase.projectDao().upsert(project)

        val node = GisNodeEntity(
            id = "node-1",
            projectId = projectId,
            code = "N-01",
            contractor = "Contractor",
            latitude = 0.0,
            longitude = 0.0,
            mapNumberLabel = "N-01",
            workVolumeSummary = ""
        )
        sharedDatabase.gisNodeDao().upsert(node)

        sharedDatabase.workPlanDao().insert(
            WorkPlanEntity(
                id = "plan-1",
                projectId = projectId,
                title = "Thi cong",
                description = "Batch",
                plannedDateEpochDay = 1000L,
                nodeCode = "N-01",
                routeCode = null,
                taskId = null,
                sourceRawInput = "",
                createdAtEpochMs = 200L,
                quantity = 5.0,
                unit = "m",
                batchGroupId = "batch-1",
                nodeId = "node-1",
                routeId = null
            )
        )

        val status = migrationService.migrateProjectEntityIfNeeded(project)

        val scopedDb = Room.databaseBuilder(
            context,
            MapSupervisionDatabase::class.java,
            storageManager.scopedProjectDbFile(slug).absolutePath
        ).addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val scopedPlans = scopedDb.workPlanDao().byProject(projectId)
            assertEquals(1, scopedPlans.size)
            assertEquals("node-1", scopedPlans.single().nodeId)
        } finally {
            scopedDb.close()
        }
        assertTrue(status.verified)
        assertEquals(1, status.scopedRowCounts["work_plan"])
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
