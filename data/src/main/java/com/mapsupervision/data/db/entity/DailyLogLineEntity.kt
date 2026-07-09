package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.DailyLogLine
import com.mapsupervision.domain.model.DailyLogLineType

@Entity(
    tableName = "daily_log_line",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DailyLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["dailyLogId"],
            onDelete = ForeignKey.CASCADE
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
        Index(value = ["projectId", "dailyLogId"]),
        Index("nodeId"),
        Index("routeId")
    ]
)
data class DailyLogLineEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val dailyLogId: String,
    val lineType: String,
    val workName: String,
    val categoryName: String,
    val quantity: Double,
    val unit: String,
    val linkedWorkPlanId: String? = null,
    val nodeId: String? = null,
    val routeId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs
) {
    fun toDomain(nodeCode: String?, routeCode: String?) = DailyLogLine(
        id = id,
        projectId = projectId,
        dailyLogId = dailyLogId,
        lineType = runCatching { DailyLogLineType.valueOf(lineType) }.getOrDefault(DailyLogLineType.EXTRA),
        workName = workName,
        categoryName = categoryName,
        quantity = quantity,
        unit = unit,
        nodeCode = nodeCode,
        routeCode = routeCode,
        linkedWorkPlanId = linkedWorkPlanId,
        nodeId = nodeId,
        routeId = routeId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    companion object {
        fun fromDomain(domain: DailyLogLine) = DailyLogLineEntity(
            id = domain.id,
            projectId = domain.projectId,
            dailyLogId = domain.dailyLogId,
            lineType = domain.lineType.name,
            workName = domain.workName,
            categoryName = domain.categoryName,
            quantity = domain.quantity,
            unit = domain.unit,
            linkedWorkPlanId = domain.linkedWorkPlanId,
            nodeId = domain.nodeId,
            routeId = domain.routeId,
            createdAtEpochMs = domain.createdAtEpochMs,
            updatedAtEpochMs = domain.updatedAtEpochMs
        )
    }
}
