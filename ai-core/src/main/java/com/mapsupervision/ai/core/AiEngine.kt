package com.mapsupervision.ai.core

/**
 * AI Engine Types
 */
enum class AiEngine {
    LOCAL_LITERT,
    MEDIAPIPE_LLM,
    TFLITE_VISION,
    MLKIT_VISION,
    CLOUD_GEMINI,
    RULE_BASED
}

/**
 * Interface for AI engine implementations
 * Each engine can handle specific types of AI tasks
 */
interface AiEngineInterface {
    val engineType: AiEngine
    val priority: Int // Higher priority engines are tried first
    
    /**
     * Check if this engine can handle the given capability
     */
    fun canHandle(capability: AiCapability): Boolean
    
    /**
     * Check if this engine is available on the current device
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Execute the AI task
     */
    suspend fun execute(payload: AiPayload): AiResult
    
    /**
     * Get the estimated resource usage (0-100 scale)
     */
    fun getResourceUsageScore(): Int
}

/**
 * Device capability information for routing decisions
 */
data class DeviceCapabilities(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val cpuCoreCount: Int,
    val hasNpu: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val thermalStatus: ThermalStatus
)

enum class ThermalStatus {
    NORMAL,
    MODERATE,
    SEVERE,
    CRITICAL
}

/**
 * Device capability detector
 */
interface DeviceCapabilityDetector {
    suspend fun detectCapabilities(): DeviceCapabilities
    
    /**
     * Check if device is suitable for heavy AI tasks (MediaPipe LLM)
     */
    fun canRunHeavyAi(capabilities: DeviceCapabilities): Boolean {
        return capabilities.availableRamMb >= 4096 && 
               capabilities.thermalStatus != ThermalStatus.CRITICAL &&
               capabilities.batteryLevel > 20
    }
    
    /**
     * Check if device is suitable for medium AI tasks (TFLite Vision)
     */
    fun canRunMediumAi(capabilities: DeviceCapabilities): Boolean {
        return capabilities.availableRamMb >= 2048 && 
               capabilities.thermalStatus != ThermalStatus.SEVERE &&
               capabilities.batteryLevel > 15
    }
    
    /**
     * Check if device is suitable for light AI tasks (ML Kit)
     */
    fun canRunLightAi(capabilities: DeviceCapabilities): Boolean {
        return capabilities.availableRamMb >= 512 && 
               capabilities.thermalStatus != ThermalStatus.CRITICAL
    }
}

/**
 * Smart routing strategy for AI tasks
 */
data class RoutingStrategy(
    val capability: AiCapability,
    val preferredEngines: List<AiEngine>,
    val fallbackEngines: List<AiEngine>
)

/**
 * Smart Router configuration
 */
data class SmartRouterConfig(
    val enableLocalLiteRt: Boolean = true,
    val enableMediaPipeLlm: Boolean = true,
    val enableTfliteVision: Boolean = true,
    val enableMlKitVision: Boolean = true,
    val enableCloudGemini: Boolean = true,
    val enableRuleBased: Boolean = true,
    val thermalThreshold: ThermalStatus = ThermalStatus.SEVERE,
    val batteryThreshold: Int = 20,
    val ramThresholdForHeavyAi: Long = 4096,
    val ramThresholdForMediumAi: Long = 2048
)
