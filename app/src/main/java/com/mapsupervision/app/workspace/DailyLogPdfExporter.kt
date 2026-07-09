package com.mapsupervision.app.workspace

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.DailyLogLineType
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.resolveEpochDay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object DailyLogPdfExporter {
    suspend fun export(
        projectId: String,
        scopeLabel: String,
        dailyLogs: List<DailyLog>,
        workPlans: List<WorkPlan>,
        progress: List<NodeProgress>,
        nodes: List<GisNode>,
        photos: List<SitePhoto>,
        routeLabels: Map<String, String>,
        includePlan: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MapSupervision/DailyLogs"
            ).apply { mkdirs() }
            val scopeSlug = scopeLabel
                .replace(Regex("[^a-zA-Z0-9]+"), "_")
                .trim('_')
                .ifBlank { "all" }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(exportDir, "nhatky_${projectId}_${scopeSlug}_$timestamp.pdf")

            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = margin

            val titlePaint = textPaint("#0F172A", 18f, true)
            val headingPaint = textPaint("#111827", 13f, true)
            val bodyPaint = textPaint("#334155", 10f, false)
            val mutedPaint = textPaint("#64748B", 9f, false)
            val accentPaint = textPaint("#B45309", 10f, true)
            val successPaint = textPaint("#059669", 10f, true)
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
                isAntiAlias = true
            }

            val nodesByCode = nodes.associateBy { it.code }
            val progressByNode = progress.associateBy { it.nodeCode }
            val logsByDay = dailyLogs.groupBy { it.resolveEpochDay() }
            val plansByDay = workPlans.groupBy { it.plannedDateEpochDay }
            val photosByDay = photos.groupBy { it.captureEpochDay() }
            val allDays = buildSet {
                addAll(logsByDay.keys)
                addAll(photosByDay.keys)
                if (includePlan) addAll(plansByDay.keys)
            }
                .filter { it > 0L }
                .sorted()

            fun newPage() {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }

            fun ensureSpace(height: Float) {
                if (y + height > pageHeight - margin) newPage()
            }

            fun drawLine(
                text: String,
                paint: Paint,
                indent: Float = 0f,
                lineHeight: Float = 15f
            ) {
                ensureSpace(lineHeight)
                canvas.drawText(text.take(150), margin + indent, y, paint)
                y += lineHeight
            }

            fun drawDivider() {
                ensureSpace(10f)
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 10f
            }

            fun locationLabel(nodeCode: String?, routeCode: String?): String {
                return when {
                    !nodeCode.isNullOrBlank() -> {
                        val node = nodesByCode[nodeCode]
                        val name = node?.mapNumberLabel?.takeIf { it.isNotBlank() } ?: nodeCode
                        "$name ($nodeCode)"
                    }
                    !routeCode.isNullOrBlank() -> routeLabels[routeCode] ?: routeCode
                    else -> "Khong lien ket"
                }
            }

            fun drawLogLines(log: DailyLog) {
                if (log.plannedWorkName.isNotBlank()) {
                    drawLine("Ke hoach goc: ${log.plannedWorkName}", bodyPaint, indent = 24f)
                    if (log.plannedQuantity > 0.0 || log.plannedUnit.isNotBlank()) {
                        drawLine(
                            "Khoi luong KH: ${formatQty(log.plannedQuantity)} ${log.plannedUnit}".trim(),
                            mutedPaint,
                            indent = 32f
                        )
                    }
                    drawLine(
                        "Vi tri KH: ${locationLabel(log.plannedNodeCode, log.plannedRouteCode)}",
                        mutedPaint,
                        indent = 32f
                    )
                }

                val actualLines = if (log.lines.isNotEmpty()) {
                    log.lines.map { line ->
                        ExportDailyLogLine(
                            label = if (line.lineType == DailyLogLineType.PLAN_PRIMARY) "Theo ke hoach" else "Phat sinh",
                            workName = line.workName,
                            categoryName = line.categoryName,
                            quantity = line.quantity,
                            unit = line.unit,
                            nodeCode = line.nodeCode,
                            routeCode = line.routeCode
                        )
                    }
                } else if (log.workItem.isBlank() && log.volume == 0.0 && log.unit.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        ExportDailyLogLine(
                            label = "Thuc hien",
                            workName = log.workItem,
                            categoryName = log.categoryName,
                            quantity = log.volume,
                            unit = log.unit,
                            nodeCode = log.nodeCode,
                            routeCode = log.routeCode
                        )
                    )
                }

                if (actualLines.isNotEmpty()) {
                    drawLine("Khoi luong thuc hien", headingPaint, indent = 24f, lineHeight = 14f)
                    actualLines.forEach { line ->
                        val workLabel = buildString {
                            append("- ${line.label}: ${line.workName}")
                            if (line.categoryName.isNotBlank()) append(" [${line.categoryName}]")
                        }
                        drawLine(workLabel, bodyPaint, indent = 32f)
                        if (line.quantity > 0.0 || line.unit.isNotBlank()) {
                            drawLine(
                                "Luy ke: ${formatQty(line.quantity)} ${line.unit}".trim(),
                                mutedPaint,
                                indent = 40f
                            )
                        }
                        drawLine(
                            "Vi tri: ${locationLabel(line.nodeCode, line.routeCode)}",
                            mutedPaint,
                            indent = 40f
                        )
                    }
                }
            }

            drawLine("NHAT KY THI CONG", titlePaint, lineHeight = 24f)
            drawLine("Du an: $projectId", bodyPaint)
            drawLine("Pham vi: $scopeLabel", bodyPaint)
            drawLine(
                "Xuat luc: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}",
                mutedPaint
            )
            drawDivider()

            if (allDays.isEmpty()) {
                drawLine("Khong co du lieu nhat ky trong pham vi da chon.", bodyPaint)
            } else {
                allDays.forEach { epochDay ->
                    val dateLabel = LocalDate.ofEpochDay(epochDay)
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    val dayLogs = logsByDay[epochDay].orEmpty().sortedBy { it.createdAtEpochMs }
                    val dayPlans = plansByDay[epochDay].orEmpty()
                    val dayPhotos = photosByDay[epochDay].orEmpty()
                        .filter { File(it.filePath).exists() }

                    drawLine("NGAY $dateLabel", headingPaint, lineHeight = 18f)
                    val weatherLabel = dayLogs.firstOrNull { it.weather.isNotBlank() }?.let { log ->
                        buildString {
                            append(log.weather)
                            if (log.temperature != 0.0) append(" - ${log.temperature.roundToInt()}°C")
                        }
                    } ?: "Chua cap nhat"
                    drawLine("Thoi tiet: $weatherLabel", accentPaint)

                    if (includePlan) {
                        drawLine("Ke hoach", headingPaint, lineHeight = 14f)
                        if (dayPlans.isEmpty()) {
                            drawLine("Chua co ke hoach trong ngay.", mutedPaint, indent = 8f)
                        } else {
                            dayPlans.forEach { plan ->
                                drawLine(plan.title, bodyPaint, indent = 8f)
                                drawLine(
                                    "Khoi luong KH: ${formatQty(plan.quantity)} ${plan.unit}".trim(),
                                    successPaint,
                                    indent = 16f
                                )
                                drawLine(
                                    "Vi tri/tuyen: ${locationLabel(plan.nodeCode, plan.routeCode)}",
                                    mutedPaint,
                                    indent = 16f
                                )
                                if (plan.description.isNotBlank()) {
                                    drawLine("Ghi chu: ${plan.description}", mutedPaint, indent = 16f)
                                }
                            }
                        }
                    }

                    drawLine("Nhat ky thi cong", headingPaint, lineHeight = 14f)
                    if (dayLogs.isEmpty()) {
                        drawLine("Chua co nhat ky trong ngay.", mutedPaint, indent = 8f)
                    } else {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        dayLogs.forEach { log ->
                            drawLine(
                                "Thoi gian: ${timeFormat.format(Date(log.createdAtEpochMs))}",
                                accentPaint,
                                indent = 8f
                            )
                            drawLine("Nhan cong: ${log.manpower}", mutedPaint, indent = 16f)
                            drawLogLines(log)
                            log.nodeCode?.let { nodeCode ->
                                progressByNode[nodeCode]?.let { nodeProgress ->
                                    drawLine(
                                        "Tien do node: KH ${formatPercent(nodeProgress.planned)} / TT ${formatPercent(nodeProgress.actual)}",
                                        mutedPaint,
                                        indent = 24f
                                    )
                                }
                            }
                            if (log.note.isNotBlank()) {
                                drawLine("Ghi chu: ${log.note}", mutedPaint, indent = 24f)
                            }
                            y += 4f
                        }
                    }

                    val relatedNodeCodes = buildSet {
                        addAll(dayLogs.mapNotNull { it.nodeCode })
                        addAll(dayPlans.mapNotNull { it.nodeCode })
                    }
                    if (relatedNodeCodes.isNotEmpty()) {
                        drawLine("Tien do lien quan", headingPaint, lineHeight = 14f)
                        relatedNodeCodes.forEach { nodeCode ->
                            progressByNode[nodeCode]?.let { nodeProgress ->
                                val nodeName = nodesByCode[nodeCode]?.mapNumberLabel?.takeIf { it.isNotBlank() } ?: nodeCode
                                drawLine(
                                    "$nodeName: KH ${formatPercent(nodeProgress.planned)} / TT ${formatPercent(nodeProgress.actual)}",
                                    mutedPaint,
                                    indent = 8f
                                )
                            }
                        }
                    }

                    if (dayPhotos.isNotEmpty()) {
                        drawLine("Anh nhat ky", headingPaint, lineHeight = 14f)
                        var leftColumn = true
                        val imageWidth = 230f
                        val imageHeight = 138f
                        val gap = 18f
                        dayPhotos.forEach { photo ->
                            if (y > pageHeight - margin - imageHeight - 30f) newPage()
                            val x = if (leftColumn) margin else margin + imageWidth + gap
                            val rect = RectF(x, y, x + imageWidth, y + imageHeight)
                            BitmapFactory.decodeFile(photo.filePath)?.let { bitmap ->
                                canvas.drawBitmap(bitmap, null, rect, null)
                                bitmap.recycle()
                            }
                            val captionY = y + imageHeight + 12f
                            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(photo.capturedAtEpochMs))
                            canvas.drawText(
                                "${photo.objectCode} - $timeText",
                                x,
                                captionY,
                                mutedPaint
                            )
                            if (!leftColumn) {
                                y += imageHeight + 28f
                            }
                            leftColumn = !leftColumn
                        }
                        if (!leftColumn) {
                            y += imageHeight + 28f
                        }
                    }

                    y += 4f
                    drawDivider()
                }
            }

            document.finishPage(page)
            FileOutputStream(outFile).use { document.writeTo(it) }
            document.close()
            outFile
        }
    }

    private fun textPaint(colorHex: String, sizeSp: Float, bold: Boolean): Paint {
        return Paint().apply {
            color = Color.parseColor(colorHex)
            textSize = sizeSp
            isFakeBoldText = bold
            isAntiAlias = true
        }
    }

    private fun formatQty(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f%%", value)
}

private data class ExportDailyLogLine(
    val label: String,
    val workName: String,
    val categoryName: String,
    val quantity: Double,
    val unit: String,
    val nodeCode: String?,
    val routeCode: String?
)

private fun SitePhoto.captureEpochDay(): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = capturedAtEpochMs }
    return LocalDate.of(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).toEpochDay()
}
