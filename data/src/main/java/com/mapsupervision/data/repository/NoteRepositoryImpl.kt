package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.NoteDao
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: com.mapsupervision.domain.repository.ActiveProjectRepository
) : NoteRepository {
    override suspend fun add(note: Note): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val database = projectScopedDatabaseProvider.databaseFor(note.projectId)
        val resolvedNodeId = note.objectNodeId ?: database?.gisNodeDao()?.byProject(note.projectId)?.find { it.code == note.objectCode }?.id
        val resolvedRouteId = note.objectRouteId ?: if (resolvedNodeId == null) database?.gisRouteDao()?.byProject(note.projectId)?.find { it.code == note.objectCode }?.id else null

        val normalized = note.copy(
            objectNodeId = resolvedNodeId,
            objectRouteId = resolvedRouteId,
            updatedAtEpochMs = if (note.updatedAtEpochMs == 0L) System.currentTimeMillis() else note.updatedAtEpochMs
        )
        dao(note.projectId).insert(NoteEntity.fromDomain(normalized))
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add note", it)) }
    ) }

    override suspend fun delete(noteId: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        val scopedDao = if (activeProjectId.isNullOrBlank()) dao else dao(activeProjectId)
        val now = System.currentTimeMillis()
        scopedDao.markDeletedById(noteId, now, now)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to delete note", it)) }
    ) }

    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Note>> = withContext(Dispatchers.IO) { runCatching {
        hydrateNotes(projectId, dao(projectId).byObject(projectId, objectCode))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list notes by object", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<Note>> = withContext(Dispatchers.IO) { runCatching {
        hydrateNotes(projectId, dao(projectId).byProject(projectId))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list notes by project", it)) }
    ) }

    private suspend fun hydrateNotes(projectId: String, entities: List<NoteEntity>): List<Note> {
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

    private suspend fun dao(projectId: String): NoteDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.noteDao() ?: dao
}
