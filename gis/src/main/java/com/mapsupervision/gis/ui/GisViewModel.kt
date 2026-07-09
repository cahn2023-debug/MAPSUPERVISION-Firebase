package com.mapsupervision.gis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class GisViewModel @Inject constructor(
    private val activeProjectRepository: ActiveProjectRepository,
    private val gisRepository: GisRepository,
    private val projectSyncRepository: ProjectSyncRepository,
) : ViewModel() {
    private val _nodes = MutableStateFlow<List<GisNode>>(emptyList())
    val nodes: StateFlow<List<GisNode>> = _nodes.asStateFlow()

    private val _routes = MutableStateFlow<List<GisRoute>>(emptyList())
    val routes: StateFlow<List<GisRoute>> = _routes.asStateFlow()
    val styleJson: String = "asset://style_street.json"

    init {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.debounce(250).collectLatest {
                search("")
            }
        }
        viewModelScope.launch {
            projectSyncRepository.events.debounce(250).collectLatest { event ->
                val activeProjectId = activeProjectRepository.activeProjectId.value
                if (event.projectId == null || event.projectId == activeProjectId) {
                    search("")
                }
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            _nodes.value = (gisRepository.searchNodes(projectId, query) as? AppResult.Success)?.data.orEmpty()
            _routes.value = (gisRepository.searchRoutes(projectId, query) as? AppResult.Success)?.data.orEmpty()
        }
    }

    fun seedDemo() {
    }
}
