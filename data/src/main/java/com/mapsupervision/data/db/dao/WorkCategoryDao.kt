package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkCategoryEntity)

    @Query("SELECT * FROM work_categories WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<WorkCategoryEntity>

    @Query("SELECT * FROM work_categories WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<WorkCategoryEntity>>
}
