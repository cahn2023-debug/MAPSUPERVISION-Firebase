package com.mapsupervision.domain.model

data class TimelineSnapshot(
    val projectId: String,
    val progress: List<NodeProgress>,
    val logs: List<DailyLog>,
    val photoCount: Int
)
