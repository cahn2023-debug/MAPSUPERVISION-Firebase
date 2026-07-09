package com.mapsupervision.app.ai.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mapsupervision.ai.model.mediapipe.GemmaChatController
import com.mapsupervision.ai.model.mediapipe.GemmaModelManager
import com.mapsupervision.domain.ai.ChatActionParser
import com.mapsupervision.domain.ai.GemmaModelInfo
import com.mapsupervision.domain.ai.GemmaModelStatus
import com.mapsupervision.ai.model.engines.RuleBasedEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class HeavyAIWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val modelManager: GemmaModelManager,
    private val chatController: GemmaChatController
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PROMPT = "prompt"
        const val KEY_CONTEXT = "context"
        const val KEY_TAB = "tab"
        const val KEY_NODE_CODE = "node_code"
        const val KEY_ROUTE_CODE = "route_code"
        const val KEY_NORMALIZATION = "normalization"
        const val KEY_RESULT = "result"
        const val KEY_ERROR = "error"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val prompt = inputData.getString(KEY_PROMPT).orEmpty()
        val contextSummary = inputData.getString(KEY_CONTEXT).orEmpty()
        val tab = inputData.getString(KEY_TAB).orEmpty()
        val nodeCode = inputData.getString(KEY_NODE_CODE)
        val routeCode = inputData.getString(KEY_ROUTE_CODE)
        val normalizationContext = inputData.getString(KEY_NORMALIZATION).orEmpty()

        if (prompt.isBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Prompt cannot be empty"))
        }

        setProgress(workDataOf("progress" to 20, "status" to "Dang chuan bi..."))

        // 1. Find suitable local LLM model
        val candidates = modelManager.supportedModels()
        val selectedModel: GemmaModelInfo? = candidates.firstOrNull { model ->
            modelManager.status(model) == GemmaModelStatus.READY
        }

        if (selectedModel == null) {
            // No model ready, run fallback rule-based engine directly
            setProgress(workDataOf("progress" to 50, "status" to "Su dung cong cu quy tac..."))
            val ruleEngine = RuleBasedEngine()
            val parsedResult = ChatActionParser.parse(prompt, contextSummary, nodeCode, normalizationContext)
            return@withContext Result.success(workDataOf(KEY_RESULT to parsedResult.answer))
        }

        setProgress(workDataOf("progress" to 40, "status" to "Dang nap model: ${selectedModel.displayName}..."))

        // 2. Initialize and load model safely
        val init = chatController.initialize(selectedModel)
        if (!init.ready) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Failed to init AI model: ${init.message}"))
        }

        setProgress(workDataOf("progress" to 70, "status" to "Dang tinh toan ket qua..."))

        // 3. Process LLM inference
        val response = runCatching {
            chatController.sendPrompt(
                model = selectedModel,
                history = emptyList(),
                contextSummary = contextSummary,
                normalizationContext = normalizationContext,
                retrievedContext = "",
                currentTab = tab,
                selectedNodeCode = nodeCode,
                selectedRouteCode = routeCode,
                userMessage = prompt
            )
        }.getOrElse { e ->
            return@withContext Result.failure(workDataOf(KEY_ERROR to "AI inference error: ${e.message}"))
        }

        setProgress(workDataOf("progress" to 100, "status" to "Hoàn thành"))
        return@withContext Result.success(workDataOf(KEY_RESULT to response.text))
    }
}
