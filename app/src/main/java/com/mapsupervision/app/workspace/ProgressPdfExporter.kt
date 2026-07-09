package com.mapsupervision.app.workspace

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProgressPdfExporter {

    private fun nodeDisplayName(code: String, nodesMap: Map<String, GisNode>): String {
        val node = nodesMap[code] ?: return "Trạm $code"
        return if (node.mapNumberLabel.isNotBlank()) "Trạm ${node.mapNumberLabel} ($code)" else "Trạm $code"
    }

    suspend fun export(
        projectId: String,
        progress: List<NodeProgress>,
        nodes: List<GisNode>,
        photos: List<SitePhoto> = emptyList()
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val nodesMap = nodes.associateBy { it.code }
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val displayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val fileName = "tiendo_${projectId}_${dateStr}.pdf"

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)

            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f
            var yPos = margin + 20f

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A") // Slate 900
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#475569") // Slate 600
                textSize = 10f
                isAntiAlias = true
            }
            val headerPaint = Paint().apply {
                color = Color.parseColor("#1E293B") // Slate 800
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#334155") // Slate 700
                textSize = 10f
                isAntiAlias = true
            }
            val delayedPaint = Paint().apply {
                color = Color.parseColor("#EF4444") // Premium Red 500
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val onTrackPaint = Paint().apply {
                color = Color.parseColor("#10B981") // Premium Emerald 500
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0") // Slate 200
                strokeWidth = 1f
                isAntiAlias = true
            }

            var currentPage = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            )
            var canvas: Canvas = currentPage.canvas

            fun finishAndNewPage() {
                document.finishPage(currentPage)
                val pageNum = document.pages.size + 1
                currentPage = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                )
                canvas = currentPage.canvas
                yPos = margin + 20f
            }

            fun drawDivider() {
                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, dividerPaint)
                yPos += 12f
            }

            // Draw Header Logo Icon
            val logoPaint = Paint().apply {
                color = Color.parseColor("#4F46E5") // Indigo 600
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val logoRect = android.graphics.RectF(margin, yPos - 12f, margin + 20f, yPos + 8f)
            canvas.drawRoundRect(logoRect, 4f, 4f, logoPaint)

            val logoInnerPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(margin + 10f, yPos - 2f, 3f, logoInnerPaint)

            val brandPaint = Paint().apply {
                color = Color.parseColor("#4F46E5") // Indigo 600
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("MAPSUPERVISION REPORTING", margin + 28f, yPos + 3f, brandPaint)
            yPos += 30f

            // Document Title
            canvas.drawText("BÁO CÁO TIẾN ĐỘ THI CÔNG", margin, yPos, titlePaint)
            yPos += 24f
            canvas.drawText("Dự án: $projectId", margin, yPos, subtitlePaint)
            yPos += 16f
            canvas.drawText("Ngày xuất: $displayDate", margin, yPos, subtitlePaint)
            yPos += 20f
            drawDivider()

            // Summary
            canvas.drawText("TÓM TẮT TIẾN ĐỘ", margin, yPos, headerPaint)
            yPos += 20f

            if (progress.isEmpty()) {
                canvas.drawText("Chưa có dữ liệu tiến độ", margin, yPos, bodyPaint)
                yPos += 18f
            } else {
                val avgPlanned = progress.map { it.planned }.average().toFloat()
                val avgActual = progress.map { it.actual }.average().toFloat()
                val delayedCount = progress.count { it.delayed }
                canvas.drawText("Kế hoạch trung bình: ${String.format("%.1f", avgPlanned)}%", margin, yPos, bodyPaint)
                yPos += 16f
                canvas.drawText("Thực tế trung bình: ${String.format("%.1f", avgActual)}%", margin, yPos, bodyPaint)
                yPos += 16f
                canvas.drawText("Số hạng mục chậm tiến độ: $delayedCount", margin, yPos, bodyPaint)
                yPos += 16f
            }
            yPos += 8f
            drawDivider()

            // Item list
            canvas.drawText("DANH SÁCH HẠNG MỤC CHI TIẾT", margin, yPos, headerPaint)
            yPos += 20f

            if (progress.isEmpty()) {
                canvas.drawText("Chưa có dữ liệu tiến độ", margin, yPos, bodyPaint)
                yPos += 18f
            } else {
                for (item in progress) {
                    if (yPos > pageHeight - margin - 60f) {
                        finishAndNewPage()
                        canvas.drawText("DANH SÁCH HẠNG MỤC (tiếp theo)", margin, yPos, headerPaint)
                        yPos += 20f
                    }

                    val name = nodeDisplayName(item.nodeCode, nodesMap)
                    val statusPaint = if (item.delayed) delayedPaint else onTrackPaint
                    val statusText = if (item.delayed) "Chậm trễ" else "Đúng tiến độ"

                    canvas.drawText(name, margin, yPos, headerPaint)
                    yPos += 16f
                    canvas.drawText(
                        "Mã: ${item.nodeCode}  |  KH: ${String.format("%.1f", item.planned)}%  |  TT: ${String.format("%.1f", item.actual)}%",
                        margin + 8f, yPos, bodyPaint
                    )
                    canvas.drawText(statusText, pageWidth - margin - 80f, yPos, statusPaint)
                    yPos += 20f
                    canvas.drawLine(margin + 8f, yPos, pageWidth - margin, yPos, dividerPaint)
                    yPos += 10f
                }
            }

            // Site Photos Section (2-Column Grid)
            val activePhotos = photos.filter { File(it.filePath).exists() }
            if (activePhotos.isNotEmpty()) {
                yPos += 10f
                if (yPos > pageHeight - margin - 120f) {
                    finishAndNewPage()
                }
                canvas.drawText("HÌNH ẢNH HIỆN TRƯỜNG THỰC ĐỊA", margin, yPos, headerPaint)
                yPos += 20f

                val imgWidth = 230f
                val imgHeight = 150f
                val spacing = 20f

                var isLeft = true
                for (photo in activePhotos) {
                    val photoFile = File(photo.filePath)

                    if (yPos > pageHeight - margin - imgHeight - 30f) {
                        finishAndNewPage()
                        canvas.drawText("HÌNH ẢNH HIỆN TRƯỜNG THỰC ĐỊA (tiếp theo)", margin, yPos, headerPaint)
                        yPos += 20f
                    }

                    val x = if (isLeft) margin else margin + imgWidth + spacing
                    val rect = android.graphics.RectF(x, yPos, x + imgWidth, yPos + imgHeight)

                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                        if (bitmap != null) {
                            canvas.drawBitmap(bitmap, null, rect, null)
                            val labelY = yPos + imgHeight + 12f
                            val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(photo.capturedAtEpochMs))
                            val labelText = "${photo.objectCode}: $timeStr (${photo.engineer})"
                            canvas.drawText(labelText, x, labelY, bodyPaint)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (!isLeft) {
                        yPos += imgHeight + 25f
                    }
                    isLeft = !isLeft
                }
                if (!isLeft) {
                    yPos += imgHeight + 25f
                }
            }

            document.finishPage(currentPage)
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()

            file
        }
    }
}
