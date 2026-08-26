package com.mapsupervision.data.sync

import com.mapsupervision.domain.model.MediaType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveMediaUploadClientTest {
    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("drive-media-upload-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun upload_postsMultipartAndReturnsRemoteUrl() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"data":{"remoteUrl":"https://drive.google.com/uc?export=view&id=file1"}}""")
        )
        val original = File(tempDir, "photo.jpg").apply { writeText("image") }
        val thumbnail = File(tempDir, "photo-thumb.jpg").apply { writeText("thumb") }

        val url = DriveMediaUploadClient(
            OkHttpClient(),
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        ).upload(
            server.url("/").toString(),
            DriveMediaUploadRequest(
                projectId = "project1",
                projectName = "Project Alpha",
                rootFolderId = "drive-folder-123",
                token = "token1",
                photoId = "photo1",
                objectType = DriveMediaObjectType.ROUTE,
                objectCode = "R-01",
                mediaType = MediaType.IMAGE,
                mimeType = "image/jpeg",
                capturedAtEpochMs = 123L,
                address = "Hanoi",
                captureNote = "test note",
                originalFile = original,
                thumbnailFile = thumbnail
            )
        )

        assertEquals("https://drive.google.com/uc?export=view&id=file1", url)
        val recorded = server.takeRequest()
        assertEquals("/api/projects/project1/media", recorded.path)
        assertEquals("Bearer token1", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"photoId\""))
        assertTrue(body.contains("photo1"))
        assertTrue(body.contains("name=\"objectType\""))
        assertTrue(body.contains("ROUTE"))
        assertTrue(body.contains("name=\"original\""))
        assertTrue(body.contains("name=\"thumbnail\""))
    }

    @Test(expected = IllegalStateException::class)
    fun upload_throwsWhenServerFails() {
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("""{"success":false,"error":{"code":"UPLOAD_FAILED","message":"failed"}}""")
        )
        val original = File(tempDir, "photo.jpg").apply { writeText("image") }

        DriveMediaUploadClient(
            OkHttpClient(),
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        ).upload(
            server.url("/").toString(),
            DriveMediaUploadRequest(
                projectId = "project1",
                projectName = "Project Alpha",
                rootFolderId = "drive-folder-123",
                token = "token1",
                photoId = "photo1",
                objectType = DriveMediaObjectType.NODE,
                objectCode = "N-01",
                mediaType = MediaType.IMAGE,
                mimeType = "image/jpeg",
                capturedAtEpochMs = 123L,
                address = null,
                captureNote = null,
                originalFile = original,
                thumbnailFile = null
            )
        )
    }

    @Test
    fun buildMediaFileName_formatsTimestampAddressAndNote() {
        val client = DriveMediaUploadClient(
            OkHttpClient(),
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        )

        val name = client.buildMediaFileName(
            capturedAtEpochMs = 0L,
            address = "123 Tran Hung Dao / Quan 1",
            captureNote = "Ghi chu: test",
            extension = "jpg"
        )

        assertEquals("1970-01-01 07.00.00 - 123 Tran Hung Dao Quan 1 - Ghi chu test.jpg", name)
    }

    @Test
    fun buildDriveFailureMessage_explainsServiceAccountQuotaLimit() {
        val client = DriveMediaUploadClient(
            OkHttpClient(),
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        )

        val message = client.buildDriveFailureMessage(
            action = "Failed to upload Drive media multipart",
            statusCode = 403,
            responseText = """{"error":{"errors":[{"reason":"storageQuotaExceeded","message":"Service Accounts do not have storage quota."}]}}"""
        )

        assertTrue(message.contains("Shared drive"))
        assertTrue(message.contains("Service account cannot upload there"))
    }

    @Test
    fun buildDriveMultipartUploadBody_doesNotSetForbiddenPartHeaders() {
        val client = DriveMediaUploadClient(
            OkHttpClient(),
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        )

        val body = client.buildDriveMultipartUploadBody(
            metadataJson = """{"name":"photo.jpg"}""",
            mimeType = "image/jpeg",
            bytes = "image".toByteArray()
        )

        assertEquals(2, body.size)
        assertEquals(null, body.part(0).headers)
        assertEquals(null, body.part(1).headers)
    }

    @Test
    fun uploadMultipartFile_usesPatchForExistingDriveFile() {
        val capturedRequest = AtomicReference<Request>()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    capturedRequest.set(chain.request())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"id":"file1"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            )
            .build()
        val client = DriveMediaUploadClient(
            httpClient,
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        )
        val method = DriveMediaUploadClient::class.java.getDeclaredMethod(
            "uploadMultipartFile",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            ByteArray::class.java
        )
        method.isAccessible = true

        method.invoke(
            client,
            "token1",
            "file1",
            "parent1",
            "photo1",
            "photo.jpg",
            "image/jpeg",
            "image".toByteArray()
        )

        assertEquals("PATCH", capturedRequest.get()?.method)
    }

    @Test
    fun pruneOldSnapshots_deletesSnapshotsOlderThan5Minutes() {
        val deletedUrls = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val tNewest = java.time.Instant.ofEpochMilli(now).toString()
        val t3Min = java.time.Instant.ofEpochMilli(now - 3 * 60 * 1000L).toString()
        val t6Min = java.time.Instant.ofEpochMilli(now - 6 * 60 * 1000L).toString()
        val t10Min = java.time.Instant.ofEpochMilli(now - 10 * 60 * 1000L).toString()

        val listResponseBody = """
            {
                "files": [
                    {"id": "file-newest", "name": "snapshot_1.json", "createdTime": "$tNewest"},
                    {"id": "file-3min", "name": "snapshot_2.json", "createdTime": "$t3Min"},
                    {"id": "file-6min", "name": "snapshot_3.json", "createdTime": "$t6Min"},
                    {"id": "file-10min", "name": "snapshot_4.json", "createdTime": "$t10Min"}
                ]
            }
        """.trimIndent()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val req = chain.request()
                    if (req.method == "GET") {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(listResponseBody.toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    } else if (req.method == "DELETE") {
                        deletedUrls.add(req.url.toString())
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    } else {
                        chain.proceed(req)
                    }
                }
            )
            .build()

        val client = DriveMediaUploadClient(
            httpClient,
            DriveDirectUploadConfig(enabled = false, rootFolderId = "", serviceAccountJsonBase64 = "")
        )

        val deleted = client.pruneOldSnapshots(
            accessToken = "token123",
            snapshotsFolderId = "snap-folder-1",
            maxAgeMs = 5 * 60 * 1000L
        )

        assertEquals(listOf("file-6min", "file-10min"), deleted)
        assertEquals(2, deletedUrls.size)
        assertTrue(deletedUrls.any { it.contains("file-6min") })
        assertTrue(deletedUrls.any { it.contains("file-10min") })
    }
}

