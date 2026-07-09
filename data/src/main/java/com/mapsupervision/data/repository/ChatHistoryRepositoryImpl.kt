package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.ChatHistoryDao
import com.mapsupervision.data.db.entity.ChatHistoryEntity
import com.mapsupervision.domain.model.ChatHistoryMessage
import com.mapsupervision.domain.repository.ChatHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatHistoryRepositoryImpl @Inject constructor(
    private val dao: ChatHistoryDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : ChatHistoryRepository {
    override suspend fun append(message: ChatHistoryMessage): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(message.projectId).upsert(message.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to append chat history", it)) }
    ) }

    override suspend fun listByProject(projectId: String): AppResult<List<ChatHistoryMessage>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list chat history", it)) }
    ) }

    override suspend fun listRecentByProject(projectId: String, limit: Int): AppResult<List<ChatHistoryMessage>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).recentByProject(projectId, limit).map { it.toDomain() }.reversed()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list recent chat history", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<ChatHistoryMessage>> = flow {
        emitAll(dao(projectId).observeByProject(projectId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    override suspend fun clearByProject(projectId: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).clearByProject(projectId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to clear chat history", it)) }
    ) }

    private suspend fun dao(projectId: String): ChatHistoryDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.chatHistoryDao() ?: dao

    private fun ChatHistoryMessage.toEntity() = ChatHistoryEntity(id, projectId, role, text, createdAtEpochMs)
    private fun ChatHistoryEntity.toDomain() = ChatHistoryMessage(id, projectId, role, text, createdAtEpochMs)
}
