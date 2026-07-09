package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DedupRiskSummaryFormatterTest {
    @Test
    fun format_risk_summary_counts_in_fixed_order() {
        val text = DedupRiskSummaryFormatter.format(
            high = 2,
            medium = 5,
            low = 7
        )
        assertEquals("riskByFile=high:2(14%),medium:5(36%),low:7(50%),batchAction=review_required", text)
    }

    @Test
    fun format_batch_action_recommended_when_only_medium_and_low() {
        val text = DedupRiskSummaryFormatter.format(
            high = 0,
            medium = 3,
            low = 2
        )
        assertEquals("riskByFile=high:0(0%),medium:3(60%),low:2(40%),batchAction=review_recommended", text)
    }

    @Test
    fun format_batch_action_monitor_when_only_low() {
        val text = DedupRiskSummaryFormatter.format(
            high = 0,
            medium = 0,
            low = 4
        )
        assertEquals("riskByFile=high:0(0%),medium:0(0%),low:4(100%),batchAction=monitor", text)
    }

    @Test
    fun format_percentages_sum_to_100_when_total_positive() {
        val text = DedupRiskSummaryFormatter.format(
            high = 1,
            medium = 1,
            low = 1
        )
        assertEquals("riskByFile=high:1(34%),medium:1(33%),low:1(33%),batchAction=review_required", text)
    }

    @Test
    fun parse_round_trip_from_format() {
        val text = DedupRiskSummaryFormatter.format(
            high = 2,
            medium = 5,
            low = 7
        )
        val parsed = DedupRiskSummaryFormatter.parse(text)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.high)
        assertEquals(14, parsed.highPct)
        assertEquals(5, parsed.medium)
        assertEquals(36, parsed.mediumPct)
        assertEquals(7, parsed.low)
        assertEquals(50, parsed.lowPct)
        assertEquals("review_required", parsed.batchAction)
    }

    @Test
    fun summarize_and_format_are_consistent() {
        val summary = DedupRiskSummaryFormatter.summarize(
            high = 2,
            medium = 5,
            low = 7
        )
        val text = DedupRiskSummaryFormatter.format(summary)
        assertEquals("riskByFile=high:2(14%),medium:5(36%),low:7(50%),batchAction=review_required", text)
    }

    @Test
    fun parse_returns_null_for_invalid_text() {
        val parsed = DedupRiskSummaryFormatter.parse("riskByFile=broken")
        assertNull(parsed)
    }

    @Test
    fun parse_returns_null_when_percentages_not_100_for_non_zero_total() {
        val parsed = DedupRiskSummaryFormatter.parse(
            "riskByFile=high:1(20%),medium:1(20%),low:1(20%),batchAction=review_required"
        )
        assertNull(parsed)
    }

    @Test
    fun parse_returns_null_when_batch_action_conflicts_with_counts() {
        val parsed = DedupRiskSummaryFormatter.parse(
            "riskByFile=high:1(34%),medium:1(33%),low:1(33%),batchAction=monitor"
        )
        assertNull(parsed)
    }

    @Test
    fun is_valid_accepts_consistent_summary() {
        val summary = DedupRiskSummaryFormatter.RiskSummary(
            high = 0,
            highPct = 0,
            medium = 3,
            mediumPct = 60,
            low = 2,
            lowPct = 40,
            batchAction = "review_recommended"
        )
        assertEquals(true, DedupRiskSummaryFormatter.isValid(summary))
    }

    @Test
    fun summarize_single_risk_high_maps_to_high_bucket() {
        val summary = DedupRiskSummaryFormatter.summarizeSingleRisk("high")
        assertEquals(1, summary.high)
        assertEquals(0, summary.medium)
        assertEquals(0, summary.low)
        assertEquals("review_required", summary.batchAction)
    }

    @Test
    fun summarize_single_risk_medium_maps_to_medium_bucket() {
        val summary = DedupRiskSummaryFormatter.summarizeSingleRisk("medium")
        assertEquals(0, summary.high)
        assertEquals(1, summary.medium)
        assertEquals(0, summary.low)
        assertEquals("review_recommended", summary.batchAction)
    }
}
