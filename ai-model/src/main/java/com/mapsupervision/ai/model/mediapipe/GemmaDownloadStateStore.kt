package com.mapsupervision.ai.model.mediapipe

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface GemmaDownloadState {
    data object Idle : GemmaDownloadState

    data class Running(
        val workName: String,
        val modelId: String,
        val fileName: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val lastUpdatedAt: Long,
        val warningCode: String = "",
        val warningMessage: String = "",
        val httpCode: Int = 0
    ) : GemmaDownloadState

    data class Completed(
        val workName: String,
        val modelId: String,
        val fileName: String,
        val totalBytes: Long,
        val lastUpdatedAt: Long
    ) : GemmaDownloadState

    data class Failed(
        val workName: String,
        val modelId: String,
        val fileName: String,
        val errorCode: String,
        val message: String,
        val httpCode: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val lastUpdatedAt: Long
    ) : GemmaDownloadState
}

@Singleton
class GemmaDownloadStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("gemma_download_state", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadPersistedState())
    val state: StateFlow<GemmaDownloadState> = _state.asStateFlow()

    fun update(state: GemmaDownloadState) {
        persist(state)
        _state.value = state
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = GemmaDownloadState.Idle
    }

    fun currentState(): GemmaDownloadState = _state.value

    private fun loadPersistedState(): GemmaDownloadState {
        val modelId = prefs.getString(KEY_MODEL_ID, null) ?: return GemmaDownloadState.Idle
        val workName = prefs.getString(KEY_WORK_NAME, "") ?: ""
        val fileName = prefs.getString(KEY_FILE_NAME, "") ?: ""
        val totalBytes = prefs.getLong(KEY_TOTAL_BYTES, 0L)
        val bytesDownloaded = prefs.getLong(KEY_BYTES_DOWNLOADED, 0L)
        val lastUpdatedAt = prefs.getLong(KEY_LAST_UPDATED_AT, 0L)
        val httpCode = prefs.getInt(KEY_HTTP_CODE, 0)
        return when (prefs.getString(KEY_STATE, STATE_IDLE)) {
            STATE_RUNNING -> GemmaDownloadState.Running(
                workName = workName,
                modelId = modelId,
                fileName = fileName,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                lastUpdatedAt = lastUpdatedAt,
                warningCode = prefs.getString(KEY_WARNING_CODE, "").orEmpty(),
                warningMessage = prefs.getString(KEY_WARNING_MESSAGE, "").orEmpty(),
                httpCode = httpCode
            )
            STATE_COMPLETED -> GemmaDownloadState.Completed(
                workName = workName,
                modelId = modelId,
                fileName = fileName,
                totalBytes = totalBytes,
                lastUpdatedAt = lastUpdatedAt
            )
            STATE_FAILED -> GemmaDownloadState.Failed(
                workName = workName,
                modelId = modelId,
                fileName = fileName,
                errorCode = prefs.getString(KEY_ERROR_CODE, "").orEmpty(),
                message = prefs.getString(KEY_ERROR_MESSAGE, "").orEmpty(),
                httpCode = httpCode,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                lastUpdatedAt = lastUpdatedAt
            )
            else -> GemmaDownloadState.Idle
        }
    }

    private fun persist(state: GemmaDownloadState) {
        when (state) {
            GemmaDownloadState.Idle -> {
                prefs.edit().clear().apply()
            }
            is GemmaDownloadState.Running -> {
                prefs.edit()
                    .putString(KEY_STATE, STATE_RUNNING)
                    .putString(KEY_WORK_NAME, state.workName)
                    .putString(KEY_MODEL_ID, state.modelId)
                    .putString(KEY_FILE_NAME, state.fileName)
                    .putLong(KEY_BYTES_DOWNLOADED, state.bytesDownloaded)
                    .putLong(KEY_TOTAL_BYTES, state.totalBytes)
                    .putLong(KEY_LAST_UPDATED_AT, state.lastUpdatedAt)
                    .putString(KEY_WARNING_CODE, state.warningCode)
                    .putString(KEY_WARNING_MESSAGE, state.warningMessage)
                    .putInt(KEY_HTTP_CODE, state.httpCode)
                    .remove(KEY_ERROR_CODE)
                    .remove(KEY_ERROR_MESSAGE)
                    .apply()
            }
            is GemmaDownloadState.Completed -> {
                prefs.edit()
                    .putString(KEY_STATE, STATE_COMPLETED)
                    .putString(KEY_WORK_NAME, state.workName)
                    .putString(KEY_MODEL_ID, state.modelId)
                    .putString(KEY_FILE_NAME, state.fileName)
                    .putLong(KEY_BYTES_DOWNLOADED, state.totalBytes)
                    .putLong(KEY_TOTAL_BYTES, state.totalBytes)
                    .putLong(KEY_LAST_UPDATED_AT, state.lastUpdatedAt)
                    .remove(KEY_WARNING_CODE)
                    .remove(KEY_WARNING_MESSAGE)
                    .remove(KEY_ERROR_CODE)
                    .remove(KEY_ERROR_MESSAGE)
                    .putInt(KEY_HTTP_CODE, 0)
                    .apply()
            }
            is GemmaDownloadState.Failed -> {
                prefs.edit()
                    .putString(KEY_STATE, STATE_FAILED)
                    .putString(KEY_WORK_NAME, state.workName)
                    .putString(KEY_MODEL_ID, state.modelId)
                    .putString(KEY_FILE_NAME, state.fileName)
                    .putLong(KEY_BYTES_DOWNLOADED, state.bytesDownloaded)
                    .putLong(KEY_TOTAL_BYTES, state.totalBytes)
                    .putLong(KEY_LAST_UPDATED_AT, state.lastUpdatedAt)
                    .putString(KEY_ERROR_CODE, state.errorCode)
                    .putString(KEY_ERROR_MESSAGE, state.message)
                    .putInt(KEY_HTTP_CODE, state.httpCode)
                    .remove(KEY_WARNING_CODE)
                    .remove(KEY_WARNING_MESSAGE)
                    .apply()
            }
        }
    }

    private companion object {
        private const val KEY_STATE = "state"
        private const val KEY_WORK_NAME = "workName"
        private const val KEY_MODEL_ID = "modelId"
        private const val KEY_FILE_NAME = "fileName"
        private const val KEY_BYTES_DOWNLOADED = "bytesDownloaded"
        private const val KEY_TOTAL_BYTES = "totalBytes"
        private const val KEY_LAST_UPDATED_AT = "lastUpdatedAt"
        private const val KEY_WARNING_CODE = "warningCode"
        private const val KEY_WARNING_MESSAGE = "warningMessage"
        private const val KEY_ERROR_CODE = "errorCode"
        private const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val KEY_HTTP_CODE = "httpCode"

        private const val STATE_IDLE = "idle"
        private const val STATE_RUNNING = "running"
        private const val STATE_COMPLETED = "completed"
        private const val STATE_FAILED = "failed"
    }
}
