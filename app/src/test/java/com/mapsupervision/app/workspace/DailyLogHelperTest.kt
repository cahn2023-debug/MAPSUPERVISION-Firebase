package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.resolveEpochDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

class DailyLogHelperTest {

    @Test
    fun `resolveEpochDay returns dateEpochDay when it is not zero`() {
        val log = DailyLog(
            id = "log-1",
            projectId = "p-1",
            workItem = "Bê tông",
            manpower = 5,
            note = "",
            createdAtEpochMs = 1718000000000L,
            dateEpochDay = 20000L
        )
        assertEquals(20000L, log.resolveEpochDay())
    }

    @Test
    fun `resolveEpochDay falls back to createdAtEpochMs when dateEpochDay is zero`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 22, 12, 0, 0)
        }
        val expectedEpoch = LocalDate.of(2026, 6, 22).toEpochDay()
        
        val log = DailyLog(
            id = "log-2",
            projectId = "p-1",
            workItem = "Bê tông",
            manpower = 5,
            note = "",
            createdAtEpochMs = cal.timeInMillis,
            dateEpochDay = 0L
        )
        assertEquals(expectedEpoch, log.resolveEpochDay())
    }
}
