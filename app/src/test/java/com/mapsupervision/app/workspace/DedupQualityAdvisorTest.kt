package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupQualityAdvisorTest {
    @Test
    fun label_thresholds_are_stable() {
        assertEquals("cao", DedupQualityAdvisor.label(85))
        assertEquals("trung binh", DedupQualityAdvisor.label(65))
        assertEquals("thap", DedupQualityAdvisor.label(64))
    }

    @Test
    fun hint_empty_when_score_not_low() {
        val hint = DedupQualityAdvisor.hint(
            score = 80,
            incomingNodes = 100,
            strongMatches = 70,
            weakMatches = 20,
            coordOnlyRejected = 0,
            incomingRoutes = 50,
            skippedSelfRoutes = 5,
            skippedDuplicateRoutes = 5
        )
        assertEquals("", hint)
    }

    @Test
    fun hint_prefers_self_route_issue_when_dominant() {
        val hint = DedupQualityAdvisor.hint(
            score = 40,
            incomingNodes = 100,
            strongMatches = 10,
            weakMatches = 5,
            coordOnlyRejected = 0,
            incomingRoutes = 20,
            skippedSelfRoutes = 10,
            skippedDuplicateRoutes = 2
        )
        assertTrue(hint.contains("điểm đầu/cuối"))
    }

    @Test
    fun hint_prefers_weak_match_issue_when_dominant() {
        val hint = DedupQualityAdvisor.hint(
            score = 40,
            incomingNodes = 100,
            strongMatches = 10,
            weakMatches = 50,
            coordOnlyRejected = 0,
            incomingRoutes = 20,
            skippedSelfRoutes = 1,
            skippedDuplicateRoutes = 2
        )
        assertTrue(hint.contains("cột mã/tọa độ"))
    }

    @Test
    fun hint_prefers_duplicate_route_issue_when_dominant() {
        val hint = DedupQualityAdvisor.hint(
            score = 40,
            incomingNodes = 100,
            strongMatches = 10,
            weakMatches = 5,
            coordOnlyRejected = 0,
            incomingRoutes = 20,
            skippedSelfRoutes = 1,
            skippedDuplicateRoutes = 15
        )
        assertTrue(hint.contains("dữ liệu tuyến trùng"))
    }

    @Test
    fun hint_prefers_coord_reject_issue_when_dominant_by_focus() {
        val hint = DedupQualityAdvisor.hint(
            score = 40,
            incomingNodes = 100,
            strongMatches = 10,
            weakMatches = 1,
            coordOnlyRejected = 35,
            incomingRoutes = 20,
            skippedSelfRoutes = 1,
            skippedDuplicateRoutes = 1
        )
        assertTrue(hint.contains("đối chiếu mã + nhà thầu"))
    }

    @Test
    fun hint_uses_balanced_fallback_when_no_dominant_issue() {
        val hint = DedupQualityAdvisor.hint(
            score = 40,
            incomingNodes = 100,
            strongMatches = 0,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 20,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertTrue(hint.contains("kiểm tra ngẫu nhiên"))
    }

    @Test
    fun hint_prioritizes_low_confidence_for_tiny_sample() {
        val hint = DedupQualityAdvisor.hint(
            score = 40,
            incomingNodes = 1,
            strongMatches = 0,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertTrue(hint.contains("mẫu import còn ít"))
    }

    @Test
    fun hint_empty_for_stable_quality() {
        val hint = DedupQualityAdvisor.hint(
            score = 90,
            incomingNodes = 100,
            strongMatches = 85,
            weakMatches = 8,
            coordOnlyRejected = 3,
            incomingRoutes = 20,
            skippedSelfRoutes = 1,
            skippedDuplicateRoutes = 1
        )
        assertEquals("", hint)
    }

    @Test
    fun diagnostics_reports_percentages_including_coord_reject() {
        val diagnostics = DedupQualityAdvisor.diagnostics(
            incomingNodes = 100,
            strongMatches = 60,
            weakMatches = 25,
            coordOnlyRejected = 10,
            incomingRoutes = 20,
            skippedSelfRoutes = 5,
            skippedDuplicateRoutes = 2
        )
        assertEquals("strong=60%, weak=25%, coordReject=10%, self=25%, dup=10%, focus=weak, confidence=high", diagnostics)
    }

    @Test
    fun diagnostics_focus_balanced_when_all_zero() {
        val diagnostics = DedupQualityAdvisor.diagnostics(
            incomingNodes = 0,
            strongMatches = 0,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertEquals("strong=0%, weak=0%, coordReject=0%, self=0%, dup=0%, focus=balanced, confidence=low", diagnostics)
    }

    @Test
    fun diagnostics_focus_stable_when_strong_is_high_and_issues_low() {
        val diagnostics = DedupQualityAdvisor.diagnostics(
            incomingNodes = 100,
            strongMatches = 80,
            weakMatches = 10,
            coordOnlyRejected = 5,
            incomingRoutes = 20,
            skippedSelfRoutes = 1,
            skippedDuplicateRoutes = 1
        )
        assertEquals("strong=80%, weak=10%, coordReject=5%, self=5%, dup=5%, focus=stable, confidence=high", diagnostics)
    }

    @Test
    fun risk_is_high_when_score_is_low() {
        val risk = DedupQualityAdvisor.riskLevel(
            score = 40,
            incomingNodes = 100,
            strongMatches = 10,
            weakMatches = 40,
            coordOnlyRejected = 10,
            incomingRoutes = 20,
            skippedSelfRoutes = 5,
            skippedDuplicateRoutes = 4
        )
        assertEquals("high", risk)
    }

    @Test
    fun risk_is_high_when_confidence_low_and_score_not_high() {
        val risk = DedupQualityAdvisor.riskLevel(
            score = 70,
            incomingNodes = 1,
            strongMatches = 1,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertEquals("high", risk)
    }

    @Test
    fun risk_is_low_for_stable_high_confidence_quality() {
        val risk = DedupQualityAdvisor.riskLevel(
            score = 90,
            incomingNodes = 100,
            strongMatches = 80,
            weakMatches = 10,
            coordOnlyRejected = 5,
            incomingRoutes = 20,
            skippedSelfRoutes = 1,
            skippedDuplicateRoutes = 1
        )
        assertEquals("low", risk)
    }

    @Test
    fun action_is_review_required_for_high_risk() {
        assertEquals("review_required", DedupQualityAdvisor.actionByRisk("high"))
    }

    @Test
    fun action_is_review_recommended_for_medium_risk() {
        assertEquals("review_recommended", DedupQualityAdvisor.actionByRisk("medium"))
    }

    @Test
    fun action_is_monitor_for_low_risk() {
        assertEquals("monitor", DedupQualityAdvisor.actionByRisk("low"))
    }

    @Test
    fun action_note_for_review_required() {
        assertEquals("đối chiếu thủ công trước khi chốt dữ liệu", DedupQualityAdvisor.actionNote("review_required"))
    }

    @Test
    fun action_note_for_review_recommended() {
        assertEquals("kiểm tra mẫu 5-10 bản ghi tiêu biểu", DedupQualityAdvisor.actionNote("review_recommended"))
    }

    @Test
    fun action_note_for_monitor() {
        assertEquals("theo dõi và tiếp tục import theo lô", DedupQualityAdvisor.actionNote("monitor"))
    }
}
