package com.mapsupervision.domain.model

data class WorkCategory(
    val id: String,
    val projectId: String,
    val name: String,
    val unit: String,
    val createdAtEpochMs: Long
)
