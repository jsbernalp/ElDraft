package com.eldraft.domain.auth

/**
 * Abstracción del login por email + contraseña. La implementación es específica
 * de plataforma (Firebase Auth en Android/iOS). Permite que el caso de uso viva
 * en commonMain y sea testeable con un fake. Espejo de [GoogleSignInProvider].
 *
 * Devuelve el ID token de Firebase que el backend espera en POST /auth/login,
 * idéntico al que entrega Google Sign-In — el backend no distingue el proveedor.
 */
interface EmailAuthProvider {
    /** Inicia sesión con email y contraseña. */
    suspend fun signIn(email: String, password: String): String

    /** Registra una cuenta nueva con [name] como nombre de pantalla. */
    suspend fun signUp(name: String, email: String, password: String): String
}
