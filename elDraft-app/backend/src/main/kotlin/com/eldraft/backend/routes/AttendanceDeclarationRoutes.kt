package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.repository.PlayerAttendanceRow
import com.eldraft.backend.service.AttendanceDeclarationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import java.util.UUID

/** Una fila de la lista de asistencia que ve el organizador. */
@Serializable
data class PlayerAttendanceDto(
    val playerId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val scanned: Boolean,
    val markedNoShow: Boolean,
)

/** Cuerpo de la declaración: ids de convocados que NO llegaron. */
@Serializable
data class DeclareAttendanceRequest(val absentPlayerIds: List<String>)

fun Route.attendanceDeclarationRoutes() {
    val service = application.get<AttendanceDeclarationService>()

    authenticate("firebase-auth") {
        route("/convocatories/{convocatoryId}") {

            // El organizador ve la lista de aprobados con su estado de asistencia.
            get("/attendance-list") {
                val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
                val requesterId = call.currentUserId()
                val rows = service.attendanceList(convocatoryId, requesterId).map { it.toDto() }
                call.respond(HttpStatusCode.OK, rows)
            }

            // El organizador declara quién no llegó (reemplaza la lista previa).
            post("/declare-attendance") {
                val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
                val requesterId = call.currentUserId()
                val body = call.receive<DeclareAttendanceRequest>()
                val absentIds = body.absentPlayerIds.map { parseUuid(it, "playerId") }.toSet()
                val rows = service.declare(convocatoryId, requesterId, absentIds).map { it.toDto() }
                call.respond(HttpStatusCode.OK, rows)
            }
        }
    }
}

private fun PlayerAttendanceRow.toDto() = PlayerAttendanceDto(
    playerId = playerId.toString(),
    name = name,
    avatarUrl = avatarUrl,
    positionPrimary = positionPrimary,
    scanned = scanned,
    markedNoShow = markedNoShow,
)

private fun parseUuid(raw: String?, field: String): UUID {
    if (raw.isNullOrBlank()) throw IllegalArgumentException("Falta $field")
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("$field no es un UUID válido")
    }
}
