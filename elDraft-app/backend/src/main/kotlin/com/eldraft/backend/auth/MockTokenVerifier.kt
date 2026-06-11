package com.eldraft.backend.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Verificador de tokens para DESARROLLO. No contacta a Google ni valida la firma
 * del token (eso lo hará FirebaseTokenVerifier en producción), pero SÍ decodifica
 * el payload para extraer la identidad real.
 *
 * Acepta tres formatos, en orden:
 *  1. JWT de Google/Firebase ("xxxxx.yyyyy.zzzzz"): decodifica el payload y usa
 *     el claim `sub` como uid (corto y estable), junto a `name`, `email`, `picture`.
 *  2. JSON en Base64 con { "uid", "name", "email", "avatarUrl" } (modo dev/curl).
 *  3. Token de texto plano: se usa como uid (truncado a 128 por seguridad).
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

        // Estrategia 1: JWT (3 partes separadas por '.') — token de Google/Firebase.
        decodeJwtPayload(idToken)?.let { claims ->
            val sub = claims.string("sub") ?: claims.string("user_id")
            if (!sub.isNullOrBlank()) {
                return VerifiedIdentity(
                    firebaseUid = sub,
                    name = claims.string("name")
                        ?: claims.string("email")?.substringBefore("@")
                        ?: "Jugador ${sub.take(6)}",
                    email = claims.string("email"),
                    avatarUrl = claims.string("picture")
                )
            }
        }

        // Estrategia 2: JSON en Base64 (modo dev/curl).
        decodeBase64Json(idToken)?.let { payload ->
            return VerifiedIdentity(
                firebaseUid = payload.uid,
                name = payload.name ?: "Jugador ${payload.uid.take(6)}",
                email = payload.email,
                avatarUrl = payload.avatarUrl
            )
        }

        // Estrategia 3 (fallback dev): token plano como uid, truncado a 128.
        val uid = idToken.take(128)
        return VerifiedIdentity(
            firebaseUid = uid,
            name = "Jugador ${uid.take(6)}",
            email = null,
            avatarUrl = null
        )
    }

    /** Decodifica el payload (parte central) de un JWT sin validar la firma. */
    private fun decodeJwtPayload(token: String): JsonObject? = try {
        val parts = token.split(".")
        if (parts.size != 3) {
            null
        } else {
            val payloadJson = String(Base64.getUrlDecoder().decode(padBase64(parts[1])))
            json.parseToJsonElement(payloadJson) as? JsonObject
        }
    } catch (_: Exception) {
        null
    }

    private fun decodeBase64Json(token: String): MockPayload? = try {
        val decoded = String(Base64.getDecoder().decode(token))
        json.decodeFromString(MockPayload.serializer(), decoded)
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    /** Base64URL del JWT puede venir sin padding; lo restauramos. */
    private fun padBase64(s: String): String = when (s.length % 4) {
        2 -> "$s=="
        3 -> "$s="
        else -> s
    }
}
