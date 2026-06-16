package com.eldraft.domain.usecase.convocatory

import com.eldraft.data.models.MapEvent
import com.eldraft.domain.repository.ConvocatoryRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stream de eventos del mapa en tiempo real (new_pin / pin_closed) para el
 * radio observado. Encapsula la suscripción al WebSocket.
 */
class ObserveMapEventsUseCase(
    private val repository: ConvocatoryRepository,
) {
    operator fun invoke(lat: Double, lng: Double, radius: Double = 5000.0, userId: String? = null): Flow<MapEvent> =
        repository.observeMapEvents(lat, lng, radius, userId)
}
