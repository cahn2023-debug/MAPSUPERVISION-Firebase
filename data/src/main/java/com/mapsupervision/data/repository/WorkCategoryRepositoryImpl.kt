package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.repository.WorkCategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class WorkCategoryRepositoryImpl @Inject constructor(
    private val dao: WorkCategoryDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : WorkCategoryRepository {
    override suspend fun add(category: WorkCategory): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val entity = category.toEntity()
        val projectDatabase = projectScopedDatabaseProvider.databaseFor(category.projectId)
        writeToSharedAndScoped(
            sharedWrite = { dao.upsert(entity) },
            scopedWrite = { projectDatabase?.workCategoryDao()?.upsert(entity) }
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add work category", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = withContext(Dispatchers.IO) { runCatching {
        val rows = dao(projectId).byProject(projectId)
        val resolvedRows = if (rows.isEmpty()) dao.byProject(projectId) else rows
        resolvedRows.map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list work categories", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<WorkCategory>> = flow {
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

    private fun WorkCategory.toEntity() = WorkCategoryEntity(id, projectId, name, unit, createdAtEpochMs)
    private fun WorkCategoryEntity.toDomain() = WorkCategory(id, projectId, name, unit, createdAtEpochMs)

    private suspend fun dao(projectId: String): WorkCategoryDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.workCategoryDao() ?: dao

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
