package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.plugins.optionalUserId
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.PositionSlot
import com.eldraft.backend.service.ConvocatoryCancelForbidden
import com.eldraft.backend.service.ConvocatoryService
import com.eldraft.backend.websocket.MapEvent
import com.eldraft.backend.websocket.MapPinData
import com.eldraft.backend.websocket.MapSessionRegistry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import java.util.UUID

@Serializable
data class PositionSlotDto(
    val position: String,
    val slots: Int,
)

@Serializable
data class CreateConvocatoryRequest(
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val positionSlots: List<PositionSlotDto>,
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val scheduledAt: String,
    /** Confirma cancelar las postulaciones del organizador que choquen de horario. */
    val cancelConflicts: Boolean = false,
)

@Serializable
data class CancelConvocatoryRequest(val reason: String)

@Serializable
data class ConvocatoryDto(
    val id: String,
    val organizerId: String,
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val slotsNeeded: Int,
    val positionRequired: String,
    val positionSlots: List<PositionSlotDto> = emptyList(),
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val status: String = "active",
    val scheduledAt: String,
    val organizerNoShow: Boolean = false,
    val pendingCount: Int = 0,
    val cancellationReason: String? = null,
    val cancelledAt: String? = null,
)

fun Route.convocatoryRoutes() {
    val service = application.get<ConvocatoryService>()

    route("/convocatories") {

        // Pines en un radio (PostGIS ST_DWithin). Público; si viene un token
        // válido, se ocultan las convocatorias del propio organizador.
        get("/nearby") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lat es obligatorio")
            val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lng es obligatorio")
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 5000.0
            val viewer = call.optionalUserId()
            val results = service.getNearby(lat, lng, radius, excludeOrganizerId = viewer).map { it.toDto() }
            call.respond(HttpStatusCode.OK, results)
        }

        authenticate("firebase-auth") {
            // Crear convocatoria (El Draft) + emitir new_pin por WebSocket.
            post {
                val organizerId = call.currentUserId()
                val body = call.receive<CreateConvocatoryRequest>()

                val created = service.create(
                    ConvocatoryCreate(
                        organizerId = organizerId,
                        lat = body.lat,
                        lng = body.lng,
                        addressText = body.addressText,
                        positionSlots = body.positionSlots.map { PositionSlot(it.position, it.slots) },
                        fee = body.fee,
                        format = body.format,
                        ambiente = body.ambiente,
                        scheduledAt = body.scheduledAt,
                        cancelConflicts = body.cancelConflicts,
                    )
                )

                // Notificar a los clientes con el mapa abierto en el radio.
                MapSessionRegistry.broadcast(
                    event = MapEvent(
                        event = "new_pin",
                        data = MapPinData(
                            id = created.id.toString(),
                            lat = created.lat,
                            lng = created.lng,
                            slots = created.slotsNeeded,
                            format = created.format,
                            ambiente = created.ambiente,
                        ),
                    ),
                    originLat = created.lat,
                    originLng = created.lng,
                    // No le reenvíes el pin a su propio mapa.
                    excludeUserId = organizerId.toString(),
                )

                call.respond(HttpStatusCode.Created, created.toDto())
            }

            // Mis convocatorias (organizador autenticado).
            get("/mine") {
                val organizerId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.getMine(organizerId).map { it.toDto() })
            }
        }

        authenticate("firebase-auth") {
            // Cancelar convocatoria (solo el organizador, antes de que empiece).
            delete("/{id}") {
                val callerId = call.currentUserId()
                val id = parseConvocatoryId(call.parameters["id"])
                val body = call.receive<CancelConvocatoryRequest>()
                service.cancel(id, callerId, body.reason)

                // Notificar al mapa que el pin desapareció.
                val convocatory = service.getById(id)
                if (convocatory != null) {
                    MapSessionRegistry.broadcast(
                        event = com.eldraft.backend.websocket.MapEvent(
                            event = "pin_closed",
                            data = com.eldraft.backend.websocket.MapPinData(
                                id = convocatory.id.toString(),
                                lat = convocatory.lat,
                                lng = convocatory.lng,
                                slots = convocatory.slotsNeeded,
                                format = convocatory.format,
                                ambiente = convocatory.ambiente,
                            ),
                        ),
                        originLat = convocatory.lat,
                        originLng = convocatory.lng,
                        excludeUserId = callerId.toString(),
                    )
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }

        // Detalle de una convocatoria. Público.
        get("/{id}") {
            val id = parseConvocatoryId(call.parameters["id"])
            val convocatory = service.getById(id)
                ?: throw NoSuchElementException("Convocatoria no encontrada")
            call.respond(HttpStatusCode.OK, convocatory.toDto())
        }
    }
}

private fun parseConvocatoryId(raw: String?): UUID {
    if (raw.isNullOrBlank()) throw IllegalArgumentException("Falta el id de la convocatoria")
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("El id de la convocatoria no es un UUID válido")
    }
}

private fun ConvocatoryRecord.toDto() = ConvocatoryDto(
    id = id.toString(),
    organizerId = organizerId.toString(),
    lat = lat,
    lng = lng,
    addressText = addressText,
    slotsNeeded = slotsNeeded,
    positionRequired = positionRequired,
    positionSlots = positionSlots.map { PositionSlotDto(it.position, it.slots) },
    fee = fee,
    format = format,
    ambiente = ambiente,
    status = status,
    scheduledAt = scheduledAt,
    organizerNoShow = organizerNoShow,
    pendingCount = pendingCount,
    cancellationReason = cancellationReason,
    cancelledAt = cancelledAt,
)
