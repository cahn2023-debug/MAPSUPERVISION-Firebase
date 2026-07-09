package com.mapsupervision.photo.worker

import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.lifecycle.ProcessCameraProvider
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.StampRenderMode
import com.mapsupervision.domain.repository.StampDataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StampPipelineCoordinator @Inject constructor(
    private val repository: StampDataRepository
) {
    private var activeEffect: StampCameraEffect? = null
    private var activeProcessor: StampSurfaceProcessor? = null

    @Synchronized
    fun getOrCreateEffect(isVideoMode: Boolean): StampCameraEffect {
        val targets = if (isVideoMode) {
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE
        } else {
            CameraEffect.PREVIEW or CameraEffect.IMAGE_CAPTURE
        }
        
        val existing = activeEffect
        if (existing != null) {
            return existing
        }

        AppLogger.d("StampPipelineCoordinator: Creating new StampCameraEffect")
        val processor = StampSurfaceProcessor(repository)
        val effect = StampCameraEffect(processor, targets)
        activeProcessor = processor
        activeEffect = effect
        return effect
    }

    @Synchronized
    fun release() {
        AppLogger.d("StampPipelineCoordinator: Releasing active effect and processor")
        activeEffect?.release()
        activeEffect = null
        activeProcessor = null
    }

    fun isRealtimeSupported(
        cameraProvider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        extensionMode: Int,
        isVideoMode: Boolean
    ): Boolean {
        // If vendor extensions are active, we must prioritize them and fallback to legacy post-burn overlay
        if (extensionMode != ExtensionMode.NONE) {
            AppLogger.d("StampPipelineCoordinator: Extension active ($extensionMode), fallback to legacy post-burn to avoid clashes")
            return false
        }
        
        if (isVideoMode) {
            AppLogger.d("StampPipelineCoordinator: Video mode active, fallback to post-processing for reliability")
            return false
        }
        
        // Dynamic try-catch will handle actual binding limits, so we default to true here for standard modes
        return true
    }
}
