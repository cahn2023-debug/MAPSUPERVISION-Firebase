package com.mapsupervision.reporting.ui

import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto

data class ReportingSnapshot(
    val projectId: String? = null,
    val projectName: String = "",
    val version: Long = 0L,
    val loadedAtEpochMs: Long = 0L,
    val nodes: List<GisNode> = emptyList(),
    val routes: List<GisRoute> = emptyList(),
    val photos: List<SitePhoto> = emptyList(),
    val progress: List<NodeProgress> = emptyList(),
    val workVolumeRowsRaw: List<WorkVolumeProgress> = emptyList(),
    val workVolumeRows: List<MaterialReportRow> = emptyList(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val aiDraft: ReportDraftResult? = null
) {
    companion object {
        val Empty = ReportingSnapshot()
    }

    fun isForProject(projectId: String): Boolean = this.projectId == projectId

    fun withDraft(draft: ReportDraftResult?): ReportingSnapshot = copy(aiDraft = draft)
}


