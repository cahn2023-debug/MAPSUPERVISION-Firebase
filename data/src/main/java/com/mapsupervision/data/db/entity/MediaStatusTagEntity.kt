package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_status_tags",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "normalizedName"], unique = true),
        Index(value = ["projectId", "createdAtEpochMs"])
    ]
)
data class MediaStatusTagEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val normalizedName: String,
    val createdAtEpochMs: Long
)
