package com.mapsupervision.data.sync

import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.canRequestAgain
import com.mapsupervision.domain.model.validateApprovedScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAccessRequestLifecycleTest {
    @Test
    fun request_document_id_is_deterministic_for_a_user_project_pair() {
        assertEquals("project-1__user-1", accessRequestDocumentId(" project-1 ", " user-1 "))
    }

    @Test
    fun admin_transition_matrix_only_allows_pending_approval_rejection_and_approved_revoke() {
        assertTrue(isValidAdminTransition(FirebaseAccessRequestStatus.PENDING, FirebaseAccessRequestStatus.APPROVED))
        assertTrue(isValidAdminTransition(FirebaseAccessRequestStatus.PENDING, FirebaseAccessRequestStatus.REJECTED))
        assertTrue(isValidAdminTransition(FirebaseAccessRequestStatus.APPROVED, FirebaseAccessRequestStatus.REVOKED))
        assertFalse(isValidAdminTransition(FirebaseAccessRequestStatus.REJECTED, FirebaseAccessRequestStatus.APPROVED))
        assertFalse(isValidAdminTransition(FirebaseAccessRequestStatus.REVOKED, FirebaseAccessRequestStatus.REJECTED))
    }

    @Test
    fun parses_pending_request_and_rejects_malformed_approved_scope() {
        val pending = parseFirebaseProjectAccessRequest(
            requestId = "project-1__user-1",
            fields = requestFields(status = "PENDING")
        )
        assertEquals(FirebaseAccessRequestStatus.PENDING, pending?.status)
        assertTrue(pending?.canRequestAgain() == false)

        assertNull(
            parseFirebaseProjectAccessRequest(
                requestId = "other-project__user-1",
                fields = requestFields(status = "PENDING")
            )
        )
        assertNull(
            parseFirebaseProjectAccessRequest(
                requestId = "project-1__user-1",
                fields = requestFields(
                    status = "APPROVED",
                    approvedBy = "admin-1",
                    approvedAtEpochMs = 100L,
                    allowedDataGroups = emptyList<String>()
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun scoped_approval_requires_a_contractor() {
        validateApprovedScope(
            allowedDataGroups = setOf("task"),
            contractorScope = ContractorScope.SCOPED,
            allowedContractors = emptySet()
        )
    }

    private fun requestFields(
        status: String,
        approvedBy: String? = null,
        approvedAtEpochMs: Long? = null,
        allowedDataGroups: List<String> = emptyList(),
        allowedContractors: List<String> = emptyList()
    ): Map<String, Any?> = mapOf(
        "projectId" to "project-1",
        "userId" to "user-1",
        "status" to status,
        "allowedDataGroups" to allowedDataGroups,
        "contractorScope" to "ALL",
        "allowedContractors" to allowedContractors,
        "approvedBy" to approvedBy,
        "approvedAtEpochMs" to approvedAtEpochMs,
        "requestedAtEpochMs" to 100L,
        "updatedAtEpochMs" to 100L
    )
}
