package com.mapsupervision.reporting.ui

import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportBuilderTest {
    @Test
    fun buildReportExportContent_withoutFilterKeepsWholeProjectData() {
        val content = buildReportExportContent(
            projectId = "project-1",
            filterContractor = null,
            photos = samplePhotos(),
            progress = sampleProgress(),
            workVolumeRowsRaw = sampleWorkVolumeRows(),
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            dailyLogs = sampleDailyLogs(),
            activeDraft = sampleDraft()
        )

        assertEquals("project-1", content.targetId)
        assertEquals(2, content.photos.size)
        assertEquals(2, content.dailyLogLines.size)
        assertEquals(8, content.lines.size)
        assertTrue(content.lines.any { it.contains("Summary") })
        assertTrue(content.lines.any { it.contains("Risk") })
        assertEquals(listOf("Cable", "Pipe", "Tổng"), content.workVolumeRows.map { it.workName })
        assertTrue(content.workVolumeRows.last().isTotal)
        assertEquals(30f, content.workVolumeRows[0].totalPlannedQty, 0.001f)
        assertEquals(20f, content.workVolumeRows[0].totalActualQty, 0.001f)
        assertEquals(66.666664f, content.workVolumeRows[0].completionPercent, 0.001f)
    }

    @Test
    fun buildReportExportContent_withFilterContractorAKeepsOnlyAData() {
        val content = buildReportExportContent(
            projectId = "project-1",
            filterContractor = "Contractor A",
            photos = samplePhotos(),
            progress = sampleProgress(),
            workVolumeRowsRaw = sampleWorkVolumeRows(),
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            dailyLogs = sampleDailyLogs(),
            activeDraft = sampleDraft()
        )

        assertEquals("project-1", content.targetId)
        assertEquals(2, content.photos.size)
        assertEquals(2, content.dailyLogLines.size)
        assertEquals(8, content.lines.size)
        assertTrue(content.lines.any { it.contains("Summary") })
        assertTrue(content.lines.any { it.contains("Risk") })
        assertEquals(listOf("Cable", "Tổng"), content.workVolumeRows.map { it.workName })
        assertEquals(10f, content.workVolumeRows[0].totalPlannedQty, 0.001f)
        assertEquals(6f, content.workVolumeRows[0].totalActualQty, 0.001f)
        assertEquals(60f, content.workVolumeRows[0].completionPercent, 0.001f)
        assertEquals(10f, content.workVolumeRows.last().totalPlannedQty, 0.001f)
        assertEquals(6f, content.workVolumeRows.last().totalActualQty, 0.001f)
        assertEquals(60f, content.workVolumeRows.last().completionPercent, 0.001f)
    }

    @Test
    fun buildReportExportContent_fromSnapshotUsesSnapshotData() {
        val snapshot = ReportingSnapshot(
            projectId = "project-1",
            projectName = "Project One",
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            photos = samplePhotos(),
            progress = sampleProgress(),
            workVolumeRowsRaw = sampleWorkVolumeRows(),
            workVolumeRows = buildMaterialReportRows(sampleNodes(), sampleRoutes(), sampleWorkVolumeRows()),
            dailyLogs = sampleDailyLogs()
        )

        val content = buildReportExportContent(
            snapshot = snapshot,
            filterContractor = "Contractor A",
            activeDraft = sampleDraft()
        )

        assertEquals("project-1", content.targetId)
        assertEquals(2, content.photos.size)
        assertEquals(2, content.dailyLogLines.size)
        assertEquals(8, content.lines.size)
        assertTrue(content.lines.first().startsWith("B"))
        assertEquals(listOf("Cable", "Tổng"), content.workVolumeRows.map { it.workName })
    }


    @Test
    fun buildMaterialReportRows_usesPlannedQtyFallbackWhenSummaryMissing() {
        val nodes = listOf(
            GisNode(
                id = "node-c-id",
                projectId = "project-1",
                code = "NODE-C",
                contractor = "Contractor C",
                latitude = 0.0,
                longitude = 0.0,
                workVolumeSummary = ""
            )
        )
        val rows = listOf(
            WorkVolumeProgress(
                id = "material-c",
                projectId = "project-1",
                nodeCode = "NODE-C",
                workName = "Clamp",
                plannedQty = 7f,
                actualQty = 3f,
                updatedAtEpochMs = 1_000L,
                unit = ""
            )
        )

        val materialRows = buildMaterialReportRows(nodes, emptyList(), rows, filterContractor = "Contractor C")

        assertEquals(listOf("Clamp", "Tổng"), materialRows.map { it.workName })
        assertEquals(7f, materialRows[0].totalPlannedQty, 0.001f)
        assertEquals(3f, materialRows[0].totalActualQty, 0.001f)
        assertEquals(42.857143f, materialRows[0].completionPercent, 0.001f)
        assertEquals(7f, materialRows.last().totalPlannedQty, 0.001f)
        assertEquals(3f, materialRows.last().totalActualQty, 0.001f)
    }

    @Test
    fun buildProjectTextSummary_countsWorkItemsUsesTotalRowAndLatestIssues() {
        val snapshot = ReportingSnapshot(
            projectId = "project-1",
            projectName = "Project One",
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            workVolumeRowsRaw = sampleWorkVolumeRows(),
            dailyLogs = listOf(
                DailyLog(
                    id = "log-1",
                    projectId = "project-1",
                    workItem = "Work A",
                    manpower = 3,
                    note = "Issue old",
                    createdAtEpochMs = 1_000L,
                    nodeCode = "NODE-A"
                ),
                DailyLog(
                    id = "log-2",
                    projectId = "project-1",
                    workItem = "Work B",
                    manpower = 2,
                    note = "Issue newest",
                    createdAtEpochMs = 3_000L,
                    routeCode = "ROUTE-1"
                ),
                DailyLog(
                    id = "log-3",
                    projectId = "project-1",
                    workItem = "Work C",
                    manpower = 2,
                    note = "Issue middle",
                    createdAtEpochMs = 2_000L
                ),
                DailyLog(
                    id = "log-4",
                    projectId = "project-1",
                    workItem = "Work D",
                    manpower = 1,
                    note = "",
                    createdAtEpochMs = 4_000L,
                    nodeCode = "NODE-D"
                )
            )
        )

        val summary = buildProjectTextSummary(snapshot)

        assertEquals("Project One", summary.projectLabel)
        assertEquals(2, summary.workItemCount)
        assertEquals(35f, summary.totalPlannedQty, 0.001f)
        assertEquals(22f, summary.totalActualQty, 0.001f)
        assertEquals(62.857143f, summary.completionPercent, 0.001f)
        assertEquals(
            listOf(
                "Work B (ROUTE-1): Issue newest",
                "Work C: Issue middle",
                "Work A (NODE-A): Issue old"
            ),
            summary.recentIssues
        )
    }

    @Test
    fun buildProjectTextSummary_returnsZerosAndEmptyIssuesWhenNoData() {
        val summary = buildProjectTextSummary(
            ReportingSnapshot(
                projectId = "project-1",
                projectName = "",
                nodes = emptyList(),
                routes = emptyList(),
                workVolumeRowsRaw = emptyList(),
                dailyLogs = emptyList()
            )
        )

        assertEquals("project-1", summary.projectLabel)
        assertEquals(0, summary.workItemCount)
        assertEquals(0f, summary.totalPlannedQty, 0.001f)
        assertEquals(0f, summary.totalActualQty, 0.001f)
        assertEquals(0f, summary.completionPercent, 0.001f)
        assertTrue(summary.recentIssues.isEmpty())
    }

    @Test
    fun buildRecentIssueSummaries_respectsLimitAndSkipsBlankNotes() {
        val issues = buildRecentIssueSummaries(
            dailyLogs = listOf(
                DailyLog("1", "p1", "A", 1, "Note A", 1_000L, nodeCode = "N1"),
                DailyLog("2", "p1", "B", 1, " ", 5_000L, nodeCode = "N2"),
                DailyLog("3", "p1", "C", 1, "Note C", 3_000L, routeCode = "R3"),
                DailyLog("4", "p1", "D", 1, "Note D", 4_000L, nodeCode = "N4")
            ),
            limit = 2
        )

        assertEquals(
            listOf(
                "D (N4): Note D",
                "C (R3): Note C"
            ),
            issues
        )
    }

    private fun sampleDraft() = ReportDraftResult(
        executiveSummary = "Summary",
        riskSection = "Risk",
        recommendedActions = listOf("Action 1")
    )

    private fun samplePhotos() = listOf(
        SitePhoto(
            id = "photo-a",
            projectId = "project-1",
            objectCode = "NODE-A",
            filePath = "/tmp/photo-a.jpg",
            thumbnailPath = "/tmp/photo-a-thumb.jpg",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = PhotoLocationStatus.OK,
            engineer = "Engineer",
            capturedAtEpochMs = 1_000L,
            matchedNodeCode = "NODE-A"
        ),
        SitePhoto(
            id = "photo-b",
            projectId = "project-1",
            objectCode = "NODE-B",
            filePath = "/tmp/photo-b.jpg",
            thumbnailPath = "/tmp/photo-b-thumb.jpg",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = PhotoLocationStatus.OK,
            engineer = "Engineer",
            capturedAtEpochMs = 2_000L,
            matchedNodeCode = "NODE-B"
        )
    )

    private fun sampleProgress() = listOf(
        NodeProgress(
            id = "progress-a",
            projectId = "project-1",
            nodeCode = "NODE-A",
            planned = 50f,
            actual = 40f,
            remain = 10f,
            delayed = true
        ),
        NodeProgress(
            id = "progress-b",
            projectId = "project-1",
            nodeCode = "NODE-B",
            planned = 60f,
            actual = 60f,
            remain = 0f,
            delayed = false
        )
    )

    private fun sampleWorkVolumeRows() = listOf(
        WorkVolumeProgress(
            id = "material-a",
            projectId = "project-1",
            nodeCode = "NODE-A",
            workName = "Cable",
            plannedQty = 10f,
            actualQty = 6f,
            updatedAtEpochMs = 1_000L,
            unit = ""
        ),
        WorkVolumeProgress(
            id = "material-b",
            projectId = "project-1",
            nodeCode = "NODE-B",
            workName = "Cable",
            plannedQty = 20f,
            actualQty = 14f,
            updatedAtEpochMs = 2_000L,
            unit = ""
        ),
        WorkVolumeProgress(
            id = "material-c",
            projectId = "project-1",
            nodeCode = "NODE-B",
            workName = "Pipe",
            plannedQty = 5f,
            actualQty = 2f,
            updatedAtEpochMs = 3_000L,
            unit = ""
        )
    )

    private fun sampleNodes() = listOf(
        GisNode(
            id = "node-a-id",
            projectId = "project-1",
            code = "NODE-A",
            contractor = "Contractor A",
            latitude = 0.0,
            longitude = 0.0,
            workVolumeSummary = "Cable: 10"
        ),
        GisNode(
            id = "node-b-id",
            projectId = "project-1",
            code = "NODE-B",
            contractor = "Contractor B",
            latitude = 0.0,
            longitude = 0.0,
            workVolumeSummary = "Cable: 20\nPipe: 5"
        )
    )

    private fun sampleRoutes() = listOf(
        GisRoute(
            id = "route-1",
            projectId = "project-1",
            code = "ROUTE-1",
            contractor = "Contractor A",
            startNodeCode = "NODE-A",
            endNodeCode = "NODE-B"
        )
    )

    private fun sampleDailyLogs() = listOf(
        DailyLog(
            id = "log-a",
            projectId = "project-1",
            workItem = "Work A",
            manpower = 3,
            note = "Note A",
            createdAtEpochMs = 1_000L,
            nodeCode = "NODE-A",
            volume = 1.0,
            unit = "m"
        ),
        DailyLog(
            id = "log-b",
            projectId = "project-1",
            workItem = "Work B",
            manpower = 2,
            note = "Note B",
            createdAtEpochMs = 2_000L,
            nodeCode = "NODE-B",
            volume = 2.0,
            unit = "m"
        )
    )
}




