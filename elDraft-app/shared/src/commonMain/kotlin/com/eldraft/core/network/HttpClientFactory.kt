package com.eldraft.core.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/** Json compartido por cliente y (potencialmente) decodificación manual de WS. */
val eldraftJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Crea el [HttpClient] con la configuración común de la app:
 * - ContentNegotiation (JSON)
 * - WebSockets
 * - expectSuccess + traducción de errores no-2xx a [ApiException]
 *
 * El token de auth NO se configura aquí; cada API lo añade por request leyendo
 * del [AuthTokenProvider] (ver [withAuth]).
 */
fun createHttpClient(json: Json = eldraftJson): HttpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
    install(WebSockets)
    expectSuccess = true
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            val clientEx = exception as? ResponseException ?: return@handleResponseExceptionWithRequest
            val response = clientEx.response
            val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
            throw ApiException(
                status = response.status.value,
                body = errorBody,
                cause = exception
            )
        }
    }
}
