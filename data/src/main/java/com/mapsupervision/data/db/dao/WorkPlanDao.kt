package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.WorkPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkPlanEntity)

    @Query("SELECT * FROM work_plan WHERE projectId = :projectId ORDER BY plannedDateEpochDay ASC, createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<WorkPlanEntity>

    @Query("SELECT * FROM work_plan WHERE projectId = :projectId ORDER BY plannedDateEpochDay ASC, createdAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<WorkPlanEntity>>
}
