package com.mapsupervision.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mapsupervision.app.MainActivity
import com.mapsupervision.ai.model.mediapipe.GemmaDownloadFailure
import com.mapsupervision.ai.model.mediapipe.GemmaDownloadState
import com.mapsupervision.ai.model.mediapipe.GemmaDownloadStateStore
import com.mapsupervision.ai.model.mediapipe.GemmaModelDownloader
import com.mapsupervision.ai.model.mediapipe.GemmaModelManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@HiltWorker
class GemmaModelDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val modelManager: GemmaModelManager,
    private val downloadStateStore: GemmaDownloadStateStore,
    private val downloader: GemmaModelDownloader
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val model = modelManager.supportedModels().firstOrNull { it.downloadFileName == modelId }
            ?: return Result.failure()

        val downloadUrl = model.url
        if (downloadUrl.isNullOrBlank()) {
            downloadStateStore.update(
                GemmaDownloadState.Failed(
                    workName = WORK_NAME_PREFIX + model.family.name.lowercase(),
                    modelId = model.downloadFileName,
                    fileName = model.downloadFileName,
                    errorCode = "CONFIG_ERROR",
                    message = "Thiếu URL tải model.",
                    httpCode = 0,
                    bytesDownloaded = 0L,
                    totalBytes = modelManager.expectedBytes(model),
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )
            return Result.failure()
        }

        val workName = WORK_NAME_PREFIX + model.family.name.lowercase()
        val targetFile = modelManager.modelFile(model)
        val expectedBytes = modelManager.expectedBytes(model)

        val initialRunningState = GemmaDownloadState.Running(
            workName = workName,
            modelId = model.downloadFileName,
            fileName = model.downloadFileName,
            bytesDownloaded = if (targetFile.exists()) targetFile.length() else 0L,
            totalBytes = expectedBytes,
            lastUpdatedAt = System.currentTimeMillis()
        )

        createNotificationChannel()
        // Set foreground info immediately to comply with Android 12+ strict foreground service rules
        setForeground(createForegroundInfo(initialRunningState))
        downloadStateStore.update(initialRunningState)

        var lastProgress = initialRunningState

        try {
            downloader.download(
                url = downloadUrl,
                targetFile = targetFile,
                expectedBytes = expectedBytes
            ) { progress ->
                val runningState = GemmaDownloadState.Running(
                    workName = workName,
                    modelId = model.downloadFileName,
                    fileName = model.downloadFileName,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                lastProgress = runningState
                downloadStateStore.update(runningState)
                updateProgressNotification(runningState)
            }

            val completed = GemmaDownloadState.Completed(
                workName = workName,
                modelId = model.downloadFileName,
                fileName = model.downloadFileName,
                totalBytes = expectedBytes,
                lastUpdatedAt = System.currentTimeMillis()
            )
            downloadStateStore.update(completed)
            showTerminalNotification(buildCompletedNotification(model.displayName))
            return Result.success()
        } catch (cancelled: CancellationException) {
            downloadStateStore.update(GemmaDownloadState.Idle)
            removeForegroundNotification()
            throw cancelled
        } catch (failure: GemmaDownloadFailure) {
            val failed = GemmaDownloadState.Failed(
                workName = workName,
                modelId = model.downloadFileName,
                fileName = model.downloadFileName,
                errorCode = failure.code,
                message = failure.userMessage,
                httpCode = failure.httpCode,
                bytesDownloaded = lastProgress.bytesDownloaded,
                totalBytes = expectedBytes,
                lastUpdatedAt = System.currentTimeMillis()
            )
            downloadStateStore.update(failed)
            showTerminalNotification(buildFailedNotification(model.displayName, failure.userMessage))
            return Result.failure()
        } catch (e: Exception) {
            val failed = GemmaDownloadState.Failed(
                workName = workName,
                modelId = model.downloadFileName,
                fileName = model.downloadFileName,
                errorCode = "UNEXPECTED_ERROR",
                message = e.message ?: "Tai model that bai.",
                httpCode = 0,
                bytesDownloaded = lastProgress.bytesDownloaded,
                totalBytes = expectedBytes,
                lastUpdatedAt = System.currentTimeMillis()
            )
            downloadStateStore.update(failed)
            showTerminalNotification(buildFailedNotification(model.displayName, failed.message))
            return Result.failure()
        }
    }

    private fun createForegroundInfo(state: GemmaDownloadState.Running): ForegroundInfo {
        val notification = buildRunningNotification(state)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgressNotification(state: GemmaDownloadState.Running) {
        val notification = buildRunningNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildRunningNotification(state: GemmaDownloadState.Running): Notification {
        val percent = if (state.totalBytes > 0L) {
            ((state.bytesDownloaded * 100L) / state.totalBytes).toInt().coerceIn(0, 100)
        } else 0
        val builder = baseNotificationBuilder()
            .setContentTitle("Đang tải ${state.fileName}")
            .setContentText("$percent%")
            .setOngoing(true)
            .setProgress(100, percent, state.totalBytes <= 0L)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Hủy",
                cancelPendingIntent()
            )
        return builder.build()
    }

    private fun buildCompletedNotification(modelName: String): Notification {
        return baseNotificationBuilder()
            .setContentTitle("Đã tải xong model")
            .setContentText(modelName)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun buildFailedNotification(modelName: String, message: String): Notification {
        return baseNotificationBuilder()
            .setContentTitle("Tải model thất bại")
            .setContentText(modelName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun baseNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        // Direct WorkManager cancel intent
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        return cancelIntent
    }

    private fun showTerminalNotification(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun removeForegroundNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gemma downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_MODEL_ID = "key_model_id"
        private const val CHANNEL_ID = "gemma_model_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val WORK_NAME_PREFIX = "gemma-service-"

        fun start(context: Context, modelId: String) {
            val inputData = Data.Builder()
                .putString(KEY_MODEL_ID, modelId)
                .build()

            val request = OneTimeWorkRequestBuilder<GemmaModelDownloadWorker>()
                .setInputData(inputData)
                .addTag("GemmaDownload")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + modelId,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag("GemmaDownload")
        }
    }
}
