package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.*
import com.mapsupervision.ai.model.engines.LocalLiteRtEngine
import com.mapsupervision.ai.model.engines.RuleBasedEngine
import com.mapsupervision.domain.repository.LocalLlmRepository
import com.mapsupervision.domain.repository.LocalLlmRequest
import com.mapsupervision.domain.repository.LocalLlmResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiOrchestratorRoutingTest {

    @Test
    fun `local lite stays enabled when media pipe is disabled`() = runBlocking {
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

        orchestrator.updateConfig(
            SmartRouterConfig(
                enableLocalLiteRt = true,
                enableMediaPipeLlm = false
            )
        )

        val policy = orchestrator.determineExecutionPolicy(
            ReportDraftPayload("P1", 10, 1, 70f, 3),
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

        assertEquals(AiExecutionPolicy.LOCAL_LITERT, policy)
    }

    @Test
    fun `local lite can be disabled independently of media pipe`() = runBlocking {
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

        orchestrator.updateConfig(
            SmartRouterConfig(
                enableLocalLiteRt = false,
                enableMediaPipeLlm = true
            )
        )

        val policy = orchestrator.determineExecutionPolicy(
            ReportDraftPayload("P1", 10, 1, 70f, 3),
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

        assertEquals(AiExecutionPolicy.RULE_BASED, policy)
    }

    private class FakeLocalLlmRepository : LocalLlmRepository {
        override suspend fun isReady() = true
        override suspend fun warmUp() = true
        override suspend fun generate(request: LocalLlmRequest) = LocalLlmResponse("res", "fake", "fake")
        override fun cancel() = Unit
    }
}

