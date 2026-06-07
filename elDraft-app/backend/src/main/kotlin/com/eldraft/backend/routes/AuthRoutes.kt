package com.eldraft.backend.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val firebaseToken: String)

@Serializable
data class PhoneRequest(val phone: String)

fun Route.authRoutes() {
    route("/auth") {
        post("/login") {
            val body = call.receive<LoginRequest>()
            // TODO: verificar token Firebase con Firebase Admin SDK
            // TODO: crear o recuperar usuario en BD
            call.respond(HttpStatusCode.OK, mapOf("message" to "login OK", "token" to "jwt-placeholder"))
        }

        put("/phone") {
            val body = call.receive<PhoneRequest>()
            // TODO: actualizar teléfono del usuario autenticado
            call.respond(HttpStatusCode.OK, mapOf("message" to "phone updated"))
        }
    }
}
