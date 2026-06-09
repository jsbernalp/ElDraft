package com.eldraft.android.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Encapsula el flujo de autenticación con email + contraseña usando Firebase Auth.
 *
 * Devuelve el ID token de Firebase que el backend espera en POST /auth/login,
 * idéntico al que llega por Google Sign-In — el backend no distingue el proveedor.
 */
class EmailAuthClient {

    private val auth = FirebaseAuth.getInstance()

    /**
     * Registra un nuevo usuario con email y contraseña, asigna su nombre de
     * pantalla y devuelve el ID token de Firebase.
     * @throws EmailAuthException con mensaje legible si falla.
     */
    suspend fun register(name: String, email: String, password: String): String {
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw EmailAuthException("No se pudo crear la cuenta")

            // Asignar nombre de pantalla en Firebase
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            user.updateProfile(profileUpdate).await()

            return user.getIdToken(false).await()?.token
                ?: throw EmailAuthException("No se pudo obtener el token de sesión")
        } catch (e: EmailAuthException) {
            throw e
        } catch (e: Exception) {
            throw EmailAuthException(friendlyMessage(e), e)
        }
    }

    /**
     * Inicia sesión con email y contraseña y devuelve el ID token de Firebase.
     * @throws EmailAuthException con mensaje legible si falla.
     */
    suspend fun signIn(email: String, password: String): String {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw EmailAuthException("No se pudo iniciar sesión")
            return user.getIdToken(false).await()?.token
                ?: throw EmailAuthException("No se pudo obtener el token de sesión")
        } catch (e: EmailAuthException) {
            throw e
        } catch (e: Exception) {
            throw EmailAuthException(friendlyMessage(e), e)
        }
    }

    private fun friendlyMessage(e: Exception): String {
        val msg = e.message ?: return "Error desconocido"
        return when {
            "EMAIL_EXISTS" in msg || "email address is already in use" in msg ->
                "Ya existe una cuenta con ese correo."
            "WEAK_PASSWORD" in msg || "password is invalid" in msg ->
                "La contraseña es muy débil. Usa al menos 6 caracteres."
            "INVALID_EMAIL" in msg || "email address is badly formatted" in msg ->
                "El correo no tiene un formato válido."
            "EMAIL_NOT_FOUND" in msg || "no user record" in msg ->
                "No encontramos una cuenta con ese correo."
            "INVALID_PASSWORD" in msg || "password is invalid" in msg ->
                "Contraseña incorrecta."
            "INVALID_LOGIN_CREDENTIALS" in msg ->
                "Correo o contraseña incorrectos."
            "NETWORK_ERROR" in msg || "network error" in msg ->
                "Sin conexión. Verifica tu red e intenta de nuevo."
            "TOO_MANY_REQUESTS" in msg ->
                "Demasiados intentos. Espera unos minutos e intenta de nuevo."
            else -> "Error al autenticar. Intenta de nuevo."
        }
    }
}

class EmailAuthException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
