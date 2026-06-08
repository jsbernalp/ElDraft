package com.eldraft.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Error de API con el status HTTP y el cuerpo crudo devuelto por el backend. */
class ApiException(
    val status: Int,
    val body: String,
    cause: Throwable? = null,
) : RuntimeException("HTTP $status: ${body.ifBlank { "(sin cuerpo)" }}", cause) {

    /** Código de error del backend (p. ej. "FORBIDDEN"), si vino en el cuerpo. */
    val code: String? = parseField("code")

    /**
     * Mensaje apto para mostrar al usuario. Prioriza el `message` del backend;
     * si no hay cuerpo legible, cae a un texto genérico según el status HTTP.
     */
    val userMessage: String =
        parseField("message")?.takeIf { it.isNotBlank() } ?: genericMessageFor(status)

    private fun parseField(name: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content
    }.getOrNull()

    private fun genericMessageFor(status: Int): String = when (status) {
        401 -> "Tu sesión expiró. Inicia sesión de nuevo."
        403 -> "No tienes permiso para esta acción."
        404 -> "No encontramos lo que buscabas."
        409 -> "Esta acción ya se realizó."
        in 500..599 -> "El servidor tuvo un problema. Intenta más tarde."
        else -> "Algo salió mal. Intenta de nuevo."
    }
}

/** Mensaje amable para cualquier excepción (API o de red). Útil en ViewModels. */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is ApiException -> userMessage
    else -> message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") } ?: fallback
}
