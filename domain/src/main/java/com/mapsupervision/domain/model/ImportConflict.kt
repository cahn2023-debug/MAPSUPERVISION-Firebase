package com.mapsupervision.domain.model

data class ImportConflict(
    val id: String,
    val projectId: String,
    val importSessionId: String,
    val featureBusinessCode: String,
    val conflictType: String,
    val severity: String,
    val details: String,
    val resolvedBy: String? = null,
    val resolvedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

