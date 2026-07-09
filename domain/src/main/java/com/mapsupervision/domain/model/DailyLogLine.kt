package com.mapsupervision.domain.model

enum class DailyLogLineType {
    PLAN_PRIMARY,
    EXTRA
}

data class DailyLogLine(
    val id: String,
    val projectId: String,
    val dailyLogId: String,
    val lineType: DailyLogLineType,
    val workName: String,
    val categoryName: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val nodeCode: String? = null,
    val routeCode: String? = null,
    val linkedWorkPlanId: String? = null,
    val nodeId: String? = null,
    val routeId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs
)
