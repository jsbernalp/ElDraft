package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateRatingRequest(
    val convocatoryId: String,
    val ratedPlayerId: String,
    val sportsmanshipScore: Int
)

fun Route.ratingRoutes() {
    route("/ratings") {
        post {
            val body = call.receive<CreateRatingRequest>()
            // TODO: guardar calificación + recalcular sportsmanship_score del jugador
            call.respond(HttpStatusCode.Created, mapOf("message" to "rating saved"))
        }
    }
}
