package com.mapsupervision.ai.model.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.mapsupervision.ai.core.ThermalStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow


/**
 * Resource Monitor for thermal and battery status
 * Monitors device resources and provides updates for AI engine bypass decisions
 */
class ResourceMonitor(private val context: Context) {
    private val resourceEvents = Channel<ResourceEvent>(capacity = Channel.UNLIMITED)
    private var isMonitoring = false
    
    private val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { status ->
            val mappedStatus = mapThermalStatus(status)
            resourceEvents.trySend(ResourceEvent.ThermalUpdate(mappedStatus))
        }
    } else null

    private fun mapThermalStatus(status: Int): ThermalStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.CRITICAL
                else -> ThermalStatus.NORMAL
            }
        }
        return ThermalStatus.NORMAL
    }

    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                           status == BatteryManager.BATTERY_STATUS_FULL
            
            val event = ResourceEvent.BatteryUpdate(level, isCharging)
            resourceEvents.trySend(event)
        }
    }
    
    private val thermalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Android doesn't have a direct thermal broadcast, but we can infer from system
            // In production, you'd use a more sophisticated approach
            val event = ResourceEvent.ThermalUpdate(ThermalStatus.NORMAL)
            resourceEvents.trySend(event)
        }
    }
    
    /**
     * Start monitoring resources
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, batteryFilter)
        
        // Đăng ký bộ lắng nghe sự kiện nhiệt độ phần cứng từ Android Q (API 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            thermalListener?.let { powerManager.addThermalStatusListener(it) }
        }
        
        isMonitoring = true
    }
    
    /**
     * Stop monitoring resources
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        
        // Hủy đăng ký lắng nghe sự kiện nhiệt độ để tránh rò rỉ bộ nhớ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            thermalListener?.let { powerManager.removeThermalStatusListener(it) }
        }
        
        isMonitoring = false
    }
    
    /**
     * Get resource events as flow
     */
    fun getResourceEvents(): Flow<ResourceEvent> = resourceEvents.receiveAsFlow()
    
    /**
     * Get current resource status
     */
    fun getCurrentStatus(): ResourceStatus {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging()
        
        // Lấy trạng thái nhiệt độ thời gian thực
        val thermalStatus = estimateThermalStatus(powerManager)
        
        return ResourceStatus(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            thermalStatus = thermalStatus
        )
    }
    
    private fun estimateThermalStatus(powerManager: PowerManager): ThermalStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return mapThermalStatus(powerManager.currentThermalStatus)
        }
        
        // Trình dự phòng dựa trên uptime cho thiết bị cũ
        val uptimeMs = android.os.SystemClock.uptimeMillis()
        val uptimeHours = uptimeMs / (1000 * 60 * 60)
        
        return when {
            uptimeHours > 72 -> ThermalStatus.CRITICAL
            uptimeHours > 48 -> ThermalStatus.SEVERE
            uptimeHours > 24 -> ThermalStatus.MODERATE
            else -> ThermalStatus.NORMAL
        }
    }

    
    /**
     * Check if AI should be bypassed based on current resources
     */
    fun shouldBypassAi(threshold: ThermalStatus = ThermalStatus.SEVERE, batteryThreshold: Int = 20): Boolean {
        val status = getCurrentStatus()
        
        // Bypass if thermal status is critical
        val thermalIndex = ThermalStatus.values().indexOf(status.thermalStatus)
        val thresholdIndex = ThermalStatus.values().indexOf(threshold)
        if (thermalIndex >= thresholdIndex) {
            return true
        }
        
        // Bypass if battery is low and not charging
        if (status.batteryLevel < batteryThreshold && !status.isCharging) {
            return true
        }
        
        return false
    }
}

/**
 * Resource event types
 */
sealed class ResourceEvent {
    data class BatteryUpdate(val level: Int, val isCharging: Boolean) : ResourceEvent()
    data class ThermalUpdate(val status: ThermalStatus) : ResourceEvent()
}

/**
 * Current resource status
 */
data class ResourceStatus(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val thermalStatus: ThermalStatus
)
