package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhotoSyncStatus

@Entity(
    tableName = "site_photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = GisNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchedNodeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = GisRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchedRouteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["projectId", "capturedAtEpochMs"]),
        Index("matchedNodeId"),
        Index("matchedRouteId")
    ]
)
data class SitePhotoEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val objectCode: String,
    val tagCodesCsv: String,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val isGpsMocked: Boolean,
    val locationStatus: PhotoLocationStatus,
    val engineer: String,
    val capturedAtEpochMs: Long,
    val matchedAtEpochMs: Long,
    val matchingTimeOffsetMs: Long,
    val mediaType: com.mapsupervision.domain.model.MediaType = com.mapsupervision.domain.model.MediaType.IMAGE,
    val mimeType: String = "image/jpeg",
    val durationMs: Long = 0L,
    val address: String? = null,
    val captureNote: String? = null,
    val matchedNodeId: String? = null,
    val matchedRouteId: String? = null,
    val updatedAtEpochMs: Long = capturedAtEpochMs,
    val syncStatus: SitePhotoSyncStatus = SitePhotoSyncStatus.PENDING,
    val remoteUrl: String? = null,
    val lastSyncAttemptEpochMs: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
