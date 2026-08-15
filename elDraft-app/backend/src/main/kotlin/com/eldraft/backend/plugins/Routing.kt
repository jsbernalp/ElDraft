package com.eldraft.backend.plugins

import com.eldraft.backend.routes.*
import com.eldraft.backend.websocket.mapWebSocketRoute
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Healthcheck del hosting (Fly/Railway). Sin auth y sin tocar la BD: solo
        // responde si el proceso está vivo y atendiendo peticiones.
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // Páginas legales públicas exigidas por Google Play. Sin auth y sin prefijo
        // de API: son URLs que se abren desde un navegador.
        legalRoutes()

        route("/api/v1") {
            authRoutes()
            playerRoutes()
            convocatoryRoutes()
            postulationRoutes()
            attendanceRoutes()
            ratingRoutes()
            noShowRoutes()
            attendanceDeclarationRoutes()
        }
        mapWebSocketRoute()
    }
}
