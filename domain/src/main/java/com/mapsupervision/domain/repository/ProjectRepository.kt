package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectDeletionState

interface ProjectRepository {
    suspend fun create(name: String, customPath: String? = null): AppResult<Project>
    suspend fun list(includeArchived: Boolean = false): AppResult<List<Project>>
    suspend fun clone(projectId: String, newName: String): AppResult<Project>
    suspend fun archive(projectId: String): AppResult<Unit>
    suspend fun importProject(project: Project): AppResult<Unit>
    suspend fun clearProject(projectId: String): AppResult<Unit>
    suspend fun requestDeletion(projectId: String, requestId: String): AppResult<ProjectDeletionState> =
        AppResult.Error(UnsupportedOperationException("Project deletion is not supported"))
    suspend fun pendingDeletionWork(projectId: String): AppResult<Int> =
        AppResult.Error(UnsupportedOperationException("Project deletion work inspection is not supported"))
    suspend fun markDeletionFailed(projectId: String, requestId: String, errorCode: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Project deletion failure state is not supported"))
    suspend fun markCloudDeletionCompleted(projectId: String, requestId: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Cloud project deletion completion is not supported"))
    suspend fun completeLocalDeletion(projectId: String, requestId: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Local project deletion is not supported"))
    suspend fun acknowledgeRemoteDeletion(projectId: String, deleteLocal: Boolean): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Remote project deletion acknowledgement is not supported"))
    suspend fun touch(projectId: String): AppResult<Unit>
    suspend fun updateStoragePath(projectId: String, newPath: String): AppResult<Unit>
    suspend fun updateMediaStorage(projectId: String, folderId: String, folderUrl: String): AppResult<Unit> =
        AppResult.Error(UnsupportedOperationException("Project media storage update is not supported"))
}
