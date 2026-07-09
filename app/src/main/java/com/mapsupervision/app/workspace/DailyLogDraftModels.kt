package com.mapsupervision.app.workspace

data class DailyLogDraftLine(
    val id: String,
    val workName: String = "",
    val categoryName: String = "",
    val quantityInput: String = "",
    val unit: String = "",
    val nodeCode: String? = null,
    val routeCode: String? = null
)

data class DailyLogPlanSnapshotDraft(
    val linkedWorkPlanId: String? = null,
    val plannedWorkName: String = "",
    val plannedQuantity: Double = 0.0,
    val plannedUnit: String = "",
    val plannedNodeCode: String? = null,
    val plannedRouteCode: String? = null
)

data class AddDailyLogRequest(
    val workItem: String,
    val manpower: Int,
    val note: String,
    val weather: String = "",
    val temperature: Double = 0.0,
    val nodeCode: String? = null,
    val routeCode: String? = null,
    val dateEpochDay: Long = 0L,
    val planSnapshot: DailyLogPlanSnapshotDraft? = null,
    val actualLines: List<DailyLogDraftLine> = emptyList(),
    val existingId: String? = null,
    val actualProgressPercent: Float? = null
)
