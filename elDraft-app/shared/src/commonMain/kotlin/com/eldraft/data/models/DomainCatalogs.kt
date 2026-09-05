package com.eldraft.data.models

/**
 * Catálogos de valores de dominio que viajan al backend tal cual (posición,
 * pierna hábil, contextura, formato, ambiente, motivo de cancelación). Antes
 * estaban hardcodeados y duplicados en varias pantallas de Android; una
 * divergencia entre plataformas rompería datos, no solo texto, así que se
 * centralizan aquí para que Android e iOS los consuman del mismo lugar.
 */
object DomainCatalogs {
    val POSITIONS = listOf("Arquero", "Defensa", "Mediocampista", "Delantero", "Extremo")
    val FEET = listOf("Derecho", "Zurdo", "Ambidiestro")
    val BUILDS = listOf("Delgado", "Atlético", "Robusto")
    val FORMATS = listOf("Fútbol 5", "Fútbol 7", "Fútbol 8", "Fútbol 9", "Fútbol 11")
    val AMBIENTES = listOf("Recocha", "Competitivo")
    val CANCELLATION_REASONS = listOf(
        "Lluvia / mal clima",
        "Cancha no disponible",
        "Pocos jugadores confirmados",
        "Problema personal",
        "Otro",
    )
}
