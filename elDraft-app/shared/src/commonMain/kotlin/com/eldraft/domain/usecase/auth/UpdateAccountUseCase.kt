package com.eldraft.domain.usecase.auth

import com.eldraft.data.models.User
import com.eldraft.domain.repository.AuthRepository

/** Actualiza el nombre y avatar del usuario autenticado. */
class UpdateAccountUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(name: String, avatarUrl: String?): User =
        authRepository.updateAccount(name, avatarUrl)
}
