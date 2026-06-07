package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class GenerateQrRequest(val convocatoryId: String)

@Serializable
data class ScanQrRequest(val qrCode: String, val convocatoryId: String)

fun Route.attendanceRoutes() {
    route("/attendance") {
        post("/generate-qr") {
            val body = call.receive<GenerateQrRequest>()
            // TODO: generar QR con expiración de 10 minutos
            call.respond(HttpStatusCode.Created, mapOf("qrCode" to "qr-placeholder-${body.convocatoryId}", "expiresInSeconds" to 600))
        }

        post("/scan") {
            val body = call.receive<ScanQrRequest>()
            // TODO: validar QR + actualizar attendance_pct del jugador
            call.respond(HttpStatusCode.OK, mapOf("validated" to true, "playerId" to "placeholder"))
        }
    }
}
