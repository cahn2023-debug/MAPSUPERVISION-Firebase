package com.mapsupervision.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.FirebaseAccessState
import com.mapsupervision.domain.model.FirebaseUserSession
import com.mapsupervision.domain.model.ProjectAccess
import com.mapsupervision.domain.repository.FirebaseAccessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FirebaseAccessViewModelTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun register_requires_matching_passwords() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.updateAuthMode(isRegisterMode = true)
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("secret123")
        viewModel.updateConfirmPassword("secret456")
        viewModel.register()

        assertEquals("Mật khẩu xác nhận không khớp.", viewModel.uiState.value.error)
        assertEquals(0, repository.registerCalls)
    }

    @Test
    fun register_success_shows_verification_message() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.updateAuthMode(isRegisterMode = true)
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("secret123")
        viewModel.updateConfirmPassword("secret123")
        viewModel.register()

        waitUntil {
            viewModel.uiState.value.message.contains("Kiểm tra email", ignoreCase = true)
        }
        assertEquals(1, repository.registerCalls)
        assertEquals(false, viewModel.uiState.value.isRegisterMode)
    }

    @Test
    fun skip_enters_offline_mode() = runBlocking {
        val repository = FakeFirebaseAccessRepository()
        val viewModel = FirebaseAccessViewModel(repository, context)

        viewModel.enterOfflineMode()

        waitUntil { viewModel.uiState.value.user?.isOffline == true }
        assertTrue(viewModel.uiState.value.user?.isOffline == true)
        assertEquals(1, repository.offlineCalls)
    }
}

private suspend fun waitUntil(condition: () -> Boolean) {
    withTimeout(5_000) {
        while (!condition()) {
            delay(10)
        }
    }
}

private class FakeFirebaseAccessRepository : FirebaseAccessRepository {
    private val state = MutableStateFlow(FirebaseAccessState(isInitialized = true))
    override val accessState: StateFlow<FirebaseAccessState> = state
    var registerCalls = 0
    var offlineCalls = 0

    override suspend fun signIn(email: String, password: String): AppResult<FirebaseUserSession> {
        val session = FirebaseUserSession(uid = "u1", email = email, emailVerified = true)
        state.value = FirebaseAccessState(
            session = session,
            allowedProjectIds = setOf("project-1"),
            permissionsByProject = mapOf("project-1" to ProjectAccess("project-1")),
            isInitialized = true
        )
        return AppResult.Success(session)
    }

    override suspend fun register(email: String, password: String): AppResult<Unit> {
        registerCalls += 1
        return AppResult.Success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<FirebaseUserSession> =
        signIn("google@example.com", "unused")

    override suspend fun enterOfflineMode(): AppResult<FirebaseAccessState> {
        offlineCalls += 1
        val nextState = FirebaseAccessState(
            session = FirebaseUserSession(
                uid = "offline",
                email = "offline@local",
                emailVerified = true,
                isOffline = true
            ),
            isInitialized = true
        )
        state.value = nextState
        return AppResult.Success(nextState)
    }

    override suspend fun signOut(): AppResult<Unit> {
        state.value = FirebaseAccessState(isInitialized = true)
        return AppResult.Success(Unit)
    }

    override suspend fun refreshAccess(): AppResult<FirebaseAccessState> = AppResult.Success(state.value)
    override suspend fun ensureUserProfile(): AppResult<Unit> = AppResult.Success(Unit)
    override fun projectAccess(projectId: String): ProjectAccess? = state.value.permissionsByProject[projectId]
}
