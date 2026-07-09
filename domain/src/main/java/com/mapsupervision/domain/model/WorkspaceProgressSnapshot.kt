package com.mapsupervision.domain.model

data class WorkspaceProgressSnapshot(
    val projectId: String,
    val workVolumeRows: List<WorkVolumeProgress> = emptyList(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val workCategories: List<WorkCategory> = emptyList()
)
