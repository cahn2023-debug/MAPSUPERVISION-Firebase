package com.mapsupervision.reporting.docx

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.reporting.ui.MaterialReportRow
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocxReportGenerator @Inject constructor() {

    fun exportProjectSummary(
        context: Context,
        projectId: String,
        summaryLines: List<String>,
        workVolumeRows: List<MaterialReportRow>,
        photos: List<SitePhoto>,
        dailyLogs: List<DailyLog>
    ): File {
        val outDir = publicReportsDir()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(outDir, "${projectId}_$ts.docx")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            // 1. Write [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(buildContentTypesXml(photos.size).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Write _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(buildRootRelsXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Write word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(buildDocumentRelsXml(photos.size).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Copy image files and add to Zip as word/media/image_i.jpg
            photos.forEachIndexed { index, photo ->
                val imgFile = File(photo.filePath)
                if (imgFile.exists()) {
                    zos.putNextEntry(ZipEntry("word/media/image_${index + 1}.jpg"))
                    val embeddedBytes = downsamplePhotoBytes(imgFile)
                    if (embeddedBytes != null) {
                        zos.write(embeddedBytes)
                    } else {
                        imgFile.inputStream().use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }

            // 5. Write word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(buildDocumentXml(projectId, summaryLines, workVolumeRows, photos, dailyLogs).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

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

    private fun downsamplePhotoBytes(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1600, 1600)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        }
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

    private fun buildContentTypesXml(photoCount: Int): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
    }

    private fun buildDocumentRelsXml(photoCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..photoCount) {
            sb.append("""
  <Relationship Id="rId_img_$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image_$i.jpg"/>""")
        }
        sb.append("\n</Relationships>")
        return sb.toString()
    }

    private fun buildDocumentXml(
        projectId: String,
        summaryLines: List<String>,
        workVolumeRows: List<MaterialReportRow>,
        photos: List<SitePhoto>,
        dailyLogs: List<DailyLog>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>""")

        // Title
        sb.append("""
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="36"/>
          <w:szCs w:val="36"/>
          <w:color w:val="F5A623"/>
        </w:rPr>
        <w:t>BÁO CÁO GIÁM SÁT DỰ ÁN</w:t>
      </w:r>
    </w:p>
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:sz w:val="24"/>
          <w:szCs w:val="24"/>
        </w:rPr>
        <w:t>Dự án: $projectId</w:t>
      </w:r>
    </w:p>
    <w:p/>""")

        // Summary Lines (General Stats & AI info)
        summaryLines.forEach { line ->
            sb.append("""
    <w:p>
      <w:r>
        <w:rPr>
          <w:sz w:val="24"/>
          <w:szCs w:val="24"/>
        </w:rPr>
        <w:t>${escapeXml(line)}</w:t>
      </w:r>
    </w:p>""")
        }
        sb.append("<w:p/>")

        // Materials Table Header Text
        sb.append("""
    <w:p>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:szCs w:val="28"/>
        </w:rPr>
        <w:t>Bảng tổng hợp khối lượng thi công</w:t>
      </w:r>
    </w:p>
    <w:tbl>
      <w:tblPr>
        <w:tblStyle w:val="TableGrid"/>
        <w:tblW w:w="5000" w:type="pct"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:left w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:right w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:insideV w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
        </w:tblBorders>
      </w:tblPr>
      <w:tblGrid>
        <w:gridCol w:w="4000"/>
        <w:gridCol w:w="2000"/>
        <w:gridCol w:w="2000"/>
        <w:gridCol w:w="1000"/>
      </w:tblGrid>
      <w:tr>
        <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Nội dung công việc</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>KL Thiết kế</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>KL Thi công</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>%</w:t></w:r></w:p></w:tc>
      </w:tr>""")

        // Materials Table Rows
        workVolumeRows.forEach { row ->
            sb.append("""
      <w:tr>
        <w:tc><w:p><w:r><w:rPr>${if (row.isTotal) "<w:b/>" else ""}</w:rPr><w:t>${escapeXml(row.workName)}</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:rPr>${if (row.isTotal) "<w:b/>" else ""}</w:rPr><w:t>${row.totalPlannedQty.toInt()}</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:rPr>${if (row.isTotal) "<w:b/>" else ""}</w:rPr><w:t>${row.totalActualQty.toInt()}</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:rPr>${if (row.isTotal) "<w:b/>" else ""}</w:rPr><w:t>${row.completionPercent.toInt()}%</w:t></w:r></w:p></w:tc>
      </w:tr>""")
        }
        sb.append("\n    </w:tbl>\n    <w:p/>")

        if (dailyLogs.isNotEmpty()) {
            sb.append("""
    <w:p>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:szCs w:val="28"/>
        </w:rPr>
        <w:t>Nhật ký thi công và Thời tiết</w:t>
      </w:r>
    </w:p>""")

            dailyLogs.sortedByDescending { it.createdAtEpochMs }.forEach { log ->
                val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.createdAtEpochMs))
                val temperatureText = if (log.temperature != 0.0) " | Nhiệt độ: ${"%.1f".format(log.temperature)}°C" else ""
                val weatherText = if (log.weather.isNotBlank()) " | Thời tiết: ${log.weather}" else ""
                val nodeText = log.nodeCode?.let { " | Node: $it" }.orEmpty()
                val routeText = log.routeCode?.let { " | Tuyến: $it" }.orEmpty()
                val volumeText = if (log.volume != 0.0) {
                    val unitText = if (log.unit.isNotBlank()) " ${log.unit}" else ""
                    " | Khối lượng: ${"%.2f".format(log.volume)}$unitText"
                } else ""
                val noteText = if (log.note.isNotBlank()) " | Ghi chú: ${log.note}" else ""
                val line = "[$timeStr] ${log.workItem}$nodeText$routeText$weatherText$temperatureText$volumeText$noteText"

                sb.append("""
    <w:p>
      <w:r>
        <w:rPr>
          <w:sz w:val="22"/>
          <w:szCs w:val="22"/>
        </w:rPr>
        <w:t>${escapeXml(line)}</w:t>
      </w:r>
    </w:p>""")
            }
            sb.append("<w:p/>")
        }

        // Photo Grid Section
        if (photos.isNotEmpty()) {
            sb.append("""
    <w:p>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="28"/>
          <w:szCs w:val="28"/>
        </w:rPr>
        <w:t>Nhật ký hình ảnh thực địa</w:t>
      </w:r>
    </w:p>
    <w:tbl>
      <w:tblPr>
        <w:tblStyle w:val="TableGrid"/>
        <w:tblW w:w="5000" w:type="pct"/>
        <w:tblBorders>
          <w:top w:val="none"/>
          <w:left w:val="none"/>
          <w:bottom w:val="none"/>
          <w:right w:val="none"/>
          <w:insideH w:val="none"/>
          <w:insideV w:val="none"/>
        </w:tblBorders>
      </w:tblPr>
      <w:tblGrid>
        <w:gridCol w:w="4500"/>
        <w:gridCol w:w="4500"/>
      </w:tblGrid>""")

            val chunked = photos.chunked(2)
            chunked.forEach { rowPhotos ->
                sb.append("\n      <w:tr>")
                rowPhotos.forEachIndexed { index, photo ->
                    val photoIndex = photos.indexOf(photo) + 1
                    val latStr = photo.latitude?.let { "%.6f".format(it) } ?: "N/A"
                    val lngStr = photo.longitude?.let { "%.6f".format(it) } ?: "N/A"
                    val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(photo.capturedAtEpochMs))
                    
                    sb.append("""
        <w:tc>
          <w:p>
            <w:r>
              <w:drawing>
                <wp:inline distT="0" distB="0" distL="0" distR="0">
                  <wp:extent cx="2438400" cy="1828800"/>
                  <wp:docPr id="$photoIndex" name="Image_$photoIndex"/>
                  <wp:cNvGraphicPr>
                    <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                  </wp:cNvGraphicPr>
                  <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                      <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                        <pic:nvPicPr>
                          <pic:cNvPr id="0" name="Image_$photoIndex"/>
                          <pic:cNvPicPr/>
                        </pic:nvPicPr>
                        <pic:blipFill>
                          <a:blip r:embed="rId_img_$photoIndex"/>
                          <a:stretch>
                            <a:fillRect/>
                          </a:stretch>
                        </pic:blipFill>
                        <pic:spPr>
                          <a:xfrm>
                            <a:off x="0" y="0"/>
                            <a:ext cx="2438400" cy="1828800"/>
                          </a:xfrm>
                          <a:prstGeom prst="rect">
                            <a:avLst/>
                          </a:prstGeom>
                        </pic:spPr>
                      </pic:pic>
                    </a:graphicData>
                  </a:graphic>
                </wp:inline>
              </w:drawing>
            </w:r>
          </w:p>
          <w:p>
            <w:r>
              <w:rPr>
                <w:sz w:val="18"/>
                <w:szCs w:val="18"/>
                <w:color w:val="666666"/>
              </w:rPr>
              <w:t>📍 ${escapeXml(photo.objectCode)} | Toạ độ: $latStr, $lngStr&#xA;Thời gian: $timeStr</w:t>
            </w:r>
          </w:p>
        </w:tc>""")
                }
                if (rowPhotos.size < 2) {
                    sb.append("\n        <w:tc><w:p/></w:tc>")
                }
                sb.append("\n      </w:tr>")
            }
            sb.append("\n    </w:tbl>")
        }

        sb.append("""
  </w:body>
</w:document>""")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

