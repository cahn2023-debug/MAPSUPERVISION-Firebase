package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class WorkspaceCaptureDedupTest {

    @Test
    fun `direct capture save deduper only saves the same file once`() = runBlocking {
        val deduper = DirectCaptureSaveDeduper()
        var saveCount = 0

        val first = deduper.saveOnce("/tmp/capture-1.jpg") {
            saveCount++
            true
        }
        val second = deduper.saveOnce("/tmp/capture-1.jpg") {
            saveCount++
            true
        }

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, saveCount)
    }

    @Test
    fun `direct capture save deduper allows different files`() = runBlocking {
        val deduper = DirectCaptureSaveDeduper()
        var saveCount = 0

        assertTrue(deduper.saveOnce("/tmp/capture-a.jpg") {
            saveCount++
            true
        })
        assertTrue(deduper.saveOnce("/tmp/capture-b.jpg") {
            saveCount++
            true
        })

        assertEquals(2, saveCount)
    }
}
