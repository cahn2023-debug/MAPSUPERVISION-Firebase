package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gis_route",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ImportedFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["importedFileId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = GisNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["startNodeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = GisNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["endNodeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("importedFileId"),
        Index(value = ["projectId", "code"], unique = true),
        Index("startNodeId"),
        Index("endNodeId")
    ]
)
data class GisRouteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val startNodeCode: String,
    val endNodeCode: String,
    val points: List<Pair<Double, Double>>,
    val importedFileId: String? = null,
    val designLength: String? = null,
    val fiberCoreCount: Int? = null,
    val fiberConnection: String = "",
    val startNodeId: String? = null,
    val endNodeId: String? = null,
    val updatedAtEpochMs: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
