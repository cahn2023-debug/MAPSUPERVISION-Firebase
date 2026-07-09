package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.domain.model.MediaType
import com.mapsupervision.domain.model.SitePhotoSyncStatus
import kotlinx.coroutines.flow.Flow

data class SitePhotoProjection(
    val id: String,
    val projectId: String,
    val objectCode: String,
    val tagCodesCsv: String,
    val matchedNodeCode: String?,
    val matchedRouteCode: String?,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val engineer: String,
    val capturedAtEpochMs: Long,
    val matchedAtEpochMs: Long,
    val matchingTimeOffsetMs: Long,
    val mediaType: MediaType,
    val mimeType: String,
    val durationMs: Long,
    val address: String?,
    val captureNote: String?,
    val updatedAtEpochMs: Long,
    val syncStatus: SitePhotoSyncStatus,
    val remoteUrl: String?,
    val lastSyncAttemptEpochMs: Long?,
    val matchedNodeId: String?,
    val matchedRouteId: String?
)

@Dao
interface SitePhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
         suspend fun upsert(entity: SitePhotoEntity)

    @Query("SELECT * FROM site_photos WHERE projectId = :projectId AND isDeleted = 0 ORDER BY capturedAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<SitePhotoEntity>

    @Query("SELECT * FROM site_photos WHERE projectId = :projectId ORDER BY capturedAtEpochMs DESC")
    suspend fun byProjectIncludingDeleted(projectId: String): List<SitePhotoEntity>

    @Query("SELECT * FROM site_photos WHERE projectId = :projectId AND isDeleted = 0 ORDER BY capturedAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<SitePhotoEntity>>

    @Query(
        """
        SELECT 
            sp.id, 
            sp.projectId, 
            sp.objectCode AS objectCode, 
            sp.tagCodesCsv, 
            n.code AS matchedNodeCode, 
            r.code AS matchedRouteCode, 
            sp.filePath, 
            sp.thumbnailPath, 
            sp.latitude, 
            sp.longitude, 
            sp.engineer, 
            sp.capturedAtEpochMs, 
            sp.matchedAtEpochMs, 
            sp.matchingTimeOffsetMs, 
            sp.mediaType, 
            sp.mimeType, 
            sp.durationMs, 
            sp.address, 
            sp.captureNote, 
            sp.updatedAtEpochMs, 
            sp.syncStatus, 
            sp.remoteUrl, 
            sp.lastSyncAttemptEpochMs,
            sp.matchedNodeId,
            sp.matchedRouteId
        FROM site_photos sp
        LEFT JOIN gis_node n ON sp.matchedNodeId = n.id
        LEFT JOIN gis_route r ON sp.matchedRouteId = r.id
        WHERE sp.projectId = :projectId AND sp.isDeleted = 0 
        ORDER BY sp.capturedAtEpochMs DESC
        """
    )
    suspend fun byProjectSummary(projectId: String): List<SitePhotoProjection>

    @Query(
        """
        SELECT 
            sp.id, 
            sp.projectId, 
            sp.objectCode AS objectCode, 
            sp.tagCodesCsv, 
            n.code AS matchedNodeCode, 
            r.code AS matchedRouteCode, 
            sp.filePath, 
            sp.thumbnailPath, 
            sp.latitude, 
            sp.longitude, 
            sp.engineer, 
            sp.capturedAtEpochMs, 
            sp.matchedAtEpochMs, 
            sp.matchingTimeOffsetMs, 
            sp.mediaType, 
            sp.mimeType, 
            sp.durationMs, 
            sp.address, 
            sp.captureNote, 
            sp.updatedAtEpochMs, 
            sp.syncStatus, 
            sp.remoteUrl, 
            sp.lastSyncAttemptEpochMs,
            sp.matchedNodeId,
            sp.matchedRouteId
        FROM site_photos sp
        LEFT JOIN gis_node n ON sp.matchedNodeId = n.id
        LEFT JOIN gis_route r ON sp.matchedRouteId = r.id
        WHERE sp.projectId = :projectId AND sp.isDeleted = 0 
        ORDER BY sp.capturedAtEpochMs DESC
        """
    )
    fun observeByProjectSummary(projectId: String): Flow<List<SitePhotoProjection>>

    @Query(
        """
        SELECT 
            sp.id, 
            sp.projectId, 
            sp.objectCode AS objectCode, 
            sp.tagCodesCsv, 
            n.code AS matchedNodeCode, 
            r.code AS matchedRouteCode, 
            sp.filePath, 
            sp.thumbnailPath, 
            sp.latitude, 
            sp.longitude, 
            sp.engineer, 
            sp.capturedAtEpochMs, 
            sp.matchedAtEpochMs, 
            sp.matchingTimeOffsetMs, 
            sp.mediaType, 
            sp.mimeType, 
            sp.durationMs, 
            sp.address, 
            sp.captureNote, 
            sp.updatedAtEpochMs, 
            sp.syncStatus, 
            sp.remoteUrl, 
            sp.lastSyncAttemptEpochMs,
            sp.matchedNodeId,
            sp.matchedRouteId
        FROM site_photos sp
        LEFT JOIN gis_node n ON sp.matchedNodeId = n.id
        LEFT JOIN gis_route r ON sp.matchedRouteId = r.id
        WHERE sp.projectId = :projectId AND sp.isDeleted = 0 AND sp.objectCode = :objectCode
        ORDER BY sp.capturedAtEpochMs DESC
        """
    )
    suspend fun byObjectCodeSummary(projectId: String, objectCode: String): List<SitePhotoProjection>

    @Query(
        """
        SELECT 
            sp.id, 
            sp.projectId, 
            sp.objectCode AS objectCode, 
            sp.tagCodesCsv, 
            n.code AS matchedNodeCode, 
            r.code AS matchedRouteCode, 
            sp.filePath, 
            sp.thumbnailPath, 
            sp.latitude, 
            sp.longitude, 
            sp.engineer, 
            sp.capturedAtEpochMs, 
            sp.matchedAtEpochMs, 
            sp.matchingTimeOffsetMs, 
            sp.mediaType, 
            sp.mimeType, 
            sp.durationMs, 
            sp.address, 
            sp.captureNote, 
            sp.updatedAtEpochMs, 
            sp.syncStatus, 
            sp.remoteUrl, 
            sp.lastSyncAttemptEpochMs,
            sp.matchedNodeId,
            sp.matchedRouteId
        FROM site_photos sp
        LEFT JOIN gis_node n ON sp.matchedNodeId = n.id
        LEFT JOIN gis_route r ON sp.matchedRouteId = r.id
        WHERE sp.projectId = :projectId AND sp.isDeleted = 0 AND n.code = :nodeCode
        ORDER BY sp.capturedAtEpochMs DESC
        """
    )
    suspend fun byMatchedNodeCodeSummary(projectId: String, nodeCode: String): List<SitePhotoProjection>

    @Query(
        """
        SELECT 
            sp.id, 
            sp.projectId, 
            sp.objectCode AS objectCode, 
            sp.tagCodesCsv, 
            n.code AS matchedNodeCode, 
            r.code AS matchedRouteCode, 
            sp.filePath, 
            sp.thumbnailPath, 
            sp.latitude, 
            sp.longitude, 
            sp.engineer, 
            sp.capturedAtEpochMs, 
            sp.matchedAtEpochMs, 
            sp.matchingTimeOffsetMs, 
            sp.mediaType, 
            sp.mimeType, 
            sp.durationMs, 
            sp.address, 
            sp.captureNote, 
            sp.updatedAtEpochMs, 
            sp.syncStatus, 
            sp.remoteUrl, 
            sp.lastSyncAttemptEpochMs,
            sp.matchedNodeId,
            sp.matchedRouteId
        FROM site_photos sp
        LEFT JOIN gis_node n ON sp.matchedNodeId = n.id
        LEFT JOIN gis_route r ON sp.matchedRouteId = r.id
        WHERE sp.projectId = :projectId AND sp.isDeleted = 0 AND r.code = :routeCode
        ORDER BY sp.capturedAtEpochMs DESC
        """
    )
    suspend fun byMatchedRouteCodeSummary(projectId: String, routeCode: String): List<SitePhotoProjection>

    @Query("SELECT * FROM site_photos WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<SitePhotoEntity>

    @Query("DELETE FROM site_photos WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
