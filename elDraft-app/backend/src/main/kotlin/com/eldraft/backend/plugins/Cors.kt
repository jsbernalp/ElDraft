package com.eldraft.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/**
 * CORS restringido a una lista explícita de orígenes.
 *
 * La app Android es un cliente nativo y NO necesita CORS: sin ningún host
 * configurado el backend sigue funcionando para ella. La lista existe para el
 * futuro panel/web admin, que sí corre en un navegador.
 *
 * Configuración: `cors.allowedHosts`, separados por coma. Cada entrada acepta
 * host con o sin esquema (`admin.eldraft.app`, `https://admin.eldraft.app`,
 * `http://localhost:3000`). Sin esquema se asume https.
 */
fun Application.configureCors() {
    val allowedHosts = environment.config
        .propertyOrNull("cors.allowedHosts")?.getString()
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        allowedHosts.forEach { entry ->
            val schemes = if (entry.startsWith("http://")) listOf("http") else listOf("https")
            val host = entry.removePrefix("https://").removePrefix("http://")
            allowHost(host, schemes = schemes)
        }
    }

    if (allowedHosts.isEmpty()) {
        log.info("CORS sin orígenes permitidos (solo clientes nativos). Configura CORS_ALLOWED_HOSTS si añades un panel web.")
    } else {
        log.info("CORS permitido para: ${allowedHosts.joinToString()}")
    }
}
