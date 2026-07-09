package com.mapsupervision.reporting.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Environment
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.reporting.ui.MaterialReportRow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportGenerator @Inject constructor() {
    fun exportProjectSummary(
        context: Context,
        projectId: String,
        summaryLines: List<String>,
        workVolumeRows: List<MaterialReportRow>,
        photos: List<SitePhoto>,
        dailyLogLines: List<String> = emptyList()
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { 
            textSize = 20f
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            textSize = 12f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply { textSize = 11f }
        val footerPaint = Paint().apply { 
            textSize = 9f
            color = android.graphics.Color.GRAY
        }

        fun drawFooter() {
            canvas.drawText("Trang $pageNumber", (pageWidth - 60).toFloat(), (pageHeight - 30).toFloat(), footerPaint)
        }

        canvas.drawText("Báo cáo MapSupervision - $projectId", 40f, 60f, titlePaint)
        var y = 100f

        // Write summary lines with automatic pagination
        summaryLines.forEach { line ->
            if (y > pageHeight - 60f) {
                drawFooter()
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 60f
            }
            canvas.drawText(line, 40f, y, bodyPaint)
            y += 22f
        }

        // Write materials section
        if (y > pageHeight - 120f) {
            drawFooter()
            doc.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = 60f
        }

        y += 14f
        canvas.drawText("Bảng tổng hợp khối lượng thi công", 40f, y, headerPaint)
        y += 22f
        canvas.drawText("Nội dung", 40f, y, headerPaint)
        canvas.drawText("Tổng thiết kế", 280f, y, headerPaint)
        canvas.drawText("Tổng thi công", 390f, y, headerPaint)
        canvas.drawText("%", 510f, y, headerPaint)
        y += 18f

        workVolumeRows.forEach { row ->
            if (y > pageHeight - 50f) {
                drawFooter()
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 60f
                
                // Redraw table headers on new page
                canvas.drawText("Nội dung", 40f, y, headerPaint)
                canvas.drawText("Tổng thiết kế", 280f, y, headerPaint)
                canvas.drawText("Tổng thi công", 390f, y, headerPaint)
                canvas.drawText("%", 510f, y, headerPaint)
                y += 18f
            }
            val rowPaint = if (row.isTotal) headerPaint else bodyPaint
            canvas.drawText(row.workName, 40f, y, rowPaint)
            canvas.drawText(row.totalPlannedQty.toInt().toString(), 300f, y, rowPaint)
            canvas.drawText(row.totalActualQty.toInt().toString(), 410f, y, rowPaint)
            canvas.drawText("${row.completionPercent.toInt()}%", 510f, y, rowPaint)
            y += 18f
        }

        if (dailyLogLines.isNotEmpty()) {
            if (y > pageHeight - 120f) {
                drawFooter()
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 60f
            }
            y += 14f
            canvas.drawText("Tổng hợp nhật ký thi công", 40f, y, headerPaint)
            y += 18f
            dailyLogLines.forEach { line ->
                if (y > pageHeight - 60f) {
                    drawFooter()
                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = 60f
                }
                canvas.drawText(line, 40f, y, bodyPaint)
                y += 18f
            }
        }

        // Draw Construction Photo Log section
        if (photos.isNotEmpty()) {
            drawFooter()
            doc.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = 60f

            canvas.drawText("Nhật ký hình ảnh thực địa", 40f, y, headerPaint)
            y += 30f

            val cellWidth = 230f
            val cellHeight = 160f
            val col1X = 40f
            val col2X = 310f

            val chunked = photos.chunked(2)
            chunked.forEach { rowPhotos ->
                if (y > pageHeight - 220f) {
                    drawFooter()
                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = 60f
                }

                rowPhotos.forEachIndexed { colIndex, photo ->
                    val startX = if (colIndex == 0) col1X else col2X
                    val imgFile = File(photo.thumbnailPath.ifBlank { photo.filePath })
                    
                    if (imgFile.exists()) {
                        runCatching {
                            val rawBitmap = decodeBitmapForCell(
                                imgFile,
                                reqWidth = cellWidth.toInt(),
                                reqHeight = (cellHeight - 35f).toInt()
                            )
                            if (rawBitmap != null) {
                                val rectDst = RectF(startX, y, startX + cellWidth, y + cellHeight - 35f)
                                canvas.drawBitmap(rawBitmap, null, rectDst, null)
                                rawBitmap.recycle()
                            }
                        }
                    }

                    // Photo captions/labels
                    val textY = y + cellHeight - 20f
                    val label1 = "📍 ${photo.objectCode}"
                    val latStr = photo.latitude?.let { "%.4f".format(it) } ?: "N/A"
                    val lngStr = photo.longitude?.let { "%.4f".format(it) } ?: "N/A"
                    val label2 = "Toạ độ: $latStr, $lngStr"
                    val label3 = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(photo.matchedAtEpochMs.takeIf { it > 0L } ?: photo.capturedAtEpochMs))
                    val label4 = if (photo.matchingTimeOffsetMs != 0L) "Offset: ${photo.matchingTimeOffsetMs / 60000}m" else ""

                    val detailPaint = Paint().apply { textSize = 9f }
                    canvas.drawText(label1, startX, textY, headerPaint.apply { textSize = 9f })
                    canvas.drawText(label2, startX, textY + 12f, detailPaint)
                    canvas.drawText(label3, startX, textY + 24f, detailPaint)
                    if (label4.isNotBlank()) canvas.drawText(label4, startX, textY + 36f, detailPaint)
                }

                y += cellHeight + 15f
            }
        }

        drawFooter()
        doc.finishPage(page)

        val outDir = publicReportsDir()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(outDir, "${projectId}_$ts.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        // Notify MediaScanner
        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)

        return outFile
    }

    private fun publicReportsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "MapSupervision/Reports")
        dir.mkdirs()
        return dir
    }

    private fun decodeBitmapForCell(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var halfHeight = srcHeight / 2
        var halfWidth = srcWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

