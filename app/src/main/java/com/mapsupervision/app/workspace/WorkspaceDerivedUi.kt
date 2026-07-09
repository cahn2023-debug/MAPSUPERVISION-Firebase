package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.resolveEpochDay
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress

import com.mapsupervision.domain.model.WorkTemplateOption

data class SelectorOption(
    val key: String,
    val label: String
)

data class PreparedMaterialLine(
    val itemName: String,
    val plannedText: String,
    val plannedQty: Float,
    val actualText: String
)

data class DataHubDisplayItem(
    val isRoute: Boolean,
    val id: String,
    val code: String,
    val contractor: String,
    val node: GisNode? = null,
    val route: GisRoute? = null,
    val materialLines: List<PreparedMaterialLine> = emptyList(),
    val routeDistanceText: String? = null
)

data class ProgressUiState(
    val nonStructuralNodes: List<GisNode> = emptyList(),
    val nodesByCode: Map<String, GisNode> = emptyMap(),
    val routeSelectorOptions: List<SelectorOption> = emptyList(),
    val progressByNodeCode: Map<String, NodeProgress> = emptyMap(),
    val allDisplayItems: List<NodeProgress> = emptyList(),
    val activeNodeCodes: Set<String> = emptySet(),
    val criticalNodes: List<NodeProgress> = emptyList(),
    val nodeSelectorOptions: List<SelectorOption> = emptyList(),
    val logEpochDays: Set<Long> = emptySet(),
    val materialOptionsByNodeCode: Map<String, List<SelectorOption>> = emptyMap(),
    val templateOptions: List<WorkTemplateOption> = emptyList(),
    val workVolumeRows: List<WorkVolumeProgress> = emptyList()
)

data class DataHubUiState(
    val nonStructuralNodes: List<GisNode> = emptyList(),
    val contractorOptions: List<String> = emptyList(),
    val baseDisplayItems: List<DataHubDisplayItem> = emptyList()
)

internal data class WorkspaceIndexes(
    val nodesById: Map<String, GisNode> = emptyMap(),
    val nodesByCode: Map<String, GisNode> = emptyMap(),
    val routesByCode: Map<String, GisRoute> = emptyMap(),
    val routeRepresentativesByCode: Map<String, GisRoute> = emptyMap(),
    val progressByNodeCode: Map<String, NodeProgress> = emptyMap(),
    val workVolumeRowsByNodeKey: Map<String, List<WorkVolumeProgress>> = emptyMap(),
    val parsedMaterialsByNodeKey: Map<String, List<PreparedMaterialLine>> = emptyMap(),
    val normalizedNodeSearch: Map<String, NodeSearchIndex> = emptyMap(),
    val normalizedRouteSearch: Map<String, RouteSearchIndex> = emptyMap(),
    val materialTypeOptions: List<String> = emptyList(),
    val progressUi: ProgressUiState = ProgressUiState(),
    val dataHubUi: DataHubUiState = DataHubUiState()
)

internal data class NodeSearchIndex(
    val codeNormalized: String,
    val mapNumberLabelNormalized: String,
    val contractor: String
) {
    private val contractorNormalized = normalizeForSearch(contractor)
    private val codeCompact = codeNormalized.replace(" ", "")
    private val mapNumberLabelCompact = mapNumberLabelNormalized.replace(" ", "")
    private val contractorCompact = contractorNormalized.replace(" ", "")

    fun matches(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        val compactQuery = normalizedQuery.replace(" ", "")
        return codeNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && codeCompact.contains(compactQuery)) ||
            mapNumberLabelNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && mapNumberLabelCompact.contains(compactQuery)) ||
            contractorNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && contractorCompact.contains(compactQuery))
    }
}

internal data class RouteSearchIndex(
    val codeNormalized: String,
    val startNodeCodeNormalized: String,
    val endNodeCodeNormalized: String,
    val contractor: String
) {
    private val contractorNormalized = normalizeForSearch(contractor)
    private val codeCompact = codeNormalized.replace(" ", "")
    private val startNodeCodeCompact = startNodeCodeNormalized.replace(" ", "")
    private val endNodeCodeCompact = endNodeCodeNormalized.replace(" ", "")
    private val contractorCompact = contractorNormalized.replace(" ", "")

    fun matches(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        val compactQuery = normalizedQuery.replace(" ", "")
        return codeNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && codeCompact.contains(compactQuery)) ||
            startNodeCodeNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && startNodeCodeCompact.contains(compactQuery)) ||
            endNodeCodeNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && endNodeCodeCompact.contains(compactQuery)) ||
            contractorNormalized.contains(normalizedQuery) ||
            (compactQuery.isNotEmpty() && contractorCompact.contains(compactQuery))
    }
}

internal fun buildWorkspaceIndexes(state: WorkspaceState): WorkspaceIndexes {
    val routeNodeCodesUpper = HashSet<String>(state.designRoutes.size * 2 + 1)
    for (route in state.designRoutes) {
        routeNodeCodesUpper += route.startNodeCode.trim().uppercase()
        routeNodeCodesUpper += route.endNodeCode.trim().uppercase()
    }

    val nonStructuralNodes = state.designNodes.filter { node ->
        !WorkspaceImportHelper.isStructuralRouteNode(node.code, routeNodeCodesUpper)
    }
    val nodesById = state.designNodes.associateBy { it.id }
    val nodesByCode = state.designNodes.associateBy { it.code }
    val routesByCode = state.designRoutes.associateBy { it.code }
    val progressByNodeCode = state.constructionProgress.associateBy { it.nodeCode }
    val activeNodeCodes = nonStructuralNodes.mapTo(linkedSetOf()) { normalizeObjectCode(it.code) }

    val parsedMaterialsByNodeKey = LinkedHashMap<String, List<PreparedMaterialLine>>(state.designNodes.size * 2 + 1)
    for (node in state.designNodes) {
        val parsed = parseworkVolumeSummary(node.workVolumeSummary)
        parsedMaterialsByNodeKey[node.id] = parsed
        parsedMaterialsByNodeKey[node.code] = parsed
    }

    val workVolumeRowsByNodeKey = LinkedHashMap<String, MutableList<WorkVolumeProgress>>(state.workVolumeRows.size * 2 + 1)
    for (row in state.workVolumeRows) {
        workVolumeRowsByNodeKey.getOrPut(row.nodeCode) { mutableListOf() } += row
        val aliasNode = nodesById[row.nodeCode] ?: nodesByCode[row.nodeCode]
        if (aliasNode != null) {
            workVolumeRowsByNodeKey.getOrPut(aliasNode.id) { mutableListOf() } += row
            workVolumeRowsByNodeKey.getOrPut(aliasNode.code) { mutableListOf() } += row
        }
    }

    val nodeSelectorOptions = state.designNodes
        .sortedBy { it.code }
        .map { node ->
            SelectorOption(
                key = node.code,
                label = "${nodeDisplayName(node.code, nodesByCode)} (${node.code})"
            )
        }

    val materialOptionsByNodeCode = LinkedHashMap<String, List<SelectorOption>>(nonStructuralNodes.size * 2 + 1)
    for (node in nonStructuralNodes) {
        val options = parsedMaterialsByNodeKey[node.id].orEmpty()
            .map { SelectorOption(it.itemName, it.itemName) }
        materialOptionsByNodeCode[node.code] = options
    }

    val routeSelectorOptions = state.designRoutes
        .sortedWith(compareBy<GisRoute>({ it.code }, { it.startNodeCode }, { it.endNodeCode }))
        .map { route ->
            SelectorOption(
                key = route.code,
                label = buildString {
                    append(route.code)
                    if (route.startNodeCode.isNotBlank() || route.endNodeCode.isNotBlank()) {
                        append(" (")
                        append(route.startNodeCode)
                        if (route.startNodeCode.isNotBlank() && route.endNodeCode.isNotBlank()) append(" -> ")
                        append(route.endNodeCode)
                        append(")")
                    }
                }
            )
        }

    val allDisplayItems = nonStructuralNodes.map { node ->
        progressByNodeCode[node.code] ?: NodeProgress(
            id = "",
            projectId = state.activeProjectId.orEmpty(),
            nodeCode = node.code,
            planned = 0f,
            actual = 0f,
            remain = 0f,
            delayed = false,
            updatedAtEpochMs = 0L
        )
    }

    val criticalNodes = state.constructionProgress
        .asSequence()
        .filter { progress -> progress.planned - progress.actual > 0f }
        .filter { progress -> normalizeObjectCode(progress.nodeCode) in activeNodeCodes }
        .sortedByDescending { progress -> progress.planned - progress.actual }
        .take(3)
        .toList()

    val contractorOptions = buildList {
        add("Tất cả")
        addAll(
            ((nonStructuralNodes.map { it.contractor } + state.designRoutes.map { it.contractor }))
                .filter { it.isNotBlank() }
                .distinct()
        )
    }

    val routeRepresentatives = LinkedHashMap<String, GisRoute>(state.designRoutes.size)
    for (route in state.designRoutes) {
        val prefix = routeDisplayKey(route.code)
        if (prefix !in routeRepresentatives) {
            routeRepresentatives[prefix] = route
        }
    }

    val baseDisplayItems = buildList {
        nonStructuralNodes.forEach { node ->
            add(
                DataHubDisplayItem(
                    isRoute = false,
                    id = node.id,
                    code = node.code,
                    contractor = node.contractor,
                    node = node,
                    materialLines = parsedMaterialsByNodeKey[node.id].orEmpty()
                )
            )
        }
        routeRepresentatives.forEach { (prefix, representative) ->
            val segments = state.designRoutes
                .asSequence()
                .filter { routeDisplayKey(it.code) == prefix }
                .sortedBy { route -> route.code.substringAfterLast("_s").toIntOrNull() ?: 0 }
                .toList()
            val resolvedSegments = if (segments.isEmpty()) listOf(representative) else segments
            val routeDistanceText = buildRouteDistanceText(resolvedSegments, nodesByCode)

            if (prefix.contains("#pm")) {
                val first = resolvedSegments.firstOrNull() ?: representative
                val last = resolvedSegments.lastOrNull() ?: representative
                add(
                    DataHubDisplayItem(
                        isRoute = true,
                        id = prefix,
                        code = prefix,
                        contractor = representative.contractor,
                        route = representative.copy(
                            id = prefix,
                            code = prefix,
                            startNodeCode = first.startNodeCode,
                            endNodeCode = last.endNodeCode
                        ),
                        routeDistanceText = routeDistanceText
                    )
                )
            } else {
                add(
                    DataHubDisplayItem(
                        isRoute = true,
                        id = representative.id,
                        code = representative.code,
                        contractor = representative.contractor,
                        route = representative,
                        routeDistanceText = routeDistanceText
                    )
                )
            }
        }
    }

    val logEpochDays = state.dailyLogs.mapTo(linkedSetOf()) { log -> log.resolveEpochDay() }

    val materialTypeOptions = (state.designNodes.flatMap { node ->
        parsedMaterialsByNodeKey[node.id].orEmpty().map { it.itemName }
    } + state.workVolumeRows.map { it.workName })
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .groupBy { it.lowercase() }
        .map { (_, group) -> group.first() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toList()

    val dbMaterials = LinkedHashMap<String, WorkTemplateOption>()
    for (row in state.workVolumeRows) {
        val trimmedName = row.workName.trim()
        if (trimmedName.isEmpty()) continue
        val key = trimmedName.lowercase()
        val existing = dbMaterials[key]
        if (existing == null || (existing.unit.isBlank() && row.unit.isNotBlank())) {
            dbMaterials[key] = WorkTemplateOption(
                name = trimmedName,
                unit = row.unit.trim(),
                source = com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_WORK_VOLUME
            )
        }
    }

    for (node in state.designNodes) {
        val parsedList = parsedMaterialsByNodeKey[node.id].orEmpty()
        for (parsed in parsedList) {
            val trimmedName = parsed.itemName.trim()
            if (trimmedName.isEmpty()) continue
            val key = trimmedName.lowercase()
            if (!dbMaterials.containsKey(key)) {
                dbMaterials[key] = WorkTemplateOption(
                    name = trimmedName,
                    unit = "",
                    source = com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_WORK_VOLUME
                )
            }
        }
    }

    val manualCategories = LinkedHashMap<String, WorkTemplateOption>()
    for (cat in state.workCategories) {
        val trimmedName = cat.name.trim()
        if (trimmedName.isEmpty()) continue
        val key = trimmedName.lowercase()
        if (!manualCategories.containsKey(key)) {
            manualCategories[key] = WorkTemplateOption(
                name = trimmedName,
                unit = cat.unit.trim(),
                source = com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_GENERAL
            )
        }
    }

    val sortedMaterials = dbMaterials.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val sortedManual = manualCategories.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val templateOptions = sortedMaterials + sortedManual
    val preferredTemplateOptions = templateOptions
        .groupBy { it.name.trim().lowercase() }
        .mapNotNull { (_, options) ->
            options.firstOrNull { it.source == com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_GENERAL && it.unit.isNotBlank() }
                ?: options.firstOrNull { it.unit.isNotBlank() }
                ?: options.firstOrNull()
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    return WorkspaceIndexes(
        nodesById = nodesById,
        nodesByCode = nodesByCode,
        routesByCode = routesByCode,
        routeRepresentativesByCode = routeRepresentatives,
        progressByNodeCode = progressByNodeCode,
        workVolumeRowsByNodeKey = workVolumeRowsByNodeKey,
        parsedMaterialsByNodeKey = parsedMaterialsByNodeKey,
        normalizedNodeSearch = state.designNodes.associate { node -> node.id to buildNodeSearchIndex(node) },
        normalizedRouteSearch = state.designRoutes.associate { route -> route.code to buildRouteSearchIndex(route) },
        materialTypeOptions = materialTypeOptions,
        progressUi = ProgressUiState(
            nonStructuralNodes = nonStructuralNodes,
            nodesByCode = nodesByCode,
            routeSelectorOptions = routeSelectorOptions,
            progressByNodeCode = progressByNodeCode,
            allDisplayItems = allDisplayItems,
            activeNodeCodes = activeNodeCodes,
            criticalNodes = criticalNodes,
            nodeSelectorOptions = nodeSelectorOptions,
            logEpochDays = logEpochDays,
            materialOptionsByNodeCode = materialOptionsByNodeCode,
            templateOptions = preferredTemplateOptions,
            workVolumeRows = state.workVolumeRows
        ),
        dataHubUi = DataHubUiState(
            nonStructuralNodes = nonStructuralNodes,
            contractorOptions = contractorOptions,
            baseDisplayItems = baseDisplayItems
        )
    )
}

internal fun resolveMaterialActualText(
    workVolumeProgress: Map<String, String>,
    node: GisNode,
    itemName: String
): String {
    return workVolumeProgress["${node.id}_$itemName"]
        ?: workVolumeProgress["${node.code}_$itemName"]
        ?: ""
}

private fun parseworkVolumeSummary(summary: String): List<PreparedMaterialLine> {
    if (summary.isBlank()) return emptyList()
    return summary
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            val itemName = parts.getOrNull(0)?.trim().orEmpty()
            if (itemName.isBlank()) {
                null
            } else {
                val plannedText = parts.getOrNull(1)?.trim().orEmpty()
                PreparedMaterialLine(
                    itemName = itemName,
                    plannedText = plannedText,
                    plannedQty = plannedText.toFloatOrNull() ?: 0f,
                    actualText = ""
                )
            }
        }
        .toList()
}

private fun buildNodeSearchIndex(node: GisNode): NodeSearchIndex =
    NodeSearchIndex(
        codeNormalized = normalizeForSearch(node.code),
        mapNumberLabelNormalized = normalizeForSearch(node.mapNumberLabel),
        contractor = node.contractor
    )

private fun buildRouteSearchIndex(route: GisRoute): RouteSearchIndex =
    RouteSearchIndex(
        codeNormalized = normalizeForSearch(route.code),
        startNodeCodeNormalized = normalizeForSearch(route.startNodeCode),
        endNodeCodeNormalized = normalizeForSearch(route.endNodeCode),
        contractor = route.contractor
    )

private fun resolveLogEpochDay(log: DailyLog): Long = log.resolveEpochDay()

private fun normalizeObjectCode(value: String): String = value.trim().uppercase()

private fun routeDisplayKey(code: String): String {
    val markerIndex = if (code.contains("#pm")) code.lastIndexOf("_s") else code.lastIndexOf("_R")
    return if (markerIndex >= 0) code.substring(0, markerIndex) else code
}

private fun buildRouteDistanceText(
    segments: List<GisRoute>,
    nodesByCode: Map<String, GisNode>
): String? {
    var totalDistM = 0.0
    segments.forEach { seg ->
        val start = nodesByCode[seg.startNodeCode]
        val end = nodesByCode[seg.endNodeCode]
        if (start != null && end != null) {
            totalDistM += com.mapsupervision.domain.util.Haversine.distanceInMeters(
                start.latitude, start.longitude,
                end.latitude, end.longitude
            )
        }
    }
    if (totalDistM <= 0.0) return null
    return if (totalDistM >= 1000) "${"%.2f".format(totalDistM / 1000)} km" else "${totalDistM.toInt()} m"
}

private fun normalizeForSearch(text: String): String {
    val stripped = java.text.Normalizer
        .normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped
        .replace('đ', 'd')
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

