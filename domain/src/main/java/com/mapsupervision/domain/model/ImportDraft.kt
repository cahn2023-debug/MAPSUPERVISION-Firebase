package com.mapsupervision.domain.model

data class ImportDraft(
    val fileName: String,
    val fileType: String,
    val storedPath: String,
    val summary: String,
    val suggestedNodes: List<GisNode> = emptyList(),
    val suggestedRoutes: List<GisRoute> = emptyList(),
    val routeLengthMeters: Double = 0.0
)
