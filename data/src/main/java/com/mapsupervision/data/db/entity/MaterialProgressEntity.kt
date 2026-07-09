package com.mapsupervision.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_volume_progress",
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
        Index(value = ["projectId", "nodeCode", "workName"], unique = true),
        Index(value = ["projectId", "nodeId", "workName"]),
        Index("nodeCode"),
        Index("nodeId")
    ]
)
data class MaterialProgressEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val nodeCode: String,
    @ColumnInfo(name = "workName")
    val materialName: String,
    val plannedQty: Float,
    val actualQty: Float,
    val updatedAtEpochMs: Long,
    val unit: String = "",
    val nodeId: String? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
) {
    val workName: String
        get() = materialName
}
