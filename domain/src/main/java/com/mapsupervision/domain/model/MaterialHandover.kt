package com.mapsupervision.domain.model

/**
 * Đại diện cho một giao dịch giao/nhận vật tư.
 */
data class MaterialHandover(
    val id: String,
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
)
