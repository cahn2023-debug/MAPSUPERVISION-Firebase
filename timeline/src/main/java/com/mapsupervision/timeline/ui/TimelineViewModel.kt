package com.mapsupervision.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.TimelineSummaryPayload
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.TimelineSnapshot
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.usecase.ObserveTimelineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val activeProjectRepository: ActiveProjectRepository,
    private val progressRepository: ProgressRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val aiFacade: AIFacade,
    private val observeTimelineUseCase: ObserveTimelineUseCase,
    private val projectSyncRepository: ProjectSyncRepository
) : ViewModel() {
    private val _progress = MutableStateFlow<List<NodeProgress>>(emptyList())
    val progress: StateFlow<List<NodeProgress>> = _progress.asStateFlow()

    private val _logs = MutableStateFlow<List<DailyLog>>(emptyList())
    val logs: StateFlow<List<DailyLog>> = _logs.asStateFlow()
    private val _aiSummary = MutableStateFlow("")
    val aiSummary: StateFlow<String> = _aiSummary.asStateFlow()
    private val _aiHighlights = MutableStateFlow<List<String>>(emptyList())
    val aiHighlights: StateFlow<List<String>> = _aiHighlights.asStateFlow()

    init {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refresh()
            }
        }
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest { event ->
                val activeProjectId = activeProjectRepository.activeProjectId.value
                if (event.projectId == null || event.projectId == activeProjectId) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val snapshot = observeTimelineUseCase(projectId).first()
            _progress.value = snapshot.progress
            _logs.value = snapshot.logs
            val aiProgress = snapshot.progress.map {
                com.mapsupervision.ai.core.AiNodeProgress(
                    nodeCode = it.nodeCode,
                    planned = it.planned,
                    actual = it.actual
                )
            }
            val aiLogs = snapshot.logs.map {
                com.mapsupervision.ai.core.AiDailyLog(
                    workItem = it.workItem,
                    manpower = it.manpower,
                    note = it.note,
                    dateEpochDay = it.dateEpochDay
                )
            }
            val ai = aiFacade.execute<com.mapsupervision.ai.core.TimelineSummaryResult>(
                TimelineSummaryPayload(progress = aiProgress, logs = aiLogs, photoCount = snapshot.photoCount)
            )
            _aiSummary.value = ai.result.summary
            _aiHighlights.value = ai.result.issueHighlights
        }
    }

    fun addProgress(nodeCode: String, planned: Float, actual: Float) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val remain = (planned - actual).coerceAtLeast(0f)
            progressRepository.upsert(
                NodeProgress(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    nodeCode = nodeCode,
                    planned = planned,
                    actual = actual,
                    remain = remain,
                    delayed = actual < planned
                )
            )
            refresh()
        }
    }

    fun addDailyLog(workItem: String, manpower: Int, note: String) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            dailyLogRepository.add(
                DailyLog(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    workItem = workItem,
                    manpower = manpower,
                    note = note,
                    createdAtEpochMs = System.currentTimeMillis(),
                    routeCode = null,
                    batchGroupId = "",
                    appliedNodeCodesCsv = "",
                    linkedPhotoIdsCsv = "",
                    photoMatchOffsetMinutes = 0
                )
            )
            refresh()
        }
    }
}

