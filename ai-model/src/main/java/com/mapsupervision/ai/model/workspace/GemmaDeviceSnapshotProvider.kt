package com.mapsupervision.ai.model.workspace

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.core.content.getSystemService
import com.mapsupervision.ai.core.GemmaDeviceSnapshot
import com.mapsupervision.ai.core.ThermalStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaDeviceSnapshotProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun snapshot(): GemmaDeviceSnapshot {
        val activityManager = context.getSystemService<ActivityManager>()
        val memoryInfo = ActivityManager.MemoryInfo().also {
            activityManager?.getMemoryInfo(it)
        }
        val storage = StatFs(context.filesDir.absolutePath)
        val freeStorageMb = storage.availableBytes / (1024 * 1024)
        val thermal = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            when (context.getSystemService<android.os.PowerManager>()?.currentThermalStatus ?: 0) {
                0 -> ThermalStatus.NORMAL
                1 -> ThermalStatus.MODERATE
                2 -> ThermalStatus.SEVERE
                else -> ThermalStatus.CRITICAL
            }
        } else {
            ThermalStatus.NORMAL
        }
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?.let { intent ->
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 80)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
            } ?: 80
        val isCharging = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?.let { intent ->
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            } ?: false
        return GemmaDeviceSnapshot(
            availableRamMb = (memoryInfo?.availMem ?: 0L) / (1024 * 1024),
            freeStorageMb = freeStorageMb,
            batteryLevel = battery,
            thermalStatus = thermal,
            isCharging = isCharging
        )
    }
}
