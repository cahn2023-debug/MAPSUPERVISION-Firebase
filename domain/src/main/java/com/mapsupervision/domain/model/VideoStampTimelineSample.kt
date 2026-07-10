package com.mapsupervision.domain.model

data class VideoStampTimelineSample(
    val elapsedMs: Long,
    val stamp: CaptureStamp,
    val tileBitmap: Any? = null // Platform specific Bitmap (e.g. android.graphics.Bitmap)
)
