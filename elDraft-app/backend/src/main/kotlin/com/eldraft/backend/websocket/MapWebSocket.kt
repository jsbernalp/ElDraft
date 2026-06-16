package com.eldraft.backend.websocket

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MapEvent(
    val event: String,
    val data: MapPinData
)

@Serializable
data class MapPinData(
    val id: String,
    val lat: Double,
    val lng: Double,
    val slots: Int,
    val format: String,
    val ambiente: String
)

// Sesiones activas: sessionId -> (connection, userId, lat, lng, radius)
object MapSessionRegistry {
    data class Session(
        val connection: DefaultWebSocketSession,
        val lat: Double,
        val lng: Double,
        val radius: Double,
        val userId: String? = null,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun register(id: String, session: Session) = sessions.put(id, session)
    fun unregister(id: String) = sessions.remove(id)

    /**
     * Reenvía el evento a las sesiones dentro del radio, omitiendo la de
     * [excludeUserId] (p. ej. el propio organizador que creó el pin: no debe
     * ver su convocatoria en el mapa).
     */
    suspend fun broadcast(
        event: MapEvent,
        originLat: Double,
        originLng: Double,
        excludeUserId: String? = null,
    ) {
        val json = Json.encodeToString(event)
        sessions.values
            .filter { it.userId == null || it.userId != excludeUserId }
            .filter { isWithinRadius(it.lat, it.lng, originLat, originLng, it.radius) }
            .forEach { runCatching { it.connection.send(Frame.Text(json)) } }
    }

    private fun isWithinRadius(sLat: Double, sLng: Double, eLat: Double, eLng: Double, radius: Double): Boolean {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(eLat - sLat)
        val dLng = Math.toRadians(eLng - sLng)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(sLat)) * Math.cos(Math.toRadians(eLat)) *
                Math.sin(dLng / 2).let { it * it }
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) <= radius
    }
}

fun Routing.mapWebSocketRoute() {
    webSocket("/ws/map") {
        val lat = call.request.queryParameters["lat"]?.toDoubleOrNull() ?: run { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "lat required")); return@webSocket }
        val lng = call.request.queryParameters["lng"]?.toDoubleOrNull() ?: run { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "lng required")); return@webSocket }
        val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 5000.0
        // Identidad opcional: si viene, permite omitir los pines propios.
        val userId = call.request.queryParameters["userId"]?.takeIf { it.isNotBlank() }

        val sessionId = java.util.UUID.randomUUID().toString()
        MapSessionRegistry.register(sessionId, MapSessionRegistry.Session(this, lat, lng, radius, userId))

        try {
            for (frame in incoming) { /* keep-alive, ignorar mensajes entrantes */ }
        } catch (_: ClosedReceiveChannelException) {
        } finally {
            MapSessionRegistry.unregister(sessionId)
        }
    }
}
