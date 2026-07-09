package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class DedupBatchDecisionAdvisorTest {
    @Test
    fun decision_is_critical_when_high_pct_large() {
        val summary = DedupRiskSummaryFormatter.RiskSummary(
            high = 4,
            highPct = 40,
            medium = 3,
            mediumPct = 30,
            low = 3,
            lowPct = 30,
            batchAction = "review_required"
        )
        assertEquals("critical_review", DedupBatchDecisionAdvisor.decision(summary))
    }

    @Test
    fun decision_is_review_required_when_has_high_but_not_critical() {
        val summary = DedupRiskSummaryFormatter.RiskSummary(
            high = 1,
            highPct = 10,
            medium = 4,
            mediumPct = 40,
            low = 5,
            lowPct = 50,
            batchAction = "review_required"
        )
        assertEquals("review_required", DedupBatchDecisionAdvisor.decision(summary))
    }

    @Test
    fun decision_is_monitor_when_low_dominates_without_high_medium_pressure() {
        val summary = DedupRiskSummaryFormatter.RiskSummary(
            high = 0,
            highPct = 0,
            medium = 1,
            mediumPct = 10,
            low = 9,
            lowPct = 90,
            batchAction = "review_recommended"
        )
        assertEquals("monitor", DedupBatchDecisionAdvisor.decision(summary))
    }

    @Test
    fun decision_from_summary_text_uses_parser_output() {
        val text = "riskByFile=high:0(0%),medium:6(60%),low:4(40%),batchAction=review_recommended"
        assertEquals("review_recommended", DedupBatchDecisionAdvisor.decisionFromSummaryText(text))
    }

    @Test
    fun decision_from_summary_text_fallbacks_when_invalid() {
        assertEquals("review_required", DedupBatchDecisionAdvisor.decisionFromSummaryText("riskByFile=invalid"))
    }

    @Test
    fun priority_mapping_is_stable() {
        assertEquals(3, DedupBatchDecisionAdvisor.priority("critical_review"))
        assertEquals(2, DedupBatchDecisionAdvisor.priority("review_required"))
        assertEquals(1, DedupBatchDecisionAdvisor.priority("review_recommended"))
        assertEquals(0, DedupBatchDecisionAdvisor.priority("monitor"))
    }

    @Test
    fun bundle_from_summary_text_contains_decision_priority_and_note() {
        val text = "riskByFile=high:0(0%),medium:6(60%),low:4(40%),batchAction=review_recommended"
        val bundle = DedupBatchDecisionAdvisor.bundleFromSummaryText(text)
        assertEquals("review_recommended", bundle.decision)
        assertEquals(1, bundle.priority)
        assertEquals("kiem tra xac suat theo mau dai dien", bundle.note)
    }

    @Test
    fun bundle_from_invalid_summary_text_uses_safe_fallback() {
        val bundle = DedupBatchDecisionAdvisor.bundleFromSummaryText("riskByFile=invalid")
        assertEquals("review_required", bundle.decision)
        assertEquals(2, bundle.priority)
        assertEquals("kiem tra thu cong cac file high truoc khi xac nhan", bundle.note)
    }
}
