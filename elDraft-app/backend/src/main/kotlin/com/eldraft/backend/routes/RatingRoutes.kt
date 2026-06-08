package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.repository.TeammateRecord
import com.eldraft.backend.service.RatingService
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
data class TeammateDto(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val alreadyRated: Boolean,
)

@Serializable
data class CreateRatingRequest(
    val convocatoryId: String,
    val ratedPlayerId: String,
    val sportsmanshipScore: Int,
)

fun Route.ratingRoutes() {
    val service = application.get<RatingService>()

    authenticate("firebase-auth") {

        // Compañeros (asistentes) que el solicitante puede calificar.
        get("/convocatories/{convocatoryId}/teammates") {
            val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
            val requesterId = call.currentUserId()
            val list = service.teammatesToRate(convocatoryId, requesterId).map { it.toDto() }
            call.respond(HttpStatusCode.OK, list)
        }

        route("/ratings") {
            post {
                val raterId = call.currentUserId()
                val body = call.receive<CreateRatingRequest>()
                service.submit(
                    convocatoryId = parseUuid(body.convocatoryId, "convocatoryId"),
                    raterId = raterId,
                    ratedPlayerId = parseUuid(body.ratedPlayerId, "ratedPlayerId"),
                    score = body.sportsmanshipScore,
                )
                call.respond(HttpStatusCode.Created, mapOf("message" to "rating saved"))
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

private fun TeammateRecord.toDto() = TeammateDto(
    userId = userId.toString(),
    name = name,
    avatarUrl = avatarUrl,
    positionPrimary = positionPrimary,
    alreadyRated = alreadyRated,
)
