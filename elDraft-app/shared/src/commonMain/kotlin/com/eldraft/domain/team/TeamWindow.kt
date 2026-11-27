package com.eldraft.domain.team

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Una opción de ventana exclusiva y si se puede elegir para este partido. */
data class TeamWindowOption(val minutes: Int, val enabled: Boolean)

/**
 * Ventana exclusiva del equipo, compartida por Android e iOS. Refleja
 * `TeamPriorityService.prepareCreate` del backend (que sigue siendo la
 * autoridad y responde 400): la ventana debe CERRAR al menos 1 h antes del
 * partido.
 */
object TeamWindow {
    /** Opciones que ofrece el selector, en minutos. */
    val OPTIONS_MINUTES = listOf(30, 60, 120, 360)

    /** La ventana debe cerrar al menos esto antes del partido. */
    const val MIN_LEAD_MINUTES = 60

    private const val MS_PER_MINUTE = 60_000L

    /**
     * Opciones con `enabled` = true solo si, empezando ahora, la ventana cierra a
     * tiempo. [scheduledAt] y [now] son relojes de pared locales (como los manda
     * el formulario); la aritmética pasa por UTC fijo, sin horario de verano.
     */
    fun options(scheduledAt: LocalDateTime, now: LocalDateTime): List<TeamWindowOption> {
        val minutesToMatch = minutesBetween(now, scheduledAt)
        return OPTIONS_MINUTES.map { TeamWindowOption(it, enabled = it + MIN_LEAD_MINUTES <= minutesToMatch) }
    }

    /** True si [minutes] sigue siendo elegible para ese horario (p. ej. tras cambiar la fecha). */
    fun isValid(minutes: Int?, scheduledAt: LocalDateTime, now: LocalDateTime): Boolean =
        minutes != null && options(scheduledAt, now).any { it.minutes == minutes && it.enabled }

    /** Minutos que faltan para [endsAt] (0 si ya pasó). Para la cuenta regresiva. */
    fun minutesLeft(endsAt: LocalDateTime, now: LocalDateTime): Int =
        minutesBetween(now, endsAt).coerceAtLeast(0).toInt()

    /** Minutos enteros de [from] a [to] (negativo si [to] es anterior). */
    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long {
        val ms = to.toInstant(TimeZone.UTC).toEpochMilliseconds() - from.toInstant(TimeZone.UTC).toEpochMilliseconds()
        return ms.floorDiv(MS_PER_MINUTE)
    }
}
