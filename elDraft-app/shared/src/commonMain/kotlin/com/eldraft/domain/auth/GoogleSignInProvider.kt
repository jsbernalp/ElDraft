package com.eldraft.domain.auth

/** Token de identidad devuelto por el proveedor de Google Sign-In. */
data class GoogleIdentity(
    val idToken: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

/**
 * Abstracción del Sign-In con Google. La implementación es específica de
 * plataforma (en Android: Credential Manager). Permite que el caso de uso
 * viva en commonMain y sea testeable con un fake.
 */
interface GoogleSignInProvider {
    suspend fun signIn(): GoogleIdentity
}
