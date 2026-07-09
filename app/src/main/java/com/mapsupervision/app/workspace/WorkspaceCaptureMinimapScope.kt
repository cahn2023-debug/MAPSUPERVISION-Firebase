package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute

internal data class CaptureMinimapScope(
    val nodes: List<GisNode>,
    val routes: List<GisRoute>
)

internal fun buildCaptureMinimapScope(
    targetCode: String?,
    nodes: List<GisNode>,
    routes: List<GisRoute>
): CaptureMinimapScope {
    val cleanTarget = targetCode?.trim()
    if (cleanTarget.isNullOrBlank()) {
        return CaptureMinimapScope(emptyList(), emptyList())
    }

    val directNodes = nodes.filter { it.code.trim().equals(cleanTarget, ignoreCase = true) }
    val directRoutes = routes.filter { it.code.trim().equals(cleanTarget, ignoreCase = true) }

    if (directNodes.isEmpty() && directRoutes.isEmpty()) {
        return CaptureMinimapScope(emptyList(), emptyList())
    }

    val scopeRoutes = routes.filter { route ->
        route.code.trim().equals(cleanTarget, ignoreCase = true) ||
        route.startNodeCode.trim().equals(cleanTarget, ignoreCase = true) ||
        route.endNodeCode.trim().equals(cleanTarget, ignoreCase = true)
    }

    val routeNodeCodes = if (directRoutes.isNotEmpty()) {
        directRoutes.flatMap { listOf(it.startNodeCode.trim(), it.endNodeCode.trim()) }
    } else {
        emptyList()
    }

    val scopeNodes = nodes.filter { node ->
        val trimmedCode = node.code.trim()
        trimmedCode.equals(cleanTarget, ignoreCase = true) ||
        routeNodeCodes.any { trimmedCode.equals(it, ignoreCase = true) }
    }

    return CaptureMinimapScope(
        nodes = scopeNodes,
        routes = scopeRoutes
    )
}
