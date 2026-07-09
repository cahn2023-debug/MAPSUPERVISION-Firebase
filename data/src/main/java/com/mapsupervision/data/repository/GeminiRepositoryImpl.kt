package com.mapsupervision.data.repository

import com.google.ai.client.generativeai.GenerativeModel
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
import com.mapsupervision.ai.core.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

class GeminiRepositoryImpl @Inject constructor() : AiRepository {
    companion object {
        private const val apiTimeoutMs = 8_000L
    }

    private val apiKey = com.mapsupervision.data.BuildConfig.GEMINI_API_KEY

    private val isConfigured: Boolean
        get() = apiKey.isNotBlank() && !apiKey.contains("YOUR_API_KEY", ignoreCase = true)

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    override suspend fun suggestMapping(payload: ImportMappingPayload): ImportMappingResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext suggestMappingLocal(payload)
        }
        val fileType = payload.fileType
        val isExcel = fileType == null || fileType.equals("xlsx", ignoreCase = true) || fileType.equals("xls", ignoreCase = true)
        val fileTypeLabel = if (isExcel) "Excel" else fileType.uppercase()
        val fileDesc = if (isExcel) "Excel giám sát thi công" else "$fileTypeLabel chứa thông tin địa lý của các đối tượng hạ tầng"
        val itemsRule = if (isExcel) {
            "3. Danh sách \"items\" phải chứa các tên cột khớp chính xác 100% từ danh sách Headers đại diện cho các công việc, khối lượng công việc và dữ liệu thực hiện. Không chứa các cột kết cấu hay thông tin hành chính khác."
        } else {
            "3. Đối với tệp tin địa lý ($fileTypeLabel), cột \"items\" có thể trống hoặc chứa các trường dữ liệu bổ sung (ExtendedData/properties) đặc trưng cho công việc hoặc thuộc tính chi tiết của đối tượng."
        }

        val formattedSampleRows = if (payload.sampleRows.isEmpty()) {
            "Không có hàng mẫu."
        } else {
            val sb = java.lang.StringBuilder()
            payload.sampleRows.forEachIndexed { rIdx, row ->
                sb.append("Hang mau #${rIdx + 1}:\n")
                payload.headers.forEachIndexed { hIdx, header ->
                    val value = row.getOrNull(hIdx).orEmpty().trim()
                    if (value.isNotEmpty()) {
                        sb.append("  - $header: $value\n")
                    }
                }
            }
            sb.toString()
        }

        val prompt = """
            Bạn là trợ lý phân tích file $fileDesc.
            Trả về JSON hợp lệ duy nhất theo schema:
            {
              "node_code": "string",
              "latitude": "string",
              "longitude": "string",
              "contractor": "string",
              "items": ["string"],
              "requires_manual_review": true|false
            }

            Quy tắc bắt buộc:
            1. Các giá trị "node_code", "latitude", "longitude", "contractor" phải là một chuỗi khớp chính xác 100% (bao gồm cả dấu và viết hoa/thường) với một trong các tên cột/trường trong danh sách Headers dưới đây.
            2. Nếu không tìm thấy cột/trường tương ứng trong danh sách Headers, hãy để giá trị trống ("").
            $itemsRule
            4. Cột/trường chứa từ khóa liên quan đến "nhà thầu" hoặc "tọa độ/vĩ độ/kinh độ" tuyệt đối KHÔNG được ánh xạ vào trường "node_code".

            Headers: ${payload.headers.joinToString(", ")}
            Sample rows:
            $formattedSampleRows
        """.trimIndent()
        val text = withTimeout(apiTimeoutMs) {
            generativeModel.generateContent(prompt).text
        } ?: throw IllegalStateException("empty model response")
        parseImportMapping(text)
    }

    private fun suggestMappingLocal(payload: ImportMappingPayload): ImportMappingResult {
        return com.mapsupervision.ai.core.engines.ImportMappingHelper.suggestMapping(payload.headers)
    }

    override suspend fun detectDiscrepancies(payload: DiscrepancyCheckPayload): DiscrepancyResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext detectDiscrepanciesLocal(payload)
        }
        val rows = payload.rows.joinToString("\n") {
            "code=${it.code}, incoming=${it.incomingContractor}, existing=${it.existingContractor}, dist=${it.distanceMeters}"
        }
        val prompt = """
            Phân tích sai lệch dữ liệu hạ tầng và trả về JSON:
            {
              "issues": ["string"],
              "recommended_actions": ["string"]
            }
            Dữ liệu:
            $rows
        """.trimIndent()
        val text = withTimeout(apiTimeoutMs) {
            generativeModel.generateContent(prompt).text
        } ?: throw IllegalStateException("empty model response")
        val json = parseObject(text)
        DiscrepancyResult(
            issues = json.optJSONArray("issues").toStringList(),
            recommendedActions = json.optJSONArray("recommended_actions").toStringList()
        )
    }

    private fun detectDiscrepanciesLocal(payload: DiscrepancyCheckPayload): DiscrepancyResult {
        val issues = ArrayList<String>()
        val actions = ArrayList<String>()
        payload.rows.forEach { row ->
            if (row.distanceMeters > 50.0) {
                issues.add("Trạm ${row.code} lệch tọa độ thiết kế ${"%.1f".format(Locale.US, row.distanceMeters)}m.")
                actions.add("Kiểm tra lại thực địa hoặc cập nhật tọa độ chuẩn cho trạm ${row.code}.")
            }
            if (row.incomingContractor.isNotBlank() && row.existingContractor.isNotBlank() &&
                !row.incomingContractor.equals(row.existingContractor, ignoreCase = true)) {
                issues.add("Trạm ${row.code} có sự sai khác nhà thầu: '${row.incomingContractor}' vs '${row.existingContractor}'.")
                actions.add("Xác minh nhà thầu thi công chính xác cho trạm ${row.code}.")
            }
        }
        if (issues.isEmpty()) {
            issues.add("Không phát hiện sai lệch nghiêm trọng nào về tọa độ và nhà thầu.")
            actions.add("Duy trì chế độ kiểm soát dữ liệu định kỳ.")
        }
        return DiscrepancyResult(issues = issues, recommendedActions = actions)
    }

    override suspend fun summarizeDaily(payload: TimelineSummaryPayload): TimelineSummaryResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext summarizeDailyLocal(payload)
        }
        val delayed = payload.progress.count { it.actual < it.planned }
        val prompt = """
            Tóm tắt vận hành thi công theo ngày/tuần và trả về JSON:
            {
              "summary": "string",
              "issue_highlights": ["string"],
              "recommended_actions": ["string"]
            }
            nodes=${payload.progress.size}, delayed=$delayed, logs=${payload.logs.size}, photos=${payload.photoCount}
        """.trimIndent()
        val text = withTimeout(apiTimeoutMs) {
            generativeModel.generateContent(prompt).text
        } ?: throw IllegalStateException("empty model response")
        val json = parseObject(text)
        TimelineSummaryResult(
            summary = json.optString("summary", ""),
            issueHighlights = json.optJSONArray("issue_highlights").toStringList(),
            recommendedActions = json.optJSONArray("recommended_actions").toStringList()
        )
    }

    private fun summarizeDailyLocal(payload: TimelineSummaryPayload): TimelineSummaryResult {
        val delayed = payload.progress.count { it.actual < it.planned }
        val summary = "Tóm tắt tiến độ: Tổng số ${payload.progress.size} trạm, có $delayed trạm bị chậm trễ. " +
                "Hôm nay ghi nhận ${payload.logs.size} lượt nhật ký và ${payload.photoCount} ảnh hiện trường."
        val highlights = ArrayList<String>()
        if (delayed > 0) highlights.add("Có $delayed trạm thi công chậm so với kế hoạch.")
        if (payload.photoCount == 0) highlights.add("Thiếu ảnh giám sát trực quan trong ngày.")
        val actions = listOf(
            "Yêu cầu các nhà thầu cập nhật lý do chậm trễ tại các trạm bị muộn.",
            "Bổ sung ảnh chụp hiện trường đầy đủ cho các công đoạn hoàn thành."
        )
        return TimelineSummaryResult(summary = summary, issueHighlights = highlights, recommendedActions = actions)
    }

    override suspend fun photoQualityCheck(payload: PhotoQualityPayload): PhotoQualityResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext photoQualityCheckLocal(payload)
        }
        val prompt = """
            Đánh giá chất lượng metadata ảnh hiện trường và trả về JSON:
            {
              "score": 0-100,
              "issues": ["string"],
              "recommendation": "string",
              "should_retake": true|false
            }
            objectCode=${payload.objectCode}, engineer=${payload.engineer}, lat=${payload.latitude}, lon=${payload.longitude}
        """.trimIndent()
        val text = withTimeout(apiTimeoutMs) {
            generativeModel.generateContent(prompt).text
        } ?: throw IllegalStateException("empty model response")
        val json = parseObject(text)
        PhotoQualityResult(
            score = json.optInt("score", 70).coerceIn(0, 100),
            issues = json.optJSONArray("issues").toStringList(),
            recommendation = json.optString("recommendation", ""),
            shouldRetake = json.optBoolean("should_retake", false)
        )
    }

    private fun photoQualityCheckLocal(payload: PhotoQualityPayload): PhotoQualityResult {
        val issues = ArrayList<String>()
        if (payload.latitude == 0.0 || payload.longitude == 0.0) {
            issues.add("Không tìm thấy thông tin định vị GPS đi kèm ảnh.")
        }
        val score = if (issues.isEmpty()) 90 else 40
        return PhotoQualityResult(
            score = score,
            issues = issues,
            recommendation = if (issues.isEmpty()) "Ảnh đạt chất lượng giám sát tốt." else "Chụp lại ảnh có bật quyền truy cập vị trí GPS.",
            shouldRetake = issues.isNotEmpty()
        )
    }

    override suspend fun reportDraft(payload: ReportDraftPayload): ReportDraftResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext reportDraftLocal(payload)
        }
        val prompt = """
            Bạn là một Giám đốc dự án AI chuyên nghiệp chịu trách nhiệm báo cáo điều hành thi công hạ tầng giao thông/viễn thông.
            Hãy viết một báo cáo tóm tắt tổng hợp thi công chi tiết và trả về định dạng JSON duy nhất khớp chính xác cấu trúc sau:
            {
              "executive_summary": "Tóm tắt tiến độ các nút giao, các tuyến cáp đã thi công như thế nào VÀ đánh giá chi tiết nhà thầu nào đang chậm tiến độ, cần đẩy nhanh ra sao bằng các biện pháp cụ thể.",
              "risk_section": "Tổng hợp các khó khăn, vướng mắc thực tế rút ra từ nhật ký/ghi chú hiện trường (ví dụ vướng mặt bằng, kỹ thuật, thời tiết).",
              "recommended_actions": [
                "Kế hoạch hành động chi tiết 1",
                "Kế hoạch hành động chi tiết 2",
                "Giải pháp kỹ thuật cụ thể"
              ]
            }

            Hãy sử dụng ngôn ngữ tiếng Việt chuyên nghiệp, ngắn gọn, có số liệu rõ ràng và phân tích sâu sắc từ dữ liệu đầu vào dưới đây:
            - Mã dự án: ${payload.projectId}
            - Tổng số điểm nút kỹ thuật: ${payload.totalNodes}
            - Số nút đang thực hiện: ${payload.inProgressNodes}
            - Số nút bị chậm tiến độ: ${payload.delayedNodes}
            - Tiến độ thực tế trung bình: ${"%.1f".format(payload.avgActualProgress)}%
            - Tổng số ảnh thực địa làm bằng chứng: ${payload.totalPhotos} ảnh tại các nút: ${payload.photoNodesSummary}
            
            [DỮ LIỆU TIẾN ĐỘ CHI TIẾT CÁC NÚT GIAO & TUYẾN CÁP]:
            ${payload.nodesSummary.ifBlank { "Chưa có thông tin cụ thể." }}

            [DỮ LIỆU ĐÁNH GIÁ TIẾN ĐỘ NHÀ THẦU]:
            ${payload.contractorsSummary.ifBlank { "Chưa có thông tin cụ thể." }}

            [TỔNG HỢP GHI CHÚ / NHẬT KÝ THỰC ĐỊA]:
            ${payload.notesSummary.ifBlank { "Chưa có ghi chú khó khăn nào từ hiện trường." }}
        """.trimIndent()
        val text = withTimeout(apiTimeoutMs) {
            generativeModel.generateContent(prompt).text
        } ?: throw IllegalStateException("empty model response")
        val json = parseObject(text)
        ReportDraftResult(
            executiveSummary = json.optString("executive_summary", ""),
            riskSection = json.optString("risk_section", ""),
            recommendedActions = json.optJSONArray("recommended_actions").toStringList()
        )
    }

    private fun reportDraftLocal(payload: ReportDraftPayload): ReportDraftResult {
        val exec = buildString {
            append("BÁO CÁO TIẾN ĐỘ THI CÔNG:\n")
            append("- Mã dự án: ${payload.projectId}\n")
            append("- Tổng số điểm nút/tuyến hạ tầng theo thiết kế: ${payload.totalNodes} nút\n")
            append("- Số nút đang thực hiện: ${payload.inProgressNodes} nút\n")
            append("- Tiến độ thực tế trung bình toàn dự án đạt ${"%.1f".format(payload.avgActualProgress)}%.\n")
            if (payload.totalPhotos > 0) {
                append("- Dữ liệu hình ảnh: Có ${payload.totalPhotos} ảnh hiện trường tại nút: ${payload.photoNodesSummary}.\n\n")
            } else {
                append("- Dữ liệu hình ảnh: Chưa có ảnh hiện trường.\n\n")
            }
            
            if (payload.nodesSummary.isNotBlank()) {
                append("[TÌNH HÌNH THI CÔNG CÁC NÚT GIAO & TUYẾN CÁP]:\n")
                append(payload.nodesSummary)
                append("\n\n")
            }
            
            append("[ĐÁNH GIÁ TIẾN ĐỘ NHÀ THẦU]:\n")
            if (payload.contractorsSummary.isNotBlank()) {
                append(payload.contractorsSummary)
            } else {
                append("- Ghi nhận ${payload.delayedNodes} điểm thi công đang chậm tiến độ. Đề xuất đôn đốc các nhà thầu phụ trách các điểm nóng này.")
            }
        }
        
        val risk = buildString {
            if (payload.notesSummary.isNotBlank()) {
                append("[TỔNG HỢP KHÓ KHĂN & VƯỚNG MẮC HIỆN TRƯỜNG]:\n")
                append(payload.notesSummary)
            } else {
                append("[ĐÁNH GIÁ RỦI RO]:\n")
                if (payload.delayedNodes > 0) {
                    append("- Có ${payload.delayedNodes} nút thi công chậm tiến độ kế hoạch. Tiềm ẩn nguy cơ ảnh hưởng tiến độ chung.")
                } else {
                    append("- Tiến độ dự án ở mức an toàn. Chưa phát hiện vướng mắc hay rủi ro nghiêm trọng tại hiện trường.")
                }
            }
        }
        
        val actions = buildList {
            if (payload.delayedNodes > 0) {
                add("Đẩy nhanh tiến độ thi công tại ${payload.delayedNodes} điểm chậm tiến độ.")
                add("Tổ chức rà soát mặt bằng thi công để tháo gỡ vướng mắc phát sinh.")
                add("Yêu cầu các nhà thầu chậm tiến độ bổ sung nhân lực và nguồn lực thi công để bù tiến độ.")
            } else {
                add("Duy trì nhịp độ thi công và tiến hành công tác nghiệm thu cuốn chiếu.")
            }
            add("Kế hoạch tiếp theo: Tập trung hoàn thiện kéo cáp và đấu nối camera tại các tuyến giao lộ lớn.")
        }
        
        return ReportDraftResult(executiveSummary = exec, riskSection = risk, recommendedActions = actions)
    }

    override suspend fun operationRecommendations(payload: OpsRecommendationPayload): OpsRecommendationResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext operationRecommendationsLocal(payload)
        }
        val prompt = """
            Tạo khuyến nghị vận hành ưu tiên và trả về JSON:
            {
              "prioritized_actions": ["string"],
              "priority": 1|2|3
            }
            totalNodes=${payload.totalNodes}, delayed=${payload.delayedNodes}, completion=${payload.completionPercent}, importWarnings=${payload.importWarnings}
        """.trimIndent()
        val text = withTimeout(apiTimeoutMs) {
            generativeModel.generateContent(prompt).text
        } ?: throw IllegalStateException("empty model response")
        val json = parseObject(text)
        OpsRecommendationResult(
            prioritizedActions = json.optJSONArray("prioritized_actions").toStringList(),
            priority = json.optInt("priority", 1).coerceIn(1, 3)
        )
    }

    private fun operationRecommendationsLocal(payload: OpsRecommendationPayload): OpsRecommendationResult {
        val actions = listOf(
            "Tập trung đôn đốc xử lý dứt điểm các trạm đang bị trễ hạn.",
            "Tổ chức nghiệm thu nhanh các trạm đã hoàn thành 100% khối lượng."
        )
        val priority = if (payload.delayedNodes > 3) 3 else 1
        return OpsRecommendationResult(prioritizedActions = actions, priority = priority)
    }

    private fun parseImportMapping(raw: String): ImportMappingResult {
        val json = parseObject(raw)
        val items = json.optJSONArray("items").toStringList()
        return ImportMappingResult(
            nodeCodeColumn = json.optString("node_code", ""),
            latitudeColumn = json.optString("latitude", ""),
            longitudeColumn = json.optString("longitude", ""),
            contractorColumn = json.optString("contractor", ""),
            itemColumns = items,
            requiresManualReview = json.optBoolean("requires_manual_review", false)
        )
    }

    private fun parseObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("invalid json")
        return JSONObject(trimmed.substring(start, end + 1))
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = ArrayList<String>(length())
        for (i in 0 until length()) {
            val value = optString(i)
            if (value.isNotBlank()) list += value.trim()
        }
        return list
    }
}
