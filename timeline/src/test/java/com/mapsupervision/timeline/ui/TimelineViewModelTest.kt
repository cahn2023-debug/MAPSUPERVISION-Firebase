package com.mapsupervision.timeline.ui

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.OpsRecommendationPayload
import com.mapsupervision.ai.core.OpsRecommendationResult
import com.mapsupervision.ai.core.ReportDraftPayload
import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.ai.core.TimelineSummaryPayload
import com.mapsupervision.ai.core.TimelineSummaryResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectSyncEvent
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.usecase.ObserveTimelineUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_loads_progress_logs_and_summary() = runTest(dispatcher) {
        val progressRepository = FakeProgressRepository(
            mutableListOf(
                NodeProgress(
                    id = "p1",
                    projectId = "project-1",
                    nodeCode = "N1",
                    planned = 10f,
                    actual = 8f,
                    remain = 2f,
                    delayed = true
                )
            )
        )
        val dailyLogRepository = FakeDailyLogRepository(
            mutableListOf(
                DailyLog(
                    id = "d1",
                    projectId = "project-1",
                    workItem = "Install cable",
                    manpower = 4,
                    note = "done",
                    createdAtEpochMs = 100L
                )
            )
        )
        val viewModel = TimelineViewModel(
            activeProjectRepository = FakeActiveProjectRepository("project-1"),
            progressRepository = progressRepository,
            dailyLogRepository = dailyLogRepository,
            aiFacade = FakeAiFacade(),
            observeTimelineUseCase = ObserveTimelineUseCase(progressRepository, dailyLogRepository, FakePhotoRepository()),
            projectSyncRepository = FakeProjectSyncRepository()
        )

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, viewModel.progress.value.size)
        assertEquals(1, viewModel.logs.value.size)
        assertTrue(viewModel.aiSummary.value.contains("1 progress"))
        assertEquals(listOf("logs=1"), viewModel.aiHighlights.value)
    }

    @Test
    fun addProgress_persists_delayed_state() = runTest(dispatcher) {
        val progressRepository = FakeProgressRepository(mutableListOf())
        val viewModel = TimelineViewModel(
            activeProjectRepository = FakeActiveProjectRepository("project-1"),
            progressRepository = progressRepository,
            dailyLogRepository = FakeDailyLogRepository(mutableListOf()),
            aiFacade = FakeAiFacade(),
            observeTimelineUseCase = ObserveTimelineUseCase(progressRepository, FakeDailyLogRepository(mutableListOf()), FakePhotoRepository()),
            projectSyncRepository = FakeProjectSyncRepository()
        )

        viewModel.addProgress(nodeCode = "N9", planned = 5f, actual = 3f)
        advanceUntilIdle()

        val saved = progressRepository.saved.single()
        assertEquals("project-1", saved.projectId)
        assertEquals("N9", saved.nodeCode)
        assertEquals(2f, saved.remain)
        assertEquals(true, saved.delayed)
    }
}

private class FakeActiveProjectRepository(
    initialProjectId: String?
) : ActiveProjectRepository {
    private val state = MutableStateFlow(initialProjectId)

    override val activeProjectId: StateFlow<String?> = state

    override suspend fun setActive(projectId: String): AppResult<Unit> {
        state.value = projectId
        return AppResult.Success(Unit)
    }

    override suspend fun getActive(): AppResult<String?> = AppResult.Success(state.value)
}

private class FakeProgressRepository(
    val saved: MutableList<NodeProgress>
) : ProgressRepository {
    override suspend fun upsert(progress: NodeProgress): AppResult<Unit> {
        saved.removeAll { it.id == progress.id }
        saved += progress
        return AppResult.Success(Unit)
    }

    override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> {
        return AppResult.Success(saved.filter { it.projectId == projectId })
    }

    override fun observeByProject(projectId: String): Flow<List<NodeProgress>> {
        return flowOf(saved.filter { it.projectId == projectId })
    }
}

private class FakeDailyLogRepository(
    private val saved: MutableList<DailyLog>
) : DailyLogRepository {
    override suspend fun add(log: DailyLog): AppResult<Unit> {
        saved += log
        return AppResult.Success(Unit)
    }

    override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> {
        return AppResult.Success(saved.filter { it.projectId == projectId })
    }

    override fun observeByProject(projectId: String): Flow<List<DailyLog>> {
        return flowOf(saved.filter { it.projectId == projectId })
    }
}

private class FakeProjectSyncRepository : ProjectSyncRepository {
    override val events: SharedFlow<ProjectSyncEvent> = MutableSharedFlow()

    override suspend fun notifyProjectChanged(projectId: String?, reason: String) = Unit
}

private class FakePhotoRepository : com.mapsupervision.domain.repository.PhotoRepository {
    override suspend fun add(photo: com.mapsupervision.domain.model.SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<com.mapsupervision.domain.model.SitePhoto>> = AppResult.Success(emptyList())
    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<com.mapsupervision.domain.model.SitePhoto>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.mapsupervision.domain.model.SitePhoto>())
}

private class FakeAiFacade : AIFacade {
    override suspend fun <T : com.mapsupervision.ai.core.AiResult> execute(payload: com.mapsupervision.ai.core.AiPayload): com.mapsupervision.ai.core.AiDecision<T> {
        val result = TimelineSummaryResult(
            summary = "${(payload as TimelineSummaryPayload).progress.size} progress / ${payload.logs.size} logs",
            issueHighlights = listOf("logs=${payload.logs.size}"),
            recommendedActions = emptyList()
        )
        @Suppress("UNCHECKED_CAST")
        return com.mapsupervision.ai.core.AiDecision(
            capability = payload.capability,
            result = result as T,
            confidence = 100,
            source = com.mapsupervision.ai.core.AiDecisionSource.RULE_BASED,
            reason = "test"
        )
    }
}

