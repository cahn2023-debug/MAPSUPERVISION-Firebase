package com.mapsupervision.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mapsupervision.core.logging.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FirebaseMediaUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val runner: FirebaseMediaUploadRunner
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString(FirebaseMediaUploadWorkRequest.KEY_REASON).orEmpty().ifBlank { "unknown" }
        val projectId = inputData.getString(FirebaseMediaUploadWorkRequest.KEY_PROJECT_ID).orEmpty().ifBlank { null }
        AppLogger.d("firebase.media_auto_upload.started reason=$reason")

        return when (val outcome = runner.run(projectId)) {
            is FirebaseMediaUploadRunOutcome.Success -> {
                AppLogger.d(
                    "firebase.media_auto_upload.success reason=$reason projects=${outcome.projectCount} uploaded=${outcome.uploadedMedia} failed=${outcome.failedMedia}"
                )
                Result.success()
            }

            is FirebaseMediaUploadRunOutcome.Retry -> {
                AppLogger.d(
                    "firebase.media_auto_upload.retry reason=$reason projects=${outcome.projectCount} uploaded=${outcome.uploadedMedia} failed=${outcome.failedMedia} detail=${outcome.reason}"
                )
                Result.retry()
            }

            is FirebaseMediaUploadRunOutcome.Failure -> {
                AppLogger.e(
                    IllegalStateException(outcome.reason),
                    "firebase.media_auto_upload.failure reason=$reason projects=${outcome.projectCount} uploaded=${outcome.uploadedMedia} failed=${outcome.failedMedia}"
                )
                Result.failure()
            }
        }
    }

    companion object {
        fun enqueue(context: Context, reason: String) {
            FirebaseMediaUploadWorkRequest.enqueue(context, reason)
        }

        fun enqueue(context: Context, reason: String, projectId: String?) {
            FirebaseMediaUploadWorkRequest.enqueue(context, reason, projectId)
        }
    }
}
