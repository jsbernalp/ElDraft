package com.eldraft.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eldraft.backend.auth.JwtService
import com.eldraft.backend.di.AuthConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.get

/**
 * Configura el plugin de autenticación JWT de Ktor. Los servicios de auth
 * (JwtService, TokenVerifier) y la config viven en Koin (ver backendModule);
 * aquí solo se monta el verificador del plugin de autenticación.
 */
fun Application.configureAuth() {
    val authConfig = get<AuthConfig>()

    authentication {
        jwt("firebase-auth") {
            realm = authConfig.jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(authConfig.jwtSecret))
                    .withAudience(authConfig.jwtAudience)
                    .withIssuer(authConfig.jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim(JwtService.CLAIM_USER_ID).asString()
                if (credential.payload.audience.contains(authConfig.jwtAudience) && !userId.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}
