package com.mapsupervision.domain.repository

data class LocalLlmMessage(
    val role: String,
    val text: String
)

data class LocalLlmRequest(
    val prompt: String,
    val contextSummary: String = "",
    val normalizationContext: String = "",
    val retrievedContext: String = "",
    val currentTab: String = "ai",
    val selectedNodeCode: String? = null,
    val selectedRouteCode: String? = null,
    val history: List<LocalLlmMessage> = emptyList()
)

data class LocalLlmResponse(
    val text: String,
    val modelName: String,
    val backendUsed: String,
    val warnings: List<String> = emptyList()
)

interface LocalLlmRepository {
    suspend fun isReady(): Boolean
    suspend fun warmUp(): Boolean
    suspend fun generate(request: LocalLlmRequest): LocalLlmResponse
    fun cancel()
}
