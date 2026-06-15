package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.service.NoShowService
import com.eldraft.backend.service.NoShowStatus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import java.util.UUID

@Serializable
data class NoShowStatusDto(
    val canReport: Boolean,
    val alreadyReported: Boolean,
    val windowOpen: Boolean,
    val reports: Int,
    val attendees: Int,
    val consensusReached: Boolean,
    val markedNoShow: Boolean = false,
)

fun Route.noShowRoutes() {
    val service = application.get<NoShowService>()

    authenticate("firebase-auth") {
        route("/convocatories/{convocatoryId}") {

            // Un asistente reporta que el organizador no se presentó.
            post("/report-no-show") {
                val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
                val reporterId = call.currentUserId()
                val status = service.report(convocatoryId, reporterId)
                call.respond(HttpStatusCode.Created, status.toDto())
            }

            // Estado del reporte para pintar el botón (¿puedo?, votos, consenso).
            get("/no-show-status") {
                val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
                val requesterId = call.currentUserId()
                val status = service.status(convocatoryId, requesterId)
                call.respond(HttpStatusCode.OK, status.toDto())
            }
        }
    }
}

private fun NoShowStatus.toDto() = NoShowStatusDto(
    canReport = canReport,
    alreadyReported = alreadyReported,
    windowOpen = windowOpen,
    reports = reports,
    attendees = attendees,
    consensusReached = consensusReached,
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
