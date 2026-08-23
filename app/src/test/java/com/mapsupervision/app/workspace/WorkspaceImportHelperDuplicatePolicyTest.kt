package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.DuplicateImportPolicy
import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceImportHelperDuplicatePolicyTest {

    private val existing = GisNode(
        id = "node-existing",
        projectId = "project-1",
        code = "N-1",
        contractor = "Old contractor",
        latitude = 10.0,
        longitude = 106.0
    )

    private val incoming = existing.copy(
        id = "node-imported",
        contractor = "New contractor",
        latitude = 10.1,
        longitude = 106.1
    )

    @Test
    fun `skip policy leaves duplicate out of write set`() {
        val result = WorkspaceImportHelper.deduplicateImportedGeometry(
            projectId = "project-1",
            incomingNodes = listOf(incoming),
            incomingRoutes = emptyList(),
            existingNodes = listOf(existing),
            existingRoutes = emptyList(),
            duplicatePolicy = DuplicateImportPolicy.SKIP
        )

        assertEquals(1, result.duplicateNodes)
        assertTrue(result.nodesToInsert.isEmpty())
    }

    @Test
    fun `update policy reuses canonical id for duplicate`() {
        val result = WorkspaceImportHelper.deduplicateImportedGeometry(
            projectId = "project-1",
            incomingNodes = listOf(incoming),
            incomingRoutes = emptyList(),
            existingNodes = listOf(existing),
            existingRoutes = emptyList(),
            duplicatePolicy = DuplicateImportPolicy.UPDATE
        )

        assertEquals(1, result.duplicateNodes)
        assertEquals(1, result.nodesToInsert.size)
        assertEquals(existing.id, result.nodesToInsert.single().id)
        assertEquals("New contractor", result.nodesToInsert.single().contractor)
    }

    @Test
    fun `coordinate business key matches when codes differ but coords match`() {
        val incomingWithDifferentCode = incoming.copy(
            code = "DIFFERENT_CODE",
            latitude = 10.0,
            longitude = 106.0 // Same coordinates as existing
        )

        val result = WorkspaceImportHelper.deduplicateImportedGeometry(
            projectId = "project-1",
            incomingNodes = listOf(incomingWithDifferentCode),
            incomingRoutes = emptyList(),
            existingNodes = listOf(existing),
            existingRoutes = emptyList(),
            duplicatePolicy = DuplicateImportPolicy.SKIP,
            deduplicationKey = com.mapsupervision.domain.model.DuplicateBusinessKey.COORDINATES
        )

        assertEquals(1, result.duplicateNodes)
        assertTrue(result.nodesToInsert.isEmpty())
    }

    @Test
    fun `composite business key requires both code and coordinates to match`() {
        val incomingWithSameCodeDiffCoord = incoming.copy(
            code = "N-1",
            latitude = 11.0,
            longitude = 107.0 // Different coordinates
        )

        val result = WorkspaceImportHelper.deduplicateImportedGeometry(
            projectId = "project-1",
            incomingNodes = listOf(incomingWithSameCodeDiffCoord),
            incomingRoutes = emptyList(),
            existingNodes = listOf(existing),
            existingRoutes = emptyList(),
            duplicatePolicy = DuplicateImportPolicy.SKIP,
            deduplicationKey = com.mapsupervision.domain.model.DuplicateBusinessKey.COMPOSITE_CODE_COORD
        )

        // Since coordinates differ, composite key should NOT count it as duplicate
        assertEquals(0, result.duplicateNodes)
        assertEquals(1, result.nodesToInsert.size)
    }
}
