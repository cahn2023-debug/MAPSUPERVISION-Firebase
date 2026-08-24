package com.mapsupervision.data.sync

import com.mapsupervision.domain.model.FirebaseProjectCatalogStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirebaseProjectCatalogParserTest {
    @Test
    fun parses_active_catalog_entry_from_allowlisted_fields() {
        val entry = parseFirebaseProjectCatalog(
            projectId = "project-1",
            fields = mapOf(
                "projectName" to "Project One",
                "projectCode" to "P-001",
                "createdByUid" to "owner-1",
                "updatedAtEpochMs" to 123L,
                "status" to "ACTIVE"
            )
        )

        assertEquals("project-1", entry?.projectId)
        assertEquals("Project One", entry?.projectName)
        assertEquals("P-001", entry?.projectCode)
        assertEquals("owner-1", entry?.createdByUid)
        assertEquals(123L, entry?.updatedAtEpochMs)
        assertEquals(FirebaseProjectCatalogStatus.ACTIVE, entry?.status)
    }

    @Test
    fun parses_archived_status_case_insensitively() {
        val entry = parseFirebaseProjectCatalog(
            projectId = "project-2",
            fields = mapOf(
                "projectName" to "Project Two",
                "projectCode" to "P-002",
                "createdByUid" to "owner-2",
                "updatedAtEpochMs" to 456,
                "status" to " archived "
            )
        )

        assertEquals(FirebaseProjectCatalogStatus.ARCHIVED, entry?.status)
        assertEquals(456L, entry?.updatedAtEpochMs)
    }

    @Test
    fun rejects_missing_or_invalid_allowlisted_fields() {
        assertNull(
            parseFirebaseProjectCatalog(
                projectId = "project-missing-owner",
                fields = mapOf(
                    "projectName" to "Project Missing Owner",
                    "projectCode" to "P-000",
                    "updatedAtEpochMs" to 1L,
                    "status" to "ACTIVE"
                )
            )
        )
        assertNull(
            parseFirebaseProjectCatalog(
                projectId = "project-3",
                fields = mapOf(
                    "projectName" to "Project Three",
                    "projectCode" to "P-003",
                    "createdByUid" to "owner-3",
                    "updatedAtEpochMs" to 789L,
                    "status" to "DELETED"
                )
            )
        )
        assertNull(
            parseFirebaseProjectCatalog(
                projectId = "project-4",
                fields = mapOf(
                    "projectName" to " ",
                    "projectCode" to "P-004",
                    "createdByUid" to "owner-4",
                    "updatedAtEpochMs" to -1L,
                    "status" to "ACTIVE"
                )
            )
        )
        assertNull(
            parseFirebaseProjectCatalog(
                projectId = "project-5",
                fields = mapOf(
                    "projectName" to "Project Five",
                    "projectCode" to "P-005",
                    "createdByUid" to "owner-5",
                    "updatedAtEpochMs" to 123.9,
                    "status" to "ACTIVE"
                )
            )
        )
    }

    @Test
    fun extracts_catalog_entry_from_envelope_project_doc() {
        val entry = extractCatalogEntryFromProjectDoc(
            projectId = "proj-100",
            docData = mapOf(
                "payload" to mapOf(
                    "name" to "Metro Line 1",
                    "slug" to "metro-line-1",
                    "projectCode" to "ML-01",
                    "createdByUid" to "owner-100",
                    "updatedAtEpochMs" to 999999L,
                    "isDeleted" to false,
                    "isArchived" to false
                )
            )
        )
        assertEquals("proj-100", entry?.projectId)
        assertEquals("Metro Line 1", entry?.projectName)
        assertEquals("ML-01", entry?.projectCode)
        assertEquals(999999L, entry?.updatedAtEpochMs)
        assertEquals(FirebaseProjectCatalogStatus.ACTIVE, entry?.status)
    }

    @Test
    fun ignores_deleted_project_doc() {
        val entry = extractCatalogEntryFromProjectDoc(
            projectId = "proj-200",
            docData = mapOf(
                "payload" to mapOf(
                    "name" to "Deleted Project",
                    "isDeleted" to true
                )
            )
        )
        assertNull(entry)
    }

    @Test
    fun ignores_project_doc_without_owner_until_migration_repairs_it() {
        val entry = extractCatalogEntryFromProjectDoc(
            projectId = "proj-300",
            docData = mapOf(
                "payload" to mapOf(
                    "name" to "Legacy Project",
                    "projectCode" to "LEGACY-300",
                    "isDeleted" to false
                )
            )
        )

        assertNull(entry)
    }
}
