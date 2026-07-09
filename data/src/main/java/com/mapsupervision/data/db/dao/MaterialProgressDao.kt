package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import kotlinx.coroutines.flow.Flow

data class MaterialProgressProjection(
    val id: String,
    val nodeCode: String,
    val nodeId: String?,
    val workName: String,
    val plannedQty: Float,
    val actualQty: Float,
    val updatedAtEpochMs: Long,
    val unit: String
)

@Dao
interface MaterialProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MaterialProgressEntity)

    @Query("SELECT * FROM work_volume_progress WHERE projectId = :projectId AND isDeleted = 0 ORDER BY nodeCode, workName")
    suspend fun byProject(projectId: String): List<MaterialProgressEntity>

    @Query("SELECT * FROM work_volume_progress WHERE projectId = :projectId AND isDeleted = 0 ORDER BY nodeCode, workName")
    fun observeByProject(projectId: String): Flow<List<MaterialProgressEntity>>

    @Query("SELECT id, nodeCode, nodeId, workName, plannedQty, actualQty, updatedAtEpochMs, unit FROM work_volume_progress WHERE projectId = :projectId AND isDeleted = 0 ORDER BY nodeCode, workName")
    suspend fun byProjectSummary(projectId: String): List<MaterialProgressProjection>

    @Query("SELECT id, nodeCode, nodeId, workName, plannedQty, actualQty, updatedAtEpochMs, unit FROM work_volume_progress WHERE projectId = :projectId AND isDeleted = 0 ORDER BY nodeCode, workName")
    fun observeByProjectSummary(projectId: String): Flow<List<MaterialProgressProjection>>

    @Query("SELECT * FROM work_volume_progress WHERE projectId = :projectId AND nodeCode = :nodeCode AND workName = :workName AND isDeleted = 0 LIMIT 1")
    suspend fun findByNaturalKey(
        projectId: String,
        nodeCode: String,
        workName: String
    ): MaterialProgressEntity?

    @Query("SELECT * FROM work_volume_progress WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<MaterialProgressEntity>

    @Query("DELETE FROM work_volume_progress WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}

typealias WorkVolumeProgressDao = MaterialProgressDao
