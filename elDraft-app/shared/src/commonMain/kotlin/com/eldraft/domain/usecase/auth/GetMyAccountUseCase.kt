package com.eldraft.domain.usecase.auth

import com.eldraft.data.models.User
import com.eldraft.domain.repository.AuthRepository

/** Devuelve los datos del usuario autenticado (nombre, email, avatarUrl, phone). */
class GetMyAccountUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): User = authRepository.getMe()
}
