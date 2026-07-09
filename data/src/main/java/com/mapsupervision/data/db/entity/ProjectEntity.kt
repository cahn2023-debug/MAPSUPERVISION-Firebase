package com.mapsupervision.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
