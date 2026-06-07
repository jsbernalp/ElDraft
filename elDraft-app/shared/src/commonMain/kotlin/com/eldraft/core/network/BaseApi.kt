package com.eldraft.core.network

import com.eldraft.core.config.ApiConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Base para las APIs por feature. Centraliza el cliente, la URL base y la
 * inyección del header Authorization desde el [AuthTokenProvider].
 */
abstract class BaseApi(
    protected val client: HttpClient,
    config: ApiConfig,
    private val tokenProvider: AuthTokenProvider,
) {
    protected val baseUrl: String = config.baseUrl
    protected val wsBaseUrl: String = config.wsBaseUrl

    /** Añade el header Bearer con el token actual (si existe) a la request. */
    protected suspend fun HttpRequestBuilder.auth() {
        tokenProvider.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }
}
