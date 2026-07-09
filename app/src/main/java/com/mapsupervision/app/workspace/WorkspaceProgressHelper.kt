package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.MaterialHandover

object WorkspaceProgressHelper {
    fun buildDashboard(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        progress: List<NodeProgress>,
        workVolumeRows: List<WorkVolumeProgress>
    ): DashboardState {
        val routeNodeCodesUpper = HashSet<String>(routes.size * 2)
        for (r in routes) {
            routeNodeCodesUpper.add(r.startNodeCode.trim().uppercase())
            routeNodeCodesUpper.add(r.endNodeCode.trim().uppercase())
        }
        val activeNodes = nodes.filter { !WorkspaceImportHelper.isStructuralRouteNode(it.code, routeNodeCodesUpper) }
        val totalDesignNodes = activeNodes.size
        val activeNodeCodes = activeNodes.map { it.code.trim().uppercase() }.toSet()

        val totalDesignRoutes = routes.size
        val updatedNodeCodes = HashSet<String>(progress.size)
        var delayed = 0
        var totalActual = 0.0
        var activeProgressCount = 0
        for (item in progress) {
            val codeUpper = item.nodeCode.trim().uppercase()
            if (codeUpper in activeNodeCodes) {
                activeProgressCount++
                updatedNodeCodes += item.nodeCode
                totalActual += item.actual.toDouble()
                if (item.delayed) delayed++
            }
        }
        val updatedNodes = updatedNodeCodes.size
        val completion = if (activeProgressCount == 0) 0f else (totalActual / activeProgressCount).toFloat()

        // Material quantity aggregation
        var totalPlannedQty = 0f
        var totalActualQty = 0f
        val nodesWithMaterial = HashSet<String>()
        for (row in workVolumeRows) {
            val codeUpper = row.nodeCode.trim().uppercase()
            if (codeUpper in activeNodeCodes) {
                totalPlannedQty += row.plannedQty
                totalActualQty += row.actualQty
                if (row.actualQty > 0f) nodesWithMaterial += row.nodeCode
            }
        }
        val materialCompletionPercent = if (totalPlannedQty > 0f) (totalActualQty / totalPlannedQty * 100f) else 0f

        return DashboardState(
            totalDesignNodes = totalDesignNodes,
            totalDesignRoutes = totalDesignRoutes,
            updatedConstructionNodes = updatedNodes,
            completionPercent = completion,
            delayedCount = delayed,
            totalPlannedQty = totalPlannedQty,
            totalActualQty = totalActualQty,
            materialCompletionPercent = materialCompletionPercent,
            nodesWithMaterialEntry = nodesWithMaterial.size
        )
    }
}

data class MaterialAggregateResult(
    val workName: String,
    val planned: Float,
    val delivered: Float,
    val remaining: Float,
    val unit: String,
    val rows: List<WorkVolumeProgress>
)

object MaterialAggregationHelper {
    fun aggregate(
        workVolumeRows: List<WorkVolumeProgress>,
        handovers: List<MaterialHandover>
    ): List<MaterialAggregateResult> {
        val handoversByWorkAndNode = handovers.groupBy { "${it.workName.lowercase()}_${it.nodeCode.lowercase()}" }
        return workVolumeRows
            .groupBy { it.workName }
            .map { (workName, rows) ->
                val planned = rows.sumOf { it.plannedQty.toDouble() }.toFloat()
                
                val delivered = rows.sumOf { row ->
                    val key = "${row.workName.lowercase()}_${row.nodeCode.lowercase()}"
                    val nodeHandovers = handoversByWorkAndNode[key].orEmpty()
                    nodeHandovers.sumOf { it.quantity.toDouble() }
                }.toFloat()

                val remaining = rows.sumOf { row ->
                    val key = "${row.workName.lowercase()}_${row.nodeCode.lowercase()}"
                    val nodeHandovers = handoversByWorkAndNode[key].orEmpty()
                    val nodeDelivered = nodeHandovers.sumOf { it.quantity.toDouble() }.toFloat()
                    maxOf(0f, row.plannedQty - nodeDelivered).toDouble()
                }.toFloat()

                val unit = rows.firstOrNull { it.unit.isNotBlank() }?.unit ?: "Cái"

                MaterialAggregateResult(
                    workName = workName,
                    planned = planned,
                    delivered = delivered,
                    remaining = remaining,
                    unit = unit,
                    rows = rows
                )
            }
            .sortedBy { it.workName }
    }
}


