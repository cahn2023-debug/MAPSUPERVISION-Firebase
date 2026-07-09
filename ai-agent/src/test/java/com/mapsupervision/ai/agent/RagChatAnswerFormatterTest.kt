package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.rag.RagChatAnswerFormatter
import com.mapsupervision.ai.core.rag.RagQueryDomain
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.WorkspaceSnapshot
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class RagChatAnswerFormatterTest {
    @Test
    fun `progress answer reports narrative and separate volume lines`() {
        val snapshot = WorkspaceSnapshot(
            projectId = "p1",
            designNodes = listOf(GisNode("n1", "p1", "HG01", "Alpha", 0.0, 0.0, "Ho ga 01", "Cap quang")),
            constructionProgress = listOf(NodeProgress("pr1", "p1", "HG01", planned = 90f, actual = 70f, remain = 30f, delayed = true)),
            workVolumeRows = listOf(
                WorkVolumeProgress("v1", "p1", "HG01", "Dao dat", plannedQty = 10f, actualQty = 5f, updatedAtEpochMs = 1L, unit = "m3"),
                WorkVolumeProgress("v2", "p1", "HG01", "Lap cap", plannedQty = 0f, actualQty = 2f, updatedAtEpochMs = 1L, unit = "m")
            )
        )

        val answer = RagChatAnswerFormatter.format("tiến độ HG01", snapshot, RagQueryDomain.PROGRESS, "HG01").orEmpty()

        assertTrue(answer.contains("Báo cáo tiến độ"))
        assertTrue(answer.contains("kế hoạch đạt 90.0%"))
        assertTrue(answer.contains("thi công thực tế đạt 70.0%"))
        assertTrue(answer.contains("Khối lượng tổng hợp"))
        assertTrue(answer.contains("Khối lượng thi công - Dao dat: 5 / 10 m3 - hoàn thành 50.0%."))
        assertTrue(answer.contains("Khối lượng thi công - Lap cap: 2 / 0 m - hoàn thành chưa xác định."))
    }

    @Test
    fun `planning answer filters matching plan`() {
        val today = LocalDate.now().toEpochDay()
        val snapshot = WorkspaceSnapshot(
            projectId = "p1",
            designNodes = listOf(GisNode("n1", "p1", "HG01", "Alpha", 0.0, 0.0, "Ho ga 01", "")),
            workPlans = listOf(
                WorkPlan("plan1", "p1", "Keo cap", "Keo cap tuyen", today, "HG01", null, null, "raw", 1L, 12.0, "m")
            )
        )

        val answer = RagChatAnswerFormatter.format("kế hoạch hôm nay HG01", snapshot, RagQueryDomain.PLANNING, "HG01").orEmpty()

        assertTrue(answer.contains("Kế hoạch thi công phù hợp"))
        assertTrue(answer.contains("Keo cap"))
        assertTrue(answer.contains("khối lượng 12 m"))
    }

    @Test
    fun `daily log answer reports no matching data`() {
        val snapshot = WorkspaceSnapshot(
            projectId = "p1",
            dailyLogs = listOf(
                DailyLog("log1", "p1", "Dao dat", 3, "Hoan thanh", 1L, nodeCode = "HG02", dateEpochDay = LocalDate.now().toEpochDay())
            )
        )

        val answer = RagChatAnswerFormatter.format("nhật ký hôm nay HG01", snapshot, RagQueryDomain.DAILY_LOG, "HG01").orEmpty()

        assertTrue(answer.contains("Chưa có dữ liệu phù hợp"))
    }
}
