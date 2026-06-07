package com.eldraft.core.network

/**
 * Provee el token JWT actual para autenticar las peticiones.
 *
 * Implementado por la capa de sesión (en Android, respaldado por DataStore).
 * Reemplaza el antiguo `ElDraftApi.setToken()` manual: las APIs leen el token
 * desde aquí en cada request, así la fuente de verdad es siempre la sesión.
 */
fun interface AuthTokenProvider {
    suspend fun currentToken(): String?
}
