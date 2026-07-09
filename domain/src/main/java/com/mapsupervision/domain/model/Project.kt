package com.mapsupervision.domain.model

/**
 * Đại diện cho một Dự án Giám sát bản đồ số (Project) trong hệ thống.
 * Dự án chứa tập hợp toàn bộ Nút, Tuyến kỹ thuật, Ảnh chụp, Nhật ký thi công liên quan.
 * 
 * @property id Mã định danh duy nhất của dự án.
 * @property name Tên hiển thị đầy đủ của dự án (ví dụ: Giám sát Tuyến cáp quang Hà Nội - Hải Phòng).
 * @property slug Tên viết tắt chuẩn hóa URL không dấu (ví dụ: du-an-tuyen-cap-hanoi).
 * @property isArchived Cờ báo hiệu (Boolean) dự án đã được lưu trữ (đóng) hay đang hoạt động tích cực.
 * @property createdAtEpochMs Dấu thời gian khởi tạo dự án.
 */
data class Project(
    val id: String,
    val name: String,
    val slug: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val metadataVersion: Int = CURRENT_METADATA_VERSION,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val storageMode: ProjectStorageMode = ProjectStorageMode.LEGACY_SHARED,
    val projectDbPath: String = "",
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)

const val CURRENT_METADATA_VERSION: Int = 3
