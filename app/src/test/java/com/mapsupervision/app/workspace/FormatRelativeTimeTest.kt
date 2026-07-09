package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for [formatRelativeTime].
 * All tests use a fixed `now` value for deterministic results.
 * Validates: Requirements 2.4
 */
class FormatRelativeTimeTest {

    // Fixed "now": 2024-06-15 10:00:00 UTC+7 (arbitrary, deterministic)
    private val fixedNow: Long = run {
        val cal = Calendar.getInstance()
        cal.set(2024, Calendar.JUNE, 15, 10, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    @Test
    fun `today timestamp returns Hom nay with HH_mm`() {
        // Same day as fixedNow, different time
        val todayCal = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = formatRelativeTime(todayCal.timeInMillis, fixedNow)
        assertTrue("Expected 'Hôm nay,' prefix", result.startsWith("Hôm nay,"))
        assertTrue("Expected time '14:30' in result", result.contains("14:30"))
    }

    @Test
    fun `yesterday timestamp returns Hom qua with HH_mm`() {
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = formatRelativeTime(yesterdayCal.timeInMillis, fixedNow)
        assertTrue("Expected 'Hôm qua,' prefix", result.startsWith("Hôm qua,"))
        assertTrue("Expected time '16:45' in result", result.contains("16:45"))
    }

    @Test
    fun `older timestamp returns dd_MM_yyyy HH_mm format`() {
        val oldCal = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 5, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = formatRelativeTime(oldCal.timeInMillis, fixedNow)
        assertEquals("05/01/2024 09:00", result)
    }
}
