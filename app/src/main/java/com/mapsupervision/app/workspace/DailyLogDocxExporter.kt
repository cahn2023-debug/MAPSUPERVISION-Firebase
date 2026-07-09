package com.mapsupervision.app.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Environment
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.DailyLogLine
import com.mapsupervision.domain.model.DailyLogLineType
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.resolveEpochDay
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object DailyLogDocxExporter {
    fun export(
        context: Context,
        projectId: String,
        scopeLabel: String,
        dailyLogs: List<DailyLog>,
        workPlans: List<WorkPlan>,
        progress: List<NodeProgress>,
        nodes: List<GisNode>,
        photos: List<SitePhoto>,
        routeLabels: Map<String, String>,
        includePlan: Boolean
    ): File {
        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MapSupervision/DailyLogs"
        ).apply { mkdirs() }
        val scopeSlug = scopeLabel
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .ifBlank { "all" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(exportDir, "nhatky_${projectId}_${scopeSlug}_$timestamp.docx")

        val imageFiles = photos.mapNotNull { photo ->
            val file = File(photo.filePath)
            if (file.exists()) DailyLogDocxPhoto(photo = photo, file = file) else null
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(buildContentTypesXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(buildRootRelsXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(buildDocumentRelsXml(imageFiles.size).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            imageFiles.forEachIndexed { index, item ->
                zos.putNextEntry(ZipEntry("word/media/image_${index + 1}.jpg"))
                val bytes = downsamplePhotoBytes(item.file) ?: item.file.readBytes()
                zos.write(bytes)
                zos.closeEntry()
            }

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(
                buildDocumentXml(
                    projectId = projectId,
                    scopeLabel = scopeLabel,
                    dailyLogs = dailyLogs,
                    workPlans = workPlans,
                    progress = progress,
                    nodes = nodes,
                    photos = imageFiles,
                    routeLabels = routeLabels,
                    includePlan = includePlan
                ).toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
        return outFile
    }

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Default Extension="jpg" ContentType="image/jpeg"/>
          <Default Extension="png" ContentType="image/png"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildDocumentRelsXml(imageCount: Int): String {
        val xml = StringBuilder(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            """.trimIndent()
        )
        for (index in 1..imageCount) {
            xml.append(
                """
                
                  <Relationship Id="rId_img_$index" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image_$index.jpg"/>
                """.trimIndent()
            )
        }
        xml.append("\n</Relationships>")
        return xml.toString()
    }

    private fun buildDocumentXml(
        projectId: String,
        scopeLabel: String,
        dailyLogs: List<DailyLog>,
        workPlans: List<WorkPlan>,
        progress: List<NodeProgress>,
        nodes: List<GisNode>,
        photos: List<DailyLogDocxPhoto>,
        routeLabels: Map<String, String>,
        includePlan: Boolean
    ): String {
        val logsByDay = dailyLogs.groupBy { it.resolveEpochDay() }
        val plansByDay = workPlans.groupBy { it.plannedDateEpochDay }
        val photosByDay = photos.groupBy { it.photo.captureEpochDay() }
        val progressByNode = progress.associateBy { it.nodeCode }
        val nodesByCode = nodes.associateBy { it.code }
        val allDays = buildSet {
            addAll(logsByDay.keys)
            addAll(photosByDay.keys)
            if (includePlan) addAll(plansByDay.keys)
        }
            .filter { it > 0L }
            .sorted()

        fun locationLabel(nodeCode: String?, routeCode: String?): String {
            return when {
                !nodeCode.isNullOrBlank() -> {
                    val node = nodesByCode[nodeCode]
                    val label = node?.mapNumberLabel?.takeIf { it.isNotBlank() } ?: nodeCode
                    "$label ($nodeCode)"
                }
                !routeCode.isNullOrBlank() -> routeLabels[routeCode] ?: routeCode
                else -> "Khong lien ket"
            }
        }

        fun addParagraph(
            xml: StringBuilder,
            text: String,
            bold: Boolean = false,
            size: Int = 22,
            center: Boolean = false
        ) {
            xml.append("\n<w:p>")
            if (center) {
                xml.append("<w:pPr><w:jc w:val=\"center\"/></w:pPr>")
            }
            xml.append("<w:r><w:rPr>")
            if (bold) xml.append("<w:b/>")
            xml.append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr>")
            xml.append("<w:t xml:space=\"preserve\">${escapeXml(text)}</w:t></w:r></w:p>")
        }

        fun addImage(xml: StringBuilder, imageIndex: Int) {
            xml.append(
                """
                
                <w:p>
                  <w:r>
                    <w:drawing>
                      <wp:inline distT="0" distB="0" distL="0" distR="0"
                        xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
                        <wp:extent cx="4114800" cy="3086100"/>
                        <wp:docPr id="$imageIndex" name="Image_$imageIndex"/>
                        <wp:cNvGraphicPr>
                          <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                        </wp:cNvGraphicPr>
                        <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                            <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                              <pic:nvPicPr>
                                <pic:cNvPr id="0" name="Image_$imageIndex"/>
                                <pic:cNvPicPr/>
                              </pic:nvPicPr>
                              <pic:blipFill>
                                <a:blip r:embed="rId_img_$imageIndex"/>
                                <a:stretch><a:fillRect/></a:stretch>
                              </pic:blipFill>
                              <pic:spPr>
                                <a:xfrm>
                                  <a:off x="0" y="0"/>
                                  <a:ext cx="4114800" cy="3086100"/>
                                </a:xfrm>
                                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                              </pic:spPr>
                            </pic:pic>
                          </a:graphicData>
                        </a:graphic>
                      </wp:inline>
                    </w:drawing>
                  </w:r>
                </w:p>
                """.trimIndent()
            )
        }

        fun addActualLines(xml: StringBuilder, log: DailyLog) {
            if (log.plannedWorkName.isNotBlank()) {
                addParagraph(xml, "Ke hoach goc: ${log.plannedWorkName}", bold = true, size = 20)
                if (log.plannedQuantity > 0.0 || log.plannedUnit.isNotBlank()) {
                    addParagraph(
                        xml,
                        "Khoi luong ke hoach: ${formatQty(log.plannedQuantity)} ${log.plannedUnit}".trim(),
                        size = 20
                    )
                }
                addParagraph(
                    xml,
                    "Vi tri ke hoach: ${locationLabel(log.plannedNodeCode, log.plannedRouteCode)}",
                    size = 20
                )
            }

            val lines = log.lines.ifEmpty { fallbackLogLines(log) }
            if (lines.isNotEmpty()) {
                addParagraph(xml, "Khoi luong thuc hien", bold = true, size = 20)
                lines.forEach { line ->
                    val label = if (line.lineType == DailyLogLineType.PLAN_PRIMARY) "Theo ke hoach" else "Phat sinh"
                    val workTitle = buildString {
                        append("$label: ${line.workName}")
                        if (line.categoryName.isNotBlank()) append(" [${line.categoryName}]")
                    }
                    addParagraph(xml, workTitle, size = 20)
                    addParagraph(
                        xml,
                        "Luy ke: ${formatQty(line.quantity)} ${line.unit}".trim(),
                        size = 20
                    )
                    addParagraph(
                        xml,
                        "Vi tri: ${locationLabel(line.nodeCode, line.routeCode)}",
                        size = 20
                    )
                }
            }
        }

        val xml = StringBuilder(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <w:body>
            """.trimIndent()
        )

        addParagraph(xml, "NHAT KY THI CONG", bold = true, size = 34, center = true)
        addParagraph(xml, "Du an: $projectId", size = 24, center = true)
        addParagraph(xml, "Pham vi: $scopeLabel", size = 22, center = true)
        addParagraph(
            xml,
            "Ngay xuat: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}",
            size = 20,
            center = true
        )
        addParagraph(xml, "", size = 20)

        if (allDays.isEmpty()) {
            addParagraph(xml, "Khong co du lieu nhat ky trong pham vi da chon.", size = 22)
        } else {
            allDays.forEach { epochDay ->
                val dateText = LocalDate.ofEpochDay(epochDay)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                val dayLogs = logsByDay[epochDay].orEmpty().sortedBy { it.createdAtEpochMs }
                val dayPlans = plansByDay[epochDay].orEmpty()
                val dayPhotos = photosByDay[epochDay].orEmpty()

                addParagraph(xml, "NGAY $dateText", bold = true, size = 28)
                val weather = dayLogs.firstOrNull { it.weather.isNotBlank() }?.let { log ->
                    buildString {
                        append(log.weather)
                        if (log.temperature != 0.0) append(" - ${log.temperature.roundToInt()}°C")
                    }
                } ?: "Chua cap nhat"
                addParagraph(xml, "Thoi tiet: $weather", size = 20)

                if (includePlan) {
                    addParagraph(xml, "Ke hoach", bold = true, size = 24)
                    if (dayPlans.isEmpty()) {
                        addParagraph(xml, "Chua co ke hoach trong ngay.", size = 20)
                    } else {
                        dayPlans.forEach { plan ->
                            addParagraph(xml, plan.title, bold = true, size = 20)
                            addParagraph(
                                xml,
                                "Khoi luong ke hoach: ${formatQty(plan.quantity)} ${plan.unit}".trim(),
                                size = 20
                            )
                            addParagraph(
                                xml,
                                "Vi tri/tuyen: ${locationLabel(plan.nodeCode, plan.routeCode)}",
                                size = 20
                            )
                            if (plan.description.isNotBlank()) {
                                addParagraph(xml, "Ghi chu: ${plan.description}", size = 20)
                            }
                        }
                    }
                }

                addParagraph(xml, "Nhat ky thi cong", bold = true, size = 24)
                if (dayLogs.isEmpty()) {
                    addParagraph(xml, "Chua co nhat ky trong ngay.", size = 20)
                } else {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    dayLogs.forEach { log ->
                        addParagraph(xml, "Thoi gian: ${timeFormat.format(Date(log.createdAtEpochMs))}", bold = true, size = 20)
                        addParagraph(xml, "Nhan cong: ${log.manpower}", size = 20)
                        addActualLines(xml, log)
                        log.nodeCode?.let { nodeCode ->
                            progressByNode[nodeCode]?.let { nodeProgress ->
                                addParagraph(
                                    xml,
                                    "Tien do node: KH ${formatPercent(nodeProgress.planned)} / TT ${formatPercent(nodeProgress.actual)}",
                                    size = 20
                                )
                            }
                        }
                        if (log.note.isNotBlank()) {
                            addParagraph(xml, "Ghi chu: ${log.note}", size = 20)
                        }
                    }
                }

                val relatedNodeCodes = buildSet {
                    addAll(dayLogs.mapNotNull { it.nodeCode })
                    addAll(dayPlans.mapNotNull { it.nodeCode })
                }
                if (relatedNodeCodes.isNotEmpty()) {
                    addParagraph(xml, "Tien do lien quan", bold = true, size = 24)
                    relatedNodeCodes.forEach { nodeCode ->
                        progressByNode[nodeCode]?.let { nodeProgress ->
                            val nodeName = nodesByCode[nodeCode]?.mapNumberLabel?.takeIf { it.isNotBlank() } ?: nodeCode
                            addParagraph(
                                xml,
                                "$nodeName: KH ${formatPercent(nodeProgress.planned)} / TT ${formatPercent(nodeProgress.actual)}",
                                size = 20
                            )
                        }
                    }
                }

                if (dayPhotos.isNotEmpty()) {
                    addParagraph(xml, "Anh nhat ky", bold = true, size = 24)
                    dayPhotos.forEach { item ->
                        addImage(xml, item.imageIndex(photos))
                        val photo = item.photo
                        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(photo.capturedAtEpochMs))
                        addParagraph(xml, "Anh: ${photo.objectCode} - $timeText", size = 18)
                    }
                }

                addParagraph(xml, "", size = 18)
            }
        }

        addParagraph(xml, "", size = 20)
        addParagraph(xml, "Nguoi lap: ____________________", size = 22)
        addParagraph(xml, "Chi huy cong trinh: ____________________", size = 22)
        addParagraph(xml, "Xac nhan: ____________________", size = 22)

        xml.append("\n</w:body></w:document>")
        return xml.toString()
    }

    private fun fallbackLogLines(log: DailyLog): List<DailyLogLine> {
        if (log.workItem.isBlank() && log.volume == 0.0 && log.unit.isBlank()) return emptyList()
        return listOf(
            DailyLogLine(
                id = log.id,
                projectId = log.projectId,
                dailyLogId = log.id,
                lineType = if (log.plannedWorkName.isNotBlank()) DailyLogLineType.PLAN_PRIMARY else DailyLogLineType.EXTRA,
                workName = log.workItem,
                categoryName = log.categoryName,
                quantity = log.volume,
                unit = log.unit,
                nodeCode = log.nodeCode,
                routeCode = log.routeCode,
                linkedWorkPlanId = log.linkedWorkPlanId,
                createdAtEpochMs = log.createdAtEpochMs
            )
        )
    }

    private fun downsamplePhotoBytes(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1800, 1800)
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

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var sampleSize = 1
        while ((srcWidth / sampleSize) > reqWidth || (srcHeight / sampleSize) > reqHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatQty(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f%%", value)
}

private data class DailyLogDocxPhoto(
    val photo: SitePhoto,
    val file: File
)

private fun DailyLogDocxPhoto.imageIndex(allPhotos: List<DailyLogDocxPhoto>): Int {
    return allPhotos.indexOfFirst { it.photo.id == photo.id } + 1
}

private fun SitePhoto.captureEpochDay(): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = capturedAtEpochMs }
    return LocalDate.of(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).toEpochDay()
}
