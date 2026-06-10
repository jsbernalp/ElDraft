package com.eldraft.backend.routes

import com.eldraft.backend.auth.TokenVerificationException
import com.eldraft.backend.plugins.currentUserId
import com.eldraft.backend.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get

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

@Serializable
data class FcmTokenRequest(val token: String)

@Serializable
data class LocationRequest(val lat: Double, val lng: Double)

@Serializable
data class UpdateAccountRequest(
    val name: String,
    val avatarUrl: String? = null,
)

fun Route.authRoutes() {
    val authService = application.get<AuthService>()

    route("/auth") {
        // Verifica el token de Firebase, crea/recupera el usuario y emite un JWT propio.
        post("/login") {
            val body = call.receive<LoginRequest>()

            val result = try {
                authService.login(body.firebaseToken)
            } catch (e: TokenVerificationException) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("code" to "INVALID_TOKEN", "message" to (e.message ?: "Token inválido"))
                )
            }

            val user = result.user
            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    token = result.token,
                    user = UserDto(
                        id = user.id.toString(),
                        name = user.name,
                        email = user.email,
                        phone = user.phone,
                        avatarUrl = user.avatarUrl
                    ),
                    needsOnboarding = result.needsOnboarding
                )
            )
        }

        authenticate("firebase-auth") {
            // Devuelve los datos del usuario autenticado.
            get("/me") {
                val uid = call.currentUserId()
                val user = authService.getMe(uid)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("code" to "NOT_FOUND", "message" to "Usuario no encontrado")
                    )
                call.respond(
                    HttpStatusCode.OK,
                    UserDto(
                        id = user.id.toString(),
                        name = user.name,
                        email = user.email,
                        phone = user.phone,
                        avatarUrl = user.avatarUrl
                    )
                )
            }

            // Actualiza nombre y avatar del usuario autenticado.
            patch("/me") {
                val uid = call.currentUserId()
                val body = call.receive<UpdateAccountRequest>()
                val user = try {
                    authService.updateAccount(uid, body.name, body.avatarUrl)
                } catch (e: IllegalArgumentException) {
                    return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("code" to "INVALID", "message" to (e.message ?: "Datos inválidos"))
                    )
                }
                call.respond(
                    HttpStatusCode.OK,
                    UserDto(
                        id = user.id.toString(),
                        name = user.name,
                        email = user.email,
                        phone = user.phone,
                        avatarUrl = user.avatarUrl
                    )
                )
            }
        }

        // Actualiza el teléfono del usuario autenticado.
        authenticate("firebase-auth") {
            put("/phone") {
                val body = call.receive<PhoneRequest>()
                val uid = call.currentUserId()
                val updated = authService.updatePhone(uid, body.phone)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "phone updated"))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("code" to "NOT_FOUND", "message" to "Usuario no encontrado")
                    )
                }
            }

            // Registra el token FCM del dispositivo (para recibir push).
            put("/fcm-token") {
                val body = call.receive<FcmTokenRequest>()
                val uid = call.currentUserId()
                val updated = authService.updateFcmToken(uid, body.token)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "fcm token updated"))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("code" to "NOT_FOUND", "message" to "Usuario no encontrado")
                    )
                }
            }

            // Reporta la última ubicación conocida del dispositivo (para
            // notificar convocatorias cercanas). Best-effort desde el cliente.
            put("/location") {
                val body = call.receive<LocationRequest>()
                val uid = call.currentUserId()
                val updated = try {
                    authService.updateLocation(uid, body.lat, body.lng)
                } catch (e: IllegalArgumentException) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("code" to "INVALID", "message" to (e.message ?: "Ubicación inválida"))
                    )
                }
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "location updated"))
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
