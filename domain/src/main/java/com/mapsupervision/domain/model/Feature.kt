package com.mapsupervision.domain.model

data class Feature(
    val id: String,
    val businessCode: String,
    val geometry: Geometry,
    val properties: Map<String, String> = emptyMap(),
    val quantities: Map<String, Double> = emptyMap(),
    val contractor: String = "",
    val status: String = "",
    val attachments: List<String> = emptyList(),
    val displayCode: String = businessCode,
    val sourceFileId: String? = null,
    val sourceVersionId: String? = null
)

