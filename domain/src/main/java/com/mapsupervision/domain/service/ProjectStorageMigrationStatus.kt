package com.mapsupervision.domain.service

data class ProjectStorageMigrationStatus(
    val projectId: String,
    val migrated: Boolean,
    val verified: Boolean,
    val verificationMessage: String,
    val projectDbPath: String = "",
    val sharedRowCounts: Map<String, Int> = emptyMap(),
    val scopedRowCounts: Map<String, Int> = emptyMap()
)
