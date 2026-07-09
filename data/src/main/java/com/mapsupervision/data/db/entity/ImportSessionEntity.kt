package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_session",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ImportedFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["importedFileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("projectId"),
        Index("importedFileId"),
        Index(value = ["projectId", "createdAtEpochMs"])
    ]
)
data class ImportSessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sourceKind: String,
    val sourceFileName: String,
    val sourceFileType: String,
    val sourceFilePath: String,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val importedFileId: String? = null,
    val featureCount: Int = 0,
    val conflictCount: Int = 0,
    val warningCount: Int = 0,
    val message: String = ""
)

