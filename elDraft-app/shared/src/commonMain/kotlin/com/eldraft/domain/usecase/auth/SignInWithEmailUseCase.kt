package com.eldraft.domain.usecase.auth

import com.eldraft.data.models.LoginResponse
import com.eldraft.domain.auth.EmailAuthProvider
import com.eldraft.domain.repository.AuthRepository

/**
 * Orquesta el login/registro por email: obtiene el ID token de Firebase del
 * proveedor y lo intercambia por la sesión del backend (que el repo persiste).
 * Hermano de [SignInWithGoogleUseCase].
 */
class SignInWithEmailUseCase(
    private val emailAuth: EmailAuthProvider,
    private val authRepository: AuthRepository,
) {
    suspend fun signIn(email: String, password: String): LoginResponse {
        val idToken = emailAuth.signIn(email, password)
        return authRepository.login(idToken)
    }

    suspend fun signUp(name: String, email: String, password: String): LoginResponse {
        val idToken = emailAuth.signUp(name, email, password)
        return authRepository.login(idToken)
    }
}
