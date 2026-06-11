package com.eldraft.domain.usecase.auth

import com.eldraft.domain.repository.AuthRepository

/**
 * Registra el token FCM del dispositivo en el backend. Best-effort:
 * el repositorio ya ignora fallos de red para no afectar el flujo de sesión.
 */
class RegisterFcmTokenUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(token: String) {
        if (token.isBlank()) return
        authRepository.registerFcmToken(token)
    }
}
