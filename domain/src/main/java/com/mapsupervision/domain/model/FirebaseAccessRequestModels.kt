package com.mapsupervision.domain.model

enum class FirebaseAccessRequestStatus {
    NOT_REQUESTED,
    PENDING,
    APPROVED,
    REJECTED,
    REVOKED
}

enum class FirebaseAccessAdminAction {
    APPROVE,
    REJECT,
    REVOKE
}

data class FirebaseProjectAccessRequest(
    val requestId: String,
    val projectId: String,
    val userId: String,
    val status: FirebaseAccessRequestStatus,
    val allowedDataGroups: Set<String> = emptySet(),
    val contractorScope: ContractorScope = ContractorScope.ALL,
    val allowedContractors: Set<String> = emptySet(),
    val approvedBy: String? = null,
    val approvedAtEpochMs: Long? = null,
    val requestedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 0L
)

data class FirebaseAccessAuditRecord(
    val auditId: String,
    val projectId: String,
    val targetUserId: String,
    val action: FirebaseAccessAdminAction,
    val previousState: FirebaseAccessRequestStatus,
    val newState: FirebaseAccessRequestStatus,
    val actorAdminId: String,
    val timestampEpochMs: Long
)

fun FirebaseAccessRequestStatus.canRequestAgain(): Boolean =
    this == FirebaseAccessRequestStatus.NOT_REQUESTED ||
        this == FirebaseAccessRequestStatus.REJECTED ||
        this == FirebaseAccessRequestStatus.REVOKED

fun FirebaseProjectAccessRequest.canRequestAgain(): Boolean =
    status.canRequestAgain()

fun validateApprovedScope(
    allowedDataGroups: Set<String>,
    contractorScope: ContractorScope,
    allowedContractors: Set<String>
) {
    require(allowedDataGroups.any { it.isNotBlank() }) {
        "At least one data group is required for approval."
    }
    if (contractorScope == ContractorScope.SCOPED) {
        require(allowedContractors.any { it.isNotBlank() }) {
            "At least one contractor is required for a scoped approval."
        }
    }
}
