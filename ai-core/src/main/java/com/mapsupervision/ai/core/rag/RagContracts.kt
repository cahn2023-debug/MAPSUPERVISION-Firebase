package com.mapsupervision.ai.core.rag

interface RagIndexRepository {
    suspend fun listByProject(projectId: String): List<RagDocument>
    suspend fun upsertAll(projectId: String, documents: List<RagDocument>)
    suspend fun deleteMissing(projectId: String, keepIds: Set<String>)
    suspend fun clearProject(projectId: String)
}

interface RagRetriever {
    suspend fun retrieve(request: RagBuildRequest): RagBuildResult
}

interface RagContextBuilder {
    suspend fun build(request: RagBuildRequest): RagBuildResult
}
