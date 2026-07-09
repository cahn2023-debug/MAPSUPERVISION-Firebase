package com.mapsupervision.data.repository

import com.mapsupervision.data.db.dao.AiDecisionCacheDao
import com.mapsupervision.data.db.entity.AiDecisionCacheEntity
import com.mapsupervision.ai.core.AiCapability
import com.mapsupervision.ai.core.repository.AiDecisionCacheStore
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiDecisionCacheStoreImpl @Inject constructor(
    private val sharedDao: AiDecisionCacheDao
) : AiDecisionCacheStore {
    override suspend fun get(projectId: String, capability: AiCapability, payloadHash: String): String? =
        withContext(Dispatchers.IO) {
            sharedDao.find(projectId, capability.name, payloadHash)?.resultJson
        }

    override suspend fun put(projectId: String, capability: AiCapability, payloadHash: String, resultJson: String) {
        withContext(Dispatchers.IO) {
            sharedDao.upsert(
                AiDecisionCacheEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    capability = capability.name,
                    payloadHash = payloadHash,
                    resultJson = resultJson,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }
}
