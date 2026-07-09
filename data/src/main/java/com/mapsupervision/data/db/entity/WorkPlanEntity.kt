package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.WorkPlan

@Entity(
    tableName = "work_plan",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
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
        Index(value = ["projectId", "plannedDateEpochDay"]),
        Index("nodeId"),
        Index("routeId")
    ]
)
data class WorkPlanEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val plannedDateEpochDay: Long,
    val nodeCode: String?,
    val routeCode: String?,
    val taskId: String?,
    val sourceRawInput: String,
    val createdAtEpochMs: Long,
    val quantity: Double,
    val unit: String,
    val batchGroupId: String,
    val nodeId: String? = null,
    val routeId: String? = null
) {
    fun toDomain() = WorkPlan(
        id = id,
        projectId = projectId,
        title = title,
        description = description,
        plannedDateEpochDay = plannedDateEpochDay,
        nodeCode = nodeCode,
        routeCode = routeCode,
        nodeId = nodeId,
        routeId = routeId,
        taskId = taskId,
        sourceRawInput = sourceRawInput,
        createdAtEpochMs = createdAtEpochMs,
        quantity = quantity,
        unit = unit,
        batchGroupId = batchGroupId
    )

    companion object {
        fun fromDomain(domain: WorkPlan) = WorkPlanEntity(
            id = domain.id,
            projectId = domain.projectId,
            title = domain.title,
            description = domain.description,
            plannedDateEpochDay = domain.plannedDateEpochDay,
            nodeCode = domain.nodeCode,
            routeCode = domain.routeCode,
            nodeId = domain.nodeId,
            routeId = domain.routeId,
            taskId = domain.taskId,
            sourceRawInput = domain.sourceRawInput,
            createdAtEpochMs = domain.createdAtEpochMs,
            quantity = domain.quantity,
            unit = domain.unit,
            batchGroupId = domain.batchGroupId
        )
    }
}
