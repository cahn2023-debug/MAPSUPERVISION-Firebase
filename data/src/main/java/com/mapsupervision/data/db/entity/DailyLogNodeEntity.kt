package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_log_nodes",
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
            entity = GisNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["projectId", "dailyLogId"]),
        Index("dailyLogId"),
        Index("nodeId")
    ]
)
data class DailyLogNodeEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val dailyLogId: String,
    val nodeId: String?,
    val nodeCodeSnapshot: String,
    val createdAtEpochMs: Long
)
