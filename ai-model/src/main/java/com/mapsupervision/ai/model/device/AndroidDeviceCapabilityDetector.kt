package com.mapsupervision.ai.model.device

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.mapsupervision.ai.core.DeviceCapabilities
import com.mapsupervision.ai.core.DeviceCapabilityDetector
import com.mapsupervision.ai.core.ThermalStatus

/**
 * Android implementation of device capability detector
 */
class AndroidDeviceCapabilityDetector(private val context: Context) : DeviceCapabilityDetector {
    
    override suspend fun detectCapabilities(): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        // Get RAM information
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availableRamMb = memInfo.availMem / (1024 * 1024)
        
        // Get CPU core count
        val cpuCoreCount = Runtime.getRuntime().availableProcessors()
        
        // Check for NPU (Neural Processing Unit)
        val hasNpu = checkForNpu()
        
        // Get battery level
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        // Check if charging
        val isCharging = batteryManager.isCharging
        
        // Get thermal status
        val thermalStatus = getThermalStatus(powerManager)
        
        return DeviceCapabilities(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            cpuCoreCount = cpuCoreCount,
            hasNpu = hasNpu,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            thermalStatus = thermalStatus
        )
    }
    
    private fun checkForNpu(): Boolean {
        // Check for common NPU indicators
        // This is a simplified check - in production, you'd want more sophisticated detection
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val hasNpuIndicators = hardware.contains("npu") || 
                              hardware.contains("npu") ||
                              board.contains("npu") ||
                              board.contains("npu")
        
        // Check for specific chipsets known to have NPUs
        val chipset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.lowercase()
        } else {
            ""
        }
        val hasKnownNpuChipset = chipset.contains("snapdragon") ||
                                 chipset.contains("mediatek") ||
                                 chipset.contains("exynos") ||
                                 chipset.contains("kirin")
        
        return hasNpuIndicators || hasKnownNpuChipset
    }
    
    private fun getThermalStatus(powerManager: PowerManager): ThermalStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.CRITICAL
                else -> ThermalStatus.NORMAL
            }
        }
        
        // Trình dự phòng (Fallback) dựa trên uptime cho Android dưới 10 (Q)
        val uptimeMs = SystemClock.uptimeMillis()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        
        return when {
            uptimeHours > 72 -> ThermalStatus.CRITICAL
            uptimeHours > 48 -> ThermalStatus.SEVERE
            uptimeHours > 24 -> ThermalStatus.MODERATE
            else -> ThermalStatus.NORMAL
        }
    }

}
