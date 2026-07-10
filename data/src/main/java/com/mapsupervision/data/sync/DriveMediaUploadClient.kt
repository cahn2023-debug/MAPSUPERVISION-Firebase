package com.mapsupervision.data.sync

import com.mapsupervision.data.BuildConfig
import com.mapsupervision.domain.model.MediaType
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal enum class DriveMediaObjectType {
    NODE,
    ROUTE
}

internal data class DriveMediaUploadRequest(
    val projectId: String,
    val projectName: String,
    val rootFolderId: String?,
    val token: String,
    val photoId: String,
    val objectType: DriveMediaObjectType,
    val objectCode: String,
    val mediaType: MediaType,
    val mimeType: String,
    val capturedAtEpochMs: Long,
    val address: String?,
    val captureNote: String?,
    val originalFile: File,
    val thumbnailFile: File?
)

internal data class DriveDirectUploadConfig(
    val enabled: Boolean,
    val rootFolderId: String,
    val serviceAccountJsonBase64: String
) {
    companion object {
        fun fromBuildConfig(): DriveDirectUploadConfig = DriveDirectUploadConfig(
            enabled = BuildConfig.DRIVE_DIRECT_UPLOAD_ENABLED,
            rootFolderId = BuildConfig.GOOGLE_DRIVE_ROOT_FOLDER_ID,
            serviceAccountJsonBase64 = BuildConfig.GOOGLE_SERVICE_ACCOUNT_JSON_BASE64
        )
    }
}

internal open class DriveMediaUploadClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val directUploadConfig: DriveDirectUploadConfig = DriveDirectUploadConfig.fromBuildConfig()
) {
    open fun upload(baseUrl: String, request: DriveMediaUploadRequest): String {
        if (!request.originalFile.exists()) {
            error("Media file does not exist: ${request.originalFile.absolutePath}")
        }

        if (directUploadConfig.enabled) {
            return uploadDirectToDrive(request)
        }

        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) {
            error("MEDIA_UPLOAD_BASE_URL missing. Set it to the webapp base URL in .env")
        }
        return uploadViaWebapp(normalizedBaseUrl, request)
    }

    private fun uploadViaWebapp(baseUrl: String, request: DriveMediaUploadRequest): String {
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photoId", request.photoId)
            .addFormDataPart("projectName", request.projectName)
            .addFormDataPart("objectType", request.objectType.name)
            .addFormDataPart("objectCode", request.objectCode)
            .addFormDataPart("mediaType", request.mediaType.name)
            .addFormDataPart("mimeType", request.mimeType)
            .addFormDataPart("capturedAtEpochMs", request.capturedAtEpochMs.toString())
            .addFormDataPart("address", request.address.orEmpty())
            .addFormDataPart("captureNote", request.captureNote.orEmpty())
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
            .url("$baseUrl/api/projects/${request.projectId}/media")
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

    private fun uploadDirectToDrive(request: DriveMediaUploadRequest): String {
        val rootFolderId = request.rootFolderId?.trim().orEmpty().ifBlank { directUploadConfig.rootFolderId.trim() }
        if (rootFolderId.isBlank()) {
            error("GOOGLE_DRIVE_ROOT_FOLDER_ID missing. Set it in .env for direct Drive upload.")
        }
        val serviceAccount = decodeServiceAccount()
        val accessToken = exchangeAccessToken(serviceAccount)

        val projectFolderId = ensureProjectFolder(accessToken, rootFolderId, request.projectId, request.projectName)
        val objectFolder = sanitizeSegment(request.objectCode)
        val folderSegments = if (request.mediaType == MediaType.VIDEO) {
            listOf(
                "media",
                "videos",
                if (request.objectType == DriveMediaObjectType.ROUTE) "Routes" else "Nodes",
                objectFolder
            )
        } else {
            listOf(
                "photos",
                if (request.objectType == DriveMediaObjectType.ROUTE) "Routes" else "Nodes",
                objectFolder
            )
        }

        val parentId = ensureFolderPath(accessToken, projectFolderId, folderSegments)
        val originalExtension = extensionForMime(
            request.mimeType,
            if (request.mediaType == MediaType.VIDEO) "mp4" else "jpg"
        )
        val originalName = buildMediaFileName(
            capturedAtEpochMs = request.capturedAtEpochMs,
            address = request.address,
            captureNote = request.captureNote,
            extension = originalExtension
        )
        val originalId = upsertFile(
            accessToken = accessToken,
            parentId = parentId,
            photoId = request.photoId,
            name = originalName,
            mimeType = request.mimeType,
            bytes = request.originalFile.readBytes()
        )

        val thumbnailFile = request.thumbnailFile
        if (
            request.mediaType == MediaType.IMAGE &&
            thumbnailFile != null &&
            thumbnailFile.exists() &&
            thumbnailFile.absolutePath != request.originalFile.absolutePath
        ) {
            val thumbnailExtension = extensionForMime(request.mimeType, "jpg")
            val thumbnailName = buildMediaFileName(
                capturedAtEpochMs = request.capturedAtEpochMs,
                address = request.address,
                captureNote = listOfNotNull(request.captureNote?.takeIf { it.isNotBlank() }, "thumbnail").joinToString(" - "),
                extension = thumbnailExtension
            )
            upsertFile(
                accessToken = accessToken,
                parentId = parentId,
                photoId = "${request.photoId}__thumb",
                name = thumbnailName,
                mimeType = request.mimeType,
                bytes = thumbnailFile.readBytes()
            )
        }

        return publicDriveUrl(originalId)
    }

    private fun ensureProjectFolder(
        accessToken: String,
        rootFolderId: String,
        projectId: String,
        projectName: String
    ): String {
        val query = listOf(
            "'$rootFolderId' in parents",
            "appProperties has { key='mapsupervisionProjectId' and value='${escapeDriveQuery(projectId)}' }",
            "mimeType = '$FOLDER_MIME_TYPE'",
            "trashed = false"
        ).joinToString(" and ")
        val url = buildDriveListUrl(query)
        val responseText = authorizedJsonRequest(accessToken, url)
        val files = Json.parseToJsonElement(responseText).jsonObject["files"]?.let { it.jsonArray }.orEmpty()
        val existingFolderId = files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        if (existingFolderId != null) {
            return existingFolderId
        }

        val folderName = sanitizeSegment(projectName, "DuAn")
        val existingByNameId = findChildFolder(accessToken, rootFolderId, folderName)
        if (existingByNameId != null) {
            authorizedJsonRequest(
                accessToken = accessToken,
                url = "https://www.googleapis.com/drive/v3/files/$existingByNameId?supportsAllDrives=true&fields=id",
                method = "PATCH",
                body = """{"appProperties":{"mapsupervisionProjectId":"${jsonEscape(projectId)}"}}""".toRequestBody(JSON_MEDIA_TYPE)
            )
            return existingByNameId
        }

        val createResponse = authorizedJsonRequest(
            accessToken = accessToken,
            url = "https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id",
            method = "POST",
            body = """
                {"name":"${jsonEscape(folderName)}","mimeType":"$FOLDER_MIME_TYPE","parents":["${jsonEscape(rootFolderId)}"],"appProperties":{"mapsupervisionProjectId":"${jsonEscape(projectId)}"}}
            """.trimIndent().toRequestBody(JSON_MEDIA_TYPE)
        )
        return Json.parseToJsonElement(createResponse).jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
            .ifBlank { error("Failed to create project folder $projectName.") }
    }

    private fun decodeServiceAccount(): DriveServiceAccount {
        val encoded = directUploadConfig.serviceAccountJsonBase64.trim()
        if (encoded.isBlank()) {
            error("GOOGLE_SERVICE_ACCOUNT_JSON/FILE missing. Set it in .env for direct Drive upload.")
        }
        val decoded = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        val json = Json.parseToJsonElement(decoded).jsonObject
        return DriveServiceAccount(
            clientEmail = json["client_email"]?.jsonPrimitive?.content.orEmpty(),
            privateKey = json["private_key"]?.jsonPrimitive?.content.orEmpty().replace("\\n", "\n")
        ).also {
            if (it.clientEmail.isBlank() || it.privateKey.isBlank()) {
                error("Google service account credentials are incomplete.")
            }
        }
    }

    private fun exchangeAccessToken(serviceAccount: DriveServiceAccount): String {
        val assertion = createServiceAccountJwt(serviceAccount)
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(buildDriveFailureMessage("Failed to obtain Google Drive access token", response.code, responseText))
            }
            val json = Json.parseToJsonElement(responseText).jsonObject
            return json["access_token"]?.jsonPrimitive?.content.orEmpty()
                .ifBlank { error("Google Drive access token missing from token response.") }
        }
    }

    private fun createServiceAccountJwt(serviceAccount: DriveServiceAccount): String {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val payload = """
            {"iss":"${jsonEscape(serviceAccount.clientEmail)}","scope":"https://www.googleapis.com/auth/drive","aud":"https://oauth2.googleapis.com/token","exp":${nowSeconds + 3600},"iat":$nowSeconds}
        """.trimIndent().replace("\n", "")
        val encodedHeader = base64Url(header.toByteArray(StandardCharsets.UTF_8))
        val encodedPayload = base64Url(payload.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = Signature.getInstance("SHA256withRSA")
        val privateKeyBytes = parsePem(serviceAccount.privateKey)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray(StandardCharsets.UTF_8))
        val signed = base64Url(signature.sign())
        return "$signingInput.$signed"
    }

    private fun ensureFolderPath(accessToken: String, rootFolderId: String, segments: List<String>): String {
        var currentFolderId = rootFolderId
        segments.forEach { segment ->
            currentFolderId = ensureChildFolder(accessToken, currentFolderId, segment)
        }
        return currentFolderId
    }

    private fun ensureChildFolder(accessToken: String, parentId: String, name: String): String {
        val existing = findChildFolder(accessToken, parentId, name)
        if (existing != null) return existing
        val responseText = authorizedJsonRequest(
            accessToken = accessToken,
            url = "https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id",
            method = "POST",
            body = """
                {"name":"${jsonEscape(name)}","mimeType":"$FOLDER_MIME_TYPE","parents":["${jsonEscape(parentId)}"]}
            """.trimIndent().toRequestBody(JSON_MEDIA_TYPE)
        )
        return Json.parseToJsonElement(responseText).jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
            .ifBlank { error("Failed to create Drive folder $name.") }
    }

    private fun findChildFolder(accessToken: String, parentId: String, name: String): String? {
        val query = listOf(
            "'${escapeDriveQuery(parentId)}' in parents",
            "name = '${escapeDriveQuery(name)}'",
            "mimeType = '$FOLDER_MIME_TYPE'",
            "trashed = false"
        ).joinToString(" and ")
        val url = buildDriveListUrl(query)
        val responseText = authorizedJsonRequest(accessToken, url)
        val files = Json.parseToJsonElement(responseText).jsonObject["files"]?.let { it.jsonArray }.orEmpty()
        return files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private fun upsertFile(
        accessToken: String,
        parentId: String,
        photoId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        val existingId = findChildFileByPhotoId(accessToken, parentId, photoId)
        if (existingId != null) {
            uploadMultipartFile(
                accessToken = accessToken,
                fileId = existingId,
                parentId = parentId,
                photoId = photoId,
                name = name,
                mimeType = mimeType,
                bytes = bytes
            )
            ensurePublicReader(accessToken, existingId)
            return existingId
        }

        val resolvedName = resolveUniqueFileName(accessToken, parentId, name)
        val metadataResponse = uploadMultipartFile(
            accessToken = accessToken,
            fileId = null,
            parentId = parentId,
            photoId = photoId,
            name = resolvedName,
            mimeType = mimeType,
            bytes = bytes
        )
        val createdId = Json.parseToJsonElement(metadataResponse).jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
            .ifBlank { error("Failed to create Drive file $resolvedName.") }
        ensurePublicReader(accessToken, createdId)
        return createdId
    }

    private fun findChildFileByPhotoId(accessToken: String, parentId: String, photoId: String): String? {
        val query = listOf(
            "'${escapeDriveQuery(parentId)}' in parents",
            "appProperties has { key='mapsupervisionPhotoId' and value='${escapeDriveQuery(photoId)}' }",
            "mimeType != '$FOLDER_MIME_TYPE'",
            "trashed = false"
        ).joinToString(" and ")
        val url = buildDriveListUrl(query)
        val responseText = authorizedJsonRequest(accessToken, url)
        val files = Json.parseToJsonElement(responseText).jsonObject["files"]?.let { it.jsonArray }.orEmpty()
        return files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private fun findChildFile(accessToken: String, parentId: String, name: String): String? {
        val query = listOf(
            "'${escapeDriveQuery(parentId)}' in parents",
            "name = '${escapeDriveQuery(name)}'",
            "mimeType != '$FOLDER_MIME_TYPE'",
            "trashed = false"
        ).joinToString(" and ")
        val url = buildDriveListUrl(query)
        val responseText = authorizedJsonRequest(accessToken, url)
        val files = Json.parseToJsonElement(responseText).jsonObject["files"]?.let { it.jsonArray }.orEmpty()
        return files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private fun resolveUniqueFileName(accessToken: String, parentId: String, desiredName: String): String {
        val extension = desiredName.substringAfterLast('.', "")
        val baseName = if (extension.isBlank()) desiredName else desiredName.removeSuffix(".$extension")
        var resolvedName = desiredName
        var counter = 2
        while (findChildFile(accessToken, parentId, resolvedName) != null) {
            resolvedName = if (extension.isBlank()) {
                "$baseName ($counter)"
            } else {
                "$baseName ($counter).$extension"
            }
            counter += 1
        }
        return resolvedName
    }

    private fun uploadMultipartFile(
        accessToken: String,
        fileId: String?,
        parentId: String,
        photoId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        val metadataJson = buildString {
            append("{")
            append("\"name\":\"${jsonEscape(name)}\",")
            append("\"mimeType\":\"${jsonEscape(mimeType)}\",")
            append("\"appProperties\":{\"mapsupervisionPhotoId\":\"${jsonEscape(photoId)}\"}")
            if (fileId == null) {
                append(",\"parents\":[\"${jsonEscape(parentId)}\"]")
            }
            append("}")
        }
        val body = buildDriveMultipartUploadBody(metadataJson, mimeType, bytes)
        val uploadUrl = if (fileId == null) {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&fields=id"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart&supportsAllDrives=true&fields=id"
        }
        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $accessToken")
        if (fileId == null) {
            requestBuilder.post(body)
        } else {
            requestBuilder.put(body)
        }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(buildDriveFailureMessage("Failed to upload Drive media multipart", response.code, responseText))
            }
            return responseText
        }
    }

    internal fun buildDriveMultipartUploadBody(
        metadataJson: String,
        mimeType: String,
        bytes: ByteArray
    ): MultipartBody =
        MultipartBody.Builder()
            .setType(MULTIPART_RELATED_MEDIA_TYPE)
            .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            .build()

    private fun ensurePublicReader(accessToken: String, fileId: String) {
        val permissionList = authorizedJsonRequest(
            accessToken = accessToken,
            url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions?fields=permissions(id,type,role)&supportsAllDrives=true"
        )
        val permissions = Json.parseToJsonElement(permissionList).jsonObject["permissions"]?.let { it.jsonArray }.orEmpty()
        val alreadyPublic = permissions.any { permissionElement ->
            val permission = permissionElement.jsonObject
            permission["type"]?.jsonPrimitive?.content == "anyone" &&
                permission["role"]?.jsonPrimitive?.content in setOf("reader", "writer")
        }
        if (alreadyPublic) return

        authorizedJsonRequest(
            accessToken = accessToken,
            url = "https://www.googleapis.com/drive/v3/files/$fileId/permissions?supportsAllDrives=true&fields=id",
            method = "POST",
            body = """{"type":"anyone","role":"reader"}""".toRequestBody(JSON_MEDIA_TYPE)
        )
    }

    private fun authorizedJsonRequest(
        accessToken: String,
        url: String,
        method: String = "GET",
        body: okhttp3.RequestBody? = null
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
        when (method) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(buildDriveFailureMessage("Google Drive request failed for $url", response.code, responseText))
            }
            return responseText
        }
    }

    internal fun buildDriveFailureMessage(action: String, statusCode: Int, responseText: String): String {
        if (
            statusCode == 403 &&
            (
                responseText.contains("storageQuotaExceeded", ignoreCase = true) ||
                    responseText.contains("Service Accounts do not have storage quota", ignoreCase = true)
                )
        ) {
            return "$action ($statusCode): Google Drive root folder is in My Drive. Service account cannot upload there because it has no storage quota. Move root folder to a Shared drive and grant writer access to service account."
        }
        return "$action ($statusCode): $responseText"
    }

    private fun buildDriveListUrl(query: String): String =
        "https://www.googleapis.com/drive/v3/files?q=${urlEncode(query)}&fields=files(id,name)&pageSize=1&supportsAllDrives=true&includeItemsFromAllDrives=true"

    internal fun sanitizeSegment(value: String, default: String = "unknown"): String =
        value.trim()
            .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { default }

    internal fun buildMediaFileName(
        capturedAtEpochMs: Long,
        address: String?,
        captureNote: String?,
        extension: String
    ): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.US)
            .format(java.util.Date(capturedAtEpochMs))
        val segments = buildList {
            add(timestamp)
            address?.takeIf { it.isNotBlank() }?.let { add(sanitizeSegment(it, "")) }
            captureNote?.takeIf { it.isNotBlank() }?.let { add(sanitizeSegment(it, "")) }
        }.filter { it.isNotBlank() }
        val ext = extension.removePrefix(".")
        return "${segments.joinToString(" - ")}.$ext"
    }

    private fun extensionForMime(mimeType: String, fallback: String): String {
        val normalized = mimeType.lowercase()
        return when {
            "jpeg" in normalized || "jpg" in normalized -> "jpg"
            "png" in normalized -> "png"
            "webp" in normalized -> "webp"
            "mp4" in normalized -> "mp4"
            "quicktime" in normalized -> "mov"
            else -> fallback
        }
    }

    private fun publicDriveUrl(fileId: String): String =
        "https://drive.google.com/uc?export=view&id=${urlEncode(fileId)}"

    private fun escapeDriveQuery(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun jsonEscape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

    private fun base64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun parsePem(privateKeyPem: String): ByteArray {
        val sanitized = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(sanitized)
    }

    private data class DriveServiceAccount(
        val clientEmail: String,
        val privateKey: String
    )

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val MULTIPART_RELATED_MEDIA_TYPE = "multipart/related".toMediaType()
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}
