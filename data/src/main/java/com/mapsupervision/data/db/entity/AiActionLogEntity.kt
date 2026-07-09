package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_action_log",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index(value = ["projectId", "timestamp"])
    ]
)
data class AiActionLogEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val rawInput: String,
    val actionType: String,
    val draftJson: String,
    val confidence: Int,
    val status: String, // e.g. "DRAFT_CREATED", "CONFIRMED", "REJECTED", "COMMITTED", "FAILED"
    val timestamp: Long
)
