package com.eldraft.backend.auth

/**
 * Datos verificados que provienen del proveedor de identidad (Firebase).
 * Es el resultado mínimo que el backend necesita para crear/recuperar un usuario.
 */
data class VerifiedIdentity(
    val firebaseUid: String,
    val name: String,
    val email: String?,
    val avatarUrl: String?
)

/**
 * Abstracción sobre la verificación del ID token recibido del cliente.
 *
 * Tiene dos implementaciones:
 *  - [MockTokenVerifier]: para desarrollo, sin depender de la consola de Firebase.
 *  - FirebaseTokenVerifier: verificación real con Firebase Admin SDK (a implementar
 *    cuando exista el proyecto en la consola y la service account).
 */
interface TokenVerifier {
    /**
     * Verifica el [idToken] y devuelve la identidad asociada.
     * @throws TokenVerificationException si el token es inválido o no se puede verificar.
     */
    fun verify(idToken: String): VerifiedIdentity
}

class TokenVerificationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
