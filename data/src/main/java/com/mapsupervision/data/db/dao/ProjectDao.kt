package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mapsupervision.data.db.entity.ProjectEntity

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectEntity)

    @Query("SELECT * FROM projects WHERE isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0) ORDER BY createdAtEpochMs DESC")
    suspend fun list(includeArchived: Boolean): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectEntity?

    @Query("UPDATE projects SET metadataVersion = :metadataVersion, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
    suspend fun touch(projectId: String, metadataVersion: Int, updatedAtEpochMs: Long)

    @Query("UPDATE projects SET projectDbPath = :projectDbPath WHERE id = :projectId")
    suspend fun updateProjectDbPath(projectId: String, projectDbPath: String)

    @Query("UPDATE projects SET isArchived = 1 WHERE id = :projectId")
    suspend fun archive(projectId: String)

    @Query("UPDATE gis_node SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markGisNodesDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE gis_route SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markGisRoutesDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE note SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markNotesDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE task SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markTasksDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE work_volume_progress SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markWorkVolumeProgressDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE node_progress SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markNodeProgressDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE site_photos SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markSitePhotosDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE imported_files SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markImportedFilesDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE daily_log SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markDailyLogsDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE projects SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND isDeleted = 0")
    suspend fun markProjectDeleted(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Transaction
    suspend fun clearProjectData(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long) {
        markGisNodesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markGisRoutesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markNotesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markTasksDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markWorkVolumeProgressDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markNodeProgressDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markSitePhotosDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markImportedFilesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markDailyLogsDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markProjectDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
    }
}

