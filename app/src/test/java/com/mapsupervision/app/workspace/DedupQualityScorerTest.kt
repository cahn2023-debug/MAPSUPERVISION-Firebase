package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupQualityScorerTest {
    @Test
    fun score_is_100_when_no_input() {
        val score = DedupQualityScorer.score(
            incomingNodes = 0,
            strongMatches = 0,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertEquals(100, score)
    }

    @Test
    fun score_penalizes_weak_and_route_issues() {
        val score = DedupQualityScorer.score(
            incomingNodes = 100,
            strongMatches = 10,
            weakMatches = 50,
            coordOnlyRejected = 10,
            incomingRoutes = 20,
            skippedSelfRoutes = 10,
            skippedDuplicateRoutes = 10
        )
        assertTrue(score < 70)
    }

    @Test
    fun score_rewards_strong_matches() {
        val low = DedupQualityScorer.score(
            incomingNodes = 100,
            strongMatches = 0,
            weakMatches = 10,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        val high = DedupQualityScorer.score(
            incomingNodes = 100,
            strongMatches = 80,
            weakMatches = 10,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertTrue(high > low)
    }

    @Test
    fun score_is_clamped_to_bounds() {
        val minScore = DedupQualityScorer.score(
            incomingNodes = 10,
            strongMatches = 0,
            weakMatches = 10,
            coordOnlyRejected = 10,
            incomingRoutes = 10,
            skippedSelfRoutes = 10,
            skippedDuplicateRoutes = 10
        )
        val maxScore = DedupQualityScorer.score(
            incomingNodes = 10,
            strongMatches = 10,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertTrue(minScore in 0..100)
        assertTrue(maxScore in 0..100)
    }

    @Test
    fun score_penalizes_coord_reject_conflicts() {
        val noReject = DedupQualityScorer.score(
            incomingNodes = 100,
            strongMatches = 20,
            weakMatches = 10,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        val highReject = DedupQualityScorer.score(
            incomingNodes = 100,
            strongMatches = 20,
            weakMatches = 10,
            coordOnlyRejected = 40,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertTrue(highReject < noReject)
    }

    @Test
    fun score_caps_confidence_for_tiny_sample() {
        val score = DedupQualityScorer.score(
            incomingNodes = 1,
            strongMatches = 1,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertEquals(85, score)
    }

    @Test
    fun score_keeps_high_value_when_sample_is_sufficient() {
        val score = DedupQualityScorer.score(
            incomingNodes = 10,
            strongMatches = 10,
            weakMatches = 0,
            coordOnlyRejected = 0,
            incomingRoutes = 0,
            skippedSelfRoutes = 0,
            skippedDuplicateRoutes = 0
        )
        assertEquals(100, score)
    }
}
