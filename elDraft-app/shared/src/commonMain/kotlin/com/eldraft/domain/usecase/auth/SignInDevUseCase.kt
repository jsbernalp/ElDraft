package com.eldraft.domain.usecase.auth

import com.eldraft.data.models.LoginResponse
import com.eldraft.domain.repository.AuthRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Login de desarrollo SIN Google: genera un token mock (JSON Base64) que el
 * backend acepta en modo mock, y lo intercambia por la sesión.
 */
class SignInDevUseCase(
    private val authRepository: AuthRepository,
) {
    @OptIn(ExperimentalEncodingApi::class)
    suspend operator fun invoke(
        uid: String = "dev-user",
        name: String = "Jugador Dev",
        email: String = "dev@eldraft.app",
    ): LoginResponse {
        val payload = """{"uid":"$uid","name":"$name","email":"$email"}"""
        val mockToken = Base64.encode(payload.encodeToByteArray())
        return authRepository.login(mockToken)
    }
}
