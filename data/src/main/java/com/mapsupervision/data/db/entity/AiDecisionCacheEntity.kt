package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_decision_cache",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "capability", "payloadHash"], unique = true),
        Index("projectId")
    ]
)
data class AiDecisionCacheEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val capability: String,
    val payloadHash: String,
    val resultJson: String,
    val createdAtEpochMs: Long
)
