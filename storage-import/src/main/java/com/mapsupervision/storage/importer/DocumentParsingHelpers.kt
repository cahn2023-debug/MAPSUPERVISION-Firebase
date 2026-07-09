package com.mapsupervision.storage.importer

import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile
fun parseDocx(file: File): String {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return "DOCX parsed: missing document.xml"
        val paragraphs = zip.getInputStream(entry).bufferedReader().use { reader ->
            countTokenInReader(reader, "<w:p")
        }
        return "DOCX parsed: $paragraphs paragraphs"
    }
}

fun parsePdf(file: File): String {
    val marker = "/Type /Page"
    val pages = InputStreamReader(file.inputStream(), Charsets.ISO_8859_1).use { reader ->
        val buffer = CharArray(8 * 1024)
        val markerChars = marker.toCharArray()
        var matchIndex = 0
        var count = 0
        while (true) {
            val read = reader.read(buffer)
            if (read <= 0) break
            for (i in 0 until read) {
                val ch = buffer[i]
                if (ch == markerChars[matchIndex]) {
                    matchIndex++
                    if (matchIndex == markerChars.size) {
                        count++
                        matchIndex = 0
                    }
                } else {
                    matchIndex = if (ch == markerChars[0]) 1 else 0
                }
            }
        }
        count.coerceAtLeast(1)
    }
    val sizeKb = file.length() / 1024
    return "PDF parsed: approx $pages pages, ${sizeKb}KB"
}

fun countTokenInReader(reader: java.io.Reader, token: String): Int {
    if (token.isEmpty()) return 0
    val tokenChars = token.toCharArray()
    val buffer = CharArray(8 * 1024)
    var matchIndex = 0
    var count = 0
    while (true) {
        val read = reader.read(buffer)
        if (read <= 0) break
        for (i in 0 until read) {
            val ch = buffer[i]
            if (ch == tokenChars[matchIndex]) {
                matchIndex++
                if (matchIndex == tokenChars.size) {
                    count++
                    matchIndex = 0
                }
            } else {
                matchIndex = if (ch == tokenChars[0]) 1 else 0
            }
        }
    }
    return count
}

