package com.mapsupervision.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.RoundedLocationKey
import com.mapsupervision.domain.model.StampRenderMode
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.photo.worker.calculateAspectCropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class CameraOverlayStampOptions(
    val nodes: List<GisNode>,
    val routes: List<GisRoute>,
    val showNumberLabels: Boolean,
    val colorByContractor: Boolean,
    val contractorColors: Map<String, String>,
    val labelField: GisLabelField
)

internal class CameraOverlayState(
    private val context: Context,
    val stampPipelineCoordinator: com.mapsupervision.photo.worker.StampPipelineCoordinator,
    val stampDataRepository: com.mapsupervision.domain.repository.StampDataRepository
) {
    var stampRenderMode by mutableStateOf(StampRenderMode.FALLBACK_POSTPROCESS)
    var hasCameraPermission by mutableStateOf(
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
    var hasAudioPermission by mutableStateOf(
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )

    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val preview = Preview.Builder().build()
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
    val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.fromOrderedList(
                listOf(Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
            )
        )
        .build()
    val videoCapture = VideoCapture.withOutput(recorder)
    val photoCaptureSession = PhotoCaptureSession()
    val addressCache = mutableMapOf<RoundedLocationKey, String>()

    var cameraProvider by mutableStateOf<ProcessCameraProvider?>(null)
    var extensionsManager by mutableStateOf<ExtensionsManager?>(null)
    var boundCamera by mutableStateOf<Camera?>(null)
    var hasFrontCamera by mutableStateOf(false)
    var flashAvailable by mutableStateOf(false)

    var targetRotation by mutableStateOf(Surface.ROTATION_0)
    var activeRecording by mutableStateOf<Recording?>(null)
    var isRecording by mutableStateOf(false)
    var isVideoMode by mutableStateOf(false)
    var isProcessingVideoStamp by mutableStateOf(false)
    var lensFacing by mutableStateOf(CaptureLensFacing.BACK)
    var flashMode by mutableStateOf(CameraFlashMode.OFF)
    var showSettingsSheet by mutableStateOf(false)
    var showFlashMenu by mutableStateOf(false)
    var activeExtensionMode by mutableStateOf(ExtensionMode.NONE)
    var previousExtensionMode by mutableStateOf(ExtensionMode.NONE)
    var zoomRatio by mutableStateOf(1f)
    var minZoomRatio by mutableStateOf(1f)
    var maxZoomRatio by mutableStateOf(1f)

    var stampEnabled by mutableStateOf(true)
    var noteText by mutableStateOf("")
    var bearing by mutableStateOf(0f)
    var liveLocation by mutableStateOf<PhotoLocationSnapshot?>(null)
    var liveAddress by mutableStateOf("")
    var selectedAspectRatio by mutableStateOf(CameraAspectRatio.RATIO_4_3)
    var showZoomIndicator by mutableStateOf(false)

    var currentTileBitmap by mutableStateOf<Bitmap?>(null)
    var currentTileKey by mutableStateOf<RoundedLocationKey?>(null)
    var cachedTileBitmap by mutableStateOf<Bitmap?>(null)
    var cachedTileKey by mutableStateOf<RoundedLocationKey?>(null)
    var previewSurfaceSize by mutableStateOf(IntSize.Zero)
    var previewOverlayBitmap by mutableStateOf<Bitmap?>(null)

    val controlsEnabled: Boolean
        get() = !isRecording && !isProcessingVideoStamp && !photoCaptureSession.isCapturingPhoto

    val previewViewport: com.mapsupervision.photo.worker.AspectCropRect?
        get() = if (previewSurfaceSize.width <= 0 || previewSurfaceSize.height <= 0) {
            null
        } else {
            calculateAspectCropRect(previewSurfaceSize.width, previewSurfaceSize.height, selectedAspectRatio)
        }

    val selectedLensSelector: CameraSelector
        get() = if (lensFacing == CaptureLensFacing.BACK) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

    fun resolveCameraSelector(): CameraSelector {
        val selectedLensSelector = selectedLensSelector
        val extensionsManager = extensionsManager
        return if (isVideoMode || extensionsManager == null || activeExtensionMode == ExtensionMode.NONE) {
            selectedLensSelector
        } else if (extensionsManager.isExtensionAvailable(selectedLensSelector, activeExtensionMode)) {
            extensionsManager.getExtensionEnabledCameraSelector(selectedLensSelector, activeExtensionMode)
        } else {
            selectedLensSelector
        }
    }

    fun isExtensionSupported(mode: Int): Boolean {
        val manager = extensionsManager
        return mode == ExtensionMode.NONE || manager?.isExtensionAvailable(selectedLensSelector, mode) == true
    }

    fun recycleBitmaps() {
        previewOverlayBitmap?.recycle()
        currentTileBitmap?.recycle()
        cachedTileBitmap?.recycle()
    }

    fun clearTileState() {
        val oldTile = currentTileBitmap
        currentTileBitmap = null
        currentTileKey = null
        oldTile?.recycle()
        cachedTileBitmap?.recycle()
        cachedTileBitmap = null
        cachedTileKey = null
        addressCache.clear()
    }
}

@Composable
internal fun rememberCameraOverlayState(
    context: Context,
    stampPipelineCoordinator: com.mapsupervision.photo.worker.StampPipelineCoordinator,
    stampDataRepository: com.mapsupervision.domain.repository.StampDataRepository
): CameraOverlayState {
    return remember(context, stampPipelineCoordinator, stampDataRepository) { 
        CameraOverlayState(context, stampPipelineCoordinator, stampDataRepository) 
    }
}

@Composable
internal fun BindCameraOverlayState(
    state: CameraOverlayState,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    locationProvider: IPhotoLocationProvider,
    nodeCode: String,
    stampOptions: CameraOverlayStampOptions
) {
    val previewRenderKey = remember(
        state.stampEnabled,
        state.isVideoMode,
        state.selectedAspectRatio,
        state.previewViewport,
        state.liveLocation,
        state.liveAddress,
        state.noteText,
        state.currentTileKey,
        state.bearing
    ) {
        buildPreviewStampRenderKey(
            stampEnabled = state.stampEnabled,
            isVideoMode = state.isVideoMode,
            aspectRatio = state.selectedAspectRatio,
            viewport = state.previewViewport,
            location = state.liveLocation,
            address = state.liveAddress,
            note = state.noteText,
            tileKey = state.currentTileKey,
            bearing = state.bearing
        )
    }
    val cameraSelector = remember(
        state.activeExtensionMode,
        state.extensionsManager,
        state.isVideoMode,
        state.lensFacing
    ) {
        state.resolveCameraSelector()
    }

    LaunchedEffect(state.zoomRatio) {
        state.showZoomIndicator = true
        delay(1000)
        state.showZoomIndicator = false
    }

    LaunchedEffect(previewRenderKey, stampOptions, state.stampRenderMode) {
        val viewport = state.previewViewport ?: return@LaunchedEffect
        if (!state.stampEnabled) {
            state.previewOverlayBitmap?.recycle()
            state.previewOverlayBitmap = null
            return@LaunchedEffect
        }
        val previewStamp = buildCaptureStamp(
            timestampMs = System.currentTimeMillis(),
            location = state.liveLocation,
            address = state.liveAddress,
            note = state.noteText,
            bearingDeg = state.bearing,
            nodes = stampOptions.nodes,
            routes = stampOptions.routes
        )
        val tileSnapshot = snapshotBitmap(state.currentTileBitmap)
        val overlay = withContext(Dispatchers.Default) {
            buildPreviewStampOverlayBitmap(
                frameWidthPx = viewport.width,
                frameHeightPx = viewport.height,
                stamp = previewStamp,
                tileBitmap = tileSnapshot
            )
        }
        tileSnapshot?.recycle()
        val oldOverlay = state.previewOverlayBitmap
        state.previewOverlayBitmap = overlay
        oldOverlay?.recycle()
    }

    LaunchedEffect(
        state.liveLocation,
        state.liveAddress,
        state.bearing,
        state.noteText,
        stampOptions
    ) {
        val mapScene = if (stampOptions.nodes.isNotEmpty() || stampOptions.routes.isNotEmpty()) {
            buildCaptureStamp(
                timestampMs = System.currentTimeMillis(),
                location = state.liveLocation,
                address = state.liveAddress,
                note = state.noteText,
                bearingDeg = state.bearing,
                nodes = stampOptions.nodes,
                routes = stampOptions.routes
            ).mapScene
        } else null

        state.stampDataRepository.updateInput(
            latitude = state.liveLocation?.latitude,
            longitude = state.liveLocation?.longitude,
            bearing = state.bearing,
            note = state.noteText,
            address = state.liveAddress,
            mapScene = mapScene
        )
    }


    DisposableEffect(Unit) {
        onDispose {
            state.recycleBitmaps()
        }
    }

    LaunchedEffect(state.stampEnabled, locationProvider, context) {
        if (!state.stampEnabled) {
            state.clearTileState()
            return@LaunchedEffect
        }

        while (true) {
            runCatching {
                val loc = locationProvider.lastKnownLocation()
                state.liveLocation = loc
                val lat = loc.latitude
                val lng = loc.longitude
                val locationKey = roundedLocationKey(lat, lng)
                if (locationKey == null) {
                    state.liveAddress = ""
                    state.currentTileKey = null
                    state.currentTileBitmap?.recycle()
                    state.currentTileBitmap = null
                    return@runCatching
                }
                val safeLat = lat ?: return@runCatching
                val safeLng = lng ?: return@runCatching

                state.liveAddress = state.addressCache[locationKey] ?: withContext(Dispatchers.IO) {
                    reverseGeocode(context, safeLat, safeLng)
                }.also { state.addressCache[locationKey] = it }

                val nextTileBitmap = when {
                    state.currentTileKey == locationKey && state.currentTileBitmap != null -> state.currentTileBitmap
                    state.cachedTileKey == locationKey && state.cachedTileBitmap != null -> snapshotBitmap(state.cachedTileBitmap)
                    else -> {
                        val fetchedTile = withContext(Dispatchers.IO) {
                            com.mapsupervision.photo.worker.PhotoStampRenderer.fetchOsmTile(
                                safeLat,
                                safeLng,
                                zoom = com.mapsupervision.photo.worker.PhotoStampRenderer.MINIMAP_MAX_ZOOM
                            )
                        }
                        state.cachedTileBitmap?.takeIf { state.cachedTileKey != locationKey }?.recycle()
                        state.cachedTileBitmap = snapshotBitmap(fetchedTile)
                        state.cachedTileKey = locationKey
                        fetchedTile
                    }
                }

                if (nextTileBitmap !== state.currentTileBitmap) {
                    state.currentTileBitmap?.recycle()
                    state.currentTileBitmap = nextTileBitmap
                }
                state.currentTileKey = locationKey
            }.onFailure {
                AppLogger.e(it, "camera.overlay.location.poll.failed")
            }
            delay(LOCATION_POLL_INTERVAL_MS)
        }
    }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val rotationMatrix = FloatArray(9)
        val remappedRotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val rotation = state.targetRotation
                        val remapped = when (rotation) {
                            Surface.ROTATION_90 -> {
                                SensorManager.remapCoordinateSystem(
                                    rotationMatrix,
                                    SensorManager.AXIS_Y,
                                    SensorManager.AXIS_MINUS_X,
                                    remappedRotationMatrix
                                )
                            }
                            Surface.ROTATION_180 -> {
                                SensorManager.remapCoordinateSystem(
                                    rotationMatrix,
                                    SensorManager.AXIS_MINUS_X,
                                    SensorManager.AXIS_MINUS_Y,
                                    remappedRotationMatrix
                                )
                            }
                            Surface.ROTATION_270 -> {
                                SensorManager.remapCoordinateSystem(
                                    rotationMatrix,
                                    SensorManager.AXIS_MINUS_Y,
                                    SensorManager.AXIS_X,
                                    remappedRotationMatrix
                                )
                            }
                            else -> false
                        }
                        val finalMatrix = if (remapped) remappedRotationMatrix else rotationMatrix
                        SensorManager.getOrientation(finalMatrix, orientationAngles)
                        state.updateBearing(
                            Math.toDegrees(orientationAngles[0].toDouble()).toFloat().let {
                                if (it < 0f) it + 360f else it
                            }
                        )
                    }

                    Sensor.TYPE_ORIENTATION -> {
                        val rawBearing = event.values[0]
                        val rotation = state.targetRotation
                        val adjustedBearing = when (rotation) {
                            Surface.ROTATION_90 -> (rawBearing + 90f) % 360f
                            Surface.ROTATION_180 -> (rawBearing + 180f) % 360f
                            Surface.ROTATION_270 -> (rawBearing + 270f) % 360f
                            else -> rawBearing
                        }
                        state.updateBearing(adjustedBearing)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        rotationSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    DisposableEffect(state.cameraProviderFuture) {
        val executor = ContextCompat.getMainExecutor(context)
        state.cameraProviderFuture.addListener(
            { state.cameraProvider = runCatching { state.cameraProviderFuture.get() }.getOrNull() },
            executor
        )
        onDispose {
            runCatching { state.cameraProvider?.unbindAll() }
        }
    }

    LaunchedEffect(state.cameraProvider) {
        val provider = state.cameraProvider ?: return@LaunchedEffect
        val managerFuture = ExtensionsManager.getInstanceAsync(context, provider)
        managerFuture.addListener(
            {
                state.extensionsManager = runCatching { managerFuture.get() }.getOrNull()
                state.hasFrontCamera = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(state.lensFacing) {
        state.zoomRatio = 1f
    }

    LaunchedEffect(state.isVideoMode) {
        if (state.isVideoMode) {
            state.previousExtensionMode = state.activeExtensionMode
            state.activeExtensionMode = ExtensionMode.NONE
        } else if (state.previousExtensionMode != ExtensionMode.NONE) {
            state.activeExtensionMode = state.previousExtensionMode
        }
    }

    DisposableEffect(context, state.previewView, state.preview, state.imageCapture, state.videoCapture) {
        state.targetRotation = state.previewView.display?.rotation ?: Surface.ROTATION_0
        state.preview.targetRotation = state.targetRotation
        state.imageCapture.targetRotation = state.targetRotation
        state.videoCapture.targetRotation = state.targetRotation

        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val updatedRotation = orientationToSurfaceRotation(orientation)
                if (updatedRotation != state.targetRotation) {
                    state.targetRotation = updatedRotation
                    state.preview.targetRotation = updatedRotation
                    state.imageCapture.targetRotation = updatedRotation
                    state.videoCapture.targetRotation = updatedRotation
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose { orientationListener.disable() }
    }

    LaunchedEffect(state.cameraProvider, cameraSelector, state.isVideoMode, state.zoomRatio, state.stampEnabled, state.activeExtensionMode) {
        val provider = state.cameraProvider ?: return@LaunchedEffect
        runCatching {
            provider.unbindAll()

            val isRealtimeSupported = state.stampEnabled && state.stampPipelineCoordinator.isRealtimeSupported(
                cameraProvider = provider,
                cameraSelector = cameraSelector,
                extensionMode = state.activeExtensionMode,
                isVideoMode = state.isVideoMode
            )

            if (isRealtimeSupported) {
                try {
                    val effect = state.stampPipelineCoordinator.getOrCreateEffect(state.isVideoMode)
                    val useCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(state.preview)
                        .let { builder ->
                            if (state.isVideoMode) builder.addUseCase(state.videoCapture) else builder.addUseCase(state.imageCapture)
                        }
                        .addEffect(effect)
                        .build()

                    state.preview.surfaceProvider = state.previewView.surfaceProvider
                    val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
                    state.boundCamera = camera
                    state.stampRenderMode = StampRenderMode.REALTIME_EFFECT

                    setupCameraControls(state, camera)
                    return@runCatching
                } catch (e: Exception) {
                    AppLogger.e(e, "camera.overlay.bind.realtime.failed - falling back to legacy postprocess")
                }
            }

            state.preview.surfaceProvider = state.previewView.surfaceProvider
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(state.preview)
                .let { builder ->
                    if (state.isVideoMode) builder.addUseCase(state.videoCapture) else builder.addUseCase(state.imageCapture)
                }
                .build()
            val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
            state.boundCamera = camera
            state.stampRenderMode = StampRenderMode.FALLBACK_POSTPROCESS

            setupCameraControls(state, camera)
        }.onFailure {
            state.boundCamera = null
            AppLogger.e(it, "camera.overlay.bind.failed")
        }
    }

    LaunchedEffect(state.flashMode) {
        state.imageCapture.flashMode = resolveImageCaptureFlashMode(state.flashMode)
    }

    LaunchedEffect(state.boundCamera, state.isVideoMode, state.isRecording, state.flashMode, state.flashAvailable) {
        val camera = state.boundCamera ?: return@LaunchedEffect
        val enableTorch = state.isVideoMode &&
            state.isRecording &&
            state.flashAvailable &&
            resolveVideoTorchEnabled(state.flashMode)
        runCatching { camera.cameraControl.enableTorch(enableTorch) }
            .onFailure { AppLogger.e(it, "camera.overlay.torch.failed enabled=$enableTorch") }
    }
}

private fun CameraOverlayState.updateBearing(newBearing: Float) {
    val diff = kotlin.math.abs(newBearing - bearing) % 360f
    val shortestDiff = if (diff > 180f) 360f - diff else diff
    if (shortestDiff >= 0.5f) {
        bearing = newBearing
    }
}

private const val LOCATION_POLL_INTERVAL_MS = 8_000L
private const val LOCATION_RENDER_DECIMALS = 4


private fun orientationToSurfaceRotation(orientation: Int): Int {
    return when (orientation) {
        in 45..134 -> Surface.ROTATION_270
        in 135..224 -> Surface.ROTATION_180
        in 225..314 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }
}

private fun reverseGeocode(context: Context, lat: Double, lng: Double): String {
    if (android.location.Geocoder.isPresent()) {
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val street = address.thoroughfare ?: address.subThoroughfare ?: ""
                val ward = address.subLocality ?: ""
                val district = address.locality ?: address.subAdminArea ?: ""
                val city = address.adminArea ?: ""
                val parts = listOf(street, ward, district, city).filter { it.isNotBlank() }
                if (parts.isNotEmpty()) {
                    return parts.joinToString(", ").take(60)
                }
                val featureName = address.featureName
                if (!featureName.isNullOrBlank()) return featureName.take(60)
            }
        } catch (e: Exception) {
            AppLogger.e(e, "camera.overlay.reverseGeocode.nativeFailed")
        }
    }

    return try {
        val url = java.net.URL(
            "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=jsonv2"
        )
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MapSupervision/1.0")
            connectTimeout = 1200
            readTimeout = 1200
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            val key = "\"display_name\":"
            val start = body.indexOf(key)
            if (start >= 0) {
                val valueStart = body.indexOf('"', start + key.length) + 1
                val valueEnd = body.indexOf('"', valueStart)
                body.substring(valueStart, valueEnd).take(60)
            } else {
                ""
            }
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}

private fun setupCameraControls(state: CameraOverlayState, camera: Camera) {
    val zoomState = camera.cameraInfo.zoomState.value
    val resolvedMinZoom = zoomState?.minZoomRatio ?: 1f
    val resolvedMaxZoom = zoomState?.maxZoomRatio ?: 1f
    state.minZoomRatio = resolvedMinZoom
    state.maxZoomRatio = resolvedMaxZoom
    val clampedZoom = clampZoomRatio(state.zoomRatio, resolvedMinZoom, resolvedMaxZoom)
    state.zoomRatio = clampedZoom
    state.flashAvailable = camera.cameraInfo.hasFlashUnit()
    if (!state.flashAvailable && state.flashMode != CameraFlashMode.OFF) {
        state.flashMode = CameraFlashMode.OFF
    }
    state.imageCapture.flashMode = resolveImageCaptureFlashMode(state.flashMode)
    camera.cameraControl.setZoomRatio(clampedZoom)
}
