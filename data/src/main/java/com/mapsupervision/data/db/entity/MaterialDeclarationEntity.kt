package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.MaterialDeclaration

@Entity(
    tableName = "material_declaration",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["workCategoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["projectId", "workName"]),
        Index(value = ["projectId", "materialName"]),
        Index(value = ["workCategoryId"])
    ]
)
data class MaterialDeclarationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val workName: String,
    val materialName: String,
    val ratio: Float,
    val unit: String,
    val createdAtEpochMs: Long,
    val batchId: String? = null,
    val workCategoryId: String? = null
) {
    fun toDomain() = MaterialDeclaration(
        id = id,
        projectId = projectId,
        batchId = batchId,
        workCategoryId = workCategoryId,
        workName = workName,
        materialName = materialName,
        ratio = ratio,
        unit = unit,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(domain: MaterialDeclaration) = MaterialDeclarationEntity(
            id = domain.id,
            projectId = domain.projectId,
            batchId = domain.batchId,
            workCategoryId = domain.workCategoryId,
            workName = domain.workName,
            materialName = domain.materialName,
            ratio = domain.ratio,
            unit = domain.unit,
            createdAtEpochMs = domain.createdAtEpochMs
        )
    }
}
