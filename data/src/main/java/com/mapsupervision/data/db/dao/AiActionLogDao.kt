package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.AiActionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiActionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiActionLogEntity)

    @Query("SELECT * FROM ai_action_log WHERE projectId = :projectId ORDER BY timestamp DESC")
    suspend fun byProject(projectId: String): List<AiActionLogEntity>

    @Query("SELECT * FROM ai_action_log WHERE id = :id")
    suspend fun getById(id: String): AiActionLogEntity?

    @Query("SELECT * FROM ai_action_log WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun observeByProject(projectId: String): Flow<List<AiActionLogEntity>>
}
