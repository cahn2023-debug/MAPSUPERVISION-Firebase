package com.mapsupervision.gis.maplibre

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MapStyleAssetsTest {
    private val expectedGlyphUrl = "https://orangemug.github.io/font-glyphs/glyphs/{fontstack}/{range}.pbf"
    private val assetDir = File("src/main/assets")
    private val styleFiles = listOf(
        "style_street.json",
        "style_satellite.json",
        "style_satellite_labels.json",
        "style_dark.json"
    )

    @Test
    fun bundled_styles_define_reference_gis_sources_and_layers() {
        styleFiles.forEach { fileName ->
            val content = File(assetDir, fileName).readText()

            assertTrue("$fileName missing base_raster source", content.contains("\"base_raster\""))
            assertTrue("$fileName missing base layer", content.contains("\"id\": \"base\""))
            assertTrue("$fileName should use the reference glyph source", content.contains(expectedGlyphUrl))
            assertTrue("$fileName missing nodes_source", content.contains("\"nodes_source\""))
            assertTrue("$fileName missing routes_source", content.contains("\"routes_source\""))
            assertTrue("$fileName missing measure_source", content.contains("\"measure_source\""))
            assertTrue("$fileName missing nodes layer", content.contains("\"id\": \"nodes\""))
            assertTrue("$fileName missing nodes_labels layer", content.contains("\"id\": \"nodes_labels\""))
            assertTrue("$fileName missing routes layer", content.contains("\"id\": \"routes\""))
            assertTrue("$fileName missing measure_line layer", content.contains("\"id\": \"measure_line\""))
            assertTrue("$fileName should render node color from feature property", content.contains("\"circle-color\": [\"coalesce\", [\"get\", \"color\"], \"#f97316\"]"))
            assertTrue("$fileName should render node labels from label property", content.contains("\"text-field\": [\"get\", \"label\"]"))
            assertTrue("$fileName should use Roboto Regular labels", content.contains("\"text-font\": [\"Roboto Regular\"]"))
        }
    }

    @Test
    fun street_satellite_and_hybrid_styles_request_vietnamese_labels() {
        listOf("style_street.json", "style_satellite.json", "style_satellite_labels.json").forEach { fileName ->
            val content = File(assetDir, fileName).readText()

            assertTrue("$fileName should request Vietnamese labels", content.contains("hl=vi"))
        }
    }

    @Test
    fun bundled_styles_do_not_include_cluster_or_point_count_filters() {
        styleFiles.forEach { fileName ->
            val content = File(assetDir, fileName).readText()

            assertFalse("$fileName should not include cluster-only layers", content.contains("\"id\": \"node_clusters\""))
            assertFalse("$fileName should not include cluster count layer", content.contains("\"id\": \"node_cluster_count\""))
            assertFalse("$fileName should not include point_count filters", content.contains("\"point_count\""))
        }
    }
}
