package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "node_progress",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
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
        Index(value = ["projectId", "nodeId"], unique = true),
        Index("nodeId")
    ]
)
data class NodeProgressEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val planned: Float,
    val actual: Float,
    val remain: Float,
    val delayed: Boolean,
    val updatedAtEpochMs: Long = 0L,
    val nodeId: String? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
