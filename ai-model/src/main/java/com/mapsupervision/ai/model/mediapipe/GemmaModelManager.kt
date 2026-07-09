package com.mapsupervision.ai.model.mediapipe

import com.mapsupervision.ai.core.GemmaDeviceSnapshot
import com.mapsupervision.ai.core.GemmaModelFamily
import com.mapsupervision.ai.core.GemmaModelInfo
import com.mapsupervision.ai.core.GemmaModelSelection
import com.mapsupervision.ai.core.GemmaModelStatus
import com.mapsupervision.ai.core.ThermalStatus
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val STANDARD_RAG_INSTRUCTION =
    "Quy tắc RAG: chỉ dùng dữ liệu trong snapshot và retrieved_context; nếu thiếu dữ liệu thì nói rõ chưa có dữ liệu phù hợp. " +
        "Với câu hỏi tiến độ, trả lời theo dạng báo cáo gồm kế hoạch, thi công thực tế, còn lại, trạng thái, " +
        "và từng dòng khối lượng thi công kèm tỷ lệ hoàn thành. Không tự bịa số liệu."

@Singleton
class GemmaModelManager @Inject constructor(
    private val storageManager: ProjectStorageManager,
    private val downloadStateStore: GemmaDownloadStateStore
) {
    companion object {
        private const val QWEN3_0_6B_BYTES = 497_664_000L
        private const val GEMMA_E2B_BYTES = 2_583_085_056L
        private const val GEMMA_E4B_BYTES = 3_654_467_584L
        private const val QWEN3_0_6B_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm?download=true"
        private const val GEMMA_E2B_REVISION = "7fa1d78473894f7e736a21d920c3aa80f950c0db"
        private const val GEMMA_E4B_REVISION = "9695417f248178c63a9f318c6e0c56cb917cb837"
        private const val GEMMA_SYSTEM_INSTRUCTION =
            "Bạn là trợ lý vận hành công trình của MapSupervision. " +
                "Chỉ trả lời dựa trên ngữ cảnh dự án được cung cấp. " +
                "Nếu thiếu dữ liệu, nói rõ thiếu gì. Trả lời ngắn gọn, thực dụng và bằng tiếng Việt.\n" +
                "Khi người dùng yêu cầu thực hiện hành động, hãy chèn một thẻ hành động tương ứng ở cuối câu trả lời của bạn:\n" +
                "1. Cập nhật tiến độ thi công:\n" +
                "[ACTION: UPDATE_CONSTRUCTION_PROGRESS nodeCode=\"mã_nút\" planned=số_kế_hoạch actual=số_thực_tế]\n" +
                "2. Thêm nhật ký thi công hàng ngày:\n" +
                "[ACTION: ADD_DAILY_LOG workItem=\"work item\" manpower=number note=\"journal content\" weather=\"weather\" temperature=number nodeCode=\"node code\" routeCode=\"route code\" date=\"dd/MM/yyyy\" volume=number unit=\"unit\" categoryName=\"category\"]\n" +
                "3. Cập nhật thông tin ảnh hiện trường:\n" +
                "[ACTION: UPDATE_SITE_PHOTO photoId=\"mã_ảnh\" tagCodesCsv=\"mã_nhãn\" matchedNodeCode=\"mã_nút\" latitude=vĩ_độ longitude=kinh_độ]\n" +
                "4. Lưu bản thảo báo cáo giám sát:\n" +
                "[ACTION: SAVE_REPORT_DRAFT projectId=\"mã_dự_án\" title=\"tiêu_đề\" executiveSummary=\"tóm_tắt_báo_cáo\" riskSection=\"rủi_ro\" recommendedActions=\"hành_động_1|hành_động_2\"]"
    }

    private val modelDir: File
        get() = File(storageManager.publicBaseDir(), "ai/gemma4").apply { mkdirs() }

    fun supportedModels(): List<GemmaModelInfo> = listOf(
        GemmaModelInfo(
            family = GemmaModelFamily.QWEN3_0_6B,
            displayName = "Qwen3 0.6B INT4",
            estimatedSizeMb = 474,
            recommendedMinAvailableRamMb = 1536,
            recommendedMinFreeStorageMb = 1200,
            downloadFileName = "qwen3_0_6b_mixed_int4.litertlm",
            expectedBytes = QWEN3_0_6B_BYTES,
            url = QWEN3_0_6B_URL
        ),
        GemmaModelInfo(
            family = GemmaModelFamily.E2B,
            displayName = "Gemma4 E2B",
            estimatedSizeMb = 2583,
            recommendedMinAvailableRamMb = 3072,
            recommendedMinFreeStorageMb = 3500,
            downloadFileName = "gemma-4-E2B-it.litertlm",
            expectedBytes = GEMMA_E2B_BYTES,
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$GEMMA_E2B_REVISION/gemma-4-E2B-it.litertlm?download=true"
        ),
        GemmaModelInfo(
            family = GemmaModelFamily.E4B,
            displayName = "Gemma4 E4B",
            estimatedSizeMb = 3654,
            recommendedMinAvailableRamMb = 6144,
            recommendedMinFreeStorageMb = 6500,
            downloadFileName = "gemma-4-E4B-it.litertlm",
            expectedBytes = GEMMA_E4B_BYTES,
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/$GEMMA_E4B_REVISION/gemma-4-E4B-it.litertlm?download=true"
        )
    )

    fun selectModel(snapshot: GemmaDeviceSnapshot): GemmaModelSelection {
        val candidates = supportedModels()
        if (snapshot.thermalStatus == ThermalStatus.CRITICAL || snapshot.batteryLevel < 10) {
            return GemmaModelSelection(false, null, "device_not_ready", candidates)
        }
        if (snapshot.freeStorageMb < candidates.minOf { it.recommendedMinFreeStorageMb }) {
            return GemmaModelSelection(false, null, "insufficient_storage", candidates)
        }
        val selected = candidates.lastOrNull { model ->
            snapshot.availableRamMb >= model.recommendedMinAvailableRamMb &&
                snapshot.freeStorageMb >= model.recommendedMinFreeStorageMb
        } ?: candidates.first()
        val reason = when {
            snapshot.availableRamMb < selected.recommendedMinAvailableRamMb ->
                "selected_${selected.family.name.lowercase()}_low_ram"
            else -> "selected_${selected.family.name.lowercase()}"
        }
        return GemmaModelSelection(true, selected, reason, candidates)
    }

    fun modelFile(model: GemmaModelInfo): File = File(modelDir, model.downloadFileName)

    fun status(model: GemmaModelInfo): GemmaModelStatus {
        val file = modelFile(model)
        return when {
            file.exists() && file.length() == model.expectedBytes -> GemmaModelStatus.READY
            file.exists() && file.length() > 0L -> GemmaModelStatus.LOAD_FAILED
            else -> GemmaModelStatus.NOT_DOWNLOADED
        }
    }

    fun delete(model: GemmaModelInfo): Boolean {
        val target = modelFile(model)
        val partial = File("${target.absolutePath}.download")
        val targetDeleted = !target.exists() || target.delete()
        val partialDeleted = !partial.exists() || partial.delete()
        return targetDeleted && partialDeleted
    }

    fun downloadUrlFor(model: GemmaModelInfo): String? = model.url

    fun observeDownloadState() = downloadStateStore.state

    fun currentDownloadState(): GemmaDownloadState = downloadStateStore.currentState()

    fun clearDownloadState() = downloadStateStore.clear()

    fun updateDownloadState(state: GemmaDownloadState) = downloadStateStore.update(state)

    fun isModelDownloadComplete(model: GemmaModelInfo): Boolean {
        val state = currentDownloadState()
        return status(model) == GemmaModelStatus.READY &&
            state is GemmaDownloadState.Completed &&
            state.modelId == model.downloadFileName
    }

    fun canInitializeLiteRt(model: GemmaModelInfo): Boolean {
        val file = modelFile(model)
        return file.exists() && file.length() == model.expectedBytes
    }

    fun expectedBytes(model: GemmaModelInfo): Long = model.expectedBytes

    fun systemInstruction(): String = GEMMA_SYSTEM_INSTRUCTION + "\n" + STANDARD_RAG_INSTRUCTION
}
