package com.mapsupervision.storage.importer

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NonExcelImportMapping
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.UUID

internal data class GeoJsonPreviewScan(
    val metadataKeys: List<String>,
    val sampleRows: List<Map<String, String>>
)

internal fun scanGeoJsonPreview(
    stream: InputStream,
    sourceName: String
): GeoJsonPreviewScan {
    val metadataKeys = linkedSetOf<String>()
    val sampleRows = mutableListOf<Map<String, String>>()
    val base = sourceName.substringBeforeLast(".").take(12).uppercase(Locale.US)
    var featureIndex = 0

    readGeoJsonFeatures(stream) { feature ->
        metadataKeys.addAll(feature.properties.keys.filter { it.isNotBlank() })
        if (sampleRows.size < 5) {
            val defaultCode = feature.properties["name"].orEmpty()
                .ifBlank { feature.properties["id"].orEmpty().ifBlank { "$base-$featureIndex" } }
            val row = linkedMapOf<String, String>()
            row["properties.name"] = feature.properties["name"].orEmpty()
            row["properties.id"] = feature.properties["id"].orEmpty()
            row["Tự sinh mã"] = defaultCode
            row["properties.contractor"] = feature.properties["contractor"].orEmpty()
            row["properties.mapNumber"] = feature.properties["mapNumber"].orEmpty()
            row["Mã tự sinh từ đối tượng"] = defaultCode
            feature.properties.forEach { (key, value) ->
                row.putIfAbsent(key, value)
                row["properties.$key"] = value
            }
            sampleRows += row
        }
        featureIndex += 1
    }

    return GeoJsonPreviewScan(
        metadataKeys = metadataKeys.toList(),
        sampleRows = sampleRows
    )
}

internal fun parseGeoJsonContentStreaming(
    stream: InputStream,
    sourceName: String,
    projectId: String = "",
    mapping: NonExcelImportMapping? = null
): ParsedImportResult {
    val nodes = mutableListOf<GisNode>()
    val routes = mutableListOf<GisRoute>()
    val localLineSegments = ArrayList<CollectedLineSegment>()
    var featureCount = 0
    val base = sourceName.substringBeforeLast(".").take(12).uppercase(Locale.US)

    readGeoJsonFeatures(stream) { feature ->
        val mappedKeys = mappedGeoJsonKeys(mapping)
        val defaultCode = feature.properties["name"].orEmpty()
            .ifBlank { feature.properties["id"].orEmpty().ifBlank { "$base-$featureCount" } }
        val extractedCode = extractGeoJsonCode(feature.properties, mapping, defaultCode)
        val extractedContractor = extractGeoJsonContractor(feature.properties, mapping)
        val extractedMapNumber = extractGeoJsonMapNumber(feature.properties, mapping, defaultCode)
        val customFields = feature.properties.filterKeys { it !in mappedKeys }
        val workVolumeSummary = buildGeoJsonWorkVolumeSummary(feature.properties, customFields, mapping)

        when (feature.geometryType) {
            "Point" -> {
                val point = feature.point ?: return@readGeoJsonFeatures
                nodes += GisNode(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    code = extractedCode,
                    contractor = extractedContractor,
                    latitude = point.first,
                    longitude = point.second,
                    mapNumberLabel = extractedMapNumber,
                    workVolumeSummary = workVolumeSummary,
                    ipAddress = extractGeoJsonValue(feature.properties, mapping?.ipAddressField),
                    subnet = extractGeoJsonValue(feature.properties, mapping?.subnetField),
                    gateway = extractGeoJsonValue(feature.properties, mapping?.gatewayField),
                    signalStatus = parseNodeSignalStatus(extractGeoJsonValue(feature.properties, mapping?.signalStatusField))
                )
            }
            "LineString", "MultiLineString", "Polygon", "MultiPolygon" -> {
                for (points in feature.lineStrings) {
                    val normalizedLine = normalizeGeometryLine(points)
                    if (normalizedLine.points.size < 2) continue
                    localLineSegments += CollectedLineSegment(
                        routeDisplayName = extractedCode,
                        contractor = extractedContractor,
                        mapNumber = extractedMapNumber,
                        workVolumeSummary = "",
                        description = "",
                        points = normalizedLine.points,
                        extendedData = feature.properties,
                        customFields = customFields
                    )
                }
            }
        }
        featureCount += 1
    }

    val totalRouteLengthMeters = mergeAndProcessLines(localLineSegments, projectId, mapping, nodes, routes, base)
    val displayNodes = filterNodesMatchingRouteVertices(nodes, routes)
    return ParsedImportResult(
        summary = "GeoJSON parsed: features=$featureCount, nodes=${displayNodes.size}, routes=${routes.size}, routeLength=${"%.2f".format(Locale.US, totalRouteLengthMeters)}m",
        nodes = displayNodes,
        routes = routes,
        routeLengthMeters = totalRouteLengthMeters
    )
}

private data class GeoJsonFeatureData(
    val geometryType: String = "",
    val point: Pair<Double, Double>? = null,
    val lineStrings: List<List<Pair<Double, Double>>> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)

private fun readGeoJsonFeatures(
    stream: InputStream,
    onFeature: (GeoJsonFeatureData) -> Unit
) {
    JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
        reader.isLenient = true
        reader.beginObject()
        var rootType = ""
        var singleFeature: GeoJsonFeatureData? = null
        var sawFeaturesArray = false

        while (reader.hasNext()) {
            val fieldName = reader.nextName()
            when (fieldName) {
                "type" -> rootType = nextStringOrEmpty(reader)
                "features" -> {
                    sawFeaturesArray = true
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readFeature(reader)?.let(onFeature)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "geometry", "properties" -> {
                    singleFeature = mergeFeatureField(singleFeature, reader, fieldName = fieldName)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!sawFeaturesArray && rootType == "Feature") {
            singleFeature?.let(onFeature)
        }
    }
}

private fun mergeFeatureField(
    current: GeoJsonFeatureData?,
    reader: JsonReader,
    fieldName: String
): GeoJsonFeatureData {
    val existing = current ?: GeoJsonFeatureData()
    return when (fieldName) {
        "geometry" -> {
            val geometry = readGeometry(reader)
            existing.copy(
                geometryType = geometry.type,
                point = geometry.point,
                lineStrings = geometry.lineStrings
            )
        }
        "properties" -> existing.copy(properties = readProperties(reader))
        else -> {
            reader.skipValue()
            existing
        }
    }
}

private fun readFeature(reader: JsonReader): GeoJsonFeatureData? {
    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        reader.skipValue()
        return null
    }
    var geometryType = ""
    var point: Pair<Double, Double>? = null
    var lineStrings: List<List<Pair<Double, Double>>> = emptyList()
    var properties: Map<String, String> = emptyMap()

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "geometry" -> {
                val geometry = readGeometry(reader)
                geometryType = geometry.type
                point = geometry.point
                lineStrings = geometry.lineStrings
            }
            "properties" -> properties = readProperties(reader)
            else -> reader.skipValue()
        }
    }
    reader.endObject()

    return GeoJsonFeatureData(
        geometryType = geometryType,
        point = point,
        lineStrings = lineStrings,
        properties = properties
    )
}

private data class GeometryData(
    val type: String = "",
    val point: Pair<Double, Double>? = null,
    val lineStrings: List<List<Pair<Double, Double>>> = emptyList()
)

private fun readGeometry(reader: JsonReader): GeometryData {
    if (reader.peek() == JsonToken.NULL) {
        reader.nextNull()
        return GeometryData()
    }
    var type = ""
    var point: Pair<Double, Double>? = null
    var lineStrings: List<List<Pair<Double, Double>>> = emptyList()

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "type" -> type = nextStringOrEmpty(reader)
            "coordinates" -> when (type) {
                "Point" -> point = readPointCoordinates(reader)
                "LineString" -> lineStrings = listOf(readLineStringCoordinates(reader))
                "MultiLineString" -> lineStrings = readMultiLineStringCoordinates(reader)
                "Polygon" -> lineStrings = readPolygonCoordinates(reader)
                "MultiPolygon" -> lineStrings = readMultiPolygonCoordinates(reader)
                else -> reader.skipValue()
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return GeometryData(type = type, point = point, lineStrings = lineStrings)
}

private fun readPointCoordinates(reader: JsonReader): Pair<Double, Double>? {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
        reader.skipValue()
        return null
    }
    reader.beginArray()
    val lon = nextDoubleOrNull(reader)
    val lat = nextDoubleOrNull(reader)
    while (reader.hasNext()) reader.skipValue()
    reader.endArray()
    return if (lat != null && lon != null) normalizeGeoJsonCoordinatePair(longitude = lon, latitude = lat) else null
}

private fun readLineStringCoordinates(reader: JsonReader): List<Pair<Double, Double>> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
        reader.skipValue()
        return emptyList()
    }
    val points = mutableListOf<Pair<Double, Double>>()
    reader.beginArray()
    while (reader.hasNext()) {
        readPointCoordinates(reader)?.let(points::add)
    }
    reader.endArray()
    return points
}

private fun readMultiLineStringCoordinates(reader: JsonReader): List<List<Pair<Double, Double>>> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
        reader.skipValue()
        return emptyList()
    }
    val lines = mutableListOf<List<Pair<Double, Double>>>()
    reader.beginArray()
    while (reader.hasNext()) {
        lines += readLineStringCoordinates(reader)
    }
    reader.endArray()
    return lines
}

private fun readPolygonCoordinates(reader: JsonReader): List<List<Pair<Double, Double>>> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
        reader.skipValue()
        return emptyList()
    }
    val rings = mutableListOf<List<Pair<Double, Double>>>()
    reader.beginArray()
    while (reader.hasNext()) {
        rings += readLineStringCoordinates(reader)
    }
    reader.endArray()
    return rings
}

private fun readMultiPolygonCoordinates(reader: JsonReader): List<List<Pair<Double, Double>>> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
        reader.skipValue()
        return emptyList()
    }
    val rings = mutableListOf<List<Pair<Double, Double>>>()
    reader.beginArray()
    while (reader.hasNext()) {
        rings += readPolygonCoordinates(reader)
    }
    reader.endArray()
    return rings
}

private fun readProperties(reader: JsonReader): Map<String, String> {
    if (reader.peek() == JsonToken.NULL) {
        reader.nextNull()
        return emptyMap()
    }
    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        reader.skipValue()
        return emptyMap()
    }
    val properties = linkedMapOf<String, String>()
    reader.beginObject()
    while (reader.hasNext()) {
        val name = reader.nextName()
        when (reader.peek()) {
            JsonToken.STRING -> reader.nextString().trim().takeIf { it.isNotEmpty() }?.let { properties[name] = it }
            JsonToken.NUMBER -> properties[name] = reader.nextString()
            JsonToken.BOOLEAN -> properties[name] = reader.nextBoolean().toString()
            JsonToken.NULL -> reader.nextNull()
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return properties
}

private fun nextStringOrEmpty(reader: JsonReader): String =
    if (reader.peek() == JsonToken.NULL) {
        reader.nextNull()
        ""
    } else {
        reader.nextString()
    }

private fun nextDoubleOrNull(reader: JsonReader): Double? = when (reader.peek()) {
    JsonToken.NUMBER -> reader.nextDouble()
    JsonToken.STRING -> reader.nextString().toDoubleOrNull()
    JsonToken.NULL -> {
        reader.nextNull()
        null
    }
    else -> {
        reader.skipValue()
        null
    }
}

private fun mappedGeoJsonKeys(mapping: NonExcelImportMapping?): Set<String> {
    if (mapping == null) return emptySet()
    val keys = linkedSetOf<String>()
    fun addKey(value: String?) {
        if (value.isNullOrBlank()) return
        keys += value
        keys += value.removePrefix("properties.")
    }
    addKey(mapping.positionField)
    addKey(mapping.coordinateField)
    addKey(mapping.contractorField)
    addKey(mapping.mapNumberField)
    addKey(mapping.objectTypeField)
    addKey(mapping.routeLengthField)
    addKey(mapping.ipAddressField)
    addKey(mapping.subnetField)
    addKey(mapping.gatewayField)
    addKey(mapping.signalStatusField)
    addKey(mapping.fiberCoreCountField)
    addKey(mapping.fiberConnectionField)
    mapping.itemFields.forEach(::addKey)
    return keys
}

private fun extractGeoJsonValue(
    properties: Map<String, String>,
    field: String?
): String {
    if (field.isNullOrBlank()) return ""
    val cleanField = field.removePrefix("properties.")
    return properties[field].orEmpty().ifBlank { properties[cleanField].orEmpty() }
}

private fun extractGeoJsonCode(
    properties: Map<String, String>,
    mapping: NonExcelImportMapping?,
    defaultCode: String
): String {
    val field = mapping?.positionField.orEmpty()
    val cleanField = field.removePrefix("properties.")
    return when (field) {
        "Tự sinh mã" -> defaultCode
        "" -> defaultCode
        else -> properties[field].orEmpty().ifBlank { properties[cleanField].orEmpty() }.ifBlank { defaultCode }
    }
}

private fun extractGeoJsonContractor(
    properties: Map<String, String>,
    mapping: NonExcelImportMapping?
): String {
    val field = mapping?.contractorField
    if (field.isNullOrBlank()) return properties["contractor"].orEmpty()
    val cleanField = field.removePrefix("properties.")
    return when (field) {
        "UPLOAD" -> "UPLOAD"
        else -> properties[field].orEmpty().ifBlank { properties[cleanField].orEmpty() }
    }
}

private fun extractGeoJsonMapNumber(
    properties: Map<String, String>,
    mapping: NonExcelImportMapping?,
    defaultCode: String
): String {
    val field = mapping?.mapNumberField
    if (field.isNullOrBlank()) return properties["mapNumber"].orEmpty()
    val cleanField = field.removePrefix("properties.")
    return when (field) {
        "Mã tự sinh từ đối tượng" -> defaultCode
        else -> properties[field].orEmpty().ifBlank { properties[cleanField].orEmpty() }
    }
}

private fun buildGeoJsonWorkVolumeSummary(
    properties: Map<String, String>,
    customFields: Map<String, String>,
    mapping: NonExcelImportMapping?
): String = buildString {
    if (mapping != null && mapping.itemFields.isNotEmpty()) {
        var hasItems = false
        mapping.itemFields.forEach { itemKey ->
            val cleanKey = itemKey.removePrefix("properties.")
            val value = properties[itemKey].orEmpty().ifBlank { properties[cleanKey].orEmpty() }.trim()
            if (value.isNotEmpty()) {
                if (!hasItems) {
                    append("Cong viec:\n")
                    hasItems = true
                }
                append("  $cleanKey: $value\n")
            }
        }
    }
    if (customFields.isNotEmpty()) {
        if (isNotEmpty()) append("\n")
        append("Thuoc tinh khac:\n")
        customFields.forEach { (key, value) ->
            append("  $key: $value\n")
        }
    }
}.trim()
