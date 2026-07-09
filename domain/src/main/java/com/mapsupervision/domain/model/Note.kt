package com.mapsupervision.domain.model

/**
 * Ghi chú hiện trường (Note) được đính kèm vào một nút giao hoặc tuyến đường cụ thể.
 * Lưu trữ ý kiến kỹ thuật hoặc khó khăn thi công phục vụ cho tóm tắt AI.
 * 
 * @property id Mã định danh duy nhất của ghi chú.
 * @property projectId Mã dự án quản lý.
 * @property objectCode Mã ký hiệu đối tượng kỹ thuật tương ứng (ví dụ: hố ga HG01).
 * @property content Nội dung văn bản ghi chú chi tiết.
 * @property createdAtEpochMs Dấu thời gian tạo ghi chú để lập dòng lịch sử sự kiện.
 */
data class Note(
    val id: String,
    val projectId: String,
    val objectCode: String,
    val content: String,
    val createdAtEpochMs: Long,
    val objectNodeId: String? = null,
    val objectRouteId: String? = null,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
