package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_log",
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
        ),
        ForeignKey(
            entity = GisRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["projectId", "dateEpochDay"]),
        Index(value = ["projectId", "batchGroupId"]),
        Index("nodeId"),
        Index("routeId")
    ]
)
data class DailyLogEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val workItem: String,
    val manpower: Int,
    val note: String,
    val createdAtEpochMs: Long,
    val weather: String,
    val temperature: Double,
    val dateEpochDay: Long,
    val volume: Double,
    val unit: String,
    val categoryName: String,
    val batchGroupId: String,
    val linkedWorkPlanId: String? = null,
    val plannedWorkName: String = "",
    val plannedQuantity: Double = 0.0,
    val plannedUnit: String = "",
    val photoMatchOffsetMinutes: Int,
    val nodeId: String? = null,
    val routeId: String? = null,
    val plannedNodeId: String? = null,
    val plannedRouteId: String? = null,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
