package com.mapsupervision.domain.model

data class ReportWorkspaceSnapshot(
    val projectId: String,
    val projectName: String,
    val projectSlug: String,
    val workspace: WorkspaceSnapshot
)
