package com.eldraft.backend.routes

import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.service.PostulationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import java.util.UUID

@Serializable
data class PostulantSummaryDto(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val positionPrimary: String? = null,
    val dominantFoot: String? = null,
    val speedRating: Int? = null,
    val precisionRating: Int? = null,
    val attendancePct: Double? = null,
    val sportsmanshipScore: Double? = null,
    val totalMatches: Int? = null,
)

@Serializable
data class PostulationDto(
    val id: String,
    val convocatoryId: String,
    val playerId: String,
    val status: String,
    val createdAt: String,
    val player: PostulantSummaryDto? = null,
)

fun Route.postulationRoutes() {
    val service = application.get<PostulationService>()

    authenticate("firebase-auth") {

        route("/convocatories/{convocatoryId}") {

            // Un jugador se postula a la convocatoria.
            post("/apply") {
                val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
                val playerId = call.currentUserId()
                val created = service.apply(convocatoryId, playerId)
                call.respond(HttpStatusCode.Created, created.toDto())
            }

            // El organizador lista los postulantes (con su ficha).
            get("/applicants") {
                val convocatoryId = parseUuid(call.parameters["convocatoryId"], "convocatoryId")
                val requesterId = call.currentUserId()
                val applicants = service.getApplicants(convocatoryId, requesterId).map { it.toDto() }
                call.respond(HttpStatusCode.OK, applicants)
            }
        }

        route("/postulations/{id}") {
            put("/approve") {
                val id = parseUuid(call.parameters["id"], "id")
                val requesterId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.approve(id, requesterId).toDto())
            }

            put("/reject") {
                val id = parseUuid(call.parameters["id"], "id")
                val requesterId = call.currentUserId()
                call.respond(HttpStatusCode.OK, service.reject(id, requesterId).toDto())
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

private fun PostulationRecord.toDto() = PostulationDto(
    id = id.toString(),
    convocatoryId = convocatoryId.toString(),
    playerId = playerId.toString(),
    status = status,
    createdAt = createdAt,
    player = player?.let {
        PostulantSummaryDto(
            userId = it.userId.toString(),
            name = it.name,
            avatarUrl = it.avatarUrl,
            positionPrimary = it.positionPrimary,
            dominantFoot = it.dominantFoot,
            speedRating = it.speedRating,
            precisionRating = it.precisionRating,
            attendancePct = it.attendancePct,
            sportsmanshipScore = it.sportsmanshipScore,
            totalMatches = it.totalMatches,
        )
    },
)
