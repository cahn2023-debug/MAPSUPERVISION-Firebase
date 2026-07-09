package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.WorkPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePlanHardeningTest {

    @Test
    fun `build work plan batch locations returns empty list when nothing selected`() {
        val locations = buildWorkPlanBatchLocations(emptyList(), emptyList())

        assertEquals(emptyList<Pair<String?, String?>>(), locations)
    }

    @Test
    fun `build work plan batch plans keeps same batch group id across multiple locations`() {
        val batchGroupId = "batch-1"
        val plans = buildWorkPlanBatchPlans(
            projectId = "project-1",
            title = "Dao ranh",
            taskId = null,
            note = "Theo tung doan",
            dateEpochDay = 2000L,
            quantity = 12.5,
            unit = "m3",
            batchGroupId = batchGroupId,
            createdAtEpochMs = 123456789L,
            locations = listOf(
                "N-01" to null,
                null to "R-01",
                null to "R-02"
            )
        )

        assertEquals(3, plans.size)
        assertEquals(listOf("N-01", null, null), plans.map { it.nodeCode })
        assertEquals(listOf(null, "R-01", "R-02"), plans.map { it.routeCode })
        assertEquals(listOf(batchGroupId, batchGroupId, batchGroupId), plans.map { it.batchGroupId })
        assertEquals(listOf(12.5, 12.5, 12.5), plans.map { it.quantity })
    }

    @Test
    fun `build work plan batch plans keeps selected task id`() {
        val plans = buildWorkPlanBatchPlans(
            projectId = "project-1",
            title = "Kiem tra cap",
            taskId = "task-1",
            note = "",
            dateEpochDay = 2000L,
            quantity = 4.0,
            unit = "m",
            batchGroupId = "batch-1",
            createdAtEpochMs = 123456789L,
            locations = listOf("N-01" to null)
        )

        assertEquals("task-1", plans.single().taskId)
    }

    @Test
    fun `workspace state carries work plans into data state`() {
        val plans = listOf(
            WorkPlan(
                id = "plan-1",
                projectId = "project-1",
                title = "Dao ranh",
                description = "Theo tung doan",
                plannedDateEpochDay = 2000L,
                nodeCode = "N-01",
                routeCode = null,
                taskId = null,
                sourceRawInput = "",
                createdAtEpochMs = 123456789L,
                quantity = 8.0,
                unit = "m3",
                batchGroupId = "batch-1"
            )
        )

        val state = WorkspaceState(workPlans = plans)

        assertEquals(plans, state.toDataState().workPlans)
    }
}
