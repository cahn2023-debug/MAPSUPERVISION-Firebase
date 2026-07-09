package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mapsupervision.data.db.entity.ImportedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImportedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ImportedFileEntity>)

    @Query("SELECT * FROM imported_files WHERE projectId = :projectId AND isDeleted = 0 ORDER BY importedAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<ImportedFileEntity>

    @Query("SELECT * FROM imported_files WHERE projectId = :projectId AND isDeleted = 0 ORDER BY importedAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<ImportedFileEntity>>

    @Query("SELECT * FROM imported_files WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ImportedFileEntity?

    @Query("UPDATE imported_files SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id AND isDeleted = 0")
    suspend fun markFileDeleted(id: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("DELETE FROM imported_files WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int

    @Query("UPDATE gis_node SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE importedFileId = :fileId AND isDeleted = 0")
    suspend fun markGisNodesDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE gis_route SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE importedFileId = :fileId AND isDeleted = 0")
    suspend fun markGisRoutesDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("""
        UPDATE work_volume_progress
        SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs
        WHERE projectId = (SELECT projectId FROM imported_files WHERE id = :fileId)
          AND nodeId IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)
          AND isDeleted = 0
    """)
    suspend fun markMaterialProgressDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("""
        UPDATE node_progress
        SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs
        WHERE projectId = (SELECT projectId FROM imported_files WHERE id = :fileId)
          AND nodeId IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)
          AND isDeleted = 0
    """)
    suspend fun markNodeProgressDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("""
        UPDATE note
        SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs
        WHERE projectId = (SELECT projectId FROM imported_files WHERE id = :fileId)
          AND (
            objectNodeId IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)
            OR objectRouteId IN (SELECT id FROM gis_route WHERE importedFileId = :fileId)
          )
          AND isDeleted = 0
    """)
    suspend fun markNotesDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("""
        UPDATE task
        SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs
        WHERE projectId = (SELECT projectId FROM imported_files WHERE id = :fileId)
          AND (
            objectNodeId IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)
            OR objectRouteId IN (SELECT id FROM gis_route WHERE importedFileId = :fileId)
          )
          AND isDeleted = 0
    """)
    suspend fun markTasksDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("""
        UPDATE site_photos
        SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs
        WHERE projectId = (SELECT projectId FROM imported_files WHERE id = :fileId)
          AND (
            matchedNodeId IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)
            OR matchedRouteId IN (SELECT id FROM gis_route WHERE importedFileId = :fileId)
          )
          AND isDeleted = 0
    """)
    suspend fun markPhotosDeletedByFile(fileId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Transaction
    suspend fun deleteById(id: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long) {
        markMaterialProgressDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markNodeProgressDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markNotesDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markTasksDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markPhotosDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markGisNodesDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markGisRoutesDeletedByFile(id, updatedAtEpochMs, deletedAtEpochMs)
        markFileDeleted(id, updatedAtEpochMs, deletedAtEpochMs)
    }
}
