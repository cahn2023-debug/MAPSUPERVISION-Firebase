package com.mapsupervision.ai.prompt

import com.mapsupervision.ai.core.*

data class NormalizationRefs(
    val nodeCode: String? = null,
    val routeCode: String? = null,
    val categoryName: String? = null,
    val categoryUnit: String? = null,
    val categories: List<NormalizationCategoryRef> = emptyList(),
    val routeRefs: List<NormalizationRouteRef> = emptyList(),
    val nodeCodes: List<String> = emptyList(),
    val routeCodes: List<String> = emptyList(),
    val projectId: String? = null
)

data class NormalizationCategoryRef(
    val name: String,
    val unit: String
)

data class NormalizationRouteRef(
    val code: String,
    val startNodeCode: String,
    val endNodeCode: String,
    val contractor: String
)

object NormalizationRefsParser {
    fun parse(normalizationContext: String): NormalizationRefs {
        var nodeCode: String? = null
        var routeCode: String? = null
        var categoryName: String? = null
        var categoryUnit: String? = null
        var projectId: String? = null
        val categories = mutableListOf<NormalizationCategoryRef>()
        val routeRefs = mutableListOf<NormalizationRouteRef>()
        val nodeCodes = mutableListOf<String>()
        val routeCodes = mutableListOf<String>()

        normalizationContext.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("project=")) {
                projectId = trimmed.removePrefix("project=").trim().ifBlank { null }
            } else if (trimmed.startsWith("work_categories=")) {
                trimmed.removePrefix("work_categories=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { item ->
                        val pieces = item.split(':', limit = 2)
                        val name = pieces.getOrNull(0)?.trim().orEmpty()
                        val unit = pieces.getOrNull(1)?.trim().orEmpty()
                        if (name.isNotBlank()) {
                            categories += NormalizationCategoryRef(name, unit)
                        }
                    }
            } else if (trimmed.startsWith("node_codes=")) {
                trimmed.removePrefix("node_codes=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { nodeCodes += it }
            } else if (trimmed.startsWith("route_codes=")) {
                trimmed.removePrefix("route_codes=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { routeCodes += it }
            } else if (trimmed.startsWith("route_aliases=")) {
                trimmed.removePrefix("route_aliases=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { alias ->
                        val pieces = alias.split('>')
                        val code = pieces.getOrNull(0)?.trim().orEmpty()
                        if (code.isNotBlank()) {
                            routeRefs += NormalizationRouteRef(
                                code = code,
                                startNodeCode = pieces.getOrNull(1)?.trim().orEmpty(),
                                endNodeCode = pieces.getOrNull(2)?.trim().orEmpty(),
                                contractor = pieces.getOrNull(3)?.trim().orEmpty()
                            )
                        }
                    }
            }
            
            if (trimmed.startsWith("canonical_refs=")) {
                trimmed.removePrefix("canonical_refs=")
                    .split(';')
                    .forEach { part ->
                        when {
                            part.startsWith("node=") -> nodeCode = part.removePrefix("node=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("route=") -> routeCode = part.removePrefix("route=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("category=") -> {
                                val cat = part.removePrefix("category=").substringBefore('(').trim()
                                val pieces = cat.split(':', limit = 2)
                                categoryName = pieces.getOrNull(0)?.trim().orEmpty().ifBlank { null }
                                categoryUnit = pieces.getOrNull(1)?.trim().orEmpty().ifBlank { null }
                            }
                        }
                    }
            } else if (trimmed.startsWith("resolved_refs=")) {
                trimmed.removePrefix("resolved_refs=")
                    .split(';')
                    .forEach { part ->
                        when {
                            part.startsWith("node=") -> nodeCode = part.removePrefix("node=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("route=") -> routeCode = part.removePrefix("route=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("category=") -> {
                                val cat = part.removePrefix("category=").substringBefore('(').trim()
                                val pieces = cat.split(':', limit = 2)
                                categoryName = pieces.getOrNull(0)?.trim().orEmpty().ifBlank { null }
                                categoryUnit = pieces.getOrNull(1)?.trim().orEmpty().ifBlank { null }
                            }
                        }
                    }
            } else if (trimmed.startsWith("selected_node=")) {
                nodeCode = trimmed.removePrefix("selected_node=").trim().ifBlank { null }
            } else if (trimmed.startsWith("selected_route=")) {
                routeCode = trimmed.removePrefix("selected_route=").trim().ifBlank { null }
            }
        }

        return NormalizationRefs(
            nodeCode = nodeCode,
            routeCode = routeCode,
            categoryName = categoryName,
            categoryUnit = categoryUnit,
            categories = categories,
            routeRefs = routeRefs,
            nodeCodes = nodeCodes,
            routeCodes = routeCodes,
            projectId = projectId
        )
    }
}
