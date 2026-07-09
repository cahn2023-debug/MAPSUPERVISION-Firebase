package com.mapsupervision.ai.core

object LiteRtSafetyGate {
    fun canRun(
        model: GemmaModelInfo,
        availableRamMb: Long,
        thermalStatus: ThermalStatus,
        batteryLevel: Int,
        isCharging: Boolean
    ): Boolean {
        if (availableRamMb < model.recommendedMinAvailableRamMb) return false
        if (thermalStatus == ThermalStatus.SEVERE || thermalStatus == ThermalStatus.CRITICAL) return false
        return isCharging || batteryLevel > 20
    }
}
