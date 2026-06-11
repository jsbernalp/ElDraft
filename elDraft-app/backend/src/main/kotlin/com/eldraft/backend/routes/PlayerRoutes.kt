package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.repository.PlayerProfileRecord
import com.eldraft.backend.repository.ProfileUpsert
import com.eldraft.backend.service.PlayerService
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
data class PlayerProfileDto(
    val userId: String,
    val positionPrimary: String,
    val positionSecondary: String? = null,
    val dominantFoot: String,
    val height: Int? = null,
    val build: String? = null,
    val speedRating: Int = 0,
    val precisionRating: Int = 0,
    val attendancePct: Double = 100.0,
    val skillScore: Double = 5.0,
    val sportsmanshipScore: Double = 5.0,
    val responsibilityScore: Double = 5.0,
    val totalMatches: Int = 0
)

/** Cuerpo para crear/actualizar la ficha técnica. */
@Serializable
data class UpdateProfileRequest(
    val positionPrimary: String,
    val positionSecondary: String? = null,
    val dominantFoot: String,
    val height: Int? = null,
    val build: String? = null
)

fun Route.playerRoutes() {
    val playerService = application.get<PlayerService>()

    route("/players") {
        // Ficha técnica pública de un jugador (El Cromo). No requiere ser el dueño.
        get("/{id}/profile") {
            val id = parseUuid(call.parameters["id"])
            val profile = playerService.getProfile(id)
                ?: throw NoSuchElementException("El jugador no tiene ficha técnica")
            call.respond(HttpStatusCode.OK, profile.toDto())
        }

        authenticate("firebase-auth") {
            // Solo el dueño puede editar su propia ficha técnica.
            put("/{id}/profile") {
                val pathId = parseUuid(call.parameters["id"])
                val authUid = call.currentUserId()
                if (pathId != authUid) {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("code" to "FORBIDDEN", "message" to "No puedes editar la ficha de otro jugador")
                    )
                }

                val body = call.receive<UpdateProfileRequest>()

                // La validación vive en PlayerService.upsertProfile.
                val saved = playerService.upsertProfile(
                    authUid,
                    ProfileUpsert(
                        positionPrimary = body.positionPrimary,
                        positionSecondary = body.positionSecondary,
                        dominantFoot = body.dominantFoot,
                        height = body.height,
                        build = body.build
                    )
                )
                call.respond(HttpStatusCode.OK, saved.toDto())
            }
        }
    }
}

private fun parseUuid(raw: String?): UUID {
    if (raw.isNullOrBlank()) throw IllegalArgumentException("Falta el id del jugador")
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("El id del jugador no es un UUID válido")
    }
}

private fun PlayerProfileRecord.toDto() = PlayerProfileDto(
    userId = userId.toString(),
    positionPrimary = positionPrimary,
    positionSecondary = positionSecondary,
    dominantFoot = dominantFoot,
    height = height,
    build = build,
    speedRating = speedRating,
    precisionRating = precisionRating,
    attendancePct = attendancePct,
    skillScore = skillScore,
    sportsmanshipScore = sportsmanshipScore,
    responsibilityScore = responsibilityScore,
    totalMatches = totalMatches
)
