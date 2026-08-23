package com.mapsupervision.domain.model

enum class ContractorScope {
    ALL,
    SCOPED
}

enum class FirebaseProjectCatalogStatus {
    ACTIVE,
    ARCHIVED
}

data class FirebaseProjectCatalogEntry(
    val projectId: String,
    val projectName: String,
    val projectCode: String,
    val updatedAtEpochMs: Long,
    val status: FirebaseProjectCatalogStatus
)

data class FirebaseUserSession(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val emailVerified: Boolean = false,
    val isAdmin: Boolean = false,
    val isOffline: Boolean = false
)

data class ProjectAccess(
    val projectId: String,
    val isActive: Boolean = true,
    val contractorScope: ContractorScope = ContractorScope.ALL,
    val allowedContractors: Set<String> = emptySet()
)

data class FirebaseAccessState(
    val session: FirebaseUserSession? = null,
    val allowedProjectIds: Set<String> = emptySet(),
    val permissionsByProject: Map<String, ProjectAccess> = emptyMap(),
    val isInitialized: Boolean = false
)
