package com.mapsupervision.ai.core.repository

import com.mapsupervision.ai.core.DiscrepancyCheckPayload
import com.mapsupervision.ai.core.DiscrepancyResult
import com.mapsupervision.ai.core.ImportMappingPayload
import com.mapsupervision.ai.core.ImportMappingResult
import com.mapsupervision.ai.core.OpsRecommendationPayload
import com.mapsupervision.ai.core.OpsRecommendationResult
import com.mapsupervision.ai.core.PhotoQualityPayload
import com.mapsupervision.ai.core.PhotoQualityResult
import com.mapsupervision.ai.core.ReportDraftPayload
import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.ai.core.TimelineSummaryPayload
import com.mapsupervision.ai.core.TimelineSummaryResult

/**
 * Giao diện Kho lưu trữ AI (AiRepository) định nghĩa các dịch vụ xử lý AI đám mây/mô hình lớn.
 * Được hiện thực hóa ở tầng Data bằng các API như Google Gemini hoặc các bộ máy tính toán tương đương.
 */
interface AiRepository {
    
    /**
     * Phân tích danh sách tiêu đề cột (Headers) và dữ liệu mẫu Excel để gợi ý cấu hình ánh xạ cột tối ưu.
     * 
     * @param payload Chứa danh sách Headers và các dòng dữ liệu Excel mẫu.
     * @return [ImportMappingResult] Gợi ý ánh xạ cột chính xác cho mã nút, tọa độ, nhà thầu và công việc.
     */
    suspend fun suggestMapping(payload: ImportMappingPayload): ImportMappingResult
    
    /**
     * Phát hiện sai lệch (Discrepancy) giữa dữ liệu import mới so với dữ liệu thực tế hiện hữu (như lệch GPS hoặc sai nhà thầu).
     * 
     * @param payload Danh sách các dòng dữ liệu đối sánh thực tế.
     * @return [DiscrepancyResult] Danh sách các sai lệch phát hiện và hành động đề xuất khắc phục.
     */
    suspend fun detectDiscrepancies(payload: DiscrepancyCheckPayload): DiscrepancyResult
    
    /**
     * Tạo tóm tắt nhật ký vận hành (Daily/Weekly Timeline Summary) dựa trên số lượng nút, ảnh hiện trường và nhật ký làm việc trong ngày.
     * 
     * @param payload Tập hợp tiến độ các nút giao, danh sách nhật ký ngày và số lượng ảnh minh chứng.
     * @return [TimelineSummaryResult] Văn bản tóm tắt tình hình thi công và các điểm nóng trễ tiến độ nổi bật.
     */
    suspend fun summarizeDaily(payload: TimelineSummaryPayload): TimelineSummaryResult
    
    /**
     * Đánh giá chất lượng hình ảnh và metadata của ảnh chụp hiện trường (tọa độ GPS, kỹ sư ký tên, mã đối tượng tương ứng).
     * 
     * @param payload Metadata của bức ảnh thực địa.
     * @return [PhotoQualityResult] Điểm chất lượng (0-100), các vấn đề phát hiện và đề xuất chụp lại nếu không đạt.
     */
    suspend fun photoQualityCheck(payload: PhotoQualityPayload): PhotoQualityResult
    
    /**
     * Tự động dự thảo báo cáo thi công tổng hợp chất lượng cao (Report Draft) dành cho Ban lãnh đạo/Đối tác.
     * Tổng hợp chuyên sâu số liệu tiến độ thực tế, đánh giá nhà thầu chậm trễ và các khó khăn thực địa rút ra từ nhật ký ngày.
     * 
     * @param payload Các số liệu tổng hợp tiến độ và ghi chú khó khăn hiện trường.
     * @return [ReportDraftResult] Dự thảo báo cáo bao gồm tóm tắt điều hành, phân tích rủi ro và các giải pháp kỹ thuật cụ thể.
     */
    suspend fun reportDraft(payload: ReportDraftPayload): ReportDraftResult
    
    /**
     * Phân tích chỉ số thi công chung và cảnh báo sai lệch để đề xuất các hoạt động vận hành ưu tiên (Ops Recommendation).
     * 
     * @param payload Thống kê tổng quan số nút thi công chậm, tỷ lệ hoàn thành dự án và số cảnh báo import.
     * @return [OpsRecommendationResult] Danh sách hoạt động ưu tiên kèm cấp độ nghiêm trọng của dự án (Priority level 1-3).
     */
    suspend fun operationRecommendations(payload: OpsRecommendationPayload): OpsRecommendationResult
}
