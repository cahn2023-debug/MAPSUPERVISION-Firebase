package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.MaterialHandoverDao
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MaterialHandoverRepositoryImpl @Inject constructor(
    private val dao: MaterialHandoverDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : MaterialHandoverRepository {

    override suspend fun add(handover: MaterialHandover): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = MaterialHandoverEntity.fromDomain(handover)
            val projectDatabase = projectScopedDatabaseProvider.databaseFor(handover.projectId)
            writeToSharedAndScoped(
                sharedWrite = { upsertSafely(dao, entity) },
                scopedWrite = { projectDatabase?.materialHandoverDao()?.let { upsertSafely(it, entity) } }
            )
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to add material handover", it)) }
        )
    }

    override suspend fun delete(handover: MaterialHandover): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = MaterialHandoverEntity.fromDomain(handover)
            val projectDatabase = projectScopedDatabaseProvider.databaseFor(handover.projectId)
            writeToSharedAndScoped(
                sharedWrite = { dao.delete(entity) },
                scopedWrite = { projectDatabase?.materialHandoverDao()?.delete(entity) }
            )
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to delete material handover", it)) }
        )
    }

    override suspend fun byProject(projectId: String): AppResult<List<MaterialHandover>> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = dao(projectId).byProject(projectId)
            val resolvedRows = if (rows.isEmpty()) dao.byProject(projectId) else rows
            resolvedRows.map { it.toDomain() }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to list material handovers", it)) }
        )
    }

    override fun observeByProject(projectId: String): Flow<List<MaterialHandover>> = flow {
        val scopedDao = dao(projectId)
        emitAll(
            combine(
                scopedDao.observeByProject(projectId),
                dao.observeByProject(projectId)
            ) { scopedRows, sharedRows ->
                val resolvedRows = if (scopedRows.isEmpty()) sharedRows else scopedRows
                resolvedRows.map { it.toDomain() }
            }.distinctUntilChanged()
        )
    }

    private suspend fun dao(projectId: String): MaterialHandoverDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.materialHandoverDao() ?: dao

    private suspend fun upsertSafely(
        targetDao: MaterialHandoverDao,
        entity: MaterialHandoverEntity
    ) {
        runCatching {
            targetDao.upsert(entity)
        }.getOrElse { failure ->
            val withoutOptionalLinks = entity.copy(
                nodeId = null,
                materialDeclarationId = null,
                workCategoryId = null
            )
            if (withoutOptionalLinks == entity) {
                throw failure
            }
            targetDao.upsert(withoutOptionalLinks)
        }
    }

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

        if (!success && failures.isNotEmpty()) {
            throw failures.first()
        }
    }
}
