package com.mapsupervision.app.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupCoordMatchPolicyTest {
    @Test
    fun reject_when_both_code_and_contractor_conflict() {
        val rejected = DedupCoordMatchPolicy.shouldRejectCoordOnlyMatch(
            incomingCodeKey = "n-001",
            canonicalCodeKey = "n-002",
            incomingContractorKey = "ctr-a",
            canonicalContractorKey = "ctr-b"
        )
        assertTrue(rejected)
    }

    @Test
    fun keep_when_only_code_conflicts() {
        val rejected = DedupCoordMatchPolicy.shouldRejectCoordOnlyMatch(
            incomingCodeKey = "n-001",
            canonicalCodeKey = "n-002",
            incomingContractorKey = "ctr-a",
            canonicalContractorKey = "ctr-a"
        )
        assertFalse(rejected)
    }

    @Test
    fun keep_when_only_contractor_conflicts() {
        val rejected = DedupCoordMatchPolicy.shouldRejectCoordOnlyMatch(
            incomingCodeKey = "n-001",
            canonicalCodeKey = "n-001",
            incomingContractorKey = "ctr-a",
            canonicalContractorKey = "ctr-b"
        )
        assertFalse(rejected)
    }

    @Test
    fun keep_when_missing_keys() {
        val rejected = DedupCoordMatchPolicy.shouldRejectCoordOnlyMatch(
            incomingCodeKey = "",
            canonicalCodeKey = "n-001",
            incomingContractorKey = "",
            canonicalContractorKey = "ctr-a"
        )
        assertFalse(rejected)
    }
}
