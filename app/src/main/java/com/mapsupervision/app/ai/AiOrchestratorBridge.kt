package com.mapsupervision.domain.ai

import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.AiPayload
import com.mapsupervision.ai.core.AiResult
import com.mapsupervision.ai.core.AiDecision
import javax.inject.Inject
import javax.inject.Singleton

typealias OpsRecommendationPayload = com.mapsupervision.ai.core.OpsRecommendationPayload
typealias OpsRecommendationResult = com.mapsupervision.ai.core.OpsRecommendationResult
typealias NoteSummarizationPayload = com.mapsupervision.ai.core.NoteSummarizationPayload
typealias NoteSummarizationResult = com.mapsupervision.ai.core.NoteSummarizationResult
typealias TaskRecommendationPayload = com.mapsupervision.ai.core.TaskRecommendationPayload
typealias TaskRecommendationResult = com.mapsupervision.ai.core.TaskRecommendationResult
typealias ImportMappingPayload = com.mapsupervision.ai.core.ImportMappingPayload
typealias ImportMappingResult = com.mapsupervision.ai.core.ImportMappingResult
typealias ChatAssistantPayload = com.mapsupervision.ai.core.ChatAssistantPayload
typealias ChatAssistantResult = com.mapsupervision.ai.core.ChatAssistantResult
typealias AiDecisionSource = com.mapsupervision.ai.core.AiDecisionSource

typealias ChatActionType = com.mapsupervision.ai.core.ChatActionType
typealias DailyLogDraft = com.mapsupervision.ai.core.DailyLogDraft
typealias ChatPendingAction = com.mapsupervision.ai.core.ChatPendingAction
typealias ChatClarificationPrompt = com.mapsupervision.ai.core.ChatClarificationPrompt
typealias ChatIntentOption = com.mapsupervision.ai.core.ChatIntentOption
typealias SitePhotoUpdateDraft = com.mapsupervision.ai.core.SitePhotoUpdateDraft
typealias ReportDraftDbSaveDraft = com.mapsupervision.ai.core.ReportDraftDbSaveDraft
typealias GemmaDeviceSnapshot = com.mapsupervision.ai.core.GemmaDeviceSnapshot
typealias GemmaModelInfo = com.mapsupervision.ai.core.GemmaModelInfo
typealias GemmaModelSelection = com.mapsupervision.ai.core.GemmaModelSelection
typealias GemmaModelStatus = com.mapsupervision.ai.core.GemmaModelStatus
typealias SummaryRow = com.mapsupervision.ai.core.SummaryRow
typealias WorkPlanDraft = com.mapsupervision.ai.core.WorkPlanDraft
typealias WriteDisposition = com.mapsupervision.ai.core.WriteDisposition

typealias SummaryAggregator = com.mapsupervision.ai.agent.SummaryAggregator

typealias ChatActionParser = com.mapsupervision.ai.prompt.ChatActionParser
typealias DailyLogDateResolver = com.mapsupervision.ai.prompt.DailyLogDateResolver
typealias CanonicalTextNormalizer = com.mapsupervision.ai.prompt.CanonicalTextNormalizer
typealias DictionaryResolverCore = com.mapsupervision.ai.prompt.DictionaryResolverCore
typealias DictionarySnapshot = com.mapsupervision.ai.prompt.DictionarySnapshot
typealias GemmaModelFamily = com.mapsupervision.ai.core.GemmaModelFamily
typealias DictionaryMatch<T> = com.mapsupervision.ai.prompt.DictionaryMatch<T>
typealias ThermalStatus = com.mapsupervision.ai.core.ThermalStatus

@Singleton
class AiOrchestrator @Inject constructor(
    private val aiFacade: AIFacade
) {
    suspend fun <T : AiResult> execute(payload: AiPayload): AiDecision<T> {
        return aiFacade.execute(payload)
    }
}
