package com.mapsupervision.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.SetOptions
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAccessRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : FirebaseAccessRepository {
    private val appContext = context.applicationContext
    private val firebaseRuntime = FirebaseRuntime(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _accessState = MutableStateFlow(FirebaseAccessState())
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user == null) {
            _accessState.value = FirebaseAccessState(isInitialized = true)
        } else {
            scope.launch {
                safelySyncAccessForUser(user, forceRefresh = false)
            }
        }
    }

    override val accessState: StateFlow<FirebaseAccessState> = _accessState.asStateFlow()

    init {
        if (firebaseRuntime.authConfigured()) {
            runCatching {
                val auth = firebaseRuntime.auth()
                auth.addAuthStateListener(authListener)
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    scope.launch {
                        safelySyncAccessForUser(currentUser, forceRefresh = false)
                    }
                } else {
                    _accessState.value = FirebaseAccessState(isInitialized = true)
                }
            }.onFailure { error ->
                AppLogger.e(error, "firebase.access.init_failed")
                _accessState.value = FirebaseAccessState(isInitialized = true)
            }
        } else {
            _accessState.value = FirebaseAccessState(isInitialized = true)
        }
    }


    override suspend fun signIn(email: String, password: String): AppResult<FirebaseUserSession> = runCatching {
        ensureConfigured()
        val auth = firebaseRuntime.auth()
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Khong the dang nhap Firebase.")
        if (!user.isEmailVerified) {
            auth.signOut()
            error("Tai khoan chua xac thuc email.")
        }
        syncAccessForUser(user, forceRefresh = true).session
            ?: error("Khong tai duoc phien dang nhap Firebase.")
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession> = runCatching {
        ensureConfigured()
        val auth = firebaseRuntime.auth()
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user
            ?: error("Không thể đăng nhập Firebase bằng tài khoản Google.")
        syncAccessForUser(user, forceRefresh = true).session
            ?: error("Không tải được phiên đăng nhập Firebase.")
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun signOut(): AppResult<Unit> = runCatching {
        ensureConfigured()
        firebaseRuntime.auth().signOut()
        _accessState.value = FirebaseAccessState(isInitialized = true)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun refreshAccess(): AppResult<FirebaseAccessState> = runCatching {
        ensureConfigured()
        val user = firebaseRuntime.auth().currentUser ?: return@runCatching FirebaseAccessState(isInitialized = true)
        syncAccessForUser(user, forceRefresh = false)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun ensureUserProfile(): AppResult<Unit> = runCatching {
        ensureConfigured()
        val user = firebaseRuntime.auth().currentUser ?: return@runCatching Unit
        ensureUserProfileInternal(user)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(it) }
    )

    override fun projectAccess(projectId: String): ProjectAccess? =
        _accessState.value.permissionsByProject[projectId]

    private suspend fun syncAccessForUser(
        user: FirebaseUser,
        forceRefresh: Boolean
    ): FirebaseAccessState {
        val session = buildSession(user, forceRefresh)
        runCatching {
            ensureUserProfileInternal(user)
        }.onFailure { error ->
            AppLogger.e(error, "firebase.access.user_profile_sync_failed uid=${user.uid}")
        }
        val permissions = loadProjectPermissions(session)
        val nextState = FirebaseAccessState(
            session = session,
            allowedProjectIds = permissions.keys,
            permissionsByProject = permissions,
            isInitialized = true
        )
        _accessState.value = nextState
        return nextState
    }

    private suspend fun safelySyncAccessForUser(
        user: FirebaseUser,
        forceRefresh: Boolean
    ) {
        runCatching {
            syncAccessForUser(user, forceRefresh)
        }.onFailure { error ->
            AppLogger.e(error, "firebase.access.sync_failed uid=${user.uid}")
            val fallbackSession = runCatching { buildSession(user, forceRefresh) }
                .getOrElse {
                    FirebaseUserSession(
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        displayName = user.displayName,
                        emailVerified = user.isEmailVerified,
                        isAdmin = false
                    )
                }
            _accessState.value = FirebaseAccessState(
                session = fallbackSession,
                isInitialized = true
            )
        }
    }

    private suspend fun buildSession(user: FirebaseUser, forceRefresh: Boolean): FirebaseUserSession {
        val tokenResult = user.getIdToken(forceRefresh).await()
        return FirebaseUserSession(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName,
            emailVerified = user.isEmailVerified,
            isAdmin = tokenResult.claims["admin"] == true
        )
    }

    private suspend fun ensureUserProfileInternal(user: FirebaseUser) {
        val now = System.currentTimeMillis()
        firebaseRuntime.firestore()
            .collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "email" to user.email.orEmpty(),
                    "displayName" to user.displayName,
                    "emailVerified" to user.isEmailVerified,
                    "createdAtEpochMs" to now,
                    "lastLoginAtEpochMs" to now,
                    "updatedAtEpochMs" to now,
                    "isDisabled" to false
                ),
                SetOptions.merge()
            )
            .await()
    }

    private suspend fun loadProjectPermissions(session: FirebaseUserSession): Map<String, ProjectAccess> {
        val firestore = firebaseRuntime.firestore()
        if (session.isAdmin) {
            val projectDocuments = firestore.collection("projects").get().await().documents
            return projectDocuments
                .filter { !it.getBoolean("isDeleted").orFalse() }
                .associate { document ->
                    document.id to ProjectAccess(
                        projectId = document.id,
                        isActive = true,
                        contractorScope = ContractorScope.ALL,
                        allowedContractors = emptySet()
                    )
                }
        }

        val permissions = linkedMapOf<String, ProjectAccess>()
        val memberDocuments = firestore
            .collectionGroup("projectMembers")
            .whereEqualTo(FieldPath.documentId(), session.uid)
            .get()
            .await()
            .documents

        memberDocuments.forEach { memberSnapshot ->
            val isActive = memberSnapshot.getBoolean("isActive") != false
            if (!isActive) return@forEach
            val projectId = memberSnapshot.reference.parent.parent?.id ?: return@forEach
            val scope = if (memberSnapshot.getString("contractorScope") == "SCOPED") {
                ContractorScope.SCOPED
            } else {
                ContractorScope.ALL
            }
            val allowedContractors = (memberSnapshot.get("allowedContractors") as? List<*>)
                .orEmpty()
                .mapNotNull { value -> value?.toString()?.trim()?.takeIf { it.isNotBlank() } }
                .toSet()
            permissions[projectId] = ProjectAccess(
                projectId = projectId,
                isActive = true,
                contractorScope = scope,
                allowedContractors = allowedContractors
            )
        }
        return permissions
    }

    private fun ensureConfigured() {
        check(firebaseRuntime.authConfigured()) {
            "Firebase config missing. Set FIREBASE_PROJECT_ID, FIREBASE_APP_ID, FIREBASE_API_KEY in .env"
        }
    }

    private fun Boolean?.orFalse(): Boolean = this == true
}
