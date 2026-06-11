package com.eldraft.backend.service

import com.eldraft.backend.auth.JwtService
import com.eldraft.backend.auth.TokenVerifier
import com.eldraft.backend.repository.UserRecord
import com.eldraft.backend.repository.UserRepository
import java.util.UUID

/** Resultado del login: usuario, JWT y si necesita completar el onboarding. */
data class LoginResult(
    val token: String,
    val user: UserRecord,
    val needsOnboarding: Boolean,
)

/**
 * Lógica de autenticación: verifica el token externo, crea/recupera el usuario
 * y emite el JWT propio. Antes vivía dentro de la ruta /auth/login.
 */
class AuthService(
    private val tokenVerifier: TokenVerifier,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) {
    /** Verifica el token (Firebase/Google/mock) y devuelve sesión + JWT. */
    fun login(externalToken: String): LoginResult {
        val identity = tokenVerifier.verify(externalToken)
        val user = userRepository.findOrCreateByIdentity(identity)
        val token = jwtService.generateToken(user.id.toString())
        val hasProfile = userRepository.getProfile(user.id) != null
        return LoginResult(token = token, user = user, needsOnboarding = !hasProfile)
    }

    /** Devuelve el usuario actual, o null si no existe. */
    fun getMe(userId: UUID): UserRecord? = userRepository.findById(userId)

    /**
     * Actualiza nombre y avatar del usuario.
     * @throws IllegalArgumentException si el nombre está vacío o la URL no es válida.
     */
    fun updateAccount(userId: UUID, name: String, avatarUrl: String?): UserRecord {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "El nombre no puede estar vacío" }
        require(trimmedName.length <= 200) { "El nombre no puede tener más de 200 caracteres" }
        if (avatarUrl != null) {
            require(avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                "La URL del avatar debe comenzar con http:// o https://"
            }
            require(avatarUrl.length <= 500) { "La URL del avatar es demasiado larga" }
        }
        return userRepository.updateAccount(userId, trimmedName, avatarUrl)
            ?: error("Usuario no encontrado")
    }

    /** Actualiza el teléfono del usuario; false si no existe. */
    fun updatePhone(userId: UUID, phone: String): Boolean =
        userRepository.updatePhone(userId, phone)

    /** Registra el token FCM del dispositivo del usuario; false si no existe. */
    fun updateFcmToken(userId: UUID, token: String): Boolean =
        userRepository.updateFcmToken(userId, token)

    /**
     * Guarda la última ubicación conocida del usuario (para notificar
     * convocatorias cercanas). false si el usuario no existe.
     * @throws IllegalArgumentException si lat/lng están fuera de rango.
     */
    fun updateLocation(userId: UUID, lat: Double, lng: Double): Boolean {
        require(lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(lng in -180.0..180.0) { "Longitud fuera de rango" }
        return userRepository.updateLastLocation(userId, lat, lng)
    }
}
