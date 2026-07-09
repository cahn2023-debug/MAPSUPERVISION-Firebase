package com.mapsupervision.ai.core.rag

interface TextEmbeddingEngine {
    suspend fun embed(text: String): FloatArray?
}
