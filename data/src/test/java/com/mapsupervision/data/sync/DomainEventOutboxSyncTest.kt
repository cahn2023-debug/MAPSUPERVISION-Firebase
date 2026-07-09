package com.mapsupervision.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.domain.repository.DomainEvent
import com.mapsupervision.storage.DomainEventBusImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DomainEventOutboxSyncTest {

    @Test
    fun publish_persists_and_dispatches_outbox_event() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val bus = DomainEventBusImpl()
            val dispatcher = DomainEventOutboxDispatcher(database.eventOutboxDao())
            DomainEventOutboxWriter(
                domainEventBus = bus,
                eventOutboxDao = database.eventOutboxDao(),
                dispatcher = dispatcher
            )
            delay(50)

            bus.publish(
                DomainEvent.ImportCompleted(
                    projectId = "project-1",
                    importSessionId = "session-1",
                    importedFileId = "file-1",
                    featureCount = 3,
                    occurredAtEpochMs = 10L
                )
            )

            repeat(20) {
                if (database.eventOutboxDao().all().isNotEmpty()) return@repeat
                delay(50)
            }

            val events = database.eventOutboxDao().all()
            assertEquals(1, events.size)
            assertEquals("DISPATCHED", events.single().status)
            assertTrue(events.single().payloadJson.contains("\"importSessionId\":\"session-1\""))
        } finally {
            database.close()
        }
    }
}
