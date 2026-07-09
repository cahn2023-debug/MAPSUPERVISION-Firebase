package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.GisNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GisNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GisNodeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GisNodeEntity>)

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND isDeleted = 0 ORDER BY code")
    suspend fun byProject(projectId: String): List<GisNodeEntity>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND isDeleted = 0 ORDER BY code")
    fun observeByProject(projectId: String): Flow<List<GisNodeEntity>>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND isDeleted = 0 AND (code LIKE '%' || :query || '%' OR contractor LIKE '%' || :query || '%') ORDER BY code")
    suspend fun search(projectId: String, query: String): List<GisNodeEntity>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND isDeleted = 0 AND (code = :query OR code LIKE :query || '%' OR contractor LIKE :query || '%') ORDER BY code")
    suspend fun searchFast(projectId: String, query: String): List<GisNodeEntity>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND isDeleted = 0 AND (code LIKE '%' || :query || '%' OR contractor LIKE '%' || :query || '%') ORDER BY code")
    fun observeSearch(projectId: String, query: String): Flow<List<GisNodeEntity>>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND code = :code AND isDeleted = 0 LIMIT 1")
    suspend fun findByCode(projectId: String, code: String): GisNodeEntity?

    @Query("UPDATE gis_node SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND importedFileId = :importedFileId AND isDeleted = 0")
    suspend fun markDeletedByImportedFileId(projectId: String, importedFileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<GisNodeEntity>

    @Query("DELETE FROM gis_node WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
