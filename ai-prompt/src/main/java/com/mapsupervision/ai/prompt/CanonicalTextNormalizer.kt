package com.mapsupervision.ai.prompt

import java.text.Normalizer
import java.util.Locale

object CanonicalTextNormalizer {
    private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

    fun normalizeCode(code: String): String {
        val length = code.length
        if (length == 0) return ""
        var start = 0
        while (start < length && code[start].isWhitespace()) start++
        if (start >= length) return ""
        var end = length
        while (end > start && code[end - 1].isWhitespace()) end--
        var hasUpper = false
        for (i in start until end) {
            if (code[i].isUpperCase()) {
                hasUpper = true
                break
            }
        }
        val text = if (!hasUpper) code.substring(start, end) else code.substring(start, end).lowercase(Locale.US)
        return normalizeAscii(text)
    }

    fun normalizeName(name: String): String {
        val trimmedLower = normalizeCode(name)
        if (trimmedLower.isEmpty()) return ""
        var simpleAscii = true
        for (i in trimmedLower.indices) {
            val ch = trimmedLower[i]
            if (!(ch in 'a'..'z' || ch in '0'..'9')) {
                simpleAscii = false
                break
            }
        }
        if (simpleAscii) return trimmedLower
        return normalizeAscii(trimmedLower)
    }

    fun normalizeSearchText(value: String): String {
        if (value.isBlank()) return ""
        val temp = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD)
        return temp.replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun normalizeKey(value: String): String {
        if (value.isBlank()) return ""
        val temp = Normalizer.normalize(value, Normalizer.Form.NFD)
        return temp.replace(COMBINING_MARKS_REGEX, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase(Locale.US)
            .replace(NON_ALNUM_REGEX, "")
    }

    private fun normalizeAscii(value: String): String {
        val temp = Normalizer.normalize(value, Normalizer.Form.NFD)
        return temp.replace(COMBINING_MARKS_REGEX, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .replace(NON_ALNUM_REGEX, "")
            .trim()
    }
}
