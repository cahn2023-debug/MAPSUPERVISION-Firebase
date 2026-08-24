package com.mapsupervision.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.ProjectDeletionState
import com.mapsupervision.domain.model.ProjectStorageMode

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val slug: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    @ColumnInfo(defaultValue = "3")
    val metadataVersion: Int = 3,
    @ColumnInfo(defaultValue = "0")
    val updatedAtEpochMs: Long = createdAtEpochMs,
    @ColumnInfo(defaultValue = "LEGACY_SHARED")
    val storageMode: ProjectStorageMode = ProjectStorageMode.LEGACY_SHARED,
    @ColumnInfo(defaultValue = "")
    val projectDbPath: String = "",
    val projectCode: String? = null,
    @ColumnInfo(defaultValue = "GOOGLE_DRIVE")
    val mediaStorageProvider: String = "GOOGLE_DRIVE",
    @ColumnInfo(defaultValue = "")
    val mediaStorageFolderId: String = "",
    @ColumnInfo(defaultValue = "")
    val mediaStorageFolderUrl: String = "",
    @ColumnInfo(defaultValue = "0")
    val mediaStorageUpdatedAtEpochMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "ACTIVE")
    val deletionState: ProjectDeletionState = ProjectDeletionState.ACTIVE,
    val deletionRequestId: String? = null,
    val deletionErrorCode: String? = null,
    val cloudDeletionCompletedAtEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val cloudDataConfirmed: Boolean = false,
    val cloudDecisionRequestId: String? = null,
    val localDeletionErrorCode: String? = null
)
