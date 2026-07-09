package com.mapsupervision.app.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KmlMapNodeCodesTest {

    @Test
    fun legacy_map_filter_hid_all_kml_nodes() {
        val kmlCodes = listOf(
            "Hub Long Biên - Gia Lâm#pm1_b1_p1",
            "Cap ADSS#pm5_b1_p12",
            "Tuyen 165#pm2_b1_s1".replace("_s1", "_p1")
        )
        kmlCodes.forEach { code ->
            assertTrue("KML import codes contain #pm", code.contains("#pm"))
            assertTrue("Old filter removed this from map: $code", legacyMapFilterWouldHide(code))
        }
    }

    @Test
    fun excel_codes_without_pm_marker_were_not_hidden_by_legacy_filter() {
        assertFalse(legacyMapFilterWouldHide("LINE_A_p2"))
        assertFalse(legacyMapFilterWouldHide("NODE_S"))
    }

    /** Old getFilteredDesignNodesForMap() filter — caused empty map for every KML/KMZ import. */
    private fun legacyMapFilterWouldHide(code: String): Boolean {
        val markerIndex = code.lastIndexOf("#pm")
        if (markerIndex < 0) return false
        val suffix = code.substring(markerIndex)
        return suffix.contains("_p")
    }
}
