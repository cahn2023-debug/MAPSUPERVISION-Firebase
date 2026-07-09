package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "report_draft",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "createdAtEpochMs"])
    ]
)
data class ReportDraftEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val executiveSummary: String,
    val riskSection: String,
    val recommendedActionsCsv: String,
    val createdAtEpochMs: Long
)
