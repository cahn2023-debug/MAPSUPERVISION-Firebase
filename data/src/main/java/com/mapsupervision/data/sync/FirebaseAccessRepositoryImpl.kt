package com.mapsupervision.data.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.FirebaseAccessAdminAction
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import com.mapsupervision.domain.model.FirebaseProjectCatalogStatus
import com.mapsupervision.domain.model.FirebaseCatalogMigrationReport
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.model.canRequestAgain
import com.mapsupervision.domain.model.validateApprovedScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

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
        AppLogger.d("firebase.access.sign_in.start provider=password")
        val user = withAuthTimeout {
            auth.signInWithEmailAndPassword(email.trim(), password).await().user
        } ?: throw FirebaseAuthErrorMapper.AppAuthMessageException("Không thể đăng nhập. Vui lòng thử lại.")
        if (!user.isEmailVerified) {
            auth.signOut()
            throw FirebaseAuthErrorMapper.AppAuthMessageException(
                "Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư và xác thực trước khi đăng nhập."
            )
        }
        val session = syncAccessForUser(user, forceRefresh = true).session
            ?: throw FirebaseAuthErrorMapper.AppAuthMessageException("Không tải được phiên đăng nhập. Vui lòng thử lại.")
        AppLogger.d("firebase.access.sign_in.success uid=${session.uid} provider=password")
        session
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { error ->
            AppLogger.e(error, "firebase.access.sign_in.failed provider=password")
            AppResult.Error(FirebaseAuthErrorMapper.wrap(error))
        }
    )

    override suspend fun register(email: String, password: String): AppResult<Unit> = runCatching {
        ensureConfigured()
        val auth = firebaseRuntime.auth()
        AppLogger.d("firebase.access.register.start")
        val user = withAuthTimeout {
            auth.createUserWithEmailAndPassword(email.trim(), password).await().user
        } ?: throw FirebaseAuthErrorMapper.AppAuthMessageException("Không thể tạo tài khoản. Vui lòng thử lại.")
        AppLogger.d("firebase.access.register.user_created uid=${user.uid}")
        withAuthTimeout { user.sendEmailVerification().await() }
        AppLogger.d("firebase.access.register.verification_sent uid=${user.uid}")
        auth.signOut()
        _accessState.value = FirebaseAccessState(isInitialized = true)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { error ->
            AppLogger.e(error, "firebase.access.register.failed")
            runCatching { firebaseRuntime.auth().signOut() }
            AppResult.Error(FirebaseAuthErrorMapper.wrap(error))
        }
    )

    override suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession> = runCatching {
        ensureConfigured()
        val auth = firebaseRuntime.auth()
        AppLogger.d("firebase.access.sign_in.start provider=google")
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        val authResult = withAuthTimeout { auth.signInWithCredential(credential).await() }
        val user = authResult.user
            ?: throw FirebaseAuthErrorMapper.AppAuthMessageException(
                "Không thể đăng nhập bằng tài khoản Google. Vui lòng thử lại."
            )
        val session = syncAccessForUser(user, forceRefresh = true).session
            ?: throw FirebaseAuthErrorMapper.AppAuthMessageException("Không tải được phiên đăng nhập. Vui lòng thử lại.")
        AppLogger.d(
            "firebase.access.sign_in.success uid=${session.uid} provider=google isNewUser=${authResult.additionalUserInfo?.isNewUser == true}"
        )
        session
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { error ->
            AppLogger.e(error, "firebase.access.sign_in.failed provider=google")
            AppResult.Error(FirebaseAuthErrorMapper.wrap(error))
        }
    )

    override suspend fun enterOfflineMode(): AppResult<FirebaseAccessState> = runCatching {
        val nextState = FirebaseAccessState(
            session = FirebaseUserSession(
                uid = "offline-user",
                email = "offline@local",
                displayName = "Offline",
                emailVerified = true,
                isOffline = true
            ),
            isInitialized = true
        )
        _accessState.value = nextState
        nextState
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun signOut(): AppResult<Unit> = runCatching {
        if (_accessState.value.session?.isOffline == true) {
            _accessState.value = FirebaseAccessState(isInitialized = true)
            return@runCatching
        }
        if (firebaseRuntime.authConfigured()) {
            firebaseRuntime.auth().signOut()
        }
        _accessState.value = FirebaseAccessState(isInitialized = true)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun refreshAccess(): AppResult<FirebaseAccessState> = runCatching {
        if (_accessState.value.session?.isOffline == true) {
            return@runCatching _accessState.value
        }
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

    override suspend fun getProjectAccessRequest(projectId: String): AppResult<FirebaseProjectAccessRequest?> = runCatching {
        ensureConfigured()
        val user = firebaseRuntime.auth().currentUser ?: error("Firebase user is not signed in.")
        require(projectId.isValidFirebaseId()) { "Project id is required." }
        firebaseRuntime.firestore()
            .collection(ACCESS_REQUESTS_COLLECTION)
            .document(accessRequestDocumentId(projectId, user.uid))
            .get()
            .await()
            .takeIf { it.exists() }
            ?.let { parseFirebaseProjectAccessRequest(it.id, it.data.orEmpty()) }
            ?: return@runCatching null
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun requestProjectAccess(projectId: String): AppResult<FirebaseProjectAccessRequest> = runCatching {
        ensureConfigured()
        val user = firebaseRuntime.auth().currentUser ?: error("Firebase user is not signed in.")
        require(projectId.isValidFirebaseId()) { "Project id is required." }
        val firestore = firebaseRuntime.firestore()
        val reference = firestore.collection(ACCESS_REQUESTS_COLLECTION)
            .document(accessRequestDocumentId(projectId, user.uid))
        firestore.runTransaction { transaction ->
            val existing = transaction.get(reference)
                .takeIf { it.exists() }
                ?.let { parseFirebaseProjectAccessRequest(it.id, it.data.orEmpty()) }
            if (existing != null && !existing.canRequestAgain()) {
                return@runTransaction existing
            }
            val now = System.currentTimeMillis()
            val next = FirebaseProjectAccessRequest(
                requestId = reference.id,
                projectId = projectId,
                userId = user.uid,
                status = FirebaseAccessRequestStatus.PENDING,
                requestedAtEpochMs = now,
                updatedAtEpochMs = now
            )
            transaction.set(reference, next.toFirestoreFields())
            next
        }.await()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun listProjectAccessRequests(
        status: FirebaseAccessRequestStatus?,
        pageSize: Long,
        startAfterUpdatedAtEpochMs: Long?,
        startAfterRequestId: String?
    ): AppResult<List<FirebaseProjectAccessRequest>> = runCatching {
        ensureAdminClaim()
        if (status == FirebaseAccessRequestStatus.NOT_REQUESTED) {
            return@runCatching emptyList()
        }
        require((startAfterUpdatedAtEpochMs == null) == (startAfterRequestId == null)) {
            "Access request cursor must include both updatedAtEpochMs and requestId."
        }
        var query = firebaseRuntime.firestore()
            .collection(ACCESS_REQUESTS_COLLECTION)
            .orderBy("updatedAtEpochMs", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(pageSize.coerceIn(1L, 100L))
        if (status != null && status != FirebaseAccessRequestStatus.NOT_REQUESTED) {
            query = firebaseRuntime.firestore()
                .collection(ACCESS_REQUESTS_COLLECTION)
                .whereEqualTo("status", status.name)
                .orderBy("updatedAtEpochMs", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .limit(pageSize.coerceIn(1L, 100L))
        }
        if (startAfterUpdatedAtEpochMs != null && startAfterRequestId != null) {
            query = query.startAfter(startAfterUpdatedAtEpochMs, startAfterRequestId)
        }
        query.get().await().documents.mapNotNull { document ->
            parseFirebaseProjectAccessRequest(document.id, document.data.orEmpty())
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun transitionProjectAccess(
        projectId: String,
        targetUserId: String,
        action: FirebaseAccessAdminAction,
        allowedDataGroups: Set<String>,
        contractorScope: ContractorScope,
        allowedContractors: Set<String>
    ): AppResult<FirebaseProjectAccessRequest> = runCatching {
        ensureAdminClaim()
        require(projectId.isValidFirebaseId()) { "Project id is required." }
        require(targetUserId.isValidFirebaseId()) { "Target user id is required." }
        if (action == FirebaseAccessAdminAction.APPROVE) {
            validateApprovedScope(allowedDataGroups, contractorScope, allowedContractors)
        }
        val firestore = firebaseRuntime.firestore()
        val reference = firestore.collection(ACCESS_REQUESTS_COLLECTION)
            .document(accessRequestDocumentId(projectId, targetUserId))
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val current = snapshot.takeIf { it.exists() }
                ?.let { parseFirebaseProjectAccessRequest(it.id, it.data.orEmpty()) }
                ?: error("Access request not found.")
            val targetStatus = action.targetStatus()
            if (current.status == targetStatus) {
                return@runTransaction current
            }
            require(isValidAdminTransition(current.status, targetStatus)) {
                "Invalid access transition ${current.status} -> $targetStatus."
            }
            val now = System.currentTimeMillis()
            val next = current.copy(
                status = targetStatus,
                allowedDataGroups = if (targetStatus == FirebaseAccessRequestStatus.APPROVED) {
                    allowedDataGroups.filter(String::isNotBlank).toSet()
                } else current.allowedDataGroups,
                contractorScope = if (targetStatus == FirebaseAccessRequestStatus.APPROVED) contractorScope else current.contractorScope,
                allowedContractors = if (targetStatus == FirebaseAccessRequestStatus.APPROVED) {
                    allowedContractors.filter(String::isNotBlank).toSet()
                } else current.allowedContractors,
                approvedBy = if (targetStatus == FirebaseAccessRequestStatus.APPROVED) {
                    firebaseRuntime.auth().currentUser?.uid
                } else current.approvedBy,
                approvedAtEpochMs = if (targetStatus == FirebaseAccessRequestStatus.APPROVED) now else current.approvedAtEpochMs,
                updatedAtEpochMs = now
            )
            transaction.set(reference, next.toFirestoreFields())
            val auditReference = reference.collection(ACCESS_AUDIT_SUBCOLLECTION).document()
            transaction.set(
                auditReference,
                mapOf(
                    "projectId" to projectId,
                    "targetUserId" to targetUserId,
                    "action" to action.name,
                    "previousState" to current.status.name,
                    "newState" to targetStatus.name,
                    "actorAdminId" to (firebaseRuntime.auth().currentUser?.uid ?: error("Admin user is missing.")),
                    "timestampEpochMs" to now
                )
            )
            next
        }.await()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun listProjectCatalog(
        pageSize: Long,
        startAfterUpdatedAtEpochMs: Long?,
        startAfterProjectId: String?
    ): AppResult<List<FirebaseProjectCatalogEntry>> = runCatching {
        ensureConfigured()
        check(firebaseRuntime.auth().currentUser != null) { "Firebase user is not signed in." }
        require((startAfterUpdatedAtEpochMs == null) == (startAfterProjectId == null)) {
            "Catalog cursor must include both updatedAtEpochMs and projectId."
        }
        val firestore = firebaseRuntime.firestore()
        var query = firestore
            .collection("projectCatalog")
            .orderBy("updatedAtEpochMs", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(pageSize.coerceIn(1L, 100L))
        if (startAfterUpdatedAtEpochMs != null && startAfterProjectId != null) {
            query = query.startAfter(startAfterUpdatedAtEpochMs, startAfterProjectId)
        }
        val sessionOwner = _accessState.value.session?.uid ?: firebaseRuntime.auth().currentUser?.uid
        val catalogDocuments = query.get().await().documents
        val entries = catalogDocuments.mapNotNull { document ->
            parseFirebaseProjectCatalog(document.id, document.data.orEmpty(), fallbackOwnerUid = sessionOwner)
        }.toMutableList()

        val isAdmin = _accessState.value.session?.isAdmin == true
        if (isAdmin && startAfterUpdatedAtEpochMs == null) {
            runCatching {
                val projectDocs = firestore.collection("projects").get().await().documents
                val entriesByProjectId = entries.associateBy { it.projectId }.toMutableMap()
                val toUpsert = mutableListOf<FirebaseProjectCatalogEntry>()

                for (doc in projectDocs) {
                    val projectEntry = extractCatalogEntryFromProjectDoc(doc.id, doc.data.orEmpty(), fallbackOwnerUid = sessionOwner) ?: continue
                    val existing = entriesByProjectId[projectEntry.projectId]
                    if (existing == null) {
                        toUpsert.add(projectEntry)
                        entriesByProjectId[projectEntry.projectId] = projectEntry
                    } else if (existing.projectName.isBlank() ||
                        existing.projectName.equals(existing.projectId, ignoreCase = true) ||
                        (projectEntry.projectName.isNotBlank() &&
                         !projectEntry.projectName.equals(projectEntry.projectId, ignoreCase = true) &&
                         projectEntry.projectName != existing.projectName)
                    ) {
                        val resolvedName = if (projectEntry.projectName.isNotBlank() && !projectEntry.projectName.equals(projectEntry.projectId, ignoreCase = true)) {
                            projectEntry.projectName
                        } else {
                            existing.projectName.ifBlank { projectEntry.projectCode }
                        }
                        val resolvedCode = if (existing.projectCode.isNotBlank() && !existing.projectCode.startsWith(existing.projectId.take(8), ignoreCase = true)) {
                            existing.projectCode
                        } else {
                            projectEntry.projectCode
                        }
                        val updated = existing.copy(
                            projectName = resolvedName,
                            projectCode = resolvedCode,
                            updatedAtEpochMs = maxOf(existing.updatedAtEpochMs, projectEntry.updatedAtEpochMs)
                        )
                        toUpsert.add(updated)
                        entriesByProjectId[projectEntry.projectId] = updated
                    }
                }

                if (toUpsert.isNotEmpty()) {
                    val batch = firestore.batch()
                    toUpsert.forEach { entry ->
                        val catalogDocRef = firestore.collection("projectCatalog").document(entry.projectId)
                        batch.set(
                            catalogDocRef,
                            mapOf(
                                "projectName" to entry.projectName,
                                "projectCode" to entry.projectCode,
                                "createdByUid" to entry.createdByUid,
                                "updatedAtEpochMs" to entry.updatedAtEpochMs,
                                "status" to entry.status.name
                            ),
                            SetOptions.merge()
                        )
                    }
                    batch.commit().await()
                    entries.clear()
                    entries.addAll(entriesByProjectId.values)
                    entries.sortByDescending { it.updatedAtEpochMs }
                }
            }.onFailure { error ->
                AppLogger.d("firebase.access.catalog_backfill_failed: ${error.message}")
            }
        }
        entries.toList()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun latestCatalogMigrationReport(): AppResult<FirebaseCatalogMigrationReport?> = runCatching {
        ensureConfigured()
        check(_accessState.value.session?.isAdmin == true) { "Admin access is required." }
        val document = firebaseRuntime.firestore().collection("catalogMigrations")
            .orderBy("completedAtEpochMs", Query.Direction.DESCENDING)
            .limit(1)
            .get().await().documents.firstOrNull() ?: return@runCatching null
        val data = document.data.orEmpty()
        val counts = data["counts"] as? Map<*, *> ?: emptyMap<Any, Any>()
        FirebaseCatalogMigrationReport(
            status = data["status"] as? String ?: "COMPLETED",
            warningCount = (counts["warning"] as? Number)?.toInt() ?: 0,
            discrepancyCount = (counts["discrepancy"] as? Number)?.toInt() ?: 0,
            warnings = (data["warnings"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            discrepancies = (data["discrepancies"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        )
    }.fold(onSuccess = { AppResult.Success(it) }, onFailure = { AppResult.Error(it) })

    override suspend fun projectCreatorUid(projectId: String): AppResult<String?> = runCatching {
        ensureConfigured()
        val snapshot = firebaseRuntime.firestore().collection("projects").document(projectId).get().await()
        if (!snapshot.exists()) return@runCatching null
        @Suppress("UNCHECKED_CAST")
        val payload = (snapshot.get("payload") as? Map<String, Any?>) ?: snapshot.data.orEmpty()
        (payload["createdByUid"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
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

    override suspend fun reauthenticate(password: String): AppResult<Unit> = runCatching {
        ensureConfigured()
        val user = firebaseRuntime.auth().currentUser ?: error("Firebase user is not signed in")
        val email = user.email?.trim().orEmpty().ifBlank { error("Email reauthentication is required") }
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        withAuthTimeout { user.reauthenticate(credential).await() }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { error -> AppResult.Error(FirebaseAuthErrorMapper.wrap(error)) }
    )

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
        val tokenResult = withAuthTimeout { user.getIdToken(forceRefresh).await() }
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
                        isProjectAdmin = true,
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
                isProjectAdmin = memberSnapshot.getBoolean("isAdmin") == true ||
                    listOf("admin", "owner", "creator", "super-admin").contains(memberSnapshot.getString("role")?.lowercase()),
                contractorScope = scope,
                allowedContractors = allowedContractors
            )
        }

        // Android approvals are stored on the access request itself. Include
        // approved requests in the local access state so opening a catalog
        // entry also satisfies WorkspaceViewModel's permission gate, even
        // when no legacy projectMembers projection exists yet.
        val approvedRequests = firestore
            .collection(ACCESS_REQUESTS_COLLECTION)
            .whereEqualTo("userId", session.uid)
            .whereEqualTo("status", FirebaseAccessRequestStatus.APPROVED.name)
            .get()
            .await()
            .documents

        approvedRequests.forEach { requestSnapshot ->
            val request = parseFirebaseProjectAccessRequest(requestSnapshot.id, requestSnapshot.data.orEmpty())
                ?: return@forEach
            permissions[request.projectId] = ProjectAccess(
                projectId = request.projectId,
                isActive = true,
                contractorScope = request.contractorScope,
                allowedContractors = request.allowedContractors
            )
        }
        return permissions
    }

    private fun ensureConfigured() {
        check(firebaseRuntime.authConfigured()) {
            "Cloud config missing. Set FIREBASE_PROJECT_ID, FIREBASE_APP_ID, FIREBASE_API_KEY in .env"
        }
    }

    private fun Boolean?.orFalse(): Boolean = this == true

    private suspend fun ensureAdminClaim() {
        val user = firebaseRuntime.auth().currentUser ?: error("Firebase user is not signed in.")
        check(user.getIdToken(false).await().claims["admin"] == true) {
            "Firebase admin claim is required."
        }
    }

    private fun FirebaseAccessAdminAction.targetStatus(): FirebaseAccessRequestStatus = when (this) {
        FirebaseAccessAdminAction.APPROVE -> FirebaseAccessRequestStatus.APPROVED
        FirebaseAccessAdminAction.REJECT -> FirebaseAccessRequestStatus.REJECTED
        FirebaseAccessAdminAction.REVOKE -> FirebaseAccessRequestStatus.REVOKED
    }

    private fun String.isValidFirebaseId(): Boolean = isNotBlank() && !contains('/')

    private fun FirebaseProjectAccessRequest.toFirestoreFields(): Map<String, Any?> = mapOf(
        "projectId" to projectId,
        "userId" to userId,
        "status" to status.name,
        "allowedDataGroups" to allowedDataGroups.toList(),
        "contractorScope" to contractorScope.name,
        "allowedContractors" to allowedContractors.toList(),
        "approvedBy" to approvedBy,
        "approvedAtEpochMs" to approvedAtEpochMs,
        "requestedAtEpochMs" to requestedAtEpochMs,
        "updatedAtEpochMs" to updatedAtEpochMs
    )

    companion object {
        private const val ACCESS_REQUESTS_COLLECTION = "accessRequests"
        private const val ACCESS_AUDIT_SUBCOLLECTION = "accessAudit"
    }
}

internal fun accessRequestDocumentId(projectId: String, userId: String): String =
    "${projectId.trim()}__${userId.trim()}"

internal fun isValidAdminTransition(
    previous: FirebaseAccessRequestStatus,
    next: FirebaseAccessRequestStatus
): Boolean = when (next) {
    FirebaseAccessRequestStatus.APPROVED,
    FirebaseAccessRequestStatus.REJECTED -> previous == FirebaseAccessRequestStatus.PENDING
    FirebaseAccessRequestStatus.REVOKED -> previous == FirebaseAccessRequestStatus.APPROVED
    else -> false
}

internal fun parseFirebaseProjectAccessRequest(
    requestId: String,
    fields: Map<String, Any?>
): FirebaseProjectAccessRequest? {
    val projectId = (fields["projectId"] as? String)?.trim().orEmpty()
    val userId = (fields["userId"] as? String)?.trim().orEmpty()
    val status = (fields["status"] as? String)?.trim()?.uppercase(Locale.ROOT)
        ?.let { value -> runCatching { FirebaseAccessRequestStatus.valueOf(value) }.getOrNull() }
    val updatedAtEpochMs = fields["updatedAtEpochMs"].asLongOrNull()
    val allowedDataGroups = fields["allowedDataGroups"].asStringSetOrNull()
    val allowedContractors = fields["allowedContractors"].asStringSetOrNull()
    val contractorScope = (fields["contractorScope"] as? String)?.trim()?.uppercase(Locale.ROOT)
        ?.let { value -> runCatching { ContractorScope.valueOf(value) }.getOrNull() }
    if (requestId.isBlank() || projectId.isBlank() || userId.isBlank() ||
        requestId != accessRequestDocumentId(projectId, userId) || status == null ||
        updatedAtEpochMs == null || updatedAtEpochMs < 0L || allowedDataGroups == null ||
        allowedContractors == null || contractorScope == null
    ) return null
    val approvedAt = fields["approvedAtEpochMs"].asLongOrNull()
    val requestedAt = fields["requestedAtEpochMs"].asLongOrNull()
    if (status == FirebaseAccessRequestStatus.APPROVED) {
        if (approvedAt == null || (fields["approvedBy"] as? String).isNullOrBlank()) return null
        runCatching { validateApprovedScope(allowedDataGroups, contractorScope, allowedContractors) }
            .getOrElse { return null }
    }
    return FirebaseProjectAccessRequest(
        requestId = requestId,
        projectId = projectId,
        userId = userId,
        status = status,
        allowedDataGroups = allowedDataGroups,
        contractorScope = contractorScope,
        allowedContractors = allowedContractors,
        approvedBy = (fields["approvedBy"] as? String)?.trim()?.takeIf { it.isNotBlank() },
        approvedAtEpochMs = approvedAt,
        requestedAtEpochMs = requestedAt,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun Any?.asLongOrNull(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    else -> null
}

private fun Any?.asStringSetOrNull(): Set<String>? =
    (this as? List<*>)?.map { it as? String ?: return null }
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.toSet()

internal fun parseFirebaseProjectCatalog(
    projectId: String,
    fields: Map<String, Any?>,
    fallbackOwnerUid: String? = null
): FirebaseProjectCatalogEntry? {
    val normalizedProjectId = projectId.trim()
    if (normalizedProjectId.isBlank()) return null

    @Suppress("UNCHECKED_CAST")
    val dataMap = (fields["data"] as? Map<String, Any?>) ?: (fields["payload"] as? Map<String, Any?>) ?: fields

    val rawName = ((dataMap["projectName"] ?: dataMap["name"] ?: fields["projectName"] ?: fields["name"]) as? String)?.trim().orEmpty()
    val slug = ((dataMap["slug"] ?: fields["slug"]) as? String)?.trim().orEmpty()
    val projectCode = ((dataMap["projectCode"] ?: dataMap["code"] ?: fields["projectCode"] ?: fields["code"]) as? String)?.trim().orEmpty()
        .ifBlank { slug.ifBlank { normalizedProjectId.take(8).uppercase(Locale.ROOT) } }

    val projectName = if (rawName.isBlank() || rawName.equals(normalizedProjectId, ignoreCase = true)) {
        if (projectCode.isNotBlank() && !projectCode.equals(normalizedProjectId, ignoreCase = true) && !projectCode.startsWith(normalizedProjectId.take(8), ignoreCase = true)) {
            projectCode
        } else if (slug.isNotBlank() && !slug.equals(normalizedProjectId, ignoreCase = true)) {
            slug
        } else {
            rawName
        }
    } else {
        rawName
    }

    val createdByUid = ((dataMap["createdByUid"] ?: dataMap["ownerUid"] ?: dataMap["userId"] ?: fields["createdByUid"] ?: fields["ownerUid"] ?: fields["userId"]) as? String)?.trim()
        ?.takeIf { it.isNotBlank() } ?: fallbackOwnerUid ?: "legacy-owner"
    val updatedAtEpochMs = when (val value = dataMap["updatedAtEpochMs"] ?: fields["updatedAtEpochMs"] ?: dataMap["createdAtEpochMs"] ?: fields["createdAtEpochMs"]) {
        is Long -> value
        is Int -> value.toLong()
        is Number -> value.toLong()
        else -> 0L
    }
    val status = when ((dataMap["status"] as? String ?: fields["status"] as? String)?.trim()?.uppercase(Locale.ROOT)) {
        "ARCHIVED" -> FirebaseProjectCatalogStatus.ARCHIVED
        else -> FirebaseProjectCatalogStatus.ACTIVE
    }

    return FirebaseProjectCatalogEntry(
        projectId = normalizedProjectId,
        projectName = projectName,
        projectCode = projectCode,
        updatedAtEpochMs = updatedAtEpochMs.coerceAtLeast(0L),
        status = status,
        createdByUid = createdByUid
    )
}

internal fun extractCatalogEntryFromProjectDoc(
    projectId: String,
    docData: Map<String, Any?>,
    fallbackOwnerUid: String? = null
): FirebaseProjectCatalogEntry? {
    val normalizedProjectId = projectId.trim()
    if (normalizedProjectId.isBlank()) return null

    @Suppress("UNCHECKED_CAST")
    val dataMap = (docData["data"] as? Map<String, Any?>) ?: (docData["payload"] as? Map<String, Any?>) ?: docData
    val isDeleted = (dataMap["isDeleted"] as? Boolean)
        ?: (docData["isDeleted"] as? Boolean)
        ?: ((dataMap["isDeleted"] as? Number)?.toInt() == 1)
        ?: false
    if (isDeleted) return null

    val rawName = ((dataMap["name"] ?: dataMap["projectName"] ?: docData["name"] ?: docData["projectName"]) as? String)?.trim().orEmpty()
    val slug = ((dataMap["slug"] ?: docData["slug"]) as? String)?.trim().orEmpty()
    val projectCode = ((dataMap["projectCode"] ?: dataMap["code"] ?: docData["projectCode"] ?: docData["code"]) as? String)?.trim().orEmpty()
        .ifBlank { slug.ifBlank { normalizedProjectId.take(8).uppercase(Locale.ROOT) } }

    val projectName = if (rawName.isBlank() || rawName.equals(normalizedProjectId, ignoreCase = true)) {
        if (projectCode.isNotBlank() && !projectCode.equals(normalizedProjectId, ignoreCase = true) && !projectCode.startsWith(normalizedProjectId.take(8), ignoreCase = true)) {
            projectCode
        } else if (slug.isNotBlank() && !slug.equals(normalizedProjectId, ignoreCase = true)) {
            slug
        } else {
            rawName
        }
    } else {
        rawName
    }

    val updatedAt = when (val value = dataMap["updatedAtEpochMs"] ?: docData["updatedAtEpochMs"] ?: dataMap["createdAtEpochMs"] ?: docData["createdAtEpochMs"]) {
        is Long -> value
        is Int -> value.toLong()
        is Number -> value.toLong()
        else -> 0L
    }
    val isArchived = (dataMap["isArchived"] as? Boolean) ?: (docData["isArchived"] as? Boolean) ?: false
    val createdByUid = ((dataMap["createdByUid"] ?: dataMap["ownerUid"] ?: dataMap["userId"] ?: docData["createdByUid"] ?: docData["ownerUid"]) as? String)?.trim()
        ?.takeIf { it.isNotBlank() } ?: fallbackOwnerUid ?: "legacy-owner"
    val status = if (isArchived) FirebaseProjectCatalogStatus.ARCHIVED else FirebaseProjectCatalogStatus.ACTIVE

    return FirebaseProjectCatalogEntry(
        projectId = normalizedProjectId,
        projectName = projectName,
        projectCode = projectCode,
        updatedAtEpochMs = updatedAt.coerceAtLeast(0L),
        status = status,
        createdByUid = createdByUid
    )
}
