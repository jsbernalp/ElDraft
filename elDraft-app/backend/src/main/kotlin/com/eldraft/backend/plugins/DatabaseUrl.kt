package com.eldraft.backend.plugins

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Datos de conexión ya normalizados para HikariCP. */
data class DatabaseCredentials(
    val jdbcUrl: String,
    val user: String,
    val password: String,
)

/**
 * Normaliza la URL de base de datos a formato JDBC.
 *
 * El driver de PostgreSQL solo entiende `jdbc:postgresql://…`, pero la mayoría de
 * proveedores gestionados (Railway, Heroku, Supabase, Neon) publican la conexión
 * en el formato estándar de URI de Postgres, con las credenciales dentro:
 *
 *     postgresql://usuario:clave@host:5432/basededatos
 *
 * En Railway concretamente, el servicio de Postgres inyecta `DATABASE_URL` con ese
 * formato en cuanto lo referencias. Pasarlo tal cual a Hikari revienta con un
 * "No suitable driver" que no dice nada sobre la causa real. Aceptar las dos
 * formas elimina esa clase entera de fallo de despliegue.
 *
 * Las credenciales embebidas en la URI **ganan** sobre [fallbackUser] /
 * [fallbackPassword]: si el proveedor las manda ahí, son las suyas las que valen.
 */
fun normalizeDatabaseUrl(
    rawUrl: String,
    fallbackUser: String,
    fallbackPassword: String,
): DatabaseCredentials {
    if (rawUrl.startsWith("jdbc:")) {
        return DatabaseCredentials(rawUrl, fallbackUser, fallbackPassword)
    }

    require(rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
        "DATABASE_URL='${rawUrl.substringBefore("://")}://…' no tiene un formato reconocido. " +
            "Usa 'jdbc:postgresql://host:puerto/bd' o 'postgresql://usuario:clave@host:puerto/bd'."
    }

    val uri = try {
        URI(rawUrl)
    } catch (e: Exception) {
        throw IllegalArgumentException(
            "DATABASE_URL no se puede parsear como URI. Si la contraseña tiene caracteres " +
                "especiales (@ : / ?), deben ir percent-encoded.",
            e,
        )
    }

    val host = requireNotNull(uri.host) { "DATABASE_URL no incluye host." }
    val port = if (uri.port != -1) uri.port else 5432
    val database = uri.path.removePrefix("/").ifBlank {
        throw IllegalArgumentException("DATABASE_URL no incluye el nombre de la base de datos.")
    }

    // userInfo llega percent-encoded; las contraseñas generadas por los proveedores
    // suelen traer caracteres que lo requieren.
    val userInfo = uri.rawUserInfo?.split(":", limit = 2).orEmpty()
    val user = userInfo.getOrNull(0)?.decoded()?.ifBlank { null } ?: fallbackUser
    val password = userInfo.getOrNull(1)?.decoded() ?: fallbackPassword

    // Los parámetros de query (sslmode, etc.) se conservan tal cual.
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()

    return DatabaseCredentials(
        jdbcUrl = "jdbc:postgresql://$host:$port/$database$query",
        user = user,
        password = password,
    )
}

private fun String.decoded(): String = URLDecoder.decode(this, StandardCharsets.UTF_8)
