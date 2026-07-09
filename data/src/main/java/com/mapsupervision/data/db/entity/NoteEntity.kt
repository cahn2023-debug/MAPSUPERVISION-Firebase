package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.Note

@Entity(
    tableName = "note",
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
        Index("objectNodeId"),
        Index("objectRouteId")
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val content: String,
    val createdAtEpochMs: Long,
    val objectNodeId: String? = null,
    val objectRouteId: String? = null,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
) {
    fun toDomain() = Note(
        id = id,
        projectId = projectId,
        objectCode = "",
        objectNodeId = objectNodeId,
        objectRouteId = objectRouteId,
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )

    companion object {
        fun fromDomain(domain: Note) = NoteEntity(
            id = domain.id,
            projectId = domain.projectId,
            objectNodeId = domain.objectNodeId,
            objectRouteId = domain.objectRouteId,
            content = domain.content,
            createdAtEpochMs = domain.createdAtEpochMs,
            updatedAtEpochMs = domain.updatedAtEpochMs,
            isDeleted = domain.isDeleted,
            deletedAtEpochMs = domain.deletedAtEpochMs
        )
    }
}
