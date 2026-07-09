package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialsHubHelpersTest {

    @Test
    fun `resolve material quantity suggestion multiplies planned qty by ratio`() {
        val declaration = MaterialDeclaration(
            id = "decl-1",
            projectId = "project-1",
            workName = "Work A",
            materialName = "Steel",
            ratio = 2f,
            unit = "kg",
            createdAtEpochMs = 1L
        )
        val suggestion = resolveMaterialQuantitySuggestion(
            nodeCode = "N01",
            workName = "Work A",
            materialName = "Steel",
            allDeclarations = listOf(declaration),
            workVolumeRows = listOf(
                WorkVolumeProgress(
                    id = "row-1",
                    projectId = "project-1",
                    workName = "Work A",
                    nodeCode = "N01",
                    plannedQty = 10f,
                    actualQty = 0f,
                    updatedAtEpochMs = 1L,
                    unit = "m3"
                ),
                WorkVolumeProgress(
                    id = "row-2",
                    projectId = "project-1",
                    workName = "Work A",
                    nodeCode = "N02",
                    plannedQty = 5.5f,
                    actualQty = 0f,
                    updatedAtEpochMs = 1L,
                    unit = "m3"
                )
            ),
            parsedMaterialsByNodeKey = emptyMap()
        )

        assertEquals(10f, suggestion?.plannedWorkQty ?: 0f, 0.001f) // Matches N01 row plannedQty = 10
        assertEquals(20f, suggestion?.plannedMaterialQty ?: 0f, 0.001f)
        assertEquals("20", materialQuantityDefaultText(suggestion))
    }

    @Test
    fun `resolve planned work qty sums work rows by work name`() {
        val plannedQty = resolvePlannedWorkQty(
            nodeCode = "N01",
            workName = "Work A",
            workVolumeRows = listOf(
                WorkVolumeProgress(
                    id = "row-1",
                    projectId = "project-1",
                    workName = "Work A",
                    nodeCode = "N01",
                    plannedQty = 12.25f,
                    actualQty = 0f,
                    updatedAtEpochMs = 1L,
                    unit = "m3"
                ),
                WorkVolumeProgress(
                    id = "row-2",
                    projectId = "project-1",
                    workName = "Work A",
                    nodeCode = "N02",
                    plannedQty = 1.75f,
                    actualQty = 0f,
                    updatedAtEpochMs = 1L,
                    unit = "m3"
                )
            ),
            parsedMaterialsByNodeKey = emptyMap()
        )

        assertEquals(12.25f, plannedQty, 0.001f) // Matches N01
    }

    @Test
    fun `quantity helper refreshes only when selection changes`() {
        assertFalse(shouldRefreshMaterialQuantity("candidate-1", "candidate-1"))
        assertTrue(shouldRefreshMaterialQuantity("candidate-2", "candidate-1"))
    }

    @Test
    fun `parse material number input accepts decimal comma and vietnamese thousands`() {
        assertEquals(1.5f, parseMaterialNumberInput("1,5") ?: 0f, 0.001f)
        assertEquals(1000.5f, parseMaterialNumberInput("1.000,5") ?: 0f, 0.001f)
        assertEquals(12.25f, parseMaterialNumberInput("12.25") ?: 0f, 0.001f)
        assertEquals(null, parseMaterialNumberInput("abc"))
    }

    @Test
    fun `calculate material balance sums planned delivered and remaining`() {
        val declaration = MaterialDeclaration(
            id = "decl-1",
            projectId = "project-1",
            workName = "Work A",
            materialName = "Steel",
            ratio = 2f,
            unit = "kg",
            createdAtEpochMs = 1L
        )
        val balance = calculateMaterialBalance(
            declaration = declaration,
            workVolumeRows = listOf(
                WorkVolumeProgress(
                    id = "row-1",
                    projectId = "project-1",
                    nodeCode = "N01",
                    workName = "Work A",
                    plannedQty = 10f,
                    actualQty = 0f,
                    updatedAtEpochMs = 1L,
                    unit = "m3"
                ),
                WorkVolumeProgress(
                    id = "row-2",
                    projectId = "project-1",
                    nodeCode = "N02",
                    workName = "Work A",
                    plannedQty = 5f,
                    actualQty = 0f,
                    updatedAtEpochMs = 1L,
                    unit = "m3"
                )
            ),
            materialHandovers = listOf(
                MaterialHandover(
                    id = "handover-1",
                    projectId = "project-1",
                    nodeCode = "N01",
                    workName = "Work A",
                    materialName = "Steel",
                    contractor = "Contractor",
                    quantity = 8f,
                    unit = "kg",
                    handoverDateEpochDay = 1L,
                    note = "",
                    createdAtEpochMs = 1L
                ),
                MaterialHandover(
                    id = "handover-2",
                    projectId = "project-1",
                    nodeCode = "N03",
                    workName = "Work A",
                    materialName = "Steel",
                    contractor = "Contractor",
                    quantity = 4f,
                    unit = "kg",
                    handoverDateEpochDay = 1L,
                    note = "",
                    createdAtEpochMs = 1L
                )
            ),
            nodeCodes = setOf("N01")
        )

        assertEquals(20f, balance.planned, 0.001f)
        assertEquals(8f, balance.delivered, 0.001f)
        assertEquals(12f, balance.remaining, 0.001f)
    }

    @Test
    fun `build material project summary sums planned qty across rows with same normalized work`() {
        val result = buildMaterialProjectSummary(
            declarations = listOf(
                MaterialDeclaration("decl-1", "p1", "Quang (m) > Cáp quang 12FO", "Cáp quang DB 12FO", 1f, "m", 1L)
            ),
            workVolumeRows = listOf(
                WorkVolumeProgress("row-1", "p1", "N01", "Quang (m) > Cáp quang 12FO", 500f, 0f, 1L, "m"),
                WorkVolumeProgress("row-2", "p1", "N02", "Quang (m) > Cáp quang 12FO", 315f, 0f, 1L, "m")
            ),
            materialHandovers = emptyList(),
            nodeCodes = setOf("N01", "N02")
        )

        assertEquals(1, result.size)
        assertEquals(815f, result.first().second.planned, 0.001f)
        assertEquals(0f, result.first().second.delivered, 0.001f)
        assertEquals(815f, result.first().second.remaining, 0.001f)
    }

    @Test
    fun `build material project summary does not mix different works of same contractor`() {
        val result = buildMaterialProjectSummary(
            declarations = listOf(
                MaterialDeclaration("decl-1", "p1", "Cột hiện hữu > Cột chiếu sáng", "Tay vươn 2.5m", 1f, "cái", 1L)
            ),
            workVolumeRows = listOf(
                WorkVolumeProgress("row-1", "p1", "N01", "Cột hiện hữu > Cột chiếu sáng", 5f, 0f, 1L, "cái"),
                WorkVolumeProgress("row-2", "p1", "N02", "Cột hiện hữu > Cột camera", 29f, 0f, 1L, "cái")
            ),
            materialHandovers = emptyList(),
            nodeCodes = setOf("N01", "N02")
        )

        assertEquals(1, result.size)
        assertEquals(5f, result.first().second.planned, 0.001f)
    }

    @Test
    fun `build material project summary matches aliased work names by normalized text`() {
        val result = buildMaterialProjectSummary(
            declarations = listOf(
                MaterialDeclaration("decl-1", "p1", "Cot hien huu > Cot chieu sang", "Tay vươn 2.5m", 2f, "cái", 1L)
            ),
            workVolumeRows = listOf(
                WorkVolumeProgress("row-1", "p1", "N01", "Cột hiện hữu > Cột chiếu sáng", 5f, 0f, 1L, "cái")
            ),
            materialHandovers = listOf(
                 MaterialHandover(
                     id = "ho-1",
                     projectId = "p1",
                     nodeCode = "N01",
                     workName = "Cột hiện hữu > Cột chiếu sáng:Tay vươn 2.5m",
                     materialName = "Tay vươn 2.5m",
                     contractor = "NT",
                     quantity = 3f,
                     unit = "cái",
                     handoverDateEpochDay = 1L,
                     note = "",
                     createdAtEpochMs = 1L
                 )
            ),
            nodeCodes = setOf("N01")
        )

        assertEquals(1, result.size)
        assertEquals(10f, result.first().second.planned, 0.001f)
        assertEquals(3f, result.first().second.delivered, 0.001f)
        assertEquals(7f, result.first().second.remaining, 0.001f)
    }

    @Test
    fun `handover note helpers round trip receiver and note`() {
        val note = buildHandoverNote("  Nguyen Van A  ", "  Da giao xong  ")

        assertEquals("Nguyen Van A", extractHandoverReceiver(note))
        assertEquals("Da giao xong", extractHandoverNoteBody(note))
        assertTrue(handoverTextMatches("Hố ga", "ho ga"))
        assertEquals("Steel", extractMaterialName("Work A:Steel"))
    }

    @Test
    fun `handover display helpers prefer receiver field and keep legacy note fallback`() {
        val modern = MaterialHandover(
            id = "ho-modern",
            projectId = "p1",
            nodeCode = "N01",
            workName = "Work A",
            materialName = "Steel",
            contractor = "NT",
            quantity = 1f,
            unit = "kg",
            handoverDateEpochDay = 1L,
            note = "Delivered at site",
            createdAtEpochMs = 1L,
            receiver = "Nguyen Van B"
        )
        val legacy = modern.copy(
            id = "ho-legacy",
            receiver = "",
            note = buildHandoverNote("Nguyen Van A", "Legacy note")
        )

        assertEquals("Nguyen Van B", resolveHandoverReceiver(modern))
        assertEquals("Delivered at site", resolveHandoverNoteBody(modern))
        assertEquals("Nguyen Van A", resolveHandoverReceiver(legacy))
        assertEquals("Legacy note", resolveHandoverNoteBody(legacy))
    }

    @Test
    fun `handover label helpers support normalized and legacy handover shapes`() {
        val normalized = MaterialHandover(
            id = "ho-normalized",
            projectId = "p1",
            nodeCode = "N01",
            workName = "Work A",
            materialName = "Steel",
            contractor = "NT",
            quantity = 1f,
            unit = "kg",
            handoverDateEpochDay = 1L,
            note = "",
            createdAtEpochMs = 1L
        )
        val legacy = normalized.copy(id = "ho-legacy", workName = "Work A:Steel", materialName = "")

        assertEquals("Work A", resolveHandoverWorkName(normalized))
        assertEquals("Steel", resolveHandoverMaterialName(normalized))
        assertEquals("Work A", resolveHandoverWorkName(legacy))
        assertEquals("Steel", resolveHandoverMaterialName(legacy))
    }

    @Test
    fun `extract planned qty from node matches work text`() {
        assertEquals("work a", normalizeMatchText("Work A"))
        assertEquals("cot hien huu cot chieu sang", normalizeMatchText("Cột hiện hữu > Cột chiếu sáng"))
    }

    @Test
    fun `calculateMaterialBalance matches work and handover using normalized exact match`() {
        val declaration = MaterialDeclaration(
            id = "decl-1",
            projectId = "p1",
            workName = "Cáp quang DB 12FO",
            materialName = "Cáp 12FO",
            ratio = 1.2f,
            unit = "m",
            createdAtEpochMs = 1L
        )
        val workRows = listOf(
            WorkVolumeProgress("row-1", "p1", "N01", "Cáp quang DB 12FO ", 10f, 0f, 1L, "m"),
            WorkVolumeProgress("row-2", "p1", "N01", "Cáp quang DB 24FO", 5f, 0f, 1L, "m") // Different work
        )
        val handovers = listOf(
             MaterialHandover(
                 id = "ho-1",
                 projectId = "p1",
                 nodeCode = "N01",
                 workName = "Cáp quang DB 12FO : Cáp 12FO",
                 materialName = "Cáp 12FO",
                 contractor = "NT",
                 quantity = 4f,
                 unit = "m",
                 handoverDateEpochDay = 1L,
                 note = "",
                 createdAtEpochMs = 1L
             )
        )

        val balance = calculateMaterialBalance(
            declaration = declaration,
            workVolumeRows = workRows,
            materialHandovers = handovers,
            nodeCodes = setOf("N01")
        )

        assertEquals(12f, balance.planned, 0.001f) // 10 * 1.2
        assertEquals(4f, balance.delivered, 0.001f)
        assertEquals(8f, balance.remaining, 0.001f)
    }

    @Test
    fun `calculateMaterialBalance does not return negative remaining when delivered exceeds planned`() {
        val declaration = MaterialDeclaration(
            id = "decl-1",
            projectId = "p1",
            workName = "Work A",
            materialName = "Steel",
            ratio = 2f,
            unit = "kg",
            createdAtEpochMs = 1L
        )
        val balance = calculateMaterialBalance(
            declaration = declaration,
            workVolumeRows = listOf(
                WorkVolumeProgress("row-1", "p1", "N01", "Work A", 10f, 0f, 1L, "m")
            ),
            materialHandovers = listOf(
                MaterialHandover(
                    id = "ho-1",
                    projectId = "p1",
                    nodeCode = "N01",
                    workName = "Work A",
                    materialName = "Steel",
                    contractor = "NT",
                    quantity = 25f,
                    unit = "kg",
                    handoverDateEpochDay = 1L,
                    note = "",
                    createdAtEpochMs = 1L
                )
            ),
            nodeCodes = setOf("N01")
        )

        assertEquals(20f, balance.planned, 0.001f)
        assertEquals(25f, balance.delivered, 0.001f)
        assertEquals(0f, balance.remaining, 0.001f)
    }

    @Test
    fun `resolvePlannedWorkQty prioritizes parsedMaterialsByNodeKey design summary`() {
        val parsedMaterials = mapOf(
            "N01" to listOf(
                PreparedMaterialLine("Cột hiện hữu > Cột chiếu sáng", "2", 2f, "")
            )
        )
        val workRows = listOf(
            WorkVolumeProgress("row-1", "p1", "N01", "Cột hiện hữu > Cột chiếu sáng", 10f, 0f, 1L, "cái")
        )

        val plannedQty = resolvePlannedWorkQty(
            nodeCode = "N01",
            workName = "Cột hiện hữu > Cột chiếu sáng",
            workVolumeRows = workRows,
            parsedMaterialsByNodeKey = parsedMaterials
        )

        // Must prioritize parsed design summary (2f) over workVolumeRows (10f)
        assertEquals(2f, plannedQty, 0.001f)
    }

    @Test
    fun `resolveContractorPlannedWorkQty sums contractor-specific node volumes`() {
        val parsedMaterials = mapOf(
            "N01" to listOf(
                PreparedMaterialLine("Cột hiện hữu > Cột chiếu sáng", "2", 2f, "")
            ),
            "N02" to listOf(
                PreparedMaterialLine("Cột hiện hữu > Cột chiếu sáng", "3", 3f, "")
            ),
            "N03" to listOf(
                PreparedMaterialLine("Cột hiện hữu > Cột chiếu sáng", "5", 5f, "")
            )
        )
        val designNodes = listOf(
            GisNode("N01", "p1", "PM106_B1", "208", 0.0, 0.0),
            GisNode("N02", "p1", "PM29_B1", "208", 0.0, 0.0),
            GisNode("N03", "p1", "PM101_B1", "other", 0.0, 0.0) // different contractor
        )
        val workRows = listOf(
            WorkVolumeProgress("row-1", "p1", "N01", "Cột hiện hữu > Cột chiếu sáng", 10f, 0f, 1L, "cái")
        )

        val plannedQty = resolveContractorPlannedWorkQty(
            contractor = "208",
            workName = "Cột hiện hữu > Cột chiếu sáng",
            designNodes = designNodes,
            workVolumeRows = workRows,
            parsedMaterialsByNodeKey = parsedMaterials
        )

        // Sum for contractor 208: Node N01 (2f) + Node N02 (3f) = 5f (ignoring N03 since it belongs to contractor "other")
        assertEquals(5f, plannedQty, 0.001f)
    }
}
