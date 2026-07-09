package com.mapsupervision.gis.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRenderPolicyTest {
    @Test
    fun low_ram_device_keeps_first_render_lightweight_without_detail_upgrade() {
        val decision = resolveMapRenderPolicy(
            preferLightweightRender = true,
            requestedShowNumberLabels = true,
            isLowRamDevice = true
        )

        assertEquals(MapRenderTier.LIGHTWEIGHT, decision.renderTier)
        assertFalse(decision.scheduleDetailUpgrade)
    }

    @Test
    fun regular_device_schedules_detail_upgrade_after_lightweight_first_render() {
        val decision = resolveMapRenderPolicy(
            preferLightweightRender = true,
            requestedShowNumberLabels = true,
            isLowRamDevice = false
        )

        assertEquals(MapRenderTier.LIGHTWEIGHT, decision.renderTier)
        assertTrue(decision.scheduleDetailUpgrade)
    }

    @Test
    fun full_render_is_used_once_lightweight_phase_is_over() {
        val decision = resolveMapRenderPolicy(
            preferLightweightRender = false,
            requestedShowNumberLabels = true,
            isLowRamDevice = false
        )

        assertEquals(MapRenderTier.FULL, decision.renderTier)
        assertFalse(decision.scheduleDetailUpgrade)
    }

    @Test
    fun labels_disabled_skips_lightweight_mode_even_on_first_render() {
        val decision = resolveMapRenderPolicy(
            preferLightweightRender = true,
            requestedShowNumberLabels = false,
            isLowRamDevice = false
        )

        assertEquals(MapRenderTier.FULL, decision.renderTier)
        assertFalse(decision.scheduleDetailUpgrade)
    }
}
