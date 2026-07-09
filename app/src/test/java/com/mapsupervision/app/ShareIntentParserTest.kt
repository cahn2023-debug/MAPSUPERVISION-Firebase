package com.mapsupervision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentParserTest {

    @Test
    fun `buildIncomingShareUriStrings accepts single image share`() {
        val payload = buildIncomingShareUriStrings(
            action = "android.intent.action.SEND",
            mimeType = "image/jpeg",
            uriStrings = listOf("content://example.com/photo-1")
        )

        assertEquals(listOf("content://example.com/photo-1"), payload)
    }

    @Test
    fun `buildIncomingShareUriStrings accepts multiple image share and dedupes`() {
        val payload = buildIncomingShareUriStrings(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "image/*",
            uriStrings = listOf(
                "content://example.com/photo-1",
                "content://example.com/photo-2",
                "content://example.com/photo-1"
            )
        )

        assertEquals(listOf("content://example.com/photo-1", "content://example.com/photo-2"), payload)
    }

    @Test
    fun `buildIncomingShareUriStrings accepts single video share`() {
        val payload = buildIncomingShareUriStrings(
            action = "android.intent.action.SEND",
            mimeType = "video/mp4",
            uriStrings = listOf("content://example.com/video-1")
        )

        assertEquals(listOf("content://example.com/video-1"), payload)
    }

    @Test
    fun `buildIncomingShareUriStrings accepts wildcard media share when resolver says image or video`() {
        val payload = buildIncomingShareUriStrings(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = listOf(
                "content://example.com/photo-1",
                "content://example.com/video-1",
                "content://example.com/doc-1"
            ),
            resolveMimeType = { uri ->
                when (uri) {
                    "content://example.com/photo-1" -> "image/jpeg"
                    "content://example.com/video-1" -> "video/mp4"
                    else -> "text/plain"
                }
            }
        )

        assertEquals(
            listOf("content://example.com/photo-1", "content://example.com/video-1"),
            payload
        )
    }

    @Test
    fun `buildIncomingShareUriStrings ignores non image shares`() {
        val payload = buildIncomingShareUriStrings(
            action = "android.intent.action.SEND",
            mimeType = "text/plain",
            uriStrings = listOf("content://example.com/photo-1")
        )

        assertNull(payload)
    }
}
