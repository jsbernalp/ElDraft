package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
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
data class CreateConvocatoryRequest(
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val slotsNeeded: Int,
    val positionRequired: String,
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val scheduledAt: String,
)

@Serializable
data class ConvocatoryDto(
    val id: String,
    val organizerId: String,
    val lat: Double,
    val lng: Double,
    val addressText: String? = null,
    val slotsNeeded: Int,
    val positionRequired: String,
    val fee: Double = 0.0,
    val format: String,
    val ambiente: String,
    val status: String = "active",
    val scheduledAt: String,
)

fun Route.convocatoryRoutes() {
    val service = application.get<ConvocatoryService>()

    route("/convocatories") {

        // Pines en un radio (PostGIS ST_DWithin). Público.
        get("/nearby") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lat es obligatorio")
            val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lng es obligatorio")
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 5000.0
            val results = service.getNearby(lat, lng, radius).map { it.toDto() }
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
                        slotsNeeded = body.slotsNeeded,
                        positionRequired = body.positionRequired,
                        fee = body.fee,
                        format = body.format,
                        ambiente = body.ambiente,
                        scheduledAt = body.scheduledAt,
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
                )

                call.respond(HttpStatusCode.Created, created.toDto())
            }

            // Mis convocatorias (organizador autenticado).
            get("/mine") {
                val organizerId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.getMine(organizerId).map { it.toDto() })
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
    fee = fee,
    format = format,
    ambiente = ambiente,
    status = status,
    scheduledAt = scheduledAt,
)
