package com.eldraft.backend.service

import java.time.Duration
import java.time.LocalDateTime

/**
 * Reglas de solapamiento de horarios, compartidas por las validaciones de
 * "no puedes jugar/organizar dos partidos a la vez".
 *
 * Como el modelo no guarda duración, se asume una duración fija: dos partidos
 * "chocan" si sus ventanas [inicio, inicio + 60 min) se solapan, es decir, si
 * sus horas de inicio distan menos de 60 minutos.
 */
const val MATCH_DURATION_MIN = 60L

/** True si las ventanas [a, a+60m) y [b, b+60m) se solapan. */
fun overlaps(a: LocalDateTime, b: LocalDateTime): Boolean =
    Duration.between(a, b).abs() < Duration.ofMinutes(MATCH_DURATION_MIN)

/**
 * Parsea un `scheduledAt` en ISO local date-time (p. ej. "2026-06-16T21:05").
 * Tolerante: si no se puede parsear devuelve null y quien lo use debe omitir el
 * chequeo de conflicto para ese registro (mejor permitir que romper).
 */
fun parseSchedule(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw) }.getOrNull()
