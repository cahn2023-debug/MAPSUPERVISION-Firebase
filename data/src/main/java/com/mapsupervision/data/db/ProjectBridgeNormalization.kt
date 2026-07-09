package com.mapsupervision.data.db

import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.DailyLogLineEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import java.util.Locale

internal data class ProjectBridgeLookup(
    val projectId: String,
    val sourceNodeIdToCode: Map<String, String>,
    val targetNodeCodeToId: Map<String, String>,
    val sourceRouteIdToCode: Map<String, String>,
    val targetRouteCodeToId: Map<String, String>,
    val targetImportedFileIds: Set<String>,
    val targetWorkCategoryIds: Set<String>,
    val targetMaterialDeclarationIds: Set<String>
)

internal suspend fun buildProjectBridgeLookup(
    sourceDatabase: MapSupervisionDatabase,
    targetDatabase: MapSupervisionDatabase,
    projectId: String
): ProjectBridgeLookup {
    val sourceNodes = sourceDatabase.gisNodeDao().byProject(projectId)
    val targetNodes = targetDatabase.gisNodeDao().byProject(projectId)
    val sourceRoutes = sourceDatabase.gisRouteDao().byProject(projectId)
    val targetRoutes = targetDatabase.gisRouteDao().byProject(projectId)
    return ProjectBridgeLookup(
        projectId = projectId,
        sourceNodeIdToCode = sourceNodes.associate { it.id to it.code },
        targetNodeCodeToId = targetNodes.associateNotNullByCode { it.code to it.id },
        sourceRouteIdToCode = sourceRoutes.associate { it.id to it.code },
        targetRouteCodeToId = targetRoutes.associateNotNullByCode { it.code to it.id },
        targetImportedFileIds = targetDatabase.importedFileDao().byProject(projectId).map { it.id }.toSet(),
        targetWorkCategoryIds = targetDatabase.workCategoryDao().byProject(projectId).map { it.id }.toSet(),
        targetMaterialDeclarationIds = targetDatabase.materialDeclarationDao().getByProject(projectId).map { it.id }.toSet()
    )
}

internal fun GisRouteEntity.normalizeForBridge(lookup: ProjectBridgeLookup): GisRouteEntity = copy(
    importedFileId = lookup.resolveImportedFileId(importedFileId, "gis_route", id, code),
    startNodeId = lookup.resolveNodeId(
        sourceNodeId = startNodeId,
        fallbackCode = startNodeCode,
        table = "gis_route",
        recordId = id,
        field = "startNodeId"
    ),
    endNodeId = lookup.resolveNodeId(
        sourceNodeId = endNodeId,
        fallbackCode = endNodeCode,
        table = "gis_route",
        recordId = id,
        field = "endNodeId"
    )
)

internal fun NodeProgressEntity.normalizeForBridge(lookup: ProjectBridgeLookup): NodeProgressEntity = copy(
    nodeId = lookup.resolveNodeId(
        sourceNodeId = nodeId,
        fallbackCode = null,
        table = "node_progress",
        recordId = id,
        field = "nodeId"
    )
)

internal fun DailyLogEntity.normalizeForBridge(lookup: ProjectBridgeLookup): DailyLogEntity = copy(
    nodeId = lookup.resolveNodeId(
        sourceNodeId = nodeId,
        fallbackCode = null,
        table = "daily_log",
        recordId = id,
        field = "nodeId"
    ),
    routeId = lookup.resolveRouteId(
        sourceRouteId = routeId,
        fallbackCode = null,
        table = "daily_log",
        recordId = id,
        field = "routeId"
    ),
    plannedNodeId = lookup.resolveNodeId(
        sourceNodeId = plannedNodeId,
        fallbackCode = null,
        table = "daily_log",
        recordId = id,
        field = "plannedNodeId"
    ),
    plannedRouteId = lookup.resolveRouteId(
        sourceRouteId = plannedRouteId,
        fallbackCode = null,
        table = "daily_log",
        recordId = id,
        field = "plannedRouteId"
    )
)

internal fun DailyLogLineEntity.normalizeForBridge(lookup: ProjectBridgeLookup): DailyLogLineEntity = copy(
    nodeId = lookup.resolveNodeId(
        sourceNodeId = nodeId,
        fallbackCode = null,
        table = "daily_log_line",
        recordId = id,
        field = "nodeId"
    ),
    routeId = lookup.resolveRouteId(
        sourceRouteId = routeId,
        fallbackCode = null,
        table = "daily_log_line",
        recordId = id,
        field = "routeId"
    )
)

internal fun SitePhotoEntity.normalizeForBridge(lookup: ProjectBridgeLookup): SitePhotoEntity = copy(
    matchedNodeId = lookup.resolveNodeId(
        sourceNodeId = matchedNodeId,
        fallbackCode = null,
        table = "site_photos",
        recordId = id,
        field = "matchedNodeId"
    ),
    matchedRouteId = lookup.resolveRouteId(
        sourceRouteId = matchedRouteId,
        fallbackCode = objectCode,
        table = "site_photos",
        recordId = id,
        field = "matchedRouteId"
    )
)

internal fun NoteEntity.normalizeForBridge(lookup: ProjectBridgeLookup): NoteEntity = copy(
    objectNodeId = lookup.resolveNodeId(
        sourceNodeId = objectNodeId,
        fallbackCode = null,
        table = "note",
        recordId = id,
        field = "objectNodeId"
    ),
    objectRouteId = lookup.resolveRouteId(
        sourceRouteId = objectRouteId,
        fallbackCode = null,
        table = "note",
        recordId = id,
        field = "objectRouteId"
    )
)

internal fun TaskEntity.normalizeForBridge(lookup: ProjectBridgeLookup): TaskEntity = copy(
    objectNodeId = lookup.resolveNodeId(
        sourceNodeId = objectNodeId,
        fallbackCode = null,
        table = "task",
        recordId = id,
        field = "objectNodeId"
    ),
    objectRouteId = lookup.resolveRouteId(
        sourceRouteId = objectRouteId,
        fallbackCode = null,
        table = "task",
        recordId = id,
        field = "objectRouteId"
    )
)

internal fun WorkPlanEntity.normalizeForBridge(lookup: ProjectBridgeLookup): WorkPlanEntity = copy(
    nodeId = lookup.resolveNodeId(
        sourceNodeId = nodeId,
        fallbackCode = nodeCode,
        table = "work_plan",
        recordId = id,
        field = "nodeId"
    ),
    routeId = lookup.resolveRouteId(
        sourceRouteId = routeId,
        fallbackCode = routeCode,
        table = "work_plan",
        recordId = id,
        field = "routeId"
    )
)

internal fun MaterialDeclarationEntity.normalizeForBridge(lookup: ProjectBridgeLookup): MaterialDeclarationEntity = copy(
    workCategoryId = lookup.keepIfPresent(
        value = workCategoryId,
        validIds = lookup.targetWorkCategoryIds,
        table = "material_declaration",
        recordId = id,
        field = "workCategoryId",
        lookupHint = workName
    )
)

internal fun MaterialHandoverEntity.normalizeForBridge(lookup: ProjectBridgeLookup): MaterialHandoverEntity = copy(
    nodeId = lookup.resolveNodeId(
        sourceNodeId = nodeId,
        fallbackCode = nodeCode,
        table = "material_handover",
        recordId = id,
        field = "nodeId"
    ),
    materialDeclarationId = lookup.keepIfPresent(
        value = materialDeclarationId,
        validIds = lookup.targetMaterialDeclarationIds,
        table = "material_handover",
        recordId = id,
        field = "materialDeclarationId",
        lookupHint = "$workName|$materialName"
    ),
    workCategoryId = lookup.keepIfPresent(
        value = workCategoryId,
        validIds = lookup.targetWorkCategoryIds,
        table = "material_handover",
        recordId = id,
        field = "workCategoryId",
        lookupHint = workName
    )
)

private fun ProjectBridgeLookup.resolveNodeId(
    sourceNodeId: String?,
    fallbackCode: String?,
    table: String,
    recordId: String,
    field: String
): String? {
    val resolvedCode = fallbackCode.normalizedBridgeCode()
        ?: sourceNodeId?.let { sourceNodeIdToCode[it].normalizedBridgeCode() }
    if (resolvedCode == null) {
        if (sourceNodeId != null) {
            logNullified(table, recordId, field, sourceNodeId)
        }
        return null
    }
    val targetId = targetNodeCodeToId[resolvedCode]
    if (targetId == null) {
        logNullified(table, recordId, field, resolvedCode)
    }
    return targetId
}

private fun ProjectBridgeLookup.resolveRouteId(
    sourceRouteId: String?,
    fallbackCode: String?,
    table: String,
    recordId: String,
    field: String
): String? {
    val resolvedCode = fallbackCode.normalizedBridgeCode()
        ?: sourceRouteId?.let { sourceRouteIdToCode[it].normalizedBridgeCode() }
    if (resolvedCode == null) {
        if (sourceRouteId != null) {
            logNullified(table, recordId, field, sourceRouteId)
        }
        return null
    }
    val targetId = targetRouteCodeToId[resolvedCode]
    if (targetId == null) {
        logNullified(table, recordId, field, resolvedCode)
    }
    return targetId
}

private fun ProjectBridgeLookup.resolveImportedFileId(
    importedFileId: String?,
    table: String,
    recordId: String,
    lookupHint: String
): String? {
    return keepIfPresent(
        value = importedFileId,
        validIds = targetImportedFileIds,
        table = table,
        recordId = recordId,
        field = "importedFileId",
        lookupHint = lookupHint
    )
}

private fun ProjectBridgeLookup.keepIfPresent(
    value: String?,
    validIds: Set<String>,
    table: String,
    recordId: String,
    field: String,
    lookupHint: String
): String? {
    if (value == null) return null
    if (validIds.contains(value)) return value
    logNullified(table, recordId, field, lookupHint)
    return null
}

private fun ProjectBridgeLookup.logNullified(
    table: String,
    recordId: String,
    field: String,
    lookupHint: String
) {
    AppLogger.d(
        "project.db.bridge_fk_null projectId=$projectId table=$table recordId=$recordId field=$field lookup=$lookupHint"
    )
}

private fun String?.normalizedBridgeCode(): String? {
    return this?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.uppercase(Locale.US)
}

private inline fun <T> Iterable<T>.associateNotNullByCode(transform: (T) -> Pair<String, String>): Map<String, String> {
    return buildMap {
        for (item in this@associateNotNullByCode) {
            val (code, id) = transform(item)
            val normalized = code.normalizedBridgeCode() ?: continue
            put(normalized, id)
        }
    }
}
