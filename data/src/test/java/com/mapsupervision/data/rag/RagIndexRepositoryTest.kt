package com.mapsupervision.data.rag

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.ai.core.rag.RagDocument
import com.mapsupervision.ai.core.rag.RagDocumentType
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.storage.ProjectStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RagIndexRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: MapSupervisionDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert and deleteMissing work for rag embeddings`() = runBlocking {
        database.projectDao().upsert(
            ProjectEntity(
                id = "p1",
                name = "Project 1",
                slug = "project-1",
                isArchived = false,
                createdAtEpochMs = 1L,
                storageMode = ProjectStorageMode.LEGACY_SHARED
            )
        )

        val repository = RagIndexRepositoryImpl(
            database.ragDocumentEmbeddingDao(),
            ProjectScopedDatabaseProvider(context, database, ProjectStorageManager(context))
        )
        val doc1 = ragDoc("d1", "HG01", "node_code=HG01 label=A1", floatArrayOf(1f, 0f))
        val doc2 = ragDoc("d2", "HG02", "node_code=HG02 label=A2", floatArrayOf(0f, 1f))

        repository.upsertAll("p1", listOf(doc1, doc2))
        assertEquals(2, repository.listByProject("p1").size)

        repository.deleteMissing("p1", setOf(doc1.id))
        val remaining = repository.listByProject("p1")
        assertEquals(1, remaining.size)
        assertEquals(doc1.id, remaining.first().id)
    }

    private fun ragDoc(
        id: String,
        sourceCode: String,
        text: String,
        embedding: FloatArray
    ): RagDocument {
        return RagDocument(
            id = id,
            projectId = "p1",
            docType = RagDocumentType.NODE,
            sourceId = id,
            sourceCode = sourceCode,
            text = text,
            contentHash = text.hashCode().toString(),
            embedding = embedding,
            updatedAtEpochMs = 1L
        )
    }
}

