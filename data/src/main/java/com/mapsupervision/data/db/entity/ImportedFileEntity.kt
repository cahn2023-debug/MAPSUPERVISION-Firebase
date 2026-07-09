package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "imported_files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["projectId", "importedAtEpochMs"])
    ]
)
data class ImportedFileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val fileName: String,
    val fileType: String,
    val storedPath: String,
    val summary: String,
    val importedAtEpochMs: Long,
    val updatedAtEpochMs: Long = importedAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
