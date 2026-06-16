package com.eldraft.backend.plugins

import com.auth0.jwt.JWT
import com.eldraft.backend.auth.JwtService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import java.util.UUID

/**
 * Extrae el UUID del usuario autenticado desde el claim "userId" del JWT.
 * Solo válido dentro de un bloque authenticate("firebase-auth").
 */
fun ApplicationCall.currentUserId(): UUID {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No hay principal autenticado")
    val raw = principal.payload.getClaim(JwtService.CLAIM_USER_ID).asString()
        ?: throw IllegalStateException("JWT sin claim userId")
    return UUID.fromString(raw)
}

/**
 * Identifica al usuario en una ruta **pública** (sin bloque authenticate): si
 * viene un Bearer token válido en el header devuelve su UUID; si no hay token,
 * está mal formado o no se puede decodificar, devuelve null (acceso anónimo).
 *
 * No verifica firma/audiencia (la ruta no es protegida): solo se usa como
 * "pista" para filtrar las convocatorias propias del organizador. Un token
 * inválido simplemente no oculta nada, nunca da acceso a datos extra.
 */
fun ApplicationCall.optionalUserId(): UUID? = runCatching {
    val header = request.header("Authorization") ?: return null
    val token = header.removePrefix("Bearer ").trim().takeIf { it.isNotBlank() } ?: return null
    val raw = JWT.decode(token).getClaim(JwtService.CLAIM_USER_ID).asString() ?: return null
    UUID.fromString(raw)
}.getOrNull()
