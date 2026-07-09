package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatDictionaryResolverTest {
    @Test
    fun `resolves node by code map label and contractor`() {
        val state = sampleState()
        val resolver = ChatDictionaryResolver.from(state)

        assertEquals("HG01", resolver.resolveNode("HG01")?.value?.code)
        assertEquals("HG02", resolver.resolveNode("MB-02")?.value?.code)
        assertEquals("HG03", resolver.resolveNode("Nha thau C")?.value?.code)
    }

    @Test
    fun `resolves route by code and node references`() {
        val state = sampleState()
        val resolver = ChatDictionaryResolver.from(state)

        assertEquals("R1", resolver.resolveRoute("R1")?.value?.code)
        assertEquals("R1", resolver.resolveRoute("HG01")?.value?.code)
    }

    @Test
    fun `resolves category and unit canonically`() {
        val state = sampleState()
        val resolver = ChatDictionaryResolver.from(state)

        assertNotNull(resolver.resolveCategory("be tong"))
        assertEquals("m3", resolver.resolveCategory("m3")?.value?.unit)
    }

    @Test
    fun `builds canonical prompt context with aliases`() {
        val context = ChatDictionaryResolver.from(sampleState()).buildCanonicalPromptContext()
        assert(context.contains("node_codes=HG01, HG02, HG03"))
        assert(context.contains("work_categories=Be tong:m3, Cap:m"))
        assert(context.contains("route_codes=R1"))
        assert(context.contains("units=m3, m"))
    }

    @Test
    fun `canonicalizes ambiguous user message`() {
        val resolver = ChatDictionaryResolver.from(sampleState())
        val canonical = resolver.canonicalizeMessage("cap nhat tram A hang muc be tong", "HG01", null)
        assert(canonical.contains("HG01"))
        assert(canonical.contains("Be tong:m3"))
    }

    private fun sampleState(): WorkspaceState {
        return WorkspaceState(
            activeProjectId = "P1",
            designNodes = listOf(
                GisNode("1", "P1", "HG01", "Nha thau A", 0.0, 0.0, "A-01"),
                GisNode("2", "P1", "HG02", "Nha thau B", 0.0, 0.0, "MB-02"),
                GisNode("3", "P1", "HG03", "Nha thau C", 0.0, 0.0, "C-03")
            ),
            designRoutes = listOf(
                GisRoute("10", "P1", "R1", "Nha thau A", "HG01", "HG02")
            ),
            workCategories = listOf(
                WorkCategory("100", "P1", "Be tong", "m3", 0L),
                WorkCategory("101", "P1", "Cap", "m", 0L)
            )
        )
    }
}
