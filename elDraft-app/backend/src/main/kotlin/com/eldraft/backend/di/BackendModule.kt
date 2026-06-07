package com.eldraft.backend.di

import com.eldraft.backend.auth.JwtService
import com.eldraft.backend.auth.MockTokenVerifier
import com.eldraft.backend.auth.TokenVerifier
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.UserRepository
import com.eldraft.backend.service.AuthService
import com.eldraft.backend.service.ConvocatoryService
import com.eldraft.backend.service.PlayerService
import io.ktor.server.config.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/** Valores de configuración de JWT/auth leídos de application.conf. */
data class AuthConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtRealm: String,
    val authMode: String,
)

/**
 * Módulo Koin del backend. Reemplaza el service locator manual basado en
 * AttributeKey por inyección de dependencias.
 */
fun backendModule(config: ApplicationConfig) = module {

    single {
        AuthConfig(
            jwtSecret = config.property("jwt.secret").getString(),
            jwtIssuer = config.property("jwt.issuer").getString(),
            jwtAudience = config.property("jwt.audience").getString(),
            jwtRealm = config.property("jwt.realm").getString(),
            authMode = config.propertyOrNull("firebase.authMode")?.getString() ?: "mock",
        )
    }

    single<JwtService> {
        val c = get<AuthConfig>()
        JwtService(secret = c.jwtSecret, issuer = c.jwtIssuer, audience = c.jwtAudience)
    }

    single<TokenVerifier> {
        val c = get<AuthConfig>()
        val log = LoggerFactory.getLogger("BackendModule")
        when (c.authMode.lowercase()) {
            "firebase" -> {
                // TODO: FirebaseTokenVerifier con Firebase Admin SDK.
                log.warn("authMode='firebase' aún no implementado; usando MockTokenVerifier.")
                MockTokenVerifier()
            }
            else -> {
                log.info("Auth en modo MOCK (solo desarrollo).")
                MockTokenVerifier()
            }
        }
    }

    // Repositorios
    singleOf(::UserRepository)
    singleOf(::ConvocatoryRepository)

    // Servicios por feature (rutas delgadas delegan aquí)
    singleOf(::AuthService)
    singleOf(::PlayerService)
    singleOf(::ConvocatoryService)
}
