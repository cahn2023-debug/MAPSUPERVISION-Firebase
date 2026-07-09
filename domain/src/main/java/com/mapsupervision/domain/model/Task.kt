package com.mapsupervision.domain.model

/**
 * Trạng thái hiện tại của nhiệm vụ (TaskStatus) được gán trên hiện trường.
 */
enum class TaskStatus {
    /** Nhiệm vụ cần làm. */
    TODO,
    /** Nhiệm vụ đang được triển khai. */
    IN_PROGRESS,
    /** Nhiệm vụ đã hoàn tất thành công. */
    COMPLETED
}

/**
 * Quản lý các nhiệm vụ/công việc cần xử lý (Task) tại từng vị trí thi công cụ thể.
 * Thường được tạo tự động từ các gợi ý nhiệm vụ của AI dựa trên ghi chú khó khăn hiện trường.
 * 
 * @property id Mã định danh duy nhất của nhiệm vụ.
 * @property projectId Mã dự án quản lý.
 * @property objectCode Mã ký hiệu đối tượng kỹ thuật gắn liền (ví dụ: HG01).
 * @property title Tiêu đề tóm tắt ngắn gọn của nhiệm vụ.
 * @property description Mô tả chi tiết các bước xử lý kỹ thuật yêu cầu.
 * @property status Trạng thái thi công hiện tại (Todo, In Progress, Completed).
 * @property createdAtEpochMs Dấu thời gian tạo nhiệm vụ.
 * @property completedAtEpochMs Dấu thời gian hoàn thành nhiệm vụ (nullable nếu chưa làm xong).
 */
data class Task(
    val id: String,
    val projectId: String,
    val objectCode: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val objectNodeId: String? = null,
    val objectRouteId: String? = null,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
