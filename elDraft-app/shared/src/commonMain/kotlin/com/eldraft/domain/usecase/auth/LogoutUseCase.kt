package com.eldraft.domain.usecase.auth

import com.eldraft.domain.auth.IdentitySessionCleaner
import com.eldraft.domain.repository.AuthRepository

/**
 * Cierra la sesión: primero la del proveedor de identidad (Firebase), después la
 * local (token y userId del SessionStore).
 */
class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val identitySession: IdentitySessionCleaner,
) {
    suspend operator fun invoke() {
        // Si cerrar la sesión del proveedor falla (sin red, error de Credential
        // Manager), la sesión local se limpia igual: dejar al usuario dentro de la
        // app porque Firebase no respondió es peor que quedar con una sesión de
        // Firebase colgada, y esa se sobrescribe en el siguiente login.
        runCatching { identitySession.clear() }
        authRepository.logout()
    }
}
