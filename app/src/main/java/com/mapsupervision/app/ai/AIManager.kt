package com.mapsupervision.app.ai

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mapsupervision.app.ai.workers.HeavyAIWorker
import com.mapsupervision.domain.ai.ChatActionParser
import com.mapsupervision.domain.ai.ChatActionType
import com.mapsupervision.domain.ai.ChatAssistantResult
import com.mapsupervision.domain.ai.ChatPendingAction
import com.mapsupervision.domain.ai.ThermalStatus
import com.mapsupervision.app.workspace.GemmaDeviceSnapshotProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceSnapshotProvider: GemmaDeviceSnapshotProvider
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Determines whether the command is simple enough to process instantly without loading LLM.
     */
    fun isSimpleCommand(prompt: String): Boolean {
        val normalized = com.mapsupervision.ai.prompt.ChatDictionaryResolver.normalize(prompt)
        val wordCount = normalized.split("\\s+".toRegex()).size
        if (wordCount <= 4) return true

        // Check for quick keywords using normalized ASCII
        val quickKeywords = listOf("cap nhat", "tien do", "nhat ky", "ghi chu", "nhiem vu", "cong viec", "khoi luong", "vat tu")
        return quickKeywords.any { keyword -> normalized.startsWith(keyword) }
    }

    private val activeJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /**
     * Dispatches command:
     * - Runs instantly for simple commands or if resource status is critical.
     * - Enqueues a HeavyAIWorker for complex commands when system resources are safe.
     */
    fun dispatchCommand(
        prompt: String,
        contextSummary: String,
        normalizationContext: String,
        tab: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?,
        coroutineScope: CoroutineScope,
        onResult: (ChatAssistantResult) -> Unit,
        onProgress: (Int, String) -> Unit
    ) {
        val snapshot = deviceSnapshotProvider.snapshot()
        val isHardwareSafe = snapshot.batteryLevel > 15 &&
                             snapshot.thermalStatus != ThermalStatus.CRITICAL &&
                             snapshot.availableRamMb > 1024

        if (isSimpleCommand(prompt) || !isHardwareSafe) {
            // Process instantly via local regex/dictionary parsing
            val result = ChatActionParser.parse(
                message = prompt,
                contextSummary = contextSummary,
                selectedNodeCode = selectedNodeCode,
                normalizationContext = normalizationContext
            )
            onResult(result)
            return
        }

        // Run heavy task via WorkManager
        val inputData = Data.Builder()
            .putString(HeavyAIWorker.KEY_PROMPT, prompt)
            .putString(HeavyAIWorker.KEY_CONTEXT, contextSummary)
            .putString(HeavyAIWorker.KEY_TAB, tab)
            .putString(HeavyAIWorker.KEY_NODE_CODE, selectedNodeCode)
            .putString(HeavyAIWorker.KEY_ROUTE_CODE, selectedRouteCode)
            .putString(HeavyAIWorker.KEY_NORMALIZATION, normalizationContext)
            .build()

        val actualRequest = OneTimeWorkRequestBuilder<HeavyAIWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(actualRequest)

        // Cancel previous collector jobs to prevent leaks
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        val job = coroutineScope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(actualRequest.id).asFlow().collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt("progress", 0)
                            val status = workInfo.progress.getString("status") ?: "Đang xử lý..."
                            onProgress(progress, status)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val rawResult = workInfo.outputData.getString(HeavyAIWorker.KEY_RESULT).orEmpty()
                            val parsed = ChatActionParser.parseLlmResponse(
                                llmResponse = rawResult,
                                selectedNodeCode = selectedNodeCode,
                                normalizationContext = normalizationContext
                            )
                            onResult(parsed)
                        }
                        WorkInfo.State.FAILED -> {
                            val errorMsg = workInfo.outputData.getString(HeavyAIWorker.KEY_ERROR) ?: "Lỗi xử lý ngầm"
                            onResult(ChatAssistantResult(answer = "Đã xảy ra lỗi: $errorMsg"))
                        }
                        else -> {}
                    }
                }
            }
        }
        activeJobs[actualRequest.id.toString()] = job
    }
}
