package com.mapsupervision.ai.model.mediapipe

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.mapsupervision.ai.core.GemmaModelInfo
import com.mapsupervision.ai.core.LiteRtSafetyGate
import com.mapsupervision.ai.core.ThermalStatus
import com.mapsupervision.ai.core.GemmaModelStatus
import com.mapsupervision.ai.prompt.ChatActionParser
import com.mapsupervision.domain.repository.LocalLlmMessage
import com.mapsupervision.domain.repository.LocalLlmRepository
import com.mapsupervision.domain.repository.LocalLlmRequest
import com.mapsupervision.domain.repository.LocalLlmResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class LocalLiteRtRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: GemmaModelManager,
    private val chatController: GemmaChatController
) : LocalLlmRepository {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun isReady(): Boolean = selectModel() != null

    override suspend fun warmUp(): Boolean {
        val model = selectModel() ?: return false
        return chatController.initialize(model).ready
    }

    override suspend fun generate(request: LocalLlmRequest): LocalLlmResponse {
        val model = selectModel()
            ?: throw GemmaLiteRtChatService.DiagnosticFailure(
                code = "MODEL_MISSING",
                userMessage = "Không có model local sẵn sàng."
            )
        if (!isSafeToUse(model)) {
            val parsed = ChatActionParser.parse(
                message = request.prompt,
                contextSummary = listOf(request.contextSummary.trim(), request.retrievedContext.trim()).filter { it.isNotBlank() }.joinToString("\n"),
                selectedNodeCode = request.selectedNodeCode,
                normalizationContext = listOf(request.normalizationContext.trim(), request.retrievedContext.trim()).filter { it.isNotBlank() }.joinToString("\n"),
                selectedRouteCode = request.selectedRouteCode
            )
            return LocalLlmResponse(
                text = parsed.answer,
                modelName = "rule_based_fallback",
                backendUsed = "rule_based_fallback"
            )
        }
        val init = chatController.initialize(model)
        if (!init.ready) {
            throw GemmaLiteRtChatService.DiagnosticFailure(
                code = "INIT_FAILED",
                userMessage = init.message
            )
        }
        val result = chatController.sendPrompt(
            model = model,
            history = request.history.map { GemmaLiteRtChatService.ChatMessage(it.role, it.text) },
            contextSummary = request.contextSummary,
            normalizationContext = request.normalizationContext,
            retrievedContext = request.retrievedContext,
            currentTab = request.currentTab,
            selectedNodeCode = request.selectedNodeCode,
            selectedRouteCode = request.selectedRouteCode,
            userMessage = request.prompt
        )
        return LocalLlmResponse(
            text = result.text,
            modelName = model.displayName,
            backendUsed = result.backendUsed,
            warnings = result.warnings
        )
    }

    override fun cancel() {
        backgroundScope.launch {
            chatController.cancelGeneration()
        }
    }

    private fun selectModel(): GemmaModelInfo? {
        val readyModels = modelManager.supportedModels().filter { model ->
            modelManager.status(model) == GemmaModelStatus.READY &&
                modelManager.canInitializeLiteRt(model)
        }
        if (readyModels.isEmpty()) return null
        val availableRamMb = currentAvailableRamMb()
        return readyModels
            .sortedBy { it.recommendedMinAvailableRamMb }
            .lastOrNull { availableRamMb >= it.recommendedMinAvailableRamMb }
            ?: readyModels.first()
    }

    private fun currentAvailableRamMb(): Long {
        val activityManager = context.getSystemService<ActivityManager>()
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        return memoryInfo.availMem / (1024 * 1024)
    }

    private fun isSafeToUse(model: GemmaModelInfo): Boolean {
        val availableRamMb = currentAvailableRamMb()
        val batteryManager = context.getSystemService<BatteryManager>()
        val powerManager = context.getSystemService<PowerManager>()
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val batteryStatus = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.CRITICAL
                else -> ThermalStatus.NORMAL
            }
        } else {
            ThermalStatus.NORMAL
        }
        return LiteRtSafetyGate.canRun(model, availableRamMb, thermalStatus, batteryLevel, isCharging)
    }
}

