package com.mapsupervision.domain.usecase

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveWorkspaceSnapshotUseCaseTest {

    @Test
    fun combinesReactiveSourcesIntoSingleSnapshot() = runBlocking {
        val importedFiles = MutableStateFlow(listOf(ImportedFile("f1", "p1", "a.xlsx", "xlsx", "/tmp/a", "summary", 1L)))
        val nodes = MutableStateFlow(listOf(GisNode("n1", "p1", "N-1", "CTR-A", 10.0, 106.0)))
        val routes = MutableStateFlow(listOf(GisRoute("r1", "p1", "R-1", "CTR-A", "N-1", "N-2")))
        val progress = MutableStateFlow(listOf(NodeProgress("pg1", "p1", "N-1", 100f, 60f, 40f, true, 2L)))
        val workVolumeRows = MutableStateFlow(
            listOf(
                WorkVolumeProgress(
                    id = "m1",
                    projectId = "p1",
                    nodeCode = "N-1",
                    workName = "Cap",
                    plannedQty = 100f,
                    actualQty = 50f,
                    updatedAtEpochMs = 3L,
                    unit = ""
                )
            )
        )
        val dailyLogs = MutableStateFlow(listOf(DailyLog("d1", "p1", "Đào rãnh", 5, "done", 4L)))
        val workCategories = MutableStateFlow(listOf(WorkCategory("w1", "p1", "Cap", "m", 5L)))
        val projectTasks = MutableStateFlow(
            listOf(
                Task(
                    id = "task-1",
                    projectId = "p1",
                    objectCode = "N-1",
                    title = "Kiem tra tuyen cap",
                    description = "",
                    status = TaskStatus.TODO,
                    createdAtEpochMs = 6L
                )
            )
        )
        val handovers = MutableStateFlow(
            listOf(
                MaterialHandover(
                    id = "ho1",
                    projectId = "p1",
                    nodeCode = "N-1",
                    workName = "Thi công ga",
                    materialName = "Sắt",
                    contractor = "Nha thau A",
                    quantity = 30f,
                    unit = "kg",
                    handoverDateEpochDay = 4L,
                    note = "",
                    createdAtEpochMs = 5L
                )
            )
        )
        val declarations = MutableStateFlow(
            listOf(
                MaterialDeclaration(
                    id = "decl1",
                    projectId = "p1",
                    workName = "Thi công ga",
                    materialName = "Sắt",
                    ratio = 3.5f,
                    unit = "kg",
                    createdAtEpochMs = 5L
                )
            )
        )
        val workPlans = MutableStateFlow(
            listOf(
                WorkPlan(
                    id = "wp1",
                    projectId = "p1",
                    title = "Dao ranh",
                    description = "Theo tuyen",
                    plannedDateEpochDay = 2000L,
                    nodeCode = "N-1",
                    routeCode = null,
                    taskId = null,
                    sourceRawInput = "",
                    createdAtEpochMs = 7L,
                    quantity = 12.0,
                    unit = "m3",
                    batchGroupId = "batch-1"
                )
            )
        )
        val photos = MutableStateFlow(
            listOf(
                SitePhoto(
                    id = "s1",
                    projectId = "p1",
                    objectCode = "N-1",
                    tagCodesCsv = "N-1",
                    matchedNodeCode = "N-1",
                    matchedRouteCode = null,
                    filePath = "/tmp/p.jpg",
                    thumbnailPath = "/tmp/t.jpg",
                    latitude = null,
                    longitude = null,
                    locationAccuracyM = null,
                    isGpsMocked = false,
                    engineer = "Field",
                    capturedAtEpochMs = 6L,
                    locationStatus = PhotoLocationStatus.MISSING,
                    matchedAtEpochMs = 6L,
                    matchingTimeOffsetMs = 0L
                )
            )
        )

        val useCase = ObserveWorkspaceSnapshotUseCase(
            importedFileRepository = FakeImportedFileRepository(importedFiles),
            gisRepository = FakeGisRepository(nodes, routes),
            progressRepository = FakeProgressRepository(progress),
            workVolumeProgressRepository = FakeWorkVolumeProgressRepository(workVolumeRows),
            dailyLogRepository = FakeDailyLogRepository(dailyLogs),
            workCategoryRepository = FakeWorkCategoryRepository(workCategories),
            photoRepository = FakePhotoRepository(photos),
            materialHandoverRepository = FakeMaterialHandoverRepository(handovers),
            materialDeclarationRepository = FakeMaterialDeclarationRepository(declarations),
            workPlanRepository = FakeWorkPlanRepository(workPlans),
            taskRepository = FakeTaskRepository(projectTasks)
        )
 
        val snapshot = useCase("p1").first()
 
        assertEquals("p1", snapshot.projectId)
        assertEquals(1, snapshot.importedFiles.size)
        assertEquals(1, snapshot.designNodes.size)
        assertEquals(1, snapshot.designRoutes.size)
        assertEquals(1, snapshot.constructionProgress.size)
        assertEquals(1, snapshot.workVolumeRows.size)
        assertEquals(1, snapshot.dailyLogs.size)
        assertEquals(1, snapshot.workCategories.size)
        assertEquals(1, snapshot.sitePhotos.size)
        assertEquals(1, snapshot.materialHandovers.size)
        assertEquals(1, snapshot.materialDeclarations.size)
        assertEquals(1, snapshot.workPlans.size)
        assertEquals("wp1", snapshot.workPlans.first().id)
        assertEquals(1, snapshot.projectTasks.size)
        assertEquals("task-1", snapshot.projectTasks.first().id)
    }
 
    @Test
    fun suppressesDuplicateSnapshots() = runBlocking {
        val duplicateImportedFile = ImportedFile("f1", "p1", "a.xlsx", "xlsx", "/tmp/a", "summary", 1L)
        val useCase = ObserveWorkspaceSnapshotUseCase(
            importedFileRepository = object : ImportedFileRepository {
                override suspend fun upsert(file: ImportedFile): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<ImportedFile>> = AppResult.Success(listOf(duplicateImportedFile))
                override suspend fun deleteById(id: String): AppResult<Unit> = AppResult.Success(Unit)
                override fun observeByProject(projectId: String): Flow<List<ImportedFile>> = flow {
                    emit(listOf(duplicateImportedFile))
                    emit(listOf(duplicateImportedFile))
                }
            },
            gisRepository = object : GisRepository {
                private val nodes = listOf(GisNode("n1", "p1", "N-1", "CTR-A", 10.0, 106.0))
                private val routes = listOf(GisRoute("r1", "p1", "R-1", "CTR-A", "N-1", "N-2"))
                override suspend fun upsertNode(node: GisNode): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<GisNode>, routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = AppResult.Success(nodes)
                override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = AppResult.Success(routes)
                override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = AppResult.Success(nodes.firstOrNull())
                override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = flowOf(nodes)
                override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = flowOf(routes)
            },
            progressRepository = object : ProgressRepository {
                override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<NodeProgress>> = flowOf(emptyList())
            },
            workVolumeProgressRepository = object : WorkVolumeProgressRepository {
                override suspend fun upsert(progress: WorkVolumeProgress): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<WorkVolumeProgress>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<WorkVolumeProgress>> = flowOf(emptyList())
            },
            dailyLogRepository = object : DailyLogRepository {
                override suspend fun add(log: DailyLog): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<DailyLog>> = flowOf(emptyList())
            },
            workCategoryRepository = object : WorkCategoryRepository {
                override suspend fun add(category: WorkCategory): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<WorkCategory>> = flowOf(emptyList())
            },
            photoRepository = object : PhotoRepository {
                override suspend fun add(photo: SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
                override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flowOf(emptyList())
            },
            materialHandoverRepository = object : MaterialHandoverRepository {
                override suspend fun add(handover: MaterialHandover): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun delete(handover: MaterialHandover): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<MaterialHandover>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<MaterialHandover>> = flowOf(emptyList())
            },
            materialDeclarationRepository = object : MaterialDeclarationRepository {
                override suspend fun add(declaration: MaterialDeclaration): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun delete(declaration: MaterialDeclaration): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun getByProject(projectId: String): AppResult<List<MaterialDeclaration>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<MaterialDeclaration>> = flowOf(emptyList())
            },
            workPlanRepository = FakeWorkPlanRepository(),
            taskRepository = object : TaskRepository {
                override suspend fun upsert(task: Task): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun delete(taskId: String): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>> = AppResult.Success(emptyList())
                override suspend fun byProject(projectId: String): AppResult<List<Task>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<Task>> = flowOf(emptyList())
            }
        )

        val emissions = useCase("p1").toList()

        assertEquals(1, emissions.size)
    }
}

private class FakeImportedFileRepository(
    private val flow: MutableStateFlow<List<ImportedFile>>
) : ImportedFileRepository {
    override suspend fun upsert(file: ImportedFile): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<ImportedFile>> = AppResult.Success(flow.value)
    override suspend fun deleteById(id: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun observeByProject(projectId: String): Flow<List<ImportedFile>> = flow
}

private class FakeGisRepository(
    private val nodes: MutableStateFlow<List<GisNode>>,
    private val routes: MutableStateFlow<List<GisRoute>>
) : GisRepository {
    override suspend fun upsertNode(node: GisNode): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<GisNode>, routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = AppResult.Success(nodes.value)
    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = AppResult.Success(routes.value)
    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = AppResult.Success(nodes.value.firstOrNull())
    override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = nodes
    override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = routes
}

private class FakeProgressRepository(
    private val flow: MutableStateFlow<List<NodeProgress>>
) : ProgressRepository {
    override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<NodeProgress>> = flow
}

private class FakeWorkVolumeProgressRepository(
    private val flow: MutableStateFlow<List<WorkVolumeProgress>>
) : WorkVolumeProgressRepository {
    override suspend fun upsert(progress: WorkVolumeProgress): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<WorkVolumeProgress>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<WorkVolumeProgress>> = flow
}

private class FakeDailyLogRepository(
    private val flow: MutableStateFlow<List<DailyLog>>
) : DailyLogRepository {
    override suspend fun add(log: DailyLog): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<DailyLog>> = flow
}

private class FakeWorkCategoryRepository(
    private val flow: MutableStateFlow<List<WorkCategory>>
) : WorkCategoryRepository {
    override suspend fun add(category: WorkCategory): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<WorkCategory>> = flow
}

private class FakePhotoRepository(
    private val flow: MutableStateFlow<List<SitePhoto>>
) : PhotoRepository {
    override suspend fun add(photo: SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = AppResult.Success(flow.value)
    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flow
}

private class FakeMaterialHandoverRepository(
    private val flow: MutableStateFlow<List<MaterialHandover>>
) : MaterialHandoverRepository {
    override suspend fun add(handover: MaterialHandover): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(handover: MaterialHandover): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<MaterialHandover>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<MaterialHandover>> = flow
}

private class FakeMaterialDeclarationRepository(
    private val flow: MutableStateFlow<List<MaterialDeclaration>>
) : MaterialDeclarationRepository {
    override suspend fun add(declaration: MaterialDeclaration): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(declaration: MaterialDeclaration): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun getByProject(projectId: String): AppResult<List<MaterialDeclaration>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<MaterialDeclaration>> = flow
}

private class FakeWorkPlanRepository(
    private val flow: Flow<List<com.mapsupervision.domain.model.WorkPlan>> = flowOf(emptyList())
) : com.mapsupervision.domain.repository.WorkPlanRepository {
    override suspend fun add(workPlan: com.mapsupervision.domain.model.WorkPlan): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<com.mapsupervision.domain.model.WorkPlan>> = AppResult.Success(emptyList())
    override fun observeByProject(projectId: String): Flow<List<com.mapsupervision.domain.model.WorkPlan>> = flow
}

private class FakeTaskRepository(
    private val flow: MutableStateFlow<List<Task>>
) : TaskRepository {
    override suspend fun upsert(task: Task): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(taskId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>> = AppResult.Success(flow.value)
    override suspend fun byProject(projectId: String): AppResult<List<Task>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<Task>> = flow
}

