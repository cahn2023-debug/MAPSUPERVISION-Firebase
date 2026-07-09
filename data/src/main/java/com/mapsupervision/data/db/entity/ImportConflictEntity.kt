package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_conflict",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ImportSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["importSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("importSessionId"),
        Index(value = ["projectId", "severity", "createdAtEpochMs"])
    ]
)
data class ImportConflictEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val importSessionId: String,
    val featureBusinessCode: String,
    val conflictType: String,
    val severity: String,
    val details: String,
    val resolvedBy: String? = null,
    val resolvedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

