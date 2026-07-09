package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photo_tags",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = SitePhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["projectId", "photoId"]),
        Index("photoId"),
        Index(value = ["projectId", "tagCode"])
    ]
)
data class PhotoTagEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val photoId: String,
    val tagCode: String,
    val createdAtEpochMs: Long
)
