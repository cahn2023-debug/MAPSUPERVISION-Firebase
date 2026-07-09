package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.AiDecisionCacheEntity

@Dao
interface AiDecisionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiDecisionCacheEntity)

    @Query(
        "SELECT * FROM ai_decision_cache WHERE projectId = :projectId AND capability = :capability AND payloadHash = :payloadHash LIMIT 1"
    )
    suspend fun find(projectId: String, capability: String, payloadHash: String): AiDecisionCacheEntity?
}
