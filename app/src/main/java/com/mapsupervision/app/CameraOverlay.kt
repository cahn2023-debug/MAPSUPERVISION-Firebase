package com.mapsupervision.app

import android.Manifest
import android.content.Context
import kotlin.math.roundToInt
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CaptureStampMapNode
import com.mapsupervision.domain.model.CaptureStampMapRoute
import com.mapsupervision.domain.model.CaptureStampMapScene
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MediaStatusTags
import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.ProjectStorageRef
import com.mapsupervision.domain.model.RoundedLocationKey
import com.mapsupervision.domain.model.VideoStampTimelineSample
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.photo.worker.AspectCropRect
import com.mapsupervision.photo.worker.PhotoStampRenderer
import com.mapsupervision.photo.worker.calculateAspectCropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

internal fun resolveLatchedMinimapZoom(candidateZoom: Int, currentZoom: Int): Int {
    val minZoom = PhotoStampRenderer.MINIMAP_MIN_ZOOM
    val maxZoom = PhotoStampRenderer.MINIMAP_MAX_ZOOM
    return candidateZoom.coerceIn(minZoom, maxZoom)
}

internal fun convertToCaptureMapNodes(nodes: List<GisNode>): List<CaptureStampMapNode> =
    nodes.map {
        CaptureStampMapNode(
            code = it.code,
            latitude = it.latitude,
            longitude = it.longitude,
            label = it.code,
            highlighted = false
        )
    }

internal fun convertToCaptureMapRoutes(routes: List<GisRoute>): List<CaptureStampMapRoute> =
    routes.map {
        CaptureStampMapRoute(
            code = it.code,
            points = it.points,
            highlighted = false
        )
    }

internal fun buildCaptureStamp(
    timestampMs: Long,
    location: PhotoLocationSnapshot?,
    address: String = "",
    note: String = "",
    bearingDeg: Float,
    mapNodes: List<CaptureStampMapNode> = emptyList(),
    mapRoutes: List<CaptureStampMapRoute> = emptyList(),
    movementPath: List<Pair<Double, Double>> = emptyList(),
    minimapZoom: Int? = null,
    markerScale: Float = 1.0f,
    fovAngleDeg: Float = 30.0f,
    fovLengthScale: Float = 1.0f,
    statusTag: String? = null
): CaptureStamp {
    val mapScene = if (mapNodes.isNotEmpty() || mapRoutes.isNotEmpty() || movementPath.isNotEmpty() || minimapZoom != null || markerScale != 1.0f || fovAngleDeg != 30.0f || fovLengthScale != 1.0f) {
        CaptureStampMapScene(
            centerLatitude = location?.latitude,
            centerLongitude = location?.longitude,
            cameraLatitude = location?.latitude,
            cameraLongitude = location?.longitude,
            bearingDeg = bearingDeg,
            nodes = mapNodes,
            routes = mapRoutes,
            movementPath = movementPath,
            minimapZoom = minimapZoom,
            markerScale = markerScale,
            fovAngleDeg = fovAngleDeg,
            fovLengthScale = fovLengthScale
        )
    } else null

    return CaptureStamp(
        timestampMs = timestampMs,
        latitude = location?.latitude,
        longitude = location?.longitude,
        address = address.trim(),
        note = note.trim(),
        bearingDeg = bearingDeg,
        mapScene = mapScene,
        statusTag = statusTag?.trim()?.takeIf { it.isNotEmpty() }
    )
}

@JvmName("buildCaptureStampFromGis")
internal fun buildCaptureStamp(
    timestampMs: Long,
    location: PhotoLocationSnapshot?,
    address: String = "",
    note: String = "",
    bearingDeg: Float,
    nodes: List<GisNode>,
    routes: List<GisRoute> = emptyList(),
    movementPath: List<Pair<Double, Double>> = emptyList(),
    minimapZoom: Int? = null,
    markerScale: Float = 1.0f,
    fovAngleDeg: Float = 30.0f,
    fovLengthScale: Float = 1.0f,
    statusTag: String? = null
): CaptureStamp = buildCaptureStamp(
    timestampMs = timestampMs,
    location = location,
    address = address,
    note = note,
    bearingDeg = bearingDeg,
    mapNodes = convertToCaptureMapNodes(nodes),
    mapRoutes = convertToCaptureMapRoutes(routes),
    movementPath = movementPath,
    minimapZoom = minimapZoom,
    markerScale = markerScale,
    fovAngleDeg = fovAngleDeg,
    fovLengthScale = fovLengthScale,
    statusTag = statusTag
)

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

internal class CameraMovementPath {
    private val points = mutableListOf<Pair<Double, Double>>()

    fun append(location: PhotoLocationSnapshot?): List<Pair<Double, Double>> {
        val point = location?.latitude?.let { latitude ->
            location.longitude?.let { longitude -> latitude to longitude }
        } ?: return points.toList()

        if (points.lastOrNull() != point) {
            points += point
        }
        return points.toList()
    }

    fun snapshot(): List<Pair<Double, Double>> = points.toList()
}

internal suspend fun postProcessRecordedVideo(
    videoFile: java.io.File,
    stampEnabled: Boolean,
    stampAtRecordStart: CaptureStamp?,
    tileBitmap: Bitmap?,
    timelineSamples: List<VideoStampTimelineSample>,
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
            if (timelineSamples.isNotEmpty()) {
                photoPipelineService.exportVideoStamp(videoFile, timelineSamples)
            } else {
                photoPipelineService.exportVideoStamp(videoFile, stamp, tileBitmap)
            }
        }
        val saved = onSavePhoto(videoFile)
        if (saved) onPhotoCaptured()
        saved
    } finally {
        setProcessingVideoStamp(false)
    }
}

internal fun snapshotBitmap(bitmap: Bitmap?): Bitmap? = bitmap?.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)

internal fun buildPreviewStampOverlayBitmap(
    frameWidthPx: Int,
    frameHeightPx: Int,
    stamp: CaptureStamp,
    aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_FULL,
    tileBitmap: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(frameWidthPx, frameHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    PhotoStampRenderer.drawStamp(
        canvas = canvas,
        frameWidth = frameWidthPx.toFloat(),
        frameHeight = frameHeightPx.toFloat(),
        stamp = stamp,
        tileBitmap = tileBitmap
    )
    return bitmap
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
    val bearing: Float,
    val statusTag: String? = null
)

private const val LOCATION_POLL_INTERVAL_MS = 8_000L
private const val LOCATION_RENDER_DECIMALS = 4
private const val VIDEO_STAMP_SAMPLE_INTERVAL_MS = 250L

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
    viewport: AspectCropRect?,
    location: PhotoLocationSnapshot?,
    address: String = "",
    note: String = "",
    tileKey: RoundedLocationKey?,
    bearing: Float,
    statusTag: String? = null
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
        bearing = bearing,
        statusTag = statusTag?.trim()?.takeIf { it.isNotEmpty() }
    )
}

internal fun buildVideoStampTimelineSample(
    recordingStartElapsedMs: Long,
    nowElapsedMs: Long,
    location: PhotoLocationSnapshot?,
    address: String,
    note: String,
    bearingDeg: Float,
    mapNodes: List<CaptureStampMapNode> = emptyList(),
    mapRoutes: List<CaptureStampMapRoute> = emptyList(),
    movementPath: List<Pair<Double, Double>> = emptyList(),
    minimapZoom: Int? = null,
    markerScale: Float = 1.0f,
    fovAngleDeg: Float = 30.0f,
    fovLengthScale: Float = 1.0f,
    tileBitmap: Any? = null,
    statusTag: String? = null
): VideoStampTimelineSample {
    return VideoStampTimelineSample(
        elapsedMs = (nowElapsedMs - recordingStartElapsedMs).coerceAtLeast(0L),
        stamp = buildCaptureStamp(
            timestampMs = System.currentTimeMillis(),
            location = location,
            address = address,
            note = note,
            bearingDeg = bearingDeg,
            mapNodes = mapNodes,
            mapRoutes = mapRoutes,
            movementPath = movementPath,
            minimapZoom = minimapZoom,
            markerScale = markerScale,
            fovAngleDeg = fovAngleDeg,
            fovLengthScale = fovLengthScale,
            statusTag = statusTag
        ),
        tileBitmap = tileBitmap
    )
}

@JvmName("buildVideoStampTimelineSampleFromGis")
internal fun buildVideoStampTimelineSample(
    recordingStartElapsedMs: Long,
    nowElapsedMs: Long,
    location: PhotoLocationSnapshot?,
    address: String,
    note: String,
    bearingDeg: Float,
    nodes: List<GisNode>,
    routes: List<GisRoute> = emptyList(),
    movementPath: List<Pair<Double, Double>> = emptyList(),
    minimapZoom: Int? = null,
    markerScale: Float = 1.0f,
    fovAngleDeg: Float = 30.0f,
    fovLengthScale: Float = 1.0f,
    tileBitmap: Any? = null,
    statusTag: String? = null
): VideoStampTimelineSample = buildVideoStampTimelineSample(
    recordingStartElapsedMs = recordingStartElapsedMs,
    nowElapsedMs = nowElapsedMs,
    location = location,
    address = address,
    note = note,
    bearingDeg = bearingDeg,
    mapNodes = convertToCaptureMapNodes(nodes),
    mapRoutes = convertToCaptureMapRoutes(routes),
    movementPath = movementPath,
    minimapZoom = minimapZoom,
    markerScale = markerScale,
    fovAngleDeg = fovAngleDeg,
    fovLengthScale = fovLengthScale,
    tileBitmap = tileBitmap,
    statusTag = statusTag
)

internal fun MutableList<VideoStampTimelineSample>.appendVideoStampTimelineSample(
    sample: VideoStampTimelineSample
) {
    val previousSample = lastOrNull()
    if (previousSample == null || previousSample.elapsedMs != sample.elapsedMs || previousSample.stamp != sample.stamp) {
        add(sample)
    }
}

private fun remapBearingForTargetRotation(
    rawBearing: Float,
    targetRotation: Int
): Float {
    val adjustedBearing = when (targetRotation) {
        Surface.ROTATION_90 -> rawBearing + 90f
        Surface.ROTATION_180 -> rawBearing + 180f
        Surface.ROTATION_270 -> rawBearing + 270f
        else -> rawBearing
    }
    return ((adjustedBearing % 360f) + 360f) % 360f
}

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
    CameraOverlay(
        nodeCode = nodeCode,
        projectId = projectId,
        projectSlug = projectSlug,
        photoPipelineService = photoPipelineService,
        locationProvider = locationProvider,
        onPhotoCaptured = onPhotoCaptured,
        onSavePhoto = { file, _, _, _ -> onSavePhoto(file) },
        onDismiss = onDismiss,
        nodes = nodes,
        routes = routes
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
    onSavePhoto: suspend (file: java.io.File, statusTag: String?, note: String?, address: String?) -> Boolean,
    onDismiss: () -> Unit,
    nodes: List<GisNode> = emptyList(),
    routes: List<GisRoute> = emptyList(),
    statusTags: List<String> = MediaStatusTags.systemNames
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

    val captureMapNodes = remember(nodes) { convertToCaptureMapNodes(nodes) }
    val captureMapRoutes = remember(routes) { convertToCaptureMapRoutes(routes) }
    var selectedStatusTag by remember { mutableStateOf<String?>("Hiện trạng") }
    var noteText by remember { mutableStateOf("") }
    var bearing by remember { mutableStateOf(0f) }
    var liveLocation by remember { mutableStateOf<PhotoLocationSnapshot?>(null) }
    var liveAddress by remember { mutableStateOf("") }
    val cameraMovementPath = remember { CameraMovementPath() }
    var liveMovementPath by remember { mutableStateOf(emptyList<Pair<Double, Double>>()) }
    val cameraPrefs = remember(context) { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    var customMinimapZoom by remember {
        mutableStateOf(cameraPrefs.getInt("minimap_custom_zoom", PhotoStampRenderer.MINIMAP_MAX_ZOOM))
    }
    var customMarkerScale by remember {
        mutableStateOf(cameraPrefs.getFloat("minimap_marker_scale", 1.0f))
    }
    var customFovAngle by remember {
        mutableStateOf(cameraPrefs.getFloat("minimap_fov_angle", 30.0f))
    }
    var customFovLength by remember {
        mutableStateOf(cameraPrefs.getFloat("minimap_fov_length", 1.0f))
    }
    var liveMinimapZoom by remember { mutableStateOf(customMinimapZoom) }
    var settingsSheetHeightPx by remember { mutableStateOf(0) }
    val photoCaptureSession = remember { PhotoCaptureSession() }

    LaunchedEffect(customMinimapZoom, customMarkerScale, customFovAngle, customFovLength) {
        cameraPrefs.edit()
            .putInt("minimap_custom_zoom", customMinimapZoom)
            .putFloat("minimap_marker_scale", customMarkerScale)
            .putFloat("minimap_fov_angle", customFovAngle)
            .putFloat("minimap_fov_length", customFovLength)
            .apply()
    }

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
                            sourceUri = uri.toString(),
                            statusTag = selectedStatusTag
                        )
                    }
                    if (onSavePhoto(file, selectedStatusTag, "Imported", liveAddress)) {
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
    var isFinalizingRecording by remember { mutableStateOf(false) }
    var dismissAfterRecording by remember { mutableStateOf(false) }
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

    var selectedAspectRatio by remember { mutableStateOf(CameraAspectRatio.RATIO_4_3) }
    val isKeyboardVisible = WindowInsets.isImeVisible
    var isNoteFocused by remember { mutableStateOf(false) }
    val isKeyboardActive = isKeyboardVisible && isNoteFocused
    var showZoomIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(zoomRatio) {
        showZoomIndicator = true
        delay(1000)
        showZoomIndicator = false
    }

    var currentTileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentTileKey by remember { mutableStateOf<RoundedLocationKey?>(null) }
    var currentTileZoom by remember { mutableStateOf(PhotoStampRenderer.MINIMAP_MAX_ZOOM) }
    var recordingStartElapsedMs by remember { mutableStateOf<Long?>(null) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }
    var recordingTimelineTileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recordingTimelineTileKey by remember { mutableStateOf<RoundedLocationKey?>(null) }
    var recordingTimelineTileZoom by remember { mutableStateOf(PhotoStampRenderer.MINIMAP_MAX_ZOOM) }
    val recordingTimelineTileBitmaps = remember { mutableListOf<Bitmap>() }
    val recordingTimelineSamples = remember { mutableListOf<VideoStampTimelineSample>() }
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
            recordingTimelineTileBitmaps.forEach { it.recycle() }
            recordingTimelineTileBitmaps.clear()
        }
    }

    val controlsEnabled = !isRecording && !isFinalizingRecording && !isProcessingVideoStamp && !photoCaptureSession.isCapturingPhoto

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSeconds = 0
            while (isRecording) {
                delay(1000)
                if (isRecording) {
                    recordingDurationSeconds += 1
                }
            }
        } else {
            recordingDurationSeconds = 0
        }
    }

    LaunchedEffect(isRecording, recordingStartElapsedMs, stampEnabled) {
        val startElapsedMs = recordingStartElapsedMs ?: return@LaunchedEffect
        if (!isRecording || !stampEnabled) {
            return@LaunchedEffect
        }
        while (isRecording) {
            val tileBitmap = recordingTimelineTileBitmap
            if (tileBitmap != null) {
                recordingTimelineSamples.appendVideoStampTimelineSample(
                    buildVideoStampTimelineSample(
                        recordingStartElapsedMs = startElapsedMs,
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                        location = liveLocation,
                        address = liveAddress,
                        note = noteText,
                        bearingDeg = bearing,
                        mapNodes = captureMapNodes,
                        mapRoutes = captureMapRoutes,
                        movementPath = liveMovementPath,
                        minimapZoom = liveMinimapZoom,
                        markerScale = customMarkerScale,
                        fovAngleDeg = customFovAngle,
                        fovLengthScale = customFovLength,
                        tileBitmap = tileBitmap,
                        statusTag = selectedStatusTag
                    )
                )
            }
            delay(VIDEO_STAMP_SAMPLE_INTERVAL_MS)
        }
    }

    LaunchedEffect(stampEnabled) {
        if (!stampEnabled) {
            val oldTile = currentTileBitmap
            currentTileBitmap = null
            currentTileKey = null
            currentTileZoom = PhotoStampRenderer.MINIMAP_MAX_ZOOM
            oldTile?.recycle()
            addressCache.clear()
        }
    }

    LaunchedEffect(customMinimapZoom) {
        liveMinimapZoom = customMinimapZoom
        val loc = liveLocation
        val safeLat = loc?.latitude
        val safeLng = loc?.longitude
        if (safeLat != null && safeLng != null && stampEnabled) {
            val locationKey = roundedLocationKey(safeLat, safeLng)
            withContext(Dispatchers.IO) {
                val nextTileBitmap = PhotoStampRenderer.fetchOsmTile(safeLat, safeLng, zoom = customMinimapZoom)
                if (nextTileBitmap != null) {
                    val oldTile = currentTileBitmap
                    currentTileBitmap = nextTileBitmap
                    currentTileKey = locationKey
                    currentTileZoom = customMinimapZoom
                    if (oldTile != null && oldTile !== nextTileBitmap) {
                        oldTile.recycle()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val loc = locationProvider.lastKnownLocation()
                val lat = loc.latitude
                val lng = loc.longitude
                val locationKey = roundedLocationKey(lat, lng)
                if (locationKey == null) {
                    return@runCatching
                }
                val safeLat = lat ?: return@runCatching
                val safeLng = lng ?: return@runCatching
                liveLocation = loc
                liveMovementPath = cameraMovementPath.append(loc)
                val mapScene = buildCaptureStamp(
                    timestampMs = 0L,
                    location = loc,
                    bearingDeg = bearing,
                    mapNodes = captureMapNodes,
                    mapRoutes = captureMapRoutes,
                    movementPath = liveMovementPath,
                    minimapZoom = customMinimapZoom,
                    markerScale = customMarkerScale,
                    fovAngleDeg = customFovAngle,
                    fovLengthScale = customFovLength
                ).mapScene
                val candidateZoom = PhotoStampRenderer.resolveMinimapZoom(
                    latitude = safeLat,
                    longitude = safeLng,
                    bearingDeg = bearing,
                    mapScene = mapScene
                )
                val tileZoom = resolveLatchedMinimapZoom(candidateZoom, liveMinimapZoom)
                liveMinimapZoom = tileZoom

                liveAddress = addressCache[locationKey] ?: withContext(Dispatchers.IO) {
                    reverseGeocode(context, safeLat, safeLng)
                }.also { addressCache[locationKey] = it }

                if (!stampEnabled) {
                    return@runCatching
                }

                val nextTileBitmap = if (currentTileKey == locationKey && currentTileZoom == tileZoom && currentTileBitmap != null) {
                    currentTileBitmap
                } else {
                    withContext(Dispatchers.IO) {
                        PhotoStampRenderer.fetchOsmTile(safeLat, safeLng, zoom = tileZoom)
                    }
                }

                if (nextTileBitmap !== currentTileBitmap) {
                    currentTileBitmap?.recycle()
                    currentTileBitmap = nextTileBitmap
                }
                currentTileKey = locationKey
                currentTileZoom = tileZoom
                if (
                    isRecording &&
                    stampEnabled &&
                    nextTileBitmap != null &&
                    (recordingTimelineTileKey != locationKey || recordingTimelineTileZoom != tileZoom)
                ) {
                    snapshotBitmap(nextTileBitmap)?.let { timelineTile ->
                        recordingTimelineTileBitmaps += timelineTile
                        recordingTimelineTileBitmap = timelineTile
                        recordingTimelineTileKey = locationKey
                        recordingTimelineTileZoom = tileZoom
                    }
                }
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
        val remappedRotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val remapped = when (targetRotation) {
                            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                                rotationMatrix,
                                SensorManager.AXIS_Y,
                                SensorManager.AXIS_MINUS_X,
                                remappedRotationMatrix
                            )
                            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                                rotationMatrix,
                                SensorManager.AXIS_MINUS_X,
                                SensorManager.AXIS_MINUS_Y,
                                remappedRotationMatrix
                            )
                            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                                rotationMatrix,
                                SensorManager.AXIS_MINUS_Y,
                                SensorManager.AXIS_X,
                                remappedRotationMatrix
                            )
                            else -> false
                        }
                        SensorManager.getOrientation(
                            if (remapped) remappedRotationMatrix else rotationMatrix,
                            orientationAngles
                        )
                        bearing = Math.toDegrees(orientationAngles[0].toDouble()).toFloat().let {
                            if (it < 0f) it + 360f else it
                        }
                    }

                    Sensor.TYPE_ORIENTATION -> bearing = remapBearingForTargetRotation(event.values[0], targetRotation)
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

    LaunchedEffect(cameraProvider, cameraSelector, isVideoMode) {
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

    LaunchedEffect(boundCamera, zoomRatio) {
        val camera = boundCamera ?: return@LaunchedEffect
        val clampedZoom = clampZoomRatio(zoomRatio, minZoomRatio, maxZoomRatio)
        camera.cameraControl.setZoomRatio(clampedZoom)
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
                .pointerInput(controlsEnabled, isRecording, isVideoMode) {
                    if (!controlsEnabled || isRecording) return@pointerInput
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                        onDragEnd = {
                            if (totalDragX < -50f && !isVideoMode) {
                                isVideoMode = true
                            } else if (totalDragX > 50f && isVideoMode) {
                                isVideoMode = false
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            val viewport = previewViewport
            if (stampEnabled && viewport != null) {
                val elevatedOffsetYPx by animateIntAsState(
                    targetValue = if (showSettingsSheet && settingsSheetHeightPx > 0) {
                        (-settingsSheetHeightPx).coerceAtLeast(-viewport.height / 2)
                    } else 0,
                    label = "elevatedOffsetY"
                )
                val previewStamp = remember(
                    liveLocation,
                    liveMovementPath,
                    liveMinimapZoom,
                    customMarkerScale,
                    customFovAngle,
                    customFovLength,
                    liveAddress,
                    noteText,
                    bearing,
                    selectedStatusTag,
                    captureMapNodes,
                    captureMapRoutes
                ) {
                    buildCaptureStamp(
                        timestampMs = System.currentTimeMillis(),
                        location = liveLocation,
                        address = liveAddress,
                        note = noteText,
                        bearingDeg = bearing,
                        mapNodes = captureMapNodes,
                        mapRoutes = captureMapRoutes,
                        movementPath = liveMovementPath,
                        minimapZoom = liveMinimapZoom,
                        markerScale = customMarkerScale,
                        fovAngleDeg = customFovAngle,
                        fovLengthScale = customFovLength,
                        statusTag = selectedStatusTag
                    )
                }
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(viewport.left, viewport.top + elevatedOffsetYPx) }
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

                // Top mask
                if (vpY > 0) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(outW, vpY)
                    )
                }
                // Bottom mask
                if (vpY + vpH < outH) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, vpY + vpH),
                        size = androidx.compose.ui.geometry.Size(outW, outH - (vpY + vpH))
                    )
                }
                // Left mask
                if (vpX > 0) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, vpY),
                        size = androidx.compose.ui.geometry.Size(vpX, vpH)
                    )
                }
                // Right mask
                if (vpX + vpW < outW) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(vpX + vpW, vpY),
                        size = androidx.compose.ui.geometry.Size(outW - (vpX + vpW), vpH)
                    )
                }
            }
        }

        // Thanh điều khiển trên cùng (Top Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(20.dp))
                    .clickable(enabled = controlsEnabled) {
                        if (controlsEnabled) {
                            selectedAspectRatio = when (selectedAspectRatio) {
                                CameraAspectRatio.RATIO_4_3 -> CameraAspectRatio.RATIO_16_9
                                CameraAspectRatio.RATIO_16_9 -> CameraAspectRatio.RATIO_1_1
                                CameraAspectRatio.RATIO_1_1 -> CameraAspectRatio.RATIO_FULL
                                CameraAspectRatio.RATIO_FULL -> CameraAspectRatio.RATIO_4_3
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = selectedAspectRatio.displayName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Stamp",
                        color = if (stampEnabled) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
                    onClick = { if (controlsEnabled) showSettingsSheet = true },
                    enabled = controlsEnabled,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Cài đặt",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        if (!isProcessingVideoStamp && !isFinalizingRecording) {
                            if (isRecording) {
                                dismissAfterRecording = true
                                isFinalizingRecording = true
                                val startElapsedMs = recordingStartElapsedMs
                                val tileBitmap = recordingTimelineTileBitmap
                                if (stampEnabled && startElapsedMs != null && tileBitmap != null) {
                                    recordingTimelineSamples.appendVideoStampTimelineSample(
                                        buildVideoStampTimelineSample(
                                            recordingStartElapsedMs = startElapsedMs,
                                            nowElapsedMs = SystemClock.elapsedRealtime(),
                                            location = liveLocation,
                                            address = liveAddress,
                                            note = noteText,
                                            bearingDeg = bearing,
                                            mapNodes = captureMapNodes,
                                            mapRoutes = captureMapRoutes,
                                            movementPath = liveMovementPath,
                                            minimapZoom = liveMinimapZoom,
                                            markerScale = customMarkerScale,
                                            fovAngleDeg = customFovAngle,
                                            fovLengthScale = customFovLength,
                                            tileBitmap = tileBitmap,
                                            statusTag = selectedStatusTag
                                        )
                                    )
                                }
                                activeRecording?.stop()
                            } else {
                                onDismiss()
                            }
                        }
                    },
                    enabled = !isProcessingVideoStamp && !isFinalizingRecording,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = Color.White)
                }
            }
        }

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
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val controlsLayout = computeCameraControlsLayout(maxHeight.value.roundToInt())
            val compactSpacing = controlsLayout.useCompactSpacing
            val showNoteField = controlsLayout.showNoteField || isKeyboardActive
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = if (compactSpacing) 4.dp else 10.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(if (compactSpacing) 6.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isKeyboardActive) {
                    CameraStatusTagChips(
                        selectedTag = selectedStatusTag,
                        onTagSelected = { if (controlsEnabled) selectedStatusTag = it },
                        enabled = controlsEnabled,
                        tags = statusTags
                    )
                }

                // Trường nhập ghi chú bán trong suốt
                if (showNoteField) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { if (controlsEnabled) noteText = it },
                        enabled = controlsEnabled,
                        placeholder = { Text("Ghi chú (tùy chọn)...", color = Color(0xAAFFFFFF), fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onFocusChanged { isNoteFocused = it.isFocused },
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            isNoteFocused = false
                        }),
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
                }

                if (!isKeyboardActive && controlsLayout.showZoomBar) {
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
                }

                if (!isKeyboardActive) {
                    if (isRecording) {
                        CameraRecordingTimerBadge(durationSeconds = recordingDurationSeconds)
                    } else {
                        CameraModeSelector(
                            isVideoMode = isVideoMode,
                            onModeSelected = { isVideoMode = it },
                            enabled = controlsEnabled
                        )
                    }
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
                                    if (isRecording) {
                                        it.background(Color(0x33FF1744))
                                          .border(2.dp, Color(0xFFFF1744), CircleShape)
                                    } else {
                                        it.background(Color(0x22FFFFFF))
                                          .border(2.dp, Color.White, CircleShape)
                                    }
                                } else {
                                    it.background(Color(0x22FFFFFF))
                                      .border(1.5.dp, Color.White, CircleShape)
                                }
                            }
                            .clickable(
                                enabled = !isProcessingVideoStamp && !isFinalizingRecording,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                focusManager.clearFocus()
                                if (isVideoMode) {
                                    if (isRecording) {
                                        if (!isFinalizingRecording) {
                                            isFinalizingRecording = true
                                            val startElapsedMs = recordingStartElapsedMs
                                            val tileBitmap = recordingTimelineTileBitmap
                                            if (stampEnabled && startElapsedMs != null && tileBitmap != null) {
                                                recordingTimelineSamples.appendVideoStampTimelineSample(
                                                    buildVideoStampTimelineSample(
                                                        recordingStartElapsedMs = startElapsedMs,
                                                        nowElapsedMs = SystemClock.elapsedRealtime(),
                                                        location = liveLocation,
                                                        address = liveAddress,
                                                        note = noteText,
                                                        bearingDeg = bearing,
                                                        mapNodes = captureMapNodes,
                                                        mapRoutes = captureMapRoutes,
                                                        movementPath = liveMovementPath,
                                                        minimapZoom = liveMinimapZoom,
                                                        markerScale = customMarkerScale,
                                                        fovAngleDeg = customFovAngle,
                                                        fovLengthScale = customFovLength,
                                                        tileBitmap = tileBitmap,
                                                        statusTag = selectedStatusTag
                                                    )
                                                )
                                            }
                                            activeRecording?.stop()
                                        }
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
                                            mapNodes = captureMapNodes,
                                            mapRoutes = captureMapRoutes,
                                            movementPath = liveMovementPath,
                                            minimapZoom = liveMinimapZoom,
                                            markerScale = customMarkerScale,
                                            fovAngleDeg = customFovAngle,
                                            fovLengthScale = customFovLength,
                                            statusTag = selectedStatusTag
                                        )
                                        val recordingTileBitmap = snapshotBitmap(currentTileBitmap)
                                        recordingTimelineTileBitmaps.forEach { it.recycle() }
                                        recordingTimelineTileBitmaps.clear()
                                        recordingTimelineTileBitmap = recordingTileBitmap
                                        recordingTimelineTileKey = currentTileKey
                                        recordingTimelineTileZoom = currentTileZoom
                                        recordingTileBitmap?.let { recordingTimelineTileBitmaps += it }
                                        val nextRecordingStartElapsedMs = SystemClock.elapsedRealtime()
                                        recordingStartElapsedMs = nextRecordingStartElapsedMs
                                        recordingTimelineSamples.clear()
                                        if (stampEnabled && recordingTileBitmap != null) {
                                            recordingTimelineSamples.appendVideoStampTimelineSample(
                                                 buildVideoStampTimelineSample(
                                                     recordingStartElapsedMs = nextRecordingStartElapsedMs,
                                                     nowElapsedMs = nextRecordingStartElapsedMs,
                                                     location = liveLocation,
                                                     address = liveAddress,
                                                     note = noteText,
                                                     bearingDeg = bearing,
                                                     mapNodes = captureMapNodes,
                                                     mapRoutes = captureMapRoutes,
                                                     movementPath = liveMovementPath,
                                                     minimapZoom = liveMinimapZoom,
                                                     markerScale = customMarkerScale,
                                                     fovAngleDeg = customFovAngle,
                                                     fovLengthScale = customFovLength,
                                                     statusTag = selectedStatusTag,
                                                     tileBitmap = recordingTileBitmap
                                                 )
                                            )
                                        }
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
                                            folderType = resolveCaptureFolderType(nodeCode, routes),
                                            objectCode = nodeCode,
                                            statusTag = selectedStatusTag
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
                                                    val timelineSnapshot = recordingTimelineSamples.toList()
                                                    if (!event.hasError()) {
                                                         coroutineScope.launch {
                                                             try {
                                                                 postProcessRecordedVideo(
                                                                     videoFile = videoFile,
                                                                     stampEnabled = stampEnabled,
                                                                     stampAtRecordStart = stampAtRecordStart,
                                                                     tileBitmap = recordingTileBitmap,
                                                                     timelineSamples = timelineSnapshot,
                                                                     photoPipelineService = photoPipelineService,
                                                                     setProcessingVideoStamp = { isProcessingVideoStamp = it },
                                                                     onSavePhoto = { file ->
                                                                         onSavePhoto(
                                                                             file,
                                                                             stampAtRecordStart?.statusTag ?: selectedStatusTag,
                                                                             stampAtRecordStart?.note ?: noteText,
                                                                             stampAtRecordStart?.address ?: liveAddress
                                                                         )
                                                                     },
                                                                     onPhotoCaptured = onPhotoCaptured
                                                                 )
                                                                 onDismiss()
                                                             } catch (error: Throwable) {
                                                                 AppLogger.e(error, "camera.overlay.capture.video.failed")
                                                                 runCatching { videoFile.delete() }
                                                                 onDismiss()
                                                             } finally {
                                                                 isFinalizingRecording = false
                                                                 recordingTimelineSamples.clear()
                                                                 recordingStartElapsedMs = null
                                                                 recordingTimelineTileBitmap = null
                                                                 recordingTimelineTileKey = null
                                                                 recordingTimelineTileZoom = PhotoStampRenderer.MINIMAP_MAX_ZOOM
                                                                 recordingTimelineTileBitmaps.forEach { it.recycle() }
                                                                 recordingTimelineTileBitmaps.clear()
                                                             }
                                                         }
                                                    } else {
                                                         isFinalizingRecording = false
                                                         recordingTimelineSamples.clear()
                                                         recordingStartElapsedMs = null
                                                         recordingTimelineTileBitmap = null
                                                         recordingTimelineTileKey = null
                                                         recordingTimelineTileZoom = PhotoStampRenderer.MINIMAP_MAX_ZOOM
                                                         recordingTimelineTileBitmaps.forEach { it.recycle() }
                                                         recordingTimelineTileBitmaps.clear()
                                                         runCatching { videoFile.delete() }
                                                         onDismiss()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val capturedStatusTag = selectedStatusTag
                                    val capturedNote = noteText
                                    val capturedAddress = liveAddress
                                    val capturedStamp = buildCaptureStamp(
                                        timestampMs = System.currentTimeMillis(),
                                        location = liveLocation,
                                        address = liveAddress,
                                        note = noteText,
                                        bearingDeg = bearing,
                                        mapNodes = captureMapNodes,
                                        mapRoutes = captureMapRoutes,
                                        movementPath = liveMovementPath,
                                        minimapZoom = liveMinimapZoom,
                                        markerScale = customMarkerScale,
                                        fovAngleDeg = customFovAngle,
                                        fovLengthScale = customFovLength,
                                        statusTag = capturedStatusTag
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
                                            folderType = resolveCaptureFolderType(nodeCode, routes),
                                            objectCode = nodeCode,
                                            statusTag = capturedStatusTag
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
                                                                if (onSavePhoto(file, capturedStatusTag, capturedNote, capturedAddress)) {
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
                                    .size(if (isRecording) 24.dp else 56.dp)
                                    .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                                    .background(Color(0xFFFF1744))
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
                        .onSizeChanged { settingsSheetHeightPx = it.height }
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mức thu phóng Minimap (Zoom: $customMinimapZoom)",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (customMinimapZoom != PhotoStampRenderer.MINIMAP_MAX_ZOOM || customMarkerScale != 1.0f || customFovAngle != 30.0f || customFovLength != 1.0f) {
                            TextButton(
                                onClick = {
                                    customMinimapZoom = PhotoStampRenderer.MINIMAP_MAX_ZOOM
                                    customMarkerScale = 1.0f
                                    customFovAngle = 30.0f
                                    customFovLength = 1.0f
                                }
                            ) {
                                Text("Khôi phục mặc định", color = Color(0xFF00E5FF), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = customMinimapZoom.toFloat(),
                        onValueChange = { customMinimapZoom = it.toInt() },
                        valueRange = 14f..20f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Kích thước Marker GPS & Điểm GIS (${(customMarkerScale * 100).toInt()}%)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = customMarkerScale,
                        onValueChange = { customMarkerScale = ((it * 20).roundToInt() / 20f).coerceIn(0.5f, 1.5f) },
                        valueRange = 0.5f..1.5f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Chiều rộng góc quét hướng nhìn (${customFovAngle.toInt()}°)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = customFovAngle,
                        onValueChange = { customFovAngle = ((it / 5).roundToInt() * 5f).coerceIn(15f, 90f) },
                        valueRange = 15f..90f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Chiều dài tia nhìn hướng nhìn (${(customFovLength * 100).toInt()}%)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = customFovLength,
                        onValueChange = { customFovLength = ((it * 20).roundToInt() / 20f).coerceIn(0.3f, 1.5f) },
                        valueRange = 0.3f..1.5f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

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

@Composable
internal fun CameraModeSelector(
    isVideoMode: Boolean,
    onModeSelected: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x66000000))
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (!isVideoMode) Color(0xFF00E5FF) else Color.Transparent)
                .clickable(enabled = enabled) { onModeSelected(false) }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ẢNH",
                color = if (!isVideoMode) Color(0xFF060814) else Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = if (!isVideoMode) FontWeight.Bold else FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isVideoMode) Color(0xFF00E5FF) else Color.Transparent)
                .clickable(enabled = enabled) { onModeSelected(true) }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VIDEO",
                color = if (isVideoMode) Color(0xFF060814) else Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

internal fun formatRecordingDuration(durationSeconds: Int): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
internal fun CameraRecordingTimerBadge(
    durationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )
    val timeFormatted = remember(durationSeconds) {
        formatRecordingDuration(durationSeconds)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xCCB00020))
            .border(1.dp, Color(0x66FF1744), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = alpha))
        )
        Text(
            text = "REC $timeFormatted",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
internal fun CameraStatusTagChips(
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tags: List<String> = MediaStatusTags.systemNames
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.forEach { tag ->
            val isSelected = selectedTag == tag
            val activeBorderColor = Color(0xFF00E5FF)
            val inactiveBorderColor = Color(0x33FFFFFF)
            val activeBackgroundColor = Color(0xFF00E5FF)
            val inactiveBackgroundColor = Color(0x660A0D1A)

            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSelected) activeBackgroundColor else inactiveBackgroundColor)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) activeBorderColor else inactiveBorderColor,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable(
                        enabled = enabled,
                        onClick = { onTagSelected(tag) }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFF060814) else Color(0xFF00E5FF)
                            )
                    )
                    Text(
                        text = tag,
                        color = if (isSelected) Color(0xFF060814) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
