package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.DailyLogPhotoEntity

@Dao
interface DailyLogPhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyLogPhotoEntity>)

    @Query("DELETE FROM daily_log_photos WHERE projectId = :projectId AND dailyLogId = :dailyLogId")
    suspend fun deleteForLog(projectId: String, dailyLogId: String)

    @Query("SELECT * FROM daily_log_photos WHERE projectId = :projectId AND dailyLogId IN (:dailyLogIds)")
    suspend fun byLogIds(projectId: String, dailyLogIds: List<String>): List<DailyLogPhotoEntity>
}
