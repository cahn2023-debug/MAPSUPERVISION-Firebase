package com.mapsupervision.data.sync

import com.mapsupervision.domain.model.MediaType
import java.io.File
import java.nio.file.Files
import okhttp3.OkHttpClient
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
}
