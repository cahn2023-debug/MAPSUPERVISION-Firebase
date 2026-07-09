package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import kotlinx.coroutines.flow.StateFlow

interface ActiveProjectRepository {
    val activeProjectId: StateFlow<String?>

    suspend fun setActive(projectId: String): AppResult<Unit>
    suspend fun getActive(): AppResult<String?>
}
