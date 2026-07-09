package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.MaterialDeclaration
import kotlinx.coroutines.flow.Flow

interface MaterialDeclarationRepository {
    suspend fun add(declaration: MaterialDeclaration): AppResult<Unit>
    suspend fun delete(declaration: MaterialDeclaration): AppResult<Unit>
    suspend fun getByProject(projectId: String): AppResult<List<MaterialDeclaration>>
    fun observeByProject(projectId: String): Flow<List<MaterialDeclaration>>
}
