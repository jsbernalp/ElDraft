package com.eldraft.domain.match

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Fase temporal del partido respecto a su hora de inicio, calculada con la hora
 * del dispositivo. Sirve para decidir qué acciones mostrar en las cards:
 *  - [BEFORE_REPORT]   < inicio + 15 min  (recién empieza / aún no inicia)
 *  - [REPORT_WINDOW]   inicio + 15 min .. inicio + 45 min
 *  - [AFTER_END]       >= inicio + 45 min (partido considerado terminado)
 */
enum class MatchPhase { BEFORE_REPORT, REPORT_WINDOW, AFTER_END, UNKNOWN }

// Umbrales de fase del partido (minutos desde el inicio).
const val NO_SHOW_OPEN_MINUTES = 15L
const val MATCH_END_MINUTES = 45L

/** Minutos transcurridos desde la hora de inicio (negativo si aún no inicia). */
fun minutesSinceStart(
    scheduledAt: String,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long? = runCatching {
    val start = LocalDateTime.parse(scheduledAt).toInstant(zone)
    (now - start).inWholeMinutes
}.getOrNull()

/** Fase del partido según los minutos transcurridos desde el inicio. */
fun matchPhase(
    scheduledAt: String,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): MatchPhase {
    val mins = minutesSinceStart(scheduledAt, now, zone) ?: return MatchPhase.UNKNOWN
    return when {
        mins >= MATCH_END_MINUTES -> MatchPhase.AFTER_END
        mins >= NO_SHOW_OPEN_MINUTES -> MatchPhase.REPORT_WINDOW
        else -> MatchPhase.BEFORE_REPORT
    }
}

/** True si ya pasó la ventana de tolerancia para reportar no-show (inicio + 15 min). */
fun canReportNoShowByTime(
    scheduledAt: String,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean = (minutesSinceStart(scheduledAt, now, zone) ?: Long.MIN_VALUE) >= NO_SHOW_OPEN_MINUTES

/** True si el partido ya se considera terminado (inicio + 45 min). */
fun isMatchOver(
    scheduledAt: String,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean = (minutesSinceStart(scheduledAt, now, zone) ?: Long.MIN_VALUE) >= MATCH_END_MINUTES

/**
 * True si el partido ya arrancó (pasó su hora de inicio). Es el umbral que usa el
 * backend para cerrar cancelar y retirarse, distinto del de "terminado": entre el
 * pitazo inicial y el minuto 45 el partido está en curso, no cerrado.
 */
fun hasMatchStarted(
    scheduledAt: String,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean = (minutesSinceStart(scheduledAt, now, zone) ?: Long.MIN_VALUE) >= 0
