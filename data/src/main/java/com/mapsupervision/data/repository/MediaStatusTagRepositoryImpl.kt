package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.MediaStatusTagDao
import com.mapsupervision.data.db.entity.MediaStatusTagEntity
import com.mapsupervision.domain.model.MediaStatusTag
import com.mapsupervision.domain.model.MediaStatusTags
import com.mapsupervision.domain.repository.MediaStatusTagRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MediaStatusTagRepositoryImpl @Inject constructor(
    private val dao: MediaStatusTagDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : MediaStatusTagRepository {
    override suspend fun add(tag: MediaStatusTag): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = tag.normalized()
            val entity = normalized.toEntity()
            val projectDatabase = projectScopedDatabaseProvider.databaseFor(normalized.projectId)
            writeToSharedAndScoped(
                sharedWrite = { dao.insert(entity) },
                scopedWrite = { projectDatabase?.mediaStatusTagDao()?.insert(entity) }
            )
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to add media status tag", it)) }
        )
    }

    override suspend fun byProject(projectId: String): AppResult<List<MediaStatusTag>> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = dao(projectId).byProject(projectId)
            (if (rows.isEmpty()) dao.byProject(projectId) else rows).map { it.toDomain() }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to list media status tags", it)) }
        )
    }

    override fun observeByProject(projectId: String): Flow<List<MediaStatusTag>> = flow {
        val scopedDao = dao(projectId)
        emitAll(
            combine(
                scopedDao.observeByProject(projectId),
                dao.observeByProject(projectId)
            ) { scopedRows, sharedRows ->
                (if (scopedRows.isEmpty()) sharedRows else scopedRows).map { it.toDomain() }
            }.distinctUntilChanged()
        )
    }

    private fun MediaStatusTag.normalized(): MediaStatusTag {
        require(name.trim().isNotEmpty()) { "Media status tag name cannot be blank" }
        val trimmed = name.trim()
        require(!MediaStatusTags.isSystem(trimmed)) { "Media status tag duplicates a system tag" }
        return copy(name = trimmed)
    }

    private fun MediaStatusTag.toEntity() = MediaStatusTagEntity(
        id = id,
        projectId = projectId,
        name = name,
        normalizedName = MediaStatusTags.normalize(name),
        createdAtEpochMs = createdAtEpochMs
    )

    private fun MediaStatusTagEntity.toDomain() = MediaStatusTag(
        id = id,
        projectId = projectId,
        name = name,
        createdAtEpochMs = createdAtEpochMs
    )

    private suspend fun dao(projectId: String): MediaStatusTagDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.mediaStatusTagDao() ?: dao

    private suspend fun writeToSharedAndScoped(
        sharedWrite: suspend () -> Unit,
        scopedWrite: suspend () -> Unit?
    ) {
        val failures = mutableListOf<Throwable>()
        var success = false
        runCatching { sharedWrite() }
            .onSuccess { success = true }
            .onFailure { failures += it }
        runCatching { scopedWrite() }
            .onSuccess { if (it != null) success = true }
            .onFailure { failures += it }
        if (!success && failures.isNotEmpty()) throw failures.first()
    }
}
