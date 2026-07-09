package com.mapsupervision.storage.importer

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.mapsupervision.domain.model.NonExcelImportMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KmlKmzParserTest {

    @Test
    fun parse_kml_with_namespace_prefix() {
        val kml = """
            <kml:kml xmlns:kml="http://www.opengis.net/kml/2.2">
              <kml:Document>
                <kml:Placemark>
                  <kml:name>P1</kml:name>
                  <kml:description>Test description</kml:description>
                  <kml:Point><kml:coordinates>106.7,10.7,0</kml:coordinates></kml:Point>
                </kml:Placemark>
              </kml:Document>
            </kml:kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "sample.kml", "test-project")

        assertEquals(1, result.nodes.size)
        assertEquals(0, result.routes.size)
        assertTrue(result.summary.contains("KML parsed"))
        assertEquals("P1", result.nodes[0].mapNumberLabel)
        assertTrue(result.nodes[0].workVolumeSummary.contains("Test description"))
    }

    @Test
    fun parse_kml_without_namespace_line_string_creates_route() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>LineA</name>
                  <description>Route description</description>
                  <LineString>
                    <coordinates>106.1,10.1,0 106.2,10.2,0</coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "line.kml", "test-project")

        assertEquals(0, result.nodes.size)
        assertEquals(1, result.routes.size)
    }

    @Test
    fun parse_polyline_with_three_points_creates_segmented_routes() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>Polyline3</name>
                <LineString>
                  <coordinates>106.1,10.1,0 106.2,10.2,0 106.3,10.3,0</coordinates>
                </LineString>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "polyline.kml", "test-project")

        // Segmented routes: start and end nodes are NOT created, only route containing points is created
        assertEquals(0, result.nodes.size)
        assertEquals(1, result.routes.size)

        val route = result.routes.first()
        assertEquals(3, route.points.size)
        assertEquals(10.1, route.points[0].first, 0.000001)
        assertEquals(106.1, route.points[0].second, 0.000001)
        assertEquals(10.2, route.points[1].first, 0.000001)
        assertEquals(106.2, route.points[1].second, 0.000001)
        assertEquals(10.3, route.points[2].first, 0.000001)
        assertEquals(106.3, route.points[2].second, 0.000001)
    }

    @Test
    fun parse_kml_filters_point_placemarks_that_duplicate_route_vertices() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>RouteOnly</name>
                  <LineString>
                    <coordinates>106.1,10.1,0 106.2,10.2,0 106.3,10.3,0</coordinates>
                  </LineString>
                </Placemark>
                <Placemark><name>V1</name><Point><coordinates>106.1,10.1,0</coordinates></Point></Placemark>
                <Placemark><name>V2</name><Point><coordinates>106.2,10.2,0</coordinates></Point></Placemark>
                <Placemark><name>V3</name><Point><coordinates>106.3,10.3,0</coordinates></Point></Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parseKmlContentStreaming(kml.byteInputStream(), "route-only.kml", "test-project")

        assertEquals(0, result.nodes.size)
        assertEquals(1, result.routes.size)
        assertEquals(3, result.routes.single().points.size)
    }

    @Test
    fun parse_kmz_with_nested_kml_entry() {
        val tempKmz = File.createTempFile("kml-test", ".kmz")
        tempKmz.deleteOnExit()
        ZipOutputStream(tempKmz.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("nested/doc.kml"))
            zos.write(
                """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark><name>N1</name><Point><coordinates>106.3,10.3,0</coordinates></Point></Placemark>
                </kml>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val result = parseKmzContent(tempKmz, "nested.kmz", "test-project")

        assertEquals(1, result.nodes.size)
        assertTrue(result.summary.startsWith("KMZ parsed:"))
    }

    @Test
    fun parse_kmz_with_doc_kml_linestring_creates_renderable_route() {
        val tempKmz = File.createTempFile("kml-route-test", ".kmz")
        tempKmz.deleteOnExit()
        ZipOutputStream(tempKmz.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("doc.kml"))
            zos.write(
                """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark>
                      <name>RouteInKmz</name>
                      <LineString>
                        <coordinates>
                          106.1,10.1,0
                          106.2,10.2,0
                          106.3,10.3,0
                        </coordinates>
                      </LineString>
                    </Placemark>
                  </Document>
                </kml>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val result = parseKmzContent(tempKmz, "route.kmz", "test-project")

        assertEquals(1, result.routes.size)
        val route = result.routes.single()
        assertEquals(3, route.points.size)
        assertEquals(10.1, route.points.first().first, 0.000001)
        assertEquals(106.1, route.points.first().second, 0.000001)
    }

    @Test
    fun parse_kmz_without_kml_returns_summary() {
        val tempKmz = File.createTempFile("kml-empty", ".kmz")
        tempKmz.deleteOnExit()
        ZipOutputStream(tempKmz.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }

        val result = parseKmzContent(tempKmz, "empty.kmz", "test-project")

        assertEquals(0, result.nodes.size)
        assertEquals(0, result.routes.size)
        assertTrue(result.summary.contains("no embedded KML"))
    }

    @Test
    fun parse_kml_with_extendeddata() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>TestPoint</name>
                <description>Point with extended data</description>
                <ExtendedData>
                  <Data name="type">
                    <value>Camera</value>
                  </Data>
                  <Data name="status">
                    <value>Active</value>
                  </Data>
                </ExtendedData>
                <Point>
                  <coordinates>106.5,10.5,0</coordinates>
                </Point>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "extended.kml", "test-project")

        assertEquals(1, result.nodes.size)
        assertEquals("TestPoint", result.nodes[0].mapNumberLabel)
        assertTrue(result.nodes[0].workVolumeSummary.contains("Point with extended data"))
        assertTrue(result.nodes[0].workVolumeSummary.contains("ExtendedData"))
        assertTrue(result.nodes[0].workVolumeSummary.contains("type: Camera"))
        assertTrue(result.nodes[0].workVolumeSummary.contains("status: Active"))
    }

    @Test
    fun parse_kml_linestring_with_semicolon_delimited_coordinates() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>SemiLine</name>
                <LineString>
                  <coordinates>106.1,10.1,0;106.2,10.2,0;106.3,10.3,0</coordinates>
                </LineString>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "semi.kml", "test-project")

        // Segmented routes: start and end nodes are NOT created, only 1 route segment is created containing points
        assertEquals(0, result.nodes.size)
        assertEquals(1, result.routes.size)
    }

    @Test
    fun parse_kml_multigeometry_point_and_linestring() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>Multi</name>
                <MultiGeometry>
                  <Point><coordinates>106.0,10.0,0</coordinates></Point>
                  <LineString><coordinates>106.1,10.1,0 106.2,10.2,0</coordinates></LineString>
                </MultiGeometry>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "multi.kml", "test-project")

        assertEquals(1, result.nodes.size)
        assertEquals(1, result.routes.size)
    }

    @Test
    fun parse_kml_streaming_multiline_linestring_creates_lat_lon_route_points() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>StreamLine</name>
                  <LineString>
                    <coordinates>
                      106.4,10.4,0
                      106.5,10.5,0
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parseKmlContentStreaming(kml.byteInputStream(), "stream.kml", "test-project")

        assertEquals(1, result.routes.size)
        val route = result.routes.single()
        assertEquals(listOf(10.4 to 106.4, 10.5 to 106.5), route.points)
    }

    @Test
    fun parse_kml_polygon_outer_ring_creates_route_outline() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>Boundary</name>
                <Polygon>
                  <outerBoundaryIs>
                    <LinearRing>
                      <coordinates>106.0,10.0,0 106.1,10.0,0 106.1,10.1,0 106.0,10.0,0</coordinates>
                    </LinearRing>
                  </outerBoundaryIs>
                </Polygon>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "polygon.kml", "test-project")

        assertEquals(1, result.routes.size)
        assertTrue(result.routes.single().points.size >= 3)
    }

    @Test
    fun parse_kml_point_with_extra_coordinates_still_one_node() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>Pt</name>
                <Point><coordinates>106.5,10.5,0</coordinates></Point>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "point.kml", "test-project")

        assertEquals(1, result.nodes.size)
        assertEquals(0, result.routes.size)
    }

    @Test
    fun parse_kml_multiple_placemarks_in_folders() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Folder>
                  <Placemark><name>A</name><Point><coordinates>106.1,10.1,0</coordinates></Point></Placemark>
                  <Placemark><name>B</name><Point><coordinates>106.2,10.2,0</coordinates></Point></Placemark>
                </Folder>
                <Placemark><name>C</name><Point><coordinates>106.3,10.3,0</coordinates></Point></Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "folders.kml", "test-project")

        assertEquals(3, result.nodes.size)
        assertEquals(setOf("A", "B", "C"), result.nodes.map { it.mapNumberLabel }.toSet())
    }

    @Test
    fun parse_kml_routes_reference_existing_node_codes() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>RouteRef</name>
                <LineString>
                  <coordinates>106.1,10.1,0 106.2,10.2,0</coordinates>
                </LineString>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "route-ref.kml", "test-project")

        assertEquals(1, result.routes.size)
        val route = result.routes.first()
        assertEquals("", route.startNodeCode)
        assertEquals("", route.endNodeCode)
    }

    @Test
    fun parse_kml_with_simpledata_extended_metadata() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>SimplePt</name>
                <ExtendedData>
                  <SchemaData schemaUrl="#schema">
                    <SimpleData name="layer">Cable</SimpleData>
                  </SchemaData>
                </ExtendedData>
                <Point><coordinates>106.4,10.4,0</coordinates></Point>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "simple.kml", "test-project")

        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes[0].workVolumeSummary.contains("layer: Cable"))
    }

    @Test
    fun parse_kml_strips_html_from_description() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>HtmlDesc</name>
                <description><![CDATA[<b>Bold</b> text here]]></description>
                <Point><coordinates>106.6,10.6,0</coordinates></Point>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "html.kml", "test-project")

        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes[0].workVolumeSummary.contains("Bold text here"))
        assertTrue(!result.nodes[0].workVolumeSummary.contains("<b>"))
    }

    @Test
    fun parse_kmz_merges_multiple_kml_entries() {
        val tempKmz = File.createTempFile("kml-multi", ".kmz")
        tempKmz.deleteOnExit()
        ZipOutputStream(tempKmz.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("layers/extra.kml"))
            zos.write(
                """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark><name>Extra</name><Point><coordinates>106.8,10.8,0</coordinates></Point></Placemark>
                </kml>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("doc.kml"))
            zos.write(
                """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark><name>Main</name><Point><coordinates>106.7,10.7,0</coordinates></Point></Placemark>
                </kml>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val result = parseKmzContent(tempKmz, "multi.kmz", "test-project")

        assertEquals(2, result.nodes.size)
        assertTrue(result.summary.contains("2 KML file(s)"))
        assertTrue(result.summary.contains("doc.kml"))
        assertTrue(result.summary.contains("extra.kml"))
        assertEquals(setOf("Main", "Extra"), result.nodes.map { it.mapNumberLabel }.toSet())
    }

    @Test
    fun parse_real_world_adss_kmz_sample() {
        val kmz = javaClass.classLoader?.getResource("sample-adss.kmz")?.file
            ?: return // skip if sample not bundled in CI
        val file = File(kmz)
        if (!file.exists()) return

        val result = parseKmzContent(file, "sample-adss.kmz", "test-project")

        // After vertex-skipping: each LineString placemark produces 2 nodes (start + end) + 1 route
        // Point placemarks produce 1 node each. Expect at least some nodes and routes.
        assertTrue("Expected nodes from real KMZ sample: ${result.summary}", result.nodes.isNotEmpty())
        assertTrue("Expected routes from real KMZ sample: ${result.summary}", result.routes.isNotEmpty())
        // Hanoi area coordinates
        assertTrue(result.nodes.any { it.latitude in 20.0..22.0 && it.longitude in 105.0..106.5 })
    }

    @Test
    fun parse_segmented_kml_merges_routes() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>LINE_A_S1</name>
                  <LineString><coordinates>106.0,10.0,0 106.1,10.1,0</coordinates></LineString>
                </Placemark>
                <Placemark>
                  <name>LINE_A_S2</name>
                  <LineString><coordinates>106.1,10.1,0 106.2,10.2,0</coordinates></LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "test_segments.kml", "test-project")

        // Check that routes are merged into a single one named LINE_A
        assertEquals(1, result.routes.size)
        val route = result.routes.first()
        assertEquals("LINE_A", route.code)
        assertEquals("", route.startNodeCode)
        assertEquals("", route.endNodeCode)

        // No nodes are created
        assertEquals(0, result.nodes.size)
    }

    @Test
    fun parse_kml_maps_routeLength_directly_to_route_and_not_to_node_summaries() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>Route_1</name>
                  <ExtendedData>
                    <Data name="routeLength">
                      <value>250 m</value>
                    </Data>
                  </ExtendedData>
                  <LineString>
                    <coordinates>106.1,10.1,0 106.2,10.2,0</coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val mapping = NonExcelImportMapping(
            positionField = "Tên đối tượng (Placemark)",
            routeLengthField = "routeLength"
        )

        val result = parseKmlContent(kml.byteInputStream(), "test_length.kml", "test-project", mapping)

        assertEquals(1, result.routes.size)
        val route = result.routes.first()
        assertEquals("250 m", route.designLength)

        assertEquals(0, result.nodes.size)
    }

    @Test
    fun parse_kml_mapping_accepts_utf8_vietnamese_defaults() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>TuyenUtf8</name>
                <LineString><coordinates>106.1,10.1,0 106.2,10.2,0</coordinates></LineString>
              </Placemark>
            </kml>
        """.trimIndent()

        val result = parseKmlContentStreaming(
            kml.byteInputStream(),
            "utf8.kml",
            "test-project",
            NonExcelImportMapping(positionField = "Tên đối tượng (Placemark)")
        )

        assertEquals(1, result.routes.size)
        assertEquals("TUYENUTF8", result.routes.single().code)
    }

    @Test
    fun parse_kml_calculates_fallback_length_when_no_mapping_provided() {
        val kml = """
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>Route_1</name>
                  <LineString>
                    <coordinates>106.1,10.1,0 106.2,10.2,0</coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = parseKmlContent(kml.byteInputStream(), "test_calc.kml", "test-project", null)

        assertEquals(1, result.routes.size)
        val route = result.routes.first()
        // Calculated length is haversine distance (~15.6km)
        assertTrue(route.designLength != null)
        assertTrue(route.designLength!!.endsWith(" m"))
        val lengthVal = route.designLength!!.substringBefore(" ").toDoubleOrNull()
        assertTrue(lengthVal != null && lengthVal > 15000.0)

        assertEquals(0, result.nodes.size)
    }
}

