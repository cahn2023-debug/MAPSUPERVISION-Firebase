package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.TaskDao
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: com.mapsupervision.domain.repository.ActiveProjectRepository
) : TaskRepository {
    override suspend fun upsert(task: Task): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val database = projectScopedDatabaseProvider.databaseFor(task.projectId)
        val resolvedNodeId = task.objectNodeId
            ?: database?.gisNodeDao()?.byProject(task.projectId)?.find { it.code == task.objectCode }?.id
        val resolvedRouteId = task.objectRouteId ?: if (resolvedNodeId == null) {
            database?.gisRouteDao()?.byProject(task.projectId)?.find { it.code == task.objectCode }?.id
        } else {
            null
        }

        val normalized = task.copy(
            objectNodeId = resolvedNodeId,
            objectRouteId = resolvedRouteId,
            updatedAtEpochMs = if (task.updatedAtEpochMs == 0L) System.currentTimeMillis() else task.updatedAtEpochMs
        )
        val entity = TaskEntity.fromDomain(normalized)
        writeToSharedAndScoped(
            sharedWrite = { dao.upsert(entity) },
            scopedWrite = { database?.taskDao()?.upsert(entity) }
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert task", it)) }
    ) }

    override suspend fun delete(taskId: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        val now = System.currentTimeMillis()
        val database = activeProjectId?.let { projectScopedDatabaseProvider.databaseFor(it) }
        writeToSharedAndScoped(
            sharedWrite = { dao.markDeletedById(taskId, now, now) },
            scopedWrite = { database?.taskDao()?.markDeletedById(taskId, now, now) }
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to delete task", it)) }
    ) }

    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>> = withContext(Dispatchers.IO) { runCatching {
        val rows = dao(projectId).byObject(projectId, objectCode)
        val resolvedRows = if (rows.isEmpty()) dao.byObject(projectId, objectCode) else rows
        hydrateTasks(projectId, resolvedRows)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list tasks by object", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<Task>> = withContext(Dispatchers.IO) { runCatching {
        val rows = dao(projectId).byProject(projectId)
        val resolvedRows = if (rows.isEmpty()) dao.byProject(projectId) else rows
        hydrateTasks(projectId, resolvedRows)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list tasks by project", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<Task>> = flow {
        val scopedDao = dao(projectId)
        emitAll(
            combine(
                scopedDao.observeByProject(projectId),
                dao.observeByProject(projectId)
            ) { scopedRows, sharedRows ->
                val resolvedRows = if (scopedRows.isEmpty()) sharedRows else scopedRows
                hydrateTasks(projectId, resolvedRows)
            }
                .distinctUntilChanged()
        )
    }

    private suspend fun hydrateTasks(projectId: String, entities: List<TaskEntity>): List<Task> {
        if (entities.isEmpty()) return emptyList()
        val database = projectScopedDatabaseProvider.databaseFor(projectId)
        val nodeDao = database?.gisNodeDao()
        val routeDao = database?.gisRouteDao()
        val nodeCodeMap = nodeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        val routeCodeMap = routeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        return entities.map { entity ->
            val resolvedCode = entity.objectNodeId?.let { nodeCodeMap[it] }
                ?: entity.objectRouteId?.let { routeCodeMap[it] }
                ?: ""
            entity.toDomain().copy(objectCode = resolvedCode)
        }
    }

    private suspend fun dao(projectId: String): TaskDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.taskDao() ?: dao

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
