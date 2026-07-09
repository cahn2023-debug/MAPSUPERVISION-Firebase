package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.GemmaModelFamily
import com.mapsupervision.ai.core.GemmaModelInfo
import com.mapsupervision.ai.core.LiteRtSafetyGate
import com.mapsupervision.ai.core.ThermalStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtSafetyGateTest {

    private val model = GemmaModelInfo(
        family = GemmaModelFamily.QWEN3_0_6B,
        displayName = "Qwen3 0.6B INT4",
        estimatedSizeMb = 474,
        recommendedMinAvailableRamMb = 1536,
        recommendedMinFreeStorageMb = 1200,
        downloadFileName = "qwen3_0_6b_mixed_int4.litertlm",
        expectedBytes = 497_664_000L
    )

    @Test
    fun `allows local model when resources are safe`() {
        assertTrue(
            LiteRtSafetyGate.canRun(
                model = model,
                availableRamMb = 4096,
                thermalStatus = ThermalStatus.NORMAL,
                batteryLevel = 80,
                isCharging = false
            )
        )
    }

    @Test
    fun `rejects local model when ram is too low`() {
        assertFalse(
            LiteRtSafetyGate.canRun(
                model = model,
                availableRamMb = 1024,
                thermalStatus = ThermalStatus.NORMAL,
                batteryLevel = 80,
                isCharging = false
            )
        )
    }

    @Test
    fun `rejects local model when thermal is severe`() {
        assertFalse(
            LiteRtSafetyGate.canRun(
                model = model,
                availableRamMb = 4096,
                thermalStatus = ThermalStatus.SEVERE,
                batteryLevel = 80,
                isCharging = false
            )
        )
    }

    @Test
    fun `rejects local model when battery is low and not charging`() {
        assertFalse(
            LiteRtSafetyGate.canRun(
                model = model,
                availableRamMb = 4096,
                thermalStatus = ThermalStatus.NORMAL,
                batteryLevel = 15,
                isCharging = false
            )
        )
    }
}

