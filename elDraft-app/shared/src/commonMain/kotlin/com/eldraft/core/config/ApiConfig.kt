package com.eldraft.core.config

/**
 * Configuración de endpoints inyectada por cada plataforma.
 * Android la construye desde BuildConfig; iOS la proveerá desde su propio config.
 */
data class ApiConfig(
    val baseUrl: String,
    val wsBaseUrl: String,
)
