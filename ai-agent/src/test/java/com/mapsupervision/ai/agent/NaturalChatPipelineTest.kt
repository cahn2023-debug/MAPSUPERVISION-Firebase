package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.*
import com.mapsupervision.ai.prompt.ChatActionParser
import com.mapsupervision.ai.prompt.DailyLogCanonicalizer
import com.mapsupervision.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalChatPipelineTest {

    @Test
    fun `test intent detection with Vietnamese diacritics and shorthand`() {
        val prompt = "them nhat ky thi cong ho ga"
        val result = ChatActionParser.parse(prompt)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        
        val shorthandPrompt = "ghi chu gap node N01"
        val resultNote = ChatActionParser.parse(shorthandPrompt)
        assertNotNull(resultNote.pendingAction)
        assertEquals(ChatActionType.ADD_NOTE, resultNote.pendingAction?.type)
    }

    @Test
    fun `test fuzzy node matching and category canonicalization`() {
        val context = """
            node_codes=N01_HG, N02_COT, N03_TRAM
            work_categories=Be tong mong:m3,Cap quang:m
        """.trimIndent()

        // 1. Ambiguous label match
        val result1 = ChatActionParser.parse(
            message = "cap nhat khoi luong cap quang cho tru dau tuyen",
            normalizationContext = context
        )
        assertNotNull(result1.pendingAction)
        assertEquals(ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS, result1.pendingAction?.type)
        assertEquals("N01_HG", result1.pendingAction?.materialOrVolumeProgress?.nodeCode)
        assertEquals("Cap quang", result1.pendingAction?.materialOrVolumeProgress?.materialName)
        assertEquals("m", result1.pendingAction?.materialOrVolumeProgress?.unit)

        // 2. Misspelling and shorthand category match
        val result2 = ChatActionParser.parse(
            message = "nhat ky cho ho ga N02 do be tong mong thuc te 10",
            normalizationContext = context
        )
        assertNotNull(result2.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result2.pendingAction?.type)
        assertEquals("N02_COT", result2.pendingAction?.dailyLog?.nodeCode)
        assertEquals("Be tong mong", result2.pendingAction?.dailyLog?.categoryName)
        assertEquals("m3", result2.pendingAction?.dailyLog?.unit)
        assertEquals(10.0, result2.pendingAction?.dailyLog?.volume ?: 0.0, 0.001)
    }

    @Test
    fun `test write policy auto save vs require confirmation`() {
        val context = """
            node_codes=N01_HG
            work_categories=Be tong mong:m3
        """.trimIndent()

        // High confidence, complete fields -> REQUIRE_CONFIRMATION (as all write actions now require confirmation)
        val resultAuto = ChatActionParser.parse(
            message = "cap nhat tien do node N01_HG planned 100 actual 80",
            normalizationContext = context
        )
        assertEquals(WriteDisposition.REQUIRE_CONFIRMATION, resultAuto.writeDisposition)
        assertTrue(resultAuto.confidence!!.isDataComplete)

        // Medium confidence or missing fields -> REJECT (since required planned/actual values are missing)
        val resultConfirm = ChatActionParser.parse(
            message = "cap nhat node N01_HG",
            normalizationContext = context
        )
        assertEquals(WriteDisposition.REJECT, resultConfirm.writeDisposition)
    }

    @Test
    fun `test weather resolution`() {
        val promptSunny = "troi nang lam nhat ky truyen cap node N01_HG"
        val log = DailyLogCanonicalizer.canonicalize(
            params = mapOf("nodeCode" to "N01_HG", "workItem" to "Rải cáp"),
            message = promptSunny,
            normalizationContext = "node_codes=N01_HG",
            selectedNodeCode = null
        )
        assertEquals("Nắng", log.weather)
    }
}

