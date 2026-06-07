package com.eldraft.core.network

/** Error de API con el status HTTP y el cuerpo crudo devuelto por el backend. */
class ApiException(
    val status: Int,
    val body: String,
    cause: Throwable? = null
) : RuntimeException("HTTP $status: ${body.ifBlank { "(sin cuerpo)" }}", cause)
