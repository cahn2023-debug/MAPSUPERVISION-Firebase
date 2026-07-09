package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyLogEntity)

    @Query("SELECT * FROM daily_log WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<DailyLogEntity>

    @Query("SELECT * FROM daily_log WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProjectIncludingDeleted(projectId: String): List<DailyLogEntity>

    @Query("SELECT * FROM daily_log WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_log WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<DailyLogEntity>
}
