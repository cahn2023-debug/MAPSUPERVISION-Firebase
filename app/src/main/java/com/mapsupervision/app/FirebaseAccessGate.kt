package com.mapsupervision.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.app.auth.FirebaseAccessViewModel
import com.mapsupervision.app.workspace.IncomingSharePayload

@Composable
fun AppRoot(
    photoPipelineService: com.mapsupervision.domain.service.IPhotoPipelineService,
    locationProvider: com.mapsupervision.domain.service.IPhotoLocationProvider,
    incomingSharePayload: IncomingSharePayload? = null,
    onIncomingShareConsumed: () -> Unit = {}
) {
    val accessViewModel: FirebaseAccessViewModel = hiltViewModel()
    val accessState by accessViewModel.uiState.collectAsStateWithLifecycle()

    if (!accessState.isReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0D14)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val session = accessState.user
    if (session == null) {
        FirebaseSignInScreen(
            email = accessState.email,
            password = accessState.password,
            confirmPassword = accessState.confirmPassword,
            rememberMe = accessState.rememberMe,
            isBusy = accessState.isBusy,
            isRegisterMode = accessState.isRegisterMode,
            error = accessState.error,
            message = accessState.message,
            onEmailChange = accessViewModel::updateEmail,
            onPasswordChange = accessViewModel::updatePassword,
            onConfirmPasswordChange = accessViewModel::updateConfirmPassword,
            onRememberMeChange = accessViewModel::updateRememberMe,
            onAuthModeChange = accessViewModel::updateAuthMode,
            onSignIn = accessViewModel::signIn,
            onRegister = accessViewModel::register,
            onSkipOffline = accessViewModel::enterOfflineMode,
            onGoogleSignIn = accessViewModel::signInWithGoogle,
            setAuthError = accessViewModel::setAuthError
        )
        return
    }

    WorkspaceAppShell(
        photoPipelineService = photoPipelineService,
        locationProvider = locationProvider,
        incomingSharePayload = incomingSharePayload,
        onIncomingShareConsumed = onIncomingShareConsumed,
        session = session,
        onSignOut = accessViewModel::signOut
    )
}

@Composable
private fun FirebaseSignInScreen(
    email: String,
    password: String,
    confirmPassword: String,
    rememberMe: Boolean,
    isBusy: Boolean,
    isRegisterMode: Boolean,
    error: String,
    message: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onAuthModeChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onSkipOffline: () -> Unit,
    onGoogleSignIn: (String) -> Unit,
    setAuthError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val serverClientId = remember(context) { context.resolveGoogleServerClientId() }
    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(serverClientId)
                .requestEmail()
                .build()
        )
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            setAuthError("Đăng nhập Google đã bị hủy.")
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                setAuthError("Khong lay duoc token dang nhap Google.")
            } else {
                onGoogleSignIn(idToken)
            }
        } catch (error: ApiException) {
            AppLogger.e(error, "google.sign_in.api_exception status=${error.statusCode}")
            setAuthError(mapGoogleSignInError(error))
        } catch (error: Exception) {
            AppLogger.e(error, "google.sign_in.failed")
            setAuthError(error.message ?: "Dang nhap Google that bai.")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(24.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRegisterMode) "Tạo tài khoản" else "Đăng nhập",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isRegisterMode) {
                        "Tạo tài khoản bằng email hoặc Google để dùng trực tuyến. Nếu đăng ký bằng email, hãy xác thực email trước khi đăng nhập."
                    } else {
                        "Dùng email đã đăng ký, tài khoản Google, hoặc bỏ qua để làm việc offline với dữ liệu cục bộ."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { onAuthModeChange(false) },
                    enabled = !isBusy
                ) {
                    Text(
                        text = "Đăng nhập",
                        fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal
                    )
                }
                TextButton(
                    onClick = { onAuthModeChange(true) },
                    enabled = !isBusy
                ) {
                    Text(
                        text = "Tạo tài khoản",
                        fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isBusy,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = inputColors()
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isBusy,
                label = { Text("Mật khẩu") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = inputColors()
            )

            if (isRegisterMode) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isBusy,
                    label = { Text("Nhập lại mật khẩu") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = inputColors()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = onRememberMeChange,
                        enabled = !isBusy,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    Text(
                        text = "Lưu tài khoản",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (error.isNotBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    if (isRegisterMode) onRegister() else onSignIn()
                },
                enabled = !isBusy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isRegisterMode) "Tạo tài khoản" else "Đăng nhập",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    if (activity == null) {
                        setAuthError("Không mở được màn hình đăng nhập Google.")
                    } else {
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isRegisterMode) "Tạo tài khoản với Google" else "Đăng nhập với Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onSkipOffline,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    text = "Bỏ qua và dùng offline",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (isRegisterMode) {
                    "Google sẽ tạo tài khoản ngay lần đầu đăng nhập. Tài khoản email cần được xác thực trước khi đăng nhập."
                } else {
                    "Dữ liệu đám mây chỉ khả dụng sau khi đăng nhập."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

private fun mapGoogleSignInError(error: ApiException): String = when (error.statusCode) {
    CommonStatusCodes.NETWORK_ERROR -> "Mang dang khong on dinh. Hay kiem tra Internet va thu lai."
    CommonStatusCodes.SIGN_IN_REQUIRED,
    CommonStatusCodes.INVALID_ACCOUNT -> "Khong the xac thuc tai khoan Google tren thiet bi nay."
    CommonStatusCodes.DEVELOPER_ERROR -> "Cau hinh dang nhap Google chua khop package/SHA cua ung dung. Can cap nhat SHA-1/SHA-256 cho com.mapsupervision trong Firebase va tai lai google-services.json."
    CommonStatusCodes.CANCELED -> "Dang nhap Google da bi huy."
    else -> "Dang nhap Google that bai (${error.statusCode})."
}

private fun Context.resolveGoogleServerClientId(): String {
    val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
    if (resourceId != 0) {
        val value = getString(resourceId).trim()
        if (value.isNotBlank()) return value
    }
    return "735767087959-2868idghbi47fciqh9kp52dugpedu1kc.apps.googleusercontent.com"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
