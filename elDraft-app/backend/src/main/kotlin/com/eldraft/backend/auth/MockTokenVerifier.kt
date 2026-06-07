package com.eldraft.backend.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Verificador de tokens para DESARROLLO. No contacta a Google ni valida firmas.
 *
 * El "token" que espera es simplemente un JSON codificado en Base64 con la forma:
 *   { "uid": "...", "name": "...", "email": "...", "avatarUrl": "..." }
 *
 * Esto permite probar todo el flujo de auth end-to-end (login, creación de usuario,
 * emisión de JWT) sin necesitar el proyecto de Firebase todavía.
 *
 * Como atajo extra para pruebas rápidas con curl, también acepta tokens de texto
 * plano: en ese caso el token completo se usa como uid y se genera un nombre dummy.
 */
class MockTokenVerifier : TokenVerifier {

    @Serializable
    private data class MockPayload(
        val uid: String,
        val name: String? = null,
        val email: String? = null,
        val avatarUrl: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    override fun verify(idToken: String): VerifiedIdentity {
        if (idToken.isBlank()) {
            throw TokenVerificationException("Token vacío")
        }

        // Intento 1: el token es un JSON en Base64.
        decodeBase64Json(idToken)?.let { payload ->
            return VerifiedIdentity(
                firebaseUid = payload.uid,
                name = payload.name ?: "Jugador ${payload.uid.take(6)}",
                email = payload.email,
                avatarUrl = payload.avatarUrl
            )
        }

        // Intento 2 (atajo dev): tratar el token plano como uid.
        return VerifiedIdentity(
            firebaseUid = idToken,
            name = "Jugador ${idToken.take(6)}",
            email = null,
            avatarUrl = null
        )
    }

    private fun decodeBase64Json(token: String): MockPayload? = try {
        val decoded = String(Base64.getDecoder().decode(token))
        json.decodeFromString(MockPayload.serializer(), decoded)
    } catch (_: Exception) {
        null
    }
}
