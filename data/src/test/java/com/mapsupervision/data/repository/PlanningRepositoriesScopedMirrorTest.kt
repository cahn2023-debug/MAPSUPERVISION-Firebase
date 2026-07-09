package com.mapsupervision.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlanningRepositoriesScopedMirrorTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private lateinit var storageManager: ProjectStorageManager
    private lateinit var provider: ProjectScopedDatabaseProvider
    private lateinit var activeProjectRepository: FakeActiveProjectRepository
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("planning-repository-test").toFile()
        storageManager = object : ProjectStorageManager(context) {
            override fun scopedProjectDbRootDirectory(projectSlug: String): File {
                return File(tempDir, "scoped-private/$projectSlug")
            }
        }
        sharedDatabase = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = ProjectScopedDatabaseProvider(context, sharedDatabase, storageManager)
        activeProjectRepository = FakeActiveProjectRepository()
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `legacy planning reads fall back to shared database`() = runBlocking {
        val project = projectEntity("legacy-planning")
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.taskDao().upsert(TaskEntity.fromDomain(task(project.id, "legacy-task")))
        sharedDatabase.workCategoryDao().upsert(workCategory(project.id, "legacy-category").toEntity())
        sharedDatabase.workPlanDao().insert(WorkPlanEntity.fromDomain(workPlan(project.id, "legacy-plan")))

        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!
        val taskRepository = taskRepository()
        val workCategoryRepository = workCategoryRepository()
        val workPlanRepository = workPlanRepository()

        assertEquals(1, (taskRepository.byProject(project.id) as AppResult.Success).data.size)
        assertEquals(1, taskRepository.observeByProject(project.id).first().size)
        assertEquals(1, (workCategoryRepository.byProject(project.id) as AppResult.Success).data.size)
        assertEquals(1, workCategoryRepository.observeByProject(project.id).first().size)
        assertEquals(1, (workPlanRepository.byProject(project.id) as AppResult.Success).data.size)
        assertEquals(1, workPlanRepository.observeByProject(project.id).first().size)
    }

    @Test
    fun `planning writes are mirrored to scoped and shared databases`() = runBlocking {
        val project = projectEntity("planning-mirror")
        sharedDatabase.projectDao().upsert(project)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!
        val taskRepository = taskRepository()
        val workCategoryRepository = workCategoryRepository()
        val workPlanRepository = workPlanRepository()

        assertTrue(taskRepository.upsert(task(project.id, "task-mirror")) is AppResult.Success)
        assertTrue(workCategoryRepository.add(workCategory(project.id, "category-mirror")) is AppResult.Success)
        assertTrue(workPlanRepository.add(workPlan(project.id, "plan-mirror")) is AppResult.Success)

        assertEquals(1, scopedDatabase.taskDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.taskDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.workCategoryDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.workCategoryDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.workPlanDao().byProject(project.id).size)
        assertEquals(1, sharedDatabase.workPlanDao().byProject(project.id).size)
    }

    @Test
    fun `task delete marks scoped and shared rows deleted`() = runBlocking {
        val project = projectEntity("task-delete")
        sharedDatabase.projectDao().upsert(project)
        activeProjectRepository.setActive(project.id)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!
        val taskRepository = taskRepository()

        assertTrue(taskRepository.upsert(task(project.id, "task-delete-1")) is AppResult.Success)
        assertTrue(taskRepository.delete("task-delete-1") is AppResult.Success)

        assertEquals(true, scopedDatabase.taskDao().byProjectIncludingDeleted(project.id).single().isDeleted)
        assertEquals(true, sharedDatabase.taskDao().byProjectIncludingDeleted(project.id).single().isDeleted)
    }

    @Test
    fun `planning observe emits saved rows after add`() = runBlocking {
        val project = projectEntity("planning-observe")
        sharedDatabase.projectDao().upsert(project)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!
        val taskRepository = taskRepository()
        val workPlanRepository = workPlanRepository()

        val observedTasks = async {
            withTimeout(5_000L) {
                taskRepository.observeByProject(project.id).first { it.isNotEmpty() }
            }
        }
        val observedPlans = async {
            withTimeout(5_000L) {
                workPlanRepository.observeByProject(project.id).first { it.isNotEmpty() }
            }
        }
        yield()

        assertTrue(taskRepository.upsert(task(project.id, "task-observe")) is AppResult.Success)
        assertTrue(workPlanRepository.add(workPlan(project.id, "plan-observe")) is AppResult.Success)

        assertEquals(1, observedTasks.await().size)
        assertEquals(1, observedPlans.await().size)
    }

    private fun taskRepository() = TaskRepositoryImpl(
        sharedDatabase.taskDao(),
        provider,
        activeProjectRepository
    )

    private fun workPlanRepository() = WorkPlanRepositoryImpl(
        sharedDatabase.workPlanDao(),
        provider
    )

    private fun workCategoryRepository() = WorkCategoryRepositoryImpl(
        sharedDatabase.workCategoryDao(),
        provider
    )

    private fun task(projectId: String, id: String) = Task(
        id = id,
        projectId = projectId,
        objectCode = "",
        title = "Cable check",
        description = "Check route cable",
        status = TaskStatus.TODO,
        createdAtEpochMs = 100L
    )

    private fun workPlan(projectId: String, id: String) = WorkPlan(
        id = id,
        projectId = projectId,
        title = "Cable pulling",
        description = "Plan route work",
        plannedDateEpochDay = 20240702L,
        nodeCode = "N-01",
        routeCode = null,
        taskId = null,
        sourceRawInput = "",
        createdAtEpochMs = 200L,
        quantity = 10.0,
        unit = "m",
        batchGroupId = "batch-$id"
    )

    private fun workCategory(projectId: String, id: String) = WorkCategory(
        id = id,
        projectId = projectId,
        name = "Cable pulling",
        unit = "m",
        createdAtEpochMs = 150L
    )

    private fun WorkCategory.toEntity() = WorkCategoryEntity(
        id = id,
        projectId = projectId,
        name = name,
        unit = unit,
        createdAtEpochMs = createdAtEpochMs
    )

    private fun projectEntity(projectId: String) = ProjectEntity(
        id = projectId,
        name = projectId,
        slug = projectId,
        isArchived = false,
        createdAtEpochMs = 1L,
        storageMode = ProjectStorageMode.PROJECT_DB,
        projectDbPath = storageManager.scopedProjectDbFile(projectId).absolutePath
    )

    private class FakeActiveProjectRepository : ActiveProjectRepository {
        private val activeProject = MutableStateFlow<String?>(null)
        override val activeProjectId: StateFlow<String?> = activeProject

        override suspend fun setActive(projectId: String): AppResult<Unit> {
            activeProject.value = projectId
            return AppResult.Success(Unit)
        }

        override suspend fun getActive(): AppResult<String?> = AppResult.Success(activeProject.value)
    }

    private class TestDatabaseContext(base: Context) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File {
            return if (name.contains(File.separatorChar) || name.contains('/')) {
                File(name)
            } else {
                super.getDatabasePath(name)
            }
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            val path = getDatabasePath(name)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(path, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            val path = getDatabasePath(name)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openDatabase(
                path.absolutePath,
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler
            )
        }
    }
}
