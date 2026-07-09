package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_outbox",
    indices = [
        Index(value = ["status", "availableAtEpochMs"]),
        Index(value = ["projectId", "createdAtEpochMs"])
    ]
)
data class EventOutboxEntity(
    @PrimaryKey val id: String,
    val projectId: String? = null,
    val eventType: String,
    val payloadJson: String,
    val status: String = "PENDING",
    val availableAtEpochMs: Long = System.currentTimeMillis(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val dispatchedAtEpochMs: Long? = null
)

