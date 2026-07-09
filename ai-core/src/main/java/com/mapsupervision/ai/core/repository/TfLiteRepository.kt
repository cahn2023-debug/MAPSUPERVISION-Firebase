package com.mapsupervision.ai.core.repository

import com.mapsupervision.ai.core.DiscrepancyResult
import com.mapsupervision.ai.core.PhotoQualityResult

/**
 * Giao diện Kho lưu trữ TensorFlow Lite (TfLiteRepository) ở tầng AI Core.
 * Định nghĩa các phương thức chạy mô hình AI offline để kiểm định mờ/chất lượng ảnh và phát hiện bất thường dữ liệu.
 */
interface TfLiteRepository {
    
    /**
     * Kiểm tra xem mô hình đánh giá chất lượng ảnh đã được nạp thành công vào bộ nhớ chưa.
     */
    fun isPhotoQualityModelLoaded(): Boolean
    
    /**
     * Kiểm tra xem mô hình phân tích sai lệch dữ liệu đã được nạp thành công chưa.
     */
    fun isDiscrepancyModelLoaded(): Boolean
    
    /**
     * Thực hiện chạy suy luận offline đánh giá chất lượng hình ảnh từ đường dẫn tệp ảnh vật lý.
     * 
     * @param filePath Đường dẫn tệp ảnh thực tế trên thiết bị di động.
     * @return [PhotoQualityResult] Kết quả đánh giá chất lượng kèm điểm số và cờ cảnh báo chụp lại.
     */
    fun checkPhotoQuality(filePath: String): PhotoQualityResult
    
    /**
     * Thực hiện chạy suy luận offline phát hiện sai lệch dựa trên mảng đặc trưng số.
     * 
     * @param features Các đặc trưng số trích xuất để đối chiếu.
     * @return [DiscrepancyResult] Kết quả phát hiện sai lệch và các hành động gợi ý.
     */
    fun checkDiscrepancy(features: FloatArray): DiscrepancyResult
}
