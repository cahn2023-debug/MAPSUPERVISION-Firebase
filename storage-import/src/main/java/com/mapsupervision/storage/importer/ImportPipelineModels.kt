package com.mapsupervision.storage.importer

import com.mapsupervision.domain.model.Feature
import com.mapsupervision.domain.model.ImportAudit
import com.mapsupervision.domain.model.ImportConflict
import com.mapsupervision.domain.model.ImportSession
import com.mapsupervision.domain.model.ImportVersion

data class ImportPipelineContext(
    val session: ImportSession,
    val version: ImportVersion? = null,
    val features: List<Feature> = emptyList(),
    val conflicts: List<ImportConflict> = emptyList(),
    val audit: ImportAudit? = null
)

data class ImportCommitResult(
    val session: ImportSession,
    val version: ImportVersion? = null,
    val featureCount: Int = 0,
    val conflictCount: Int = 0,
    val warningCount: Int = 0,
    val messages: List<String> = emptyList()
)

