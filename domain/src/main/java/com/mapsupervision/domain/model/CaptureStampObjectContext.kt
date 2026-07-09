package com.mapsupervision.domain.model

data class CaptureStampObjectContext(
    val objectType: CaptureObjectType,
    val objectCode: String,
    val title: String,
    val details: List<String> = emptyList()
)

