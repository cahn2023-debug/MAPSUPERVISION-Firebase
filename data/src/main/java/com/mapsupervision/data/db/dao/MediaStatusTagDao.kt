package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.MediaStatusTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaStatusTagDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MediaStatusTagEntity)

    @Query("SELECT * FROM media_status_tags WHERE projectId = :projectId ORDER BY createdAtEpochMs ASC")
    suspend fun byProject(projectId: String): List<MediaStatusTagEntity>

    @Query("SELECT * FROM media_status_tags WHERE projectId = :projectId ORDER BY createdAtEpochMs ASC")
    fun observeByProject(projectId: String): Flow<List<MediaStatusTagEntity>>
}
