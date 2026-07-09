package com.mapsupervision.domain.model

data class ImportVersion(
    val id: String,
    val projectId: String,
    val importSessionId: String,
    val versionNumber: Int,
    val sourceHash: String,
    val createdAtEpochMs: Long,
    val createdBy: String = "",
    val note: String = ""
)

