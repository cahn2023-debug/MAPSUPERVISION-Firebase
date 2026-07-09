package com.mapsupervision.domain.model

/**
 * Quản lý chi tiết khối lượng công việc cho từng nút kỹ thuật.
 *
 * @property id Mã định danh duy nhất của bản ghi.
 * @property projectId Mã dự án quản lý.
 * @property nodeCode Mã ký hiệu nút hạ tầng tương ứng (liên kết với [GisNode.code]).
 * @property workName Tên công việc / hạng mục kỹ thuật.
 * @property plannedQty Khối lượng công việc theo hồ sơ thiết kế kế hoạch.
 * @property actualQty Khối lượng công việc thực tế đã thực hiện tại thực địa.
 * @property updatedAtEpochMs Dấu thời gian cập nhật lần cuối cùng.
 */
data class WorkVolumeProgress(
    val id: String,
    val projectId: String,
    val nodeCode: String,
    val workName: String,
    val plannedQty: Float,
    val actualQty: Float,
    val updatedAtEpochMs: Long,
    val unit: String = "",
    val nodeId: String? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
) {
    val materialName: String
        get() = workName
}

typealias MaterialProgress = WorkVolumeProgress
