package com.mapsupervision.storage

import com.mapsupervision.domain.repository.DomainEvent
import com.mapsupervision.domain.repository.DomainEventBus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class DomainEventBusImpl @Inject constructor() : DomainEventBus {
    private val _events = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    override val events: SharedFlow<DomainEvent> = _events.asSharedFlow()

    override suspend fun publish(event: DomainEvent) {
        _events.emit(event)
    }
}

