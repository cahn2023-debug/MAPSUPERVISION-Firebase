package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.MaterialHandover

@Entity(
    tableName = "material_handover",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaterialDeclarationEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialDeclarationId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = WorkCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["workCategoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = GisNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("projectId"),
        Index("nodeCode"),
        Index("nodeId"),
        Index(value = ["projectId", "nodeId", "handoverDateEpochDay"]),
        Index("materialDeclarationId"),
        Index("workCategoryId")
    ]
)
data class MaterialHandoverEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val nodeCode: String,
    val workName: String,
    val materialName: String,
    val contractor: String,
    val quantity: Float,
    val unit: String,
    val handoverDateEpochDay: Long,
    val note: String,
    val createdAtEpochMs: Long,
    val nodeId: String? = null,
    val materialDeclarationId: String? = null,
    val workCategoryId: String? = null,
    val receiver: String = ""
) {
    fun toDomain() = MaterialHandover(
        id = id,
        projectId = projectId,
        nodeCode = nodeCode,
        nodeId = nodeId,
        workName = workName,
        materialName = materialName,
        contractor = contractor,
        quantity = quantity,
        unit = unit,
        handoverDateEpochDay = handoverDateEpochDay,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
        materialDeclarationId = materialDeclarationId,
        workCategoryId = workCategoryId,
        receiver = receiver
    )

    companion object {
        fun fromDomain(handover: MaterialHandover) = MaterialHandoverEntity(
            id = handover.id,
            projectId = handover.projectId,
            nodeCode = handover.nodeCode,
            nodeId = handover.nodeId,
            workName = handover.workName,
            materialName = handover.materialName,
            contractor = handover.contractor,
            quantity = handover.quantity,
            unit = handover.unit,
            handoverDateEpochDay = handover.handoverDateEpochDay,
            note = handover.note,
            createdAtEpochMs = handover.createdAtEpochMs,
            materialDeclarationId = handover.materialDeclarationId,
            workCategoryId = handover.workCategoryId,
            receiver = handover.receiver
        )
    }
}
