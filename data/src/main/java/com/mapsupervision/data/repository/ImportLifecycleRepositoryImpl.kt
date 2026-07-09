package com.mapsupervision.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.ImportAuditEntity
import com.mapsupervision.data.db.entity.ImportConflictEntity
import com.mapsupervision.data.db.entity.ImportSessionEntity
import com.mapsupervision.data.db.entity.ImportVersionEntity
import com.mapsupervision.domain.model.ImportAudit
import com.mapsupervision.domain.model.ImportConflict
import com.mapsupervision.domain.model.ImportSession
import com.mapsupervision.domain.model.ImportVersion
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportLifecycleRepository
import com.mapsupervision.domain.repository.ImportRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImportLifecycleRepositoryImpl @Inject constructor(
    private val sharedDatabase: MapSupervisionDatabase,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val importRepository: ImportRepository,
    private val gisRepository: GisRepository
) : ImportLifecycleRepository {
    override suspend fun upsertSession(session: ImportSession): AppResult<Unit> = writeResult(session.projectId) {
        sessionDao().upsert(session.toEntity())
    }

    override suspend fun upsertVersion(version: ImportVersion): AppResult<Unit> = writeResult(version.projectId) {
        versionDao().upsert(version.toEntity())
    }

    override suspend fun upsertAudit(audit: ImportAudit): AppResult<Unit> = writeResult(audit.projectId) {
        auditDao().upsert(audit.toEntity())
    }

    override suspend fun upsertConflict(conflict: ImportConflict): AppResult<Unit> = writeResult(conflict.projectId) {
        conflictDao().upsert(conflict.toEntity())
    }

    override suspend fun latestVersionNumber(projectId: String): AppResult<Int> = withDb(projectId) { db ->
        val latest = db.importVersionDao().latest(projectId)?.versionNumber ?: 0
        AppResult.Success(latest)
    }

    override suspend fun rollbackToVersion(projectId: String, versionNumber: Int): AppResult<Unit> = withDb(projectId) { db ->
        val version = db.importVersionDao().findByVersion(projectId, versionNumber)
            ?: return@withDb AppResult.Error(DatabaseException("Version not found", IllegalArgumentException("version=$versionNumber")))
        val session = db.importSessionDao().findById(version.importSessionId)
            ?: return@withDb AppResult.Error(DatabaseException("Import session not found", IllegalStateException("session=${version.importSessionId}")))
        val importedFileId = session.importedFileId
            ?: return@withDb AppResult.Error(DatabaseException("Rollback requires imported file id", IllegalStateException("session=${session.id}")))
        val file = File(session.sourceFilePath)
        if (!file.exists()) {
            return@withDb AppResult.Error(DatabaseException("Rollback source file missing", IllegalStateException(file.absolutePath)))
        }
        val draft = importRepository.importFile(projectId, Uri.fromFile(file).toString())
        val saveResult = gisRepository.replaceImportedGeometry(
            importedFileId = importedFileId,
            nodes = draft.suggestedNodes,
            routes = draft.suggestedRoutes
        )
        when (saveResult) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> saveResult
        }
    }

    override suspend fun purgeDeletedArtifacts(projectId: String, deletedBeforeEpochMs: Long): AppResult<Unit> = writeResult(projectId) {
        val db = databaseFor(projectId)
        db.withTransaction {
            db.sitePhotoDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.taskDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.noteDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.nodeProgressDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.workVolumeProgressDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.gisRouteDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.gisNodeDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
            db.importConflictDao().purgeBefore(projectId, deletedBeforeEpochMs)
            db.importAuditDao().purgeBefore(projectId, deletedBeforeEpochMs)
            db.importSessionDao().purgeCompletedBefore(projectId, deletedBeforeEpochMs)
            db.importedFileDao().purgeDeletedBefore(projectId, deletedBeforeEpochMs)
        }
    }

    private suspend fun databaseFor(projectId: String): MapSupervisionDatabase =
        projectScopedDatabaseProvider.databaseFor(projectId) ?: sharedDatabase

    private suspend fun <T> withDb(projectId: String, block: suspend (MapSupervisionDatabase) -> AppResult<T>): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { block(databaseFor(projectId)) }.getOrElse {
                AppResult.Error(DatabaseException("Import lifecycle operation failed", it))
            }
        }

    private suspend fun writeResult(projectId: String, block: suspend MapSupervisionDatabase.() -> Unit): AppResult<Unit> =
        withDb(projectId) { db ->
            runCatching {
                db.withTransaction { db.block() }
                AppResult.Success(Unit)
            }.getOrElse {
                AppResult.Error(DatabaseException("Import lifecycle write failed", it))
            }
        }

    private fun ImportSession.toEntity() = ImportSessionEntity(
        id = id,
        projectId = projectId,
        sourceKind = sourceKind,
        sourceFileName = sourceFileName,
        sourceFileType = sourceFileType,
        sourceFilePath = sourceFilePath,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        importedFileId = importedFileId,
        featureCount = featureCount,
        conflictCount = conflictCount,
        warningCount = warningCount,
        message = message
    )

    private fun ImportVersion.toEntity() = ImportVersionEntity(
        id = id,
        projectId = projectId,
        importSessionId = importSessionId,
        versionNumber = versionNumber,
        sourceHash = sourceHash,
        createdAtEpochMs = createdAtEpochMs,
        createdBy = createdBy,
        note = note
    )

    private fun ImportConflict.toEntity() = ImportConflictEntity(
        id = id,
        projectId = projectId,
        importSessionId = importSessionId,
        featureBusinessCode = featureBusinessCode,
        conflictType = conflictType,
        severity = severity,
        details = details,
        resolvedBy = resolvedBy,
        resolvedAtEpochMs = resolvedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs
    )

    private fun ImportAudit.toEntity() = ImportAuditEntity(
        id = id,
        projectId = projectId,
        importSessionId = importSessionId,
        action = action,
        actor = actor,
        payloadJson = payloadJson,
        createdAtEpochMs = createdAtEpochMs
    )

    private fun MapSupervisionDatabase.sessionDao() = importSessionDao()
    private fun MapSupervisionDatabase.versionDao() = importVersionDao()
    private fun MapSupervisionDatabase.conflictDao() = importConflictDao()
    private fun MapSupervisionDatabase.auditDao() = importAuditDao()
}
