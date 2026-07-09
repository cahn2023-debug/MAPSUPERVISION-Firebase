package com.mapsupervision.domain.model

/**
 * Định nghĩa một định mức vật tư cho một công việc cụ thể trong dự án.
 */
data class MaterialDeclaration(
    val id: String,
    val projectId: String,
    val workName: String,
    val materialName: String,
    val ratio: Float,
    val unit: String,
    val createdAtEpochMs: Long,
    val batchId: String? = null,
    val workCategoryId: String? = null
)
