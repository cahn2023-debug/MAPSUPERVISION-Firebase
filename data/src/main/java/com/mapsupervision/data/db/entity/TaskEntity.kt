package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus

@Entity(
    tableName = "task",
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
            childColumns = ["objectNodeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = GisRouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectRouteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["projectId", "objectNodeId", "createdAtEpochMs"]),
        Index(value = ["projectId", "objectRouteId", "createdAtEpochMs"]),
        Index("objectNodeId"),
        Index("objectRouteId")
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val objectNodeId: String? = null,
    val objectRouteId: String? = null,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
) {
    fun toDomain() = Task(
        id = id,
        projectId = projectId,
        objectCode = "",
        objectNodeId = objectNodeId,
        objectRouteId = objectRouteId,
        title = title,
        description = description,
        status = TaskStatus.valueOf(status),
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    companion object {
        fun fromDomain(domain: Task) = TaskEntity(
            id = domain.id,
            projectId = domain.projectId,
            objectNodeId = domain.objectNodeId,
            objectRouteId = domain.objectRouteId,
            title = domain.title,
            description = domain.description,
            status = domain.status.name,
            createdAtEpochMs = domain.createdAtEpochMs,
            completedAtEpochMs = domain.completedAtEpochMs,
            updatedAtEpochMs = domain.updatedAtEpochMs,
            isDeleted = domain.isDeleted,
            deletedAtEpochMs = domain.deletedAtEpochMs
        )
    }
}
