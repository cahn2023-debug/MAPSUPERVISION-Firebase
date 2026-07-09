package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.DailyLogNodeEntity

@Dao
interface DailyLogNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyLogNodeEntity>)

    @Query("DELETE FROM daily_log_nodes WHERE projectId = :projectId AND dailyLogId = :dailyLogId")
    suspend fun deleteForLog(projectId: String, dailyLogId: String)

    @Query("SELECT * FROM daily_log_nodes WHERE projectId = :projectId AND dailyLogId IN (:dailyLogIds)")
    suspend fun byLogIds(projectId: String, dailyLogIds: List<String>): List<DailyLogNodeEntity>
}
