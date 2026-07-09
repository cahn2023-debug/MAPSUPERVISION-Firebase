package com.mapsupervision.domain.model

/**
 * Đại diện cho một hình ảnh chụp giám sát thực địa (SitePhoto) tại công trường.
 * Bức ảnh được đính kèm tọa độ GPS thời gian thực, phục vụ nghiệm thu hình ảnh và kiểm tra chất lượng thi công.
 * 
 * @property id Mã định danh duy nhất của ảnh trong hệ thống.
 * @property projectId Mã dự án quản lý bức ảnh.
 * @property objectCode Mã ký hiệu nút hoặc tuyến hạ tầng tương ứng (ví dụ: liên kết với [GisNode.code]).
 * @property filePath Đường dẫn lưu trữ vật lý của ảnh gốc chất lượng cao trên thiết bị di động.
 * @property thumbnailPath Đường dẫn ảnh thu nhỏ (thumbnail) đã nén để tối ưu tốc độ tải và bộ nhớ hiển thị UI.
 * @property latitude Vĩ độ GPS thực tế lúc chụp ảnh (nullable nếu camera chưa được cấp quyền vị trí hoặc GPS yếu).
 * @property longitude Kinh độ GPS thực tế lúc chụp ảnh (nullable).
 * @property engineer Tên kỹ sư giám sát hiện trường đã chụp và ký nhận bức ảnh.
 * @property capturedAtEpochMs Dấu thời gian chụp ảnh (Epoch Milliseconds) phục vụ lập nhật ký công việc hằng ngày.
 */
enum class MediaType {
    IMAGE,
    VIDEO
}

data class SitePhoto(
    val id: String,
    val projectId: String,
    val objectCode: String,
    val tagCodesCsv: String = "",
    val tagCodes: List<String> = emptyList(),
    val matchedNodeCode: String? = null,
    val matchedRouteCode: String? = null,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val isGpsMocked: Boolean,
    val locationStatus: PhotoLocationStatus,
    val engineer: String,
    val capturedAtEpochMs: Long,
    val matchedAtEpochMs: Long = 0L,
    val matchingTimeOffsetMs: Long = 0L,
    val mediaType: MediaType = MediaType.IMAGE,
    val mimeType: String = "image/jpeg",
    val durationMs: Long = 0L,
    val address: String? = null,
    val captureNote: String? = null,
    val matchedNodeId: String? = null,
    val matchedRouteId: String? = null,
    val updatedAtEpochMs: Long = capturedAtEpochMs,
    val syncStatus: SitePhotoSyncStatus = SitePhotoSyncStatus.PENDING,
    val remoteUrl: String? = null,
    val lastSyncAttemptEpochMs: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)

val SitePhoto.resolvedTagCodes: List<String>
    get() = if (tagCodes.isNotEmpty()) tagCodes else parseCsvList(tagCodesCsv)
