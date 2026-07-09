package com.mapsupervision.ai.core.rag

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.WorkspaceSnapshot
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

object RagChatAnswerFormatter {
    fun format(
        query: String,
        snapshot: WorkspaceSnapshot?,
        domain: RagQueryDomain,
        selectedNodeCode: String? = null,
        selectedRouteCode: String? = null
    ): String? {
        val safeSnapshot = snapshot ?: return null
        return when (domain) {
            RagQueryDomain.PROGRESS -> formatProgress(query, safeSnapshot, selectedNodeCode)
            RagQueryDomain.PLANNING -> formatPlans(query, safeSnapshot, selectedNodeCode, selectedRouteCode)
            RagQueryDomain.DAILY_LOG -> formatDailyLogs(query, safeSnapshot, selectedNodeCode, selectedRouteCode)
            RagQueryDomain.GENERAL -> null
        }
    }

    private fun formatProgress(
        query: String,
        snapshot: WorkspaceSnapshot,
        selectedNodeCode: String?
    ): String {
        val nodeCode = resolveNodeCode(query, snapshot, selectedNodeCode)
        val progressRows = if (nodeCode.isNullOrBlank()) {
            snapshot.constructionProgress
        } else {
            snapshot.constructionProgress.filter { it.nodeCode.equals(nodeCode, ignoreCase = true) }
        }
        val volumeRows = if (nodeCode.isNullOrBlank()) {
            snapshot.workVolumeRows
        } else {
            snapshot.workVolumeRows.filter { it.nodeCode.equals(nodeCode, ignoreCase = true) }
        }

        if (progressRows.isEmpty() && volumeRows.isEmpty()) {
            return "Chưa có dữ liệu phù hợp về tiến độ thi công trong dự án."
        }

        if (!nodeCode.isNullOrBlank()) {
            val progress = progressRows.firstOrNull()
            val node = snapshot.designNodes.firstOrNull { it.code.equals(nodeCode, ignoreCase = true) }
            return buildString {
                append("Báo cáo tiến độ ")
                append(node?.mapNumberLabel?.takeIf { it.isNotBlank() } ?: nodeCode)
                append(": ")
                if (progress != null) {
                    val status = if (progress.delayed || progress.actual < progress.planned) "chậm so với kế hoạch" else "đúng hoặc vượt kế hoạch"
                    append("kế hoạch đạt ").append(percent(progress.planned))
                    append(", thi công thực tế đạt ").append(percent(progress.actual))
                    append(", còn lại ").append(percent(progress.remain))
                    append(". Trạng thái hiện tại: ").append(status).append(".")
                } else {
                    append("chưa có dòng tiến độ tổng hợp theo phần trăm.")
                }
                if (node?.contractor?.isNotBlank() == true) {
                    append(" Nhà thầu phụ trách: ").append(node.contractor).append(".")
                }
                append("\n")
                append(formatVolumeLines(node, volumeRows))
            }.trim()
        }

        val total = progressRows.size
        val delayed = progressRows.count { it.delayed || it.actual < it.planned }
        val avgActual = if (progressRows.isEmpty()) 0f else progressRows.map { it.actual }.average().toFloat()
        return buildString {
            append("Báo cáo tiến độ toàn dự án: có ").append(total).append(" node có dữ liệu tiến độ, ")
            append(delayed).append(" node đang chậm so với kế hoạch. ")
            append("Tỷ lệ thi công thực tế trung bình đạt ").append(percent(avgActual)).append(".")
            if (volumeRows.isNotEmpty()) {
                append("\n")
                append(formatVolumeLines(null, volumeRows))
            }
        }.trim()
    }

    private fun formatVolumeLines(
        node: com.mapsupervision.domain.model.GisNode?,
        volumeRows: List<WorkVolumeProgress>
    ): String {
        if (volumeRows.isEmpty()) {
            return "Khối lượng thi công: chưa có dữ liệu khối lượng phù hợp."
        }
        val plannedTotal = volumeRows.sumOf { it.plannedQty.toDouble() }
        val actualTotal = volumeRows.sumOf { it.actualQty.toDouble() }
        return buildString {
            val summaryLabel = node?.workVolumeSummary?.takeIf { it.isNotBlank() } ?: "tổng các hạng mục"
            append("Khối lượng tổng hợp (").append(summaryLabel).append("): ")
            append(number(actualTotal)).append(" / ").append(number(plannedTotal))
            append(" - hoàn thành ").append(ratio(actualTotal, plannedTotal)).append(".")
            volumeRows.sortedWith(compareBy({ it.nodeCode }, { it.workName })).forEach { row ->
                append("\nKhối lượng thi công - ").append(row.workName)
                if (node == null) append(" [").append(row.nodeCode).append("]")
                append(": ")
                append(number(row.actualQty.toDouble())).append(" / ").append(number(row.plannedQty.toDouble()))
                if (row.unit.isNotBlank()) append(" ").append(row.unit)
                append(" - hoàn thành ").append(ratio(row.actualQty.toDouble(), row.plannedQty.toDouble())).append(".")
            }
        }
    }

    private fun formatPlans(
        query: String,
        snapshot: WorkspaceSnapshot,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ): String {
        val nodeCode = resolveNodeCode(query, snapshot, selectedNodeCode)
        val routeCode = resolveRouteCode(query, snapshot, selectedRouteCode)
        val day = resolveDay(query)
        val plans = snapshot.workPlans
            .filter { plan -> nodeCode.isNullOrBlank() || plan.nodeCode.equals(nodeCode, ignoreCase = true) }
            .filter { plan -> routeCode.isNullOrBlank() || plan.routeCode.equals(routeCode, ignoreCase = true) }
            .filter { plan -> day == null || plan.plannedDateEpochDay == day }
            .sortedBy { it.plannedDateEpochDay }
            .take(8)

        if (plans.isEmpty()) {
            return "Chưa có dữ liệu phù hợp về kế hoạch thi công."
        }

        return buildString {
            append("Kế hoạch thi công phù hợp gồm ").append(plans.size).append(" mục:")
            plans.forEach { plan ->
                append("\n- ").append(formatPlanDate(plan)).append(": ").append(plan.title)
                plan.nodeCode?.takeIf { it.isNotBlank() }?.let { append(" tại node ").append(it) }
                plan.routeCode?.takeIf { it.isNotBlank() }?.let { append(" trên tuyến ").append(it) }
                if (plan.quantity > 0.0) {
                    append(", khối lượng ").append(number(plan.quantity))
                    if (plan.unit.isNotBlank()) append(" ").append(plan.unit)
                }
                plan.description.takeIf { it.isNotBlank() && it != plan.title }?.let {
                    append(". Ghi chú: ").append(it)
                }
            }
        }
    }

    private fun formatDailyLogs(
        query: String,
        snapshot: WorkspaceSnapshot,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ): String {
        val nodeCode = resolveNodeCode(query, snapshot, selectedNodeCode)
        val routeCode = resolveRouteCode(query, snapshot, selectedRouteCode)
        val day = resolveDay(query)
        val logs = snapshot.dailyLogs
            .filter { log -> nodeCode.isNullOrBlank() || log.nodeCode.equals(nodeCode, ignoreCase = true) }
            .filter { log -> routeCode.isNullOrBlank() || log.routeCode.equals(routeCode, ignoreCase = true) }
            .filter { log -> day == null || resolveLogDay(log) == day }
            .sortedByDescending { it.createdAtEpochMs }
            .take(8)

        if (logs.isEmpty()) {
            return "Chưa có dữ liệu phù hợp về nhật ký thi công."
        }

        return buildString {
            append("Nhật ký thi công phù hợp gồm ").append(logs.size).append(" ghi nhận:")
            logs.forEach { log ->
                append("\n- ").append(formatEpochDay(resolveLogDay(log))).append(": ").append(log.workItem)
                log.nodeCode?.takeIf { it.isNotBlank() }?.let { append(" tại node ").append(it) }
                log.routeCode?.takeIf { it.isNotBlank() }?.let { append(" trên tuyến ").append(it) }
                append(", nhân lực ").append(log.manpower)
                if (log.volume > 0.0) {
                    append(", khối lượng ").append(number(log.volume))
                    if (log.unit.isNotBlank()) append(" ").append(log.unit)
                }
                log.note.takeIf { it.isNotBlank() }?.let { append(". Ghi chú: ").append(it) }
            }
        }
    }

    private fun resolveNodeCode(query: String, snapshot: WorkspaceSnapshot, selectedNodeCode: String?): String? {
        selectedNodeCode?.takeIf { it.isNotBlank() }?.let { return it }
        val normalizedQuery = normalize(query)
        return snapshot.designNodes.firstOrNull { node ->
            normalizedQuery.contains(normalize(node.code)) ||
                node.mapNumberLabel.isNotBlank() && normalizedQuery.contains(normalize(node.mapNumberLabel))
        }?.code
    }

    private fun resolveRouteCode(query: String, snapshot: WorkspaceSnapshot, selectedRouteCode: String?): String? {
        selectedRouteCode?.takeIf { it.isNotBlank() }?.let { return it }
        val normalizedQuery = normalize(query)
        return snapshot.designRoutes.firstOrNull { route ->
            normalizedQuery.contains(normalize(route.code))
        }?.code
    }

    private fun resolveDay(query: String): Long? {
        val normalized = normalize(query)
        val today = LocalDate.now()
        return when {
            normalized.contains("hom nay") -> today.toEpochDay()
            normalized.contains("hom qua") -> today.minusDays(1).toEpochDay()
            normalized.contains("ngay mai") -> today.plusDays(1).toEpochDay()
            else -> null
        }
    }

    private fun resolveLogDay(log: DailyLog): Long {
        if (log.dateEpochDay != 0L) return log.dateEpochDay
        return java.time.Instant.ofEpochMilli(log.createdAtEpochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
    }

    private fun formatPlanDate(plan: WorkPlan): String = formatEpochDay(plan.plannedDateEpochDay)

    private fun formatEpochDay(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).toString()
    }

    private fun ratio(actual: Double, planned: Double): String {
        if (planned <= 0.0) return "chưa xác định"
        return percent((actual / planned * 100.0).toFloat())
    }

    private fun percent(value: Float): String {
        return "${String.format(Locale.US, "%.1f", value)}%"
    }

    private fun number(value: Double): String {
        return if (value == value.roundToInt().toDouble()) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private fun normalize(value: String): String {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.US)
    }
}
