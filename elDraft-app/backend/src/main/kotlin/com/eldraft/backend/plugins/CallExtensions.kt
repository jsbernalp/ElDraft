package com.eldraft.backend.plugins

import com.eldraft.backend.auth.JwtService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
