package com.eldraft.backend.plugins

import com.eldraft.backend.routes.*
import com.eldraft.backend.websocket.mapWebSocketRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            authRoutes()
            playerRoutes()
            convocatoryRoutes()
            postulationRoutes()
            attendanceRoutes()
            ratingRoutes()
        }
        mapWebSocketRoute()
    }
}
