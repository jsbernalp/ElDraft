package com.eldraft.backend.di

import com.eldraft.backend.attendance.QrTokenService
import com.eldraft.backend.auth.JwtService
import com.eldraft.backend.auth.MockTokenVerifier
import com.eldraft.backend.auth.TokenVerifier
import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.AttendanceDeclarationRepository
import com.eldraft.backend.repository.AttendanceRepository
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.NoShowRepository
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.RatingRepository
import com.eldraft.backend.repository.UserRepository
import com.eldraft.backend.service.AttendanceDeclarationService
import com.eldraft.backend.service.AttendanceService
import com.eldraft.backend.service.AuthService
import com.eldraft.backend.service.ConvocatoryService
import com.eldraft.backend.service.NoShowService
import com.eldraft.backend.service.PlayerService
import com.eldraft.backend.service.PostulationService
import com.eldraft.backend.service.RatingService
import io.ktor.server.config.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

/**
 * Lee local.properties buscando desde el directorio de trabajo hacia arriba
 * (hasta 3 niveles). Devuelve un mapa vacío si no se encuentra el archivo.
 *
 * Esto permite que Run Configurations de tipo Ktor/Kotlin en Android Studio
 * (que no pasan por el task :backend:run) dispongan de las mismas variables
 * locales que el task de Gradle configurado en build.gradle.kts.
 */
private fun readLocalProperties(): Map<String, String> {
    val candidates = listOf(
        File("local.properties"),
        File("../local.properties"),
        File("../../local.properties"),
    )
    val file = candidates.firstOrNull { it.exists() } ?: return emptyMap()
    val props = Properties().also { file.inputStream().use(it::load) }
    return props.entries.associate { (k, v) -> k.toString() to v.toString() }
}

/**
 * Devuelve el valor de [key] buscando en este orden de prioridad:
 * 1. Variable de entorno del sistema (producción / Docker)
 * 2. local.properties (desarrollo local con cualquier tipo de Run Config)
 * Si no se encuentra en ninguna fuente devuelve null.
 */
private fun resolveEnvOrLocal(key: String, localProps: Map<String, String>): String? =
    System.getenv(key) ?: localProps[key]

/** Valores de configuración de JWT/auth leídos de application.conf. */
data class AuthConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtRealm: String,
    val authMode: String,
)

/** Configuración de notificaciones leída de application.conf. */
data class NotificationsConfig(
    /** Radio (en km) para notificar convocatorias cercanas a un jugador. */
    val nearbyRadiusKm: Double,
)

/**
 * Módulo Koin del backend. Reemplaza el service locator manual basado en
 * AttributeKey por inyección de dependencias.
 */
fun backendModule(config: ApplicationConfig) = module {

    // Leemos local.properties una vez al inicio y lo pasamos donde sea necesario.
    val localProps = readLocalProperties()

    single {
        AuthConfig(
            jwtSecret = config.property("jwt.secret").getString(),
            jwtIssuer = config.property("jwt.issuer").getString(),
            jwtAudience = config.property("jwt.audience").getString(),
            jwtRealm = config.property("jwt.realm").getString(),
            authMode = config.propertyOrNull("firebase.authMode")?.getString() ?: "mock",
        )
    }

    single {
        NotificationsConfig(
            nearbyRadiusKm = config.propertyOrNull("notifications.nearbyRadiusKm")
                ?.getString()?.toDoubleOrNull() ?: 50.0,
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

    // Notificaciones push (best-effort: deshabilitado si no hay service account).
    // La ruta al service account se resuelve con este orden de prioridad:
    //   1. application.conf → firebase.serviceAccountPath (normalmente via env var)
    //   2. Variable de entorno FIREBASE_SERVICE_ACCOUNT_PATH
    //   3. local.properties → FIREBASE_SERVICE_ACCOUNT_PATH (dev local, cualquier Run Config)
    single {
        val serviceAccountPath = config.propertyOrNull("firebase.serviceAccountPath")?.getString()
            ?: resolveEnvOrLocal("FIREBASE_SERVICE_ACCOUNT_PATH", localProps)
        FcmService(serviceAccountPath = serviceAccountPath)
    }

    // Tokens QR firmados (reutiliza el secreto JWT para el HMAC)
    single { QrTokenService(secret = get<AuthConfig>().jwtSecret) }

    // Repositorios
    singleOf(::UserRepository)
    singleOf(::ConvocatoryRepository)
    singleOf(::PostulationRepository)
    singleOf(::AttendanceRepository)
    singleOf(::RatingRepository)
    singleOf(::NoShowRepository)
    singleOf(::AttendanceDeclarationRepository)

    // Servicios por feature (rutas delgadas delegan aquí)
    singleOf(::AuthService)
    singleOf(::PlayerService)
    single {
        ConvocatoryService(
            repository = get(),
            users = get(),
            fcm = get(),
            nearbyRadiusKm = get<NotificationsConfig>().nearbyRadiusKm,
        )
    }
    singleOf(::PostulationService)
    singleOf(::AttendanceService)
    singleOf(::RatingService)
    single { NoShowService(repository = get(), declarations = get()) }
    single { AttendanceDeclarationService(repository = get()) }
}
