package com.mapsupervision.storage.importer

import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ExcelClassificationMode
import com.mapsupervision.domain.model.ExcelColumnMapping
import com.mapsupervision.domain.model.ExcelMappingSuggestion
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
fun readXlsxTable(file: File, sheetName: String? = null): XlsxTable {
    val totalStartedAtMs = System.currentTimeMillis()
    ZipFile(file).use { zip ->
        val sharedStartedAtMs = System.currentTimeMillis()
        val sharedStrings = readSharedStrings(zip)
        val sharedMs = System.currentTimeMillis() - sharedStartedAtMs
        val sheetEntry = findWorksheetEntryForSheet(zip, sheetName) ?: throw IllegalArgumentException("E_PARSE: no worksheet found")
        val scanStartedAtMs = System.currentTimeMillis()
        val scanned = zip.getInputStream(sheetEntry).use { parseWorksheet(it, sharedStrings) }
        val scanMs = System.currentTimeMillis() - scanStartedAtMs
        val rowMaps = scanned.rowMaps
        if (rowMaps.isEmpty()) return XlsxTable(emptyList(), emptyList())
        var maxCol = scanned.maxCol
        val merges = scanned.merges
        merges.forEach { maxCol = maxOf(maxCol, it.endCol) }

        val denseStartedAtMs = System.currentTimeMillis()
        val sortedRows = ArrayList<Map.Entry<Int, MutableMap<Int, String>>>(rowMaps.size)
        sortedRows.addAll(rowMaps.entries)
        sortedRows.sortBy { it.key }
        if (sortedRows.isEmpty()) return XlsxTable(emptyList(), emptyList())

        val rowIndexToDense = HashMap<Int, Int>(sortedRows.size)
        val rows = MutableList(sortedRows.size) { MutableList(maxCol + 1) { "" } }
        for (denseIndex in sortedRows.indices) {
            val entry = sortedRows[denseIndex]
            val originalRowIndex = entry.key
            rowIndexToDense[originalRowIndex] = denseIndex
            val rowMap = entry.value
            val denseRow = rows[denseIndex]
            for ((col, value) in rowMap) {
                denseRow[col] = value
            }
        }

        val denseMerges = ArrayList<MergeRange>(merges.size)
        for (merge in merges) {
            val denseStart = rowIndexToDense[merge.startRow] ?: continue
            val denseEnd = rowIndexToDense[merge.endRow] ?: denseStart
            denseMerges += MergeRange(
                startRow = minOf(denseStart, denseEnd),
                startCol = merge.startCol,
                endRow = maxOf(denseStart, denseEnd),
                endCol = merge.endCol
            )
        }
        val denseMs = System.currentTimeMillis() - denseStartedAtMs

        val mergeApplyStartedAtMs = System.currentTimeMillis()
        val resolvedRows = applyMergedCells(rows, denseMerges)
        val mergeApplyMs = System.currentTimeMillis() - mergeApplyStartedAtMs
        val headerStartedAtMs = System.currentTimeMillis()
        val headerRows = detectHeaderRows(resolvedRows)
        val headers = flattenHeaders(resolvedRows, headerRows)
        val expectedCols = headers.size
        val dataCount = (resolvedRows.size - headerRows).coerceAtLeast(0)
        val dataRows = ArrayList<List<String>>(dataCount)
        for (rowIndex in headerRows until resolvedRows.size) {
            val row = resolvedRows[rowIndex]
            if (row.size == expectedCols) {
                dataRows += row
                continue
            }
            val adjusted = ArrayList<String>(expectedCols)
            val copyCount = minOf(row.size, expectedCols)
            for (i in 0 until copyCount) adjusted.add(row[i])
            while (adjusted.size < expectedCols) adjusted.add("")
            dataRows.add(adjusted)
        }
        val headerMs = System.currentTimeMillis() - headerStartedAtMs
        AppLogger.d(
            "perf.xlsx.table sharedMs=$sharedMs scanMs=$scanMs denseMs=$denseMs mergeMs=$mergeApplyMs " +
                "headerMs=$headerMs totalMs=${System.currentTimeMillis() - totalStartedAtMs} " +
                "rows=${dataRows.size} headers=${headers.size} merges=${merges.size} sharedStrings=${sharedStrings.size}"
        )
        return XlsxTable(headers = headers, rows = dataRows)
    }
}

fun parseWorksheet(input: InputStream, sharedStrings: List<String>): WorksheetScan {
    val parser = xmlPullParserFactory.newPullParser()
    parser.setInput(InputStreamReader(input, Charsets.UTF_8))

    val rowMaps = HashMap<Int, MutableMap<Int, String>>(512)
    val merges = ArrayList<MergeRange>(64)
    var maxCol = 0

    var currentRowIndex = -1
    var nextImplicitRowIndex = 0
    var currentCellCol = -1
    var currentCellSharedString = false
    var currentCellInlineString = false
    var currentRowMap: MutableMap<Int, String>? = null
    var inValueNode = false
    var inInlineTextNode = false
    val valueBuilder = StringBuilder(32)
    val inlineBuilder = StringBuilder(32)
    var nextImplicitCol = 0

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "row" -> {
                        val attr = parser.getAttributeValue(null, "r")
                        currentRowIndex = attr?.let { parsePositiveInt(it)?.minus(1) } ?: nextImplicitRowIndex
                        nextImplicitRowIndex = currentRowIndex + 1
                        currentRowMap = null
                        nextImplicitCol = 0
                    }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        currentCellCol = if (ref.isBlank()) nextImplicitCol else colIndexFromRef(ref)
                        nextImplicitCol = currentCellCol + 1
                        val cellType = parser.getAttributeValue(null, "t").orEmpty()
                        currentCellSharedString = cellType == "s"
                        currentCellInlineString = cellType == "inlineStr"
                        valueBuilder.setLength(0)
                        inlineBuilder.setLength(0)
                    }
                    "v" -> inValueNode = true
                    "t" -> if (currentCellInlineString) inInlineTextNode = true
                    "mergeCell" -> {
                        val ref = parser.getAttributeValue(null, "ref").orEmpty()
                        val range = parseMergeRef(ref)
                        if (range != null) {
                            merges += range
                            if (range.endCol > maxCol) maxCol = range.endCol
                        }
                    }
                }
            }
            XmlPullParser.TEXT -> {
                if (inValueNode) valueBuilder.append(parser.text.orEmpty())
                if (inInlineTextNode) inlineBuilder.append(parser.text.orEmpty())
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "v" -> inValueNode = false
                    "t" -> inInlineTextNode = false
                    "c" -> {
                        if (currentRowIndex >= 0 && currentCellCol >= 0) {
                            val value = resolveCellValue(
                                sharedString = currentCellSharedString,
                                inlineString = currentCellInlineString,
                                value = valueBuilder,
                                inline = inlineBuilder,
                                sharedStrings = sharedStrings
                            )
                            if (value.isNotEmpty()) {
                                val rowMap = currentRowMap ?: HashMap<Int, String>(16).also { currentRowMap = it }
                                rowMap[currentCellCol] = value
                            }
                            if (currentCellCol > maxCol) maxCol = currentCellCol
                        }
                    }
                    "row" -> {
                        val map = currentRowMap
                        if (currentRowIndex >= 0 && map != null && map.isNotEmpty()) {
                            rowMaps[currentRowIndex] = map
                        }
                        currentRowMap = null
                    }
                }
            }
        }
        eventType = parser.next()
    }
    return WorksheetScan(rowMaps = rowMaps, merges = merges, maxCol = maxCol)
}

fun findFirstWorksheetEntry(zip: ZipFile): java.util.zip.ZipEntry? {
    val enumeration = zip.entries()
    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement()
        if (entry.isDirectory) continue
        val name = entry.name
        if (
            name.length >= 24 &&
            name.regionMatches(0, "xl/worksheets/sheet", 0, 19, ignoreCase = true) &&
            name.regionMatches(name.length - 4, ".xml", 0, 4, ignoreCase = true)
        ) {
            return entry
        }
    }
    return null
}


fun findWorksheetEntryForSheet(zip: ZipFile, sheetName: String?): java.util.zip.ZipEntry? {
    if (sheetName == null) return findFirstWorksheetEntry(zip)
    val relsEntry = zip.getEntry("xl/_rels/workbook.xml.rels")
    val rIdToTarget = HashMap<String, String>()
    if (relsEntry != null) {
        runCatching {
            zip.getInputStream(relsEntry).use { input ->
                val parser = xmlPullParserFactory.newPullParser()
                parser.setInput(InputStreamReader(input, Charsets.UTF_8))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                        val id = parser.getAttributeValue(null, "Id")
                        val target = parser.getAttributeValue(null, "Target")
                        if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                            rIdToTarget[id] = target
                        }
                    }
                    eventType = parser.next()
                }
            }
        }
    }

    val workbookEntry = zip.getEntry("xl/workbook.xml") ?: return null
    var targetRid: String? = null
    var sheetIndex = -1
    var currentIdx = 0
    runCatching {
        zip.getInputStream(workbookEntry).use { input ->
            val parser = xmlPullParserFactory.newPullParser()
            parser.setInput(InputStreamReader(input, Charsets.UTF_8))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    if (name.equals(sheetName, ignoreCase = true)) {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i)
                            if (attrName == "id" || attrName.endsWith(":id") || parser.getAttributeNamespace(i).contains("relationships")) {
                                targetRid = parser.getAttributeValue(i)
                            }
                        }
                        if (targetRid == null) {
                            val rIdVal = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                            if (!rIdVal.isNullOrBlank()) {
                                targetRid = rIdVal
                            }
                        }
                        sheetIndex = currentIdx
                    }
                    currentIdx++
                }
                eventType = parser.next()
            }
        }
    }

    if (targetRid != null) {
        val targetPath = rIdToTarget[targetRid!!]
        if (targetPath != null) {
            val cleanPath = targetPath.removePrefix("/")
            val fullPath = if (cleanPath.startsWith("xl/")) cleanPath else "xl/$cleanPath"
            val entry = zip.getEntry(fullPath)
            if (entry != null) return entry
        }
    }

    if (sheetIndex >= 0) {
        val sheets = ArrayList<java.util.zip.ZipEntry>()
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (entry.isDirectory) continue
            val name = entry.name
            if (name.length >= 24 &&
                name.regionMatches(0, "xl/worksheets/sheet", 0, 19, ignoreCase = true) &&
                name.regionMatches(name.length - 4, ".xml", 0, 4, ignoreCase = true)
            ) {
                sheets.add(entry)
            }
        }
        sheets.sortBy { entry ->
            val numPart = entry.name.substringAfter("xl/worksheets/sheet").substringBefore(".xml")
            numPart.toIntOrNull() ?: 0
        }
        if (sheetIndex < sheets.size) {
            return sheets[sheetIndex]
        }
    }

    return findFirstWorksheetEntry(zip)
}

fun parseMergeRef(ref: String): MergeRange? {
    val sep = ref.indexOf(':')
    if (sep <= 0 || sep >= ref.lastIndex) return null
    val startCol = colIndexFromRef(ref, 0, sep)
    val endCol = colIndexFromRef(ref, sep + 1, ref.length)
    val startRow = rowIndexFromRef(ref, 0, sep)
    val endRow = rowIndexFromRef(ref, sep + 1, ref.length)
    if (startRow < 0 || endRow < 0) return null
    return MergeRange(
        startRow = minOf(startRow, endRow),
        startCol = minOf(startCol, endCol),
        endRow = maxOf(startRow, endRow),
        endCol = maxOf(startCol, endCol)
    )
}

fun applyMergedCells(
    rows: List<MutableList<String>>,
    merges: List<MergeRange>
): List<List<String>> {
    if (merges.isEmpty()) return rows

    val mutableRows = rows.toMutableList()
    val maxEndRow = merges.maxOfOrNull { it.endRow } ?: -1
    val maxEndCol = merges.maxOfOrNull { it.endCol } ?: -1

    while (mutableRows.size <= maxEndRow) mutableRows.add(mutableListOf())
    if (maxEndCol >= 0 && mutableRows.isNotEmpty()) {
        for (row in mutableRows) {
            val deficit = maxEndCol - row.lastIndex
            if (deficit > 0) {
                repeat(deficit) { row.add("") }
            }
        }
    }

    merges.forEach { merge ->
        if (merge.startRow !in mutableRows.indices) return@forEach
        val sourceRow = mutableRows[merge.startRow]
        if (merge.startCol !in sourceRow.indices) return@forEach
        val sourceValue = sourceRow[merge.startCol]
        if (sourceValue.isBlank()) return@forEach

        for (rowIndex in merge.startRow..merge.endRow) {
            val row = mutableRows[rowIndex]
            for (colIndex in merge.startCol..merge.endCol) {
                if (row[colIndex].isBlank()) {
                    row[colIndex] = sourceValue
                }
            }
        }
    }
    return mutableRows
}

fun detectHeaderRows(rows: List<List<String>>): Int {
    if (rows.isEmpty()) return 0
    val limit = minOf(5, rows.size)
    val coordinateCellCache = HashMap<String, Boolean>(32)
    for (rowIndex in 0 until limit) {
        val row = rows[rowIndex]
        var nonBlankCount = 0
        var numericCount = 0
        var hasCoordinateCell = false

        for (rawCell in row) {
            val cell = rawCell.trim()
            if (cell.isBlank()) continue
            nonBlankCount++
            val coordinateCandidate = coordinateCellCache.getOrPut(cell) {
                isCoordinateCandidate(cell) && parseCoordinatesRobust(cell).isNotEmpty()
            }
            if (coordinateCandidate) {
                hasCoordinateCell = true
                break
            }
            if (cell.toPlainNumberOrNull() != null) {
                numericCount++
            }
        }

        if (nonBlankCount == 0) {
            if (rowIndex > 0) return rowIndex
            continue
        }
        if (hasCoordinateCell) {
            return maxOf(1, rowIndex)
        }
        if (nonBlankCount >= 3 && numericCount.toFloat() / nonBlankCount > 0.6f) {
            return maxOf(1, rowIndex)
        }
    }
    return minOf(limit, rows.size).coerceAtLeast(1)
}

fun flattenHeaders(rows: List<List<String>>, headerRows: Int): List<String> {
    if (rows.isEmpty() || headerRows <= 0) return emptyList()
    val maxCols = (0 until headerRows).maxOfOrNull { rowIndex -> rows.getOrNull(rowIndex)?.size ?: 0 } ?: 0
    val used = HashMap<String, Int>(maxCols * 2)
    val headers = ArrayList<String>(maxCols)
    for (colIndex in 0 until maxCols) {
        val parts = ArrayList<String>(headerRows)
        var lastPart = ""
        for (rowIndex in 0 until headerRows) {
            val raw = rows.getOrNull(rowIndex)?.getOrNull(colIndex).orEmpty().trim()
            if (raw.isEmpty()) continue
            if (raw != lastPart) {
                parts += raw
                lastPart = raw
            }
        }
        val base = if (parts.isEmpty()) "Column ${colIndex + 1}" else parts.joinToString(" > ")
        val count = (used[base] ?: 0) + 1
        used[base] = count
        headers += if (count == 1) base else "$base ($count)"
    }
    return headers
}

fun autoDetectExcelMapping(table: XlsxTable): ExcelColumnMapping? {
    return autoDetectExcelMappingSuggestion(table)?.mapping
}

fun autoDetectExcelMappingSuggestion(table: XlsxTable): ExcelMappingSuggestion? {
    val headers = table.headers
    if (headers.isEmpty()) return null
    val normalizedHeaders = ArrayList<String>(headers.size)
    for (header in headers) normalizedHeaders.add(normalizeText(header))
    val headerCount = headers.size
    val sampleLimit = 5
    val sampleCounts = IntArray(headerCount)
    val numericCounts = IntArray(headerCount)
    for (row in table.rows) {
        var anyPending = false
        val rowSize = row.size
        for (col in 0 until headerCount) {
            if (sampleCounts[col] >= sampleLimit) continue
            anyPending = true
            if (col >= rowSize) continue
            val value = row[col].trim()
            if (value.isBlank()) continue
            sampleCounts[col]++
            if (value.toPlainNumberOrNull() != null) numericCounts[col]++
        }
        if (!anyPending) break
    }
    val sampleNumericHintByIndex = BooleanArray(headerCount)
    for (index in headers.indices) {
        val sampleCount = sampleCounts[index]
        val hint = sampleCount > 0 && numericCounts[index] >= maxOf(1, sampleCount / 2)
        sampleNumericHintByIndex[index] = hint
    }

    var positionIndex = -1
    var fallbackCodeIndex = -1
    var coordinateIndex = -1
    var latitudeIndex = -1
    var longitudeIndex = -1
    var contractorIndex = -1
    var objectTypeIndex = -1
    var remaining = 6
    for (i in headers.indices) {
        val normalized = normalizedHeaders[i]
        if (positionIndex < 0) {
            val isContractorKey = normalized.contains("nha thau") ||
                    normalized.contains("contractor") ||
                    normalized.contains("don vi") ||
                    normalized.contains("team")
            val isCoordKey = normalized.contains("toa do") ||
                    normalized.contains("gps") ||
                    normalized.contains("coordinate") ||
                    normalized.contains("coord") ||
                    normalized.contains("vi do") ||
                    normalized.contains("latitude") ||
                    normalized.contains("lat") ||
                    normalized.contains("kinh do") ||
                    normalized.contains("longitude") ||
                    normalized.contains("lng") ||
                    normalized.contains("lon")

            if (!isContractorKey && !isCoordKey && (
                normalized.contains("vi tri") ||
                normalized.contains("node") ||
                normalized.contains("nut") ||
                normalized.contains("tuyen") ||
                normalized.contains("ten") ||
                normalized.contains("name") ||
                normalized.contains("ma doi tuong") ||
                normalized.contains("ma node") ||
                normalized.contains("object")
            )) {
                positionIndex = i
                remaining--
            } else if (fallbackCodeIndex < 0 && (normalized == "ma" || normalized.endsWith(" ma"))) {
                fallbackCodeIndex = i
            }
        }
        if (coordinateIndex < 0 && (normalized.contains("toa do") || normalized.contains("gps") || normalized.contains("coordinate") || normalized.contains("coord"))) {
            coordinateIndex = i
            remaining--
        }
        if (latitudeIndex < 0 && (normalized.contains("vi do") || normalized.contains("latitude") || normalized.contains("lat"))) {
            latitudeIndex = i
            remaining--
        }
        if (longitudeIndex < 0 && (normalized.contains("kinh do") || normalized.contains("longitude") || normalized.contains("lng") || normalized.contains("lon"))) {
            longitudeIndex = i
            remaining--
        }
        if (contractorIndex < 0 && (normalized.contains("nha thau") || normalized.contains("contractor") || normalized.contains("don vi thi cong") || normalized.contains("doi thi cong"))) {
            contractorIndex = i
            remaining--
        }
        if (objectTypeIndex < 0 && (normalized.contains("loai doi tuong") || normalized.contains("doi tuong") || normalized.contains("object type") || normalized.contains("object") || normalized.contains("phan loai"))) {
            objectTypeIndex = i
            remaining--
        }
        if (remaining == 0) break
    }
    if (positionIndex < 0) positionIndex = fallbackCodeIndex

    if (positionIndex < 0 || (coordinateIndex < 0 && (latitudeIndex < 0 || longitudeIndex < 0))) return null

    val structuralByIndex = BooleanArray(headers.size)
    structuralByIndex[positionIndex] = true
    if (coordinateIndex >= 0) structuralByIndex[coordinateIndex] = true
    if (latitudeIndex >= 0) structuralByIndex[latitudeIndex] = true
    if (longitudeIndex >= 0) structuralByIndex[longitudeIndex] = true
    if (contractorIndex >= 0) structuralByIndex[contractorIndex] = true
    if (objectTypeIndex >= 0) structuralByIndex[objectTypeIndex] = true

    val itemColumns = ArrayList<String>(headers.size)
    for (index in headers.indices) {
        if (structuralByIndex[index]) continue
        if (
            isLikelyItemColumn(
                normalizedHeader = normalizedHeaders[index],
                numericHint = sampleNumericHintByIndex[index]
            )
        ) {
            itemColumns.add(headers[index])
        }
    }

    var confidence = 45
    if (positionIndex >= 0) confidence += 20
    if (coordinateIndex >= 0) confidence += 25
    if (latitudeIndex >= 0 && longitudeIndex >= 0) confidence += 20
    if (contractorIndex >= 0) confidence += 5
    if (objectTypeIndex >= 0) confidence += 5
    if (itemColumns.isNotEmpty()) confidence += 5
    if (positionIndex >= 0 && coordinateIndex < 0 && (latitudeIndex < 0 || longitudeIndex < 0)) {
        confidence = minOf(confidence, 50)
    }
    confidence = confidence.coerceIn(0, 100)

    return ExcelMappingSuggestion(
        mapping = ExcelColumnMapping(
            positionColumn = headers[positionIndex],
            coordinateColumn = headers.getOrNull(coordinateIndex),
            latitudeColumn = headers.getOrNull(latitudeIndex),
            longitudeColumn = headers.getOrNull(longitudeIndex),
            contractorColumn = headers.getOrNull(contractorIndex),
            mapNumberColumn = headers.getOrNull(positionIndex),
            objectTypeColumn = headers.getOrNull(objectTypeIndex),
            itemColumns = itemColumns
        ),
        confidence = confidence
    )
}

fun parseObjectKind(value: String): ObjectKind {
    val normalized = normalizeText(value)
    return when {
        normalized.contains("route") || normalized.contains("line") || normalized.contains("tuyen") -> ObjectKind.ROUTE
        normalized.contains("node") || normalized.contains("nut") || normalized.contains("diem") || normalized.contains("point") -> ObjectKind.NODE
        else -> ObjectKind.AUTO
    }
}

fun isLikelyItemColumn(normalizedHeader: String, numericHint: Boolean): Boolean {
    val keywordMatch = ITEM_COLUMN_KEYWORDS.any { normalizedHeader.contains(it) }
    if (keywordMatch) return true
    return numericHint
}

fun normalizeText(value: String): String {
    val stripped = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
    return stripped
        .replace("đ", "d")
        .replace(NON_ALNUM_SPACE_REGEX, " ")
        .trim()
        .replace(MULTI_SPACE_REGEX, " ")
}

fun readSharedStrings(zip: ZipFile): List<String> {
    val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
    val values = ArrayList<String>(1024)
    val parser = xmlPullParserFactory.newPullParser()
    zip.getInputStream(entry).use { input ->
        parser.setInput(InputStreamReader(input, Charsets.UTF_8))
        var eventType = parser.eventType
        var inStringItem = false
        var inTextNode = false
        var current = StringBuilder(32)

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            inStringItem = true
                            current = StringBuilder(32)
                        }
                        "t" -> if (inStringItem) inTextNode = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inStringItem && inTextNode) {
                        current.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inTextNode = false
                        "si" -> {
                            inStringItem = false
                            values += current.toString()
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }
    return values
}

fun parseXml(input: InputStream): org.w3c.dom.Document =
    documentBuilderFactory.newDocumentBuilder().parse(input)

fun resolveCellValue(
    sharedString: Boolean,
    inlineString: Boolean,
    value: StringBuilder,
    inline: StringBuilder,
    sharedStrings: List<String>
): String {
    return when {
        sharedString -> parsePositiveInt(value)?.let { idx -> sharedStrings.getOrNull(idx).orEmpty() }.orEmpty()
        inlineString -> inline.toString()
        else -> value.toString()
    }.trim()
}

fun parsePositiveInt(builder: StringBuilder): Int? {
    if (builder.isEmpty()) return null
    var value = 0
    for (i in 0 until builder.length) {
        val ch = builder[i]
        if (ch !in '0'..'9') return null
        value = (value * 10) + (ch.code - '0'.code)
    }
    return value
}

fun parsePositiveInt(text: String): Int? {
    if (text.isEmpty()) return null
    var value = 0
    for (i in text.indices) {
        val ch = text[i]
        if (ch !in '0'..'9') return null
        value = (value * 10) + (ch.code - '0'.code)
    }
    return value
}

fun colIndexFromRef(ref: String, start: Int = 0, endExclusive: Int = ref.length): Int {
    var result = 0
    var i = start
    var hasLetter = false
    while (i < endExclusive) {
        val ch = ref[i]
        val normalized = when {
            ch in 'A'..'Z' -> ch
            ch in 'a'..'z' -> (ch.code - 32).toChar()
            else -> break
        }
        hasLetter = true
        result = result * 26 + (normalized.code - 'A'.code + 1)
        i++
    }
    if (!hasLetter) return 0
    return (result - 1).coerceAtLeast(0)
}

fun rowIndexFromRef(ref: String, start: Int = 0, endExclusive: Int = ref.length): Int {
    var i = start
    while (i < endExclusive && ref[i].isLetter()) i++
    if (i >= endExclusive) return -1
    var value = 0
    var hasDigit = false
    while (i < endExclusive) {
        val ch = ref[i]
        if (!ch.isDigit()) break
        hasDigit = true
        value = value * 10 + (ch.code - '0'.code)
        i++
    }
    if (!hasDigit) return -1
    return value - 1
}

fun parseCoordinatesRobust(coordStr: String): List<Pair<Double, Double>> {
    val coords = mutableListOf<Pair<Double, Double>>()
    val normalized = coordStr.trim()
    if (normalized.isBlank()) return coords
    // Fast-path for the most common form "lat,lon" to avoid regex-heavy parsing.
    if (!normalized.contains(';') && !normalized.contains('\n') && !normalized.contains('\r')) {
        parseSingleCoordinatePairFast(normalized)?.let { pair ->
            coords += pair
            return coords
        }
    }

    var segmentStart = 0
    val length = normalized.length
    for (i in 0..length) {
        val isBoundary = i == length || normalized[i] == ';' || normalized[i] == '\n' || normalized[i] == '\r'
        if (!isBoundary) continue
        if (i > segmentStart) {
            val segment = normalized.substring(segmentStart, i).trim()
            if (segment.isNotEmpty()) {
                parseSingleCoordinatePairFast(segment)?.let { coords.add(it) }
            }
        }
        segmentStart = i + 1
    }

    if (coords.isEmpty() && normalized.contains(",")) {
        val firstSpace = normalized.indexOf(' ')
        if (firstSpace > 0 && firstSpace < normalized.length - 1) {
            val raw1 = normalized.substring(0, firstSpace).trim()
            val raw2 = normalized.substring(firstSpace + 1).trim()
            val p1 = if (raw1.indexOf(',') >= 0) raw1.replace(',', '.').toDoubleOrNull() else raw1.toDoubleOrNull()
            val p2 = if (raw2.indexOf(',') >= 0) raw2.replace(',', '.').toDoubleOrNull() else raw2.toDoubleOrNull()
            if (p1 != null && p2 != null) {
                cleanAndValidatePair(p1, p2)?.let { coords.add(it) }
            }
        }
    }
    return coords
}

fun parseSingleCoordinatePairFast(value: String): Pair<Double, Double>? {
    val commaIdx = value.indexOf(',')
    if (commaIdx > 0 && commaIdx < value.length - 1) {
        val p1 = value.substring(0, commaIdx).trim().toDoubleOrNull()
        val p2 = value.substring(commaIdx + 1).trim().toDoubleOrNull()
        if (p1 != null && p2 != null) return cleanAndValidatePair(p1, p2)
    }
    val slashIdx = value.indexOf('/')
    if (slashIdx > 0 && slashIdx < value.length - 1) {
        val raw1 = value.substring(0, slashIdx).trim()
        val raw2 = value.substring(slashIdx + 1).trim()
        val p1 = if (raw1.indexOf(',') >= 0) raw1.replace(',', '.').toDoubleOrNull() else raw1.toDoubleOrNull()
        val p2 = if (raw2.indexOf(',') >= 0) raw2.replace(',', '.').toDoubleOrNull() else raw2.toDoubleOrNull()
        if (p1 != null && p2 != null) return cleanAndValidatePair(p1, p2)
    }
    val spaceIdx = value.indexOf(' ')
    if (spaceIdx > 0 && spaceIdx < value.length - 1) {
        val raw1 = value.substring(0, spaceIdx).trim()
        val raw2 = value.substring(spaceIdx + 1).trim()
        val p1 = if (raw1.indexOf(',') >= 0) raw1.replace(',', '.').toDoubleOrNull() else raw1.toDoubleOrNull()
        val p2 = if (raw2.indexOf(',') >= 0) raw2.replace(',', '.').toDoubleOrNull() else raw2.toDoubleOrNull()
        if (p1 != null && p2 != null) return cleanAndValidatePair(p1, p2)
    }

    // Slow-path without building intermediate lists from split/map/filter.
    val firstComma = value.indexOf(',')
    if (firstComma <= 0 || firstComma >= value.length - 1) return null
    if (value.indexOf(',', firstComma + 1) >= 0) return null
    val left = value.substring(0, firstComma).trim()
    val right = value.substring(firstComma + 1).trim()
    if (left.isEmpty() || right.isEmpty()) return null
    val p1 = left.toDoubleOrNull() ?: return null
    val p2 = right.toDoubleOrNull() ?: return null
    return cleanAndValidatePair(p1, p2)
}

fun cleanAndValidatePair(p1: Double, p2: Double): Pair<Double, Double>? {
    if (p1 in -90.0..90.0 && p2 in -180.0..180.0) return p1 to p2
    if (p2 in -90.0..90.0 && p1 in -180.0..180.0) return p2 to p1
    return null
}

fun isCoordinateCandidate(value: String): Boolean {
    if (value.length < 3) return false
    var hasDigit = false
    var hasSeparator = false
    for (ch in value) {
        if (ch.isDigit()) hasDigit = true
        if (ch == ',' || ch == ';' || ch == '/' || ch == ' ') hasSeparator = true
        if (hasDigit && hasSeparator) return true
    }
    return false
}
