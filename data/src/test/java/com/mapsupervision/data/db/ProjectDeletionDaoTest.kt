package com.mapsupervision.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.ProjectDeletionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProjectDeletionDaoTest {

    @Test
    fun requestDeletion_is_idempotent_and_rejects_a_different_owner() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.projectDao().upsert(
                ProjectEntity(
                    id = "project-1",
                    name = "Project 1",
                    slug = "project-1",
                    isArchived = false,
                    createdAtEpochMs = 1L
                )
            )

            assertEquals(1, database.projectDao().requestDeletion("project-1", "request-1", 2L))
            assertEquals(0, database.projectDao().requestDeletion("project-1", "request-2", 3L))
            val project = database.projectDao().get("project-1")
            assertTrue(project != null)
            assertEquals(ProjectDeletionState.DELETING, project?.deletionState)
            assertEquals("request-1", project?.deletionRequestId)
            assertEquals(0, database.projectDao().completeLocalDeletion("project-1", "request-1", 4L, 4L))
            assertEquals(1, database.projectDao().markCloudDeletionCompleted("project-1", "request-1", 5L, 5L))
            assertEquals(1, database.projectDao().completeLocalDeletion("project-1", "request-1", 6L, 6L))
            assertEquals(ProjectDeletionState.DELETED, database.projectDao().get("project-1")?.deletionState)
        } finally {
            database.close()
        }
    }

    @Test
    fun purgeProjectRows_keeps_only_the_project_tombstone_row() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.projectDao().upsert(
                ProjectEntity(
                    id = "project-1",
                    name = "Project 1",
                    slug = "project-1",
                    isArchived = false,
                    createdAtEpochMs = 1L
                )
            )

            database.projectDao().purgeProjectRows("project-1")

            assertTrue(database.projectDao().get("project-1") != null)
        } finally {
            database.close()
        }
    }
}
