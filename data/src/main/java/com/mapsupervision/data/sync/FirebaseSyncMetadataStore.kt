package com.mapsupervision.data.sync

import android.content.Context

internal class FirebaseSyncMetadataStore(context: Context) {
    private val prefs = context.getSharedPreferences("firebase_sync_meta", Context.MODE_PRIVATE)

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun lastPushedAt(projectId: String, tableName: String): Long =
        prefs.getLong("push:$projectId:$tableName", 0L)

    fun setLastPushedAt(projectId: String, tableName: String, epochMs: Long) {
        prefs.edit().putLong("push:$projectId:$tableName", epochMs).apply()
    }

    fun lastPulledAt(projectId: String, tableName: String): Long =
        prefs.getLong("pull:$projectId:$tableName", 0L)

    fun setLastPulledAt(projectId: String, tableName: String, epochMs: Long) {
        prefs.edit().putLong("pull:$projectId:$tableName", epochMs).apply()
    }

    fun setLastError(projectId: String, tableName: String, error: String?) {
        prefs.edit().putString("error:$projectId:$tableName", error).apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }
}
