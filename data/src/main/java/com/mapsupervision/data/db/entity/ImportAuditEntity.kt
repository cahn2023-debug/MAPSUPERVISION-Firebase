package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_audit",
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
        Index(value = ["projectId", "createdAtEpochMs"])
    ]
)
data class ImportAuditEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val importSessionId: String,
    val action: String,
    val actor: String,
    val payloadJson: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

