package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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
    val scheduledAt: String
)

fun Route.convocatoryRoutes() {
    route("/convocatories") {
        post {
            val body = call.receive<CreateConvocatoryRequest>()
            // TODO: crear convocatoria + emitir evento WebSocket al mapa
            call.respond(HttpStatusCode.Created, mapOf("message" to "convocatory created", "data" to body))
        }

        get("/nearby") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lat required")
            val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lng required")
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 5000.0
            // TODO: consulta PostGIS ST_DWithin
            call.respond(HttpStatusCode.OK, mapOf("lat" to lat, "lng" to lng, "radius" to radius, "results" to emptyList<Any>()))
        }

        get("/mine") {
            // TODO: obtener convocatorias del organizador autenticado
            call.respond(HttpStatusCode.OK, emptyList<Any>())
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing id")
            // TODO: obtener detalle de convocatoria
            call.respond(HttpStatusCode.OK, mapOf("id" to id))
        }
    }
}
