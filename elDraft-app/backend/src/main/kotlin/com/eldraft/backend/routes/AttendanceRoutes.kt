package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.service.AttendanceService
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
data class GenerateQrRequest(val convocatoryId: String)

@Serializable
data class GenerateQrResponse(val qrCode: String, val expiresInSeconds: Long)

@Serializable
data class ScanQrRequest(val qrCode: String)

@Serializable
data class ScanQrResponse(val validated: Boolean, val convocatoryId: String, val playerId: String)

fun Route.attendanceRoutes() {
    val service = application.get<AttendanceService>()

    authenticate("firebase-auth") {
        route("/attendance") {

            // El organizador genera el QR de su convocatoria (expira en 10 min).
            post("/generate-qr") {
                val requesterId = call.currentUserId()
                val body = call.receive<GenerateQrRequest>()
                val convocatoryId = parseUuid(body.convocatoryId, "convocatoryId")
                val qr = service.generateQr(convocatoryId, requesterId)
                call.respond(
                    HttpStatusCode.Created,
                    GenerateQrResponse(qrCode = qr.token, expiresInSeconds = qr.expiresInSeconds),
                )
            }

            // El jugador aprobado escanea el QR para registrar su asistencia.
            post("/scan") {
                val playerId = call.currentUserId()
                val body = call.receive<ScanQrRequest>()
                val result = service.scan(body.qrCode, playerId)
                call.respond(
                    HttpStatusCode.OK,
                    ScanQrResponse(
                        validated = result.validated,
                        convocatoryId = result.convocatoryId,
                        playerId = result.playerId,
                    ),
                )
            }
        }
    }
}

private fun parseUuid(raw: String?, field: String): UUID {
    if (raw.isNullOrBlank()) throw IllegalArgumentException("Falta $field")
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("$field no es un UUID válido")
    }
}
