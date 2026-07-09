package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mapsupervision.data.db.entity.EventOutboxEntity

@Dao
interface EventOutboxDao {
    @Upsert
    suspend fun upsert(event: EventOutboxEntity)

    @Upsert
    suspend fun upsertAll(events: List<EventOutboxEntity>)

    @Query("SELECT * FROM event_outbox ORDER BY createdAtEpochMs")
    suspend fun all(): List<EventOutboxEntity>

    @Query("SELECT * FROM event_outbox WHERE status = 'PENDING' AND availableAtEpochMs <= :nowEpochMs ORDER BY availableAtEpochMs, createdAtEpochMs LIMIT :limit")
    suspend fun pending(nowEpochMs: Long, limit: Int): List<EventOutboxEntity>

    @Query("SELECT * FROM event_outbox WHERE status = 'PENDING' AND projectId = :projectId AND availableAtEpochMs <= :nowEpochMs ORDER BY availableAtEpochMs, createdAtEpochMs LIMIT :limit")
    suspend fun pendingByProject(projectId: String, nowEpochMs: Long, limit: Int): List<EventOutboxEntity>

    @Query("UPDATE event_outbox SET status = :status, dispatchedAtEpochMs = :dispatchedAtEpochMs WHERE id = :id")
    suspend fun markStatus(id: String, status: String, dispatchedAtEpochMs: Long?)
}
