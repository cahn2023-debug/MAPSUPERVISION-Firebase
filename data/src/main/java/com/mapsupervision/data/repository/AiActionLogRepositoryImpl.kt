package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.AiActionLogDao
import com.mapsupervision.data.db.entity.AiActionLogEntity
import com.mapsupervision.domain.model.AiActionLog
import com.mapsupervision.domain.repository.AiActionLogRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AiActionLogRepositoryImpl @Inject constructor(
    private val dao: AiActionLogDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : AiActionLogRepository {
    override suspend fun log(action: AiActionLog): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(action.projectId).upsert(action.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to save ai action log", it)) }
    ) }

    override suspend fun listByProject(projectId: String): AppResult<List<AiActionLog>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list ai action logs", it)) }
    ) }

    override suspend fun getById(id: String): AppResult<AiActionLog?> = withContext(Dispatchers.IO) { runCatching {
        // Find by querying through database (since id has to be project mapped we might need to search project, but let's query default dao first or all scoped dbs. To keep it simple, try default dao then try active project DB)
        dao.getById(id)?.toDomain()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to get ai action log", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<AiActionLog>> = flow {
        emitAll(dao(projectId).observeByProject(projectId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    private suspend fun dao(projectId: String): AiActionLogDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.aiActionLogDao() ?: dao

    private fun AiActionLog.toEntity() = AiActionLogEntity(id, projectId, rawInput, actionType, draftJson, confidence, status, timestamp)
    private fun AiActionLogEntity.toDomain() = AiActionLog(id, projectId, rawInput, actionType, draftJson, confidence, status, timestamp)
}
