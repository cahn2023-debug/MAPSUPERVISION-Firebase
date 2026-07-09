package com.mapsupervision.photo.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.extensions.ExtensionMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapsupervision.domain.model.joinCsvList
import com.mapsupervision.domain.model.resolvedTagCodes
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoScreen(viewModel: PhotoViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsState()
    val aiQuality by viewModel.lastAiPhotoQuality.collectAsState()
    val selectedForReview by viewModel.selectedPhotoForReview.collectAsState()
    val currentAiQuality = aiQuality
    val currentSelectedForReview = selectedForReview
    var objectCode by remember { mutableStateOf("NODE") }
    var engineer by remember { mutableStateOf("Engineer") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromGallery(uris, objectCode, engineer)
    }
    val availableTagOptions by viewModel.availableTagOptions.collectAsState()

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quan ly hinh anh", style = MaterialTheme.typography.titleLarge)
        if (currentAiQuality != null) {
            Text(
                "AI chat luong anh: ${currentAiQuality.score}/100 - ${currentAiQuality.recommendation}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = objectCode, onValueChange = { objectCode = it }, label = { Text("Doi tuong") })
            OutlinedTextField(value = engineer, onValueChange = { engineer = it }, label = { Text("Ky su") })
        }

        if (!hasCameraPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Cap quyen Camera") }
        } else {
            CameraXCaptureView(
                createCaptureFile = { viewModel.createCaptureFile(objectCode) },
                onCaptured = { file -> viewModel.registerCapturedPhoto(file, objectCode, engineer) },
                createCaptureVideoFile = { viewModel.createCaptureVideoFile(objectCode) },
                onVideoCaptured = { file, duration -> viewModel.registerCapturedVideo(file, duration, objectCode, engineer) },
                hasAudioPermission = hasAudioPermission,
                requestAudioPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!hasLocationPermission) {
                Button(onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) { Text("Cap quyen Vi tri") }
            }
            Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Nhap tu Thu vien") }
            Button(onClick = { viewModel.addDemoPhoto(objectCode, engineer) }) { Text("Them Demo") }
            Button(onClick = { viewModel.refresh() }) { Text("Lam moi") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = statusFilter == "ALL",
                onClick = { statusFilter = "ALL" },
                label = { Text("Tất cả") }
            )
            FilterChip(
                selected = statusFilter == "MATCHED",
                onClick = { statusFilter = "MATCHED" },
                label = { Text("Khớp") }
            )
            FilterChip(
                selected = statusFilter == "UNMATCHED",
                onClick = { statusFilter = "UNMATCHED" },
                label = { Text("Chưa khớp") }
            )
        }

        val matchedPhotos = photos.filter { it.matchedNodeCode != null || it.matchedRouteCode != null || it.resolvedTagCodes.isNotEmpty() }
        val unmatchedPhotos = photos.filterNot { it.matchedNodeCode != null || it.matchedRouteCode != null || it.resolvedTagCodes.isNotEmpty() }
        val visiblePhotos = when (statusFilter) {
            "MATCHED" -> matchedPhotos
            "UNMATCHED" -> unmatchedPhotos
            else -> photos
        }

        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ảnh đã khớp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (matchedPhotos.isEmpty()) {
                    Text("Chưa có ảnh khớp tag/thời gian.", style = MaterialTheme.typography.bodySmall)
                } else {
                    matchedPhotos.take(10).forEach { photo ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            MatchBadge(photo = photo)
                            Text("${photo.objectCode} | tag=${joinCsvList(photo.resolvedTagCodes).ifBlank { "-" }} | offset=${photo.matchingTimeOffsetMs / 60000}m", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ảnh chưa khớp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                if (unmatchedPhotos.isEmpty()) {
                    Text("Không có ảnh chưa khớp.", style = MaterialTheme.typography.bodySmall)
                } else {
                    unmatchedPhotos.take(10).forEach { photo ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            MatchBadge(photo = photo)
                            Text("${photo.objectCode} | ${photo.filePath}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visiblePhotos) { photo ->
                ElevatedCard(
                    onClick = { viewModel.selectPhotoForReview(photo.id) },
                    colors = CardDefaults.elevatedCardColors(containerColor = if (photo.id == selectedForReview?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            MatchBadge(photo = photo)
                            Text("${photo.objectCode} | ${photo.filePath}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("tag=${joinCsvList(photo.resolvedTagCodes).ifBlank { "-" }} | offset=${photo.matchingTimeOffsetMs / 60000}m", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (currentSelectedForReview != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearPhotoReviewSelection() },
            title = { Text("Chỉnh ảnh đối chiếu", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val selectedPhoto = currentSelectedForReview
                    Text("Ảnh: ${selectedPhoto.objectCode}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var offsetText by remember(selectedPhoto.id, selectedPhoto.matchingTimeOffsetMs) { mutableStateOf((selectedPhoto.matchingTimeOffsetMs / 60000).toString()) }
                    Text("Chọn node/tuyến", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableTagOptions.forEach { tag ->
                            val selected = selectedPhoto.resolvedTagCodes.any { it == tag }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleSelectedPhotoTag(tag) },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Text(
                        text = "Đã chọn: " + (joinCsvList(selectedPhoto.resolvedTagCodes).ifBlank { "Chưa chọn" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = {
                            offsetText = it
                            val minutes = it.toIntOrNull() ?: 0
                            viewModel.updateSelectedPhotoOffsetMinutes(minutes)
                        },
                        label = { Text("Offset thời gian (phút)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Giờ khớp: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(selectedPhoto.matchedAtEpochMs.takeIf { it > 0L } ?: selectedPhoto.capturedAtEpochMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveSelectedPhotoReview()
                    viewModel.clearPhotoReviewSelection()
                }) { Text("Lưu") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.clearPhotoReviewSelection() }) { Text("Hủy") }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .wrapContentHeight()
                .navigationBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun MatchBadge(photo: com.mapsupervision.domain.model.SitePhoto) {
    val isMatched = photo.matchedNodeCode != null || photo.matchedRouteCode != null || photo.resolvedTagCodes.isNotEmpty()
    val text = if (isMatched) "Đã khớp" else "Chưa khớp"
    val bg = if (isMatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    val fg = if (isMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
private fun CameraXCaptureView(
    createCaptureFile: () -> File?,
    onCaptured: (File) -> Unit,
    createCaptureVideoFile: () -> File?,
    onVideoCaptured: (File, Long) -> Unit,
    hasAudioPermission: Boolean,
    requestAudioPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }
    var activeExtensionMode by remember { mutableStateOf(ExtensionMode.NONE) }
    var previousExtensionMode by remember { mutableStateOf(ExtensionMode.NONE) }

    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    var targetRotation by remember { mutableStateOf(Surface.ROTATION_0) }

    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                cameraProvider = cameraProviderFuture.get()
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching {
                cameraProvider?.unbindAll()
            }
        }
    }

    LaunchedEffect(cameraProvider) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val managerFuture = ExtensionsManager.getInstanceAsync(context, provider)
        managerFuture.addListener({
            runCatching {
                extensionsManager = managerFuture.get()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(context, previewView, preview, imageCapture) {
        targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        preview.targetRotation = targetRotation
        imageCapture.targetRotation = targetRotation

        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val updatedRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (updatedRotation != targetRotation) {
                    targetRotation = updatedRotation
                    preview.targetRotation = updatedRotation
                    imageCapture.targetRotation = updatedRotation
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose { orientationListener.disable() }
    }

    val cameraSelector = remember(activeExtensionMode, isRecording, extensionsManager) {
        val baseSelector = CameraSelector.DEFAULT_BACK_CAMERA
        if (isRecording || extensionsManager == null || activeExtensionMode == ExtensionMode.NONE) {
            baseSelector
        } else {
            if (extensionsManager!!.isExtensionAvailable(baseSelector, activeExtensionMode)) {
                extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, activeExtensionMode)
            } else {
                baseSelector
            }
        }
    }

    LaunchedEffect(cameraProvider, cameraSelector, isRecording) {
        val provider = cameraProvider ?: return@LaunchedEffect
        provider.unbindAll()
        preview.surfaceProvider = previewView.surfaceProvider
        try {
            if (isRecording) {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } else {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )

        // Extension Selection Row
        Text("Chế độ Camera (Extensions):", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val modes = listOf(
                "Thường" to ExtensionMode.NONE,
                "HDR" to ExtensionMode.HDR,
                "Đêm" to ExtensionMode.NIGHT,
                "Chân dung" to ExtensionMode.BOKEH,
                "Làm mịn" to ExtensionMode.FACE_RETOUCH
            )
            modes.forEach { (name, mode) ->
                val isSupported = if (extensionsManager != null) {
                    extensionsManager!!.isExtensionAvailable(CameraSelector.DEFAULT_BACK_CAMERA, mode) || mode == ExtensionMode.NONE
                } else {
                    mode == ExtensionMode.NONE
                }
                FilterChip(
                    selected = activeExtensionMode == mode,
                    onClick = {
                        if (isSupported) {
                            activeExtensionMode = mode
                        }
                    },
                    label = { Text(name) },
                    enabled = isSupported && !isRecording,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // Action controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isRecording) {
                // Button 1: Chụp ảnh
                Button(
                    onClick = {
                        val file = createCaptureFile() ?: return@Button
                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.targetRotation = targetRotation
                        imageCapture.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onCaptured(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Chụp ảnh")
                }

                // Button 2: Quay video
                Button(
                    onClick = {
                        if (!hasAudioPermission) {
                            requestAudioPermission()
                            return@Button
                        }
                        val file = createCaptureVideoFile() ?: return@Button
                        recordingFile = file
                        val outputOptions = FileOutputOptions.Builder(file).build()
                        var pending = recorder.prepareRecording(context, outputOptions)
                        if (hasAudioPermission) {
                            pending = pending.withAudioEnabled()
                        }
                        // Save current extension mode, then reset to NONE for recording compatibility
                        previousExtensionMode = activeExtensionMode
                        activeExtensionMode = ExtensionMode.NONE

                        recordingStartTime = System.currentTimeMillis()
                        val recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> {
                                    isRecording = true
                                    isPaused = false
                                }
                                is VideoRecordEvent.Pause -> {
                                    isPaused = true
                                }
                                is VideoRecordEvent.Resume -> {
                                    isPaused = false
                                }
                                is VideoRecordEvent.Finalize -> {
                                    isRecording = false
                                    isPaused = false
                                    activeRecording = null
                                    val duration = System.currentTimeMillis() - recordingStartTime
                                    if (event.hasError()) {
                                        recordingFile?.delete()
                                    } else {
                                        recordingFile?.let { onVideoCaptured(it, duration) }
                                    }
                                    recordingFile = null
                                    // Restore previous extension mode
                                    activeExtensionMode = previousExtensionMode
                                }
                            }
                        }
                        activeRecording = recording
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Quay video")
                }
            } else {
                // Recording active controls
                Button(
                    onClick = {
                        activeRecording?.stop()
                    },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Dừng quay")
                }

                if (!isPaused) {
                    Button(
                        onClick = {
                            activeRecording?.pause()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tạm dừng")
                    }
                } else {
                    Button(
                        onClick = {
                            activeRecording?.resume()
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Tiếp tục")
                    }
                }
            }
        }
    }
}
