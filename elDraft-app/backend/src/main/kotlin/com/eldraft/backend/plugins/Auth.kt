package com.eldraft.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eldraft.backend.auth.JwtService
import com.eldraft.backend.auth.MockTokenVerifier
import com.eldraft.backend.auth.TokenVerifier
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.AttributeKey

/** Claves para recuperar los servicios de auth desde la Application. */
val TokenVerifierKey = AttributeKey<TokenVerifier>("TokenVerifier")
val JwtServiceKey = AttributeKey<JwtService>("JwtService")

val Application.tokenVerifier: TokenVerifier get() = attributes[TokenVerifierKey]
val Application.jwtService: JwtService get() = attributes[JwtServiceKey]

fun Application.configureAuth() {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()
    val authMode = environment.config.propertyOrNull("firebase.authMode")?.getString() ?: "mock"

    // Selección del verificador de token según el modo configurado.
    val verifier: TokenVerifier = when (authMode.lowercase()) {
        "firebase" -> {
            // TODO: implementar FirebaseTokenVerifier con Firebase Admin SDK.
            log.warn("authMode='firebase' aún no implementado; usando MockTokenVerifier.")
            MockTokenVerifier()
        }
        else -> {
            log.info("Auth en modo MOCK (solo desarrollo). No se verifican tokens contra Firebase.")
            MockTokenVerifier()
        }
    }
    attributes.put(TokenVerifierKey, verifier)
    attributes.put(JwtServiceKey, JwtService(jwtSecret, jwtIssuer, jwtAudience))

    authentication {
        jwt("firebase-auth") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim(JwtService.CLAIM_USER_ID).asString()
                if (credential.payload.audience.contains(jwtAudience) && !userId.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}
