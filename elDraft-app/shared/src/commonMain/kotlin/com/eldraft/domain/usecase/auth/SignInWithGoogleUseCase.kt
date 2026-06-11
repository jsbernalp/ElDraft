package com.eldraft.domain.usecase.auth

import com.eldraft.data.models.LoginResponse
import com.eldraft.domain.auth.GoogleSignInProvider
import com.eldraft.domain.repository.AuthRepository

/**
 * Orquesta el login con Google: obtiene el idToken del proveedor y lo
 * intercambia por la sesión del backend (que el repo persiste).
 */
class SignInWithGoogleUseCase(
    private val googleSignIn: GoogleSignInProvider,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): LoginResponse {
        val identity = googleSignIn.signIn()
        return authRepository.login(identity.idToken)
    }
}
