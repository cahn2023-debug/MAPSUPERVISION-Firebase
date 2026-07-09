package com.mapsupervision.domain.model

data class StampSnapshot(
    val timestampMs: Long,
    val locationKey: RoundedLocationKey?,
    val address: String,
    val bearingBucket: Int,
    val note: String,
    val mapScene: CaptureStampMapScene?,
    val tileCacheKey: RoundedLocationKey?
)
