package com.eldraft.domain.usecase.auth

import com.eldraft.domain.repository.AuthRepository

/** Cierra la sesión local (limpia token y userId del SessionStore). */
class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() = authRepository.logout()
}
