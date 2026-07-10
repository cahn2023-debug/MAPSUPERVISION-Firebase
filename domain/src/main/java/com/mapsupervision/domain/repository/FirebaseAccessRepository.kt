package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.ProjectAccess
import kotlinx.coroutines.flow.StateFlow

interface FirebaseAccessRepository {
    val accessState: StateFlow<FirebaseAccessState>

    suspend fun signIn(email: String, password: String): AppResult<FirebaseUserSession>
    suspend fun register(email: String, password: String): AppResult<Unit>
    suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession>
    suspend fun enterOfflineMode(): AppResult<FirebaseAccessState>
    suspend fun signOut(): AppResult<Unit>

    suspend fun refreshAccess(): AppResult<FirebaseAccessState>
    suspend fun ensureUserProfile(): AppResult<Unit>
    fun projectAccess(projectId: String): ProjectAccess?
}
