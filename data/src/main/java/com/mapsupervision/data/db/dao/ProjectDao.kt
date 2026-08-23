package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import com.mapsupervision.data.db.entity.ProjectEntity
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SimpleSQLiteQuery

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectEntity)

    @Query("SELECT * FROM projects WHERE isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0) ORDER BY createdAtEpochMs DESC")
    suspend fun list(includeArchived: Boolean): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects WHERE slug = :slug AND isDeleted = 0")
    suspend fun countActiveBySlug(slug: String): Int

    @Query("UPDATE projects SET metadataVersion = :metadataVersion, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
    suspend fun touch(projectId: String, metadataVersion: Int, updatedAtEpochMs: Long)

    @Query("UPDATE projects SET projectDbPath = :projectDbPath WHERE id = :projectId")
    suspend fun updateProjectDbPath(projectId: String, projectDbPath: String)

    @Query("UPDATE projects SET mediaStorageProvider = 'GOOGLE_DRIVE', mediaStorageFolderId = :folderId, mediaStorageFolderUrl = :folderUrl, mediaStorageUpdatedAtEpochMs = :updatedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
    suspend fun updateMediaStorage(projectId: String, folderId: String, folderUrl: String, updatedAtEpochMs: Long)

    @Query("UPDATE projects SET isArchived = 1 WHERE id = :projectId")
    suspend fun archive(projectId: String)

    @Query("UPDATE projects SET deletionState = 'DELETING', deletionRequestId = :requestId, deletionErrorCode = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND isDeleted = 0 AND deletionState IN ('ACTIVE', 'DELETE_FAILED')")
    suspend fun requestDeletion(projectId: String, requestId: String, updatedAtEpochMs: Long): Int

    @Query("UPDATE projects SET deletionState = 'DELETE_FAILED', deletionErrorCode = :errorCode, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND deletionRequestId = :requestId AND isDeleted = 0")
    suspend fun markDeletionFailed(projectId: String, requestId: String, errorCode: String, updatedAtEpochMs: Long): Int

    @Query("UPDATE projects SET cloudDeletionCompletedAtEpochMs = :completedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND deletionRequestId = :requestId AND deletionState = 'DELETING' AND isDeleted = 0")
    suspend fun markCloudDeletionCompleted(projectId: String, requestId: String, completedAtEpochMs: Long, updatedAtEpochMs: Long): Int

    @Query("UPDATE projects SET deletionState = 'DELETED', deletionRequestId = :requestId, cloudDeletionCompletedAtEpochMs = :completedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND isDeleted = 0")
    suspend fun markRemoteDeletion(projectId: String, requestId: String, completedAtEpochMs: Long, updatedAtEpochMs: Long): Int

    @Query("UPDATE projects SET isDeleted = 1, deletionState = 'DELETED', deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND deletionRequestId = :requestId AND deletionState = 'DELETING' AND cloudDeletionCompletedAtEpochMs IS NOT NULL")
    suspend fun completeLocalDeletion(projectId: String, requestId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long): Int

    @Query("UPDATE projects SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId AND isDeleted = 0 AND deletionState = 'DELETED'")
    suspend fun completeRemoteLocalDeletion(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long): Int

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
        clearProjectRows(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markProjectDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
    }

    @Transaction
    suspend fun clearProjectRows(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long) {
        markGisNodesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markGisRoutesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markNotesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markTasksDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markWorkVolumeProgressDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markNodeProgressDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markSitePhotosDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markImportedFilesDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
        markDailyLogsDeleted(projectId, updatedAtEpochMs, deletedAtEpochMs)
    }

    @RawQuery
    suspend fun executeProjectPurge(query: SupportSQLiteQuery): Int

    @Transaction
    suspend fun purgeProjectRows(projectId: String) {
        val tables = listOf(
            "daily_log_photos",
            "daily_log_nodes",
            "daily_log_line",
            "photo_tags",
            "import_audit",
            "import_conflict",
            "import_version",
            "import_session",
            "event_outbox",
            "rag_document_embedding",
            "ai_action_log",
            "ai_decision_cache",
            "chat_history",
            "report_draft",
            "material_handover",
            "material_declaration",
            "work_plan",
            "work_categories",
            "note",
            "task",
            "site_photos",
            "node_progress",
            "work_volume_progress",
            "daily_log",
            "gis_route",
            "gis_node",
            "imported_files"
        )
        tables.forEach { table ->
            executeProjectPurge(SimpleSQLiteQuery("DELETE FROM `$table` WHERE projectId = ?", arrayOf(projectId)))
        }
    }
}

