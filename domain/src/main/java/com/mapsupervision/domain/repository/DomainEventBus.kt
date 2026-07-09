package com.mapsupervision.domain.repository

import com.mapsupervision.domain.model.Feature
import kotlinx.coroutines.flow.SharedFlow

sealed interface DomainEvent {
    val projectId: String?
    val occurredAtEpochMs: Long

    data class ImportCompleted(
        override val projectId: String?,
        val importSessionId: String,
        val importedFileId: String?,
        val featureCount: Int,
        override val occurredAtEpochMs: Long
    ) : DomainEvent

    data class ImportDeleted(
        override val projectId: String?,
        val importSessionId: String?,
        val importedFileId: String?,
        override val occurredAtEpochMs: Long
    ) : DomainEvent

    data class ImportMerged(
        override val projectId: String?,
        val sourceImportFileIds: List<String>,
        val mergedImportFileId: String,
        override val occurredAtEpochMs: Long
    ) : DomainEvent

    data class GeometryUpdated(
        override val projectId: String?,
        val feature: Feature,
        override val occurredAtEpochMs: Long
    ) : DomainEvent

    data class ProgressChanged(
        override val projectId: String?,
        val nodeCode: String,
        val workName: String,
        val actualQty: Double,
        override val occurredAtEpochMs: Long
    ) : DomainEvent

    data class TaskChanged(
        override val projectId: String?,
        val objectCode: String,
        val taskId: String,
        val action: String,
        override val occurredAtEpochMs: Long
    ) : DomainEvent

    data class MaterialUpdated(
        override val projectId: String?,
        val nodeCode: String,
        val materialName: String,
        val actualQty: Double,
        override val occurredAtEpochMs: Long
    ) : DomainEvent
}

interface DomainEventBus {
    val events: SharedFlow<DomainEvent>

    suspend fun publish(event: DomainEvent)
}

