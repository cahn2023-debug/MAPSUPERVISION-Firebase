package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.ReportDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReportDraftEntity)

    @Query("SELECT * FROM report_draft WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<ReportDraftEntity>

    @Query("SELECT * FROM report_draft WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<ReportDraftEntity>>

    @Query("DELETE FROM report_draft WHERE id = :id")
    suspend fun delete(id: String)
}
