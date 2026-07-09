package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_log_photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = DailyLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["dailyLogId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = SitePhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "dailyLogId"]),
        Index("dailyLogId"),
        Index("photoId")
    ]
)
data class DailyLogPhotoEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val dailyLogId: String,
    val photoId: String,
    val createdAtEpochMs: Long
)
