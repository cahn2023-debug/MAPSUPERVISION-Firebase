package com.mapsupervision.storage

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProjectPackageServiceTest {

    private lateinit var tempDir: File
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var service: ProjectPackageService

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("project-package-service-test").toFile()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storageManager = object : ProjectStorageManager(context) {
            override fun projectRootDirectory(projectSlug: String): File {
                return File(tempDir, "public/Projects/$projectSlug")
            }

            override fun privateProjectRootDirectory(projectSlug: String): File {
                return File(tempDir, "private/Projects/$projectSlug")
            }

            override fun projectRoot(projectSlug: String): File {
                return projectRootDirectory(projectSlug).apply { mkdirs() }
            }

            override fun privateProjectRoot(projectSlug: String): File {
                return privateProjectRootDirectory(projectSlug).apply { mkdirs() }
            }
        }
        service = ProjectPackageService(storageManager)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun importProjectZip_skips_db_entries() {
        val zipBytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                writeEntry(zip, "project_metadata.json", """{"project":{"id":"p1","slug":"p1","name":"P1"}}""")
                writeEntry(zip, "db/project.sqlite", "legacy-db")
                writeEntry(zip, "db/project.sqlite-wal", "legacy-wal")
                writeEntry(zip, "db/project.sqlite-shm", "legacy-shm")
                writeEntry(zip, "Media/Node/N1/photo.jpg", "photo")
                writeEntry(zip, "imports/data.kml", "kml")
            }
            output.toByteArray()
        }

        service.importProjectZip(ByteArrayInputStream(zipBytes), "p1")

        val root = storageManager.projectRoot("p1")
        assertTrue(File(root, "Media/Node/N1/photo.jpg").exists())
        assertTrue(File(root, "imports/data.kml").exists())
        assertFalse(File(root, "db/project.sqlite").exists())
        assertFalse(File(root, "db/project.sqlite-wal").exists())
        assertFalse(File(root, "db/project.sqlite-shm").exists())
    }

    @Test
    fun copyImportedFilesToPrivateStorage_skips_db_and_clears_existing_db_folder() {
        val source = File(tempDir, "source").apply { mkdirs() }
        File(source, "project_metadata.json").writeText("""{"project":{"id":"p1"}}""")
        File(source, "db/project.sqlite").apply { parentFile?.mkdirs() }.writeText("legacy-db")
        File(source, "db/project.sqlite-wal").writeText("legacy-wal")
        File(source, "Media/Node/N1/photo.jpg").apply { parentFile?.mkdirs() }.writeText("photo")
        File(source, "imports/data.kml").apply { parentFile?.mkdirs() }.writeText("kml")

        val root = storageManager.projectRoot("p1")
        File(root, "db/project.sqlite").apply { parentFile?.mkdirs() }.writeText("stale-db")

        service.copyImportedFilesToPrivateStorage(source, "p1")

        assertTrue(File(root, "Media/Node/N1/photo.jpg").exists())
        assertTrue(File(root, "imports/data.kml").exists())
        assertFalse(File(root, "db/project.sqlite").exists())
        assertFalse(File(root, "db/project.sqlite-wal").exists())
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
}
