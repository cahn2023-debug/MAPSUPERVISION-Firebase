package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import javax.inject.Inject
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class GisRepositoryImpl @Inject constructor(
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val sharedDatabase: MapSupervisionDatabase,
    private val activeProjectRepository: ActiveProjectRepository
) : GisRepository {
    override suspend fun upsertNode(node: GisNode): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val normalized = node.copy(updatedAtEpochMs = if (node.updatedAtEpochMs == 0L) System.currentTimeMillis() else node.updatedAtEpochMs)
        nodeDao(node.projectId).upsert(normalized.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert node", it)) }
    ) }

    override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val normalized = route.copy(updatedAtEpochMs = if (route.updatedAtEpochMs == 0L) System.currentTimeMillis() else route.updatedAtEpochMs)
        routeDao(route.projectId).upsert(normalized.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert route", it)) }
    ) }

    override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        if (nodes.isNotEmpty()) nodeDao(nodes.first().projectId).upsertAll(nodes.map {
            it.copy(updatedAtEpochMs = if (it.updatedAtEpochMs == 0L) System.currentTimeMillis() else it.updatedAtEpochMs).toEntity()
        })
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to bulk upsert nodes", it)) }
    ) }

    override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        if (routes.isNotEmpty()) routeDao(routes.first().projectId).upsertAll(routes.map {
            it.copy(updatedAtEpochMs = if (it.updatedAtEpochMs == 0L) System.currentTimeMillis() else it.updatedAtEpochMs).toEntity()
        })
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to bulk upsert routes", it)) }
    ) }

    override suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<GisNode>, routes: List<GisRoute>): AppResult<Unit> =
        withContext(Dispatchers.IO) { runCatching {
            val projectId = nodes.firstOrNull()?.projectId
                ?: routes.firstOrNull()?.projectId
                ?: (activeProjectRepository.getActive() as? AppResult.Success)?.data
                ?: throw IllegalStateException("Active project is required to replace imported geometry")

            val normalizedNodes = nodes.map { it.copy(projectId = projectId, importedFileId = importedFileId) }
            val normalizedRoutes = routes.map { it.copy(projectId = projectId, importedFileId = importedFileId) }
            val db = databaseFor(projectId)
            val now = System.currentTimeMillis()
            db.withTransaction {
                db.gisNodeDao().markDeletedByImportedFileId(projectId, importedFileId, now, now)
                db.gisRouteDao().markDeletedByImportedFileId(projectId, importedFileId, now, now)
                if (normalizedNodes.isNotEmpty()) db.gisNodeDao().upsertAll(normalizedNodes.map { it.toEntity() })
                if (normalizedRoutes.isNotEmpty()) db.gisRouteDao().upsertAll(normalizedRoutes.map { it.toEntity() })
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to replace imported geometry", it)) }
        ) }

    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = withContext(Dispatchers.IO) { runCatching {
        val dao = nodeDao(projectId)
        val trimmedQuery = query.trim()
        val rows = when {
            trimmedQuery.isBlank() -> dao.byProject(projectId)
            else -> dao.searchFast(projectId, trimmedQuery).ifEmpty { dao.search(projectId, trimmedQuery) }
        }
        rows.map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to search nodes", it)) }
    ) }

    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = withContext(Dispatchers.IO) { runCatching {
        val dao = routeDao(projectId)
        val trimmedQuery = query.trim()
        val rows = when {
            trimmedQuery.isBlank() -> dao.byProject(projectId)
            else -> dao.searchFast(projectId, trimmedQuery).ifEmpty { dao.search(projectId, trimmedQuery) }
        }
        rows.map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to search routes", it)) }
    ) }

    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = withContext(Dispatchers.IO) { runCatching {
        val entity = nodeDao(projectId).findByCode(projectId, code)
        entity?.toDomain()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to find node by code", it)) }
    ) }

    override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = flow {
        val dao = nodeDao(projectId)
        val source = if (query.isBlank()) dao.observeByProject(projectId) else dao.observeSearch(projectId, query)
        emitAll(source.map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = flow {
        val dao = routeDao(projectId)
        val source = if (query.isBlank()) dao.observeByProject(projectId) else dao.observeSearch(projectId, query)
        emitAll(source.map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    private fun GisNode.toEntity() = GisNodeEntity(
        id = id,
        projectId = projectId,
        code = code,
        contractor = contractor,
        latitude = latitude,
        longitude = longitude,
        mapNumberLabel = mapNumberLabel,
        workVolumeSummary = workVolumeSummary,
        importedFileId = importedFileId,
        ipAddress = ipAddress,
        subnet = subnet,
        gateway = gateway,
        signalStatus = signalStatus,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )
    private fun GisRoute.toEntity() = GisRouteEntity(
        id = id,
        projectId = projectId,
        code = code,
        contractor = contractor,
        startNodeCode = startNodeCode,
        endNodeCode = endNodeCode,
        points = points,
        importedFileId = importedFileId,
        designLength = designLength,
        fiberCoreCount = fiberCoreCount,
        fiberConnection = fiberConnection,
        startNodeId = startNodeId,
        endNodeId = endNodeId,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )
    private fun GisNodeEntity.toDomain() = GisNode(
        id = id,
        projectId = projectId,
        code = code,
        contractor = contractor,
        latitude = latitude,
        longitude = longitude,
        mapNumberLabel = mapNumberLabel,
        workVolumeSummary = workVolumeSummary,
        importedFileId = importedFileId,
        ipAddress = ipAddress,
        subnet = subnet,
        gateway = gateway,
        signalStatus = signalStatus,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )
    private fun GisRouteEntity.toDomain() = GisRoute(
        id = id,
        projectId = projectId,
        code = code,
        contractor = contractor,
        startNodeCode = startNodeCode,
        endNodeCode = endNodeCode,
        points = points,
        importedFileId = importedFileId,
        designLength = designLength,
        fiberCoreCount = fiberCoreCount,
        fiberConnection = fiberConnection,
        startNodeId = startNodeId,
        endNodeId = endNodeId,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private suspend fun databaseFor(projectId: String): MapSupervisionDatabase =
        projectScopedDatabaseProvider.databaseFor(projectId) ?: sharedDatabase

    private suspend fun nodeDao(projectId: String): GisNodeDao =
        databaseFor(projectId).gisNodeDao()

    private suspend fun routeDao(projectId: String): GisRouteDao =
        databaseFor(projectId).gisRouteDao()
}

