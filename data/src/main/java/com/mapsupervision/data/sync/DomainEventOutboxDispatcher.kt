package com.mapsupervision.data.sync

import com.mapsupervision.data.db.dao.EventOutboxDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainEventOutboxDispatcher @Inject constructor(
    private val eventOutboxDao: EventOutboxDao
) {
    suspend fun dispatchPending(projectId: String? = null, limit: Int = 50): Int {
        val now = System.currentTimeMillis()
        val events = if (projectId.isNullOrBlank()) {
            eventOutboxDao.pending(nowEpochMs = now, limit = limit)
        } else {
            eventOutboxDao.pendingByProject(projectId = projectId, nowEpochMs = now, limit = limit)
        }
        events.forEach { event ->
            eventOutboxDao.markStatus(
                id = event.id,
                status = "DISPATCHED",
                dispatchedAtEpochMs = now
            )
        }
        return events.size
    }
}
