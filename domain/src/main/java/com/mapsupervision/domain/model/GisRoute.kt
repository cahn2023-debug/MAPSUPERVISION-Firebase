package com.mapsupervision.domain.model

/**
 * Đại diện cho một Tuyến/Phân đoạn kỹ thuật (GisRoute) trên bản đồ số GIS.
 * Tuyến kỹ thuật là đoạn nối tiếp liên kết hai nút (ví dụ: đường ống ngầm chạy cáp, tuyến kết nối quang).
 * 
 * @property id Mã định danh duy nhất của tuyến trong cơ sở dữ liệu hệ thống.
 * @property projectId Mã dự án mà tuyến này thuộc về.
 * @property code Mã ký hiệu của tuyến kỹ thuật (ví dụ: T_HG01_HG02).
 * @property contractor Tên nhà thầu phụ trách kéo cáp hoặc rải ống tại tuyến này.
 * @property startNodeCode Mã nút bắt đầu của đoạn tuyến (liên kết khóa ngoại logic với [GisNode.code]).
 * @property endNodeCode Mã nút kết thúc của đoạn tuyến (liên kết khóa ngoại logic với [GisNode.code]).
 * @property importedFileId Mã tệp tin quy hoạch gốc (KML/KMZ) đã nhập tuyến này vào hệ thống (nếu có).
 */
data class GisRoute(
    val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val startNodeCode: String,
    val endNodeCode: String,
    val points: List<Pair<Double, Double>> = emptyList(),
    val importedFileId: String? = null,
    val designLength: String? = null,
    val fiberCoreCount: Int? = null,
    val fiberConnection: String = "",
    val startNodeId: String? = null,
    val endNodeId: String? = null,
    val updatedAtEpochMs: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
