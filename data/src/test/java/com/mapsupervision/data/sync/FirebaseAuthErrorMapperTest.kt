package com.mapsupervision.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAuthErrorMapperTest {

    private val mapper = FirebaseAuthErrorMapper

    @Test
    fun emailExists_mapsToVietnameseMessage() {
        val cause = Exception("The email address is already in use by another account.")
        assertEquals(
            "Email này đã được dùng bởi một tài khoản khác. Hãy đăng nhập hoặc chọn email khác.",
            mapper.map(cause)
        )
    }

    @Test
    fun weakPassword_mapsToVietnameseMessage() {
        val cause = Exception("Password should be at least 6 characters (weak_password).")
        assertEquals(
            "Mật khẩu quá yếu. Vui lòng dùng ít nhất 6 ký tự.",
            mapper.map(cause)
        )
    }

    @Test
    fun invalidEmail_mapsToVietnameseMessage() {
        val cause = Exception("The email address is badly formatted.")
        assertEquals(
            "Email không hợp lệ. Vui lòng kiểm tra lại.",
            mapper.map(cause)
        )
    }

    @Test
    fun badCredentials_variantsMapToSingleVietnameseMessage() {
        val variants = listOf(
            Exception("The supplied auth credential is incorrect, malformed or has expired."),
            Exception("There is no user record corresponding to this identifier."),
            Exception("INVALID_LOGIN_CREDENTIALS"),
            object : Exception("wrong_password") {}
        )
        variants.forEach { cause ->
            assertEquals(
                "Email hoặc mật khẩu không đúng. Vui lòng thử lại.",
                mapper.map(cause)
            )
        }
    }

    @Test
    fun tooManyRequests_mapsToVietnameseMessage() {
        val cause = Exception("Too many requests from this device (too_many_requests).")
        assertEquals(
            "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau vài phút.",
            mapper.map(cause)
        )
    }

    @Test
    fun ioException_mapsToNetworkMessage() {
        assertEquals(
            FirebaseAuthErrorMapper.NETWORK_MESSAGE,
            mapper.map(java.io.IOException("Connection reset by peer"))
        )
    }

    @Test
    fun unknownError_fallsBackToGenericMessage() {
        assertEquals(
            FirebaseAuthErrorMapper.FALLBACK_MESSAGE,
            mapper.map(IllegalStateException("boom"))
        )
    }

    @Test
    fun appAuthMessage_isShownVerbatim() {
        val cause = FirebaseAuthErrorMapper.AppAuthMessageException("Tài khoản chưa xác thực email.")
        assertEquals("Tài khoản chưa xác thực email.", mapper.map(cause))
    }

    @Test
    fun wrap_preservesOriginalCauseForDiagnostics() {
        val original = java.io.IOException("offline")
        val wrapped = mapper.wrap(original)
        assertTrue(wrapped.message!!.startsWith("Lỗi kết nối mạng"))
        assertEquals(original, wrapped.cause)
    }

    @Test
    fun timeoutCancellation_becomesAuthTimeoutWithVietnameseMessage() {
        val thrown = assertThrows(FirebaseAuthErrorMapper.AuthTimeoutException::class.java) {
            kotlinx.coroutines.runBlocking {
                withAuthTimeout(timeoutMs = 10) {
                    kotlinx.coroutines.delay(5_000)
                }
            }
        }
        assertEquals(mapper.TIMEOUT_MESSAGE, thrown.message)
    }

    @Test
    fun withinTimeout_returnsBlockResult() {
        val result = kotlinx.coroutines.runBlocking {
            withAuthTimeout(timeoutMs = 5_000) { "ok" }
        }
        assertEquals("ok", result)
    }

    @Test
    fun timeoutConstants_areUserFacing() {
        assertTrue(mapper.TIMEOUT_MESSAGE.contains("Internet"))
        assertTrue(mapper.NETWORK_MESSAGE.contains("mạng", ignoreCase = true) || "Internet" in mapper.NETWORK_MESSAGE)
        assertTrue(mapper.FALLBACK_MESSAGE.isNotBlank())
    }
}
