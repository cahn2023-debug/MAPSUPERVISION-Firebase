package com.mapsupervision.data.sync

import com.mapsupervision.data.db.dao.EventOutboxDao
import com.mapsupervision.data.db.entity.EventOutboxEntity
import com.mapsupervision.domain.repository.DomainEvent
import com.mapsupervision.domain.repository.DomainEventBus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class DomainEventOutboxWriter @Inject constructor(
    domainEventBus: DomainEventBus,
    private val eventOutboxDao: EventOutboxDao,
    private val dispatcher: DomainEventOutboxDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            domainEventBus.events.collect { event ->
                eventOutboxDao.upsert(event.toEntity())
                dispatcher.dispatchPending(projectId = event.projectId)
            }
        }
    }

    private fun DomainEvent.toEntity(): EventOutboxEntity {
        val now = System.currentTimeMillis()
        return EventOutboxEntity(
            id = "${eventType()}-${occurredAtEpochMs}-$now-${projectId.orEmpty()}",
            projectId = projectId,
            eventType = eventType(),
            payloadJson = toPayloadJson(),
            status = "PENDING",
            availableAtEpochMs = now,
            createdAtEpochMs = now
        )
    }

    private fun DomainEvent.eventType(): String = when (this) {
        is DomainEvent.ImportCompleted -> "ImportCompleted"
        is DomainEvent.ImportDeleted -> "ImportDeleted"
        is DomainEvent.ImportMerged -> "ImportMerged"
        is DomainEvent.GeometryUpdated -> "GeometryUpdated"
        is DomainEvent.ProgressChanged -> "ProgressChanged"
        is DomainEvent.TaskChanged -> "TaskChanged"
        is DomainEvent.MaterialUpdated -> "MaterialUpdated"
    }

    private fun DomainEvent.toPayloadJson(): String {
        val json = JSONObject()
            .put("eventType", eventType())
            .put("projectId", projectId)
            .put("occurredAtEpochMs", occurredAtEpochMs)
        when (this) {
            is DomainEvent.ImportCompleted -> {
                json.put("importSessionId", importSessionId)
                json.put("importedFileId", importedFileId)
                json.put("featureCount", featureCount)
            }
            is DomainEvent.ImportDeleted -> {
                json.put("importSessionId", importSessionId)
                json.put("importedFileId", importedFileId)
            }
            is DomainEvent.ImportMerged -> {
                json.put("sourceImportFileIds", JSONArray(sourceImportFileIds))
                json.put("mergedImportFileId", mergedImportFileId)
            }
            is DomainEvent.GeometryUpdated -> {
                json.put("featureId", feature.id)
                json.put("featureCode", feature.businessCode)
                json.put("geometryType", when (val geometry = feature.geometry) {
                    is com.mapsupervision.domain.model.Geometry.Point -> "Point"
                    is com.mapsupervision.domain.model.Geometry.Line -> "Line"
                    is com.mapsupervision.domain.model.Geometry.Polygon -> "Polygon"
                    is com.mapsupervision.domain.model.Geometry.MultiLine -> "MultiLine"
                    is com.mapsupervision.domain.model.Geometry.MultiPolygon -> "MultiPolygon"
                })
            }
            is DomainEvent.ProgressChanged -> {
                json.put("nodeCode", nodeCode)
                json.put("workName", workName)
                json.put("actualQty", actualQty)
            }
            is DomainEvent.TaskChanged -> {
                json.put("objectCode", objectCode)
                json.put("taskId", taskId)
                json.put("action", action)
            }
            is DomainEvent.MaterialUpdated -> {
                json.put("nodeCode", nodeCode)
                json.put("materialName", materialName)
                json.put("actualQty", actualQty)
            }
        }
        return json.toString()
    }
}
