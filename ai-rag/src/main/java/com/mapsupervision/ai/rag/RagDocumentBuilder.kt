package com.mapsupervision.ai.rag

import com.mapsupervision.ai.prompt.CanonicalTextNormalizer
import com.mapsupervision.ai.core.rag.RagDocument
import com.mapsupervision.ai.core.rag.RagDocumentType
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.WorkspaceSnapshot
import java.security.MessageDigest
import java.util.Locale

object RagDocumentBuilder {
    fun build(snapshot: WorkspaceSnapshot): List<RagDocument> {
        val now = System.currentTimeMillis()
        val docs = mutableListOf<RagDocument>()
        snapshot.designNodes.forEach { node ->
            docs += nodeDocument(snapshot.projectId, node, now)
        }
        snapshot.designRoutes.forEach { route ->
            docs += routeDocument(snapshot.projectId, route, snapshot.designNodes, now)
        }
        snapshot.workCategories.forEach { category ->
            docs += workCategoryDocument(snapshot.projectId, category, now)
        }
        snapshot.constructionProgress.forEach { progress ->
            docs += nodeProgressDocument(snapshot.projectId, progress, now)
        }
        snapshot.workVolumeRows.forEach { row ->
            docs += workVolumeDocument(snapshot.projectId, row, now)
        }
        snapshot.dailyLogs.forEach { log ->
            docs += dailyLogDocument(snapshot.projectId, log, now)
        }
        snapshot.workPlans.forEach { plan ->
            docs += workPlanDocument(snapshot.projectId, plan, now)
        }
        return docs
    }

    fun nodeDocument(projectId: String, node: GisNode, updatedAtEpochMs: Long): RagDocument {
        val text = buildString {
            append("node_code=").append(node.code)
            append(" label=").append(node.mapNumberLabel)
            append(" contractor=").append(node.contractor)
            append(" work_volume_summary=").append(node.workVolumeSummary)
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.NODE,
            sourceId = node.id,
            sourceCode = node.code,
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun routeDocument(projectId: String, route: GisRoute, nodes: List<GisNode>, updatedAtEpochMs: Long): RagDocument {
        val startLabel = nodes.firstOrNull { it.code == route.startNodeCode }?.mapNumberLabel.orEmpty()
        val endLabel = nodes.firstOrNull { it.code == route.endNodeCode }?.mapNumberLabel.orEmpty()
        val text = buildString {
            append("route_code=").append(route.code)
            append(" start_node=").append(route.startNodeCode)
            append(" end_node=").append(route.endNodeCode)
            if (startLabel.isNotBlank() || endLabel.isNotBlank()) {
                append(" endpoint_labels=").append(listOf(startLabel, endLabel).filter { it.isNotBlank() }.joinToString(" -> "))
            }
            append(" contractor=").append(route.contractor)
            route.designLength?.takeIf { it.isNotBlank() }?.let {
                append(" design_length=").append(it)
            }
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.ROUTE,
            sourceId = route.id,
            sourceCode = route.code,
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun workCategoryDocument(projectId: String, category: WorkCategory, updatedAtEpochMs: Long): RagDocument {
        val text = buildString {
            append("category_name=").append(category.name)
            append(" unit=").append(category.unit)
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.WORK_CATEGORY,
            sourceId = category.id,
            sourceCode = category.name,
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun nodeProgressDocument(projectId: String, progress: NodeProgress, updatedAtEpochMs: Long): RagDocument {
        val text = buildString {
            append("node_code=").append(progress.nodeCode)
            append(" planned=").append(formatNumber(progress.planned))
            append(" actual=").append(formatNumber(progress.actual))
            append(" remain=").append(formatNumber(progress.remain))
            append(" delayed=").append(progress.delayed)
            append(" updatedAtEpochMs=").append(progress.updatedAtEpochMs)
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.NODE_PROGRESS,
            sourceId = progress.id,
            sourceCode = progress.nodeCode,
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun workVolumeDocument(projectId: String, row: WorkVolumeProgress, updatedAtEpochMs: Long): RagDocument {
        val text = buildString {
            append("node_code=").append(row.nodeCode)
            append(" work_name=").append(row.workName)
            append(" planned=").append(formatNumber(row.plannedQty))
            append(" actual=").append(formatNumber(row.actualQty))
            append(" unit=").append(row.unit)
            append(" updatedAtEpochMs=").append(row.updatedAtEpochMs)
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.WORK_VOLUME_PROGRESS,
            sourceId = row.id,
            sourceCode = "${row.nodeCode}:${row.workName}",
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun dailyLogDocument(projectId: String, log: DailyLog, updatedAtEpochMs: Long): RagDocument {
        val text = buildString {
            append("node_code=").append(log.nodeCode.orEmpty())
            append(" route_code=").append(log.routeCode.orEmpty())
            append(" work_item=").append(log.workItem)
            append(" note=").append(log.note)
            append(" weather=").append(log.weather)
            append(" volume=").append(formatNumber(log.volume))
            append(" unit=").append(log.unit)
            append(" category_name=").append(log.categoryName)
            append(" createdAtEpochMs=").append(log.createdAtEpochMs)
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.DAILY_LOG,
            sourceId = log.id,
            sourceCode = log.nodeCode ?: log.routeCode ?: log.workItem,
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun workPlanDocument(projectId: String, plan: WorkPlan, updatedAtEpochMs: Long): RagDocument {
        val text = buildString {
            append("node_code=").append(plan.nodeCode.orEmpty())
            append(" route_code=").append(plan.routeCode.orEmpty())
            append(" title=").append(plan.title)
            append(" description=").append(plan.description)
            append(" plannedDateEpochDay=").append(plan.plannedDateEpochDay)
            append(" quantity=").append(formatNumber(plan.quantity))
            append(" unit=").append(plan.unit)
            append(" createdAtEpochMs=").append(plan.createdAtEpochMs)
        }.trim()
        return document(
            projectId = projectId,
            docType = RagDocumentType.WORK_PLAN,
            sourceId = plan.id,
            sourceCode = plan.nodeCode ?: plan.routeCode ?: plan.title,
            text = text,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    fun contentHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun stableId(projectId: String, docType: RagDocumentType, sourceId: String): String {
        return "${projectId}_${docType.name.lowercase(Locale.US)}_${sanitizeKey(sourceId)}"
    }

    private fun document(
        projectId: String,
        docType: RagDocumentType,
        sourceId: String,
        sourceCode: String,
        text: String,
        updatedAtEpochMs: Long
    ): RagDocument {
        val normalizedText = text.trim()
        return RagDocument(
            id = stableId(projectId, docType, sourceId),
            projectId = projectId,
            docType = docType,
            sourceId = sourceId,
            sourceCode = sourceCode,
            text = normalizedText,
            contentHash = contentHash(normalizedText),
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    private fun formatNumber(value: Float): String = String.format(Locale.US, "%.3f", value)
    private fun formatNumber(value: Double): String = String.format(Locale.US, "%.3f", value)
    private fun sanitizeKey(value: String): String = CanonicalTextNormalizer.normalizeKey(value).ifBlank { "unknown" }
}
