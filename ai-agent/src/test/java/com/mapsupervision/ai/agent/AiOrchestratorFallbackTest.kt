package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.*
import com.mapsupervision.domain.repository.LocalLlmRepository
import com.mapsupervision.domain.repository.LocalLlmRequest
import com.mapsupervision.domain.repository.LocalLlmResponse
import com.mapsupervision.ai.core.repository.AiRepository
import com.mapsupervision.ai.model.engines.LocalLiteRtEngine
import com.mapsupervision.ai.model.engines.RuleBasedEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiOrchestratorFallbackTest {

    @Test
    fun `fallback to RULE_BASED when thermal is critical`() = runBlocking {
        val orchestrator = AiOrchestrator(
            deviceCapabilityDetector = object : DeviceCapabilityDetector {
                override suspend fun detectCapabilities() = DeviceCapabilities(
                    totalRamMb = 4096,
                    availableRamMb = 2048,
                    cpuCoreCount = 4,
                    hasNpu = false,
                    batteryLevel = 80,
                    isCharging = true,
                    thermalStatus = ThermalStatus.CRITICAL
                )
            },
            resourceGate = NoOpResourceGate,
            initialEngines = listOf(
                LocalLiteRtEngine(FakeLocalLlmRepository()),
                RuleBasedEngine()
            ),
            decisionCacheStore = null
        )

        val payload = ReportDraftPayload("P1", 10, 2, 60f, 0)
        val caps = orchestrator.determineExecutionPolicy(
            payload,
            DeviceCapabilities(
                totalRamMb = 4096,
                availableRamMb = 2048,
                cpuCoreCount = 4,
                hasNpu = false,
                batteryLevel = 80,
                isCharging = true,
                thermalStatus = ThermalStatus.CRITICAL
            )
        )
        assertEquals(AiExecutionPolicy.RULE_BASED, caps)
    }

    @Test
    fun `fallback to RULE_BASED when battery is low and not charging`() = runBlocking {
        val orchestrator = AiOrchestrator(
            deviceCapabilityDetector = object : DeviceCapabilityDetector {
                override suspend fun detectCapabilities() = DeviceCapabilities(
                    totalRamMb = 4096,
                    availableRamMb = 2048,
                    cpuCoreCount = 4,
                    hasNpu = false,
                    batteryLevel = 10,
                    isCharging = false,
                    thermalStatus = ThermalStatus.NORMAL
                )
            },
            resourceGate = NoOpResourceGate,
            initialEngines = listOf(
                LocalLiteRtEngine(FakeLocalLlmRepository()),
                RuleBasedEngine()
            ),
            decisionCacheStore = null
        )

        val payload = ReportDraftPayload("P1", 10, 2, 60f, 0)
        val caps = orchestrator.determineExecutionPolicy(
            payload,
            DeviceCapabilities(
                totalRamMb = 4096,
                availableRamMb = 2048,
                cpuCoreCount = 4,
                hasNpu = false,
                batteryLevel = 10,
                isCharging = false,
                thermalStatus = ThermalStatus.NORMAL
            )
        )
        assertEquals(AiExecutionPolicy.RULE_BASED, caps)
    }

    @Test
    fun `uses LOCAL_LITERT when resources are sufficient`() = runBlocking {
        val orchestrator = AiOrchestrator(
            deviceCapabilityDetector = object : DeviceCapabilityDetector {
                override suspend fun detectCapabilities() = DeviceCapabilities(
                    totalRamMb = 8192,
                    availableRamMb = 4096,
                    cpuCoreCount = 8,
                    hasNpu = true,
                    batteryLevel = 90,
                    isCharging = true,
                    thermalStatus = ThermalStatus.NORMAL
                )
            },
            resourceGate = NoOpResourceGate,
            initialEngines = listOf(
                LocalLiteRtEngine(FakeLocalLlmRepository()),
                RuleBasedEngine()
            ),
            decisionCacheStore = null
        )

        val payload = ReportDraftPayload("P1", 10, 2, 60f, 0)
        val caps = orchestrator.determineExecutionPolicy(
            payload,
            DeviceCapabilities(
                totalRamMb = 8192,
                availableRamMb = 4096,
                cpuCoreCount = 8,
                hasNpu = true,
                batteryLevel = 90,
                isCharging = true,
                thermalStatus = ThermalStatus.NORMAL
            )
        )
        assertEquals(AiExecutionPolicy.LOCAL_LITERT, caps)
    }

    private class FakeLocalLlmRepository : LocalLlmRepository {
        override suspend fun isReady() = true
        override suspend fun warmUp() = true
        override suspend fun generate(request: LocalLlmRequest) = LocalLlmResponse("res", "fake", "fake")
        override fun cancel() = Unit
    }
}

