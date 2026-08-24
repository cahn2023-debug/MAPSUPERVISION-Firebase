package com.mapsupervision.app.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseAccessAdminAction
import com.mapsupervision.domain.model.ContractorScope
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import com.mapsupervision.domain.model.FirebaseProjectCatalogStatus
import com.mapsupervision.domain.model.FirebaseCatalogMigrationReport
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import com.mapsupervision.domain.repository.FirebaseSyncRepository
import com.mapsupervision.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class FirebaseAccessUiState(
    val isReady: Boolean = false,
    val isBusy: Boolean = false,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val rememberMe: Boolean = false,
    val isRegisterMode: Boolean = false,
    val error: String = "",
    val message: String = "",
    val user: FirebaseUserSession? = null,
    val allowedProjectCount: Int = 0,
    val projectCatalog: List<FirebaseProjectCatalogEntry> = emptyList(),
    val accessRequestsByProject: Map<String, FirebaseProjectAccessRequest> = emptyMap(),
    val catalogLoading: Boolean = false,
    val catalogError: String = "",
    val migrationReport: FirebaseCatalogMigrationReport? = null,
    val requestingProjectId: String? = null,
    val adminRequests: List<FirebaseProjectAccessRequest> = emptyList(),
    val adminLoading: Boolean = false,
    val adminError: String = "",
    val adminBusyRequestId: String? = null
)

@HiltViewModel
class FirebaseAccessViewModel @Inject constructor(
    private val firebaseAccessRepository: FirebaseAccessRepository,
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository? = null,
    private val activeProjectRepository: ActiveProjectRepository? = null,
    private val firebaseSyncRepository: FirebaseSyncRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(FirebaseAccessUiState())
    val uiState: StateFlow<FirebaseAccessUiState> = _uiState.asStateFlow()
    private var catalogRefreshJob: kotlinx.coroutines.Job? = null
    private var lastCatalogUid: String? = null

    init {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("saved_email", "") ?: ""
        val savedPassword = prefs.getString("saved_password", "") ?: ""
        val rememberMe = prefs.getBoolean("remember_me", false)

        _uiState.value = _uiState.value.copy(
            email = savedEmail,
            password = if (rememberMe) savedPassword else "",
            rememberMe = rememberMe
        )

        viewModelScope.launch {
            firebaseAccessRepository.accessState.collectLatest { accessState ->
                _uiState.value = _uiState.value.copy(
                    isReady = accessState.isInitialized,
                    user = accessState.session,
                    allowedProjectCount = accessState.allowedProjectIds.size
                )
                val session = accessState.session
                if (session == null || session.isOffline) {
                    lastCatalogUid = null
                    catalogRefreshJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        projectCatalog = emptyList(),
                        accessRequestsByProject = emptyMap(),
                        catalogLoading = false,
                        catalogError = "",
                        migrationReport = null,
                        adminRequests = emptyList(),
                        adminLoading = false,
                        adminError = ""
                    )
                } else if (lastCatalogUid != session.uid) {
                    lastCatalogUid = session.uid
                    refreshProjectCatalog()
                    if (session.isAdmin) refreshAdminRequests()
                    if (session.isAdmin) refreshMigrationReport()
                }
            }
        }
        viewModelScope.launch {
            when (val result = firebaseAccessRepository.refreshAccess()) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(error = "")
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.throwable.message.orEmpty()
                    )
                }
            }
        }
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = "", message = "")
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = "", message = "")
    }

    fun updateConfirmPassword(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = "", message = "")
    }

    fun updateRememberMe(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = value)
    }

    fun updateAuthMode(isRegisterMode: Boolean) {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = isRegisterMode,
            confirmPassword = "",
            password = "",
            error = "",
            message = ""
        )
    }

    fun setAuthError(value: String) {
        _uiState.value = _uiState.value.copy(error = value, message = "", isBusy = false)
    }

    fun setAuthMessage(value: String) {
        _uiState.value = _uiState.value.copy(error = "", message = value, isBusy = false)
    }

    fun signIn() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Nhập email và mật khẩu.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "")
            when (val result = firebaseAccessRepository.signIn(email, password)) {
                is AppResult.Success -> {
                    persistRememberMe(email, password)
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        confirmPassword = "",
                        error = "",
                        message = "\u0110\u0103ng nh\u1eadp th\u00e0nh c\u00f4ng."
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể đăng nhập.",
                        message = ""
                    )
                }
            }
        }
    }

    fun register() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword
        when {
            email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                _uiState.value = _uiState.value.copy(error = "Nhập đầy đủ email và mật khẩu.")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _uiState.value = _uiState.value.copy(error = "Email không hợp lệ.")
                return
            }
            password != confirmPassword -> {
                _uiState.value = _uiState.value.copy(error = "Mật khẩu xác nhận không khớp.")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "")
            when (val result = firebaseAccessRepository.register(email, password)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        isRegisterMode = false,
                        password = "",
                        confirmPassword = "",
                        error = "",
                        message = "Kiểm tra email để xác thực tài khoản trước khi đăng nhập."
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể tạo tài khoản.",
                        message = ""
                    )
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "")
            when (val result = firebaseAccessRepository.signInWithGoogle(idToken)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        confirmPassword = "",
                        error = "",
                        message = "\u0110\u0103ng nh\u1eadp th\u00e0nh c\u00f4ng."
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể đăng nhập bằng tài khoản Google.",
                        message = ""
                    )
                }
            }
        }
    }

    fun enterOfflineMode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "")
            when (val result = firebaseAccessRepository.enterOfflineMode()) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        confirmPassword = "",
                        error = "",
                        message = "Đang sử dụng dữ liệu cục bộ ở chế độ offline."
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể vào chế độ offline.",
                        message = ""
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "")
            when (val result = firebaseAccessRepository.signOut()) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        confirmPassword = "",
                        error = "",
                        message = ""
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể đăng xuất.",
                        message = ""
                    )
                }
            }
        }
    }

    fun refreshProjectCatalog() {
        val session = _uiState.value.user ?: return
        if (session.isOffline) return
        catalogRefreshJob?.cancel()
        catalogRefreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(catalogLoading = true, catalogError = "")
            runCatching {
                val entries = buildList {
                    var cursorUpdatedAt: Long? = null
                    var cursorProjectId: String? = null
                    var last: FirebaseProjectCatalogEntry? = null
                    do {
                        val page = when (val result = firebaseAccessRepository.listProjectCatalog(
                            pageSize = 100L,
                            startAfterUpdatedAtEpochMs = cursorUpdatedAt,
                            startAfterProjectId = cursorProjectId
                        )) {
                            is AppResult.Success -> result.data
                            is AppResult.Error -> throw result.throwable
                        }
                        addAll(page)
                        last = page.lastOrNull()
                        cursorUpdatedAt = last?.updatedAtEpochMs
                        cursorProjectId = last?.projectId
                    } while (last != null && page.size >= 100)
                }.distinctBy { it.projectId }
                val requests = coroutineScope {
                    entries.map { entry ->
                        async {
                            when (val result = firebaseAccessRepository.getProjectAccessRequest(entry.projectId)) {
                                is AppResult.Success -> entry.projectId to result.data
                                is AppResult.Error -> throw result.throwable
                            }
                        }
                    }.awaitAll().mapNotNull { (projectId, request) -> request?.let { projectId to it } }.toMap()
                }
                _uiState.value = _uiState.value.copy(
                    projectCatalog = entries,
                    accessRequestsByProject = requests,
                    catalogLoading = false,
                    catalogError = ""
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    catalogLoading = false,
                    catalogError = error.message ?: "Không tải được danh mục dự án Firebase."
                )
            }
        }
    }

    fun refreshMigrationReport() {
        if (_uiState.value.user?.isAdmin != true) return
        viewModelScope.launch {
            when (val result = firebaseAccessRepository.latestCatalogMigrationReport()) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(migrationReport = result.data)
                is AppResult.Error -> Unit
            }
        }
    }

    fun requestProjectAccess(projectId: String) {
        if (_uiState.value.user?.isOffline == true) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(requestingProjectId = projectId, catalogError = "", message = "")
            when (val result = firebaseAccessRepository.requestProjectAccess(projectId)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        requestingProjectId = null,
                        accessRequestsByProject = _uiState.value.accessRequestsByProject + (projectId to result.data),
                        message = "Đã gửi yêu cầu truy cập. Vui lòng chờ Admin phê duyệt."
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        requestingProjectId = null,
                        catalogError = result.throwable.message ?: "Không thể gửi yêu cầu truy cập."
                    )
                }
            }
        }
    }

    fun accessStatusFor(projectId: String): FirebaseAccessRequestStatus =
        uiState.value.accessRequestsByProject[projectId]?.status ?: FirebaseAccessRequestStatus.NOT_REQUESTED

    fun refreshAdminRequests() {
        if (_uiState.value.user?.isAdmin != true) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(adminLoading = true, adminError = "")
            when (val result = firebaseAccessRepository.listProjectAccessRequests(status = null, pageSize = 100L)) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(
                    adminRequests = result.data,
                    adminLoading = false,
                    adminError = ""
                )
                is AppResult.Error -> _uiState.value = _uiState.value.copy(
                    adminLoading = false,
                    adminError = result.throwable.message ?: "Không tải được hàng đợi phê duyệt."
                )
            }
        }
    }

    fun transitionProjectAccess(
        request: FirebaseProjectAccessRequest,
        action: FirebaseAccessAdminAction,
        allowedDataGroups: Set<String> = setOf("gis_node"),
        contractorScope: ContractorScope = ContractorScope.ALL,
        allowedContractors: Set<String> = emptySet()
    ) {
        if (_uiState.value.user?.isAdmin != true) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(adminBusyRequestId = request.requestId, adminError = "")
            when (val result = firebaseAccessRepository.transitionProjectAccess(
                projectId = request.projectId,
                targetUserId = request.userId,
                action = action,
                allowedDataGroups = allowedDataGroups,
                contractorScope = contractorScope,
                allowedContractors = allowedContractors
            )) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(
                    adminRequests = _uiState.value.adminRequests
                        .map { current -> if (current.requestId == result.data.requestId) result.data else current },
                    adminBusyRequestId = null,
                    message = "Đã cập nhật trạng thái và ghi audit."
                )
                is AppResult.Error -> _uiState.value = _uiState.value.copy(
                    adminBusyRequestId = null,
                    adminError = result.throwable.message ?: "Không thể cập nhật quyền truy cập."
                )
            }
        }
    }

    fun openOrDownloadProject(
        entry: FirebaseProjectCatalogEntry,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "Đang chuẩn bị dự án...")
            try {
                if (projectRepository != null) {
                    val localProjects = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
                    val existing = localProjects.find { it.id == entry.projectId }
                    if (existing == null) {
                        val sanitizedSlug = entry.projectCode.lowercase(java.util.Locale.ROOT)
                            .replace(Regex("[^a-z0-9-]"), "-")
                            .trim('-')
                            .ifBlank { entry.projectId.take(8).lowercase(java.util.Locale.ROOT) }
                        val project = Project(
                            id = entry.projectId,
                            name = entry.projectName,
                            slug = sanitizedSlug,
                            isArchived = entry.status == FirebaseProjectCatalogStatus.ARCHIVED,
                            createdAtEpochMs = entry.updatedAtEpochMs,
                            metadataVersion = 3,
                            updatedAtEpochMs = entry.updatedAtEpochMs,
                            storageMode = ProjectStorageMode.PROJECT_DB,
                            projectDbPath = "",
                            mediaStorageProvider = "GOOGLE_DRIVE",
                            mediaStorageFolderId = "",
                            mediaStorageFolderUrl = "",
                            mediaStorageUpdatedAtEpochMs = 0L,
                            isDeleted = false,
                            deletedAtEpochMs = null
                        )
                        when (val importRes = projectRepository.importProject(project)) {
                            is AppResult.Success -> Unit
                            is AppResult.Error -> throw importRes.throwable
                        }
                    }
                }

                if (activeProjectRepository != null) {
                    when (val activeRes = activeProjectRepository.setActive(entry.projectId)) {
                        is AppResult.Success -> {
                            if (firebaseSyncRepository != null) {
                                viewModelScope.launch {
                                    runCatching {
                                        firebaseSyncRepository.pullChanges(entry.projectId)
                                    }
                                }
                            }
                            _uiState.value = _uiState.value.copy(isBusy = false, message = "")
                            onSuccess()
                        }
                        is AppResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isBusy = false,
                                error = "Không thể kích hoạt dự án: ${activeRes.throwable.message}"
                            )
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isBusy = false, message = "")
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = e.message ?: "Không thể mở dự án từ Cloud."
                )
            }
        }
    }

    fun createCloudProject(
        name: String,
        customPath: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Tên dự án không được để trống.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "", message = "Đang tạo dự án Cloud...")
            try {
                if (projectRepository != null) {
                    when (val createRes = projectRepository.create(trimmedName, customPath)) {
                        is AppResult.Success -> {
                            val created = createRes.data
                            activeProjectRepository?.setActive(created.id)
                            if (firebaseSyncRepository != null) {
                                runCatching {
                                    firebaseSyncRepository.pushPending(created.id)
                                }
                            }
                            _uiState.value = _uiState.value.copy(
                                isBusy = false,
                                message = "Đã tạo dự án thành công: ${created.name}"
                            )
                            refreshProjectCatalog()
                            onSuccess()
                        }
                        is AppResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isBusy = false,
                                error = "Không thể tạo dự án: ${createRes.throwable.message}"
                            )
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isBusy = false, message = "Đã tạo dự án thành công.")
                    refreshProjectCatalog()
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = e.message ?: "Tạo dự án thất bại."
                )
            }
        }
    }

    private fun persistRememberMe(email: String, password: String) {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        if (_uiState.value.rememberMe) {
            prefs.edit()
                .putString("saved_email", email)
                .putString("saved_password", password)
                .putBoolean("remember_me", true)
                .apply()
        } else {
            prefs.edit()
                .remove("saved_email")
                .remove("saved_password")
                .putBoolean("remember_me", false)
                .apply()
        }
    }
}
