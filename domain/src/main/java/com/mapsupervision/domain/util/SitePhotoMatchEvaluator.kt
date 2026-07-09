package com.mapsupervision.domain.util

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.SitePhoto
import kotlin.math.cos

private const val MATCH_DISTANCE_THRESHOLD_METERS = 30.0
private const val EARTH_METERS_PER_DEGREE = 111_320.0

enum class PhotoTargetKind {
    NODE,
    ROUTE
}

data class PhotoMatchEvaluation(
    val targetKind: PhotoTargetKind,
    val targetCode: String,
    val distanceMeters: Double?,
    val isMatched: Boolean
)

fun evaluateSitePhotoMatch(
    photo: SitePhoto,
    nodes: List<GisNode>,
    routes: List<GisRoute>
): PhotoMatchEvaluation {
    val nodesById = nodes.associateBy { it.id }
    val nodesByCode = nodes.associateBy { it.code }
    val routesById = routes.associateBy { it.id }
    val routesByCode = routes.associateBy { it.code }
    return evaluateSitePhotoMatch(photo, nodesById, nodesByCode, routesById, routesByCode)
}

fun evaluateSitePhotoMatch(
    photo: SitePhoto,
    nodesById: Map<String, GisNode>,
    nodesByCode: Map<String, GisNode>,
    routesById: Map<String, GisRoute>,
    routesByCode: Map<String, GisRoute>
): PhotoMatchEvaluation {
    val targetNode = photo.matchedNodeId?.let(nodesById::get)
        ?: photo.matchedNodeCode?.let(nodesByCode::get)
        ?: nodesByCode[photo.objectCode]
    if (targetNode != null) {
        val distance = if (photo.latitude != null && photo.longitude != null) {
            Haversine.distanceInMeters(
                photo.latitude,
                photo.longitude,
                targetNode.latitude,
                targetNode.longitude
            )
        } else {
            null
        }
        return PhotoMatchEvaluation(
            targetKind = PhotoTargetKind.NODE,
            targetCode = targetNode.code,
            distanceMeters = distance,
            isMatched = distance != null && distance <= MATCH_DISTANCE_THRESHOLD_METERS
        )
    }

    val targetRoute = photo.matchedRouteId?.let(routesById::get)
        ?: photo.matchedRouteCode?.let(routesByCode::get)
        ?: routesByCode[photo.objectCode]
    if (targetRoute != null) {
        val distance = if (photo.latitude != null && photo.longitude != null) {
            distanceToRouteMeters(
                photo.latitude,
                photo.longitude,
                targetRoute,
                nodesByCode
            )
        } else {
            null
        }
        return PhotoMatchEvaluation(
            targetKind = PhotoTargetKind.ROUTE,
            targetCode = targetRoute.code,
            distanceMeters = distance,
            isMatched = distance != null && distance <= MATCH_DISTANCE_THRESHOLD_METERS
        )
    }

    return PhotoMatchEvaluation(
        targetKind = PhotoTargetKind.NODE,
        targetCode = photo.objectCode,
        distanceMeters = null,
        isMatched = false
    )
}

fun distanceToRouteMeters(
    latitude: Double,
    longitude: Double,
    route: GisRoute,
    nodesByCode: Map<String, GisNode>
): Double? {
    val routePoints = route.points
        .map { (lat, lon) -> lat to lon }
        .filter { (lat, lon) -> lat.isFinite() && lon.isFinite() }
    if (routePoints.size >= 2) {
        return distanceToPolylineMeters(latitude, longitude, routePoints)
    }

    val fallbackPoints = buildList {
        nodesByCode[route.startNodeCode]?.let { add(it.latitude to it.longitude) }
        nodesByCode[route.endNodeCode]?.let { add(it.latitude to it.longitude) }
    }
    return when {
        fallbackPoints.size >= 2 -> distanceToPolylineMeters(latitude, longitude, fallbackPoints)
        fallbackPoints.size == 1 -> Haversine.distanceInMeters(
            latitude,
            longitude,
            fallbackPoints.first().first,
            fallbackPoints.first().second
        )
        else -> null
    }
}

private fun distanceToPolylineMeters(
    latitude: Double,
    longitude: Double,
    points: List<Pair<Double, Double>>
): Double? {
    if (points.size < 2) return null
    var bestDistance: Double? = null
    for (index in 0 until points.lastIndex) {
        val segmentDistance = distanceToSegmentMeters(
            pointLat = latitude,
            pointLon = longitude,
            startLat = points[index].first,
            startLon = points[index].second,
            endLat = points[index + 1].first,
            endLon = points[index + 1].second
        )
        if (bestDistance == null || segmentDistance < bestDistance) {
            bestDistance = segmentDistance
        }
    }
    return bestDistance
}

private fun distanceToSegmentMeters(
    pointLat: Double,
    pointLon: Double,
    startLat: Double,
    startLon: Double,
    endLat: Double,
    endLon: Double
): Double {
    val meanLatitudeRad = Math.toRadians((pointLat + startLat + endLat) / 3.0)
    val metersPerDegreeLon = EARTH_METERS_PER_DEGREE * cos(meanLatitudeRad)

    val px = pointLon * metersPerDegreeLon
    val py = pointLat * EARTH_METERS_PER_DEGREE
    val ax = startLon * metersPerDegreeLon
    val ay = startLat * EARTH_METERS_PER_DEGREE
    val bx = endLon * metersPerDegreeLon
    val by = endLat * EARTH_METERS_PER_DEGREE

    val abx = bx - ax
    val aby = by - ay
    val abLenSq = abx * abx + aby * aby
    if (abLenSq <= 0.0) {
        val dx = px - ax
        val dy = py - ay
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    val apx = px - ax
    val apy = py - ay
    val t = ((apx * abx) + (apy * aby)) / abLenSq
    val clampedT = t.coerceIn(0.0, 1.0)
    val closestX = ax + abx * clampedT
    val closestY = ay + aby * clampedT
    val dx = px - closestX
    val dy = py - closestY
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
