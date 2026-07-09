package com.mapsupervision.domain.model

data class ImportSession(
    val id: String,
    val projectId: String,
    val sourceKind: String,
    val sourceFileName: String,
    val sourceFileType: String,
    val sourceFilePath: String,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val importedFileId: String? = null,
    val featureCount: Int = 0,
    val conflictCount: Int = 0,
    val warningCount: Int = 0,
    val message: String = ""
)

