package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ImportAudit
import com.mapsupervision.domain.model.ImportConflict
import com.mapsupervision.domain.model.ImportSession
import com.mapsupervision.domain.model.ImportVersion

interface ImportLifecycleRepository {
    suspend fun upsertSession(session: ImportSession): AppResult<Unit>
    suspend fun upsertVersion(version: ImportVersion): AppResult<Unit>
    suspend fun upsertAudit(audit: ImportAudit): AppResult<Unit>
    suspend fun upsertConflict(conflict: ImportConflict): AppResult<Unit>
    suspend fun latestVersionNumber(projectId: String): AppResult<Int>
    suspend fun rollbackToVersion(projectId: String, versionNumber: Int): AppResult<Unit>
    suspend fun purgeDeletedArtifacts(projectId: String, deletedBeforeEpochMs: Long): AppResult<Unit>
}
