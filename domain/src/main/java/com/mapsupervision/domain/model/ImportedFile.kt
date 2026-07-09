package com.mapsupervision.domain.model

/**
 * Quản lý thông tin tệp tin quy hoạch được nhập vào hệ thống (ImportedFile).
 * Các định dạng hỗ trợ bao gồm bản đồ KML/KMZ, bảng khối lượng Excel, báo cáo PDF/Word.
 * 
 * @property id Mã định danh duy nhất của tệp tin.
 * @property projectId Mã dự án mà tệp tin này cung cấp dữ liệu thiết kế/quy hoạch.
 * @property fileName Tên tệp tin gốc của người dùng tải lên.
 * @property fileType Loại định dạng của tệp (ví dụ: KML, KMZ, XLSX, PDF).
 * @property storedPath Đường dẫn lưu trữ vật lý của tệp tin đã mã hóa trong thiết bị di động.
 * @property summary Tóm tắt kết quả phân tích dữ liệu tệp tin do hệ thống hoặc AI tạo ra.
 * @property importedAtEpochMs Dấu thời gian tệp tin được nhập vào hệ thống.
 */
data class ImportedFile(
    val id: String,
    val projectId: String,
    val fileName: String,
    val fileType: String,
    val storedPath: String,
    val summary: String,
    val importedAtEpochMs: Long,
    val updatedAtEpochMs: Long = importedAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
