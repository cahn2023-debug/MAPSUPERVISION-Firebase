package com.mapsupervision.ai.model.engines

import android.content.Context
import com.mapsupervision.ai.core.AiCapability
import com.mapsupervision.ai.core.AiCapability.IMPORT_MAPPING
import com.mapsupervision.ai.core.AiCapability.OPS_RECOMMENDATION
import com.mapsupervision.ai.core.AiCapability.REPORT_DRAFT
import com.mapsupervision.ai.core.AiCapability.TIMELINE_SUMMARY
import com.mapsupervision.ai.core.AiEngine
import com.mapsupervision.ai.core.AiEngineInterface
import com.mapsupervision.ai.core.AiPayload
import com.mapsupervision.ai.core.AiResult

/**
 * MediaPipe LLM Engine - On-device LLM for text processing
 * Handles: TIMELINE_SUMMARY, REPORT_DRAFT, IMPORT_MAPPING, OPS_RECOMMENDATION
 * Requires: ~2GB RAM, downloaded model files
 */
class MediaPipeLlmEngine(
    private val context: Context
) : AiEngineInterface {
    override val engineType = AiEngine.MEDIAPIPE_LLM
    override val priority = 100 // Highest priority for text tasks
    
    private var isModelLoaded = false
    
    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            TIMELINE_SUMMARY,
            REPORT_DRAFT,
            IMPORT_MAPPING,
            OPS_RECOMMENDATION
        )
    }
    
    override suspend fun isAvailable(): Boolean {
        return false
    }
    
    override suspend fun execute(payload: AiPayload): AiResult {
        return RuleBasedEngine().execute(payload)
    }
    
    override fun getResourceUsageScore(): Int = 90 // High resource usage
}
