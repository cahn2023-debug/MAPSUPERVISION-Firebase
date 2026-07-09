package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.ImportedFileDao
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.repository.ImportedFileRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class ImportedFileRepositoryImpl @Inject constructor(
    private val dao: ImportedFileDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: com.mapsupervision.domain.repository.ActiveProjectRepository
) : ImportedFileRepository {
    override suspend fun upsert(file: ImportedFile): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val normalized = file.copy(
            updatedAtEpochMs = if (file.updatedAtEpochMs == 0L) System.currentTimeMillis() else file.updatedAtEpochMs
        )
        dao(file.projectId).upsert(normalized.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to save imported file", it)) }
    ) }

    override suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        if (files.isEmpty()) return@runCatching
        dao(files.first().projectId).upsertAll(files.map {
            it.copy(updatedAtEpochMs = if (it.updatedAtEpochMs == 0L) System.currentTimeMillis() else it.updatedAtEpochMs).toEntity()
        })
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to save imported files", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<ImportedFile>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to load imported files", it)) }
    ) }

    override suspend fun deleteById(id: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        val scopedDao = if (activeProjectId.isNullOrBlank()) dao else dao(activeProjectId)
        val now = System.currentTimeMillis()
        scopedDao.deleteById(id, now, now)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to delete imported file", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<ImportedFile>> = flow {
        emitAll(dao(projectId).observeByProject(projectId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    private fun ImportedFile.toEntity() = ImportedFileEntity(
        id = id,
        projectId = projectId,
        fileName = fileName,
        fileType = fileType,
        storedPath = storedPath,
        summary = summary,
        importedAtEpochMs = importedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private fun ImportedFileEntity.toDomain() = ImportedFile(
        id = id,
        projectId = projectId,
        fileName = fileName,
        fileType = fileType,
        storedPath = storedPath,
        summary = summary,
        importedAtEpochMs = importedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private suspend fun dao(projectId: String): ImportedFileDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.importedFileDao() ?: dao
}
