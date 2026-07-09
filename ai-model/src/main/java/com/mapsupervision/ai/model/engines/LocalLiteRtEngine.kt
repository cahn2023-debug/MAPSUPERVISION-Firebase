package com.mapsupervision.ai.model.engines

import com.mapsupervision.ai.core.AiCapability
import com.mapsupervision.ai.core.AiCapability.CHAT_ASSISTANT
import com.mapsupervision.ai.core.AiCapability.REPORT_DRAFT
import com.mapsupervision.ai.core.AiCapability.TIMELINE_SUMMARY
import com.mapsupervision.ai.core.AiEngine
import com.mapsupervision.ai.core.AiEngineInterface
import com.mapsupervision.ai.core.AiPayload
import com.mapsupervision.ai.core.AiResult
import com.mapsupervision.ai.core.ChatAssistantPayload
import com.mapsupervision.ai.core.ChatAssistantResult
import com.mapsupervision.ai.core.ReportDraftPayload
import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.ai.core.TimelineSummaryPayload
import com.mapsupervision.ai.core.TimelineSummaryResult
import com.mapsupervision.ai.prompt.ChatActionParser
import com.mapsupervision.domain.repository.LocalLlmRepository
import com.mapsupervision.domain.repository.LocalLlmRequest

class LocalLiteRtEngine(
    private val localLlmRepository: LocalLlmRepository
) : AiEngineInterface {
    override val engineType: AiEngine = AiEngine.LOCAL_LITERT
    override val priority: Int = 95

    private val ruleEngine = RuleBasedEngine()

    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            CHAT_ASSISTANT,
            TIMELINE_SUMMARY,
            REPORT_DRAFT
        )
    }

    override suspend fun isAvailable(): Boolean = localLlmRepository.isReady()

    override suspend fun execute(payload: AiPayload): AiResult {
        return when (payload) {
            is ChatAssistantPayload -> executeChat(payload)
            is TimelineSummaryPayload -> executeTimeline(payload)
            is ReportDraftPayload -> executeReport(payload)
            else -> throw IllegalArgumentException("Unsupported payload: ${payload::class.simpleName}")
        }
    }

    override fun getResourceUsageScore(): Int = 85

    private suspend fun executeChat(payload: ChatAssistantPayload): ChatAssistantResult {
        val response = localLlmRepository.generate(
            LocalLlmRequest(
                prompt = payload.message,
                contextSummary = payload.contextSummary,
                normalizationContext = payload.normalizationContext,
                retrievedContext = payload.retrievedContext,
                currentTab = payload.currentTab,
                selectedNodeCode = payload.selectedNodeCode,
                selectedRouteCode = payload.selectedRouteCode
            )
        )
        val responseText = response.text.trim()
        val parsed = if (responseText.contains("[ACTION:", ignoreCase = true)) {
            ChatActionParser.parseLlmResponse(
                responseText,
                selectedNodeCode = payload.selectedNodeCode,
                normalizationContext = payload.normalizationContext,
                selectedRouteCode = payload.selectedRouteCode
            )
        } else {
            ChatActionParser.parse(
                payload.message,
                contextSummary = mergeContext(payload.contextSummary, payload.retrievedContext),
                selectedNodeCode = payload.selectedNodeCode,
                normalizationContext = mergeNormalization(payload.normalizationContext, payload.retrievedContext),
                selectedRouteCode = payload.selectedRouteCode
            )
        }
        val cleanAnswer = responseText.replace(Regex("""\[ACTION:.*?\]""", RegexOption.IGNORE_CASE), "").trim()
        return parsed.copy(answer = cleanAnswer.ifBlank { parsed.answer })
    }

    private suspend fun executeTimeline(payload: TimelineSummaryPayload): TimelineSummaryResult {
        val base = ruleEngine.execute(payload) as TimelineSummaryResult
        val response = localLlmRepository.generate(
            LocalLlmRequest(
                prompt = buildTimelinePrompt(payload, base),
                contextSummary = buildTimelineContext(payload),
                normalizationContext = "",
                retrievedContext = "",
                currentTab = "progress"
            )
        )
        val summary = response.text.trim().ifBlank { base.summary }
        return base.copy(summary = summary)
    }

    private suspend fun executeReport(payload: ReportDraftPayload): ReportDraftResult {
        val base = ruleEngine.execute(payload) as ReportDraftResult
        val response = localLlmRepository.generate(
            LocalLlmRequest(
                prompt = buildReportPrompt(payload, base),
                contextSummary = buildReportContext(payload),
                normalizationContext = "",
                retrievedContext = "",
                currentTab = "reports"
            )
        )
        val parsed = parseReportSections(response.text)
        return base.copy(
            executiveSummary = parsed.first.ifBlank { base.executiveSummary },
            riskSection = parsed.second.ifBlank { base.riskSection }
        )
    }

    private fun buildTimelinePrompt(
        payload: TimelineSummaryPayload,
        base: TimelineSummaryResult
    ): String {
        return buildString {
            append("Viết một tóm tắt tiến độ ngắn gọn, chính xác, bằng tiếng Việt, không suy diễn thêm số liệu.")
            append(" Tôi đã có issue highlights và recommended actions; bạn chỉ viết lại phần summary.")
            append("\nTổng số node: ").append(payload.progress.size)
            append("\nNode chậm: ").append(payload.progress.count { it.actual < it.planned })
            append("\nSố nhật ký: ").append(payload.logs.size)
            append("\nSố ảnh hiện trường: ").append(payload.photoCount)
            append("\nSummary hiện tại: ").append(base.summary)
            if (base.issueHighlights.isNotEmpty()) {
                append("\nIssue highlights: ").append(base.issueHighlights.joinToString(" | "))
            }
        }
    }

    private fun buildTimelineContext(payload: TimelineSummaryPayload): String {
        val delayedNodes = payload.progress
            .filter { it.actual < it.planned }
            .sortedByDescending { it.planned - it.actual }
            .take(5)
            .joinToString(separator = ", ") { "${it.nodeCode}:${it.actual.toInt()}%/${it.planned.toInt()}%" }
        val latestLogs = payload.logs
            .sortedByDescending { it.dateEpochDay }
            .take(3)
            .joinToString(separator = " | ") { "${it.workItem}:${it.note.take(60)}" }
        return buildString {
            append("delayed_nodes=").append(if (delayedNodes.isBlank()) "none" else delayedNodes)
            if (latestLogs.isNotBlank()) {
                append("\nlatest_logs=").append(latestLogs)
            }
        }
    }

    private fun buildReportPrompt(
        payload: ReportDraftPayload,
        base: ReportDraftResult
    ): String {
        return buildString {
            append("Viết lại hai phần bằng tiếng Việt rõ ràng, có cấu trúc, không thay đổi số liệu.")
            append("\nTrả về đúng định dạng:")
            append("\nEXECUTIVE_SUMMARY: ...")
            append("\nRISK_SECTION: ...")
            append("\nProject: ").append(payload.projectId)
            append("\nTotal nodes: ").append(payload.totalNodes)
            append("\nDelayed nodes: ").append(payload.delayedNodes)
            append("\nAverage actual progress: ").append(payload.avgActualProgress)
            append("\nPhotos: ").append(payload.totalPhotos)
            append("\nCurrent executive summary: ").append(base.executiveSummary)
            append("\nCurrent risk section: ").append(base.riskSection)
        }
    }

    private fun buildReportContext(payload: ReportDraftPayload): String {
        return buildString {
            if (payload.nodesSummary.isNotBlank()) {
                append("nodes_summary=").append(payload.nodesSummary.take(900))
            }
            if (payload.contractorsSummary.isNotBlank()) {
                append("\ncontractors_summary=").append(payload.contractorsSummary.take(900))
            }
            if (payload.notesSummary.isNotBlank()) {
                append("\nnotes_summary=").append(payload.notesSummary.take(900))
            }
        }
    }

    private fun parseReportSections(text: String): Pair<String, String> {
        val executive = text.substringAfter("EXECUTIVE_SUMMARY:", "").substringBefore("RISK_SECTION:").trim()
        val risk = text.substringAfter("RISK_SECTION:", "").trim()
        return executive to risk
    }

    private fun mergeContext(base: String, extra: String): String {
        return listOf(base.trim(), extra.trim()).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun mergeNormalization(base: String, extra: String): String {
        return listOf(base.trim(), extra.trim()).filter { it.isNotBlank() }.joinToString("\n")
    }
}

