package com.mapsupervision.domain.model

/**
 * Nhật ký công việc hằng ngày (DailyLog) ghi nhận tiến độ hoạt động tại công trường.
 * 
 * @property id Mã định danh duy nhất của bản ghi nhật ký.
 * @property projectId Mã dự án quản lý.
 * @property workItem Tên hạng mục công việc được thực hiện (ví dụ: rải cáp, đổ bê tông móng).
 * @property manpower Số lượng nhân sự/nhân công tham gia thực hiện hạng mục này trong ngày.
 * @property note Ghi chú chi tiết hoặc báo cáo khó khăn phát sinh tại công trường.
 * @property createdAtEpochMs Dấu thời gian tạo nhật ký (Epoch Milliseconds) để sắp xếp theo dòng thời gian (timeline).
 */
data class DailyLog(
    val id: String,
    val projectId: String,
    val workItem: String,
    val manpower: Int,
    val note: String,
    val createdAtEpochMs: Long,
    val weather: String = "",
    val temperature: Double = 0.0,
    val nodeCode: String? = null,
    val routeCode: String? = null,
    val dateEpochDay: Long = 0L,
    val volume: Double = 0.0,
    val unit: String = "",
    val categoryName: String = "",
    val batchGroupId: String = "",
    val linkedWorkPlanId: String? = null,
    val plannedWorkName: String = "",
    val plannedQuantity: Double = 0.0,
    val plannedUnit: String = "",
    val plannedNodeCode: String? = null,
    val plannedRouteCode: String? = null,
    val appliedNodeCodesCsv: String = "",
    val linkedPhotoIdsCsv: String = "",
    val appliedNodeIds: List<String> = emptyList(),
    val linkedPhotoIds: List<String> = emptyList(),
    val photoMatchOffsetMinutes: Int = 0,
    val nodeId: String? = null,
    val routeId: String? = null,
    val plannedNodeId: String? = null,
    val plannedRouteId: String? = null,
    val lines: List<DailyLogLine> = emptyList(),
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)

val DailyLog.resolvedAppliedNodeIds: List<String>
    get() = if (appliedNodeIds.isNotEmpty()) appliedNodeIds else parseCsvList(appliedNodeCodesCsv)

val DailyLog.resolvedLinkedPhotoIds: List<String>
    get() = if (linkedPhotoIds.isNotEmpty()) linkedPhotoIds else parseCsvList(linkedPhotoIdsCsv)

fun DailyLog.resolveEpochDay(): Long {
    if (dateEpochDay != 0L) return dateEpochDay
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = createdAtEpochMs }
    return java.time.LocalDate.of(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).toEpochDay()
}
