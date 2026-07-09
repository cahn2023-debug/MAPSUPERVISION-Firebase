package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.WorkCategory
import java.text.Normalizer

internal fun resolveWorkTemplateUnit(
    name: String,
    workCategories: List<WorkCategory>,
    workVolumeRows: List<WorkVolumeProgress>
): String {
    if (name.isBlank()) return ""

    val exactUnit = firstNonBlankUnit(
        name,
        workCategories.map { it.name to it.unit } + workVolumeRows.map { it.workName to it.unit }
    )
    if (exactUnit.isNotBlank()) return exactUnit

    val aliases = templateNameAliases(name)
    for (alias in aliases) {
        val aliasUnit = firstNonBlankUnit(
            alias,
            workCategories.map { it.name to it.unit } + workVolumeRows.map { it.workName to it.unit }
        )
        if (aliasUnit.isNotBlank()) return aliasUnit
    }

    val normalizedName = normalizeTemplateName(name)
    val fuzzyMatch = (workCategories.asSequence().map { it.name to it.unit } +
        workVolumeRows.asSequence().map { it.workName to it.unit })
        .firstOrNull { (candidateName, candidateUnit) ->
            candidateUnit.isNotBlank() && normalizeTemplateName(candidateName).let { candidateNormalized ->
                candidateNormalized == normalizedName ||
                    candidateNormalized.contains(normalizedName) ||
                    normalizedName.contains(candidateNormalized)
            }
        }
    return fuzzyMatch?.second.orEmpty()
}

private fun firstNonBlankUnit(targetName: String, candidates: List<Pair<String, String>>): String {
    val normalizedTarget = normalizeTemplateName(targetName)
    return candidates.firstOrNull { (candidateName, candidateUnit) ->
        candidateUnit.isNotBlank() && normalizeTemplateName(candidateName) == normalizedTarget
    }?.second.orEmpty()
}

private fun templateNameAliases(name: String): List<String> {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return emptyList()
    val parts = buildList {
        add(trimmed)
        if (trimmed.contains(">")) {
            add(trimmed.substringAfterLast(">").trim())
            add(trimmed.substringBefore(">").trim())
        }
        if (trimmed.contains(":")) {
            add(trimmed.substringAfterLast(":").trim())
            add(trimmed.substringBefore(":").trim())
        }
        if (trimmed.contains("/")) {
            add(trimmed.substringAfterLast("/").trim())
            add(trimmed.substringBefore("/").trim())
        }
    }
    return parts.filter { it.isNotBlank() }.distinct()
}

private fun normalizeTemplateName(value: String): String {
    val stripped = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')
    return stripped
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

