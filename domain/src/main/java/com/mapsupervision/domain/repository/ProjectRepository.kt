package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.Project

interface ProjectRepository {
    suspend fun create(name: String, customPath: String? = null): AppResult<Project>
    suspend fun list(includeArchived: Boolean = false): AppResult<List<Project>>
    suspend fun clone(projectId: String, newName: String): AppResult<Project>
    suspend fun archive(projectId: String): AppResult<Unit>
    suspend fun importProject(project: Project): AppResult<Unit>
    suspend fun clearProject(projectId: String): AppResult<Unit>
    suspend fun touch(projectId: String): AppResult<Unit>
    suspend fun updateStoragePath(projectId: String, newPath: String): AppResult<Unit>
}
