package com.mapsupervision.ai.model.mediapipe

import com.mapsupervision.ai.core.GemmaModelInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class GemmaChatController @Inject constructor(
    private val modelManager: GemmaModelManager,
    private val chatService: GemmaLiteRtChatService
) {
    private var initializedModelId: String? = null

    data class InitializationResult(
        val ready: Boolean,
        val message: String,
        val warning: String = ""
    )

    suspend fun initialize(model: GemmaModelInfo): InitializationResult = withContext(Dispatchers.IO) {
        if (!modelManager.isModelDownloadComplete(model)) {
            return@withContext InitializationResult(
                ready = false,
                message = "Model chua tai xong.",
            )
        }
        if (!modelManager.canInitializeLiteRt(model)) {
            return@withContext InitializationResult(
                ready = false,
                message = "Model da tai nhung file khong hop le."
            )
        }
        if (initializedModelId == model.downloadFileName) {
            return@withContext InitializationResult(ready = true, message = "Gemma san sang.")
        }
        val initResult = runCatching { chatService.initializeModel(model) }
        if (initResult.isSuccess) {
            initializedModelId = model.downloadFileName
            return@withContext InitializationResult(
                ready = true,
                message = "Gemma san sang.",
                warning = initResult.getOrThrow().warnings.joinToString(" | ")
            )
        } else {
            initializedModelId = null
            return@withContext InitializationResult(
                ready = false,
                message = "Model init failed: ${initResult.exceptionOrNull()?.message.orEmpty()}"
            )
        }
    }

    suspend fun sendPrompt(
        model: GemmaModelInfo,
        history: List<GemmaLiteRtChatService.ChatMessage>,
        contextSummary: String,
        normalizationContext: String,
        retrievedContext: String,
        currentTab: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?,
        userMessage: String
    ): GemmaLiteRtChatService.DiagnosticResult = withContext(Dispatchers.IO) {
        if (initializedModelId != model.downloadFileName) {
            val init = initialize(model)
            if (!init.ready) {
                throw GemmaLiteRtChatService.DiagnosticFailure(
                    code = "INIT_FAILED",
                    userMessage = init.message
                )
            }
        }
        chatService.generateReply(
            model = model,
            history = history,
            contextSummary = contextSummary,
            normalizationContext = normalizationContext,
            retrievedContext = retrievedContext,
            currentTab = currentTab,
            selectedNodeCode = selectedNodeCode,
            selectedRouteCode = selectedRouteCode,
            userMessage = userMessage
        )
    }

    suspend fun cancelGeneration() = withContext(Dispatchers.IO) {
        chatService.cancelActiveGeneration()
    }

    suspend fun resetConversation() = withContext(Dispatchers.IO) {
        chatService.clearLoadedModel()
        initializedModelId = null
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        chatService.clearLoadedModel()
        initializedModelId = null
    }
}
