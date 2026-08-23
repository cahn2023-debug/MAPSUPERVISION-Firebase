package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseAccessAdminAction
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.ProjectAccess
import kotlinx.coroutines.flow.StateFlow

interface FirebaseAccessRepository {
    val accessState: StateFlow<FirebaseAccessState>

    suspend fun signIn(email: String, password: String): AppResult<FirebaseUserSession>
    suspend fun register(email: String, password: String): AppResult<Unit>
    suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession>
    suspend fun reauthenticate(password: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Firebase reauthentication is not supported"))
    suspend fun enterOfflineMode(): AppResult<FirebaseAccessState>
    suspend fun signOut(): AppResult<Unit>

    suspend fun refreshAccess(): AppResult<FirebaseAccessState>
    suspend fun ensureUserProfile(): AppResult<Unit>
    suspend fun getProjectAccessRequest(projectId: String): AppResult<FirebaseProjectAccessRequest?> =
        AppResult.Error(UnsupportedOperationException("Firebase access requests are not supported"))
    suspend fun requestProjectAccess(projectId: String): AppResult<FirebaseProjectAccessRequest> =
        AppResult.Error(UnsupportedOperationException("Firebase access requests are not supported"))
    suspend fun listProjectAccessRequests(
        status: FirebaseAccessRequestStatus? = null,
        pageSize: Long = 100L,
        startAfterUpdatedAtEpochMs: Long? = null,
        startAfterRequestId: String? = null
    ): AppResult<List<FirebaseProjectAccessRequest>> =
        AppResult.Error(UnsupportedOperationException("Firebase access requests are not supported"))
    suspend fun transitionProjectAccess(
        projectId: String,
        targetUserId: String,
        action: FirebaseAccessAdminAction,
        allowedDataGroups: Set<String> = emptySet(),
        contractorScope: ContractorScope = ContractorScope.ALL,
        allowedContractors: Set<String> = emptySet()
    ): AppResult<FirebaseProjectAccessRequest> =
        AppResult.Error(UnsupportedOperationException("Firebase access requests are not supported"))
    suspend fun listProjectCatalog(
        pageSize: Long = 100L,
        startAfterUpdatedAtEpochMs: Long? = null,
        startAfterProjectId: String? = null
    ): AppResult<List<FirebaseProjectCatalogEntry>> =
        AppResult.Error(UnsupportedOperationException("Firebase project catalog is not supported"))
    suspend fun projectCreatorUid(projectId: String): AppResult<String?> =
        AppResult.Error(UnsupportedOperationException("Firebase project ownership lookup is not supported"))
    fun projectAccess(projectId: String): ProjectAccess?
}
