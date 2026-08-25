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
    fun applies_fallbacks_for_legacy_or_partial_catalog_entries() {
        val entryWithFallbackOwner = parseFirebaseProjectCatalog(
            projectId = "project-missing-owner",
            fields = mapOf(
                "projectName" to "Project Missing Owner",
                "projectCode" to "P-000",
                "updatedAtEpochMs" to 1L,
                "status" to "ACTIVE"
            ),
            fallbackOwnerUid = "admin-fallback"
        )
        assertEquals("project-missing-owner", entryWithFallbackOwner?.projectId)
        assertEquals("admin-fallback", entryWithFallbackOwner?.createdByUid)

        val entryWithDefaultOwner = parseFirebaseProjectCatalog(
            projectId = "project-legacy",
            fields = mapOf(
                "projectName" to "Legacy Project"
            )
        )
        assertEquals("project-legacy", entryWithDefaultOwner?.projectId)
        assertEquals("Legacy Project", entryWithDefaultOwner?.projectName)
        assertEquals("PROJECT-", entryWithDefaultOwner?.projectCode)
        assertEquals("legacy-owner", entryWithDefaultOwner?.createdByUid)
        assertEquals(FirebaseProjectCatalogStatus.ACTIVE, entryWithDefaultOwner?.status)

        assertNull(
            parseFirebaseProjectCatalog(
                projectId = "   ",
                fields = mapOf("projectName" to "Blank Project")
            )
        )
    }

    @Test
    fun keeps_missing_or_blank_catalog_name_empty() {
        val missingName = parseFirebaseProjectCatalog(
            projectId = "project-missing-name",
            fields = mapOf("projectCode" to "P-001")
        )
        val blankName = parseFirebaseProjectCatalog(
            projectId = "project-blank-name",
            fields = mapOf("projectName" to "   ", "projectCode" to "P-002")
        )

        assertEquals("", missingName?.projectName)
        assertEquals("", blankName?.projectName)
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
    fun extracts_project_doc_with_fallback_owner_for_legacy_projects() {
        val entry = extractCatalogEntryFromProjectDoc(
            projectId = "proj-300",
            docData = mapOf(
                "payload" to mapOf(
                    "name" to "Legacy Project",
                    "projectCode" to "LEGACY-300",
                    "isDeleted" to false
                )
            ),
            fallbackOwnerUid = "admin-uid"
        )

        assertEquals("proj-300", entry?.projectId)
        assertEquals("Legacy Project", entry?.projectName)
        assertEquals("LEGACY-300", entry?.projectCode)
        assertEquals("admin-uid", entry?.createdByUid)
        assertEquals(FirebaseProjectCatalogStatus.ACTIVE, entry?.status)
    }

    @Test
    fun keeps_missing_project_doc_name_empty() {
        val entry = extractCatalogEntryFromProjectDoc(
            projectId = "proj-missing-name",
            docData = mapOf(
                "payload" to mapOf(
                    "projectCode" to "P-003",
                    "isDeleted" to false
                )
            )
        )

        assertEquals("", entry?.projectName)
    }
}
