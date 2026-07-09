package com.mapsupervision.domain.model

data class AiActionLog(
    val id: String,
    val projectId: String,
    val rawInput: String,
    val actionType: String,
    val draftJson: String,
    val confidence: Int,
    val status: String, // e.g. "DRAFT_CREATED", "CONFIRMED", "REJECTED", "COMMITTED", "FAILED"
    val timestamp: Long
)
