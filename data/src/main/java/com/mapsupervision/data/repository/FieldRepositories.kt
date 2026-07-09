package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.DailyLogDao
import com.mapsupervision.data.db.dao.DailyLogLineDao
import com.mapsupervision.data.db.dao.DailyLogNodeDao
import com.mapsupervision.data.db.dao.DailyLogPhotoDao
import com.mapsupervision.data.db.dao.WorkVolumeProgressDao
import com.mapsupervision.data.db.dao.NodeProgressDao
import com.mapsupervision.data.db.dao.PhotoTagDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.dao.SitePhotoProjection
import com.mapsupervision.data.db.dao.MaterialProgressProjection
import com.mapsupervision.data.db.dao.WorkPlanDao
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.DailyLogLineEntity
import com.mapsupervision.data.db.entity.DailyLogNodeEntity
import com.mapsupervision.data.db.entity.DailyLogPhotoEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.PhotoTagEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.domain.model.joinCsvList
import com.mapsupervision.domain.model.parseCsvList
import com.mapsupervision.domain.model.resolvedAppliedNodeIds
import com.mapsupervision.domain.model.resolvedLinkedPhotoIds
import com.mapsupervision.domain.model.resolvedTagCodes
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.DailyLogLine
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.WorkPlanRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class ProgressRepositoryImpl @Inject constructor(
    private val dao: NodeProgressDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : ProgressRepository {
    override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(progress.projectId).upsert(progress.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert progress", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = withContext(Dispatchers.IO) { runCatching {
        hydrateNodeProgress(projectId, dao(projectId).byProject(projectId))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list progress", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<NodeProgress>> = flow {
        emitAll(dao(projectId).observeByProject(projectId).map { rows -> hydrateNodeProgress(projectId, rows) }.distinctUntilChanged())
    }

    private suspend fun hydrateNodeProgress(projectId: String, entities: List<NodeProgressEntity>): List<NodeProgress> {
        if (entities.isEmpty()) return emptyList()
        val database = projectScopedDatabaseProvider.databaseFor(projectId)
        val nodeDao = database?.gisNodeDao()
        val nodeMap = nodeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        return entities.map { entity ->
            entity.toDomain(nodeMap[entity.nodeId.orEmpty()].orEmpty())
        }
    }

    private fun NodeProgress.toEntity() = NodeProgressEntity(
        id = id,
        projectId = projectId,
        planned = planned,
        actual = actual,
        remain = remain,
        delayed = delayed,
        updatedAtEpochMs = updatedAtEpochMs,
        nodeId = nodeId,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )
    private fun NodeProgressEntity.toDomain(nodeCode: String) = NodeProgress(
        id = id,
        projectId = projectId,
        nodeCode = nodeCode,
        planned = planned,
        actual = actual,
        remain = remain,
        delayed = delayed,
        updatedAtEpochMs = updatedAtEpochMs,
        nodeId = nodeId,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private suspend fun dao(projectId: String): NodeProgressDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.nodeProgressDao() ?: dao
}

class PhotoRepositoryImpl @Inject constructor(
    private val dao: SitePhotoDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : PhotoRepository {
    override suspend fun add(photo: SitePhoto): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val normalized = photo.normalizeForStorage()
        val database = scopedDatabase(photo.projectId)
        
        val projectNodes = database?.gisNodeDao()?.byProject(photo.projectId).orEmpty()
        val projectRoutes = database?.gisRouteDao()?.byProject(photo.projectId).orEmpty()
        val explicitNodeId = normalized.matchedNodeId
            ?: normalized.matchedNodeCode?.let { code -> projectNodes.find { it.code == code }?.id }
        val explicitRouteId = normalized.matchedRouteId
            ?: normalized.matchedRouteCode?.let { code -> projectRoutes.find { it.code == code }?.id }
        val resolvedNodeId: String?
        val resolvedRouteId: String?
        when {
            explicitNodeId != null -> {
                resolvedNodeId = explicitNodeId
                resolvedRouteId = null
            }
            explicitRouteId != null -> {
                resolvedNodeId = null
                resolvedRouteId = explicitRouteId
            }
            else -> {
                val fallbackNodeId = projectNodes.find { it.code == normalized.objectCode }?.id
                val fallbackRouteId = projectRoutes.find { it.code == normalized.objectCode }?.id
                if (fallbackNodeId != null) {
                    resolvedNodeId = fallbackNodeId
                    resolvedRouteId = null
                } else {
                    resolvedNodeId = null
                    resolvedRouteId = fallbackRouteId
                }
            }
        }

        val finalPhoto = normalized.copy(
            matchedNodeId = resolvedNodeId,
            matchedRouteId = resolvedRouteId
        )
        val photoDao = database?.sitePhotoDao() ?: dao
        photoDao.upsert(finalPhoto.toEntity())
        database?.photoTagDao()?.deleteForPhoto(photo.projectId, photo.id)
        val tags = normalized.resolvedTagCodes
        if (database != null && tags.isNotEmpty()) {
            database.photoTagDao().upsertAll(tags.mapIndexed { index, tag ->
                PhotoTagEntity(
                    id = "${photo.id}:$index:$tag",
                    projectId = photo.projectId,
                    photoId = photo.id,
                    tagCode = tag,
                    createdAtEpochMs = normalized.updatedAtEpochMs
                )
            })
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add photo", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = withContext(Dispatchers.IO) { runCatching {
        hydratePhotos(projectId, dao(projectId).byProjectSummary(projectId))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list photos", it)) }
    ) }

    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val database = scopedDatabase(projectId)
                val rows = (database?.sitePhotoDao() ?: dao).byObjectCodeSummary(projectId, objectCode)
                hydratePhotos(projectId, rows, database)
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(DatabaseException("Failed to list photos by object", it)) }
            )
        }

    override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flow {
        val database = scopedDatabase(projectId)
        val photoDao = database?.sitePhotoDao() ?: dao
        emitAll(photoDao.observeByProjectSummary(projectId).map { rows -> hydratePhotos(projectId, rows, database) }.distinctUntilChanged())
    }

    private suspend fun dao(projectId: String): SitePhotoDao =
        scopedDatabase(projectId)?.sitePhotoDao() ?: dao

    private suspend fun scopedDatabase(projectId: String) =
        projectScopedDatabaseProvider.databaseFor(projectId)

    private suspend fun hydratePhotos(
        projectId: String,
        rows: List<SitePhotoProjection>,
        database: com.mapsupervision.data.db.MapSupervisionDatabase? = null
    ): List<SitePhoto> {
        if (rows.isEmpty()) return emptyList()
        val resolvedDatabase = database ?: scopedDatabase(projectId)
        val tagsByPhotoId = resolvedDatabase?.photoTagDao()
            ?.byPhotoIds(projectId, rows.map { it.id })
            ?.groupBy { it.photoId }
            .orEmpty()
        return rows.map { row ->
            val tags = tagsByPhotoId[row.id]?.map { it.tagCode }.orEmpty()
            row.toDomain(tags)
        }
    }

    private fun SitePhoto.normalizeForStorage(): SitePhoto {
        val normalizedTags = if (tagCodes.isNotEmpty()) tagCodes else parseCsvList(tagCodesCsv)
        val normalizedUpdatedAt = if (updatedAtEpochMs == 0L) System.currentTimeMillis() else updatedAtEpochMs
        return copy(
            tagCodesCsv = joinCsvList(normalizedTags),
            tagCodes = normalizedTags,
            updatedAtEpochMs = normalizedUpdatedAt
        )
    }

    private fun SitePhoto.toEntity() = SitePhotoEntity(
        id = id,
        projectId = projectId,
        objectCode = objectCode,
        tagCodesCsv = tagCodesCsv,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        latitude = latitude,
        longitude = longitude,
        locationAccuracyM = locationAccuracyM,
        isGpsMocked = isGpsMocked,
        locationStatus = locationStatus,
        engineer = engineer,
        capturedAtEpochMs = capturedAtEpochMs,
        matchedAtEpochMs = matchedAtEpochMs,
        matchingTimeOffsetMs = matchingTimeOffsetMs,
        mediaType = mediaType,
        mimeType = mimeType,
        durationMs = durationMs,
        address = address,
        captureNote = captureNote,
        matchedNodeId = matchedNodeId,
        matchedRouteId = matchedRouteId,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
        remoteUrl = remoteUrl,
        lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )
    private fun SitePhotoProjection.toDomain(tags: List<String>) = SitePhoto(
        id = id,
        projectId = projectId,
        objectCode = objectCode,
        tagCodesCsv = if (tags.isNotEmpty()) joinCsvList(tags) else tagCodesCsv,
        tagCodes = if (tags.isNotEmpty()) tags else parseCsvList(tagCodesCsv),
        matchedNodeCode = matchedNodeCode,
        matchedRouteCode = matchedRouteCode,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        latitude = latitude,
        longitude = longitude,
        locationAccuracyM = null,
        isGpsMocked = false,
        locationStatus = com.mapsupervision.domain.model.PhotoLocationStatus.MISSING,
        engineer = engineer,
        capturedAtEpochMs = capturedAtEpochMs,
        matchedAtEpochMs = matchedAtEpochMs,
        matchingTimeOffsetMs = matchingTimeOffsetMs,
        mediaType = mediaType,
        mimeType = mimeType,
        durationMs = durationMs,
        address = address,
        captureNote = captureNote,
        matchedNodeId = matchedNodeId,
        matchedRouteId = matchedRouteId,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
        remoteUrl = remoteUrl,
        lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
        isDeleted = false,
        deletedAtEpochMs = null
    )
}

class DailyLogRepositoryImpl @Inject constructor(
    private val dao: DailyLogDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val sharedDatabase: MapSupervisionDatabase
) : DailyLogRepository {
    override suspend fun add(log: DailyLog): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val projectDatabase = scopedDatabase(log.projectId)
        val normalized = log.normalizeForStorage()
        val lineEntities = (normalized.lines.ifEmpty { log.lines }).map { DailyLogLineEntity.fromDomain(it) }
        val nodeEntities = normalized.resolvedAppliedNodeIds.mapIndexed { index, nodeId ->
            DailyLogNodeEntity(
                id = "${log.id}:node:$index:$nodeId",
                projectId = log.projectId,
                dailyLogId = log.id,
                nodeId = nodeId,
                nodeCodeSnapshot = nodeId,
                createdAtEpochMs = normalized.updatedAtEpochMs
            )
        }
        val photoEntities = normalized.resolvedLinkedPhotoIds.mapIndexed { index, photoId ->
            DailyLogPhotoEntity(
                id = "${log.id}:photo:$index:$photoId",
                projectId = log.projectId,
                dailyLogId = log.id,
                photoId = photoId,
                createdAtEpochMs = normalized.updatedAtEpochMs
            )
        }
        writeToSharedAndScoped(
            projectDatabase = projectDatabase,
            sharedWrite = {
                dao.upsert(normalized.toEntity())
                writeDailyLogChildren(
                    dailyLogLineDao = sharedDatabase.dailyLogLineDao(),
                    dailyLogNodeDao = sharedDatabase.dailyLogNodeDao(),
                    dailyLogPhotoDao = sharedDatabase.dailyLogPhotoDao(),
                    projectId = log.projectId,
                    logId = log.id,
                    lineEntities = lineEntities,
                    nodeEntities = nodeEntities,
                    photoEntities = photoEntities
                )
            },
            scopedWrite = {
                val scopedDailyLogDao = projectDatabase?.dailyLogDao() ?: return@writeToSharedAndScoped
                scopedDailyLogDao.upsert(normalized.toEntity())
                writeDailyLogChildren(
                    dailyLogLineDao = projectDatabase.dailyLogLineDao(),
                    dailyLogNodeDao = projectDatabase.dailyLogNodeDao(),
                    dailyLogPhotoDao = projectDatabase.dailyLogPhotoDao(),
                    projectId = log.projectId,
                    logId = log.id,
                    lineEntities = lineEntities,
                    nodeEntities = nodeEntities,
                    photoEntities = photoEntities
                )
            }
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add daily log", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = withContext(Dispatchers.IO) { runCatching {
        hydrateDailyLogs(projectId, dao(projectId).byProject(projectId))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list daily logs", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<DailyLog>> = flow {
        val database = scopedDatabase(projectId)
        val dailyLogDao = database?.dailyLogDao() ?: dao
        emitAll(dailyLogDao.observeByProject(projectId).map { rows -> hydrateDailyLogs(projectId, rows, database) }.distinctUntilChanged())
    }

    private suspend fun scopedDatabase(projectId: String) =
        projectScopedDatabaseProvider.databaseFor(projectId)

    private suspend fun hydrateDailyLogs(
        projectId: String,
        rows: List<DailyLogEntity>,
        database: com.mapsupervision.data.db.MapSupervisionDatabase? = null
    ): List<DailyLog> {
        if (rows.isEmpty()) return emptyList()
        val resolvedDatabase = database ?: scopedDatabase(projectId)
        val nodesByLogId = resolvedDatabase?.dailyLogNodeDao()
            ?.byLogIds(projectId, rows.map { it.id })
            ?.groupBy { it.dailyLogId }
            .orEmpty()
        val photosByLogId = resolvedDatabase?.dailyLogPhotoDao()
            ?.byLogIds(projectId, rows.map { it.id })
            ?.groupBy { it.dailyLogId }
            .orEmpty()
        val linesByLogId = resolvedDatabase?.dailyLogLineDao()
            ?.byLogIds(projectId, rows.map { it.id })
            ?.groupBy { it.dailyLogId }
            .orEmpty()
        val nodeDao = resolvedDatabase?.gisNodeDao()
        val routeDao = resolvedDatabase?.gisRouteDao()
        val nodeCodeMap = nodeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        val routeCodeMap = routeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        return rows.map { row ->
            val nodeIds = nodesByLogId[row.id]?.mapNotNull { it.nodeId ?: it.nodeCodeSnapshot.takeIf(String::isNotBlank) }.orEmpty()
            val photoIds = photosByLogId[row.id]?.map { it.photoId }.orEmpty()
            val resolvedNodeCodes = nodeIds.map { nodeCodeMap[it] ?: it }
            row.toDomain(
                nodeIds = nodeIds,
                photoIds = photoIds,
                nodeCode = row.nodeId?.let { nodeCodeMap[it] },
                routeCode = row.routeId?.let { routeCodeMap[it] },
                plannedNodeCode = row.plannedNodeId?.let { nodeCodeMap[it] },
                plannedRouteCode = row.plannedRouteId?.let { routeCodeMap[it] },
                resolvedNodeCodes = resolvedNodeCodes,
                lines = linesByLogId[row.id].orEmpty().map { line ->
                    line.toDomain(
                        nodeCode = line.nodeId?.let { nodeCodeMap[it] },
                        routeCode = line.routeId?.let { routeCodeMap[it] }
                    )
                }
            )
        }
    }

    private fun DailyLog.normalizeForStorage(): DailyLog {
        val normalizedNodeIds = if (appliedNodeIds.isNotEmpty()) appliedNodeIds else parseCsvList(appliedNodeCodesCsv)
        val normalizedPhotoIds = if (linkedPhotoIds.isNotEmpty()) linkedPhotoIds else parseCsvList(linkedPhotoIdsCsv)
        val normalizedUpdatedAt = if (updatedAtEpochMs == 0L) System.currentTimeMillis() else updatedAtEpochMs
        return copy(
            appliedNodeCodesCsv = joinCsvList(normalizedNodeIds),
            linkedPhotoIdsCsv = joinCsvList(normalizedPhotoIds),
            appliedNodeIds = normalizedNodeIds,
            linkedPhotoIds = normalizedPhotoIds,
            lines = if (lines.isNotEmpty()) {
                lines.map { line ->
                    val normalizedLineUpdatedAt = if (line.updatedAtEpochMs == 0L) normalizedUpdatedAt else line.updatedAtEpochMs
                    line.copy(updatedAtEpochMs = normalizedLineUpdatedAt)
                }
            } else {
                emptyList()
            },
            updatedAtEpochMs = normalizedUpdatedAt
        )
    }

    private fun DailyLog.toEntity() = DailyLogEntity(
        id = id,
        projectId = projectId,
        workItem = workItem,
        manpower = manpower,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
        weather = weather,
        temperature = temperature,
        dateEpochDay = dateEpochDay,
        volume = volume,
        unit = unit,
        categoryName = categoryName,
        batchGroupId = batchGroupId,
        linkedWorkPlanId = linkedWorkPlanId,
        plannedWorkName = plannedWorkName,
        plannedQuantity = plannedQuantity,
        plannedUnit = plannedUnit,
        photoMatchOffsetMinutes = photoMatchOffsetMinutes,
        nodeId = nodeId,
        routeId = routeId,
        plannedNodeId = plannedNodeId,
        plannedRouteId = plannedRouteId,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private fun DailyLogEntity.toDomain(
        nodeIds: List<String>,
        photoIds: List<String>,
        nodeCode: String?,
        routeCode: String?,
        plannedNodeCode: String?,
        plannedRouteCode: String?,
        resolvedNodeCodes: List<String>,
        lines: List<DailyLogLine>
    ) = DailyLog(
        id = id,
        projectId = projectId,
        workItem = workItem,
        manpower = manpower,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
        weather = weather,
        temperature = temperature,
        nodeCode = nodeCode,
        routeCode = routeCode,
        dateEpochDay = dateEpochDay,
        volume = volume,
        unit = unit,
        categoryName = categoryName,
        batchGroupId = batchGroupId,
        linkedWorkPlanId = linkedWorkPlanId,
        plannedWorkName = plannedWorkName,
        plannedQuantity = plannedQuantity,
        plannedUnit = plannedUnit,
        plannedNodeCode = plannedNodeCode,
        plannedRouteCode = plannedRouteCode,
        appliedNodeCodesCsv = joinCsvList(resolvedNodeCodes),
        linkedPhotoIdsCsv = joinCsvList(photoIds),
        appliedNodeIds = nodeIds,
        linkedPhotoIds = photoIds,
        photoMatchOffsetMinutes = photoMatchOffsetMinutes,
        nodeId = nodeId,
        routeId = routeId,
        plannedNodeId = plannedNodeId,
        plannedRouteId = plannedRouteId,
        lines = lines,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private suspend fun dao(projectId: String): DailyLogDao =
        scopedDatabase(projectId)?.dailyLogDao() ?: dao

    private suspend fun writeDailyLogChildren(
        dailyLogLineDao: DailyLogLineDao?,
        dailyLogNodeDao: DailyLogNodeDao?,
        dailyLogPhotoDao: DailyLogPhotoDao?,
        projectId: String,
        logId: String,
        lineEntities: List<DailyLogLineEntity>,
        nodeEntities: List<DailyLogNodeEntity>,
        photoEntities: List<DailyLogPhotoEntity>
    ) {
        dailyLogLineDao?.deleteForLog(projectId, logId)
        if (dailyLogLineDao != null && lineEntities.isNotEmpty()) {
            dailyLogLineDao.upsertAll(lineEntities)
        }
        dailyLogNodeDao?.deleteForLog(projectId, logId)
        if (dailyLogNodeDao != null && nodeEntities.isNotEmpty()) {
            dailyLogNodeDao.upsertAll(nodeEntities)
        }
        dailyLogPhotoDao?.deleteForLog(projectId, logId)
        if (dailyLogPhotoDao != null && photoEntities.isNotEmpty()) {
            dailyLogPhotoDao.upsertAll(photoEntities)
        }
    }

    private suspend fun writeToSharedAndScoped(
        projectDatabase: com.mapsupervision.data.db.MapSupervisionDatabase?,
        sharedWrite: suspend () -> Unit,
        scopedWrite: suspend () -> Unit
    ) {
        val failures = mutableListOf<Throwable>()
        var success = false
        runCatching { sharedWrite() }
            .onSuccess { success = true }
            .onFailure { failures += it }
        if (projectDatabase != null) {
            runCatching { scopedWrite() }
                .onSuccess { success = true }
                .onFailure { failures += it }
        }
        if (!success && failures.isNotEmpty()) {
            throw failures.first()
        }
    }
}

class WorkVolumeProgressRepositoryImpl @Inject constructor(
    private val dao: WorkVolumeProgressDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : WorkVolumeProgressRepository {
    override suspend fun upsert(progress: WorkVolumeProgress): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val dao = dao(progress.projectId)
        val existing = dao.findByNaturalKey(progress.projectId, progress.nodeCode, progress.workName)
        // Preserve existing plannedQty if the incoming value is 0 (parse failed) but DB already has a valid value
        val safePlannedQty = if (progress.plannedQty <= 0f && existing != null && existing.plannedQty > 0f)
            existing.plannedQty else progress.plannedQty
        // Preserve existing unit if the incoming value is empty but DB has a valid value
        val safeUnit = if (progress.unit.isBlank() && existing != null && existing.unit.isNotBlank())
            existing.unit else progress.unit
        dao.upsert(progress.copy(id = existing?.id ?: progress.id, plannedQty = safePlannedQty, unit = safeUnit).toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert material progress", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<WorkVolumeProgress>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProjectSummary(projectId).map { it.toDomain(projectId) }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list material progress", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<WorkVolumeProgress>> = flow {
        emitAll(dao(projectId).observeByProjectSummary(projectId).map { rows -> rows.map { it.toDomain(projectId) } }.distinctUntilChanged())
    }

    private fun WorkVolumeProgress.toEntity() = MaterialProgressEntity(
        id = id,
        projectId = projectId,
        nodeCode = nodeCode,
        materialName = workName,
        plannedQty = plannedQty,
        actualQty = actualQty,
        updatedAtEpochMs = updatedAtEpochMs,
        unit = unit,
        nodeId = nodeId,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private fun MaterialProgressProjection.toDomain(projectId: String) = WorkVolumeProgress(
        id = id,
        projectId = projectId,
        nodeCode = nodeCode,
        nodeId = nodeId,
        workName = workName,
        plannedQty = plannedQty,
        actualQty = actualQty,
        updatedAtEpochMs = updatedAtEpochMs,
        unit = unit
    )

    private suspend fun dao(projectId: String): WorkVolumeProgressDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.workVolumeProgressDao() ?: dao
}

class WorkPlanRepositoryImpl @Inject constructor(
    private val dao: WorkPlanDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : WorkPlanRepository {
    override suspend fun add(workPlan: WorkPlan): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val entity = WorkPlanEntity.fromDomain(workPlan)
        val projectDatabase = projectScopedDatabaseProvider.databaseFor(workPlan.projectId)
        writeToSharedAndScoped(
            sharedWrite = { dao.insert(entity) },
            scopedWrite = { projectDatabase?.workPlanDao()?.insert(entity) }
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add work plan", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<WorkPlan>> = withContext(Dispatchers.IO) { runCatching {
        val rows = dao(projectId).byProject(projectId)
        val resolvedRows = if (rows.isEmpty()) dao.byProject(projectId) else rows
        resolvedRows.map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list work plans", it)) }
    ) }

    override fun observeByProject(projectId: String): Flow<List<WorkPlan>> = flow {
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

    private suspend fun dao(projectId: String): WorkPlanDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.workPlanDao() ?: dao

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

