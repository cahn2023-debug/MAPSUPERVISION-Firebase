package com.mapsupervision.domain.model

data class CaptureStampMapNode(
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val label: String = "",
    val highlighted: Boolean = false,
    val colorHex: String? = null
)

data class CaptureStampMapRoute(
    val code: String,
    val points: List<Pair<Double, Double>>,
    val highlighted: Boolean = false
)

data class CaptureStampMapScene(
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val cameraLatitude: Double? = null,
    val cameraLongitude: Double? = null,
    val bearingDeg: Float = 0f,
    val nodes: List<CaptureStampMapNode> = emptyList(),
    val routes: List<CaptureStampMapRoute> = emptyList()
)
