package com.mapsupervision.data.sync

/**
 * Hỗ trợ luồng xác thực: timeout cho lời gọi mạng và ánh xạ lỗi gốc của
 * Firebase/Google sang thông điệp tiếng Việt có dấu cho người dùng cuối.
 *
 * Mapper cố tình KHÔNG import lớp Firebase nào: nó nhận diện lỗi qua tên lớp +
 * nội dung thông điệp, giúp unit test thuần Kotlin mà không cần Robolectric,
 * đồng thời giữ file này trung lập với module boundary (vẫn nằm trong :data/sync).
 */
internal object FirebaseAuthErrorMapper {

    /** Lỗi nghiệp vụ có sẵn thông điệp tiếng Việt rõ ràng — hiển thị nguyên văn. */
    internal class AppAuthMessageException(message: String) : Exception(message)

    /** Quá 30 giây không có phản hồi từ Firebase/Play services. */
    internal class AuthTimeoutException(message: String) : Exception(message)

    private val EMAIL_EXISTS = listOf("already in use", "email_exists", "collision")
    private val WEAK_PASSWORD = listOf("weak_password", "password must be at least")
    private val INVALID_EMAIL = listOf("badly formatted", "invalid_email")
    private val TOO_MANY_REQUESTS = listOf("too_many_requests", "blocked all requests")
    private val BAD_CREDENTIALS = listOf(
        "invalid_login_credentials",
        "wrong_password",
        "no user record",
        "supplied auth credential",
        "credential is incorrect",
        "user_not_found"
    )
    private val NETWORK_TEXT = listOf(
        "network error",
        "networkexception",
        "interrupted connection",
        "unreachable",
        "failed to connect"
    )

    /**
     * Trả về thông điệp tiếng Việt tương ứng với lỗi gốc. Luôn kèm throwable
     * gốc khi bọc (xem [wrap]) để log đầy đủ phục vụ chẩn đoán từ xa.
     */
    fun map(cause: Throwable): String {
        if (cause is AppAuthMessageException) return cause.message.orEmpty()
        if (cause is AuthTimeoutException) return TIMEOUT_MESSAGE
        val signature = signatureOf(cause)
        return when {
            matches(signature, EMAIL_EXISTS) ->
                "Email này đã được dùng bởi một tài khoản khác. Hãy đăng nhập hoặc chọn email khác."
            matches(signature, WEAK_PASSWORD) || "weakpassword" in signature ->
                "Mật khẩu quá yếu. Vui lòng dùng ít nhất 6 ký tự."
            matches(signature, INVALID_EMAIL) ->
                "Email không hợp lệ. Vui lòng kiểm tra lại."
            matches(signature, TOO_MANY_REQUESTS) || "toomanyrequests" in signature ->
                "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau vài phút."
            matches(signature, BAD_CREDENTIALS) ||
                "invalidcredentials" in signature ||
                "usernotfound" in signature ||
                "invaliduser" in signature ->
                "Email hoặc mật khẩu không đúng. Vui lòng thử lại."
            cause is java.io.IOException || matches(signature, NETWORK_TEXT) ->
                NETWORK_MESSAGE
            else -> FALLBACK_MESSAGE
        }
    }

    /** Bọc lỗi gốc thành Exception mang thông điệp tiếng Việt để ViewModel hiển thị. */
    fun wrap(cause: Throwable): Exception = Exception(map(cause), cause)

    private fun signatureOf(cause: Throwable): String =
        "${cause.javaClass.simpleName} ${cause.message.orEmpty()}".lowercase()

    private fun matches(signature: String, needles: List<String>): Boolean =
        needles.any { it in signature }

    internal const val TIMEOUT_MESSAGE: String =
        "Hết thời gian chờ phản hồi từ máy chủ. Vui lòng kiểm tra kết nối Internet và thử lại."
    internal const val NETWORK_MESSAGE: String =
        "Lỗi kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
    internal const val FALLBACK_MESSAGE: String =
        "Xác thực thất bại. Vui lòng thử lại sau."
}

/** Chạy [block] với giới hạn [timeoutMs] (mặc định 30 giây); vượt hạn sẽ ném [FirebaseAuthErrorMapper.AuthTimeoutException]. */
internal suspend fun <T> withAuthTimeout(
    timeoutMs: Long = AUTH_TIMEOUT_MS,
    block: suspend () -> T
): T =
    try {
        kotlinx.coroutines.withTimeout(timeoutMs) { block() }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        throw FirebaseAuthErrorMapper.AuthTimeoutException(
            FirebaseAuthErrorMapper.TIMEOUT_MESSAGE
        )
    }

private const val AUTH_TIMEOUT_MS = 30_000L
