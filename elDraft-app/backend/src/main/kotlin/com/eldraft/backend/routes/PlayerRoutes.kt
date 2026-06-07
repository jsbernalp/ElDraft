package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.playerRoutes() {
    route("/players") {
        get("/{id}/profile") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing player id")
            // TODO: obtener ficha técnica del jugador
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "message" to "player profile placeholder"))
        }

        put("/{id}/profile") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing player id")
            // TODO: actualizar ficha técnica
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "message" to "profile updated"))
        }
    }
}
