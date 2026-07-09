package com.mapsupervision.photo.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.ai.core.AIFacade
import com.mapsupervision.ai.core.PhotoQualityPayload
import com.mapsupervision.ai.core.PhotoQualityResult
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.joinCsvList
import com.mapsupervision.domain.model.resolvedTagCodes
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.photo.location.PhotoLocationProvider
import com.mapsupervision.photo.worker.PhotoPipelineService
import com.mapsupervision.storage.ProjectStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import com.mapsupervision.domain.model.ProjectStorageRef

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PhotoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository,
    private val activeProjectRepository: ActiveProjectRepository,
    private val gisRepository: GisRepository,
    private val projectRepository: ProjectRepository,
    private val projectSyncRepository: ProjectSyncRepository,
    private val locationProvider: PhotoLocationProvider,
    private val photoPipelineService: PhotoPipelineService,
    private val aiFacade: AIFacade,
    private val storageManager: ProjectStorageManager
) : ViewModel() {
    private val _photos = MutableStateFlow<List<SitePhoto>>(emptyList())
    val photos: StateFlow<List<SitePhoto>> = _photos.asStateFlow()
    private val _lastAiPhotoQuality = MutableStateFlow<PhotoQualityResult?>(null)
    val lastAiPhotoQuality: StateFlow<PhotoQualityResult?> = _lastAiPhotoQuality.asStateFlow()
    private val _selectedPhotoForReview = MutableStateFlow<SitePhoto?>(null)
    val selectedPhotoForReview: StateFlow<SitePhoto?> = _selectedPhotoForReview.asStateFlow()
    private val _availableTagOptions = MutableStateFlow<List<String>>(emptyList())
    val availableTagOptions: StateFlow<List<String>> = _availableTagOptions.asStateFlow()
    private var activeProjectIdCache: String? = null
    private var activeProjectSlugCache: String? = null
    private var activeNodeCodesCache: Set<String> = emptySet()
    private var activeRouteCodesCache: Set<String> = emptySet()

    private data class CaptureTarget(
        val objectCode: String,
        val folderType: CaptureFolderType,
        val matchedNodeCode: String? = null,
        val matchedRouteCode: String? = null
    )


    init {
        observeSharedState()
        refresh()
    }

    private fun observeSharedState() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refresh()
            }
        }
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest { event ->
                val activeProjectId = activeProjectRepository.activeProjectId.value
                if (event.projectId == null || event.projectId == activeProjectId) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            activeProjectIdCache = activeId
            val projects = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
            activeProjectSlugCache = projects.firstOrNull { it.id == activeId }?.slug ?: activeId
            val result = photoRepository.byProject(activeId)
            _photos.value = (result as? AppResult.Success)?.data.orEmpty()
            refreshTagOptions(activeId)
            _selectedPhotoForReview.value = _selectedPhotoForReview.value?.let { selected ->
                _photos.value.firstOrNull { it.id == selected.id } ?: selected
            }
        }
    }

    private suspend fun refreshTagOptions(projectId: String) {
        val nodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        val routes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        activeNodeCodesCache = nodes.map { it.code.trim() }.filter { it.isNotBlank() }.toSet()
        activeRouteCodesCache = routes.map { it.code.trim() }.filter { it.isNotBlank() }.toSet()
        _availableTagOptions.value = buildSet {
            addAll(activeNodeCodesCache)
            addAll(activeRouteCodesCache)
        }.toList().sorted()
    }

    fun selectPhotoForReview(photoId: String) {
        _selectedPhotoForReview.value = _photos.value.firstOrNull { it.id == photoId }
    }

    fun clearPhotoReviewSelection() {
        _selectedPhotoForReview.value = null
    }

    fun updateSelectedPhotoTags(tagCodesCsv: String) {
        val current = _selectedPhotoForReview.value ?: return
        val activeId = activeProjectIdCache ?: return
        viewModelScope.launch {
            val nodes = (gisRepository.searchNodes(activeId, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(activeId, "") as? AppResult.Success)?.data.orEmpty()
            val nodeCodes = nodes.map { it.code.trim() }.filter { it.isNotBlank() }.toSet()
            val routeCodes = routes.map { it.code.trim() }.filter { it.isNotBlank() }.toSet()

            val tags = com.mapsupervision.domain.model.parseCsvList(tagCodesCsv)
            val matchedNode = tags.firstOrNull { nodeCodes.contains(it) }
            val matchedRoute = tags.firstOrNull { routeCodes.contains(it) }

            _selectedPhotoForReview.value = current.copy(
                tagCodesCsv = joinCsvList(tags),
                tagCodes = tags,
                matchedNodeCode = matchedNode,
                matchedRouteCode = matchedRoute
            )
        }
    }

    fun updateSelectedPhotoOffsetMinutes(offsetMinutes: Int) {
        val current = _selectedPhotoForReview.value ?: return
        val activeId = activeProjectIdCache ?: return
        val offsetMs = offsetMinutes.toLong() * 60_000L
        val matchedAt = current.capturedAtEpochMs + offsetMs
        viewModelScope.launch {
            val nodes = (gisRepository.searchNodes(activeId, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(activeId, "") as? AppResult.Success)?.data.orEmpty()
            val nodeCodes = nodes.map { it.code.trim() }.filter { it.isNotBlank() }.toSet()
            val routeCodes = routes.map { it.code.trim() }.filter { it.isNotBlank() }.toSet()

            val tags = current.resolvedTagCodes
            val matchedNode = tags.firstOrNull { nodeCodes.contains(it) } ?: current.matchedNodeCode
            val matchedRoute = tags.firstOrNull { routeCodes.contains(it) } ?: current.matchedRouteCode

            _selectedPhotoForReview.value = current.copy(
                matchingTimeOffsetMs = offsetMs,
                matchedAtEpochMs = matchedAt,
                matchedNodeCode = matchedNode,
                matchedRouteCode = matchedRoute
            )
        }
    }

    fun toggleSelectedPhotoTag(tagCode: String) {
        val current = _selectedPhotoForReview.value ?: return
        val normalized = tagCode.trim()
        if (normalized.isBlank()) return
        val nextTags = current.resolvedTagCodes
            .filter { it != normalized }
            .toMutableList()
        if (current.resolvedTagCodes.any { it == normalized }) {
            // tag already present; remove it
        } else {
            nextTags.add(normalized)
        }
        updateSelectedPhotoTags(joinCsvList(nextTags))
    }

    fun saveSelectedPhotoReview() {
        val current = _selectedPhotoForReview.value ?: return
        viewModelScope.launch {
            photoRepository.add(current)
            refresh()
        }
    }

    fun createCaptureFile(objectCode: String): File? = kotlinx.coroutines.runBlocking {
        val activeId = activeProjectIdCache ?: return@runBlocking null
        val activeSlug = activeProjectSlugCache ?: activeId
        val target = resolveCaptureTarget(objectCode)
        val location = locationProvider.lastKnownLocation()
        val locationLabel = if (location.latitude != null && location.longitude != null) {
            "${location.latitude}_${location.longitude}"
        } else null
        photoPipelineService.createCaptureOutputFile(
            storageRef = ProjectStorageRef(activeId, activeSlug),
            capturedAt = System.currentTimeMillis(),
            locationLabel = locationLabel,
            note = null,
            folderType = target.folderType,
            objectCode = target.objectCode
        )
    }

    fun createCaptureVideoFile(objectCode: String): File? = kotlinx.coroutines.runBlocking {
        val activeId = activeProjectIdCache ?: return@runBlocking null
        val activeSlug = activeProjectSlugCache ?: activeId
        val target = resolveCaptureTarget(objectCode)
        val location = locationProvider.lastKnownLocation()
        val locationLabel = if (location.latitude != null && location.longitude != null) {
            "${location.latitude}_${location.longitude}"
        } else null
        photoPipelineService.createCaptureVideoOutputFile(
            storageRef = ProjectStorageRef(activeId, activeSlug),
            capturedAt = System.currentTimeMillis(),
            locationLabel = locationLabel,
            note = null,
            folderType = target.folderType,
            objectCode = target.objectCode
        )
    }

    fun registerCapturedPhoto(file: File, objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val activeSlug = activeProjectSlugCache ?: activeId
            val target = resolveCaptureTarget(objectCode)
            val location = locationProvider.lastKnownLocation()
            withContext(Dispatchers.IO) {
                photoPipelineService.applyWatermark(file, target.objectCode, engineer)
                savePhotoModel(activeId, activeSlug, target, engineer, file, location)
                _lastAiPhotoQuality.value = aiFacade.execute<PhotoQualityResult>(
                    PhotoQualityPayload(
                        objectCode = target.objectCode,
                        engineer = engineer,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        filePath = file.absolutePath
                    )
                ).result
            }
            markProjectChanged(activeId, "photo_registered")
            refresh()
        }
    }

    fun registerCapturedVideo(file: File, durationMs: Long, objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val activeSlug = activeProjectSlugCache ?: activeId
            val target = resolveCaptureTarget(objectCode)
            val location = locationProvider.lastKnownLocation()
            withContext(Dispatchers.IO) {
                saveVideoModel(activeId, activeSlug, target, engineer, file, location, durationMs)
            }
            markProjectChanged(activeId, "video_registered")
            refresh()
        }
    }

    fun addDemoPhoto(objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val activeSlug = activeProjectSlugCache ?: activeId
            val target = resolveCaptureTarget(objectCode)
            val location = locationProvider.lastKnownLocation()
            val locationLabel = if (location.latitude != null && location.longitude != null) {
                "${location.latitude}_${location.longitude}"
            } else null
            withContext(Dispatchers.IO) {
                val file = photoPipelineService.createEmptyPhoto(
                    storageRef = ProjectStorageRef(activeId, activeSlug),
                    capturedAt = System.currentTimeMillis(),
                    locationLabel = locationLabel,
                    note = null,
                    objectCode = target.objectCode,
                    engineer = engineer,
                    folderType = target.folderType
                )
                savePhotoModel(activeId, activeSlug, target, engineer, file, location)
                _lastAiPhotoQuality.value = aiFacade.execute<PhotoQualityResult>(
                    PhotoQualityPayload(
                        objectCode = target.objectCode,
                        engineer = engineer,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        filePath = file.absolutePath
                    )
                ).result
            }
            markProjectChanged(activeId, "demo_photo_added")
            refresh()
        }
    }

    fun importFromGallery(uris: List<Uri>, objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val activeSlug = activeProjectSlugCache ?: activeId
            val target = resolveCaptureTarget(objectCode)
            val location = locationProvider.lastKnownLocation()
            val locationLabel = if (location.latitude != null && location.longitude != null) {
                "${location.latitude}_${location.longitude}"
            } else null
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        val file = photoPipelineService.importFromGallery(
                            storageRef = ProjectStorageRef(activeId, activeSlug),
                            capturedAt = System.currentTimeMillis(),
                            locationLabel = locationLabel,
                            note = null,
                            folderType = target.folderType,
                            objectCode = target.objectCode,
                            sourceUri = uri.toString()
                        )
                        val mimeType = context.contentResolver.getType(uri) ?: ""
                        val isVideo = mimeType.startsWith("video/") || uri.path?.endsWith(".mp4", ignoreCase = true) == true
                        if (isVideo) {
                            var durationMs = 0L
                            val retriever = android.media.MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, uri)
                                val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                durationMs = timeStr?.toLongOrNull() ?: 0L
                            } catch (e: Exception) {
                                AppLogger.e(e, "photo.viewmodel.import.video.duration.fail uri=$uri")
                            } finally {
                                try { retriever.release() } catch (_: Exception) {}
                            }
                            saveVideoModel(activeId, activeSlug, target, engineer, file, location, durationMs)
                        } else {
                            savePhotoModel(activeId, activeSlug, target, engineer, file, location)
                        }
                    }.onFailure { e ->
                        AppLogger.e(e, "photo.viewmodel.import.fail uri=$uri")
                    }
                }
            }
            markProjectChanged(activeId, "gallery_photos_imported")
            refresh()
        }
    }

    private suspend fun markProjectChanged(projectId: String, reason: String) {
        projectRepository.touch(projectId)
        projectSyncRepository.notifyProjectChanged(projectId, reason)
    }

    private suspend fun savePhotoModel(
        projectId: String,
        projectSlug: String,
        target: CaptureTarget,
        engineer: String,
        file: File,
        location: com.mapsupervision.domain.model.PhotoLocationSnapshot
    ) {
        storageManager.scanFile(file)
        val capturedAt = System.currentTimeMillis()
        val locationLabel = if (location.latitude != null && location.longitude != null) {
            "${location.latitude}_${location.longitude}"
        } else null
        val model = SitePhoto(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            objectCode = target.objectCode,
            tagCodesCsv = target.objectCode,
            matchedNodeCode = target.matchedNodeCode,
            matchedRouteCode = target.matchedRouteCode,
            filePath = file.absolutePath,
            thumbnailPath = file.absolutePath,
            latitude = location.latitude,
            longitude = location.longitude,
            locationAccuracyM = location.accuracyM,
            isGpsMocked = location.isMock,
            locationStatus = location.status,
            engineer = engineer,
            capturedAtEpochMs = capturedAt,
            matchedAtEpochMs = capturedAt,
            matchingTimeOffsetMs = 0L,
            address = locationLabel,
            captureNote = null
        )
        val result = photoRepository.add(model)
        if (result is AppResult.Error) {
            runCatching { file.delete() }
            throw IllegalStateException(result.throwable.message ?: "Failed to save photo")
        }
    }

    private suspend fun saveVideoModel(
        projectId: String,
        projectSlug: String,
        target: CaptureTarget,
        engineer: String,
        file: File,
        location: com.mapsupervision.domain.model.PhotoLocationSnapshot,
        durationMs: Long
    ) {
        storageManager.scanFile(file)
        val capturedAt = System.currentTimeMillis()
        val locationLabel = if (location.latitude != null && location.longitude != null) {
            "${location.latitude}_${location.longitude}"
        } else null
        val model = SitePhoto(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            objectCode = target.objectCode,
            tagCodesCsv = target.objectCode,
            matchedNodeCode = target.matchedNodeCode,
            matchedRouteCode = target.matchedRouteCode,
            filePath = file.absolutePath,
            thumbnailPath = file.absolutePath,
            latitude = location.latitude,
            longitude = location.longitude,
            locationAccuracyM = location.accuracyM,
            isGpsMocked = location.isMock,
            locationStatus = location.status,
            engineer = engineer,
            capturedAtEpochMs = capturedAt,
            matchedAtEpochMs = capturedAt,
            matchingTimeOffsetMs = 0L,
            mediaType = com.mapsupervision.domain.model.MediaType.VIDEO,
            mimeType = "video/mp4",
            durationMs = durationMs,
            address = locationLabel,
            captureNote = null
        )
        val result = photoRepository.add(model)
        if (result is AppResult.Error) {
            runCatching { file.delete() }
            throw IllegalStateException(result.throwable.message ?: "Failed to save video")
        }
    }

    private fun resolveCaptureTarget(objectCode: String): CaptureTarget {
        val normalized = objectCode.trim()
        return when {
            normalized.isNotBlank() && activeRouteCodesCache.contains(normalized) -> CaptureTarget(
                objectCode = normalized,
                folderType = CaptureFolderType.ROUTE,
                matchedRouteCode = normalized
            )
            else -> CaptureTarget(
                objectCode = normalized,
                folderType = CaptureFolderType.NODE,
                matchedNodeCode = normalized
            )
        }
    }
}

