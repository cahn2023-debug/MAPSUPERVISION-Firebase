package com.mapsupervision.domain.model

data class ChatHistoryMessage(
    val id: String,
    val projectId: String,
    val role: String,
    val text: String,
    val createdAtEpochMs: Long
)
