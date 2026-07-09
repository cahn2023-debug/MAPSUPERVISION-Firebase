package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute

internal data class GeometrySafetyIssues(
    val missingBaselineNodeIds: List<String>,
    val missingBaselineRouteIds: List<String>,
    val driftedBaselineNodeIds: List<String>,
    val driftedBaselineRouteIds: List<String>
) {
    val hasDropByIdentity: Boolean
        get() = missingBaselineNodeIds.isNotEmpty() || missingBaselineRouteIds.isNotEmpty()

    val hasBaselineDrift: Boolean
        get() = driftedBaselineNodeIds.isNotEmpty() || driftedBaselineRouteIds.isNotEmpty()
}

internal fun identitySignature(ids: Sequence<String>): Int {
    var acc = 1
    ids.sorted().forEach { id -> acc = 31 * acc + id.hashCode() }
    return acc
}

internal fun detectGeometrySafetyIssues(
    baselineNodes: List<GisNode>,
    baselineRoutes: List<GisRoute>,
    refreshedNodes: List<GisNode>,
    refreshedRoutes: List<GisRoute>,
    normalizeCode: (String) -> String,
    coordBucketKey: (Double, Double) -> Long
): GeometrySafetyIssues {
    val refreshedNodeIds = refreshedNodes.mapTo(HashSet(refreshedNodes.size * 2 + 1)) { it.id }
    val refreshedRouteIds = refreshedRoutes.mapTo(HashSet(refreshedRoutes.size * 2 + 1)) { it.id }
    val missingBaselineNodeIds = baselineNodes.asSequence().map { it.id }.filterNot { refreshedNodeIds.contains(it) }.toList()
    val missingBaselineRouteIds = baselineRoutes.asSequence().map { it.id }.filterNot { refreshedRouteIds.contains(it) }.toList()

    val refreshedNodeById = HashMap<String, GisNode>(refreshedNodes.size * 2 + 1)
    refreshedNodes.forEach { refreshedNodeById[it.id] = it }
    val refreshedRouteById = HashMap<String, GisRoute>(refreshedRoutes.size * 2 + 1)
    refreshedRoutes.forEach { refreshedRouteById[it.id] = it }

    val driftedBaselineNodeIds = baselineNodes.asSequence().mapNotNull { base ->
        val current = refreshedNodeById[base.id] ?: return@mapNotNull null
        val sameCode = normalizeCode(base.code) == normalizeCode(current.code)
        val sameCoord = coordBucketKey(base.latitude, base.longitude) == coordBucketKey(current.latitude, current.longitude)
        if (sameCode && sameCoord) null else base.id
    }.toList()
    val driftedBaselineRouteIds = baselineRoutes.asSequence().mapNotNull { base ->
        val current = refreshedRouteById[base.id] ?: return@mapNotNull null
        val sameStart = normalizeCode(base.startNodeCode) == normalizeCode(current.startNodeCode)
        val sameEnd = normalizeCode(base.endNodeCode) == normalizeCode(current.endNodeCode)
        if (sameStart && sameEnd) null else base.id
    }.toList()

    return GeometrySafetyIssues(
        missingBaselineNodeIds = missingBaselineNodeIds,
        missingBaselineRouteIds = missingBaselineRouteIds,
        driftedBaselineNodeIds = driftedBaselineNodeIds,
        driftedBaselineRouteIds = driftedBaselineRouteIds
    )
}

internal fun mergeGeometryPreferBaseline(
    baselineNodes: List<GisNode>,
    baselineRoutes: List<GisRoute>,
    refreshedNodes: List<GisNode>,
    refreshedRoutes: List<GisRoute>
): Pair<List<GisNode>, List<GisRoute>> {
    val mergedNodesById = LinkedHashMap<String, GisNode>(baselineNodes.size + refreshedNodes.size + 1)
    refreshedNodes.forEach { mergedNodesById[it.id] = it }
    baselineNodes.forEach { mergedNodesById[it.id] = it }

    val mergedRoutesById = LinkedHashMap<String, GisRoute>(baselineRoutes.size + refreshedRoutes.size + 1)
    refreshedRoutes.forEach { mergedRoutesById[it.id] = it }
    baselineRoutes.forEach { mergedRoutesById[it.id] = it }
    return mergedNodesById.values.toList() to mergedRoutesById.values.toList()
}
