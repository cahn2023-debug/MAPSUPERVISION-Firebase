package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.ImportedFile
import kotlinx.coroutines.flow.Flow

interface ImportedFileRepository {
    suspend fun upsert(file: ImportedFile): AppResult<Unit>
    suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<ImportedFile>>
    suspend fun deleteById(id: String): AppResult<Unit>
    fun observeByProject(projectId: String): Flow<List<ImportedFile>>
}
