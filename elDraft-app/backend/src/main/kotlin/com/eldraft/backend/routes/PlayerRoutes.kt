package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.plugins.userRepository
import com.eldraft.backend.repository.PlayerProfileRecord
import com.eldraft.backend.repository.ProfileUpsert
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
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
    val sportsmanshipScore: Double = 5.0,
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
    val app = application

    route("/players") {
        // Ficha técnica pública de un jugador (El Cromo). No requiere ser el dueño.
        get("/{id}/profile") {
            val id = parseUuid(call.parameters["id"])
            val profile = app.userRepository.getProfile(id)
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
                validateProfile(body)

                val saved = app.userRepository.upsertProfile(
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

private fun validateProfile(body: UpdateProfileRequest) {
    if (body.positionPrimary.isBlank()) {
        throw IllegalArgumentException("La posición primaria es obligatoria")
    }
    if (body.dominantFoot.isBlank()) {
        throw IllegalArgumentException("La pierna dominante es obligatoria")
    }
    body.height?.let {
        if (it !in 100..250) throw IllegalArgumentException("La altura debe estar entre 100 y 250 cm")
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
    sportsmanshipScore = sportsmanshipScore,
    totalMatches = totalMatches
)
