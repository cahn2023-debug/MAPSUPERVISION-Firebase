package com.mapsupervision.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.repository.ChatHistoryRepositoryImpl
import com.mapsupervision.domain.model.ChatHistoryMessage
import com.mapsupervision.storage.ProjectStorageManager
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
class ChatHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: MapSupervisionDatabase
    private lateinit var provider: ProjectScopedDatabaseProvider
    private lateinit var repository: ChatHistoryRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = ProjectScopedDatabaseProvider(context, database, ProjectStorageManager(context))
        repository = ChatHistoryRepositoryImpl(database.chatHistoryDao(), provider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppendAndListRecentByProject() = runBlocking {
        val projectId = "project-abc"
        
        database.projectDao().upsert(
            com.mapsupervision.data.db.entity.ProjectEntity(
                id = projectId,
                name = "Project ABC",
                slug = "project-abc",
                isArchived = false,
                createdAtEpochMs = 1000L,
                metadataVersion = 3,
                updatedAtEpochMs = 1000L,
                storageMode = com.mapsupervision.domain.model.ProjectStorageMode.LEGACY_SHARED,
                projectDbPath = ""
            )
        )
        
        // Append 55 messages with increasing timestamps
        for (i in 1..55) {
            val message = ChatHistoryMessage(
                id = "msg-$i",
                projectId = projectId,
                role = if (i % 2 == 0) "user" else "assistant",
                text = "Message text $i",
                createdAtEpochMs = i * 1000L
            )
            val result = repository.append(message)
            assertTrue(result is AppResult.Success)
        }

        // Retrieve with limit 50
        val result = repository.listRecentByProject(projectId, 50)
        assertTrue(result is AppResult.Success)
        val list = (result as AppResult.Success).data
        
        // Assert limit is applied
        assertEquals(50, list.size)
        
        // Assert chronological ascending order is preserved
        // The last 50 messages should be msg-6 through msg-55, in ascending order
        assertEquals("msg-6", list.first().id)
        assertEquals("msg-55", list.last().id)
        
        // Check timestamps are strictly increasing
        for (i in 0 until list.size - 1) {
            assertTrue(
                "Not sorted chronologically ascending: ${list[i].id} (${list[i].createdAtEpochMs}) vs ${list[i+1].id} (${list[i+1].createdAtEpochMs})",
                list[i].createdAtEpochMs < list[i + 1].createdAtEpochMs
            )
        }
    }
}
