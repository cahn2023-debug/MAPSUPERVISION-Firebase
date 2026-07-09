package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class DedupAiSummaryFormatterTest {
    @Test
    fun format_contains_all_structured_fields_in_order() {
        val text = DedupAiSummaryFormatter.format(
            score = 78,
            label = "trung binh",
            risk = "medium",
            action = "review_recommended",
            actionNote = "kiem tra mau",
            diagnostics = "strong=60%, weak=20%",
            riskByFile = "riskByFile=high:0(0%),medium:3(60%),low:2(40%),batchAction=review_recommended",
            batchDecision = "review_recommended",
            batchPriority = 1,
            batchNote = "kiem tra xac suat",
            hint = " (goi y)"
        )
        assertEquals(
            "AI dedup score=78/100 (trung binh, risk=medium, action=review_recommended, note=kiem tra mau, strong=60%, weak=20%, riskByFile=high:0(0%),medium:3(60%),low:2(40%),batchAction=review_recommended, batchDecision=review_recommended, batchPriority=1, batchNote=kiem tra xac suat) (goi y)",
            text
        )
    }

    @Test
    fun parse_round_trip_from_format() {
        val text = DedupAiSummaryFormatter.format(
            score = 90,
            label = "cao",
            risk = "low",
            action = "monitor",
            actionNote = "theo doi",
            diagnostics = "strong=85%, weak=5%",
            riskByFile = "riskByFile=high:0(0%),medium:0(0%),low:5(100%),batchAction=monitor",
            batchDecision = "monitor",
            batchPriority = 0,
            batchNote = "co the tiep tuc luong import binh thuong",
            hint = ""
        )
        val parsed = DedupAiSummaryFormatter.parse(text)
        assertEquals(90, parsed?.score)
        assertEquals("monitor", parsed?.batchDecision)
        assertEquals(0, parsed?.batchPriority)
    }

    @Test
    fun parse_returns_null_for_inconsistent_bundle() {
        val text = "AI dedup score=80/100 (trung binh, risk=medium, action=review_recommended, note=x, strong=60%, riskByFile=high:0(0%),medium:3(60%),low:2(40%),batchAction=review_recommended, batchDecision=monitor, batchPriority=0, batchNote=bad)"
        val parsed = DedupAiSummaryFormatter.parse(text)
        assertEquals(null, parsed)
    }
}
