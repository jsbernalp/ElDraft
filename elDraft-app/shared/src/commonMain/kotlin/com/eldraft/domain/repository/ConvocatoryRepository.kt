package com.eldraft.domain.repository

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.MapEvent
import kotlinx.coroutines.flow.Flow

/** Operaciones sobre convocatorias (El Draft) y eventos del mapa en tiempo real. */
interface ConvocatoryRepository {

    /** Crea una convocatoria y devuelve la versión persistida (con id). */
    suspend fun create(request: CreateConvocatoryRequest): Convocatory

    /** Convocatorias activas dentro del radio (metros) del punto dado. */
    suspend fun getNearby(lat: Double, lng: Double, radius: Double = 5000.0): List<Convocatory>

    /** Convocatorias del organizador autenticado. */
    suspend fun getMine(): List<Convocatory>

    /** Detalle de una convocatoria por id. */
    suspend fun getById(id: String): Convocatory

    /** Stream de eventos del mapa (new_pin / pin_closed) vía WebSocket. */
    fun observeMapEvents(lat: Double, lng: Double, radius: Double = 5000.0): Flow<MapEvent>
}
