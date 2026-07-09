package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.WorkspaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

import com.mapsupervision.domain.model.MaterialHandover

class WorkspaceStateModelsTest {

    @Test
    fun `dedupeImportedFilesById keeps first occurrence per id`() {
        val files = listOf(
            ImportedFile("file-1", "project-1", "a.xlsx", "xlsx", "/tmp/a", "summary-a", 1L),
            ImportedFile("file-1", "project-1", "a-copy.xlsx", "xlsx", "/tmp/a2", "summary-b", 2L),
            ImportedFile("file-2", "project-1", "b.xlsx", "xlsx", "/tmp/b", "summary-c", 3L)
        )

        val deduped = dedupeImportedFilesById(files)

        assertEquals(2, deduped.size)
        assertEquals(listOf("file-1", "file-2"), deduped.map { it.id })
        assertEquals("a.xlsx", deduped.first().fileName)
    }

    @Test
    fun `template options prefer work category unit over blank material unit for same name`() {
        val state = WorkspaceState(
            activeProjectId = "p1",
            workVolumeRows = listOf(
                WorkVolumeProgress("m1", "p1", "N-1", "Thiết bị đo đếm lưu lượng", 100f, 10f, 1L, "")
            ),
            workCategories = listOf(
                WorkCategory("c1", "p1", "Thiết bị đo đếm lưu lượng", "bộ", 2L)
            )
        )

        val indexes = buildWorkspaceIndexes(state)
        val option = indexes.progressUi.templateOptions.first { it.name == "Thiết bị đo đếm lưu lượng" }

        assertEquals("bộ", option.unit)
        assertEquals("Hang muc cong viec chung", option.source)
    }

    @Test
    fun `resolve work template unit falls back to hierarchical aliases`() {
        val unit = resolveWorkTemplateUnit(
            "Tay vườn > Tay vườn 4m",
            workCategories = listOf(
                WorkCategory("c1", "p1", "Tay vườn 4m", "m", 2L)
            ),
            workVolumeRows = emptyList()
        )

        assertEquals("m", unit)
    }

    @Test
    fun `MaterialAggregationHelper aggregate sums planned delivered and remaining properly`() {
        val rows = listOf(
            WorkVolumeProgress("row1", "p1", "N-1", "Sắt", 100f, 0f, 1L, "kg"),
            WorkVolumeProgress("row2", "p1", "N-2", "Sắt", 50f, 0f, 2L, "kg"),
            WorkVolumeProgress("row3", "p1", "N-3", "Đá", 80f, 0f, 3L, "m3")
        )
        val handovers = listOf(
            MaterialHandover(id = "ho1", projectId = "p1", nodeCode = "N-1", workName = "Sắt", materialName = "Sắt", contractor = "Nha thau A", quantity = 30f, unit = "kg", handoverDateEpochDay = 4L, note = "", createdAtEpochMs = 5L),
            MaterialHandover(id = "ho2", projectId = "p1", nodeCode = "N-1", workName = "Sắt", materialName = "Sắt", contractor = "Nha thau A", quantity = 80f, unit = "kg", handoverDateEpochDay = 4L, note = "", createdAtEpochMs = 5L),
            MaterialHandover(id = "ho3", projectId = "p1", nodeCode = "N-2", workName = "Sắt", materialName = "Sắt", contractor = "Nha thau B", quantity = 10f, unit = "kg", handoverDateEpochDay = 4L, note = "", createdAtEpochMs = 5L),
            MaterialHandover(id = "ho4", projectId = "p1", nodeCode = "N-3", workName = "Đá", materialName = "Đá", contractor = "Nha thau C", quantity = 20f, unit = "m3", handoverDateEpochDay = 4L, note = "", createdAtEpochMs = 5L)
        )

        val aggregated = MaterialAggregationHelper.aggregate(rows, handovers)

        assertEquals(2, aggregated.size)

        // Sắt
        val sat = aggregated.first { it.workName == "Sắt" }
        assertEquals(150f, sat.planned)
        assertEquals(120f, sat.delivered) // 30 + 80 + 10
        assertEquals(40f, sat.remaining) // remaining(N-1) is max(0, 100-110) = 0. remaining(N-2) is max(0, 50-10) = 40. Total = 40.
        assertEquals("kg", sat.unit)

        // Đá
        val da = aggregated.first { it.workName == "Đá" }
        assertEquals(80f, da.planned)
        assertEquals(20f, da.delivered)
        assertEquals(60f, da.remaining)
        assertEquals("m3", da.unit)
    }

    @Test
    fun `dynamic planned material calculation based on ratio is correct`() {
        val workPlannedQty = 10f
        val ratio = 350f
        val calculatedPlannedMaterial = workPlannedQty * ratio
        assertEquals(3500f, calculatedPlannedMaterial)
    }

    @Test
    fun `applyWorkspaceSnapshotToState keeps planning data in workspace state`() {
        val declaration = MaterialDeclaration(
            id = "decl-1",
            projectId = "p1",
            workName = "Work A",
            materialName = "Steel",
            ratio = 2f,
            unit = "kg",
            createdAtEpochMs = 1L
        )
        val handover = MaterialHandover(
            id = "ho-1",
            projectId = "p1",
            nodeCode = "N01",
            workName = "Work A",
            materialName = "Steel",
            contractor = "NT1",
            quantity = 5f,
            unit = "kg",
            handoverDateEpochDay = 2L,
            note = "",
            createdAtEpochMs = 3L
        )
        val workPlan = WorkPlan(
            id = "plan-1",
            projectId = "p1",
            title = "Plan A",
            description = "desc",
            plannedDateEpochDay = 4L,
            nodeCode = "N01",
            routeCode = null,
            taskId = null,
            sourceRawInput = "raw",
            createdAtEpochMs = 5L
        )
        val snapshot = WorkspaceSnapshot(
            projectId = "p1",
            materialDeclarations = listOf(declaration),
            materialHandovers = listOf(handover),
            workPlans = listOf(workPlan)
        )

        val updated = applyWorkspaceSnapshotToState(
            current = WorkspaceState(isRefreshing = true),
            snapshot = snapshot,
            dashboard = DashboardState(),
            savedColors = mapOf("NT1" to "#123456"),
            savedHidden = setOf("NT2"),
            savedNodeSize = 1.5f,
            savedRouteWidth = 2.0f,
            loadedWorkVolumeProgress = emptyMap(),
            nextSelectedPhotos = emptyList(),
            selectedMapUi = MapUiState(),
            refreshedAtEpochMs = 99L
        )

        assertEquals(listOf(declaration), updated.materialDeclarations)
        assertEquals(listOf(handover), updated.materialHandovers)
        assertEquals(listOf(workPlan), updated.workPlans)
        assertEquals(false, updated.isRefreshing)
        assertEquals(99L, updated.lastRefreshedAtEpochMs)
        assertEquals("#123456", updated.mapUi.contractorColors["NT1"])
        assertEquals(setOf("NT2"), updated.mapUi.hiddenContractors)
        assertEquals(1.5f, updated.mapUi.nodeSizeScale)
        assertEquals(2.0f, updated.mapUi.routeWidthScale)
    }
}

