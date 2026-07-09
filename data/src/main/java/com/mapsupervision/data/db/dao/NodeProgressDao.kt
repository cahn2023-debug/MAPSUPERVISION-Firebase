package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.NodeProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NodeProgressEntity)

    @Query("SELECT np.* FROM node_progress np LEFT JOIN gis_node n ON np.nodeId = n.id WHERE np.projectId = :projectId AND np.isDeleted = 0 ORDER BY n.code")
    suspend fun byProject(projectId: String): List<NodeProgressEntity>

    @Query("SELECT np.* FROM node_progress np LEFT JOIN gis_node n ON np.nodeId = n.id WHERE np.projectId = :projectId AND np.isDeleted = 0 ORDER BY n.code")
    fun observeByProject(projectId: String): Flow<List<NodeProgressEntity>>

    @Query("SELECT * FROM node_progress WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<NodeProgressEntity>

    @Query("DELETE FROM node_progress WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
