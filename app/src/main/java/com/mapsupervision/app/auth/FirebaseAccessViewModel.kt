package com.mapsupervision.app.auth

import android.content.Context
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
    val rememberMe: Boolean = false,
    val error: String = "",
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
        _uiState.value = _uiState.value.copy(email = value, error = "")
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = "")
    }

    fun updateRememberMe(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = value)
    }


    fun setAuthError(value: String) {
        _uiState.value = _uiState.value.copy(error = value, isBusy = false)
    }

    fun signIn() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Nhập email và mật khẩu.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "")
            when (val result = firebaseAccessRepository.signIn(email, password)) {
                is AppResult.Success -> {
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
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        error = ""
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể đăng nhập Firebase."
                    )
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "")
            when (val result = firebaseAccessRepository.signInWithGoogle(idToken)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        error = ""
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể đăng nhập bằng tài khoản Google."
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = "")
            when (val result = firebaseAccessRepository.signOut()) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        password = "",
                        error = ""
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = result.throwable.message ?: "Không thể đăng xuất Firebase."
                    )
                }
            }
        }
    }
}
