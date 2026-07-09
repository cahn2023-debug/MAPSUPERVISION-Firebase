package com.mapsupervision.photo.worker

import androidx.camera.core.CameraEffect
import com.mapsupervision.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

class StampCameraEffect(
    val processor: StampSurfaceProcessor,
    targets: Int = PREVIEW or VIDEO_CAPTURE
) : CameraEffect(
    targets,
    Dispatchers.Main.asExecutor(),
    processor,
    { throwable -> AppLogger.e(throwable, "StampCameraEffect error") }
) {
    init {
        AppLogger.d("StampCameraEffect: initialized for Preview + VideoCapture")
    }

    fun release() {
        processor.release()
    }
}
