package com.mapsupervision.domain.model

data class ReportDraft(
    val id: String,
    val projectId: String,
    val title: String,
    val executiveSummary: String,
    val riskSection: String,
    val recommendedActions: List<String>,
    val createdAtEpochMs: Long
)
