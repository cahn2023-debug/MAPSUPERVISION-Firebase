package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatHistoryEntity)

    @Query("SELECT * FROM chat_history WHERE projectId = :projectId ORDER BY createdAtEpochMs ASC")
    suspend fun byProject(projectId: String): List<ChatHistoryEntity>

    @Query("SELECT * FROM chat_history WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun recentByProject(projectId: String, limit: Int): List<ChatHistoryEntity>

    @Query("SELECT * FROM chat_history WHERE projectId = :projectId ORDER BY createdAtEpochMs ASC")
    fun observeByProject(projectId: String): Flow<List<ChatHistoryEntity>>

    @Query("DELETE FROM chat_history WHERE projectId = :projectId")
    suspend fun clearByProject(projectId: String)
}
