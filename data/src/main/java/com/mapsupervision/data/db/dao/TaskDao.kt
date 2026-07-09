package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Query("UPDATE task SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id AND isDeleted = 0")
    suspend fun markDeletedById(id: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query(
        "SELECT * FROM task WHERE projectId = :projectId AND isDeleted = 0 AND (" +
            "objectNodeId IN (SELECT id FROM gis_node WHERE projectId = :projectId AND code = :objectCode) OR " +
            "objectRouteId IN (SELECT id FROM gis_route WHERE projectId = :projectId AND code = :objectCode)" +
        ") ORDER BY createdAtEpochMs DESC"
    )
    suspend fun byObject(projectId: String, objectCode: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProjectIncludingDeleted(projectId: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<TaskEntity>

    @Query("DELETE FROM task WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
