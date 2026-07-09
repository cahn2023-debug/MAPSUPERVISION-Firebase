package com.mapsupervision.data.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.MaterialDeclarationDao
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MaterialDeclarationRepositoryImpl @Inject constructor(
    private val dao: MaterialDeclarationDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : MaterialDeclarationRepository {

    override suspend fun add(declaration: MaterialDeclaration): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = MaterialDeclarationEntity.fromDomain(declaration)
            val projectDatabase = projectScopedDatabaseProvider.databaseFor(declaration.projectId)
            writeToSharedAndScoped(
                sharedWrite = { dao.insert(entity) },
                scopedWrite = { projectDatabase?.materialDeclarationDao()?.insert(entity) }
            )
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to add material declaration", it)) }
        )
    }

    override suspend fun delete(declaration: MaterialDeclaration): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = MaterialDeclarationEntity.fromDomain(declaration)
            val projectDatabase = projectScopedDatabaseProvider.databaseFor(declaration.projectId)
            writeToSharedAndScoped(
                sharedWrite = { dao.delete(entity) },
                scopedWrite = { projectDatabase?.materialDeclarationDao()?.delete(entity) }
            )
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to delete material declaration", it)) }
        )
    }

    override suspend fun getByProject(projectId: String): AppResult<List<MaterialDeclaration>> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = dao(projectId).getByProject(projectId)
            val resolvedRows = if (rows.isEmpty()) dao.getByProject(projectId) else rows
            resolvedRows.map { it.toDomain() }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to list material declarations", it)) }
        )
    }

    override fun observeByProject(projectId: String): Flow<List<MaterialDeclaration>> {
        return flow {
            emitAll(
                dao(projectId).observeByProject(projectId)
                    .map { rows ->
                        val resolvedRows = if (rows.isEmpty()) dao.getByProject(projectId) else rows
                        resolvedRows.map { it.toDomain() }
                    }
                    .distinctUntilChanged()
            )
        }
    }

    private suspend fun dao(projectId: String): MaterialDeclarationDao {
        return projectScopedDatabaseProvider.databaseFor(projectId)?.materialDeclarationDao() ?: dao
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
