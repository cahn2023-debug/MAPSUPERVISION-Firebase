package com.mapsupervision.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.BuildConfig
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.SyncBatchResult
import com.mapsupervision.domain.repository.SyncEnvelope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class ProjectDeletionHttpException(
    val errorCode: String?,
    val responseCode: Int,
    message: String
) : Exception(message)

@Singleton
class FirebaseSyncRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    private val sharedDatabase: MapSupervisionDatabase,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : FirebaseSyncRepository {
    private val appContext = context.applicationContext
    private val metadataStore = FirebaseSyncMetadataStore(appContext)
    
    // ponytail: mutable internal fields for testing without heavy mock libraries
    internal var firebaseRuntime = FirebaseRuntime(appContext)
    internal var driveMediaUploadClient = DriveMediaUploadClient()
    internal var workspaceSnapshotExporter = WorkspaceSnapshotExporter(sharedDatabase, projectScopedDatabaseProvider)
    internal var enforceAccessChecks = true
    private val httpClient = OkHttpClient()

    override suspend fun pushPending(projectId: String): AppResult<SyncBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFirebaseConfigured()
            ensureLocalProjectActive(projectId)
            ensureApprovedAccess(projectId)
            val mediaResult = uploadPendingMediaInternal(projectId)
            var pushed = 0
            FirebaseSyncTableCatalog.tables.forEach { table ->
                val changedRows = loadRowsForPush(projectId, table)
                if (changedRows.isEmpty()) return@forEach
                val maxUpdatedAt = changedRows.maxOf { rowUpdatedAt(it, table.syncCursorColumn) }
                val written = writeRowsToFirestore(projectId, table, changedRows)
                metadataStore.setLastPushedAt(projectId, table.tableName, maxUpdatedAt)
                metadataStore.setLastError(projectId, table.tableName, null)
                pushed += written
            }
            if (pushed > 0 || mediaResult.uploadedMedia > 0) {
                sharedDatabase.projectDao().markCloudDataConfirmed(projectId, System.currentTimeMillis())
            }
            exportAndUploadSnapshotInternal(projectId)
            SyncBatchResult(
                pushed = pushed,
                uploadedMedia = mediaResult.uploadedMedia,
                failed = mediaResult.failed
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException(buildSyncFailureMessage("Failed to push Firebase sync payload", it), it)) }
        )
    }

    internal suspend fun exportAndUploadSnapshotInternal(projectId: String) {
        runCatching {
            val project = sharedDatabase.projectDao().get(projectId) ?: return@runCatching
            val snapshotJson = workspaceSnapshotExporter.exportProjectSnapshotJson(projectId) ?: return@runCatching
            driveMediaUploadClient.uploadSnapshot(
                projectId = project.id,
                projectName = project.name,
                snapshotJson = snapshotJson,
                rootFolderId = project.mediaStorageFolderId.takeIf { it.isNotBlank() }
            )
        }.onFailure { error ->
            AppLogger.e(error, "Failed to export/upload project snapshot: ${error.message}")
        }
    }

    override suspend fun pullChanges(projectId: String, sinceEpochMs: Long?): AppResult<SyncBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFirebaseConfigured()
            if (applyRemoteTombstone(projectId)) return@runCatching SyncBatchResult()
            ensureLocalProjectActive(projectId, allowRestore = true)
            ensureApprovedAccess(projectId)
            var pulled = 0
            FirebaseSyncTableCatalog.tables.forEach { table ->
                val cursorEpochMs = sinceEpochMs ?: metadataStore.lastPulledAt(projectId, table.tableName)
                val remoteDocs = readRowsFromFirestore(projectId, table, cursorEpochMs)
                if (remoteDocs.isEmpty()) return@forEach
                val maxUpdatedAt = remoteDocs.maxOf { it.updatedAtEpochMs }
                val applied = applyRemoteRows(projectId, table, remoteDocs, allowRestore = true)
                metadataStore.setLastPulledAt(projectId, table.tableName, maxUpdatedAt)
                metadataStore.setLastError(projectId, table.tableName, null)
                pulled += applied
            }
            if (pulled > 0) {
                sharedDatabase.projectDao().markCloudDataConfirmed(projectId, System.currentTimeMillis())
            }
            SyncBatchResult(pulled = pulled)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException(buildSyncFailureMessage("Failed to pull Firebase sync payload", it), it)) }
        )
    }

    override suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            ensureLocalProjectActive(projectId)
            ensureApprovedAccess(projectId)
            uploadPendingMediaInternal(projectId)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException(buildSyncFailureMessage("Failed to upload Firebase media", it), it)) }
        )
    }

    override suspend fun requestProjectDeletion(
        projectId: String,
        requestId: String,
        typedIdentity: String,
        pendingOutboxCount: Int,
        confirmPendingOutbox: Boolean
    ): AppResult<com.mapsupervision.domain.model.ProjectDeletionState> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFirebaseConfigured()
            val baseUrl = BuildConfig.MEDIA_UPLOAD_BASE_URL.trim().trimEnd('/').ifBlank {
                error("MEDIA_UPLOAD_BASE_URL is not configured")
            }
            val token = firebaseRuntime.getFirebaseToken()
            val body = JSONObject().apply {
                put("requestId", requestId)
                put("typedIdentity", typedIdentity)
                put("pendingOutboxCount", pendingOutboxCount)
                put("confirmPendingOutbox", confirmPendingOutbox)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/projects/${java.net.URLEncoder.encode(projectId, "UTF-8")}/deletion")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val payload = JSONObject(response.body?.string().orEmpty())
                if (!response.isSuccessful || payload.optBoolean("success", false).not()) {
                    val error = payload.optJSONObject("error")
                    throw ProjectDeletionHttpException(
                        errorCode = error?.optString("code")?.takeIf { it.isNotBlank() },
                        responseCode = response.code,
                        message = listOfNotNull(
                            error?.optString("code")?.takeIf { it.isNotBlank() },
                            error?.optString("message")?.takeIf { it.isNotBlank() }
                        ).joinToString(": ").ifBlank { "Cloud deletion request failed" }
                    )
                }
                when (payload.optJSONObject("data")?.optString("deletionState")) {
                    "DELETED" -> com.mapsupervision.domain.model.ProjectDeletionState.DELETED
                    "DELETING" -> com.mapsupervision.domain.model.ProjectDeletionState.DELETING
                    else -> com.mapsupervision.domain.model.ProjectDeletionState.DELETE_FAILED
                }
            }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to request cloud project deletion", it)) }
        )
    }

    override suspend fun decideProjectCloudDeletion(
        projectId: String,
        requestId: String,
        decision: String,
        typedIdentity: String
    ): AppResult<com.mapsupervision.domain.model.ProjectDeletionState> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFirebaseConfigured()
            val baseUrl = BuildConfig.MEDIA_UPLOAD_BASE_URL.trim().trimEnd('/').ifBlank {
                error("MEDIA_UPLOAD_BASE_URL is not configured")
            }
            val token = firebaseRuntime.getFirebaseToken()
            val body = JSONObject().apply {
                put("requestId", requestId)
                put("decision", decision)
                put("typedIdentity", typedIdentity)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/projects/${java.net.URLEncoder.encode(projectId, "UTF-8")}/deletion/decision")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val payload = JSONObject(response.body?.string().orEmpty())
                if (!response.isSuccessful || !payload.optBoolean("success", false)) {
                    val error = payload.optJSONObject("error")
                    throw ProjectDeletionHttpException(
                        errorCode = error?.optString("code")?.takeIf { it.isNotBlank() },
                        responseCode = response.code,
                        message = error?.optString("message") ?: "Cloud decision failed"
                    )
                }
                when (payload.optJSONObject("data")?.optString("deletionState")) {
                    "CLOUD_RETAINED" -> com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_RETAINED
                    "DELETING" -> com.mapsupervision.domain.model.ProjectDeletionState.DELETING
                    else -> com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_DECISION_PENDING
                }
            }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to submit Cloud project decision", it)) }
        )
    }

    private fun buildSyncFailureMessage(prefix: String, error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim() }
            .firstOrNull { it.isNotBlank() && it != prefix }
        return if (details.isNullOrBlank()) prefix else "$prefix: $details"
    }

    private fun photoSyncErrorMessage(error: Throwable): String? =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(400)

    private suspend fun uploadPendingMediaInternal(projectId: String): SyncBatchResult {
        ensureFirebaseConfigured()
        val photoDao = scopedDatabase(projectId)?.sitePhotoDao() ?: sharedDatabase.sitePhotoDao()
        val currentRows = photoDao.byProjectIncludingDeleted(projectId)
        if (currentRows.isEmpty()) return SyncBatchResult()

        val routes = databaseFor(projectId, FirebaseSyncTableCatalog.byTableName("gis_route"))
            .gisRouteDao()
            .byProject(projectId)
            .map { it.code.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toSet()
        var uploaded = 0
        var failed = 0
        currentRows
            .filter { !it.isDeleted && (it.syncStatus != SitePhotoSyncStatus.DONE || it.remoteUrl.isNullOrBlank()) }
            .forEach { photo ->
                val now = System.currentTimeMillis()
                try {
                    val downloadUrl = uploadMediaToDrive(projectId, photo, routes)
                    val updated = photo.copy(
                        syncStatus = SitePhotoSyncStatus.DONE,
                        remoteUrl = downloadUrl,
                        syncErrorMessage = null,
                        lastSyncAttemptEpochMs = now,
                        updatedAtEpochMs = maxOf(photo.updatedAtEpochMs, now)
                    )
                    upsertSitePhoto(projectId, updated)
                    uploaded += 1
                } catch (error: Throwable) {
                    AppLogger.e(error, "firebase.media_upload.failed projectId=$projectId photoId=${photo.id}")
                    val updated = photo.copy(
                        syncStatus = SitePhotoSyncStatus.FAILED,
                        syncErrorMessage = photoSyncErrorMessage(error),
                        lastSyncAttemptEpochMs = now,
                        updatedAtEpochMs = maxOf(photo.updatedAtEpochMs, now)
                    )
                    upsertSitePhoto(projectId, updated)
                    failed += 1
                }
            }
        return SyncBatchResult(uploadedMedia = uploaded, failed = failed)
    }

    private suspend fun uploadMediaToDrive(
        projectId: String,
        photo: SitePhotoEntity,
        routeCodes: Set<String>
    ): String {
        val token = firebaseRuntime.getFirebaseToken()
        val project = sharedDatabase.projectDao().get(projectId)
        val projectName = project?.name?.trim().orEmpty().ifBlank { projectId }
        val rootFolderId = project?.mediaStorageFolderId?.trim().orEmpty().ifBlank { null }

        val objectType = if (photo.matchedRouteId != null || photo.objectCode.trim().uppercase() in routeCodes) {
            DriveMediaObjectType.ROUTE
        } else {
            DriveMediaObjectType.NODE
        }
        return driveMediaUploadClient.upload(
            BuildConfig.MEDIA_UPLOAD_BASE_URL,
            DriveMediaUploadRequest(
                projectId = projectId,
                projectName = projectName,
                rootFolderId = rootFolderId,
                token = token,
                photoId = photo.id,
                objectType = objectType,
                objectCode = photo.objectCode,
                statusTag = photo.statusTag,
                mediaType = photo.mediaType,
                mimeType = photo.mimeType,
                capturedAtEpochMs = photo.capturedAtEpochMs,
                address = photo.address,
                captureNote = photo.captureNote,
                originalFile = java.io.File(photo.filePath),
                thumbnailFile = java.io.File(photo.thumbnailPath)
            )
        )
    }

    private suspend fun ensureFirebaseConfigured() {
        if (!firebaseRuntime.authConfigured()) {
            error("Firebase config missing. Set FIREBASE_PROJECT_ID, FIREBASE_APP_ID, FIREBASE_API_KEY in .env")
        }
    }

    private suspend fun ensureApprovedAccess(projectId: String) {
        if (!enforceAccessChecks) return
        val auth = firebaseRuntime.auth()
        val user = auth.currentUser ?: error("Firebase user is not signed in.")
        val token = user.getIdToken(false).await()
        if (token.claims["admin"] == true) return
        val access = firebaseRuntime.firestore()
            .collection("accessRequests")
            .document("${projectId.trim()}__${user.uid}")
            .get()
            .await()
        check(access.getString("status") == "APPROVED") {
            "Project access is not approved for sync."
        }
    }

    private suspend fun loadRowsForPush(projectId: String, table: FirebaseSyncTable): List<Map<String, Any?>> {
        val changedAfter = metadataStore.lastPushedAt(projectId, table.tableName)
        return queryRows(projectId, table, changedAfter)
    }

    private suspend fun queryRows(projectId: String, table: FirebaseSyncTable, changedAfter: Long): List<Map<String, Any?>> {
        val database = databaseFor(projectId, table)
        val readable = database.openHelper.readableDatabase
        val query = when (table.tableName) {
            "projects" -> "SELECT * FROM ${table.tableName} WHERE id = ?"
            else -> "SELECT * FROM ${table.tableName} WHERE ${table.projectIdColumn} = ? AND ${table.syncCursorColumn} >= ? ORDER BY ${table.syncCursorColumn} ASC"
        }
        val args = when (table.tableName) {
            "projects" -> arrayOf<Any>(projectId)
            else -> arrayOf<Any>(projectId, changedAfter)
        }
        return readable.query(SimpleSQLiteQuery(query, args)).use { cursor -> cursor.toRowMaps() }
    }

    private suspend fun writeRowsToFirestore(projectId: String, table: FirebaseSyncTable, rows: List<Map<String, Any?>>): Int {
        val firestore = firebaseRuntime.firestore()
        val deviceId = metadataStore.deviceId()
        val now = System.currentTimeMillis()
        var written = 0
        rows.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            var batchWrites = 0
            chunk.forEach { row ->
                val id = row[table.idColumn]?.toString().orEmpty()
                if (id.isBlank()) return@forEach
                val payload = row.toMutableMap()
                val envelope: SyncEnvelope<Map<String, Any?>> = SyncEnvelope(
                    id = id,
                    projectId = if (table.tableName == "projects") projectId else payload[table.projectIdColumn]?.toString().orEmpty(),
                    tableName = table.tableName,
                    data = payload,
                    updatedAtEpochMs = rowUpdatedAt(row, table.syncCursorColumn),
                    isDeleted = (payload["isDeleted"] as? Number)?.toInt() == 1 || payload["isDeleted"] == true,
                    sourceDeviceId = deviceId,
                    lastSyncedAtEpochMs = now
                )
                val target = if (table.collectionName == "__project_root__") {
                    val catalogTarget = firestore.collection("projectCatalog").document(projectId)
                    val rawName = payload["name"]?.toString()?.trim().orEmpty()
                    val slug = payload["slug"]?.toString()?.trim().orEmpty()
                    val projectCode = (payload["projectCode"]?.toString()?.trim() ?: slug).ifBlank { projectId.take(8).uppercase(java.util.Locale.ROOT) }
                    val isArchived = (payload["isArchived"] as? Number)?.toInt() == 1 || payload["isArchived"] == true
                    val currentUid = firebaseRuntime.auth().currentUser?.uid ?: "legacy-owner"
                    val catalogData = mapOf(
                        "projectName" to rawName.ifBlank { projectCode },
                        "projectCode" to projectCode,
                        "createdByUid" to currentUid,
                        "updatedAtEpochMs" to envelope.updatedAtEpochMs,
                        "status" to if (isArchived) "ARCHIVED" else "ACTIVE"
                    )
                    batch.set(catalogTarget, catalogData, SetOptions.merge())
                    batchWrites += 1
                    firestore.collection("projects").document(projectId)
                } else {
                    firestore.collection("projects").document(projectId).collection(table.collectionName).document(id)
                }
                batch.set(target, envelope.toFirestoreMap(), SetOptions.merge())
                batchWrites += 1
            }
            if (batchWrites > 0) {
                batch.commit().await()
                written += batchWrites
            }
        }
        return written
    }

    private suspend fun readRowsFromFirestore(
        projectId: String,
        table: FirebaseSyncTable,
        changedAfter: Long
    ): List<SyncEnvelope<Map<String, Any?>>> {
        val firestore = firebaseRuntime.firestore()
        if (table.collectionName == "__project_root__") {
            val snapshot = firestore.collection("projects").document(projectId).get().await()
            if (!snapshot.exists()) return emptyList()
            val data = snapshot.data.orEmpty()
            val envelope = snapshot.toEnvelope(projectId, table.tableName, data)
            return listOf(envelope).filter { it.updatedAtEpochMs > changedAfter }
        }
        val snapshot = firestore.collection("projects")
            .document(projectId)
            .collection(table.collectionName)
            .whereGreaterThan("updatedAtEpochMs", changedAfter)
            .orderBy("updatedAtEpochMs", Query.Direction.ASCENDING)
            .get()
            .await()
        return snapshot.documents.map { document ->
            document.toEnvelope(projectId, table.tableName, document.data.orEmpty())
        }
    }

    private suspend fun applyRemoteRows(
        projectId: String,
        table: FirebaseSyncTable,
        envelopes: List<SyncEnvelope<Map<String, Any?>>>,
        allowRestore: Boolean = false
    ): Int {
        ensureLocalProjectActive(projectId, allowRestore)
        val deviceId = metadataStore.deviceId()
        val rowsToApply = if (allowRestore) envelopes else envelopes.filter { it.sourceDeviceId != deviceId }
        if (rowsToApply.isEmpty()) return 0
        val targetDatabases = databasesForWrite(projectId, table)
        rowsToApply.forEach { envelope ->
            val row = mergeEnvelopeRow(table, envelope)
            targetDatabases.forEach { database ->
                val localUpdatedAt = database.openHelper.readableDatabase
                    .query(
                        SimpleSQLiteQuery(
                            "SELECT ${table.syncCursorColumn} FROM ${table.tableName} WHERE ${table.idColumn} = ?",
                            arrayOf(envelope.id)
                        )
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else null
                    }
                if (shouldApplyRemoteRow(localUpdatedAt, envelope.updatedAtEpochMs)) {
                    upsertRow(database.openHelper.writableDatabase, table, row)
                }
            }
        }
        return rowsToApply.size
    }

    internal suspend fun applyRemoteRowsForTest(
        projectId: String,
        table: FirebaseSyncTable,
        envelopes: List<SyncEnvelope<Map<String, Any?>>>,
        allowRestore: Boolean = true
    ): Int = applyRemoteRows(projectId, table, envelopes, allowRestore)

    private fun upsertRow(database: SupportSQLiteDatabase, table: FirebaseSyncTable, row: Map<String, Any?>) {
        val values = ContentValues()
        row.forEach { (key, value) ->
            when (value) {
                null -> values.putNull(key)
                is String -> values.put(key, value)
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Double -> values.put(key, value)
                is Float -> values.put(key, value)
                is Boolean -> values.put(key, if (value) 1 else 0)
                is ByteArray -> values.put(key, value)
                is Number -> values.put(key, value.toDouble())
                else -> values.put(key, value.toString())
            }
        }
        val id = row[table.idColumn]
        if (id != null) {
            val updated = database.update(
                table.tableName,
                android.database.sqlite.SQLiteDatabase.CONFLICT_NONE,
                values,
                "${table.idColumn} = ?",
                arrayOf(id.toString())
            )
            if (updated > 0) return
        }
        database.insert(table.tableName, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private suspend fun upsertSitePhoto(projectId: String, photo: SitePhotoEntity) {
        ensureLocalProjectActive(projectId)
        sharedDatabase.sitePhotoDao().upsert(photo)
        scopedDatabase(projectId)?.sitePhotoDao()?.upsert(photo)
    }

    private suspend fun databaseFor(projectId: String, table: FirebaseSyncTable): MapSupervisionDatabase =
        when (table.scope) {
            SyncScope.SHARED_ONLY -> sharedDatabase
            SyncScope.PROJECT_MIRROR -> {
                val project = sharedDatabase.projectDao().get(projectId)
                    ?: error("Project not found: $projectId")
                if (project.storageMode == com.mapsupervision.domain.model.ProjectStorageMode.PROJECT_DB) {
                    scopedDatabase(projectId) ?: error("Project database is locked: $projectId")
                } else {
                    sharedDatabase
                }
            }
        }

    private suspend fun databasesForWrite(projectId: String, table: FirebaseSyncTable): List<MapSupervisionDatabase> =
        when (table.scope) {
            SyncScope.SHARED_ONLY -> listOf(sharedDatabase)
            SyncScope.PROJECT_MIRROR -> {
                val project = sharedDatabase.projectDao().get(projectId)
                    ?: error("Project not found: $projectId")
                if (project.storageMode == com.mapsupervision.domain.model.ProjectStorageMode.PROJECT_DB) {
                    listOfNotNull(sharedDatabase, scopedDatabase(projectId)).distinctBy { it.openHelper.databaseName }
                } else {
                    listOf(sharedDatabase)
                }
            }
        }

    private suspend fun scopedDatabase(projectId: String): MapSupervisionDatabase? =
        projectScopedDatabaseProvider.databaseFor(projectId)

    private suspend fun ensureLocalProjectActive(projectId: String, allowRestore: Boolean = false) {
        val project = sharedDatabase.projectDao().get(projectId)
        val allowedState = project?.deletionState == com.mapsupervision.domain.model.ProjectDeletionState.ACTIVE ||
            (allowRestore && project?.deletionState in setOf(
                com.mapsupervision.domain.model.ProjectDeletionState.CLOUD_RETAINED,
                com.mapsupervision.domain.model.ProjectDeletionState.RESTORE_PENDING
            ))
        check(project != null && !project.isDeleted && allowedState) {
            "Project is locked for deletion: $projectId"
        }
    }

    private suspend fun applyRemoteTombstone(projectId: String): Boolean {
        val snapshot = firebaseRuntime.firestore()
            .collection("projectDeletionTombstones")
            .document(projectId)
            .get()
            .await()
        if (!snapshot.exists()) return false
        val requestId = snapshot.getString("requestId").orEmpty()
        val completedAt = snapshot.getLong("deletedAtEpochMs") ?: System.currentTimeMillis()
        sharedDatabase.projectDao().markRemoteDeletion(projectId, requestId, completedAt, System.currentTimeMillis())
        projectScopedDatabaseProvider.databaseFor(projectId)
            ?.projectDao()
            ?.markRemoteDeletion(projectId, requestId, completedAt, System.currentTimeMillis())
        return true
    }

    private fun rowUpdatedAt(row: Map<String, Any?>, columnName: String): Long =
        (row[columnName] as? Number)?.toLong() ?: 0L

    private fun Cursor.toRowMaps(): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        while (moveToNext()) {
            val row = linkedMapOf<String, Any?>()
            for (index in 0 until columnCount) {
                row[getColumnName(index)] = when (getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
                    Cursor.FIELD_TYPE_STRING -> getString(index)
                    Cursor.FIELD_TYPE_BLOB -> getBlob(index)
                    else -> getString(index)
                }
            }
            rows += row
        }
        return rows
    }

    private fun SyncEnvelope<Map<String, Any?>>.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "projectId" to projectId,
        "tableName" to tableName,
        "data" to data,
        "updatedAtEpochMs" to updatedAtEpochMs,
        "isDeleted" to isDeleted,
        "sourceDeviceId" to sourceDeviceId,
        "lastSyncedAtEpochMs" to lastSyncedAtEpochMs
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toEnvelope(
        projectId: String,
        tableName: String,
        sourceData: Map<String, Any?>
    ): SyncEnvelope<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        val data = sourceData["data"] as? Map<String, Any?> ?: sourceData
        val updatedAt = (sourceData["updatedAtEpochMs"] as? Number)?.toLong()
            ?: (data["updatedAtEpochMs"] as? Number)?.toLong()
            ?: (data["createdAtEpochMs"] as? Number)?.toLong()
            ?: 0L
        val isDeleted = (sourceData["isDeleted"] as? Boolean)
            ?: ((data["isDeleted"] as? Number)?.toInt() == 1)
        return SyncEnvelope(
            id = sourceData["id"]?.toString() ?: id,
            projectId = sourceData["projectId"]?.toString() ?: projectId,
            tableName = sourceData["tableName"]?.toString() ?: tableName,
            data = data,
            updatedAtEpochMs = updatedAt,
            isDeleted = isDeleted,
            sourceDeviceId = sourceData["sourceDeviceId"]?.toString().orEmpty(),
            lastSyncedAtEpochMs = (sourceData["lastSyncedAtEpochMs"] as? Number)?.toLong() ?: 0L
        )
    }

    companion object {
        private const val FIRESTORE_BATCH_LIMIT = 500
    }
}

internal fun mergeEnvelopeRow(
    table: FirebaseSyncTable,
    envelope: SyncEnvelope<Map<String, Any?>>
): Map<String, Any?> {
    val baseRow = envelope.data + mapOf(table.idColumn to envelope.id)
    return if (table.tableName == "projects") {
        baseRow
    } else {
        baseRow + mapOf("projectId" to envelope.projectId)
    }
}

internal fun shouldApplyRemoteRow(localUpdatedAtEpochMs: Long?, remoteUpdatedAtEpochMs: Long): Boolean =
    localUpdatedAtEpochMs == null || remoteUpdatedAtEpochMs > localUpdatedAtEpochMs
