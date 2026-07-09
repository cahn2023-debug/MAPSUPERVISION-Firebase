package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rag_document_embedding",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["projectId", "docType"]),
        Index(value = ["projectId", "sourceId"]),
        Index(value = ["projectId", "contentHash"])
    ]
)
data class RagDocumentEmbeddingEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val docType: String,
    val sourceId: String,
    val text: String,
    val contentHash: String,
    val embeddingBlob: ByteArray,
    val updatedAtEpochMs: Long,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)
