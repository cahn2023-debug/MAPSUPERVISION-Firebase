package com.mapsupervision.ai.model.mediapipe

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class GemmaDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long
)

data class GemmaDownloadFailure(
    val code: String,
    val userMessage: String,
    val httpCode: Int = 0,
    override val cause: Throwable? = null
) : IOException(userMessage, cause)

@Singleton
class GemmaModelDownloader @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun download(
        url: String,
        targetFile: File,
        expectedBytes: Long,
        onProgress: (GemmaDownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists() && targetFile.length() == expectedBytes) {
            onProgress(GemmaDownloadProgress(expectedBytes, expectedBytes))
            return@withContext
        }

        val partialFile = File("${targetFile.absolutePath}.download")
        var resumeFrom = partialFile.length().takeIf { it > 0L } ?: 0L

        while (true) {
            coroutineContext.ensureActive()
            val response = execute(url, resumeFrom)
            response.use { activeResponse ->
                val code = activeResponse.code
                if (code == 416 && resumeFrom > 0L) {
                    partialFile.delete()
                    resumeFrom = 0L
                    return@use
                }
                if (code !in 200..299) {
                    throw GemmaDownloadFailure(
                        code = "HTTP_ERROR",
                        httpCode = code,
                        userMessage = buildHttpFailureMessage(activeResponse)
                    )
                }
                val resumed = resumeFrom > 0L && code == 206
                if (resumeFrom > 0L && !resumed) {
                    partialFile.delete()
                    resumeFrom = 0L
                }

                val responseBody = activeResponse.body ?: throw GemmaDownloadFailure(
                    code = "HTTP_ERROR",
                    httpCode = code,
                    userMessage = "Phan hoi tai model khong co noi dung."
                )
                val contentLength = responseBody.contentLength().takeIf { it > 0L } ?: expectedBytes
                val totalBytes = if (resumed) {
                    parseContentRangeTotal(activeResponse.header("Content-Range")) ?: expectedBytes
                } else {
                    contentLength
                }.coerceAtLeast(expectedBytes)

                val input = runCatching { responseBody.byteStream() }.getOrElse { error ->
                    throw GemmaDownloadFailure(
                        code = "NETWORK_ERROR",
                        userMessage = error.message ?: "Khong mo duoc stream tai model.",
                        cause = error
                    )
                }
                BufferedInputStream(input).use { stream ->
                    FileOutputStream(partialFile, resumed).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = if (resumed) resumeFrom else 0L
                        var lastPublished = downloadedBytes
                        onProgress(GemmaDownloadProgress(downloadedBytes, totalBytes))

                        while (true) {
                            coroutineContext.ensureActive()
                            val read = try {
                                stream.read(buffer)
                            } catch (error: IOException) {
                                throw GemmaDownloadFailure(
                                    code = "NETWORK_ERROR",
                                    userMessage = error.message ?: "Mat ket noi khi dang tai model.",
                                    cause = error
                                )
                            }
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (downloadedBytes - lastPublished >= PROGRESS_STEP_BYTES) {
                                onProgress(GemmaDownloadProgress(downloadedBytes, totalBytes))
                                lastPublished = downloadedBytes
                            }
                        }
                        output.fd.sync()
                        onProgress(GemmaDownloadProgress(downloadedBytes, totalBytes))

                        if (downloadedBytes != expectedBytes) {
                            throw GemmaDownloadFailure(
                                code = "SIZE_MISMATCH",
                                userMessage = "Dung luong model khong khop. Da tai $downloadedBytes bytes, mong doi $expectedBytes bytes."
                            )
                        }
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!partialFile.renameTo(targetFile)) {
                    throw GemmaDownloadFailure(
                        code = "MOVE_FAILED",
                        userMessage = "Không thể đặt file model vào vị trí lưu trữ."
                    )
                }
                return@withContext
            }
        }
    }

    private fun execute(url: String, resumeFrom: Long): Response {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val builder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "MapSupervision/1.0")
                .header("Accept-Encoding", "identity")
            if (resumeFrom > 0L) {
                builder.header("Range", "bytes=$resumeFrom-")
            }
            val response = try {
                client.newCall(builder.build()).execute()
            } catch (error: IOException) {
                throw GemmaDownloadFailure(
                    code = "NETWORK_ERROR",
                    userMessage = error.message ?: "Không thể kết nối tới nguồn model.",
                    cause = error
                )
            }
            if (response.code in REDIRECT_CODES) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    throw GemmaDownloadFailure(
                        code = "HTTP_REDIRECT",
                        userMessage = "Nguon tai model tra ve redirect khong hop le."
                    )
                }
                redirects += 1
                if (redirects > MAX_REDIRECTS) {
                    throw GemmaDownloadFailure(
                        code = "HTTP_REDIRECT",
                        userMessage = "Qua nhieu redirect khi tai model."
                    )
                }
                currentUrl = response.request.url.resolve(location)?.toString() ?: location
                continue
            }
            return response
        }
    }

    private fun buildHttpFailureMessage(response: Response): String {
        val bodyPreview = response.body?.string().orEmpty().take(160).replace('\n', ' ').trim()
        return buildString {
            append("HTTP ").append(response.code)
            if (bodyPreview.isNotBlank()) {
                append(": ").append(bodyPreview)
            }
        }
    }

    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        return contentRange.substringAfterLast('/', "").toLongOrNull()?.takeIf { it > 0L }
    }

    private companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val MAX_REDIRECTS = 6
        private const val BUFFER_SIZE = 128 * 1024
        private const val PROGRESS_STEP_BYTES = 512 * 1024L
    }
}
