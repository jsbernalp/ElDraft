package com.eldraft.backend.routes

import com.eldraft.backend.auth.TokenVerificationException
import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.plugins.jwtService
import com.eldraft.backend.plugins.tokenVerifier
import com.eldraft.backend.plugins.userRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val firebaseToken: String)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
    /** true si el jugador aún no ha configurado su ficha técnica (El Cromo). */
    val needsOnboarding: Boolean
)

@Serializable
data class PhoneRequest(val phone: String)

fun Route.authRoutes() {
    val app = application

    route("/auth") {
        // Verifica el token de Firebase, crea/recupera el usuario y emite un JWT propio.
        post("/login") {
            val body = call.receive<LoginRequest>()

            val identity = try {
                app.tokenVerifier.verify(body.firebaseToken)
            } catch (e: TokenVerificationException) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("code" to "INVALID_TOKEN", "message" to (e.message ?: "Token inválido"))
                )
            }

            val user = app.userRepository.findOrCreateByIdentity(identity)
            val token = app.jwtService.generateToken(user.id.toString())
            val hasProfile = app.userRepository.getProfile(user.id) != null

            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    token = token,
                    user = UserDto(
                        id = user.id.toString(),
                        name = user.name,
                        email = user.email,
                        phone = user.phone,
                        avatarUrl = user.avatarUrl
                    ),
                    needsOnboarding = !hasProfile
                )
            )
        }

        // Actualiza el teléfono del usuario autenticado.
        authenticate("firebase-auth") {
            put("/phone") {
                val body = call.receive<PhoneRequest>()
                val uid = call.currentUserId()
                val updated = app.userRepository.updatePhone(uid, body.phone)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "phone updated"))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("code" to "NOT_FOUND", "message" to "Usuario no encontrado")
                    )
                }
            }
        }
    }
}
