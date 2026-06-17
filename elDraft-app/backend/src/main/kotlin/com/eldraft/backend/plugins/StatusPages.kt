package com.eldraft.backend.plugins

import com.eldraft.backend.service.AttendanceConflict
import com.eldraft.backend.service.AttendanceForbidden
import com.eldraft.backend.service.ConvocatoryConflict
import com.eldraft.backend.service.ConvocatoryScheduleConflict
import com.eldraft.backend.service.AttendanceInvalidQr
import com.eldraft.backend.service.AttendanceNotFound
import com.eldraft.backend.service.DeclarationForbidden
import com.eldraft.backend.service.DeclarationNotFound
import com.eldraft.backend.service.DeclarationWindowClosed
import com.eldraft.backend.service.NoShowConflict
import com.eldraft.backend.service.NoShowForbidden
import com.eldraft.backend.service.NoShowNotFound
import com.eldraft.backend.service.NoShowWindowClosed
import com.eldraft.backend.service.PostulationConflict
import com.eldraft.backend.service.PostulationForbidden
import com.eldraft.backend.service.PostulationNotFound
import com.eldraft.backend.service.RatingConflict
import com.eldraft.backend.service.RatingForbidden
import com.eldraft.backend.service.RatingInvalid
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: String, val message: String)

/** Un partido del organizador que se cancelaría al crear (para el diálogo). */
@Serializable
data class ScheduleConflictItem(val format: String, val scheduledAt: String)

/**
 * Respuesta del 409 de conflicto de horario al crear: además del mensaje, lleva
 * la lista de postulaciones que se cancelarían si el usuario confirma.
 */
@Serializable
data class ScheduleConflictResponse(
    val code: String,
    val message: String,
    val conflicts: List<ScheduleConflictItem>,
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", cause.message ?: "Invalid request"))
        }
        exception<PostulationConflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", cause.message ?: "Conflicto en la postulación"))
        }
        exception<ConvocatoryConflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", cause.message ?: "Conflicto de horario"))
        }
        exception<ConvocatoryScheduleConflict> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ScheduleConflictResponse(
                    code = "SCHEDULE_CONFLICT",
                    message = cause.message ?: "Conflicto de horario con tus postulaciones",
                    conflicts = cause.conflicts.map { ScheduleConflictItem(it.format, it.scheduledAt) },
                ),
            )
        }
        exception<PostulationForbidden> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", cause.message ?: "No autorizado"))
        }
        exception<PostulationNotFound> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Recurso no encontrado"))
        }
        exception<AttendanceForbidden> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", cause.message ?: "No autorizado"))
        }
        exception<AttendanceConflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", cause.message ?: "Conflicto de asistencia"))
        }
        exception<AttendanceNotFound> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Recurso no encontrado"))
        }
        exception<AttendanceInvalidQr> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_QR", cause.message ?: "Código QR inválido"))
        }
        exception<RatingForbidden> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", cause.message ?: "No autorizado"))
        }
        exception<RatingConflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", cause.message ?: "Conflicto de calificación"))
        }
        exception<RatingInvalid> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", cause.message ?: "Calificación inválida"))
        }
        exception<NoShowForbidden> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", cause.message ?: "No autorizado"))
        }
        exception<NoShowConflict> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("CONFLICT", cause.message ?: "Ya reportaste este partido"))
        }
        exception<NoShowNotFound> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Recurso no encontrado"))
        }
        exception<NoShowWindowClosed> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("WINDOW_CLOSED", cause.message ?: "Fuera de plazo"))
        }
        exception<DeclarationForbidden> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", cause.message ?: "No autorizado"))
        }
        exception<DeclarationNotFound> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Recurso no encontrado"))
        }
        exception<DeclarationWindowClosed> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("WINDOW_CLOSED", cause.message ?: "Fuera de plazo"))
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Resource not found"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
        }
    }
}
