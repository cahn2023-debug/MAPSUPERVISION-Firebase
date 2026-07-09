package com.mapsupervision.reporting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.domain.model.DailyLog

private const val DEFAULT_EMPTY_PROJECT_LABEL = "Chưa có thông tin dự án"
private const val DEFAULT_EMPTY_ISSUE_MESSAGE = "Chưa ghi nhận vướng mắc trong quá trình thi công."
private const val RECENT_ISSUES_LIMIT = 3

internal data class ProjectTextSummary(
    val projectLabel: String,
    val workItemCount: Int,
    val totalPlannedQty: Float,
    val totalActualQty: Float,
    val completionPercent: Float,
    val recentIssues: List<String>
)

internal fun buildProjectTextSummary(snapshot: ReportingSnapshot): ProjectTextSummary {
    val allRows = buildMaterialReportRows(
        nodes = snapshot.nodes,
        routes = snapshot.routes,
        rows = snapshot.workVolumeRowsRaw
    )
    val totalRow = allRows.firstOrNull { it.isTotal }
    return ProjectTextSummary(
        projectLabel = snapshot.projectName
            .ifBlank { snapshot.projectId.orEmpty() }
            .ifBlank { DEFAULT_EMPTY_PROJECT_LABEL },
        workItemCount = allRows.count { !it.isTotal },
        totalPlannedQty = totalRow?.totalPlannedQty ?: 0f,
        totalActualQty = totalRow?.totalActualQty ?: 0f,
        completionPercent = totalRow?.completionPercent ?: 0f,
        recentIssues = buildRecentIssueSummaries(snapshot.dailyLogs)
    )
}

internal fun buildRecentIssueSummaries(
    dailyLogs: List<DailyLog>,
    limit: Int = RECENT_ISSUES_LIMIT
): List<String> {
    if (limit <= 0) return emptyList()
    return dailyLogs
        .asSequence()
        .filter { it.note.isNotBlank() }
        .sortedByDescending { it.createdAtEpochMs }
        .map { log ->
            val location = log.nodeCode?.takeIf { it.isNotBlank() }
                ?: log.routeCode?.takeIf { it.isNotBlank() }
            buildString {
                append(log.workItem.ifBlank { "Công việc chưa xác định" })
                location?.let {
                    append(" (")
                    append(it)
                    append(")")
                }
                append(": ")
                append(log.note.trim())
            }
        }
        .take(limit)
        .toList()
}

@Composable
internal fun ProjectTextSummaryCard(
    summary: ProjectTextSummary,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.extendedColors.panelBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Tổng hợp dự án",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            ProjectSummarySection(
                title = "Thông tin dự án",
                content = "Dự án: ${summary.projectLabel}\nHạng mục công việc: ${summary.workItemCount}"
            )

            ProjectSummarySection(
                title = "Khối lượng hoàn thành",
                content = "Đã thi công ${summary.totalActualQty.toInt()} / ${summary.totalPlannedQty.toInt()} (${summary.completionPercent.toInt()}%)"
            )

            ProjectSummarySection(
                title = "Vướng mắc trong quá trình thi công",
                content = summary.recentIssues.ifEmpty { listOf(DEFAULT_EMPTY_ISSUE_MESSAGE) }.joinToString("\n")
            )
        }
    }
}

@Composable
private fun ProjectSummarySection(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.extendedColors.panelBackgroundAlt.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
