package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.DailyLogLineEntity

@Dao
interface DailyLogLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyLogLineEntity>)

    @Query("DELETE FROM daily_log_line WHERE projectId = :projectId AND dailyLogId = :dailyLogId")
    suspend fun deleteForLog(projectId: String, dailyLogId: String)

    @Query("SELECT * FROM daily_log_line WHERE projectId = :projectId AND dailyLogId IN (:dailyLogIds) ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun byLogIds(projectId: String, dailyLogIds: List<String>): List<DailyLogLineEntity>
}
