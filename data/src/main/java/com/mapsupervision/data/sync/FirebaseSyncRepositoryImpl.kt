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

    override suspend fun pushPending(projectId: String): AppResult<SyncBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFirebaseConfigured()
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
            SyncBatchResult(
                pushed = pushed,
                uploadedMedia = mediaResult.uploadedMedia,
                failed = mediaResult.failed
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to push Firebase sync payload", it)) }
        )
    }

    override suspend fun pullChanges(projectId: String, sinceEpochMs: Long?): AppResult<SyncBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            ensureFirebaseConfigured()
            var pulled = 0
            FirebaseSyncTableCatalog.tables.forEach { table ->
                val cursorEpochMs = sinceEpochMs ?: metadataStore.lastPulledAt(projectId, table.tableName)
                val remoteDocs = readRowsFromFirestore(projectId, table, cursorEpochMs)
                if (remoteDocs.isEmpty()) return@forEach
                val maxUpdatedAt = remoteDocs.maxOf { it.updatedAtEpochMs }
                val applied = applyRemoteRows(projectId, table, remoteDocs)
                metadataStore.setLastPulledAt(projectId, table.tableName, maxUpdatedAt)
                metadataStore.setLastError(projectId, table.tableName, null)
                pulled += applied
            }
            SyncBatchResult(pulled = pulled)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to pull Firebase sync payload", it)) }
        )
    }

    override suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult> = withContext(Dispatchers.IO) {
        runCatching { uploadPendingMediaInternal(projectId) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to upload Firebase media", it)) }
        )
    }

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
                        lastSyncAttemptEpochMs = now,
                        updatedAtEpochMs = maxOf(photo.updatedAtEpochMs, now)
                    )
                    upsertSitePhoto(projectId, updated)
                    uploaded += 1
                } catch (error: Throwable) {
                    AppLogger.e(error, "firebase.media_upload.failed projectId=$projectId photoId=${photo.id}")
                    val updated = photo.copy(
                        syncStatus = SitePhotoSyncStatus.FAILED,
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
            else -> arrayOf(projectId, changedAfter)
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
        envelopes: List<SyncEnvelope<Map<String, Any?>>>
    ): Int {
        val deviceId = metadataStore.deviceId()
        val rowsToApply = envelopes.filter { it.sourceDeviceId != deviceId }
        if (rowsToApply.isEmpty()) return 0
        val targetDatabases = databasesForWrite(projectId, table)
        rowsToApply.forEach { envelope ->
            val row = mergeEnvelopeRow(table, envelope)
            targetDatabases.forEach { database ->
                upsertRow(database.openHelper.writableDatabase, table.tableName, row)
            }
        }
        return rowsToApply.size
    }

    private fun upsertRow(database: SupportSQLiteDatabase, tableName: String, row: Map<String, Any?>) {
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
        database.insert(tableName, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private suspend fun upsertSitePhoto(projectId: String, photo: SitePhotoEntity) {
        sharedDatabase.sitePhotoDao().upsert(photo)
        scopedDatabase(projectId)?.sitePhotoDao()?.upsert(photo)
    }

    private suspend fun databaseFor(projectId: String, table: FirebaseSyncTable): MapSupervisionDatabase =
        when (table.scope) {
            SyncScope.SHARED_ONLY -> sharedDatabase
            SyncScope.PROJECT_MIRROR -> scopedDatabase(projectId) ?: sharedDatabase
        }

    private suspend fun databasesForWrite(projectId: String, table: FirebaseSyncTable): List<MapSupervisionDatabase> =
        when (table.scope) {
            SyncScope.SHARED_ONLY -> listOf(sharedDatabase)
            SyncScope.PROJECT_MIRROR -> listOfNotNull(sharedDatabase, scopedDatabase(projectId)).distinctBy { it.openHelper.databaseName }
        }

    private suspend fun scopedDatabase(projectId: String): MapSupervisionDatabase? =
        projectScopedDatabaseProvider.databaseFor(projectId)

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
