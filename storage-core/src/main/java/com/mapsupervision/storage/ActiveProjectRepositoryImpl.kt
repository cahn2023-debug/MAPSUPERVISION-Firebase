package com.mapsupervision.storage

import android.content.Context
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.repository.ActiveProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ActiveProjectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ActiveProjectRepository {
    private val pref by lazy { context.getSharedPreferences("active_project", Context.MODE_PRIVATE) }
    private val _activeProjectId = MutableStateFlow(pref.getString("project_id", null))

    override val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    override suspend fun setActive(projectId: String): AppResult<Unit> = runCatching {
        val committed = pref.edit().putString("project_id", projectId).commit()
        check(committed) { "Failed to persist active project" }
        _activeProjectId.value = projectId
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(it) }
    )

    override suspend fun getActive(): AppResult<String?> = runCatching {
        _activeProjectId.value ?: pref.getString("project_id", null)?.also { _activeProjectId.value = it }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it) }
    )
}
