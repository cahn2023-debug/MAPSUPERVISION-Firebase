package com.mapsupervision.ai.core.repository

import com.mapsupervision.ai.core.AiCapability

interface AiDecisionCacheStore {
    suspend fun get(projectId: String, capability: AiCapability, payloadHash: String): String?
    suspend fun put(projectId: String, capability: AiCapability, payloadHash: String, resultJson: String)
}
