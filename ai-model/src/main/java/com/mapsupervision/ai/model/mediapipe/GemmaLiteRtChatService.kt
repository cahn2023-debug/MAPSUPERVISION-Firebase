package com.mapsupervision.ai.model.mediapipe

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.mapsupervision.ai.core.GemmaModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

@Singleton
class GemmaLiteRtChatService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: GemmaModelManager
) {
    companion object {
        private const val TAG = "GemmaLiteRtChat"
    }

    data class ChatMessage(
        val role: String,
        val text: String
    )

    data class DiagnosticResult(
        val text: String,
        val backendUsed: String,
        val warnings: List<String> = emptyList()
    )

    data class DiagnosticFailure(
        val code: String,
        val userMessage: String,
        override val cause: Throwable? = null
    ) : IllegalStateException(userMessage, cause)

    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private val sessionMutex = Mutex()
    @Volatile
    private var activeConversation: com.google.ai.edge.litertlm.Conversation? = null
    @Volatile
    private var activeGenerationJob: Job? = null

    suspend fun initializeModel(model: GemmaModelInfo): EngineDiagnostic = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            validateModel(model)
            ensureEngineLocked(modelManager.modelFile(model).absolutePath)
        }
    }

    suspend fun generateReply(
        model: GemmaModelInfo,
        history: List<ChatMessage>,
        contextSummary: String,
        normalizationContext: String,
        retrievedContext: String,
        currentTab: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?,
        userMessage: String
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            validateModel(model)
            val modelFile = modelManager.modelFile(model)
            Log.d(TAG, "generateReply model=${model.displayName} size=${modelFile.length()} tab=$currentTab")

            val engineResult = ensureEngineLocked(modelFile.absolutePath)
            val activeEngine = engineResult.engine
            val conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        Content.Text(
                            buildSystemInstruction(
                                contextSummary = contextSummary,
                                normalizationContext = normalizationContext,
                                retrievedContext = retrievedContext,
                                currentTab = currentTab,
                                selectedNodeCode = selectedNodeCode,
                                selectedRouteCode = selectedRouteCode
                            )
                        )
                    ),
                    initialMessages = history.mapNotNull { message ->
                        when (message.role) {
                            "user" -> Message.user(message.text)
                            "assistant" -> Message.model(message.text)
                            else -> null
                        }
                    }
                )
            )
            activeConversation = conversation
            try {
                val textBuilder = StringBuilder()
                activeGenerationJob = kotlinx.coroutines.currentCoroutineContext()[Job]
                conversation.sendMessageAsync(Message.user(userMessage)).collect { chunk ->
                    val text = chunk.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { it.text }
                    if (text.isNotBlank()) {
                        textBuilder.append(text)
                    }
                }
                DiagnosticResult(
                    text = textBuilder.toString().trim().ifBlank {
                        "Toi chua tao duoc phan hoi tu model cuc bo."
                    },
                    backendUsed = engineResult.backendUsed,
                    warnings = engineResult.warnings
                )
            } catch (failure: DiagnosticFailure) {
                throw failure
            } catch (error: Exception) {
                throw DiagnosticFailure(
                    code = "INFERENCE_FAILED",
                    userMessage = "LiteRT inference failed: ${error.message.orEmpty()}",
                    cause = error
                )
            } finally {
                activeGenerationJob = null
                activeConversation = null
                runCatching { conversation.close() }
            }
        }
    }

    data class EngineDiagnostic(
        val engine: Engine,
        val backendUsed: String,
        val warnings: List<String>
    )

    private fun ensureEngineLocked(modelPath: String): EngineDiagnostic {
        val currentEngine = engine
        if (currentEngine != null && loadedModelPath == modelPath && currentEngine.isInitialized()) {
            return EngineDiagnostic(currentEngine, "reused", emptyList())
        }

        clearLoadedModelLocked()
        val diagnostic = buildEngine(modelPath)
        engine = diagnostic.engine
        loadedModelPath = modelPath
        return diagnostic
    }

    private fun validateModel(model: GemmaModelInfo) {
        val modelFile = modelManager.modelFile(model)
        if (!modelFile.exists()) {
            throw DiagnosticFailure("MODEL_MISSING", "Model ${model.displayName} chua duoc tai xong.")
        }
        if (modelFile.length() != modelManager.expectedBytes(model)) {
            throw DiagnosticFailure("MODEL_INVALID", "Model ${model.displayName} chua day du hoac bi hong.")
        }
    }

    private fun buildEngine(modelPath: String): EngineDiagnostic {
        val cpuBackend = Backend.CPU(numOfThreads = 4)
        val attempts = listOf(
            "cpu" to EngineConfig(
                modelPath = modelPath,
                backend = cpuBackend,
                cacheDir = context.cacheDir.absolutePath
            ),
            "gpu" to EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        val failures = mutableListOf<String>()
        attempts.forEach { (label, config) ->
            Log.d(TAG, "engineInit start backend=$label modelPath=$modelPath")
            val candidate = runCatching { Engine(config) }.getOrElse {
                failures += "$label:create:${it.message.orEmpty()}"
                Log.e(TAG, "engineInit create failed backend=$label reason=${it.message}", it)
                return@forEach
            }
            val initialized = runCatching {
                candidate.initialize()
                candidate
            }
            if (initialized.isSuccess) {
                Log.i(TAG, "engineInit success backend=$label")
                return EngineDiagnostic(
                    engine = initialized.getOrThrow(),
                    backendUsed = label,
                    warnings = failures.toList()
                )
            }
            val error = initialized.exceptionOrNull()
            failures += "$label:init:${error?.message.orEmpty()}"
            Log.e(TAG, "engineInit failed backend=$label reason=${error?.message}", error)
            runCatching { candidate.close() }
        }
        throw DiagnosticFailure(
            code = "INIT_FAILED",
            userMessage = "Không thể khởi tạo Gemma LiteRT trên thiết bị này. ${failures.joinToString(" | ")}"
        )
    }

    suspend fun clearLoadedModel() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            clearLoadedModelLocked()
        }
    }

    private fun clearLoadedModelLocked() {
        runCatching { activeConversation?.close() }
        activeGenerationJob?.cancel()
        activeConversation = null
        activeGenerationJob = null
        runCatching { engine?.close() }
        engine = null
        loadedModelPath = null
    }

    suspend fun cancelActiveGeneration() = withContext(Dispatchers.IO) {
        runCatching { activeConversation?.close() }
        activeGenerationJob?.cancel()
        activeConversation = null
        activeGenerationJob = null
    }

    private fun buildSystemInstruction(
        contextSummary: String,
        normalizationContext: String,
        retrievedContext: String,
        currentTab: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ): String = buildString {
        append(modelManager.systemInstruction())
        append("\n\nNgu canh hien tai:")
        append("\n- tab: ").append(currentTab)
        selectedNodeCode?.takeIf { it.isNotBlank() }?.let { append("\n- node: ").append(it) }
        selectedRouteCode?.takeIf { it.isNotBlank() }?.let { append("\n- route: ").append(it) }
        if (normalizationContext.isNotBlank()) {
            append("\n- canonical_context: ").append(normalizationContext)
        }
        if (retrievedContext.isNotBlank()) {
            append("\n- retrieved_context:\n").append(retrievedContext.trim())
        }
        if (contextSummary.isNotBlank()) {
            append("\n- snapshot: ").append(contextSummary)
        }
    }
}
