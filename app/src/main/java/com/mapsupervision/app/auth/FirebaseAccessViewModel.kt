package com.mapsupervision.app.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val allowedProjectCount: Int = 0
)

@HiltViewModel
class FirebaseAccessViewModel @Inject constructor(
    private val firebaseAccessRepository: FirebaseAccessRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(FirebaseAccessUiState())
    val uiState: StateFlow<FirebaseAccessUiState> = _uiState.asStateFlow()

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
                        message = ""
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
                        message = ""
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
