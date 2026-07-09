package com.mapsupervision.storage.importer

import com.mapsupervision.domain.model.NonExcelImportMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonStreamingParserTest {

    @Test
    fun parse_geojson_streaming_builds_nodes_and_routes() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "N-01",
                    "contractor": "Team A",
                    "mapNumber": "M-1"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.6, 10.6]
                  }
                },
                {
                  "type": "Feature",
                  "properties": {
                    "name": "R-01",
                    "contractor": "Team A"
                  },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[106.7, 10.7], [106.8, 10.8]]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "sample.geojson",
            projectId = "project-1",
            mapping = null
        )

        assertEquals(1, result.nodes.size)
        assertEquals(1, result.routes.size)
        assertEquals("N-01", result.nodes.single().code)
        assertEquals("Team A", result.nodes.single().contractor)
        assertTrue(result.summary.contains("features=2"))
    }

    @Test
    fun parse_geojson_streaming_filters_points_that_duplicate_route_vertices() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "name": "V-01" },
                  "geometry": { "type": "Point", "coordinates": [106.7, 10.7] }
                },
                {
                  "type": "Feature",
                  "properties": { "name": "V-02" },
                  "geometry": { "type": "Point", "coordinates": [106.8, 10.8] }
                },
                {
                  "type": "Feature",
                  "properties": { "name": "R-01" },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[106.7, 10.7], [106.8, 10.8]]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "route-only.geojson",
            projectId = "project-1",
            mapping = null
        )

        assertEquals(0, result.nodes.size)
        assertEquals(1, result.routes.size)
    }

    @Test
    fun scan_geojson_preview_collects_keys_and_sample_rows_without_loading_full_tree() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "Node 1",
                    "contractor": "Alpha",
                    "customField": "A"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.1, 10.1]
                  }
                }
              ]
            }
        """.trimIndent()

        val scan = scanGeoJsonPreview(
            stream = geoJson.byteInputStream(),
            sourceName = "preview.geojson"
        )

        assertTrue(scan.metadataKeys.contains("customField"))
        assertEquals(1, scan.sampleRows.size)
        assertEquals("Node 1", scan.sampleRows.first()["properties.name"])
        assertEquals("Alpha", scan.sampleRows.first()["properties.contractor"])
    }

    @Test
    fun parse_geojson_streaming_respects_mapping_item_fields() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "N-02",
                    "contractor": "Beta",
                    "material": "Cable 24FO",
                    "quantity": "120"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.2, 10.2]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "mapped.geojson",
            projectId = "project-2",
            mapping = NonExcelImportMapping(
                positionField = "properties.name",
                contractorField = "properties.contractor",
                itemFields = listOf("properties.material", "properties.quantity")
            )
        )

        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes.single().workVolumeSummary.contains("material: Cable 24FO"))
        assertTrue(result.nodes.single().workVolumeSummary.contains("quantity: 120"))
    }

    @Test
    fun parse_geojson_streaming_builds_routes_from_multi_and_polygon_geometries() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "name": "ML-01" },
                  "geometry": {
                    "type": "MultiLineString",
                    "coordinates": [
                      [[106.0, 10.0], [106.1, 10.1]],
                      [[106.2, 10.2], [106.3, 10.3]]
                    ]
                  }
                },
                {
                  "type": "Feature",
                  "properties": { "name": "PG-01" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [[106.4, 10.4], [106.5, 10.4], [106.5, 10.5], [106.4, 10.4]]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "multi.geojson",
            projectId = "project-3",
            mapping = null
        )

        assertEquals(3, result.routes.size)
        assertEquals(10.0, result.routes.first().points.first().first, 0.000001)
        assertEquals(106.0, result.routes.first().points.first().second, 0.000001)
    }

    @Test
    fun parse_geojson_streaming_skips_invalid_route_points() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "name": "BAD-01" },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[999.0, 999.0], [106.1, 10.1]]
                  }
                },
                {
                  "type": "Feature",
                  "properties": { "name": "OK-01" },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[106.1, 10.1], [106.2, 10.2]]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "invalid.geojson",
            projectId = "project-4",
            mapping = null
        )

        assertEquals(1, result.routes.size)
        assertEquals("OK-01", result.routes.single().code)
    }

    @Test
    fun parse_geojson_streaming_maps_network_and_fiber_fields() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "N-01",
                    "ip": "192.168.1.1",
                    "sub": "255.255.255.0",
                    "gw": "192.168.1.254",
                    "sig": "HAS_SIGNAL"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.6, 10.6]
                  }
                },
                {
                  "type": "Feature",
                  "properties": {
                    "name": "R-01",
                    "cores": "48",
                    "conn": "SPLITTER-A"
                  },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[106.7, 10.7], [106.8, 10.8]]
                  }
                }
              ]
            }
        """.trimIndent()

        val mapping = NonExcelImportMapping(
            positionField = "properties.name",
            ipAddressField = "ip",
            subnetField = "sub",
            gatewayField = "gw",
            signalStatusField = "sig",
            fiberCoreCountField = "cores",
            fiberConnectionField = "conn"
        )

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "network_fiber.geojson",
            projectId = "project-1",
            mapping = mapping
        )

        assertEquals(1, result.nodes.size)
        val node = result.nodes.single()
        assertEquals("192.168.1.1", node.ipAddress)
        assertEquals("255.255.255.0", node.subnet)
        assertEquals("192.168.1.254", node.gateway)
        assertEquals(com.mapsupervision.domain.model.NodeSignalStatus.HAS_SIGNAL, node.signalStatus)

        assertEquals(1, result.routes.size)
        val route = result.routes.single()
        assertEquals(48, route.fiberCoreCount)
        assertEquals("SPLITTER-A", route.fiberConnection)
    }
}
