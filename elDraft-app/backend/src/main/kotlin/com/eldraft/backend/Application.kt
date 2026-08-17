package com.eldraft.backend

import com.eldraft.backend.di.backendModule
import com.eldraft.backend.plugins.*
import com.eldraft.backend.plugins.normalizeDatabaseUrl
import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    // Antes que nada: si la configuración de producción es insegura, no arrancamos.
    validateProductionConfig()

    // Antes de abrir el pool: el driver de Postgres manda la zona por defecto de
    // la JVM en el handshake de cada conexión, así que esto tiene que estar fijado
    // antes de que Hikari cree la primera.
    configureTimeZone()

    // DI primero: el resto de la configuración resuelve servicios desde Koin.
    install(Koin) {
        modules(backendModule(environment.config))
    }

    configureSerialization()
    configureCors()
    configureDatabases()  // conecta la BD antes de atender peticiones
    configureAuth()
    configureWebSockets()
    configureStatusPages()
    configureRouting()
    configureScheduler()  // tareas periódicas (recordatorio de convocatorias)
}

/**
 * Fija la zona horaria por defecto de la JVM a `app.timezone`.
 *
 * Todo el dominio trabaja con `LocalDateTime` sin offset (la hora local del
 * organizador, tal como la escribió), y esas horas se comparan contra
 * `LocalDateTime.now()` y contra el `NOW()` de Postgres. En un servidor en UTC
 * esas dos referencias no son la misma hora que la del usuario, y un partido de
 * esta tarde se evalúa como pasado.
 *
 * Si el identificador no existe, `getTimeZone` devuelve GMT en silencio; por eso
 * se valida y se aborta, que es justo el fallo que este parche viene a evitar.
 */
private fun Application.configureTimeZone() {
    val id = environment.config.propertyOrNull("app.timezone")?.getString()?.takeIf { it.isNotBlank() }
        ?: DEFAULT_TIMEZONE

    val zone = try {
        java.time.ZoneId.of(id)
    } catch (e: Exception) {
        error("app.timezone='$id' no es una zona horaria válida (usa un ID de la IANA, ej. America/Bogota)")
    }

    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
    log.info("Zona horaria del backend: $zone (hora local: ${java.time.LocalDateTime.now()})")
}

/** Zona por defecto: la app hoy solo opera en Colombia. */
internal const val DEFAULT_TIMEZONE = "America/Bogota"

/** Valores por defecto de application.conf que jamás deben llegar a producción. */
private const val DEFAULT_JWT_SECRET = "change-me-in-production"
private const val DEFAULT_DB_PASSWORD = "eldraft"

/**
 * Aborta el arranque si el servidor quedaría expuesto con configuración de desarrollo.
 *
 * El problema que resuelve: `application.conf` trae valores por defecto usables en
 * local, y si la variable de entorno correspondiente no se define en el servidor,
 * el backend arrancaba igual —sin fallar— firmando JWTs con un secreto público y
 * aceptando cualquier identidad. Fallar ruidosamente al arrancar es preferible a
 * servir tráfico real en esas condiciones.
 *
 * En desarrollo (`ktor.development = true`, que el task :backend:run activa) solo
 * se avisa, para no estorbar el trabajo local.
 */
private fun Application.validateProductionConfig() {
    val config = environment.config

    val problems = buildList {
        val jwtSecret = config.property("jwt.secret").getString()
        if (jwtSecret.isBlank() || jwtSecret == DEFAULT_JWT_SECRET) {
            add(
                "JWT_SECRET no está definida (o es el valor por defecto). Cualquiera podría " +
                    "firmar tokens de sesión válidos. Genera uno con: openssl rand -base64 48"
            )
        }

        // Se valida la contraseña EFECTIVA, no la variable suelta: proveedores como
        // Railway la mandan embebida dentro de DATABASE_URL y en ese caso
        // DATABASE_PASSWORD se queda con el default sin que eso sea un problema.
        val effectiveDbPassword = runCatching {
            normalizeDatabaseUrl(
                rawUrl = config.property("database.url").getString(),
                fallbackUser = config.property("database.user").getString(),
                fallbackPassword = config.property("database.password").getString(),
            ).password
        }.getOrElse { config.property("database.password").getString() }

        if (effectiveDbPassword.isBlank() || effectiveDbPassword == DEFAULT_DB_PASSWORD) {
            add(
                "La contraseña de la base de datos no está definida (o es el valor por defecto " +
                    "'eldraft'). Define DATABASE_PASSWORD, o incluye las credenciales dentro de " +
                    "DATABASE_URL."
            )
        }

        val authMode = config.propertyOrNull("firebase.authMode")?.getString() ?: "mock"
        if (!authMode.equals("firebase", ignoreCase = true)) {
            add(
                "FIREBASE_AUTH_MODE='$authMode': los tokens no se verifican contra Google y " +
                    "cualquiera puede autenticarse como cualquier usuario. Debe ser 'firebase'."
            )
        }

        val resetOnStart = config.propertyOrNull("database.resetOnStart")
            ?.getString()?.toBoolean() ?: false
        if (resetOnStart) {
            add("DATABASE_RESET_ON_START=true borraría todos los datos en cada arranque.")
        }
    }

    if (problems.isEmpty()) return

    val detalle = problems.joinToString("\n") { "  - $it" }

    if (developmentMode) {
        log.warn("Configuración insegura (tolerada solo por ktor.development=true):\n$detalle")
        return
    }

    error(
        "El backend NO puede arrancar en producción con esta configuración:\n$detalle\n" +
            "Define las variables de entorno faltantes, o activa ktor.development=true si " +
            "esto es un entorno local."
    )
}
