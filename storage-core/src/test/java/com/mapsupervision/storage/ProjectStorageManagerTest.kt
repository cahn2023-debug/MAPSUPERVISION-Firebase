package com.mapsupervision.storage

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectStorageManagerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("project-storage-manager-test").toFile()
        storageManager = object : ProjectStorageManager(context) {
            override fun projectRootDirectory(projectSlug: String): File {
                return File(tempDir, "public/Projects/$projectSlug")
            }

            override fun privateProjectRootDirectory(projectSlug: String): File {
                return File(tempDir, "private/Projects/$projectSlug")
            }

            override fun projectRoot(projectSlug: String): File {
                return super.projectRoot(projectSlug)
            }

            override fun privateProjectRoot(projectSlug: String): File {
                return super.privateProjectRoot(projectSlug)
            }
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun sanitizeFolderName_replaces_reserved_characters() {
        val sanitized = storageManager.sanitizeFolderName(" /A:B*?<test>| ")

        assertEquals("ABtest", sanitized)
    }

    @Test
    fun projectRoot_creates_expected_children() {
        val root = storageManager.projectRoot("demo-project")

        assertTrue(root.exists())
        ProjectStorageManager.mediaFolderChildren.forEach { child ->
            assertTrue("missing child $child", File(root, child).exists())
        }
    }

    @Test
    fun helperMethods_return_expected_paths() {
        val slug = "test-project"
        val root = storageManager.projectRoot(slug)
        assertEquals(File(root, "db/project.sqlite").absolutePath, storageManager.projectDbFile(slug).absolutePath)
        assertTrue(storageManager.resolveObjectFolder(slug, false, "NODE-01").absolutePath.contains("Media${File.separator}Node${File.separator}NODE-01"))
        assertTrue(storageManager.resolveObjectFolder(slug, true, "ROUTE-01").absolutePath.contains("Media${File.separator}Route${File.separator}ROUTE-01"))
    }

    @Test
    fun buildMediaFileName_uses_time_location_note() {
        val name = storageManager.buildMediaFileName(
            capturedAt = 1710000000000L,
            locationLabel = "Ho Chi Minh",
            note = "Kiem tra",
            extension = "jpg"
        )

        assertTrue(name.endsWith(".jpg"))
        assertTrue(name.matches(Regex("""\d{8}_\d{6}_000_.*\.jpg""")))
        assertTrue(name.contains("Ho-Chi-Minh"))
        assertTrue(name.contains("Kiem-tra"))
    }

    @Test
    fun buildMediaFileName_changes_across_millisecond_captures_without_suffix() {
        val first = storageManager.buildMediaFileName(
            capturedAt = 1710000000000L,
            locationLabel = "A",
            note = "B",
            extension = "jpg"
        )
        val second = storageManager.buildMediaFileName(
            capturedAt = 1710000000001L,
            locationLabel = "A",
            note = "B",
            extension = "jpg"
        )

        assertTrue(first != second)
        assertFalse(first.contains("_1.jpg"))
        assertFalse(second.contains("_1.jpg"))
    }

    @Test
    fun prepareImportedProjectStorage_clears_custom_path_and_legacy_db_folders() {
        val slug = "legacy-project"
        val projectId = "legacy-id"
        val customRoot = File(tempDir, "custom-root")
        File(customRoot, "db/project.sqlite").apply { parentFile?.mkdirs() }.writeText("legacy-db")
        File(File(tempDir, "private/Projects/$slug"), "db/project.sqlite").apply { parentFile?.mkdirs() }.writeText("legacy-private-slug")
        File(File(tempDir, "private/Projects/$projectId"), "db/project.sqlite").apply { parentFile?.mkdirs() }.writeText("legacy-private-id")
        val publicLegacyRoot = File(storageManager.publicBaseDirDirectory(), "Projects/$projectId")
        File(publicLegacyRoot, "db/project.sqlite").apply { parentFile?.mkdirs() }.writeText("legacy-public-id")

        storageManager.setCustomPath(slug, customRoot.absolutePath)

        storageManager.prepareImportedProjectStorage(slug, projectId)

        assertEquals(null, storageManager.getCustomPath(slug))
        assertFalse(File(customRoot, "db/project.sqlite").exists())
        assertFalse(File(File(tempDir, "private/Projects/$slug"), "db/project.sqlite").exists())
        assertFalse(File(File(tempDir, "private/Projects/$projectId"), "db/project.sqlite").exists())
        assertFalse(File(publicLegacyRoot, "db/project.sqlite").exists())
    }

    @Test(expected = SecurityException::class)
    fun importProjectZip_rejects_zip_slip_entries() {
        val zipBytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.txt"))
                zip.write("blocked".toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
        val service = ProjectPackageService(storageManager)

        service.importProjectZip(ByteArrayInputStream(zipBytes), "zip-slip-project")
    }
}
