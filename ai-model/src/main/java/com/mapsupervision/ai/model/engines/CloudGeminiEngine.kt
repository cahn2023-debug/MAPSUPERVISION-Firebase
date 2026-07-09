package com.mapsupervision.ai.model.engines

import com.mapsupervision.ai.core.AiCapability
import com.mapsupervision.ai.core.AiCapability.DISCREPANCY_CHECK
import com.mapsupervision.ai.core.AiCapability.IMPORT_MAPPING
import com.mapsupervision.ai.core.AiCapability.OPS_RECOMMENDATION
import com.mapsupervision.ai.core.AiCapability.REPORT_DRAFT
import com.mapsupervision.ai.core.AiCapability.TIMELINE_SUMMARY
import com.mapsupervision.ai.core.AiEngine
import com.mapsupervision.ai.core.AiEngineInterface
import com.mapsupervision.ai.core.AiPayload
import com.mapsupervision.ai.core.AiResult
import com.mapsupervision.ai.core.repository.AiRepository

/**
 * Cloud Gemini Engine - Google Gemini API for cloud-based AI
 * Handles: All text-based capabilities as fallback
 * Requires: Internet connection, API key
 */
class CloudGeminiEngine(
    private val aiRepository: AiRepository
) : AiEngineInterface {
    override val engineType = AiEngine.CLOUD_GEMINI
    override val priority = 50 // Medium priority, fallback for text tasks
    
    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            TIMELINE_SUMMARY,
            REPORT_DRAFT,
            IMPORT_MAPPING,
            OPS_RECOMMENDATION,
            DISCREPANCY_CHECK
        )
    }
    
    override suspend fun isAvailable(): Boolean = true
    
    override suspend fun execute(payload: AiPayload): AiResult {
        return when (payload.capability) {
            IMPORT_MAPPING -> aiRepository.suggestMapping(payload as com.mapsupervision.ai.core.ImportMappingPayload)
            DISCREPANCY_CHECK -> aiRepository.detectDiscrepancies(payload as com.mapsupervision.ai.core.DiscrepancyCheckPayload)
            TIMELINE_SUMMARY -> aiRepository.summarizeDaily(payload as com.mapsupervision.ai.core.TimelineSummaryPayload)
            REPORT_DRAFT -> aiRepository.reportDraft(payload as com.mapsupervision.ai.core.ReportDraftPayload)
            OPS_RECOMMENDATION -> aiRepository.operationRecommendations(payload as com.mapsupervision.ai.core.OpsRecommendationPayload)
            else -> throw IllegalArgumentException("Unsupported capability: ${payload.capability}")
        }
    }
    
    override fun getResourceUsageScore(): Int = 40 // Low local resource usage, but requires network
    
}
