package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "work_categories",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index(value = ["projectId", "createdAtEpochMs"])
    ]
)
data class WorkCategoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val unit: String,
    val createdAtEpochMs: Long
)
