package com.mapsupervision.ai.prompt

import com.mapsupervision.ai.core.*

import java.time.LocalDate
import java.util.Locale

object DailyLogDateResolver {
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun resolveEpochDay(message: String, explicitDate: String? = null, fallbackEpochDay: Long = currentEpochDay()): Long {
        explicitDate?.let { parseDateText(it)?.let { return it } }

        parseDateText(message)?.let { return it }

        val normalized = ChatDictionaryResolver.normalize(message)
        return when {
            normalized.contains("hom qua") -> currentEpochDay() - 1L
            normalized.contains("hom nay") -> currentEpochDay()
            else -> fallbackEpochDay
        }
    }

    fun formatEpochDay(epochDay: Long): String {
        if (epochDay <= 0L) return ""
        return String.format(
            Locale.US,
            "%02d/%02d/%04d",
            LocalDate.ofEpochDay(epochDay).dayOfMonth,
            LocalDate.ofEpochDay(epochDay).monthValue,
            LocalDate.ofEpochDay(epochDay).year
        )
    }

    fun parseDateText(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        val normalized = ChatDictionaryResolver.normalize(trimmed)
        if (normalized.contains("hom nay")) return currentEpochDay()
        if (normalized.contains("hom qua")) return currentEpochDay() - 1L

        Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""").find(trimmed)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val year = normalizeYear(match.groupValues[3].toIntOrNull() ?: return null)
            return toEpochDay(year, month, day)
        }

        Regex("""\b(\d{1,2})[/-](\d{1,2})\b""").find(trimmed)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val year = LocalDate.now().year
            return toEpochDay(year, month, day)
        }

        Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""").find(trimmed)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val day = match.groupValues[3].toIntOrNull() ?: return null
            return toEpochDay(year, month, day)
        }

        return null
    }

    private fun normalizeYear(year: Int): Int = if (year < 100) 2000 + year else year

    private fun currentEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun toEpochDay(year: Int, month: Int, day: Int): Long? {
        return runCatching { LocalDate.of(year, month, day).toEpochDay() }.getOrNull()
    }
}
