package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.ReportDraftDao
import com.mapsupervision.data.db.entity.ReportDraftEntity
import com.mapsupervision.domain.model.ReportDraft
import com.mapsupervision.domain.repository.ReportDraftRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReportDraftRepositoryImpl @Inject constructor(
    private val dao: ReportDraftDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: com.mapsupervision.domain.repository.ActiveProjectRepository
) : ReportDraftRepository {

    override suspend fun add(draft: ReportDraft): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dao(draft.projectId).upsert(draft.toEntity())
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to add report draft", it)) }
        )
    }

    override suspend fun byProject(projectId: String): AppResult<List<ReportDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            dao(projectId).byProject(projectId).map { it.toDomain() }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(DatabaseException("Failed to load report drafts", it)) }
        )
    }

    override fun observeByProject(projectId: String): Flow<List<ReportDraft>> = flow {
        emitAll(
            dao(projectId).observeByProject(projectId)
                .map { rows -> rows.map { it.toDomain() } }
                .distinctUntilChanged()
        )
    }

    override suspend fun delete(id: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
            val targetDao = if (activeProjectId.isNullOrBlank()) dao else dao(activeProjectId)
            targetDao.delete(id)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to delete report draft", it)) }
        )
    }

    private suspend fun dao(projectId: String): ReportDraftDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.reportDraftDao() ?: dao

    private fun ReportDraft.toEntity() = ReportDraftEntity(
        id = id,
        projectId = projectId,
        title = title,
        executiveSummary = executiveSummary,
        riskSection = riskSection,
        recommendedActionsCsv = recommendedActions.joinToString("|"),
        createdAtEpochMs = createdAtEpochMs
    )

    private fun ReportDraftEntity.toDomain() = ReportDraft(
        id = id,
        projectId = projectId,
        title = title,
        executiveSummary = executiveSummary,
        riskSection = riskSection,
        recommendedActions = if (recommendedActionsCsv.isBlank()) emptyList() else recommendedActionsCsv.split("|"),
        createdAtEpochMs = createdAtEpochMs
    )
}
