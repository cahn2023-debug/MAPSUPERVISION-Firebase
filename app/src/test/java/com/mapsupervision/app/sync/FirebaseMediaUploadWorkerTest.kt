package com.mapsupervision.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.SyncBatchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseMediaUploadWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun runner_uploads_all_projects_with_pending_media() = runBlocking {
        val photoRepository = FakePendingPhotoRepository(listOf("project-1", "project-2"))
        val syncRepository = FakeUploadFirebaseSyncRepository(
            results = mapOf(
                "project-1" to AppResult.Success(SyncBatchResult(uploadedMedia = 2)),
                "project-2" to AppResult.Success(SyncBatchResult(uploadedMedia = 1))
            )
        )

        val outcome = FirebaseMediaUploadRunner(photoRepository, syncRepository).run()

        assertTrue(outcome is FirebaseMediaUploadRunOutcome.Success)
        assertEquals(listOf("project-1", "project-2"), syncRepository.pushCalls)
        assertEquals(3, (outcome as FirebaseMediaUploadRunOutcome.Success).uploadedMedia)
    }

    @Test
    fun runner_uploads_only_requested_project_when_projectIdProvided() = runBlocking {
        val photoRepository = FakePendingPhotoRepository(listOf("project-1", "project-2"))
        val syncRepository = FakeUploadFirebaseSyncRepository(
            results = mapOf(
                "project-1" to AppResult.Success(SyncBatchResult(uploadedMedia = 2)),
                "project-2" to AppResult.Success(SyncBatchResult(uploadedMedia = 1))
            )
        )

        val outcome = FirebaseMediaUploadRunner(photoRepository, syncRepository).run("project-2")

        assertTrue(outcome is FirebaseMediaUploadRunOutcome.Success)
        assertEquals(listOf("project-2"), syncRepository.pushCalls)
        assertEquals(1, (outcome as FirebaseMediaUploadRunOutcome.Success).uploadedMedia)
    }

    @Test
    fun runner_returns_success_when_no_pending_projects() = runBlocking {
        val outcome = FirebaseMediaUploadRunner(
            photoRepository = FakePendingPhotoRepository(emptyList()),
            firebaseSyncRepository = FakeUploadFirebaseSyncRepository(emptyMap())
        ).run()

        assertTrue(outcome is FirebaseMediaUploadRunOutcome.Success)
        assertEquals(0, (outcome as FirebaseMediaUploadRunOutcome.Success).projectCount)
    }

    @Test
    fun runner_returns_retry_for_temporary_upload_failure() = runBlocking {
        val outcome = FirebaseMediaUploadRunner(
            photoRepository = FakePendingPhotoRepository(listOf("project-1")),
            firebaseSyncRepository = FakeUploadFirebaseSyncRepository(
                mapOf("project-1" to AppResult.Error(IllegalStateException("timeout while uploading media")))
            )
        ).run()

        assertTrue(outcome is FirebaseMediaUploadRunOutcome.Retry)
    }

    @Test
    fun worker_returns_retry_when_runner_requests_retry() {
        val worker = buildWorker(
            object : FirebaseMediaUploadRunner(
                FakePendingPhotoRepository(emptyList()),
                FakeUploadFirebaseSyncRepository(emptyMap())
            ) {
                override suspend fun run(projectId: String?): FirebaseMediaUploadRunOutcome =
                    FirebaseMediaUploadRunOutcome.Retry(1, 0, 1, "temporary")
            }
        )

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.retry()::class, result::class)
    }

    @Test
    fun worker_returns_failure_when_runner_requests_failure() {
        val worker = buildWorker(
            object : FirebaseMediaUploadRunner(
                FakePendingPhotoRepository(emptyList()),
                FakeUploadFirebaseSyncRepository(emptyMap())
            ) {
                override suspend fun run(projectId: String?): FirebaseMediaUploadRunOutcome =
                    FirebaseMediaUploadRunOutcome.Failure(1, 0, 1, "missing config")
            }
        )

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.failure()::class, result::class)
    }

    @Test
    fun worker_passes_projectId_to_runner() {
        var seenProjectId: String? = null
        val worker = buildWorker(
            object : FirebaseMediaUploadRunner(
                FakePendingPhotoRepository(emptyList()),
                FakeUploadFirebaseSyncRepository(emptyMap())
            ) {
                override suspend fun run(projectId: String?): FirebaseMediaUploadRunOutcome {
                    seenProjectId = projectId
                    return FirebaseMediaUploadRunOutcome.Success(1, 0, 0)
                }
            },
            Data.Builder()
                .putString(FirebaseMediaUploadWorkRequest.KEY_REASON, "photo_saved")
                .putString(FirebaseMediaUploadWorkRequest.KEY_PROJECT_ID, "project-2")
                .build()
        )

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success()::class, result::class)
        assertEquals("project-2", seenProjectId)
    }

    private fun buildWorker(
        runner: FirebaseMediaUploadRunner,
        inputData: Data = Data.EMPTY
    ): FirebaseMediaUploadWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return FirebaseMediaUploadWorker(appContext, workerParameters, runner)
            }
        }

        return TestListenableWorkerBuilder<FirebaseMediaUploadWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(inputData)
            .build()
    }
}

private class FakePendingPhotoRepository(
    private val pendingProjects: List<String>
) : PhotoRepository {
    override suspend fun add(photo: SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
    override suspend fun listProjectsWithPendingUploads(): AppResult<List<String>> = AppResult.Success(pendingProjects)
    override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flowOf(emptyList())
}

private class FakeUploadFirebaseSyncRepository(
    private val results: Map<String, AppResult<SyncBatchResult>>
) : FirebaseSyncRepository {
    val pushCalls = mutableListOf<String>()

    override suspend fun pushPending(projectId: String): AppResult<SyncBatchResult> {
        pushCalls += projectId
        return results[projectId] ?: AppResult.Success(SyncBatchResult())
    }
    override suspend fun pullChanges(projectId: String, sinceEpochMs: Long?): AppResult<SyncBatchResult> = AppResult.Success(SyncBatchResult())
    override suspend fun uploadPendingMedia(projectId: String): AppResult<SyncBatchResult> = AppResult.Success(SyncBatchResult())
}
