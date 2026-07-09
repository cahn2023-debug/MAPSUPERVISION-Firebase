package com.mapsupervision.ai.agent

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.ai.core.SummaryRequestDraft
import com.mapsupervision.ai.core.SummaryRow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryAggregator @Inject constructor(
    private val gisRepository: GisRepository,
    private val progressRepository: ProgressRepository,
    private val workVolumeProgressRepository: WorkVolumeProgressRepository,
    private val dailyLogRepository: DailyLogRepository
) {
    suspend fun aggregate(draft: SummaryRequestDraft): List<SummaryRow> {
        val projectId = draft.projectId
        val nodesResult = gisRepository.searchNodes(projectId, "")
        val progressResult = progressRepository.byProject(projectId)
        val materialResult = workVolumeProgressRepository.byProject(projectId)
        val dailyLogsResult = dailyLogRepository.byProject(projectId)

        val nodes = (nodesResult as? AppResult.Success)?.data.orEmpty()
        val progress = (progressResult as? AppResult.Success)?.data.orEmpty()
        val material = (materialResult as? AppResult.Success)?.data.orEmpty()
        val dailyLogs = (dailyLogsResult as? AppResult.Success)?.data.orEmpty()

        val progressMap = progress.associateBy { it.nodeCode }
        val materialGrouped = material.groupBy { it.nodeCode }

        val filteredNodes = when (draft.scope) {
            "contractor" -> {
                val filterVal = draft.filterValue
                if (filterVal != null) nodes.filter { it.contractor.equals(filterVal, ignoreCase = true) } else nodes
            }
            "node" -> {
                val filterVal = draft.filterValue
                if (filterVal != null) nodes.filter { it.code.equals(filterVal, ignoreCase = true) } else nodes
            }
            else -> nodes
        }

        val nodeCodesFiltered = filteredNodes.map { it.code }.toSet()

        val fromEpoch = draft.dateFromEpochDay
        val toEpoch = draft.dateToEpochDay
        val filteredLogs = if (draft.scope == "time_range" && fromEpoch != null && toEpoch != null) {
            dailyLogs.filter { it.dateEpochDay in fromEpoch..toEpoch }
        } else {
            dailyLogs
        }

        val groups = when (draft.groupBy) {
            "contractor" -> filteredNodes.groupBy { it.contractor }
            "status" -> filteredNodes.groupBy { node ->
                val p = progressMap[node.code]
                val act = p?.actual ?: 0f
                val plan = p?.planned ?: 100f
                when {
                    act >= plan -> "COMPLETED"
                    act > 0f -> "IN_PROGRESS"
                    else -> "NOT_STARTED"
                }
            }
            else -> mapOf("Dự án" to filteredNodes)
        }

        return groups.map { (groupKey, groupNodes) ->
            val groupNodeCodes = groupNodes.map { it.code }.toSet()
            val groupProgress = groupNodes.mapNotNull { progressMap[it.code] }
            
            val totalNodes = groupNodes.size
            val completedNodes = groupProgress.count { it.actual >= it.planned }
            val avgProgress = if (groupProgress.isNotEmpty()) groupProgress.map { it.actual }.average().toFloat() else 0f
            val delayedCount = groupProgress.count { it.delayed }
            
            val totalMaterialVolume = groupNodes.sumOf { node ->
                materialGrouped[node.code]?.sumOf { it.actualQty.toDouble() } ?: 0.0
            }
            val totalLogVolume = filteredLogs.filter { it.nodeCode in groupNodeCodes }.sumOf { it.volume }

            val maxProgressUpdated = if (groupProgress.isNotEmpty()) groupProgress.maxOf { it.updatedAtEpochMs } else 0L
            val maxLogUpdated = if (filteredLogs.isNotEmpty()) filteredLogs.filter { it.nodeCode in groupNodeCodes }.maxOfOrNull { it.createdAtEpochMs } ?: 0L else 0L

            SummaryRow(
                groupKey = groupKey,
                totalNodes = totalNodes,
                completedNodes = completedNodes,
                avgProgress = avgProgress,
                delayedCount = delayedCount,
                totalVolume = totalMaterialVolume + totalLogVolume,
                lastUpdatedEpochMs = maxOf(maxProgressUpdated, maxLogUpdated)
            )
        }
    }
}

