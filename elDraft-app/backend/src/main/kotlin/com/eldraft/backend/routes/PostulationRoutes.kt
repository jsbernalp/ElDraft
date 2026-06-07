package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.postulationRoutes() {
    route("/convocatories/{convocatoryId}") {
        post("/apply") {
            val convocatoryId = call.parameters["convocatoryId"] ?: throw IllegalArgumentException("Missing convocatoryId")
            // TODO: registrar postulación + enviar push notification al organizador
            call.respond(HttpStatusCode.Created, mapOf("message" to "applied", "convocatoryId" to convocatoryId))
        }

        get("/applicants") {
            val convocatoryId = call.parameters["convocatoryId"] ?: throw IllegalArgumentException("Missing convocatoryId")
            // TODO: listar postulantes con su ficha técnica
            call.respond(HttpStatusCode.OK, emptyList<Any>())
        }
    }

    route("/postulations/{id}") {
        put("/approve") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing id")
            // TODO: aprobar postulación
            call.respond(HttpStatusCode.OK, mapOf("message" to "approved", "id" to id))
        }

        put("/reject") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing id")
            // TODO: rechazar postulación
            call.respond(HttpStatusCode.OK, mapOf("message" to "rejected", "id" to id))
        }
    }
}
