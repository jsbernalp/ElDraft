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

    /** Actualiza el teléfono del usuario; false si no existe. */
    fun updatePhone(userId: UUID, phone: String): Boolean =
        userRepository.updatePhone(userId, phone)
}
