package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class DedupSignalPolicyTest {
    @Test
    fun effective_name_key_is_empty_when_same_as_code() {
        val key = DedupSignalPolicy.effectiveNameKey(
            codeKey = "node-a",
            nameLikeKey = "node-a"
        )
        assertEquals("", key)
    }

    @Test
    fun effective_name_key_keeps_distinct_signal() {
        val key = DedupSignalPolicy.effectiveNameKey(
            codeKey = "n-01",
            nameLikeKey = "n01"
        )
        assertEquals("n01", key)
    }
}
