package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun upsert(task: Task): AppResult<Unit>
    suspend fun delete(taskId: String): AppResult<Unit>
    suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>>
    suspend fun byProject(projectId: String): AppResult<List<Task>>
    fun observeByProject(projectId: String): Flow<List<Task>>
}
