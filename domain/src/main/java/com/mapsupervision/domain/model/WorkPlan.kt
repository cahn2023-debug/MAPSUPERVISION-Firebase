package com.mapsupervision.domain.model

/**
 * Đại diện cho Kế hoạch thi công hiện trường (WorkPlan) tại một vị trí/tuyến cụ thể.
 */
data class WorkPlan(
    val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val plannedDateEpochDay: Long,
    val nodeCode: String?,
    val routeCode: String?,
    val taskId: String?,
    val sourceRawInput: String,
    val createdAtEpochMs: Long,
    val quantity: Double = 0.0,
    val unit: String = "",
    val batchGroupId: String = "",
    val nodeId: String? = null,
    val routeId: String? = null
)
