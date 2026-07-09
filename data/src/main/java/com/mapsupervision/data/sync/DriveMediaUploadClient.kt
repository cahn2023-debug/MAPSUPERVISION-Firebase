package com.mapsupervision.data.sync

import com.mapsupervision.domain.model.MediaType
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class DriveMediaObjectType {
    NODE,
    ROUTE
}

internal data class DriveMediaUploadRequest(
    val projectId: String,
    val token: String,
    val photoId: String,
    val objectType: DriveMediaObjectType,
    val objectCode: String,
    val mediaType: MediaType,
    val mimeType: String,
    val capturedAtEpochMs: Long,
    val originalFile: File,
    val thumbnailFile: File?
)

internal class DriveMediaUploadClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    fun upload(baseUrl: String, request: DriveMediaUploadRequest): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) {
            error("MEDIA_UPLOAD_BASE_URL missing. Set it to the webapp base URL in .env")
        }
        if (!request.originalFile.exists()) {
            error("Media file does not exist: ${request.originalFile.absolutePath}")
        }

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photoId", request.photoId)
            .addFormDataPart("objectType", request.objectType.name)
            .addFormDataPart("objectCode", request.objectCode)
            .addFormDataPart("mediaType", request.mediaType.name)
            .addFormDataPart("mimeType", request.mimeType)
            .addFormDataPart("capturedAtEpochMs", request.capturedAtEpochMs.toString())
            .addFormDataPart(
                "original",
                request.originalFile.name,
                request.originalFile.asRequestBody(request.mimeType.toMediaTypeOrNull())
            )

        val thumbnailFile = request.thumbnailFile
        if (
            request.mediaType == MediaType.IMAGE &&
            thumbnailFile != null &&
            thumbnailFile.exists() &&
            thumbnailFile.absolutePath != request.originalFile.absolutePath
        ) {
            bodyBuilder.addFormDataPart(
                "thumbnail",
                thumbnailFile.name,
                thumbnailFile.asRequestBody(request.mimeType.toMediaTypeOrNull())
            )
        }

        val httpRequest = Request.Builder()
            .url("$normalizedBaseUrl/api/projects/${request.projectId}/media")
            .header("Authorization", "Bearer ${request.token}")
            .post(bodyBuilder.build())
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Drive media upload failed (${response.code}): $responseText")
            }
            val json = Json.parseToJsonElement(responseText).jsonObject
            if (json["success"]?.jsonPrimitive?.booleanOrNull != true) {
                error("Drive media upload failed: $responseText")
            }
            val url = json["data"]?.jsonObject?.get("remoteUrl")?.jsonPrimitive?.content.orEmpty()
            if (url.isBlank()) {
                error("Drive media upload response missing remoteUrl.")
            }
            return url
        }
    }
}
