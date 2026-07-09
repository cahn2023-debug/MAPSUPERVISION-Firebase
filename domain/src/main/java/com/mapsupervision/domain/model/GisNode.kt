package com.mapsupervision.domain.model

/**
 * Đại diện cho một Nút kỹ thuật (GisNode) trên bản đồ số GIS.
 * Nút kỹ thuật có thể là một hố ga, cột thiết bị, tủ cáp, hoặc trạm đấu nối camera giám sát hiện trường.
 * 
 * @property id Mã định danh duy nhất của nút trong cơ sở dữ liệu hệ thống.
 * @property projectId Mã dự án mà nút này thuộc về.
 * @property code Mã ký hiệu kỹ thuật thực tế của nút (ví dụ: HG01, C02, T03).
 * @property contractor Tên nhà thầu phụ trách thi công và lắp đặt tại nút này.
 * @property latitude Vĩ độ GPS của nút trên bản đồ.
 * @property longitude Kinh độ GPS của nút trên bản đồ.
 * @property mapNumberLabel Nhãn hiển thị bản số hiệu trên bản đồ GIS trực quan.
 * @property workVolumeSummary Tóm tắt khối lượng công việc phân bổ tại nút giao.
 * @property importedFileId Mã tệp tin quy hoạch gốc (KML/KMZ) đã nhập nút này vào hệ thống (nếu có).
 */
data class GisNode(
    val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val latitude: Double,
    val longitude: Double,
    val mapNumberLabel: String = "",
    val workVolumeSummary: String = "",
    val importedFileId: String? = null,
    val ipAddress: String = "",
    val subnet: String = "",
    val gateway: String = "",
    val signalStatus: NodeSignalStatus = NodeSignalStatus.UNKNOWN,
    val updatedAtEpochMs: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)

