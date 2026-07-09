package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_version",
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
        Index(value = ["projectId", "versionNumber"], unique = true)
    ]
)
data class ImportVersionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val importSessionId: String,
    val versionNumber: Int,
    val sourceHash: String,
    val createdAtEpochMs: Long,
    val createdBy: String = "",
    val note: String = ""
)

