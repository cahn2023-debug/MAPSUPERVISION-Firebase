package com.mapsupervision.project.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.*
import com.mapsupervision.domain.model.joinCsvList
import com.mapsupervision.domain.model.parseCsvList
import com.mapsupervision.domain.model.resolvedAppliedNodeIds
import com.mapsupervision.domain.model.resolvedLinkedPhotoIds
import com.mapsupervision.domain.model.resolvedTagCodes
import com.mapsupervision.domain.repository.*
import com.mapsupervision.storage.ProjectPackageService
import com.mapsupervision.storage.ProjectStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val activeProjectRepository: ActiveProjectRepository,
    private val importedFileRepository: ImportedFileRepository,
    private val importRepository: ImportRepository,
    private val projectPackageService: ProjectPackageService,
    private val storageManager: ProjectStorageManager,
    private val gisRepository: GisRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val materialProgressRepository: MaterialProgressRepository,
    private val photoRepository: PhotoRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val progressRepository: ProgressRepository,
    private val projectSyncRepository: ProjectSyncRepository,
    private val firebaseSyncRepository: FirebaseSyncRepository,
    private val firebaseAccessRepository: FirebaseAccessRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    init {
        observeActiveProject()
        observeProjectSync()
        observeFirebaseAccess()
        refresh()
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.debounce(250).collectLatest {
                refresh()
            }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.debounce(250).collectLatest {
                refresh()
            }
        }
    }

    private fun observeFirebaseAccess() {
        viewModelScope.launch {
            firebaseAccessRepository.accessState.debounce(250).collectLatest {
                refresh()
                loadCatalog()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _uiState.value
            var activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
            val allProjects = (projectRepository.list(false) as? AppResult.Success)?.data.orEmpty()
            val accessState = firebaseAccessRepository.accessState.value
            val projects = resolveVisibleProjects(allProjects, accessState)

            if ((activeId.isNullOrBlank() || projects.none { it.id == activeId }) && projects.isNotEmpty()) {
                val defaultProjectId = projects.first().id
                when (activeProjectRepository.setActive(defaultProjectId)) {
                    is AppResult.Success -> activeId = defaultProjectId
                    is AppResult.Error -> {}
                }
            } else if (!activeId.isNullOrBlank() && projects.none { it.id == activeId }) {
                when (activeProjectRepository.setActive("")) {
                    is AppResult.Success -> activeId = null
                    is AppResult.Error -> {}
                }
            }
            val imported = if (activeId != null) {
                (importedFileRepository.byProject(activeId) as? AppResult.Success)?.data.orEmpty()
            } else emptyList()
            _uiState.value = current.copy(projects = projects, activeProjectId = activeId, importedFiles = imported)
        }
    }

    fun createProject(name: String, customPath: String? = null) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(message = "Tên dự án không được để trống")
                return@launch
            }
            val created = projectRepository.create(name, customPath)
            if (created is AppResult.Success) {
                when (val setActive = activeProjectRepository.setActive(created.data.id)) {
                    is AppResult.Success -> {
                        _uiState.value = _uiState.value.copy(message = "Đã tạo và mở dự án: ${created.data.name}")
                    }
                    is AppResult.Error -> {
                        _uiState.value = _uiState.value.copy(message = "Tạo dự án thành công nhưng không thể kích hoạt: ${setActive.throwable.message}")
                    }
                }
            } else if (created is AppResult.Error) {
                _uiState.value = _uiState.value.copy(message = "Không tạo được dự án: ${created.throwable.message}")
            }
            refresh()
        }
    }

    fun switchProject(projectId: String) {
        viewModelScope.launch {
            when (val setActive = activeProjectRepository.setActive(projectId)) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(message = "Đã mở dự án")
                is AppResult.Error -> _uiState.value = _uiState.value.copy(message = "Không mở được dự án: ${setActive.throwable.message}")
            }
            refresh()
        }
    }

    fun cloneProject(sourceProjectId: String, newName: String) {
        viewModelScope.launch {
            projectRepository.clone(sourceProjectId, newName)
            projectSyncRepository.notifyProjectChanged(null, "project_cloned")
            refresh()
        }
    }

    fun archiveProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.archive(projectId)
            projectSyncRepository.notifyProjectChanged(projectId, "project_archived")
            refresh()
        }
    }

    fun requestPermanentDeletion(
        projectId: String,
        typedIdentity: String,
        reauthPassword: String,
        confirmPendingOutbox: Boolean
    ) {
        viewModelScope.launch {
            val project = _uiState.value.projects.firstOrNull { it.id == projectId }
            val session = firebaseAccessRepository.accessState.value.session
            val canDelete = session?.isAdmin == true || session?.let {
                (firebaseAccessRepository.projectCreatorUid(projectId) as? AppResult.Success)?.data == it.uid
            } == true
            when {
                project == null -> _uiState.value = _uiState.value.copy(message = "Không tìm thấy project cục bộ")
                !canDelete -> _uiState.value = _uiState.value.copy(message = "Chỉ creator hoặc super-admin được xóa project")
                project.id == (activeProjectRepository.getActive() as? AppResult.Success)?.data -> _uiState.value = _uiState.value.copy(message = "Hãy chuyển sang project khác trước khi xóa")
                typedIdentity.trim() != project.name && typedIdentity.trim() != project.slug -> _uiState.value = _uiState.value.copy(message = "Tên hoặc mã project không khớp")
                reauthPassword.isBlank() -> _uiState.value = _uiState.value.copy(message = "Cần xác thực lại trước khi xóa")
                else -> {
                    val pending = (projectRepository.pendingDeletionWork(projectId) as? AppResult.Success)?.data ?: 0
                    if (pending > 0 && !confirmPendingOutbox) {
                        _uiState.value = _uiState.value.copy(message = "Project còn $pending thay đổi chưa đồng bộ; hãy xác nhận thêm")
                    } else when (val auth = firebaseAccessRepository.reauthenticate(reauthPassword)) {
                        is AppResult.Error -> _uiState.value = _uiState.value.copy(message = "Xác thực lại thất bại: ${auth.throwable.message}")
                        is AppResult.Success -> {
                            val requestId = project.deletionRequestId ?: UUID.randomUUID().toString()
                            val localRequest = projectRepository.requestDeletion(projectId, requestId)
                            if (localRequest is AppResult.Error) {
                                _uiState.value = _uiState.value.copy(message = "Không thể khóa project cục bộ: ${localRequest.throwable.message}")
                                refresh()
                                return@launch
                            }
                            if (localRequest is AppResult.Success &&
                                localRequest.data in setOf(ProjectDeletionState.DELETED, ProjectDeletionState.CLOUD_DECISION_PENDING)
                            ) {
                                _uiState.value = _uiState.value.copy(
                                    message = "Đã xóa dự án khỏi máy; dữ liệu Cloud được bảo toàn."
                                )
                                refresh()
                                return@launch
                            }
                            when (val result = firebaseSyncRepository.requestProjectDeletion(projectId, requestId, typedIdentity.trim(), pending, confirmPendingOutbox)) {
                                is AppResult.Success -> {
                                    if (result.data == ProjectDeletionState.DELETED) {
                                        projectRepository.markCloudDeletionCompleted(projectId, requestId)
                                        projectRepository.completeLocalDeletion(projectId, requestId)
                                    }
                                    _uiState.value = _uiState.value.copy(message = "Project đang được xóa (${result.data})")
                                }
                                is AppResult.Error -> {
                                    if (result.throwable.hasDeletionInProgress()) {
                                        _uiState.value = _uiState.value.copy(message = "Project đang được xóa, vui lòng chờ worker hoàn tất")
                                    } else {
                                        projectRepository.markDeletionFailed(projectId, requestId, "CLOUD_DELETE_REQUEST_FAILED")
                                        _uiState.value = _uiState.value.copy(message = "Không thể bắt đầu xóa: ${result.throwable.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            refresh()
        }
    }

    fun retryPermanentDeletion(projectId: String, typedIdentity: String, reauthPassword: String) =
        requestPermanentDeletion(projectId, typedIdentity, reauthPassword, confirmPendingOutbox = true)

    fun decideCloudDeletion(projectId: String, retainCloud: Boolean) {
        viewModelScope.launch {
            val project = _uiState.value.projects.firstOrNull { it.id == projectId }
            if (project == null) {
                _uiState.value = _uiState.value.copy(message = "Không tìm thấy project local")
                return@launch
            }
            val requestId = (project.cloudDecisionRequestId ?: project.deletionRequestId)?.trim()?.ifBlank { null }
                ?: java.util.UUID.randomUUID().toString()
            if (retainCloud && project.deletionState in setOf(
                    ProjectDeletionState.CLOUD_RETAINED,
                    ProjectDeletionState.RESTORE_PENDING
                )) {
                when (val restore = firebaseSyncRepository.pullChanges(projectId, sinceEpochMs = 0L)) {
                    is AppResult.Success -> {
                        projectRepository.markRestoreCompleted(projectId, requestId)
                        _uiState.value = _uiState.value.copy(message = "Đã retry và khôi phục project local")
                    }
                    is AppResult.Error -> {
                        projectRepository.markRestorePending(projectId, requestId, "RESTORE_FAILED")
                        _uiState.value = _uiState.value.copy(message = "Khôi phục local vẫn thất bại; hãy retry lại")
                    }
                }
                refresh()
                return@launch
            }
            val typedIdentity = project.name.ifBlank { project.slug.ifBlank { project.id } }
            when (val result = firebaseSyncRepository.decideProjectCloudDeletion(
                projectId = projectId,
                requestId = requestId,
                decision = if (retainCloud) "RETAIN" else "DELETE",
                typedIdentity = typedIdentity
            )) {
                is AppResult.Error -> {
                    val errorMsg = result.throwable.message.orEmpty()
                    val isNotFound = errorMsg.contains("404") ||
                        errorMsg.contains("NOT_FOUND", ignoreCase = true) ||
                        errorMsg.contains("Project not found", ignoreCase = true)
                    if (!retainCloud && isNotFound) {
                        projectRepository.forcePurgeLocalProject(projectId)
                        _uiState.value = _uiState.value.copy(
                            message = "Dự án không tồn tại trên Cloud. Đã dọn dẹp xong bản local."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            message = "Không thể ghi quyết định Cloud: $errorMsg"
                        )
                    }
                }
                is AppResult.Success -> {
                    if (retainCloud && result.data == ProjectDeletionState.CLOUD_RETAINED) {
                        projectRepository.markCloudRetained(projectId, requestId)
                        when (val restore = firebaseSyncRepository.pullChanges(projectId, sinceEpochMs = 0L)) {
                            is AppResult.Success -> {
                                projectRepository.markRestoreCompleted(projectId, requestId)
                                _uiState.value = _uiState.value.copy(message = "Đã giữ Cloud và khôi phục project local")
                            }
                            is AppResult.Error -> {
                                projectRepository.markRestorePending(projectId, requestId, "RESTORE_FAILED")
                                _uiState.value = _uiState.value.copy(message = "Đã giữ Cloud; khôi phục local đang chờ retry")
                            }
                        }
                    } else if (!retainCloud && result.data == ProjectDeletionState.DELETING) {
                        projectRepository.markCloudDeletionStarted(projectId, requestId)
                        when (val deletion = firebaseSyncRepository.requestProjectDeletion(
                            projectId,
                            requestId,
                            project.name,
                            pendingOutboxCount = 0,
                            confirmPendingOutbox = true
                        )) {
                            is AppResult.Success -> {
                                if (deletion.data == ProjectDeletionState.DELETED) {
                                    projectRepository.markCloudDeletionCompleted(projectId, requestId)
                                    projectRepository.completeLocalDeletion(projectId, requestId)
                                    _uiState.value = _uiState.value.copy(message = "Đã xóa dữ liệu Cloud và local")
                                } else {
                                    _uiState.value = _uiState.value.copy(message = "Đang xóa dữ liệu Cloud (${deletion.data})")
                                }
                            }
                            is AppResult.Error -> {
                                val delMsg = deletion.throwable.message.orEmpty()
                                val isNotFound = delMsg.contains("404") || delMsg.contains("NOT_FOUND", ignoreCase = true)
                                if (isNotFound) {
                                    projectRepository.forcePurgeLocalProject(projectId)
                                    _uiState.value = _uiState.value.copy(message = "Đã xóa dữ liệu local")
                                } else {
                                    projectRepository.markDeletionFailed(projectId, requestId, "CLOUD_DELETE_REQUEST_FAILED")
                                    _uiState.value = _uiState.value.copy(message = "Xóa Cloud thất bại; có thể retry hoặc chọn xóa cục bộ")
                                }
                            }
                        }
                    }
                }
            }
            refresh()
        }
    }

    fun forceDeleteLocalProject(projectId: String) {
        viewModelScope.launch {
            val project = _uiState.value.projects.firstOrNull { it.id == projectId }
            if (project == null) {
                _uiState.value = _uiState.value.copy(message = "Không tìm thấy project local")
                return@launch
            }
            when (val result = projectRepository.forcePurgeLocalProject(projectId)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(message = "Đã xóa hoàn toàn dự án khỏi thiết bị")
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(message = "Không thể dọn dẹp local: ${result.throwable.message}")
                }
            }
            refresh()
        }
    }

    fun acknowledgeRemoteDeletion(projectId: String, deleteLocal: Boolean) {
        viewModelScope.launch {
            when (val result = projectRepository.acknowledgeRemoteDeletion(projectId, deleteLocal)) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(message = if (deleteLocal) "Đã xóa bản local" else "Giữ project ở chế độ chỉ đọc")
                is AppResult.Error -> _uiState.value = _uiState.value.copy(message = "Không thể xử lý tombstone: ${result.throwable.message}")
            }
            refresh()
        }
    }

    fun updateProjectStoragePath(projectId: String, newPath: String) {
        viewModelScope.launch {
            when (val res = projectRepository.updateStoragePath(projectId, newPath)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(message = "Đã cập nhật vị trí lưu dữ liệu")
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(message = "Không thể cập nhật vị trí: ${res.throwable.message}")
                }
            }
            refresh()
        }
    }

    fun updateProjectMediaStorage(projectId: String, folderInput: String) {
        viewModelScope.launch {
            val normalized = runCatching { normalizeGoogleDriveFolderInput(folderInput) }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(message = error.message ?: "Google Drive folder khong hop le")
                return@launch
            }
            when (val res = projectRepository.updateMediaStorage(projectId, normalized.first, normalized.second)) {
                is AppResult.Success -> {
                    projectSyncRepository.notifyProjectChanged(projectId, "project_media_storage_updated")
                    _uiState.value = _uiState.value.copy(message = "Da cap nhat thu muc Google Drive media")
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(message = "Khong the cap nhat thu muc media: ${res.throwable.message}")
                }
            }
            refresh()
        }
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            _uiState.value = _uiState.value.copy(importMessage = "Đang import ${uris.size} file...")

            var importedCount = 0
            uris.forEach { uri ->
                runCatching {
                    val draft = importRepository.importFile(projectId, uri.toString())
                    val file = ImportedFile(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        fileName = draft.fileName,
                        fileType = draft.fileType,
                        storedPath = draft.storedPath,
                        summary = draft.summary,
                        importedAtEpochMs = System.currentTimeMillis()
                    )
                    importedFileRepository.upsert(file)
                    importedCount++
                }
            }

            _uiState.value = _uiState.value.copy(importMessage = "Đã import $importedCount/${uris.size} file")
            projectRepository.touch(projectId)
            projectSyncRepository.notifyProjectChanged(projectId, "project_files_imported")
            refresh()
        }
    }

    fun exportProject(context: Context, project: Project) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = "Đang xuất dự án ${project.name}...")

            val nodes = (gisRepository.searchNodes(project.id, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(project.id, "") as? AppResult.Success)?.data.orEmpty()
            val notes = (noteRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val tasks = (taskRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val materialProgress = (materialProgressRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val dailyLogs = (dailyLogRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val importedFiles = (importedFileRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val photos = (photoRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val progress = (progressRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()

            val json = JSONObject().apply {
                put("metadataVersion", project.metadataVersion)
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("updatedAtEpochMs", project.updatedAtEpochMs)
                put("project", JSONObject().apply {
                    put("id", project.id)
                    put("name", project.name)
                    put("slug", project.slug)
                    put("isArchived", project.isArchived)
                    put("createdAtEpochMs", project.createdAtEpochMs)
                    put("metadataVersion", project.metadataVersion)
                    put("updatedAtEpochMs", project.updatedAtEpochMs)
                    put("storageMode", project.storageMode.name)
                    put("projectDbPath", project.projectDbPath)
                    put("mediaStorageProvider", project.mediaStorageProvider)
                    put("mediaStorageFolderId", project.mediaStorageFolderId)
                    put("mediaStorageFolderUrl", project.mediaStorageFolderUrl)
                    put("mediaStorageUpdatedAtEpochMs", project.mediaStorageUpdatedAtEpochMs)
                    put("cloudDataConfirmed", project.cloudDataConfirmed)
                    put("cloudDecisionRequestId", project.cloudDecisionRequestId)
                    put("localDeletionErrorCode", project.localDeletionErrorCode)
                })
                put("nodes", JSONArray().apply {
                    nodes.forEach { n ->
                        put(JSONObject().apply {
                            put("id", n.id)
                            put("projectId", n.projectId)
                            put("code", n.code)
                            put("contractor", n.contractor)
                            put("latitude", n.latitude)
                            put("longitude", n.longitude)
                            put("mapNumberLabel", n.mapNumberLabel)
                            put("workVolumeSummary", n.workVolumeSummary)
                            put("importedFileId", n.importedFileId)
                        })
                    }
                })
                put("routes", JSONArray().apply {
                    routes.forEach { r ->
                        put(JSONObject().apply {
                            put("id", r.id)
                            put("projectId", r.projectId)
                            put("code", r.code)
                            put("contractor", r.contractor)
                            put("startNodeCode", r.startNodeCode)
                            put("endNodeCode", r.endNodeCode)
                            put("importedFileId", r.importedFileId)
                        })
                    }
                })
                put("notes", JSONArray().apply {
                    notes.forEach { nt ->
                        put(JSONObject().apply {
                            put("id", nt.id)
                            put("projectId", nt.projectId)
                            put("objectCode", nt.objectCode)
                            put("content", nt.content)
                            put("createdAtEpochMs", nt.createdAtEpochMs)
                        })
                    }
                })
                put("tasks", JSONArray().apply {
                    tasks.forEach { t ->
                        put(JSONObject().apply {
                            put("id", t.id)
                            put("projectId", t.projectId)
                            put("objectCode", t.objectCode)
                            put("title", t.title)
                            put("description", t.description)
                            put("status", t.status.name)
                            put("createdAtEpochMs", t.createdAtEpochMs)
                            put("completedAtEpochMs", t.completedAtEpochMs ?: JSONObject.NULL)
                        })
                    }
                })
                put("materialProgress", JSONArray().apply {
                    materialProgress.forEach { mp ->
                        put(JSONObject().apply {
                            put("id", mp.id)
                            put("projectId", mp.projectId)
                            put("nodeCode", mp.nodeCode)
                            put("workName", mp.workName)
                            put("plannedQty", mp.plannedQty)
                            put("actualQty", mp.actualQty)
                            put("updatedAtEpochMs", mp.updatedAtEpochMs)
                            put("unit", mp.unit)
                        })
                    }
                })
                put("dailyLogs", JSONArray().apply {
                    dailyLogs.forEach { dl ->
                        put(JSONObject().apply {
                            put("id", dl.id)
                            put("projectId", dl.projectId)
                            put("workItem", dl.workItem)
                            put("manpower", dl.manpower)
                            put("note", dl.note)
                            put("createdAtEpochMs", dl.createdAtEpochMs)
                            put("weather", dl.weather)
                            put("temperature", dl.temperature)
                            put("nodeCode", dl.nodeCode ?: JSONObject.NULL)
                            put("routeCode", dl.routeCode ?: JSONObject.NULL)
                            put("dateEpochDay", dl.dateEpochDay)
                            put("volume", dl.volume)
                            put("unit", dl.unit)
                            put("categoryName", dl.categoryName)
                            put("batchGroupId", dl.batchGroupId)
                            put("appliedNodeCodesCsv", dl.appliedNodeCodesCsv)
                            put("linkedPhotoIdsCsv", dl.linkedPhotoIdsCsv)
                            put("appliedNodeIds", JSONArray(dl.resolvedAppliedNodeIds))
                            put("linkedPhotoIds", JSONArray(dl.resolvedLinkedPhotoIds))
                            put("photoMatchOffsetMinutes", dl.photoMatchOffsetMinutes)
                            put("updatedAtEpochMs", dl.updatedAtEpochMs)
                            put("isDeleted", dl.isDeleted)
                            put("deletedAtEpochMs", dl.deletedAtEpochMs ?: JSONObject.NULL)
                        })
                    }
                })
                put("importedFiles", JSONArray().apply {
                    importedFiles.forEach { inf ->
                        put(JSONObject().apply {
                            put("id", inf.id)
                            put("projectId", inf.projectId)
                            put("fileName", inf.fileName)
                            put("fileType", inf.fileType)
                            put("storedPath", inf.storedPath)
                            put("summary", inf.summary)
                            put("importedAtEpochMs", inf.importedAtEpochMs)
                        })
                    }
                })
                put("photos", JSONArray().apply {
                    photos.forEach { ph ->
                        put(JSONObject().apply {
                            put("id", ph.id)
                            put("projectId", ph.projectId)
                            put("objectCode", ph.objectCode)
                            put("tagCodesCsv", ph.tagCodesCsv)
                            put("matchedNodeCode", ph.matchedNodeCode ?: JSONObject.NULL)
                            put("matchedRouteCode", ph.matchedRouteCode ?: JSONObject.NULL)
                            put("filePath", ph.filePath)
                            put("thumbnailPath", ph.thumbnailPath)
                            put("latitude", ph.latitude ?: JSONObject.NULL)
                            put("longitude", ph.longitude ?: JSONObject.NULL)
                            put("locationAccuracyM", ph.locationAccuracyM ?: JSONObject.NULL)
                            put("isGpsMocked", ph.isGpsMocked)
                            put("locationStatus", ph.locationStatus.name)
                            put("engineer", ph.engineer)
                            put("capturedAtEpochMs", ph.capturedAtEpochMs)
                            put("matchedAtEpochMs", ph.matchedAtEpochMs)
                            put("matchingTimeOffsetMs", ph.matchingTimeOffsetMs)
                            put("tagCodes", JSONArray(ph.resolvedTagCodes))
                            put("updatedAtEpochMs", ph.updatedAtEpochMs)
                            put("syncStatus", ph.syncStatus.name)
                            put("remoteUrl", ph.remoteUrl ?: JSONObject.NULL)
                            put("lastSyncAttemptEpochMs", ph.lastSyncAttemptEpochMs ?: JSONObject.NULL)
                            put("isDeleted", ph.isDeleted)
                            put("deletedAtEpochMs", ph.deletedAtEpochMs ?: JSONObject.NULL)
                        })
                    }
                })
                put("progress", JSONArray().apply {
                    progress.forEach { pr ->
                        put(JSONObject().apply {
                            put("id", pr.id)
                            put("projectId", pr.projectId)
                            put("nodeCode", pr.nodeCode)
                            put("planned", pr.planned)
                            put("actual", pr.actual)
                            put("remain", pr.remain)
                            put("delayed", pr.delayed)
                            put("updatedAtEpochMs", pr.updatedAtEpochMs)
                        })
                    }
                })
            }

            try {
                val projectRoot = storageManager.projectRoot(project.slug)
                val metadataFile = File(projectRoot, "project_metadata.json")
                metadataFile.writeText(json.toString(), Charsets.UTF_8)

                val zipFile = projectPackageService.exportProjectZip(project.slug)

                val publicExportsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MapSupervision/Exports").apply { mkdirs() }
                val publicZip = File(publicExportsDir, "${project.slug}_backup_${System.currentTimeMillis()}.zip")
                zipFile.inputStream().use { input ->
                    publicZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                storageManager.scanFile(publicZip)

                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, publicZip)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Chia sẻ gói dự án").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)

                _uiState.value = _uiState.value.copy(message = "Đã xuất và chia sẻ dự án: ${project.name}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Lỗi khi xuất dự án: ${e.message}")
            }
            refresh()
        }
    }

    fun dismissDuplicateDialog() {
        _uiState.value = _uiState.value.copy(duplicateProjectToResolve = null, duplicateZipUri = null)
    }

    fun importProject(context: Context, zipUri: Uri, overwrite: Boolean = false, createCopy: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importMessage = "Đang kiểm tra tệp dự án...")

            try {
                val tempZip = File(context.cacheDir, "temp_import_${UUID.randomUUID()}.zip")
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    tempZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val tempDir = File(context.cacheDir, "temp_import_dir_${UUID.randomUUID()}").apply { mkdirs() }
                try {
                    java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        val buffer = ByteArray(4096)
                        while (entry != null) {
                            val file = File(tempDir, entry.name)
                            if (!file.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                                throw SecurityException("Zip entry lies outside temp dir")
                            }
                            if (entry.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                FileOutputStream(file).use { fos ->
                                    var len = zis.read(buffer)
                                    while (len > 0) {
                                        fos.write(buffer, 0, len)
                                        len = zis.read(buffer)
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    throw IllegalArgumentException("Tệp zip bị hỏng hoặc không đọc được")
                }

                val metadataFile = File(tempDir, "project_metadata.json")
                if (!metadataFile.exists()) {
                    _uiState.value = _uiState.value.copy(importMessage = "Lỗi: Không tìm thấy project_metadata.json trong tệp zip")
                    tempZip.delete()
                    tempDir.deleteRecursively()
                    return@launch
                }

                val json = JSONObject(metadataFile.readText(Charsets.UTF_8))
                val projJson = json.getJSONObject("project")
                val originalId = projJson.getString("id")
                val originalName = projJson.getString("name")
                val originalSlug = projJson.getString("slug")

                val projectsList = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
                val existingProject = projectsList.find { it.slug == originalSlug || it.id == originalId }

                if (existingProject != null && !overwrite && !createCopy) {
                    _uiState.value = _uiState.value.copy(
                        duplicateProjectToResolve = existingProject,
                        duplicateZipUri = zipUri,
                        importMessage = ""
                    )
                    tempZip.delete()
                    tempDir.deleteRecursively()
                    return@launch
                }

                val targetProjectId: String
                val targetSlug: String
                val targetName: String

                if (existingProject != null && overwrite) {
                    targetProjectId = existingProject.id
                    targetSlug = existingProject.slug
                    targetName = existingProject.name
                    // Do not call projectRepository.clearProject(targetProjectId) here to allow merging data.
                } else if (createCopy) {
                    targetProjectId = UUID.randomUUID().toString()
                    val newSuffix = " - Bản sao"
                    var nameCandidate = "$originalName$newSuffix"
                    var slugCandidate = "${originalSlug}-copy"
                    var count = 1
                    while (projectsList.any { it.slug == slugCandidate }) {
                        count++
                        nameCandidate = "$originalName$newSuffix ($count)"
                        slugCandidate = "${originalSlug}-copy-$count"
                    }
                    targetName = nameCandidate
                    targetSlug = slugCandidate
                } else {
                    targetProjectId = originalId
                    targetSlug = originalSlug
                    targetName = originalName
                }

                storageManager.prepareImportedProjectStorage(targetSlug, targetProjectId)
                projectPackageService.copyImportedFilesToPrivateStorage(tempDir, targetSlug)

                val projObj = Project(
                    id = targetProjectId,
                    name = targetName,
                    slug = targetSlug,
                    isArchived = false,
                    createdAtEpochMs = projJson.optLong("createdAtEpochMs", System.currentTimeMillis()),
                    metadataVersion = projJson.optInt("metadataVersion", json.optInt("metadataVersion", 3)),
                    updatedAtEpochMs = projJson.optLong("updatedAtEpochMs", json.optLong("updatedAtEpochMs", System.currentTimeMillis())),
                    storageMode = ProjectStorageMode.PROJECT_DB,
                    projectDbPath = "",
                    mediaStorageProvider = projJson.optString("mediaStorageProvider", "GOOGLE_DRIVE"),
                    mediaStorageFolderId = projJson.optString("mediaStorageFolderId", ""),
                    mediaStorageFolderUrl = projJson.optString("mediaStorageFolderUrl", ""),
                    mediaStorageUpdatedAtEpochMs = projJson.optLong("mediaStorageUpdatedAtEpochMs", 0L),
                    cloudDataConfirmed = projJson.optBoolean("cloudDataConfirmed", false),
                    cloudDecisionRequestId = projJson.optString("cloudDecisionRequestId", "").ifBlank { null },
                    localDeletionErrorCode = projJson.optString("localDeletionErrorCode", "").ifBlank { null }
                )
                projectRepository.importProject(projObj)

                val copiedIds = mutableMapOf<String, String>()
                fun mapId(id: String): String =
                    if (createCopy) copiedIds.getOrPut(id) { UUID.randomUUID().toString() } else id

                fun mapOptionalId(id: String?): String? = id?.let(::mapId)

                val nodesArr = json.optJSONArray("nodes")
                if (nodesArr != null) {
                    val nodes = mutableListOf<GisNode>()
                    for (i in 0 until nodesArr.length()) {
                        val obj = nodesArr.getJSONObject(i)
                        nodes += GisNode(
                            id = mapId(obj.getString("id")),
                            projectId = targetProjectId,
                            code = obj.getString("code"),
                            contractor = obj.getString("contractor"),
                            latitude = obj.getDouble("latitude"),
                            longitude = obj.getDouble("longitude"),
                            mapNumberLabel = obj.getString("mapNumberLabel"),
                            workVolumeSummary = obj.optString("workVolumeSummary", obj.optString("materialSummary", "")),
                            importedFileId = mapOptionalId(obj.optString("importedFileId").takeIf { it.isNotBlank() })
                        )
                    }
                    gisRepository.upsertNodes(nodes)
                }

                val routesArr = json.optJSONArray("routes")
                if (routesArr != null) {
                    val routes = mutableListOf<GisRoute>()
                    for (i in 0 until routesArr.length()) {
                        val obj = routesArr.getJSONObject(i)
                        routes += GisRoute(
                            id = mapId(obj.getString("id")),
                            projectId = targetProjectId,
                            code = obj.getString("code"),
                            contractor = obj.getString("contractor"),
                            startNodeCode = obj.getString("startNodeCode"),
                            endNodeCode = obj.getString("endNodeCode"),
                            importedFileId = mapOptionalId(obj.optString("importedFileId").takeIf { it.isNotBlank() })
                        )
                    }
                    gisRepository.upsertRoutes(routes)
                }

                val notesArr = json.optJSONArray("notes")
                if (notesArr != null) {
                    for (i in 0 until notesArr.length()) {
                        val obj = notesArr.getJSONObject(i)
                        noteRepository.add(
                            Note(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                objectCode = obj.getString("objectCode"),
                                content = obj.getString("content"),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val tasksArr = json.optJSONArray("tasks")
                if (tasksArr != null) {
                    for (i in 0 until tasksArr.length()) {
                        val obj = tasksArr.getJSONObject(i)
                        taskRepository.upsert(
                            Task(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                objectCode = obj.getString("objectCode"),
                                title = obj.getString("title"),
                                description = obj.optString("description", ""),
                                status = TaskStatus.valueOf(obj.getString("status")),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis()),
                                completedAtEpochMs = obj.optPositiveLong("completedAtEpochMs")
                            )
                        )
                    }
                }

                val matArr = json.optJSONArray("materialProgress")
                if (matArr != null) {
                    for (i in 0 until matArr.length()) {
                        val obj = matArr.getJSONObject(i)
                        materialProgressRepository.upsert(
                            WorkVolumeProgress(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                nodeCode = obj.getString("nodeCode"),
                                workName = obj.optString("workName", obj.optString("materialName", "")),
                                plannedQty = obj.getDouble("plannedQty").toFloat(),
                                actualQty = obj.getDouble("actualQty").toFloat(),
                                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", System.currentTimeMillis()),
                                unit = obj.optString("unit", "")
                            )
                        )
                    }
                }

                val logsArr = json.optJSONArray("dailyLogs")
                if (logsArr != null) {
                    for (i in 0 until logsArr.length()) {
                        val obj = logsArr.getJSONObject(i)
                        dailyLogRepository.add(
                            DailyLog(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                workItem = obj.getString("workItem"),
                                manpower = obj.getInt("manpower"),
                                note = obj.getString("note"),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis()),
                                routeCode = obj.optString("routeCode").takeIf { it.isNotBlank() },
                                batchGroupId = obj.optString("batchGroupId", ""),
                                appliedNodeCodesCsv = obj.optString("appliedNodeCodesCsv", ""),
                                linkedPhotoIdsCsv = obj.optString("linkedPhotoIdsCsv", ""),
                                appliedNodeIds = obj.optJsonStringList("appliedNodeIds"),
                                linkedPhotoIds = obj.optJsonStringList("linkedPhotoIds"),
                                photoMatchOffsetMinutes = obj.optInt("photoMatchOffsetMinutes", 0)
                                ,
                                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", obj.optLong("createdAtEpochMs", System.currentTimeMillis())),
                                isDeleted = obj.optBoolean("isDeleted", false),
                                deletedAtEpochMs = obj.optPositiveLong("deletedAtEpochMs")
                            )
                        )
                    }
                }

                val impArr = json.optJSONArray("importedFiles")
                if (impArr != null) {
                    for (i in 0 until impArr.length()) {
                        val obj = impArr.getJSONObject(i)
                        val oldPath = obj.optString("storedPath").takeIf { it.isNotBlank() }
                        val newStoredPath = if (oldPath != null) {
                            val fileName = File(oldPath).name
                            val parentName = File(oldPath).parentFile?.name ?: "processed"
                            File(storageManager.projectRoot(targetSlug), "imports/$parentName/$fileName").absolutePath
                        } else ""

                        importedFileRepository.upsert(
                            ImportedFile(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                fileName = obj.getString("fileName"),
                                fileType = obj.getString("fileType"),
                                storedPath = newStoredPath,
                                summary = obj.getString("summary"),
                                importedAtEpochMs = obj.optLong("importedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val photosArr = json.optJSONArray("photos")
                if (photosArr != null) {
                    for (i in 0 until photosArr.length()) {
                        val obj = photosArr.getJSONObject(i)
                        val oldFilePath = obj.optString("filePath").takeIf { it.isNotBlank() }
                        val oldThumbPath = obj.optString("thumbnailPath").takeIf { it.isNotBlank() }
                        val objectCode = obj.getString("objectCode")
                        val isRoute = json.optJSONArray("routes")?.let { rArr ->
                            (0 until rArr.length()).any { rArr.getJSONObject(it).getString("code") == objectCode }
                        } ?: false
                        val objectFolder = if (isRoute) "Route" else "Node"
                        val objectFolderName = storageManager.sanitizeFolderName(objectCode)

                        val newFilePath = if (oldFilePath != null) {
                            val fileName = File(oldFilePath).name
                            File(storageManager.projectRoot(targetSlug), "Media/$objectFolder/$objectFolderName/$fileName").absolutePath
                        } else ""

                        val newThumbPath = if (oldThumbPath != null) {
                            val fileName = File(oldThumbPath).name
                            File(storageManager.projectRoot(targetSlug), "Media/$objectFolder/$objectFolderName/$fileName").absolutePath
                        } else ""

                        photoRepository.add(
                            SitePhoto(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                objectCode = obj.getString("objectCode"),
                                tagCodesCsv = obj.optString("tagCodesCsv", ""),
                                matchedNodeCode = obj.optString("matchedNodeCode").takeIf { it.isNotBlank() },
                                matchedRouteCode = obj.optString("matchedRouteCode").takeIf { it.isNotBlank() },
                                filePath = newFilePath,
                                thumbnailPath = newThumbPath,
                                latitude = obj.optNullableDouble("latitude"),
                                longitude = obj.optNullableDouble("longitude"),
                                locationAccuracyM = obj.optNullableFloat("locationAccuracyM"),
                                isGpsMocked = obj.optBoolean("isGpsMocked", false),
                                locationStatus = obj.optString("locationStatus").takeIf { it.isNotBlank() }?.let(PhotoLocationStatus::valueOf)
                                    ?: PhotoLocationStatus.MISSING,
                                engineer = obj.optString("engineer", "Engineers"),
                                capturedAtEpochMs = obj.optLong("capturedAtEpochMs", System.currentTimeMillis()),
                                matchedAtEpochMs = obj.optLong("matchedAtEpochMs", 0L),
                                matchingTimeOffsetMs = obj.optLong("matchingTimeOffsetMs", 0L),
                                tagCodes = obj.optJsonStringList("tagCodes"),
                                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", obj.optLong("capturedAtEpochMs", System.currentTimeMillis())),
                                syncStatus = obj.optString("syncStatus").takeIf { it.isNotBlank() }?.let(SitePhotoSyncStatus::valueOf)
                                    ?: SitePhotoSyncStatus.PENDING,
                                remoteUrl = obj.optString("remoteUrl").takeIf { it.isNotBlank() },
                                lastSyncAttemptEpochMs = obj.optPositiveLong("lastSyncAttemptEpochMs"),
                                isDeleted = obj.optBoolean("isDeleted", false),
                                deletedAtEpochMs = obj.optPositiveLong("deletedAtEpochMs")
                            )
                        )
                    }
                }

                val progArr = json.optJSONArray("progress")
                if (progArr != null) {
                    for (i in 0 until progArr.length()) {
                        val obj = progArr.getJSONObject(i)
                        progressRepository.upsert(
                            NodeProgress(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                nodeCode = obj.getString("nodeCode"),
                                planned = obj.getDouble("planned").toFloat(),
                                actual = obj.getDouble("actual").toFloat(),
                                remain = obj.getDouble("remain").toFloat(),
                                delayed = obj.getBoolean("delayed"),
                                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                activeProjectRepository.setActive(targetProjectId)
                projectSyncRepository.notifyProjectChanged(targetProjectId, "project_imported")
                _uiState.value = _uiState.value.copy(
                    importMessage = "Đã nhập dự án thành công: $targetName",
                    duplicateProjectToResolve = null,
                    duplicateZipUri = null
                )

                // Clean up temp
                tempZip.delete()
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message == "Tệp zip bị hỏng hoặc không đọc được" -> "Lỗi khi nhập dự án: Tệp zip bị hỏng hoặc không đọc được"
                    else -> "Lỗi: Nhập dữ liệu thất bại (${e.message})"
                }
                _uiState.value = _uiState.value.copy(importMessage = errorMsg)
            }
            refresh()
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            val session = firebaseAccessRepository.accessState.value.session
            if (session == null || session.isOffline) {
                _uiState.value = _uiState.value.copy(
                    catalogItems = emptyList(),
                    isCatalogLoading = false,
                    catalogError = if (session?.isOffline == true) "Đang ở chế độ offline. Danh mục Firebase không khả dụng." else ""
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isCatalogLoading = true, catalogError = "")
            when (val catalogRes = firebaseAccessRepository.listProjectCatalog(pageSize = 100)) {
                is AppResult.Success -> {
                    val catalogEntries = catalogRes.data
                    val localProjects = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
                    val accessState = firebaseAccessRepository.accessState.value

                    val requestsByProject = mutableMapOf<String, FirebaseProjectAccessRequest>()
                    catalogEntries.forEach { entry ->
                        val reqRes = firebaseAccessRepository.getProjectAccessRequest(entry.projectId)
                        if (reqRes is AppResult.Success && reqRes.data != null) {
                            requestsByProject[entry.projectId] = reqRes.data!!
                        }
                    }

                    val items = resolveCatalogItems(catalogEntries, localProjects, accessState, requestsByProject)
                    val revokedIds = resolveRevokedReadOnlyProjectIds(localProjects, accessState, requestsByProject)
                    _uiState.value = _uiState.value.copy(
                        catalogItems = items,
                        isCatalogLoading = false,
                        catalogError = "",
                        revokedReadOnlyProjectIds = revokedIds
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCatalogLoading = false,
                        catalogError = catalogRes.throwable.message ?: "Không tải được danh mục dự án"
                    )
                }
            }
        }
    }

    fun requestAccess(projectId: String) {
        viewModelScope.launch {
            val currentItems = _uiState.value.catalogItems
            val targetItem = currentItems.find { it.projectId == projectId }
            if (targetItem != null && targetItem.accessStatus == FirebaseAccessRequestStatus.PENDING) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                catalogItems = currentItems.map {
                    if (it.projectId == projectId) it.copy(isActionBusy = true) else it
                }
            )

            when (val res = firebaseAccessRepository.requestProjectAccess(projectId)) {
                is AppResult.Success -> {
                    val updatedRequest = res.data
                    _uiState.value = _uiState.value.copy(
                        message = "Đã gửi yêu cầu phê duyệt cho dự án: ${targetItem?.projectName ?: projectId}",
                        catalogItems = _uiState.value.catalogItems.map {
                            if (it.projectId == projectId) {
                                it.copy(
                                    accessStatus = updatedRequest.status,
                                    isActionBusy = false
                                )
                            } else it
                        }
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        message = "Gửi yêu cầu thất bại: ${res.throwable.message}",
                        catalogItems = _uiState.value.catalogItems.map {
                            if (it.projectId == projectId) it.copy(isActionBusy = false) else it
                        }
                    )
                }
            }
        }
    }
}

private fun Throwable.hasDeletionInProgress(): Boolean =
    generateSequence(this) { it.cause }
        .any { it.message?.contains("DELETION_IN_PROGRESS", ignoreCase = true) == true }

data class FirebaseCatalogItemUiState(
    val projectId: String = "",
    val projectName: String = "",
    val projectCode: String = "",
    val updatedAtEpochMs: Long = 0L,
    val catalogStatus: FirebaseProjectCatalogStatus = FirebaseProjectCatalogStatus.ACTIVE,
    val createdByUid: String? = null,
    val accessStatus: FirebaseAccessRequestStatus = FirebaseAccessRequestStatus.NOT_REQUESTED,
    val isLocalAvailable: Boolean = false,
    val isProjectAdmin: Boolean = false,
    val isRevokedReadOnly: Boolean = false,
    val isActionBusy: Boolean = false
)

data class ProjectUiState(
    val projects: List<Project> = emptyList(),
    val activeProjectId: String? = null,
    val importedFiles: List<ImportedFile> = emptyList(),
    val importMessage: String = "",
    val message: String = "",
    val duplicateProjectToResolve: Project? = null,
    val duplicateZipUri: Uri? = null,
    val catalogItems: List<FirebaseCatalogItemUiState> = emptyList(),
    val isCatalogLoading: Boolean = false,
    val catalogError: String = "",
    val revokedReadOnlyProjectIds: Set<String> = emptySet()
)

internal fun resolveCatalogItems(
    catalogEntries: List<FirebaseProjectCatalogEntry>,
    localProjects: List<Project>,
    accessState: com.mapsupervision.domain.model.FirebaseAccessState,
    requestsByProject: Map<String, FirebaseProjectAccessRequest>
): List<FirebaseCatalogItemUiState> {
    val localProjectIds = localProjects.map { it.id }.toSet()
    val isAdmin = accessState.session?.isAdmin == true

    return catalogEntries.map { entry ->
        val isLocal = entry.projectId in localProjectIds
        val req = requestsByProject[entry.projectId]
        val status = when {
            isAdmin -> FirebaseAccessRequestStatus.APPROVED
            req != null -> req.status
            entry.projectId in accessState.allowedProjectIds -> FirebaseAccessRequestStatus.APPROVED
            else -> FirebaseAccessRequestStatus.NOT_REQUESTED
        }
        val isRevokedReadOnly = isLocal && status == FirebaseAccessRequestStatus.REVOKED

        FirebaseCatalogItemUiState(
            projectId = entry.projectId,
            projectName = entry.projectName,
            projectCode = entry.projectCode,
            updatedAtEpochMs = entry.updatedAtEpochMs,
            catalogStatus = entry.status,
            createdByUid = entry.createdByUid,
            accessStatus = status,
            isLocalAvailable = isLocal,
            isProjectAdmin = accessState.permissionsByProject[entry.projectId]?.isProjectAdmin == true,
            isRevokedReadOnly = isRevokedReadOnly
        )
    }
}

internal fun resolveRevokedReadOnlyProjectIds(
    localProjects: List<Project>,
    accessState: com.mapsupervision.domain.model.FirebaseAccessState,
    requestsByProject: Map<String, FirebaseProjectAccessRequest>
): Set<String> {
    val session = accessState.session
    if (session == null || session.isOffline || session.isAdmin) {
        return emptySet()
    }
    return localProjects.mapNotNull { p ->
        val req = requestsByProject[p.id]
        if (req?.status == FirebaseAccessRequestStatus.REVOKED) {
            p.id
        } else {
            null
        }
    }.toSet()
}

private fun normalizeGoogleDriveFolderInput(value: String): Pair<String, String> {
    val trimmed = value.trim()
    if (trimmed.isBlank()) throw IllegalArgumentException("Google Drive folder URL/ID khong duoc de trong")
    val folderId = Regex("""/folders/([^/?#]+)""").find(trimmed)?.groupValues?.getOrNull(1)
        ?: Regex("""[?&]id=([^&#]+)""").find(trimmed)?.groupValues?.getOrNull(1)
        ?: trimmed
    val decoded = java.net.URLDecoder.decode(folderId.trim(), Charsets.UTF_8.name())
    if (!Regex("""^[A-Za-z0-9_-]{10,}$""").matches(decoded)) {
        throw IllegalArgumentException("Google Drive folder URL/ID khong hop le")
    }
    return decoded to "https://drive.google.com/drive/folders/$decoded"
}

internal fun resolveVisibleProjects(
    allProjects: List<Project>,
    accessState: com.mapsupervision.domain.model.FirebaseAccessState
): List<Project> {
    val allowedProjectIds = accessState.allowedProjectIds
    val session = accessState.session
    if (session == null || session.isAdmin || allowedProjectIds.isEmpty()) {
        return allProjects
    }
    return allProjects.sortedWith(
        compareByDescending<Project> { it.id in allowedProjectIds }
            .thenByDescending { it.updatedAtEpochMs }
    )
}

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

private fun JSONObject.optNullableFloat(name: String): Float? =
    optNullableDouble(name)?.toFloat()

private fun JSONObject.optPositiveLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name).takeIf { it > 0L }

private fun JSONObject.optJsonStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}
