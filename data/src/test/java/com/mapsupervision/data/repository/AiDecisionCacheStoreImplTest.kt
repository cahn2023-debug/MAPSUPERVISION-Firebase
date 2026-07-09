package com.mapsupervision.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.ai.core.AiCapability
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AiDecisionCacheStoreImplTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var database: MapSupervisionDatabase

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("ai-decision-cache-test").toFile()
        database = Room.databaseBuilder(
            context,
            MapSupervisionDatabase::class.java,
            File(tempDir, "shared.sqlite").absolutePath
        ).addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `put and get use shared database for project scoped projects`() = runBlocking {
        val project = ProjectEntity(
            id = "project-cache",
            name = "Project Cache",
            slug = "project-cache",
            isArchived = false,
            createdAtEpochMs = 100L,
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = File(tempDir, "scoped/project.sqlite").absolutePath
        )
        database.projectDao().upsert(project)

        val store = AiDecisionCacheStoreImpl(database.aiDecisionCacheDao())

        store.put(
            projectId = project.id,
            capability = AiCapability.OPS_RECOMMENDATION,
            payloadHash = "payload-1",
            resultJson = "{\"ok\":true}"
        )

        val cached = database.aiDecisionCacheDao().find(
            project.id,
            AiCapability.OPS_RECOMMENDATION.name,
            "payload-1"
        )

        assertNotNull(cached)
        assertEquals(
            "{\"ok\":true}",
            store.get(project.id, AiCapability.OPS_RECOMMENDATION, "payload-1")
        )
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
