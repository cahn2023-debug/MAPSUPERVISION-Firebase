package com.mapsupervision.storage.importer

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import kotlin.math.roundToLong

internal data class NormalizedGeometryLine(
    val points: List<Pair<Double, Double>>,
    val rawPointCount: Int,
    val skippedPointCount: Int
)

internal fun normalizeKmlCoordinatePair(
    longitude: Double,
    latitude: Double
): Pair<Double, Double>? {
    return when {
        isValidLatLon(latitude, longitude) -> latitude to longitude
        isValidLatLon(longitude, latitude) -> longitude to latitude
        else -> null
    }
}

internal fun normalizeGeoJsonCoordinatePair(
    longitude: Double,
    latitude: Double
): Pair<Double, Double>? {
    return when {
        isValidLatLon(latitude, longitude) -> latitude to longitude
        isValidLatLon(longitude, latitude) -> longitude to latitude
        else -> null
    }
}

internal fun normalizeGeometryLine(points: List<Pair<Double, Double>>): NormalizedGeometryLine {
    val normalized = ArrayList<Pair<Double, Double>>(points.size)
    var skipped = 0
    for (point in points) {
        if (!isValidLatLon(point.first, point.second)) {
            skipped += 1
            continue
        }
        if (normalized.lastOrNull() != point) {
            normalized += point
        }
    }
    return NormalizedGeometryLine(
        points = normalized,
        rawPointCount = points.size,
        skippedPointCount = skipped
    )
}

internal fun isRenderableGeometryLine(points: List<Pair<Double, Double>>): Boolean {
    return normalizeGeometryLine(points).points.size >= 2
}

internal fun filterNodesMatchingRouteVertices(
    nodes: List<GisNode>,
    routes: List<GisRoute>
): List<GisNode> {
    if (nodes.isEmpty() || routes.isEmpty()) return nodes
    val routeVertexKeys = routes.asSequence()
        .flatMap { it.points.asSequence() }
        .mapNotNull { coordinateKeyOrNull(it.first, it.second) }
        .toSet()
    if (routeVertexKeys.isEmpty()) return nodes
    return nodes.filterNot { node ->
        coordinateKeyOrNull(node.latitude, node.longitude) in routeVertexKeys
    }
}

private fun isValidLatLon(latitude: Double, longitude: Double): Boolean {
    return latitude in -90.0..90.0 && longitude in -180.0..180.0
}

private fun coordinateKeyOrNull(latitude: Double, longitude: Double): Pair<Long, Long>? {
    val normalized = when {
        isValidLatLon(latitude, longitude) -> latitude to longitude
        isValidLatLon(longitude, latitude) -> longitude to latitude
        else -> return null
    }
    return (normalized.first * COORDINATE_KEY_SCALE).roundToLong() to
        (normalized.second * COORDINATE_KEY_SCALE).roundToLong()
}

private const val COORDINATE_KEY_SCALE = 10_000_000.0
