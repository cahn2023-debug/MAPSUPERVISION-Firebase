package com.mapsupervision.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import com.mapsupervision.photo.worker.PhotoStampRenderer
import com.mapsupervision.photo.worker.calculateAspectCropRect
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ProjectStorageRef

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.RoundedLocationKey
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoPipelineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class CaptureLensFacing { BACK, FRONT }

internal enum class CameraFlashMode { AUTO, OFF, ON }

internal fun resolveImageCaptureFlashMode(flashMode: CameraFlashMode): Int = when (flashMode) {
    CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
}

internal fun resolveVideoTorchEnabled(flashMode: CameraFlashMode): Boolean = flashMode != CameraFlashMode.OFF

internal fun clampZoomRatio(requestedZoomRatio: Float, minZoomRatio: Float, maxZoomRatio: Float): Float {
    val normalizedMin = minZoomRatio.coerceAtLeast(1f)
    val normalizedMax = maxZoomRatio.coerceAtLeast(normalizedMin)
    return requestedZoomRatio.coerceIn(normalizedMin, normalizedMax)
}

internal fun buildCaptureStamp(
    timestampMs: Long,
    location: PhotoLocationSnapshot?,
    address: String = "",
    note: String = "",
    bearingDeg: Float,
    nodes: List<GisNode> = emptyList(),
    routes: List<GisRoute> = emptyList()
): CaptureStamp {
    val mapScene = if (nodes.isNotEmpty() || routes.isNotEmpty()) {
        com.mapsupervision.domain.model.CaptureStampMapScene(
            centerLatitude = location?.latitude,
            centerLongitude = location?.longitude,
            cameraLatitude = location?.latitude,
            cameraLongitude = location?.longitude,
            bearingDeg = bearingDeg,
            nodes = nodes.map {
                com.mapsupervision.domain.model.CaptureStampMapNode(
                    code = it.code,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    label = it.code,
                    highlighted = false
                )
            },
            routes = routes.map {
                com.mapsupervision.domain.model.CaptureStampMapRoute(
                    code = it.code,
                    points = it.points,
                    highlighted = false
                )
            }
        )
    } else null

    return CaptureStamp(
        timestampMs = timestampMs,
        latitude = location?.latitude,
        longitude = location?.longitude,
        address = address.trim(),
        note = note.trim(),
        bearingDeg = bearingDeg,
        mapScene = mapScene
    )
}

internal class PhotoCaptureSession {
    var isCapturingPhoto by mutableStateOf(false)
        private set

    fun tryBeginCapture(): Boolean {
        if (isCapturingPhoto) return false
        isCapturingPhoto = true
        return true
    }

    fun finishCapture() {
        isCapturingPhoto = false
    }
}

internal suspend fun postProcessRecordedVideo(
    videoFile: java.io.File,
    stampEnabled: Boolean,
    stampAtRecordStart: CaptureStamp?,
    tileBitmap: Bitmap?,
    photoPipelineService: IPhotoPipelineService,
    setProcessingVideoStamp: (Boolean) -> Unit,
    onSavePhoto: suspend (java.io.File) -> Boolean,
    onPhotoCaptured: () -> Unit
): Boolean {
    return try {
        if (stampEnabled) {
            val stamp = requireNotNull(stampAtRecordStart) {
                "Missing capture stamp for video export"
            }
            setProcessingVideoStamp(true)
            photoPipelineService.exportVideoStamp(videoFile, stamp, tileBitmap)
        }
        val saved = onSavePhoto(videoFile)
        if (saved) onPhotoCaptured()
        saved
    } finally {
        setProcessingVideoStamp(false)
    }
}

internal fun buildPreviewStampOverlayBitmap(
    frameWidthPx: Int,
    frameHeightPx: Int,
    stamp: CaptureStamp,
    tileBitmap: Bitmap?
): Bitmap {
    return PhotoStampRenderer.createStampOverlayBitmap(
        frameWidthPx = frameWidthPx,
        frameHeightPx = frameHeightPx,
        stamp = stamp,
        tileBitmap = tileBitmap
    )
}

internal fun snapshotBitmap(bitmap: Bitmap?): Bitmap? {
    return bitmap?.copy(Bitmap.Config.ARGB_8888, false)
}

internal data class PreviewStampRenderKey(
    val stampEnabled: Boolean,
    val isVideoMode: Boolean,
    val aspectRatio: CameraAspectRatio,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val locationKey: RoundedLocationKey?,
    val address: String,
    val note: String,
    val tileKey: RoundedLocationKey?,
    val bearing: Float
)

private const val LOCATION_POLL_INTERVAL_MS = 8_000L
private const val LOCATION_RENDER_DECIMALS = 4

internal fun roundedLocationKey(
    latitude: Double?,
    longitude: Double?,
    decimals: Int = LOCATION_RENDER_DECIMALS
): RoundedLocationKey? {
    if (latitude == null || longitude == null) return null
    val scale = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1_000.0
        4 -> 10_000.0
        else -> Math.pow(10.0, decimals.toDouble())
    }
    return RoundedLocationKey(
        latitudeE4 = kotlin.math.round(latitude * scale).toInt(),
        longitudeE4 = kotlin.math.round(longitude * scale).toInt()
    )
}

internal fun buildPreviewStampRenderKey(
    stampEnabled: Boolean,
    isVideoMode: Boolean,
    aspectRatio: CameraAspectRatio,
    viewport: com.mapsupervision.photo.worker.AspectCropRect?,
    location: PhotoLocationSnapshot?,
    address: String = "",
    note: String = "",
    tileKey: RoundedLocationKey?,
    bearing: Float
): PreviewStampRenderKey {
    return PreviewStampRenderKey(
        stampEnabled = stampEnabled,
        isVideoMode = isVideoMode,
        aspectRatio = aspectRatio,
        viewportWidth = viewport?.width ?: 0,
        viewportHeight = viewport?.height ?: 0,
        locationKey = roundedLocationKey(location?.latitude, location?.longitude),
        address = address.trim(),
        note = note.trim(),
        tileKey = tileKey,
        bearing = bearing
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("DEPRECATION")
@Composable
fun CameraOverlay(
    nodeCode: String,
    projectId: String,
    projectSlug: String,
    photoPipelineService: IPhotoPipelineService,
    locationProvider: IPhotoLocationProvider,
    onPhotoCaptured: () -> Unit,
    onSavePhoto: suspend (java.io.File) -> Boolean,
    onDismiss: () -> Unit,
    nodes: List<GisNode> = emptyList(),
    routes: List<GisRoute> = emptyList()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            var savedAny = false
            uris.forEach { uri ->
                runCatching {
                    val file = withContext(Dispatchers.IO) {
                        photoPipelineService.importFromGallery(
                            storageRef = ProjectStorageRef(projectId, projectSlug),
                            capturedAt = System.currentTimeMillis(),
                            locationLabel = null,
                            note = "Imported",
                            folderType = resolveCaptureFolderType(nodeCode, routes),
                            objectCode = nodeCode,
                            sourceUri = uri.toString()
                        )
                    }
                    if (onSavePhoto(file)) {
                        savedAny = true
                    }
                }.onFailure {
                    AppLogger.e(it, "camera.overlay.gallery.import.failed uri=$uri")
                }
            }
            if (savedAny) {
                onPhotoCaptured()
            }
            onDismiss()
        }
    }

    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }

    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    var targetRotation by remember { mutableStateOf(Surface.ROTATION_0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isVideoMode by remember { mutableStateOf(false) }
    var isProcessingVideoStamp by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CaptureLensFacing.BACK) }
    var flashMode by remember { mutableStateOf(CameraFlashMode.OFF) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showFlashMenu by remember { mutableStateOf(false) }
    var activeExtensionMode by remember { mutableStateOf(ExtensionMode.NONE) }
    var previousExtensionMode by remember { mutableStateOf(ExtensionMode.NONE) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }

    var stampEnabled by remember { mutableStateOf(true) }
    var noteText by remember { mutableStateOf("") }
    var bearing by remember { mutableStateOf(0f) }
    var liveLocation by remember { mutableStateOf<PhotoLocationSnapshot?>(null) }
    var liveAddress by remember { mutableStateOf("") }
    val photoCaptureSession = remember { PhotoCaptureSession() }

    var selectedAspectRatio by remember { mutableStateOf(CameraAspectRatio.RATIO_4_3) }
    val isKeyboardVisible = WindowInsets.isImeVisible
    var showZoomIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(zoomRatio) {
        showZoomIndicator = true
        delay(1000)
        showZoomIndicator = false
    }

    var currentTileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentTileKey by remember { mutableStateOf<RoundedLocationKey?>(null) }
    var cachedTileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cachedTileKey by remember { mutableStateOf<RoundedLocationKey?>(null) }
    val addressCache = remember { mutableMapOf<RoundedLocationKey, String>() }
    var previewSurfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val previewViewport = remember(previewSurfaceSize, selectedAspectRatio) {
        if (previewSurfaceSize.width <= 0 || previewSurfaceSize.height <= 0) {
            null
        } else {
            calculateAspectCropRect(previewSurfaceSize.width, previewSurfaceSize.height, selectedAspectRatio)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentTileBitmap?.recycle()
            cachedTileBitmap?.recycle()
        }
    }

    val controlsEnabled = !isRecording && !isProcessingVideoStamp && !photoCaptureSession.isCapturingPhoto

    LaunchedEffect(stampEnabled) {
        if (!stampEnabled) {
            val oldTile = currentTileBitmap
            currentTileBitmap = null
            currentTileKey = null
            oldTile?.recycle()
            cachedTileBitmap?.recycle()
            cachedTileBitmap = null
            cachedTileKey = null
            addressCache.clear()
            return@LaunchedEffect
        }

        while (true) {
            runCatching {
                val loc = locationProvider.lastKnownLocation()
                liveLocation = loc
                val lat = loc.latitude
                val lng = loc.longitude
                val locationKey = roundedLocationKey(lat, lng)
                if (locationKey == null) {
                    liveAddress = ""
                    currentTileKey = null
                    currentTileBitmap?.recycle()
                    currentTileBitmap = null
                    return@runCatching
                }
                val safeLat = lat ?: return@runCatching
                val safeLng = lng ?: return@runCatching

                liveAddress = addressCache[locationKey] ?: withContext(Dispatchers.IO) {
                    reverseGeocode(context, safeLat, safeLng)
                }.also { addressCache[locationKey] = it }

                val nextTileBitmap = when {
                    currentTileKey == locationKey && currentTileBitmap != null -> currentTileBitmap
                    cachedTileKey == locationKey && cachedTileBitmap != null -> snapshotBitmap(cachedTileBitmap)
                    else -> {
                        val fetchedTile = withContext(Dispatchers.IO) {
                            PhotoStampRenderer.fetchOsmTile(safeLat, safeLng, zoom = PhotoStampRenderer.MINIMAP_MAX_ZOOM)
                        }
                        cachedTileBitmap?.takeIf { cachedTileKey != locationKey }?.recycle()
                        cachedTileBitmap = snapshotBitmap(fetchedTile)
                        cachedTileKey = locationKey
                        fetchedTile
                    }
                }

                if (nextTileBitmap !== currentTileBitmap) {
                    currentTileBitmap?.recycle()
                    currentTileBitmap = nextTileBitmap
                }
                currentTileKey = locationKey
            }.onFailure {
                AppLogger.e(it, "camera.overlay.location.poll.failed")
            }
            delay(LOCATION_POLL_INTERVAL_MS)
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        bearing = Math.toDegrees(orientationAngles[0].toDouble()).toFloat().let {
                            if (it < 0f) it + 360f else it
                        }
                    }

                    Sensor.TYPE_ORIENTATION -> bearing = event.values[0]
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        rotationSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    DisposableEffect(cameraProviderFuture) {
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener(
            { cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() },
            executor
        )
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    LaunchedEffect(cameraProvider) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val managerFuture = ExtensionsManager.getInstanceAsync(context, provider)
        managerFuture.addListener(
            {
                extensionsManager = runCatching { managerFuture.get() }.getOrNull()
                hasFrontCamera = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(lensFacing) {
        zoomRatio = 1f
    }

    LaunchedEffect(isVideoMode) {
        if (isVideoMode) {
            previousExtensionMode = activeExtensionMode
            activeExtensionMode = ExtensionMode.NONE
        } else if (previousExtensionMode != ExtensionMode.NONE) {
            activeExtensionMode = previousExtensionMode
        }
    }

    DisposableEffect(context, previewView, preview, imageCapture, videoCapture) {
        targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        preview.targetRotation = targetRotation
        imageCapture.targetRotation = targetRotation
        videoCapture.targetRotation = targetRotation

        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val updatedRotation = orientationToSurfaceRotation(orientation)
                if (updatedRotation != targetRotation) {
                    targetRotation = updatedRotation
                    preview.targetRotation = updatedRotation
                    imageCapture.targetRotation = updatedRotation
                    videoCapture.targetRotation = updatedRotation
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose { orientationListener.disable() }
    }

    val selectedLensSelector = remember(lensFacing) {
        if (lensFacing == CaptureLensFacing.BACK) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }
    val cameraSelector = remember(activeExtensionMode, extensionsManager, isVideoMode, selectedLensSelector) {
        if (isVideoMode || extensionsManager == null || activeExtensionMode == ExtensionMode.NONE) {
            selectedLensSelector
        } else if (extensionsManager!!.isExtensionAvailable(selectedLensSelector, activeExtensionMode)) {
            extensionsManager!!.getExtensionEnabledCameraSelector(selectedLensSelector, activeExtensionMode)
        } else {
            selectedLensSelector
        }
    }

    LaunchedEffect(cameraProvider, cameraSelector, isVideoMode, zoomRatio, stampEnabled) {
        val provider = cameraProvider ?: return@LaunchedEffect
        runCatching {
            provider.unbindAll()
            preview.surfaceProvider = previewView.surfaceProvider
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .let { builder ->
                    if (isVideoMode) builder.addUseCase(videoCapture) else builder.addUseCase(imageCapture)
                }
                .build()
            val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
            boundCamera = camera
            val zoomState = camera.cameraInfo.zoomState.value
            val resolvedMinZoom = zoomState?.minZoomRatio ?: 1f
            val resolvedMaxZoom = zoomState?.maxZoomRatio ?: 1f
            minZoomRatio = resolvedMinZoom
            maxZoomRatio = resolvedMaxZoom
            val clampedZoom = clampZoomRatio(zoomRatio, resolvedMinZoom, resolvedMaxZoom)
            zoomRatio = clampedZoom
            flashAvailable = camera.cameraInfo.hasFlashUnit()
            if (!flashAvailable && flashMode != CameraFlashMode.OFF) {
                flashMode = CameraFlashMode.OFF
            }
            imageCapture.flashMode = resolveImageCaptureFlashMode(flashMode)
            camera.cameraControl.setZoomRatio(clampedZoom)
        }.onFailure {
            boundCamera = null
            AppLogger.e(it, "camera.overlay.bind.failed")
        }
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = resolveImageCaptureFlashMode(flashMode)
    }

    LaunchedEffect(boundCamera, isVideoMode, isRecording, flashMode, flashAvailable) {
        val camera = boundCamera ?: return@LaunchedEffect
        val enableTorch = isVideoMode && isRecording && flashAvailable && resolveVideoTorchEnabled(flashMode)
        runCatching { camera.cameraControl.enableTorch(enableTorch) }
            .onFailure { AppLogger.e(it, "camera.overlay.torch.failed enabled=$enableTorch") }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Cần quyền truy cập Camera", color = Color.White, fontWeight = FontWeight.Bold)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Cấp quyền Camera")
                }
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Hủy")
                }
            }
            return@Box
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewSurfaceSize = it }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            val viewport = previewViewport
            if (stampEnabled && viewport != null) {
                val previewStamp = remember(liveLocation, liveAddress, noteText, bearing) {
                    buildCaptureStamp(
                        timestampMs = System.currentTimeMillis(),
                        location = liveLocation,
                        address = liveAddress,
                        note = noteText,
                        bearingDeg = bearing,
                        nodes = nodes,
                        routes = routes
                    )
                }
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(viewport.left, viewport.top) }
                        .width(with(density) { viewport.width.toDp() })
                        .height(with(density) { viewport.height.toDp() })
                ) {
                    drawIntoCanvas { canvas ->
                        PhotoStampRenderer.drawStamp(
                            canvas = canvas.nativeCanvas,
                            frameWidth = size.width,
                            frameHeight = size.height,
                            stamp = previewStamp,
                            tileBitmap = currentTileBitmap,
                            missingLocationText = "Không có vị trí"
                        )
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (selectedAspectRatio != CameraAspectRatio.RATIO_FULL) {
                val viewport = calculateAspectCropRect(size.width.toInt(), size.height.toInt(), selectedAspectRatio)
                val outW = size.width
                val outH = size.height
                val vpX = viewport.left.toFloat()
                val vpY = viewport.top.toFloat()
                val vpW = viewport.width.toFloat()
                val vpH = viewport.height.toFloat()

                val paintColor = Color.Black.copy(alpha = 0.85f)
                if (vpX > 0f) {
                    drawRect(color = paintColor, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(vpX, outH))
                    drawRect(color = paintColor, topLeft = androidx.compose.ui.geometry.Offset(vpX + vpW, 0f), size = androidx.compose.ui.geometry.Size(vpX, outH))
                }
                if (vpY > 0f) {
                    drawRect(color = paintColor, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(outW, vpY))
                    drawRect(color = paintColor, topLeft = androidx.compose.ui.geometry.Offset(0f, vpY + vpH), size = androidx.compose.ui.geometry.Size(outW, vpY))
                }
            }
        }



        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        isProcessingVideoStamp -> "ĐANG ĐÓNG STAMP VIDEO..."
                        isRecording -> "ĐANG QUAY..."
                        nodeCode.isBlank() -> "Chụp ảnh hiện trường"
                        else -> "Đối tượng: $nodeCode"
                    },
                    color = if (isRecording || isProcessingVideoStamp) Color.Red else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = if (stampEnabled) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Stamp",
                        color = if (stampEnabled) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = stampEnabled,
                        onCheckedChange = { if (controlsEnabled) stampEnabled = it },
                        enabled = controlsEnabled,
                        modifier = Modifier.size(width = 44.dp, height = 24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color(0xAAFFFFFF),
                            uncheckedTrackColor = Color(0x44FFFFFF)
                        )
                    )
                }
                if (flashAvailable) {
                    IconButton(
                        onClick = { if (controlsEnabled) showFlashMenu = !showFlashMenu },
                        enabled = controlsEnabled,
                        modifier = Modifier.size(32.dp)
                    ) {
                        val flashIcon = when (flashMode) {
                            CameraFlashMode.AUTO -> Icons.Outlined.FlashAuto
                            CameraFlashMode.OFF -> Icons.Outlined.FlashOff
                            CameraFlashMode.ON -> Icons.Outlined.FlashOn
                        }
                        Icon(
                            imageVector = flashIcon,
                            contentDescription = "Flash mode",
                            tint = if (flashMode == CameraFlashMode.OFF) Color.White else Color(0xFF00E5FF)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (!isProcessingVideoStamp) {
                            if (isRecording) {
                                activeRecording?.stop()
                                activeRecording = null
                                isRecording = false
                            }
                            onDismiss()
                        }
                    },
                    enabled = !isProcessingVideoStamp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = Color.White)
                }
            }
        }

        // Popup chọn Flash mờ nổi trên màn hình
        if (showFlashMenu && controlsEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showFlashMenu = false }
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 60.dp, end = 48.dp)
                        .align(Alignment.TopEnd)
                        .background(Color(0xCC0A0D1A), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0x1A00E5FF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CameraFlashMode.entries.forEach { mode ->
                        val selected = flashMode == mode
                        Text(
                            text = when (mode) {
                                CameraFlashMode.AUTO -> "Auto"
                                CameraFlashMode.OFF -> "Off"
                                CameraFlashMode.ON -> "On"
                            },
                            color = if (selected) Color(0xFF00E5FF) else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (controlsEnabled && flashAvailable) {
                                        flashMode = mode
                                    }
                                    showFlashMenu = false
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Cụm các nút điều khiển phía dưới màn hình hoàn toàn trong suốt
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Trường nhập ghi chú bán trong suốt
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (controlsEnabled) noteText = it },
                enabled = controlsEnabled,
                placeholder = { Text("Ghi chú (tùy chọn)...", color = Color(0xAAFFFFFF), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0x33FFFFFF),
                    unfocusedContainerColor = Color(0x15FFFFFF),
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    cursorColor = Color(0xFF00E5FF)
                )
            )

            if (!isKeyboardVisible) {
                // Thanh Zoom và Cài đặt
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Floating Zoom Indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            if (showZoomIndicator) {
                                val percentage = if (maxZoomRatio > minZoomRatio) {
                                    (zoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)
                                } else {
                                    0f
                                }
                                val xOffset = (170.dp * percentage) - 10.dp
                                Text(
                                    text = "${"%.1f".format(zoomRatio)}x",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .offset(x = xOffset)
                                        .background(Color(0xCC0A0D1A), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, Color(0x3300E5FF), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Slider Line
                        @OptIn(ExperimentalMaterial3Api::class)
                        Slider(
                            value = zoomRatio,
                            onValueChange = { requestedZoom ->
                                val clampedZoom = clampZoomRatio(requestedZoom, minZoomRatio, maxZoomRatio)
                                zoomRatio = clampedZoom
                                boundCamera?.cameraControl?.setZoomRatio(clampedZoom)
                            },
                            valueRange = minZoomRatio..maxZoomRatio.coerceAtLeast(minZoomRatio),
                            enabled = controlsEnabled && maxZoomRatio > minZoomRatio,
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                        .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0x44FFFFFF),
                                        inactiveTrackColor = Color(0x11FFFFFF)
                                    ),
                                    enabled = controlsEnabled,
                                    modifier = Modifier.height(2.dp)
                                )
                            }
                        )
                    }

                    IconButton(
                        onClick = { if (controlsEnabled) showSettingsSheet = true },
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .size(38.dp)
                            .align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Cài đặt",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Thanh chọn chế độ ẢNH / VIDEO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ẢNH",
                        color = if (!isVideoMode) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = if (!isVideoMode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = controlsEnabled) { isVideoMode = false }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "VIDEO",
                        color = if (isVideoMode) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = controlsEnabled) { isVideoMode = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Dải nút chính dưới cùng: [Thêm media] [Nút chụp/quay] [Xoay camera]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                enabled = controlsEnabled,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Photo,
                            contentDescription = "Thêm từ máy",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Thêm media", color = Color.White, fontSize = 11.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .let {
                                if (isVideoMode) {
                                    it.background(if (isRecording) Color.Red else Color.White)
                                } else {
                                    it.background(Color(0x22FFFFFF))
                                      .border(1.5.dp, Color.White, CircleShape)
                                }
                            }
                            .clickable(
                                enabled = !isProcessingVideoStamp,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                focusManager.clearFocus()
                                if (isVideoMode) {
                                    if (isRecording) {
                                        activeRecording?.stop()
                                        activeRecording = null
                                        isRecording = false
                                    } else {
                                        if (!hasAudioPermission) {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            return@clickable
                                        }
                                        val stampAtRecordStart = buildCaptureStamp(
                                            timestampMs = System.currentTimeMillis(),
                                            location = liveLocation,
                                            address = liveAddress,
                                            note = noteText,
                                            bearingDeg = bearing,
                                            nodes = nodes,
                                            routes = routes
                                        )
                                        val recordingTileBitmap = snapshotBitmap(currentTileBitmap)
                                        val loc = liveLocation
                                        val locationLabel = liveAddress.takeIf { it.isNotBlank() }
                                            ?: if (loc?.latitude != null && loc?.longitude != null) {
                                                "${loc.latitude}_${loc.longitude}"
                                            } else null
                                        val videoFile = photoPipelineService.createCaptureVideoOutputFile(
                                            storageRef = ProjectStorageRef(projectId, projectSlug),
                                            capturedAt = System.currentTimeMillis(),
                                            locationLabel = locationLabel,
                                            note = noteText.takeIf { it.isNotBlank() },
                                            folderType = CaptureFolderType.NODE,
                                            objectCode = nodeCode
                                        )
                                        val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                        var pending = videoCapture.output.prepareRecording(context, outputOptions)
                                        if (hasAudioPermission) {
                                            pending = pending.withAudioEnabled()
                                        }
                                        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Start -> {
                                                    isRecording = true
                                                }
                                                is VideoRecordEvent.Finalize -> {
                                                    isRecording = false
                                                    activeRecording = null
                                                    if (!event.hasError()) {
                                                        coroutineScope.launch {
                                                            try {
                                                                postProcessRecordedVideo(
                                                                    videoFile = videoFile,
                                                                    stampEnabled = stampEnabled,
                                                                    stampAtRecordStart = stampAtRecordStart,
                                                                    tileBitmap = recordingTileBitmap,
                                                                    photoPipelineService = photoPipelineService,
                                                                    setProcessingVideoStamp = { isProcessingVideoStamp = it },
                                                                    onSavePhoto = onSavePhoto,
                                                                    onPhotoCaptured = onPhotoCaptured
                                                                )
                                                                onDismiss()
                                                            } catch (error: Throwable) {
                                                                AppLogger.e(error, "camera.overlay.capture.video.failed")
                                                                runCatching { videoFile.delete() }
                                                                onDismiss()
                                                            } finally {
                                                                recordingTileBitmap?.recycle()
                                                            }
                                                        }
                                                    } else {
                                                        runCatching { videoFile.delete() }
                                                        onDismiss()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val capturedStamp = buildCaptureStamp(
                                        timestampMs = System.currentTimeMillis(),
                                        location = liveLocation,
                                        address = liveAddress,
                                        note = noteText,
                                        bearingDeg = bearing,
                                        nodes = nodes,
                                        routes = routes
                                    )
                                    val capturedStampEnabled = stampEnabled
                                    val capturedTileBitmap = snapshotBitmap(currentTileBitmap)
                                    val loc = liveLocation
                                    val locationLabel = liveAddress.takeIf { it.isNotBlank() }
                                        ?: if (loc?.latitude != null && loc?.longitude != null) {
                                            "${loc.latitude}_${loc.longitude}"
                                        } else null
                                    if (photoCaptureSession.tryBeginCapture()) {
                                        val file = photoPipelineService.createCaptureOutputFile(
                                            storageRef = ProjectStorageRef(projectId, projectSlug),
                                            capturedAt = System.currentTimeMillis(),
                                            locationLabel = locationLabel,
                                            note = noteText.takeIf { it.isNotBlank() },
                                            folderType = CaptureFolderType.NODE,
                                            objectCode = nodeCode
                                        )
                                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                                        imageCapture.targetRotation = targetRotation
                                        try {
                                            imageCapture.takePicture(
                                                output,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                                        coroutineScope.launch {
                                                            try {
                                                                if (capturedStampEnabled) {
                                                                    withContext(Dispatchers.IO) {
                                                                        photoPipelineService.applyStamp(
                                                                            file,
                                                                            capturedStamp,
                                                                            selectedAspectRatio,
                                                                            capturedTileBitmap
                                                                        )
                                                                    }
                                                                }
                                                                if (onSavePhoto(file)) {
                                                                    onPhotoCaptured()
                                                                }
                                                            } finally {
                                                                photoCaptureSession.finishCapture()
                                                                capturedTileBitmap?.recycle()
                                                                onDismiss()
                                                            }
                                                        }
                                                    }

                                                    override fun onError(e: ImageCaptureException) {
                                                        AppLogger.e(e, "camera.overlay.capture.image.failed")
                                                        photoCaptureSession.finishCapture()
                                                        capturedTileBitmap?.recycle()
                                                        onDismiss()
                                                    }
                                                }
                                            )
                                        } catch (e: Throwable) {
                                            AppLogger.e(e, "camera.overlay.capture.image.failed")
                                            photoCaptureSession.finishCapture()
                                            capturedTileBitmap?.recycle()
                                            onDismiss()
                                        }
                                    } else {
                                        capturedTileBitmap?.recycle()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideoMode) {
                            Box(
                                modifier = Modifier
                                    .size(if (isRecording) 24.dp else 60.dp)
                                    .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                                    .background(if (isRecording) Color.White else Color.Red)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Chụp ảnh",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                enabled = controlsEnabled && hasFrontCamera,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                if (controlsEnabled && hasFrontCamera) {
                                    lensFacing = if (lensFacing == CaptureLensFacing.BACK) {
                                        CaptureLensFacing.FRONT
                                    } else {
                                        CaptureLensFacing.BACK
                                    }
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Cached,
                            contentDescription = "Đổi camera",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Xoay camera", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // Bảng cài đặt mờ (Bottom Sheet dạng Custom Card)
        if (showSettingsSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSettingsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Color(0xDD0A0D1A),
                            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .border(1.dp, Color(0x1A00E5FF), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(20.dp)
                        .clickable(enabled = false) { }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CÀI ĐẶT CAMERA",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        IconButton(
                            onClick = { showSettingsSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tỷ lệ khung hình",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CameraAspectRatio.entries.forEach { ratio ->
                            FilterChip(
                                selected = selectedAspectRatio == ratio,
                                onClick = { if (controlsEnabled) selectedAspectRatio = ratio },
                                label = { Text(ratio.displayName) },
                                enabled = controlsEnabled,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF),
                                    selectedLabelColor = Color(0xFF060814),
                                    containerColor = Color(0x15FFFFFF),
                                    labelColor = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (!isVideoMode) {
                        Text(
                            text = "Chế độ chụp ảnh",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Normal" to ExtensionMode.NONE,
                                "HDR" to ExtensionMode.HDR,
                                "Night" to ExtensionMode.NIGHT,
                                "Bokeh" to ExtensionMode.BOKEH,
                                "Face Retouch" to ExtensionMode.FACE_RETOUCH
                            ).forEach { (label, mode) ->
                                val supported = mode == ExtensionMode.NONE ||
                                    extensionsManager?.isExtensionAvailable(selectedLensSelector, mode) == true
                                FilterChip(
                                    selected = activeExtensionMode == mode,
                                    onClick = {
                                        if (controlsEnabled && supported) {
                                            activeExtensionMode = mode
                                        }
                                    },
                                    label = { Text(label) },
                                    enabled = supported && controlsEnabled,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF),
                                        selectedLabelColor = Color(0xFF060814),
                                        containerColor = Color(0x15FFFFFF),
                                        labelColor = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "* Cài đặt chế độ mở rộng chỉ hỗ trợ khi chụp ảnh",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (isProcessingVideoStamp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Đang đóng stamp vào video...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StampPill(icon: String, text: String) {
    Row(
        modifier = Modifier
            .background(
                Color(0xCC1964BE),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(icon, fontSize = 13.sp)
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

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

private fun resolveCaptureFolderType(code: String, routes: List<GisRoute>): CaptureFolderType {
    val isRoute = routes.any { it.code.equals(code, ignoreCase = true) }
    return if (isRoute) CaptureFolderType.ROUTE else CaptureFolderType.NODE
}

