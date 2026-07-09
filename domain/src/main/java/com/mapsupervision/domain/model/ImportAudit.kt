package com.mapsupervision.domain.model

data class ImportAudit(
    val id: String,
    val projectId: String,
    val importSessionId: String,
    val action: String,
    val actor: String,
    val payloadJson: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

