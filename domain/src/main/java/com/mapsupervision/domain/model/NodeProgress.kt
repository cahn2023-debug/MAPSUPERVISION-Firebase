package com.mapsupervision.domain.model

/**
 * Quản lý chỉ số tiến độ thi công (NodeProgress) của một nút hạ tầng.
 * Dùng để theo dõi khối lượng thi công thực tế so với mục tiêu kế hoạch đề ra.
 * 
 * @property id Mã định danh duy nhất của bản ghi tiến độ.
 * @property projectId Mã dự án quản lý.
 * @property nodeCode Mã ký hiệu nút hạ tầng tương ứng (liên kết với [GisNode.code]).
 * @property planned Tỷ lệ hoặc sản lượng tiến độ kế hoạch đặt ra (thang đo phần trăm từ 0% đến 100%).
 * @property actual Tỷ lệ hoặc sản lượng thi công thực tế đã đạt được tại thực địa.
 * @property remain Tỷ lệ hoặc sản lượng còn lại cần hoàn thiện để đạt 100%.
 * @property delayed Cờ báo hiệu (Boolean) xem nút này có đang bị chậm tiến độ so với kế hoạch ban đầu hay không.
 * @property updatedAtEpochMs Dấu thời gian (Epoch Milliseconds) cập nhật tiến độ lần cuối cùng.
 */
data class NodeProgress(
    val id: String,
    val projectId: String,
    val nodeCode: String,
    val planned: Float,
    val actual: Float,
    val remain: Float,
    val delayed: Boolean,
    val updatedAtEpochMs: Long = 0L,
    val nodeId: String? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
