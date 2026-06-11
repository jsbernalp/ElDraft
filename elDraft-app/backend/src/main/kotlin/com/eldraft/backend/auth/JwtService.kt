package com.eldraft.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/**
 * Emite y describe los JWT propios del backend.
 *
 * Flujo: el cliente se autentica con Firebase y manda su ID token →
 * el backend lo verifica con [TokenVerifier] → emite ESTE JWT con el userId interno.
 * Las siguientes peticiones usan este JWT (claim "userId") en el header Authorization.
 */
class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val validityMs: Long = 30L * 24 * 60 * 60 * 1000 // 30 días
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim(CLAIM_USER_ID, userId)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
            .sign(algorithm)

    companion object {
        const val CLAIM_USER_ID = "userId"
    }
}
