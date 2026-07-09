package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.rag.RagDocumentType
import com.mapsupervision.ai.rag.RagDocumentBuilder
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.WorkspaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagDocumentBuilderTest {
    @Test
    fun `builds stable documents for snapshot entities`() {
        val snapshot = WorkspaceSnapshot(
            projectId = "p1",
            designNodes = listOf(
                GisNode(
                    id = "n1",
                    projectId = "p1",
                    code = "HG01",
                    contractor = "Alpha",
                    latitude = 10.0,
                    longitude = 20.0,
                    mapNumberLabel = "A1",
                    workVolumeSummary = "summary"
                )
            ),
            designRoutes = listOf(
                GisRoute(
                    id = "r1",
                    projectId = "p1",
                    code = "R01",
                    contractor = "Alpha",
                    startNodeCode = "HG01",
                    endNodeCode = "HG02"
                )
            ),
            constructionProgress = listOf(
                NodeProgress(id = "p1", projectId = "p1", nodeCode = "HG01", planned = 100f, actual = 80f, remain = 20f, delayed = false, updatedAtEpochMs = 123L)
            ),
            workVolumeRows = listOf(
                WorkVolumeProgress(id = "w1", projectId = "p1", nodeCode = "HG01", workName = "Cap quang", plannedQty = 10f, actualQty = 5f, updatedAtEpochMs = 456L, unit = "m")
            ),
            dailyLogs = listOf(
                DailyLog(
                    id = "l1",
                    projectId = "p1",
                    workItem = "Doi cap",
                    manpower = 3,
                    note = "Ghi nhat ky",
                    createdAtEpochMs = 789L,
                    weather = "Nang",
                    temperature = 32.0,
                    nodeCode = "HG01",
                    routeCode = "R01",
                    dateEpochDay = 1L,
                    volume = 1.5,
                    unit = "m",
                    categoryName = "Cap quang"
                )
            ),
            workCategories = listOf(
                WorkCategory(id = "c1", projectId = "p1", name = "Cap quang", unit = "m", createdAtEpochMs = 100L)
            ),
            workPlans = listOf(
                WorkPlan(
                    id = "plan1",
                    projectId = "p1",
                    title = "Keo cap",
                    description = "Keo cap tuyen R01",
                    plannedDateEpochDay = 1L,
                    nodeCode = "HG01",
                    routeCode = "R01",
                    taskId = null,
                    sourceRawInput = "plan",
                    createdAtEpochMs = 999L,
                    quantity = 2.0,
                    unit = "m"
                )
            )
        )

        val documents = RagDocumentBuilder.build(snapshot)

        assertEquals(7, documents.size)
        val nodeDocument = documents.first { it.docType == RagDocumentType.NODE }
        assertTrue(nodeDocument.text.contains("node_code=HG01"))
        assertTrue(nodeDocument.contentHash.isNotBlank())
        assertTrue(nodeDocument.id.startsWith("p1_node_"))
        val planDocument = documents.first { it.docType == RagDocumentType.WORK_PLAN }
        assertTrue(planDocument.text.contains("plannedDateEpochDay=1"))
        assertEquals("HG01", planDocument.sourceCode)
    }
}


