package com.mapsupervision.app.workspace

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.DailyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DailyLogRepository error handling logic used in WorkspaceViewModel.refresh().
 *
 * Tests the expression:
 *   (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data ?: emptyList()
 *
 * Requirements: 2.6
 */
class DailyLogRepositoryErrorHandlingTest {

    private fun loadDailyLogs(result: AppResult<List<DailyLog>>): List<DailyLog> {
        return (result as? AppResult.Success)?.data ?: emptyList()
    }

    @Test
    fun `when byProject returns Error, dailyLogs is emptyList`() {
        val result: AppResult<List<DailyLog>> = AppResult.Error(Exception("DB error"))
        val dailyLogs = loadDailyLogs(result)
        assertTrue(dailyLogs.isEmpty())
    }

    @Test
    fun `when byProject returns Success with data, dailyLogs contains the data`() {
        val logs = listOf(
            DailyLog(
                id = "1",
                projectId = "p1",
                workItem = "Đào móng",
                manpower = 5,
                note = "Hoàn thành",
                createdAtEpochMs = 1_000_000L
            )
        )
        val result: AppResult<List<DailyLog>> = AppResult.Success(logs)
        val dailyLogs = loadDailyLogs(result)
        assertEquals(1, dailyLogs.size)
        assertEquals("Đào móng", dailyLogs[0].workItem)
    }

    @Test
    fun `when byProject returns Success with empty list, dailyLogs is emptyList`() {
        val result: AppResult<List<DailyLog>> = AppResult.Success(emptyList())
        val dailyLogs = loadDailyLogs(result)
        assertTrue(dailyLogs.isEmpty())
    }

    @Test
    fun `loadDailyLogs does not throw when result is Error`() {
        val result: AppResult<List<DailyLog>> = AppResult.Error(RuntimeException("unexpected"))
        // Must not throw
        val dailyLogs = loadDailyLogs(result)
        assertEquals(emptyList<DailyLog>(), dailyLogs)
    }

    @Test
    fun `DailyLog keeps defaults for extended journal fields`() {
        val log = DailyLog(
            id = "1",
            projectId = "p1",
            workItem = "Thi cong",
            manpower = 3,
            note = "",
            createdAtEpochMs = 1_000L,
            routeCode = null,
            batchGroupId = "",
            appliedNodeCodesCsv = "",
            linkedPhotoIdsCsv = "",
            photoMatchOffsetMinutes = 0
        )

        assertEquals("", log.weather)
        assertEquals(0.0, log.temperature, 0.0)
        assertEquals(null, log.nodeCode)
        assertEquals(0L, log.dateEpochDay)
        assertEquals(0.0, log.volume, 0.0)
        assertEquals("", log.unit)
        assertEquals("", log.categoryName)
        assertEquals("", log.batchGroupId)
        assertEquals("", log.appliedNodeCodesCsv)
    }

    @Test
    fun `DailyLog is invalid when both nodeCode and routeCode are null`() {
        val log = DailyLog(
            id = "1",
            projectId = "p1",
            workItem = "Thi cong",
            manpower = 3,
            note = "",
            createdAtEpochMs = 1_000L,
            nodeCode = null,
            routeCode = null
        )
        val isValid = !log.nodeCode.isNullOrBlank() || !log.routeCode.isNullOrBlank()
        assertTrue(!isValid)
    }

    @Test
    fun `DailyLog is valid when nodeCode is present`() {
        val log = DailyLog(
            id = "1",
            projectId = "p1",
            workItem = "Thi cong",
            manpower = 3,
            note = "",
            createdAtEpochMs = 1_000L,
            nodeCode = "N1",
            routeCode = null
        )
        val isValid = !log.nodeCode.isNullOrBlank() || !log.routeCode.isNullOrBlank()
        assertTrue(isValid)
    }
}
