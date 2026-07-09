package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportConflictPolicyTest {
    @Test
    fun auto_merge_when_no_conflicts() {
        assertEquals(
            ConflictAction.AUTO_MERGE,
            ImportConflictPolicy.decide(risk = "low", duplicateCount = 0, coordRejectCount = 0)
        )
    }

    @Test
    fun ask_user_for_medium_risk() {
        assertEquals(
            ConflictAction.ASK_USER,
            ImportConflictPolicy.decide(risk = "medium", duplicateCount = 2, coordRejectCount = 1)
        )
    }

    @Test
    fun manual_review_for_high_risk() {
        assertEquals(
            ConflictAction.MANUAL_REVIEW,
            ImportConflictPolicy.decide(risk = "high", duplicateCount = 1, coordRejectCount = 0)
        )
    }

    @Test
    fun severity_tracks_risk_band() {
        assertEquals("low", ImportConflictPolicy.severityFor("low"))
        assertEquals("medium", ImportConflictPolicy.severityFor("medium"))
        assertEquals("high", ImportConflictPolicy.severityFor("high"))
    }

    @Test
    fun evaluate_escalates_batch_review_to_manual_review() {
        val decision = ImportConflictPolicy.evaluate(
            risk = "medium",
            duplicateCount = 1,
            coordRejectCount = 0,
            batchAction = "review_required"
        )

        assertEquals(ConflictAction.MANUAL_REVIEW, decision.action)
        assertEquals("high", decision.severity)
        assertEquals("manual_review", decision.statusMessage)
    }

    @Test
    fun evaluate_keeps_auto_merge_when_no_conflict() {
        val decision = ImportConflictPolicy.evaluate(
            risk = "low",
            duplicateCount = 0,
            coordRejectCount = 0,
            batchAction = "monitor"
        )

        assertEquals(ConflictAction.AUTO_MERGE, decision.action)
        assertFalse(decision.requiresAttention)
    }
}
