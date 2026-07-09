package com.mapsupervision.data.sync

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FirebaseSyncMetadataStoreTest {

    @Test
    fun deviceId_persists_between_reads() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("firebase_sync_meta", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val store = FirebaseSyncMetadataStore(context)

        val first = store.deviceId()
        val second = store.deviceId()

        assertTrue(first.isNotBlank())
        assertEquals(first, second)
    }

    @Test
    fun push_and_pull_cursors_round_trip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = FirebaseSyncMetadataStore(context)

        store.setLastPushedAt("project-1", "task", 123L)
        store.setLastPulledAt("project-1", "task", 456L)

        assertEquals(123L, store.lastPushedAt("project-1", "task"))
        assertEquals(456L, store.lastPulledAt("project-1", "task"))
    }
}
