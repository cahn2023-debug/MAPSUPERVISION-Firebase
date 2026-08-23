package com.mapsupervision.storage.importer

import com.mapsupervision.core.logging.AppLogger
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

data class TabularTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val sheetName: String = "",
    val detectedDelimiter: Char? = null,
    val detectedEncoding: String = "UTF-8"
)

object TabularReader {

    private val WINDOWS_1258: Charset by lazy {
        val candidates = listOf("windows-1258", "Cp1258", "x-windows-1258", "CP1258", "windows-1252", "ISO-8859-1")
        for (name in candidates) {
            try {
                if (Charset.isSupported(name)) {
                    return@lazy Charset.forName(name)
                }
            } catch (_: Throwable) {}
        }
        Charsets.UTF_8
    }

    fun listSheets(file: File, fileType: String): List<String> {
        val ext = fileType.lowercase(Locale.US).removePrefix(".")
        return when (ext) {
            "xlsx" -> listXlsxSheets(file)
            "xls" -> listXlsSheets(file)
            "csv" -> listOf("Sheet1")
            else -> emptyList()
        }
    }

    fun readTable(file: File, sheetName: String? = null): TabularTable {
        val ext = file.extension.lowercase(Locale.US)
        return when (ext) {
            "xlsx" -> readXlsx(file, sheetName)
            "xls" -> readXls(file, sheetName)
            "csv" -> readCsv(file)
            else -> throw IllegalArgumentException("E_PARSE: unsupported tabular file extension .$ext")
        }
    }

    fun readTableFromBytes(bytes: ByteArray, fileType: String, sheetName: String? = null): TabularTable {
        val ext = fileType.lowercase(Locale.US).removePrefix(".")
        return when (ext) {
            "xlsx" -> {
                val temp = File.createTempFile("tabular_", ".xlsx")
                try {
                    temp.writeBytes(bytes)
                    readXlsx(temp, sheetName)
                } finally {
                    temp.delete()
                }
            }
            "xls" -> readXlsFromBytes(bytes, sheetName)
            "csv" -> readCsvFromBytes(bytes)
            else -> throw IllegalArgumentException("E_PARSE: unsupported tabular format .$ext")
        }
    }

    private val SHEET_NAME_REGEX = Regex("<sheet[^>]*name=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun listXlsxSheets(file: File): List<String> {
        val sheets = ArrayList<String>()
        try {
            ZipFile(file).use { zip ->
                var entry = zip.getEntry("xl/workbook.xml")
                if (entry == null) {
                    val enumeration = zip.entries()
                    while (enumeration.hasMoreElements()) {
                        val e = enumeration.nextElement()
                        if (e.name.equals("xl/workbook.xml", ignoreCase = true) || e.name.endsWith("workbook.xml", ignoreCase = true)) {
                            entry = e
                            break
                        }
                    }
                }
                if (entry == null) return emptyList()
                val xmlContent = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val matches = SHEET_NAME_REGEX.findAll(xmlContent)
                for (match in matches) {
                    val name = match.groupValues.getOrNull(1)?.trim()
                    if (!name.isNullOrBlank()) {
                        sheets.add(name)
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(t, "listXlsxSheets failed")
        }
        return sheets
    }

    fun readXlsx(file: File, sheetName: String? = null): TabularTable {
        val rawTable = readXlsxTable(file, sheetName)
        val sheets = listXlsxSheets(file)
        val selectedSheet = sheetName ?: sheets.firstOrNull().orEmpty()
        return TabularTable(
            headers = rawTable.headers,
            rows = rawTable.rows,
            sheetName = selectedSheet,
            detectedEncoding = "UTF-8"
        )
    }

    // ==========================================
    // CSV Handling (Sniffing Delimiter & Encoding)
    // ==========================================

    fun readCsv(file: File): TabularTable {
        val bytes = file.readBytes()
        return readCsvFromBytes(bytes)
    }

    fun readCsvFromBytes(bytes: ByteArray): TabularTable {
        val encodingInfo = detectEncoding(bytes)
        val text = Normalizer.normalize(encodingInfo.text, Normalizer.Form.NFC)
        val delimiter = detectDelimiter(text)
        val rawRows = parseCsvRows(text, delimiter)
        if (rawRows.isEmpty()) {
            return TabularTable(
                headers = emptyList(),
                rows = emptyList(),
                sheetName = "Sheet1",
                detectedDelimiter = delimiter,
                detectedEncoding = encodingInfo.charset.name()
            )
        }

        val headerRange = detectHeaderRowRange(rawRows)
        val headers = flattenHeaderRows(rawRows, headerRange)
        val dataStart = headerRange.last + 1
        val expectedCols = headers.size
        val dataRows = ArrayList<List<String>>((rawRows.size - dataStart).coerceAtLeast(0))

        for (i in dataStart until rawRows.size) {
            val row = rawRows[i]
            if (row.all { it.isBlank() }) continue
            val adjusted = if (row.size == expectedCols) {
                row
            } else {
                val list = ArrayList<String>(expectedCols)
                val copyCount = minOf(row.size, expectedCols)
                for (c in 0 until copyCount) list.add(row[c])
                while (list.size < expectedCols) list.add("")
                list
            }
            dataRows.add(adjusted)
        }

        return TabularTable(
            headers = headers,
            rows = dataRows,
            sheetName = "Sheet1",
            detectedDelimiter = delimiter,
            detectedEncoding = encodingInfo.charset.name()
        )
    }

    data class EncodingResult(val text: String, val charset: Charset)

    fun detectEncoding(bytes: ByteArray): EncodingResult {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            return EncodingResult(text, Charsets.UTF_8)
        }

        // Test if bytes are strictly valid UTF-8
        var isValidUtf8 = true
        var i = 0
        val len = bytes.size
        var multiByteCount = 0

        while (i < len) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= len || (bytes[i + 1].toInt() and 0xC0) != 0x80) {
                        isValidUtf8 = false
                        break
                    }
                    multiByteCount++
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= len ||
                        (bytes[i + 1].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 2].toInt() and 0xC0) != 0x80
                    ) {
                        isValidUtf8 = false
                        break
                    }
                    multiByteCount++
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= len ||
                        (bytes[i + 1].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 2].toInt() and 0xC0) != 0x80 ||
                        (bytes[i + 3].toInt() and 0xC0) != 0x80
                    ) {
                        isValidUtf8 = false
                        break
                    }
                    multiByteCount++
                    i += 4
                }
                else -> {
                    isValidUtf8 = false
                    break
                }
            }
        }

        if (!isValidUtf8) {
            val winText = String(bytes, WINDOWS_1258)
            return EncodingResult(winText, WINDOWS_1258)
        }

        val utf8Text = String(bytes, Charsets.UTF_8)
        if (utf8Text.contains('\uFFFD')) {
            val winText = String(bytes, WINDOWS_1258)
            return EncodingResult(winText, WINDOWS_1258)
        }

        return EncodingResult(utf8Text, Charsets.UTF_8)
    }

    fun detectDelimiter(text: String): Char {
        val candidates = charArrayOf(',', ';', '\t')
        val sampleLines = text.lineSequence().take(20).filter { it.isNotBlank() }.toList()
        if (sampleLines.isEmpty()) return ','

        var bestChar = ','
        var bestScore = -1.0

        for (delim in candidates) {
            var totalCount = 0
            val countList = ArrayList<Int>(sampleLines.size)
            for (line in sampleLines) {
                var inQuote = false
                var count = 0
                for (ch in line) {
                    if (ch == '"') inQuote = !inQuote
                    else if (ch == delim && !inQuote) count++
                }
                if (count > 0) {
                    countList.add(count)
                    totalCount += count
                }
            }
            if (countList.isNotEmpty()) {
                val avg = totalCount.toDouble() / countList.size
                var variance = 0.0
                for (c in countList) {
                    val diff = c - avg
                    variance += diff * diff
                }
                variance /= countList.size
                val score = (avg / (1.0 + variance)) * (countList.size.toDouble() / sampleLines.size)
                if (score > bestScore) {
                    bestScore = score
                    bestChar = delim
                }
            }
        }
        return bestChar
    }

    fun parseCsvRows(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var currentRow = ArrayList<String>()
        val currentField = StringBuilder(32)
        var inQuotes = false
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && text[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                        if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
                        currentRow = ArrayList()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                        if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
                        currentRow = ArrayList()
                    }
                    else -> currentField.append(ch)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
        }

        return rows
    }

    // ==========================================
    // Header Detection & Note Skipping
    // ==========================================

    private val KNOWN_HEADER_KEYWORDS = listOf(
        "stt", "vi tri", "node", "nut", "tuyen", "line", "route", "ten", "name",
        "ma", "code", "toa do", "coord", "gps", "vi do", "lat", "kinh do", "lng", "lon",
        "nha thau", "contractor", "doi", "don vi", "loai", "type", "so ban do", "map",
        "ip", "subnet", "gateway", "tin hieu", "signal", "core", "soi", "chieu dai",
        "khoi luong", "vat tu", "cong viec", "ghi chu", "note", "description"
    )

    fun detectHeaderRowRange(rows: List<List<String>>): IntRange {
        if (rows.isEmpty()) return 0..0
        val maxScan = minOf(12, rows.size)
        var bestIndex = 0
        var bestScore = -100.0

        for (idx in 0 until maxScan) {
            val row = rows[idx]
            val nonBlank = row.filter { it.isNotBlank() }
            if (nonBlank.isEmpty()) continue

            var keywordMatches = 0
            var numericCount = 0
            var coordinateCount = 0

            for (cell in nonBlank) {
                val normalized = normalizeText(cell)
                if (KNOWN_HEADER_KEYWORDS.any { normalized.contains(it) }) {
                    keywordMatches++
                }
                if (cell.toDoubleOrNull() != null || cell.toPlainNumberOrNull() != null) {
                    numericCount++
                }
                if (isCoordinateCandidate(cell) && parseCoordinatesRobust(cell).isNotEmpty()) {
                    coordinateCount++
                }
            }

            var score = (nonBlank.size * 2.0) + (keywordMatches * 6.0) - (numericCount * 8.0) - (coordinateCount * 15.0)
            if (nonBlank.size <= 1) score -= 20.0

            if (score > bestScore) {
                bestScore = score
                bestIndex = idx
            }
        }

        // Check if next row is truly a sub-header (e.g. parent row has gaps/merges and next row has lat/lon subheaders)
        if (bestIndex + 1 < rows.size) {
            val nextRow = rows[bestIndex + 1]
            val nextNonBlank = nextRow.filter { it.isNotBlank() }
            val nextNumeric = nextNonBlank.count { it.toDoubleOrNull() != null || it.toPlainNumberOrNull() != null }
            val nextCoords = nextNonBlank.count { isCoordinateCandidate(it) && parseCoordinatesRobust(it).isNotEmpty() }

            if (nextNumeric == 0 && nextCoords == 0 && nextNonBlank.size >= 2) {
                val nextSubHeaderKeywords = listOf("vi do", "kinh do", "lat", "lon", "lng", "x", "y", "ten", "ma", "chieu dai", "don vi", "so luong")
                val nextMatches = nextNonBlank.count { cell ->
                    val norm = normalizeText(cell)
                    nextSubHeaderKeywords.any { norm.contains(it) }
                }
                val firstHasGaps = rows[bestIndex].count { it.isBlank() } > 0
                if (firstHasGaps && nextMatches >= 2) {
                    return bestIndex..(bestIndex + 1)
                }
            }
        }

        return bestIndex..bestIndex
    }

    fun flattenHeaderRows(rows: List<List<String>>, range: IntRange): List<String> {
        val headerRows = rows.subList(range.first, minOf(range.last + 1, rows.size))
        if (headerRows.isEmpty()) return emptyList()
        val maxCols = headerRows.maxOfOrNull { it.size } ?: 0
        val used = HashMap<String, Int>(maxCols * 2)
        val headers = ArrayList<String>(maxCols)

        for (colIndex in 0 until maxCols) {
            val parts = ArrayList<String>(headerRows.size)
            var lastPart = ""
            for (row in headerRows) {
                val raw = row.getOrNull(colIndex).orEmpty().trim()
                if (raw.isNotEmpty() && raw != lastPart) {
                    parts.add(raw)
                    lastPart = raw
                }
            }
            val base = if (parts.isEmpty()) "Column ${colIndex + 1}" else parts.joinToString(" > ")
            val count = (used[base] ?: 0) + 1
            used[base] = count
            headers.add(if (count == 1) base else "$base ($count)")
        }
        return headers
    }

    // ==========================================
    // XLS (BIFF8) Native Reader
    // ==========================================

    fun listXlsSheets(file: File): List<String> {
        return runCatching {
            val bytes = file.readBytes()
            val workbookBytes = extractWorkbookStream(bytes)
            readXlsSheetNames(workbookBytes)
        }.getOrDefault(listOf("Sheet1"))
    }

    fun readXls(file: File, sheetName: String? = null): TabularTable {
        val bytes = file.readBytes()
        return readXlsFromBytes(bytes, sheetName)
    }

    fun readXlsFromBytes(bytes: ByteArray, sheetName: String? = null): TabularTable {
        val workbookBytes = extractWorkbookStream(bytes)
        val parsed = parseBiff8Workbook(workbookBytes, sheetName)
        if (parsed.rows.isEmpty()) {
            return TabularTable(
                headers = emptyList(),
                rows = emptyList(),
                sheetName = parsed.sheetName,
                detectedEncoding = "BIFF8-Unicode"
            )
        }

        val headerRange = detectHeaderRowRange(parsed.rows)
        val headers = flattenHeaderRows(parsed.rows, headerRange)
        val dataStart = headerRange.last + 1
        val expectedCols = headers.size
        val dataRows = ArrayList<List<String>>((parsed.rows.size - dataStart).coerceAtLeast(0))

        for (i in dataStart until parsed.rows.size) {
            val row = parsed.rows[i]
            if (row.all { it.isBlank() }) continue
            val adjusted = if (row.size == expectedCols) {
                row
            } else {
                val list = ArrayList<String>(expectedCols)
                val copyCount = minOf(row.size, expectedCols)
                for (c in 0 until copyCount) list.add(row[c])
                while (list.size < expectedCols) list.add("")
                list
            }
            dataRows.add(adjusted)
        }

        return TabularTable(
            headers = headers,
            rows = dataRows,
            sheetName = parsed.sheetName,
            detectedEncoding = "BIFF8-Unicode"
        )
    }

    data class Biff8ParsedSheet(
        val sheetName: String,
        val rows: List<List<String>>
    )

    private fun extractWorkbookStream(bytes: ByteArray): ByteArray {
        if (bytes.size < 512 ||
            bytes[0] != 0xD0.toByte() || bytes[1] != 0xCF.toByte() ||
            bytes[2] != 0x11.toByte() || bytes[3] != 0xE0.toByte() ||
            bytes[4] != 0xA1.toByte() || bytes[5] != 0xB1.toByte() ||
            bytes[6] != 0x1A.toByte() || bytes[7] != 0xE1.toByte()
        ) {
            return bytes
        }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sectorSizePower = bb.getShort(30).toInt()
        val sectorSize = 1 shl sectorSizePower
        val fatSectorsCount = bb.getInt(44)
        val dirFirstSector = bb.getInt(48)

        val fatSectorIds = IntArray(fatSectorsCount)
        var fatIdx = 0
        for (k in 0 until minOf(109, fatSectorsCount)) {
            fatSectorIds[fatIdx++] = bb.getInt(76 + k * 4)
        }

        val entriesPerSector = sectorSize / 4
        val fatTable = IntArray(fatSectorsCount * entriesPerSector)
        var fatOffset = 0
        for (sectorId in fatSectorIds) {
            if (sectorId < 0) continue
            val offset = (sectorId + 1) * sectorSize
            if (offset + sectorSize <= bytes.size) {
                bb.position(offset)
                for (j in 0 until entriesPerSector) {
                    fatTable[fatOffset++] = bb.getInt()
                }
            }
        }

        val dirEntriesBytes = readSectorChain(bytes, dirFirstSector, sectorSize, fatTable)
        val dirBb = ByteBuffer.wrap(dirEntriesBytes).order(ByteOrder.LITTLE_ENDIAN)
        val numDirEntries = dirEntriesBytes.size / 128

        var workbookStartSector = -1
        var workbookStreamSize = 0

        for (e in 0 until numDirEntries) {
            val entryOffset = e * 128
            dirBb.position(entryOffset)
            val nameChars = CharArray(32)
            for (c in 0 until 32) nameChars[c] = dirBb.getChar()
            val nameLen = dirBb.getShort(entryOffset + 64).toInt() / 2
            val name = if (nameLen > 1) String(nameChars, 0, nameLen - 1) else ""
            val startSector = dirBb.getInt(entryOffset + 116)
            val streamSize = dirBb.getInt(entryOffset + 120)

            if (name.equals("Workbook", ignoreCase = true) || name.equals("Book", ignoreCase = true)) {
                workbookStartSector = startSector
                workbookStreamSize = streamSize
                break
            }
        }

        if (workbookStartSector < 0) return bytes

        val fullWorkbookStream = readSectorChain(bytes, workbookStartSector, sectorSize, fatTable)
        return if (workbookStreamSize > 0 && workbookStreamSize < fullWorkbookStream.size) {
            fullWorkbookStream.copyOf(workbookStreamSize)
        } else {
            fullWorkbookStream
        }
    }

    private fun readSectorChain(fileBytes: ByteArray, startSector: Int, sectorSize: Int, fat: IntArray): ByteArray {
        if (startSector < 0) return ByteArray(0)
        val sectors = ArrayList<Int>()
        var cur = startSector
        while (cur >= 0 && cur < fat.size && cur != 0xFFFFFFFE.toInt() && cur != 0xFFFFFFFF.toInt()) {
            sectors.add(cur)
            cur = fat[cur]
            if (sectors.size > 20000) break
        }

        val out = ByteArray(sectors.size * sectorSize)
        var outOffset = 0
        for (s in sectors) {
            val fileOffset = (s + 1) * sectorSize
            if (fileOffset + sectorSize <= fileBytes.size) {
                System.arraycopy(fileBytes, fileOffset, out, outOffset, sectorSize)
            }
            outOffset += sectorSize
        }
        return out
    }

    data class BiffSheetEntry(val name: String, val streamPos: Int)

    private fun readXlsSheetNames(bytes: ByteArray): List<String> {
        val sheets = ArrayList<String>()
        var pos = 0
        val len = bytes.size
        while (pos + 4 <= len) {
            val type = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            val recLen = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
            pos += 4
            if (pos + recLen > len) break

            if (type == 0x0085) { // BOUNDSHEET
                if (recLen >= 6) {
                    val nameLen = bytes[pos + 4].toInt() and 0xFF
                    val isUnicode = (bytes[pos + 5].toInt() and 0x01) != 0
                    val strPos = pos + 6
                    val sheetName = if (isUnicode && strPos + nameLen * 2 <= pos + recLen) {
                        val chars = CharArray(nameLen)
                        for (c in 0 until nameLen) {
                            val lo = bytes[strPos + c * 2].toInt() and 0xFF
                            val hi = bytes[strPos + c * 2 + 1].toInt() and 0xFF
                            chars[c] = ((hi shl 8) or lo).toChar()
                        }
                        String(chars)
                    } else if (!isUnicode && strPos + nameLen <= pos + recLen) {
                        String(bytes, strPos, nameLen, Charsets.ISO_8859_1)
                    } else "Sheet"
                    sheets.add(sheetName)
                }
            } else if (type == 0x000A) {
                if (sheets.isNotEmpty()) break
            }
            pos += recLen
        }
        return if (sheets.isEmpty()) listOf("Sheet1") else sheets
    }

    private fun parseBiff8Workbook(bytes: ByteArray, targetSheet: String?): Biff8ParsedSheet {
        val boundSheets = ArrayList<BiffSheetEntry>()
        val sst = ArrayList<String>()
        var pos = 0
        val len = bytes.size

        while (pos + 4 <= len) {
            val type = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            val recLen = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
            pos += 4
            if (pos + recLen > len) break

            when (type) {
                0x0085 -> { // BOUNDSHEET
                    if (recLen >= 6) {
                        val streamOffset = (bytes[pos].toInt() and 0xFF) or
                                ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                                ((bytes[pos + 3].toInt() and 0xFF) shl 24)
                        val nameLen = bytes[pos + 4].toInt() and 0xFF
                        val isUnicode = (bytes[pos + 5].toInt() and 0x01) != 0
                        val strPos = pos + 6
                        val sheetName = if (isUnicode && strPos + nameLen * 2 <= pos + recLen) {
                            val chars = CharArray(nameLen)
                            for (c in 0 until nameLen) {
                                val lo = bytes[strPos + c * 2].toInt() and 0xFF
                                val hi = bytes[strPos + c * 2 + 1].toInt() and 0xFF
                                chars[c] = ((hi shl 8) or lo).toChar()
                            }
                            String(chars)
                        } else if (!isUnicode && strPos + nameLen <= pos + recLen) {
                            String(bytes, strPos, nameLen, Charsets.ISO_8859_1)
                        } else "Sheet"
                        boundSheets.add(BiffSheetEntry(sheetName, streamOffset))
                    }
                }
                0x00FC -> { // SST
                    parseSst(bytes, pos, recLen, sst)
                }
            }
            pos += recLen
        }

        val chosenSheet = if (targetSheet != null) {
            boundSheets.firstOrNull { it.name.equals(targetSheet, ignoreCase = true) } ?: boundSheets.firstOrNull()
        } else {
            boundSheets.firstOrNull()
        }

        val chosenName = chosenSheet?.name ?: "Sheet1"
        val startOffset = chosenSheet?.streamPos ?: 0

        val rowMaps = HashMap<Int, MutableMap<Int, String>>()
        var maxCol = 0
        pos = if (startOffset > 0 && startOffset < bytes.size) startOffset else 0

        var inTargetSheet = false
        var seenSheetBof = false

        while (pos + 4 <= len) {
            val type = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            val recLen = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
            pos += 4
            if (pos + recLen > len) break

            when (type) {
                0x0809 -> { // BOF
                    val dt = if (recLen >= 2) (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) else 0
                    if (dt == 0x0010 || dt == 0x0005) {
                        if (startOffset <= 0 || pos >= startOffset) {
                            inTargetSheet = true
                            seenSheetBof = true
                        }
                    }
                }
                0x000A -> { // EOF
                    if (inTargetSheet && seenSheetBof) {
                        break
                    }
                }
                0x00FD -> { // LABELSST
                    if (recLen >= 10) {
                        val row = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                        val col = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
                        val sstIdx = (bytes[pos + 6].toInt() and 0xFF) or
                                ((bytes[pos + 7].toInt() and 0xFF) shl 8) or
                                ((bytes[pos + 8].toInt() and 0xFF) shl 16) or
                                ((bytes[pos + 9].toInt() and 0xFF) shl 24)
                        val value = sst.getOrNull(sstIdx).orEmpty().trim()
                        if (value.isNotEmpty()) {
                            val map = rowMaps.getOrPut(row) { HashMap() }
                            map[col] = value
                            if (col > maxCol) maxCol = col
                        }
                    }
                }
                0x0204 -> { // LABEL
                    if (recLen >= 8) {
                        val row = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                        val col = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
                        val strLen = (bytes[pos + 6].toInt() and 0xFF) or ((bytes[pos + 7].toInt() and 0xFF) shl 8)
                        val isUnicode = if (recLen >= 9) (bytes[pos + 8].toInt() and 0x01) != 0 else false
                        val strPos = pos + 9
                        val strVal = if (isUnicode && strPos + strLen * 2 <= pos + recLen) {
                            val chars = CharArray(strLen)
                            for (c in 0 until strLen) {
                                val lo = bytes[strPos + c * 2].toInt() and 0xFF
                                val hi = bytes[strPos + c * 2 + 1].toInt() and 0xFF
                                chars[c] = ((hi shl 8) or lo).toChar()
                            }
                            String(chars).trim()
                        } else if (!isUnicode && strPos + strLen <= pos + recLen) {
                            String(bytes, strPos, strLen, Charsets.ISO_8859_1).trim()
                        } else ""
                        if (strVal.isNotEmpty()) {
                            val map = rowMaps.getOrPut(row) { HashMap() }
                            map[col] = strVal
                            if (col > maxCol) maxCol = col
                        }
                    }
                }
                0x0203 -> { // NUMBER
                    if (recLen >= 14) {
                        val row = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                        val col = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
                        val bits = ByteBuffer.wrap(bytes, pos + 6, 8).order(ByteOrder.LITTLE_ENDIAN).long
                        val dbl = Double.fromBits(bits)
                        val formatted = formatDouble(dbl)
                        val map = rowMaps.getOrPut(row) { HashMap() }
                        map[col] = formatted
                        if (col > maxCol) maxCol = col
                    }
                }
                0x027E -> { // RK
                    if (recLen >= 10) {
                        val row = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                        val col = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
                        val rk = (bytes[pos + 6].toInt() and 0xFF) or
                                ((bytes[pos + 7].toInt() and 0xFF) shl 8) or
                                ((bytes[pos + 8].toInt() and 0xFF) shl 16) or
                                ((bytes[pos + 9].toInt() and 0xFF) shl 24)
                        val dbl = decodeRkNumber(rk)
                        val formatted = formatDouble(dbl)
                        val map = rowMaps.getOrPut(row) { HashMap() }
                        map[col] = formatted
                        if (col > maxCol) maxCol = col
                    }
                }
                0x00BD -> { // MULRK
                    if (recLen >= 6) {
                        val row = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                        val colFirst = (bytes[pos + 2].toInt() and 0xFF) or ((bytes[pos + 3].toInt() and 0xFF) shl 8)
                        val colLast = (bytes[pos + recLen - 2].toInt() and 0xFF) or ((bytes[pos + recLen - 1].toInt() and 0xFF) shl 8)
                        var rkOffset = pos + 4
                        for (c in colFirst..colLast) {
                            if (rkOffset + 6 <= pos + recLen) {
                                val rk = (bytes[rkOffset + 2].toInt() and 0xFF) or
                                        ((bytes[rkOffset + 3].toInt() and 0xFF) shl 8) or
                                        ((bytes[rkOffset + 4].toInt() and 0xFF) shl 16) or
                                        ((bytes[rkOffset + 5].toInt() and 0xFF) shl 24)
                                val dbl = decodeRkNumber(rk)
                                val formatted = formatDouble(dbl)
                                val map = rowMaps.getOrPut(row) { HashMap() }
                                map[c] = formatted
                                if (c > maxCol) maxCol = c
                                rkOffset += 6
                            }
                        }
                    }
                }
            }
            pos += recLen
        }

        if (rowMaps.isEmpty()) {
            return Biff8ParsedSheet(sheetName = chosenName, rows = emptyList())
        }

        val sortedRowKeys = rowMaps.keys.sorted()
        val denseRows = ArrayList<List<String>>(sortedRowKeys.size)
        for (r in sortedRowKeys) {
            val map = rowMaps[r] ?: continue
            val rowList = MutableList(maxCol + 1) { "" }
            for ((c, v) in map) {
                if (c in rowList.indices) rowList[c] = v
            }
            denseRows.add(rowList)
        }

        return Biff8ParsedSheet(sheetName = chosenName, rows = denseRows)
    }

    private fun parseSst(bytes: ByteArray, offset: Int, length: Int, sst: ArrayList<String>) {
        if (length < 8) return
        val uniqueCount = (bytes[offset + 4].toInt() and 0xFF) or
                ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 7].toInt() and 0xFF) shl 24)
        var p = offset + 8
        val end = offset + length
        var count = 0

        while (p < end && count < uniqueCount) {
            if (p + 3 > end) break
            val charCount = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
            val flags = bytes[p + 2].toInt() and 0xFF
            val isUnicode = (flags and 0x01) != 0
            val hasRich = (flags and 0x08) != 0
            val hasAsian = (flags and 0x04) != 0
            p += 3

            var richRuns = 0
            if (hasRich) {
                if (p + 2 <= end) {
                    richRuns = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
                    p += 2
                }
            }
            if (hasAsian) {
                if (p + 4 <= end) p += 4
            }

            val byteLen = if (isUnicode) charCount * 2 else charCount
            if (p + byteLen > end) {
                val availableChars = if (isUnicode) (end - p) / 2 else end - p
                if (availableChars > 0) {
                    val str = if (isUnicode) {
                        val chars = CharArray(availableChars)
                        for (c in 0 until availableChars) {
                            val lo = bytes[p + c * 2].toInt() and 0xFF
                            val hi = bytes[p + c * 2 + 1].toInt() and 0xFF
                            chars[c] = ((hi shl 8) or lo).toChar()
                        }
                        String(chars)
                    } else {
                        String(bytes, p, availableChars, Charsets.ISO_8859_1)
                    }
                    sst.add(str)
                } else {
                    sst.add("")
                }
                break
            }

            val str = if (isUnicode) {
                val chars = CharArray(charCount)
                for (c in 0 until charCount) {
                    val lo = bytes[p + c * 2].toInt() and 0xFF
                    val hi = bytes[p + c * 2 + 1].toInt() and 0xFF
                    chars[c] = ((hi shl 8) or lo).toChar()
                }
                String(chars)
            } else {
                String(bytes, p, charCount, Charsets.ISO_8859_1)
            }
            sst.add(str)
            p += byteLen
            p += richRuns * 4
            count++
        }
    }

    private fun decodeRkNumber(rk: Int): Double {
        val isInt = (rk and 0x02) != 0
        val is100 = (rk and 0x01) != 0
        var dbl = if (isInt) {
            (rk shr 2).toDouble()
        } else {
            val highBits = (rk and 0xFFFFFFFC.toInt()).toLong() shl 32
            Double.fromBits(highBits)
        }
        if (is100) dbl /= 100.0
        return dbl
    }

    private fun formatDouble(d: Double): String {
        return if (d == d.toLong().toDouble() && !d.isInfinite()) {
            d.toLong().toString()
        } else {
            "%.6f".format(Locale.US, d).trimEnd('0').trimEnd('.')
        }
    }
}
