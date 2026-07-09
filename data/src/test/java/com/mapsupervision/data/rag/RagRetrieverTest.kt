package com.mapsupervision.data.rag

import com.mapsupervision.ai.core.rag.RagBuildRequest
import com.mapsupervision.ai.core.rag.RagDocument
import com.mapsupervision.ai.core.rag.RagDocumentType
import com.mapsupervision.ai.core.rag.RagIndexRepository
import com.mapsupervision.ai.core.rag.RagQueryDomain
import com.mapsupervision.ai.core.rag.TextEmbeddingEngine
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.WorkspaceSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagRetrieverTest {
    @Test
    fun `exact code beats semantic score`() = runBlocking {
        val docs = listOf(
            doc("HG01", RagDocumentType.NODE, "node_code=HG01 label=A1 contractor=Alpha", floatArrayOf(1f, 0f)),
            doc("HG02", RagDocumentType.NODE, "node_code=HG02 label=A2 contractor=Beta", floatArrayOf(0f, 1f))
        )
        val retriever = RagRetrieverImpl(
            repository = FakeRagIndexRepository(docs),
            embeddingEngine = FakeEngine(floatArrayOf(0f, 1f))
        )
        val snapshot = WorkspaceSnapshot(
            projectId = "p1",
            designNodes = listOf(
                GisNode("n1", "p1", "HG01", "Alpha", 0.0, 0.0, "A1", ""),
                GisNode("n2", "p1", "HG02", "Beta", 0.0, 0.0, "A2", "")
            )
        )

        val result = retriever.retrieve(
            RagBuildRequest(
                projectId = "p1",
                query = "HG01",
                workspaceSnapshot = snapshot,
                selectedNodeCode = "HG01",
                selectedRouteCode = null
            )
        )

        assertEquals("HG01", result.retrievedDocuments.first().document.sourceCode)
    }

    @Test
    fun `lexical fallback works when embedding is unavailable`() = runBlocking {
        val docs = listOf(
            doc("HG01", RagDocumentType.WORK_CATEGORY, "category_name=Be tong unit=m3", null),
            doc("HG02", RagDocumentType.WORK_CATEGORY, "category_name=Cap quang unit=m", null)
        )
        val retriever = RagRetrieverImpl(
            repository = FakeRagIndexRepository(docs),
            embeddingEngine = FakeEngine(null)
        )

        val result = retriever.retrieve(
            RagBuildRequest(
                projectId = "p1",
                query = "be tong cot thep",
                workspaceSnapshot = WorkspaceSnapshot(projectId = "p1"),
                selectedNodeCode = null,
                selectedRouteCode = null
            )
        )

        assertEquals("HG01", result.retrievedDocuments.first().document.sourceCode)
        assertTrue(result.block.retrievedContext.contains("category_name=Be tong"))
    }

    @Test
    fun `progress query filters out planning and daily log documents`() = runBlocking {
        val docs = listOf(
            doc("HG01", RagDocumentType.NODE_PROGRESS, "node_code=HG01 planned=100.000 actual=80.000 remain=20.000", null),
            doc("HG01:Cap", RagDocumentType.WORK_VOLUME_PROGRESS, "node_code=HG01 work_name=Cap planned=10.000 actual=5.000 unit=m", null),
            doc("log1", RagDocumentType.DAILY_LOG, "node_code=HG01 route_code= work_item=Dao note=Nhat ky", null),
            doc("plan1", RagDocumentType.WORK_PLAN, "node_code=HG01 route_code= title=Ke hoach description=Thi cong plannedDateEpochDay=1", null)
        )
        val retriever = RagRetrieverImpl(
            repository = FakeRagIndexRepository(docs),
            embeddingEngine = FakeEngine(null)
        )

        val result = retriever.retrieve(
            RagBuildRequest(
                projectId = "p1",
                query = "tiến độ HG01",
                workspaceSnapshot = snapshotWithHg01(),
                selectedNodeCode = "HG01"
            )
        )

        assertEquals(RagQueryDomain.PROGRESS, result.queryDomain)
        assertTrue(result.retrievedDocuments.any { it.document.docType == RagDocumentType.NODE_PROGRESS })
        assertTrue(result.retrievedDocuments.any { it.document.docType == RagDocumentType.WORK_VOLUME_PROGRESS })
        assertTrue(result.retrievedDocuments.none { it.document.docType == RagDocumentType.DAILY_LOG })
        assertTrue(result.retrievedDocuments.none { it.document.docType == RagDocumentType.WORK_PLAN })
    }

    @Test
    fun `planning query prioritizes work plan documents`() = runBlocking {
        val docs = listOf(
            doc("plan1", RagDocumentType.WORK_PLAN, "node_code=HG01 route_code= title=Ke hoach hom nay description=Keo cap plannedDateEpochDay=1", null),
            doc("HG01", RagDocumentType.NODE_PROGRESS, "node_code=HG01 planned=100.000 actual=80.000 remain=20.000", null),
            doc("log1", RagDocumentType.DAILY_LOG, "node_code=HG01 route_code= work_item=Dao note=Nhat ky", null)
        )
        val retriever = RagRetrieverImpl(
            repository = FakeRagIndexRepository(docs),
            embeddingEngine = FakeEngine(null)
        )

        val result = retriever.retrieve(
            RagBuildRequest(
                projectId = "p1",
                query = "kế hoạch hôm nay HG01",
                workspaceSnapshot = snapshotWithHg01(),
                selectedNodeCode = "HG01"
            )
        )

        assertEquals(RagQueryDomain.PLANNING, result.queryDomain)
        assertEquals(RagDocumentType.WORK_PLAN, result.retrievedDocuments.first().document.docType)
        assertTrue(result.retrievedDocuments.none { it.document.docType == RagDocumentType.DAILY_LOG })
    }

    @Test
    fun `daily log query prioritizes daily log documents`() = runBlocking {
        val docs = listOf(
            doc("log1", RagDocumentType.DAILY_LOG, "node_code=HG01 route_code= work_item=Dao note=Nhat ky", null),
            doc("plan1", RagDocumentType.WORK_PLAN, "node_code=HG01 route_code= title=Ke hoach description=Keo cap plannedDateEpochDay=1", null),
            doc("HG01", RagDocumentType.NODE_PROGRESS, "node_code=HG01 planned=100.000 actual=80.000 remain=20.000", null)
        )
        val retriever = RagRetrieverImpl(
            repository = FakeRagIndexRepository(docs),
            embeddingEngine = FakeEngine(null)
        )

        val result = retriever.retrieve(
            RagBuildRequest(
                projectId = "p1",
                query = "nhật ký hôm qua HG01",
                workspaceSnapshot = snapshotWithHg01(),
                selectedNodeCode = "HG01"
            )
        )

        assertEquals(RagQueryDomain.DAILY_LOG, result.queryDomain)
        assertEquals(RagDocumentType.DAILY_LOG, result.retrievedDocuments.first().document.docType)
        assertTrue(result.retrievedDocuments.none { it.document.docType == RagDocumentType.WORK_PLAN })
    }

    private fun doc(
        sourceCode: String,
        type: RagDocumentType,
        text: String,
        embedding: FloatArray?
    ): RagDocument {
        return RagDocument(
            id = "id_$sourceCode",
            projectId = "p1",
            docType = type,
            sourceId = "source_$sourceCode",
            sourceCode = sourceCode,
            text = text,
            contentHash = text.hashCode().toString(),
            embedding = embedding,
            updatedAtEpochMs = 1L
        )
    }

    private fun snapshotWithHg01(): WorkspaceSnapshot {
        return WorkspaceSnapshot(
            projectId = "p1",
            designNodes = listOf(
                GisNode("n1", "p1", "HG01", "Alpha", 0.0, 0.0, "A1", "")
            )
        )
    }

    private class FakeRagIndexRepository(
        private val docs: List<RagDocument>
    ) : RagIndexRepository {
        override suspend fun listByProject(projectId: String): List<RagDocument> = docs
        override suspend fun upsertAll(projectId: String, documents: List<RagDocument>) = Unit
        override suspend fun deleteMissing(projectId: String, keepIds: Set<String>) = Unit
        override suspend fun clearProject(projectId: String) = Unit
    }

    private class FakeEngine(
        private val vector: FloatArray?
    ) : TextEmbeddingEngine {
        override suspend fun embed(text: String): FloatArray? = vector
    }
}


