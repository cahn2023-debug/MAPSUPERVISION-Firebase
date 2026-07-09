package com.mapsupervision.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface FirebaseMediaUploadScheduler {
    fun enqueue(reason: String)
}

@Singleton
class WorkManagerFirebaseMediaUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : FirebaseMediaUploadScheduler {
    override fun enqueue(reason: String) {
        FirebaseMediaUploadWorker.enqueue(context, reason)
    }
}

internal object FirebaseMediaUploadWorkRequest {
    const val UNIQUE_WORK_NAME = "firebase-media-auto-upload"
    const val KEY_REASON = "reason"
    const val TAG = "FirebaseMediaUpload"

    fun enqueue(context: Context, reason: String) {
        val request = OneTimeWorkRequestBuilder<FirebaseMediaUploadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(KEY_REASON, reason)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
