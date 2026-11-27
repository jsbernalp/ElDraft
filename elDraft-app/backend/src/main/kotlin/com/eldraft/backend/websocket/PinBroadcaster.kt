package com.eldraft.backend.websocket

import com.eldraft.backend.repository.ConvocatoryRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Difusión de pines al mapa. Es una interfaz para que los services (y sus tests)
 * no dependan del registro global de sesiones WebSocket: así se puede contar
 * cuántos `new_pin` salen y comprobar que la fase privada no filtra nada.
 */
interface PinBroadcaster {
    /** `slots` es lo que muestra el pin: los cupos libres, no el total. */
    fun newPin(c: ConvocatoryRecord, slots: Int, excludeUserId: String?)
    fun pinClosed(c: ConvocatoryRecord, excludeUserId: String?)
}

/** Para construir services sin mapa (tests viejos, scripts). */
object NoopPinBroadcaster : PinBroadcaster {
    override fun newPin(c: ConvocatoryRecord, slots: Int, excludeUserId: String?) = Unit
    override fun pinClosed(c: ConvocatoryRecord, excludeUserId: String?) = Unit
}

/**
 * Implementación real sobre [MapSessionRegistry]. `broadcast` es suspend y los
 * services no lo son, así que se lanza en un scope propio (fire-and-forget).
 */
class MapPinBroadcaster : PinBroadcaster {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newPin(c: ConvocatoryRecord, slots: Int, excludeUserId: String?) = send("new_pin", c, slots, excludeUserId)

    override fun pinClosed(c: ConvocatoryRecord, excludeUserId: String?) = send("pin_closed", c, c.slotsNeeded, excludeUserId)

    private fun send(event: String, c: ConvocatoryRecord, slots: Int, excludeUserId: String?) {
        scope.launch {
            runCatching {
                MapSessionRegistry.broadcast(
                    event = MapEvent(
                        event = event,
                        data = MapPinData(
                            id = c.id.toString(),
                            lat = c.lat,
                            lng = c.lng,
                            slots = slots,
                            format = c.format,
                            ambiente = c.ambiente,
                        ),
                    ),
                    originLat = c.lat,
                    originLng = c.lng,
                    excludeUserId = excludeUserId,
                )
            }
        }
    }
}
