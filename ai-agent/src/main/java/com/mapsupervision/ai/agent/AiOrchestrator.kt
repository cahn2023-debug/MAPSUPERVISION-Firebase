package com.mapsupervision.ai.agent

import android.content.Context
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.ai.core.*
import com.mapsupervision.ai.core.repository.AiDecisionCacheStore
import com.mapsupervision.ai.core.repository.AiRepository
import com.mapsupervision.ai.core.repository.TfLiteRepository
import com.mapsupervision.ai.model.device.AndroidDeviceCapabilityDetector
import com.mapsupervision.ai.model.device.ResourceMonitor
import com.mapsupervision.ai.model.engines.CloudGeminiEngine
import com.mapsupervision.ai.model.engines.LocalLiteRtEngine
import com.mapsupervision.ai.model.engines.RuleBasedEngine
import com.mapsupervision.ai.model.engines.TfliteVisionEngine
import com.mapsupervision.domain.repository.LocalLlmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Router AI Orchestrator
 * Routes AI tasks to the most appropriate engine based on:
 * - Device capabilities (RAM, CPU, NPU, battery, thermal status)
 * - Task type (text, vision, utility)
 * - Engine availability
 * - Resource constraints
 */
@Singleton
class AiOrchestrator internal constructor(
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
    private val resourceGate: AiResourceGate,
    initialEngines: List<AiEngineInterface>,
    private val decisionCacheStore: AiDecisionCacheStore? = null
) : AIFacade {
    private val featureFlags = mutableMapOf(
        AiCapability.CHAT_ASSISTANT to true,
        AiCapability.IMPORT_MAPPING to true,
        AiCapability.DISCREPANCY_CHECK to true,
        AiCapability.TIMELINE_SUMMARY to true,
        AiCapability.PHOTO_QUALITY_CHECK to true,
        AiCapability.REPORT_DRAFT to true,
        AiCapability.OPS_RECOMMENDATION to true
    )
    
    private val allEngines = initialEngines.toList()
    private val engines = mutableListOf<AiEngineInterface>()
    
    private var config = SmartRouterConfig()
    private val aiCache = AiCache()

    
    @Inject
    constructor(
        @ApplicationContext context: Context,
        aiRepository: AiRepository,
        tfLiteRepository: TfLiteRepository,
        decisionCacheStore: AiDecisionCacheStore,
        localLlmRepository: LocalLlmRepository
    ) : this(
        deviceCapabilityDetector = AndroidDeviceCapabilityDetector(context),
        resourceGate = ResourceMonitorGate(ResourceMonitor(context)),
        initialEngines = buildDefaultEngines(context, aiRepository, tfLiteRepository, localLlmRepository),
        decisionCacheStore = decisionCacheStore
    )


    constructor(aiRepository: AiRepository) : this(
        deviceCapabilityDetector = StaticDeviceCapabilityDetector(),
        resourceGate = NoOpResourceGate,
        initialEngines = listOf(
            CloudGeminiEngine(aiRepository),
            RuleBasedEngine()
        ),
        decisionCacheStore = null
    )

    init {
        initializeEngines()
    }
    
    private fun initializeEngines() {
        engines.clear()
        engines.addAll(allEngines.filter { engine ->
            when (engine.engineType) {
                AiEngine.LOCAL_LITERT -> config.enableLocalLiteRt
                AiEngine.MEDIAPIPE_LLM -> config.enableMediaPipeLlm
                AiEngine.TFLITE_VISION -> config.enableTfliteVision
                AiEngine.MLKIT_VISION -> config.enableMlKitVision
                AiEngine.CLOUD_GEMINI -> config.enableCloudGemini
                AiEngine.RULE_BASED -> config.enableRuleBased
            }
        })
        engines.sortByDescending { it.priority }
    }
    
    fun setFeatureEnabled(capability: AiCapability, enabled: Boolean) {
        featureFlags[capability] = enabled
    }
    
    fun updateConfig(newConfig: SmartRouterConfig) {
        config = newConfig
        initializeEngines()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : AiResult> execute(payload: AiPayload): AiDecision<T> {
        val capability = payload.capability
        val startedAt = System.currentTimeMillis()
        
        // Check feature flag
        if (featureFlags[capability] != true) {
            val fallback = executeWithEngine(payload, findRuleBasedEngine())
            return AiDecision(
                capability = capability,
                result = fallback as T,
                confidence = 100,
                source = AiDecisionSource.DISABLED,
                reason = "feature_flag_disabled",
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }
        
        // Check in-memory cache
        val cachedResult = aiCache.get(payload)
        if (cachedResult != null) {
            AppLogger.d("ai.orchestrator cache_hit capability=$capability")
            return AiDecision(
                capability = capability,
                result = cachedResult as T,
                confidence = 100,
                source = AiDecisionSource.RULE_BASED,
                reason = "cache_hit",
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }
        val cacheProjectId = cacheProjectId(payload)
        val payloadHash = if (shouldPersist(payload)) payloadHash(payload) else null
        if (!cacheProjectId.isNullOrBlank() && payloadHash != null) {
            val persisted = loadPersisted(payload, cacheProjectId, payloadHash)
            if (persisted != null) {
                aiCache.put(payload, persisted)
                AppLogger.d("ai.orchestrator disk_cache_hit capability=$capability")
                return AiDecision(
                    capability = capability,
                    result = persisted as T,
                    confidence = 100,
                    source = AiDecisionSource.RULE_BASED,
                    reason = "disk_cache_hit",
                    latencyMs = System.currentTimeMillis() - startedAt
                )
            }
        }

        
        if (resourceGate.shouldBypassAi(config.thermalThreshold, config.batteryThreshold)) {
            val fallback = executeWithEngine(payload, findRuleBasedEngine())
            return AiDecision(
                capability = capability,
                result = fallback as T,
                confidence = 100,
                source = AiDecisionSource.RULE_BASED,
                reason = "thermal_battery_bypass",
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }
        
        // Get device capabilities
        val capabilities = deviceCapabilityDetector.detectCapabilities()
        
        // Find best engine for this task
        val selectedEngine = selectBestEngine(capability, capabilities)
        
        // Use background processing for MediaPipe tasks (CPU-intensive)
        val dispatcher = if (selectedEngine.engineType == AiEngine.MEDIAPIPE_LLM) {
            Dispatchers.Default
        } else {
            Dispatchers.IO
        }
        
        return withContext(dispatcher) {
            runCatching {
                val result = executeWithEngine(payload, selectedEngine)
                aiCache.put(payload, result)
                if (!cacheProjectId.isNullOrBlank() && payloadHash != null) {
                    persist(payload, result, cacheProjectId, payloadHash)
                }
                val source = mapEngineToSource(selectedEngine.engineType)
                val confidence = calculateConfidence(selectedEngine, capabilities)
                
                AiDecision(
                    capability = capability,
                    result = result as T,
                    confidence = confidence,
                    source = source,
                    reason = "smart_routing_success",
                    latencyMs = System.currentTimeMillis() - startedAt,
                    estimatedTokens = estimateTokens(payload)
                )
            }.getOrElse { error ->
                AppLogger.e(error, "ai.orchestrator engine_failure capability=$capability engine=${selectedEngine.engineType}")
                
                // Try fallback engines
                val fallbackEngine = findFallbackEngine(selectedEngine, capability, capabilities)
                val fallbackResult = executeWithEngine(payload, fallbackEngine)
                aiCache.put(payload, fallbackResult)
                if (!cacheProjectId.isNullOrBlank() && payloadHash != null) {
                    persist(payload, fallbackResult, cacheProjectId, payloadHash)
                }
                val fallbackSource = mapEngineToSource(fallbackEngine.engineType)
                
                AiDecision(
                    capability = capability,
                    result = fallbackResult as T,
                    confidence = 95,
                    source = fallbackSource,
                    reason = "engine_failure_fallback",
                    warnings = listOf(error.message ?: "unknown_ai_error"),
                    latencyMs = System.currentTimeMillis() - startedAt
                )
            }
.also { decision ->
                AppLogger.d(
                    "ai.decision capability=${decision.capability} source=${decision.source} " +
                        "engine=${selectedEngine.engineType} confidence=${decision.confidence} " +
                        "latencyMs=${decision.latencyMs} estimatedTokens=${decision.estimatedTokens ?: 0} " +
                        "reason=${decision.reason}"
                )
            }
        }
    }

    suspend fun determineExecutionPolicy(payload: AiPayload, capabilities: DeviceCapabilities): AiExecutionPolicy {
        val capability = payload.capability
        if (featureFlags[capability] != true) {
            return AiExecutionPolicy.RULE_BASED
        }
        if (resourceGate.shouldBypassAi(config.thermalThreshold, config.batteryThreshold) ||
            capabilities.thermalStatus == ThermalStatus.CRITICAL ||
            (capabilities.batteryLevel < config.batteryThreshold && !capabilities.isCharging)
        ) {
            return AiExecutionPolicy.RULE_BASED
        }
        val capableEngines = engines.filter { it.canHandle(capability) }
        val availableEngines = capableEngines.filter { it.isAvailable() }
        if (availableEngines.isEmpty()) {
            return AiExecutionPolicy.RULE_BASED
        }
        val suitableEngines = availableEngines.filter { engine ->
            isEngineSafe(engine.engineType, capabilities)
        }
        if (suitableEngines.isEmpty()) {
            return AiExecutionPolicy.RULE_BASED
        }
        val selectedEngine = preferredEngineOrder(capability).firstNotNullOfOrNull { preferred ->
            suitableEngines.firstOrNull { it.engineType == preferred }
        } ?: suitableEngines.maxByOrNull { it.priority }
        return when (selectedEngine?.engineType) {
            AiEngine.LOCAL_LITERT -> AiExecutionPolicy.LOCAL_LITERT
            AiEngine.MEDIAPIPE_LLM -> AiExecutionPolicy.MEDIAPIPE_LLM
            else -> AiExecutionPolicy.RULE_BASED
        }
    }

    private suspend fun selectBestEngine(capability: AiCapability, capabilities: DeviceCapabilities): AiEngineInterface {
        val capableEngines = engines.filter { it.canHandle(capability) }
        
        if (capableEngines.isEmpty()) {
            return findRuleBasedEngine()
        }
        
        val availableEngines = capableEngines.filter { it.isAvailable() }
        
        if (availableEngines.isEmpty()) {
            return findRuleBasedEngine()
        }
        
        val suitableEngines = availableEngines.filter { engine ->
            isEngineSafe(engine.engineType, capabilities)
        }
        
        if (suitableEngines.isEmpty()) {
            return findRuleBasedEngine()
        }

        val preferredOrder = preferredEngineOrder(capability)
        return preferredOrder.firstNotNullOfOrNull { preferred ->
            suitableEngines.firstOrNull { it.engineType == preferred }
        } ?: suitableEngines.maxByOrNull { it.priority } ?: findRuleBasedEngine()
    }
    
    private suspend fun findFallbackEngine(
        failedEngine: AiEngineInterface,
        capability: AiCapability,
        capabilities: DeviceCapabilities
    ): AiEngineInterface {
        val capableEngines = engines.filter { it.canHandle(capability) }
        val remainingEngines = capableEngines.filter { it != failedEngine && it.isAvailable() }
        val suitableEngines = remainingEngines.filter { engine ->
            isEngineSafe(engine.engineType, capabilities)
        }
        
        val preferredOrder = preferredEngineOrder(capability)
        return preferredOrder.firstNotNullOfOrNull { preferred ->
            suitableEngines.firstOrNull { it.engineType == preferred }
        } ?: suitableEngines.maxByOrNull { it.priority } ?: findRuleBasedEngine()
    }
    
    private fun findRuleBasedEngine(): AiEngineInterface {
        return engines.find { it.engineType == AiEngine.RULE_BASED } 
            ?: RuleBasedEngine()
    }
    
    private suspend fun executeWithEngine(payload: AiPayload, engine: AiEngineInterface): AiResult {
        return engine.execute(payload)
    }
    
    private fun mapEngineToSource(engineType: AiEngine, capability: AiCapability? = null): AiDecisionSource {
        if (capability == AiCapability.CHAT_ASSISTANT && engineType == AiEngine.RULE_BASED) {
            return AiDecisionSource.LOCAL_MODEL
        }
        return when (engineType) {
            AiEngine.LOCAL_LITERT -> AiDecisionSource.LOCAL_MODEL
            AiEngine.MEDIAPIPE_LLM -> AiDecisionSource.MEDIAPIPE_LLM
            AiEngine.TFLITE_VISION -> AiDecisionSource.TFLITE_VISION
            AiEngine.MLKIT_VISION -> AiDecisionSource.MLKIT_VISION
            AiEngine.CLOUD_GEMINI -> AiDecisionSource.MODEL
            AiEngine.RULE_BASED -> AiDecisionSource.RULE_BASED
        }
    }
    
    private fun calculateConfidence(engine: AiEngineInterface, capabilities: DeviceCapabilities): Int {
        val baseConfidence = 85
        
        // Adjust confidence based on resource usage
        val resourceScore = engine.getResourceUsageScore()
        val resourceFactor = when {
            resourceScore > 80 && capabilities.availableRamMb < 2048 -> -20
            resourceScore > 60 && capabilities.availableRamMb < 1024 -> -10
            else -> 0
        }
        
        // Adjust confidence based on thermal status
        val thermalFactor = when (capabilities.thermalStatus) {
            ThermalStatus.NORMAL -> 0
            ThermalStatus.MODERATE -> -5
            ThermalStatus.SEVERE -> -15
            ThermalStatus.CRITICAL -> -30
        }
        
        return (baseConfidence + resourceFactor + thermalFactor).coerceIn(50, 100)
    }

    private fun isEngineSafe(engineType: AiEngine, capabilities: DeviceCapabilities): Boolean {
        return when (engineType) {
            AiEngine.LOCAL_LITERT -> {
                LiteRtSafetyGate.canRun(
                    model = GemmaModelInfo(
                        family = GemmaModelFamily.QWEN3_0_6B,
                        displayName = "local",
                        estimatedSizeMb = 0,
                        recommendedMinAvailableRamMb = config.ramThresholdForHeavyAi.coerceAtMost(3072),
                        recommendedMinFreeStorageMb = 0,
                        downloadFileName = "local",
                        expectedBytes = 0L
                    ),
                    availableRamMb = capabilities.availableRamMb,
                    thermalStatus = capabilities.thermalStatus,
                    batteryLevel = capabilities.batteryLevel,
                    isCharging = capabilities.isCharging
                )
            }
            AiEngine.MEDIAPIPE_LLM -> {
                val hasEnoughRam = capabilities.availableRamMb >= config.ramThresholdForHeavyAi
                val thermalOk = capabilities.thermalStatus.ordinal < config.thermalThreshold.ordinal
                deviceCapabilityDetector.canRunHeavyAi(capabilities) && hasEnoughRam && thermalOk
            }
            AiEngine.TFLITE_VISION -> {
                capabilities.availableRamMb >= config.ramThresholdForMediumAi &&
                    deviceCapabilityDetector.canRunMediumAi(capabilities)
            }
            AiEngine.MLKIT_VISION -> deviceCapabilityDetector.canRunLightAi(capabilities)
            AiEngine.CLOUD_GEMINI -> true
            AiEngine.RULE_BASED -> true
        }
    }

    private fun shouldPersist(payload: AiPayload): Boolean = when (payload.capability) {
        AiCapability.IMPORT_MAPPING,
        AiCapability.TIMELINE_SUMMARY,
        AiCapability.OPS_RECOMMENDATION,
        AiCapability.REPORT_DRAFT -> true
        else -> false
    }

    private fun cacheProjectId(payload: AiPayload): String? = when (payload) {
        is DiscrepancyCheckPayload -> payload.projectId
        is ReportDraftPayload -> payload.projectId
        is ImportMappingPayload -> "__global__"
        is TimelineSummaryPayload -> "__global__"
        is OpsRecommendationPayload -> "__global__"
        else -> null
    }

    private fun payloadHash(payload: AiPayload): String {
        val raw = when (payload) {
            is ImportMappingPayload -> "${payload.fileType}|${payload.headers.joinToString("|")}|${payload.sampleRows.joinToString(";") { it.joinToString("|") }}"
            is TimelineSummaryPayload -> "${payload.progress}|${payload.logs}|${payload.photoCount}"
            is ReportDraftPayload -> "${payload.projectId}|${payload.totalNodes}|${payload.delayedNodes}|${payload.avgActualProgress}|${payload.totalPhotos}|${payload.nodesSummary}|${payload.contractorsSummary}|${payload.notesSummary}|${payload.inProgressNodes}|${payload.photoNodesSummary}"
            is OpsRecommendationPayload -> "${payload.totalNodes}|${payload.delayedNodes}|${payload.completionPercent}|${payload.importWarnings}"
            else -> payload.toString()
        }
        return raw.hashCode().toString()
    }

    private suspend fun loadPersisted(payload: AiPayload, projectId: String, payloadHash: String): AiResult? {
        val store = decisionCacheStore ?: return null
        val json = store.get(projectId, payload.capability, payloadHash) ?: return null
        return deserialize(payload.capability, json)
    }

    private suspend fun persist(payload: AiPayload, result: AiResult, projectId: String, payloadHash: String) {
        val store = decisionCacheStore ?: return
        val json = serialize(payload.capability, result) ?: return
        store.put(projectId, payload.capability, payloadHash, json)
    }

    private fun serialize(capability: AiCapability, result: AiResult): String? {
        return when (capability) {
            AiCapability.IMPORT_MAPPING -> {
                result as? ImportMappingResult ?: return null
                org.json.JSONObject()
                    .put("nodeCodeColumn", result.nodeCodeColumn)
                    .put("latitudeColumn", result.latitudeColumn)
                    .put("longitudeColumn", result.longitudeColumn)
                    .put("contractorColumn", result.contractorColumn)
                    .put("itemColumns", org.json.JSONArray(result.itemColumns))
                    .put("requiresManualReview", result.requiresManualReview)
                    .toString()
            }
            AiCapability.TIMELINE_SUMMARY -> {
                result as? TimelineSummaryResult ?: return null
                org.json.JSONObject()
                    .put("summary", result.summary)
                    .put("issueHighlights", org.json.JSONArray(result.issueHighlights))
                    .put("recommendedActions", org.json.JSONArray(result.recommendedActions))
                    .toString()
            }
            AiCapability.REPORT_DRAFT -> {
                result as? ReportDraftResult ?: return null
                org.json.JSONObject()
                    .put("executiveSummary", result.executiveSummary)
                    .put("riskSection", result.riskSection)
                    .put("recommendedActions", org.json.JSONArray(result.recommendedActions))
                    .toString()
            }
            AiCapability.OPS_RECOMMENDATION -> {
                result as? OpsRecommendationResult ?: return null
                org.json.JSONObject()
                    .put("prioritizedActions", org.json.JSONArray(result.prioritizedActions))
                    .put("priority", result.priority)
                    .toString()
            }
            else -> null
        }
    }

    private fun deserialize(capability: AiCapability, json: String): AiResult? {
        val obj = org.json.JSONObject(json)
        return when (capability) {
            AiCapability.IMPORT_MAPPING -> ImportMappingResult(
                nodeCodeColumn = obj.optString("nodeCodeColumn"),
                latitudeColumn = obj.optString("latitudeColumn"),
                longitudeColumn = obj.optString("longitudeColumn"),
                contractorColumn = obj.optString("contractorColumn"),
                itemColumns = obj.optJSONArray("itemColumns")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                }.orEmpty(),
                requiresManualReview = obj.optBoolean("requiresManualReview")
            )
            AiCapability.TIMELINE_SUMMARY -> TimelineSummaryResult(
                summary = obj.optString("summary"),
                issueHighlights = obj.optJSONArray("issueHighlights")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                }.orEmpty(),
                recommendedActions = obj.optJSONArray("recommendedActions")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                }.orEmpty()
            )
            AiCapability.REPORT_DRAFT -> ReportDraftResult(
                executiveSummary = obj.optString("executiveSummary"),
                riskSection = obj.optString("riskSection"),
                recommendedActions = obj.optJSONArray("recommendedActions")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                }.orEmpty()
            )
            AiCapability.OPS_RECOMMENDATION -> OpsRecommendationResult(
                prioritizedActions = obj.optJSONArray("prioritizedActions")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                }.orEmpty(),
                priority = obj.optInt("priority")
            )
            else -> null
        }
    }

    private fun estimateTokens(payload: AiPayload): Int {
        return when (payload) {
            is ImportMappingPayload -> payload.headers.size * 4 + payload.sampleRows.sumOf { it.size * 3 }
            is DiscrepancyCheckPayload -> payload.rows.size * 10
            is TimelineSummaryPayload -> payload.progress.size * 8 + payload.logs.size * 6
            is PhotoQualityPayload -> 32
            is ReportDraftPayload -> 64
            is OpsRecommendationPayload -> 48
            else -> 0
        }.coerceAtLeast(1)
    }

    companion object {
        private fun buildDefaultEngines(
            context: Context,
            aiRepository: AiRepository,
            tfLiteRepository: TfLiteRepository,
            localLlmRepository: LocalLlmRepository
        ): List<AiEngineInterface> =
            listOf(
                LocalLiteRtEngine(localLlmRepository),
                TfliteVisionEngine(context, tfLiteRepository),
                CloudGeminiEngine(aiRepository),
                RuleBasedEngine()
            )
    }

    private fun preferredEngineOrder(capability: AiCapability): List<AiEngine> {
        return when (capability) {
            AiCapability.CHAT_ASSISTANT -> listOf(AiEngine.LOCAL_LITERT, AiEngine.RULE_BASED)
            AiCapability.IMPORT_MAPPING -> listOf(AiEngine.CLOUD_GEMINI, AiEngine.RULE_BASED)
            AiCapability.DISCREPANCY_CHECK -> listOf(AiEngine.TFLITE_VISION, AiEngine.RULE_BASED, AiEngine.CLOUD_GEMINI)
            AiCapability.TIMELINE_SUMMARY -> listOf(AiEngine.LOCAL_LITERT, AiEngine.CLOUD_GEMINI, AiEngine.RULE_BASED)
            AiCapability.PHOTO_QUALITY_CHECK -> listOf(AiEngine.TFLITE_VISION, AiEngine.MLKIT_VISION, AiEngine.RULE_BASED)
            AiCapability.REPORT_DRAFT -> listOf(AiEngine.LOCAL_LITERT, AiEngine.CLOUD_GEMINI, AiEngine.RULE_BASED)
            AiCapability.OPS_RECOMMENDATION -> listOf(AiEngine.RULE_BASED, AiEngine.LOCAL_LITERT, AiEngine.CLOUD_GEMINI)
            AiCapability.NOTE_SUMMARIZATION, AiCapability.TASK_RECOMMENDATION -> listOf(AiEngine.RULE_BASED)
        }
    }


    fun clearCache() {
        aiCache.clear()
    }

    private class AiCache(private val maxSize: Int = 50) {
        private val cache = object : LinkedHashMap<AiPayload, AiResult>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<AiPayload, AiResult>?): Boolean {
                return size > maxSize
            }
        }

        @Synchronized
        fun get(payload: AiPayload): AiResult? = cache[payload]

        @Synchronized
        fun put(payload: AiPayload, result: AiResult) {
            cache[payload] = result
        }

        @Synchronized
        fun clear() {
            cache.clear()
        }
    }
}


internal interface AiResourceGate {
    fun shouldBypassAi(threshold: ThermalStatus, batteryThreshold: Int): Boolean
}

internal class ResourceMonitorGate(private val resourceMonitor: ResourceMonitor) : AiResourceGate {
    override fun shouldBypassAi(threshold: ThermalStatus, batteryThreshold: Int): Boolean =
        resourceMonitor.shouldBypassAi(threshold, batteryThreshold)
}

internal object NoOpResourceGate : AiResourceGate {
    override fun shouldBypassAi(threshold: ThermalStatus, batteryThreshold: Int): Boolean = false
}

internal class StaticDeviceCapabilityDetector(
    private val capabilities: DeviceCapabilities = DeviceCapabilities(
        totalRamMb = 8192,
        availableRamMb = 4096,
        cpuCoreCount = 8,
        hasNpu = false,
        batteryLevel = 100,
        isCharging = true,
        thermalStatus = ThermalStatus.NORMAL
    )
) : DeviceCapabilityDetector {
    override suspend fun detectCapabilities(): DeviceCapabilities = capabilities
}
