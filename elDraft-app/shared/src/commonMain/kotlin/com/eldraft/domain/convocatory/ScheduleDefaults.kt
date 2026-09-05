package com.eldraft.domain.convocatory

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/** Hora habitual de partido y anticipación mínima para publicar uno. */
private const val DEFAULT_MATCH_HOUR = 19
const val MIN_HOURS_AHEAD = 1L

// La aritmética sobre LocalDateTime es agnóstica de zona (cálculo puro de
// calendario, igual que java.time.LocalDateTime.plusHours/truncatedTo): se hace
// pasando por un Instant en UTC fijo, que no tiene horario de verano, así que da
// el mismo resultado que sumar directamente sobre el reloj de pared.
private fun LocalDateTime.plusHours(hoursToAdd: Long): LocalDateTime =
    toInstant(TimeZone.UTC).plus(hoursToAdd.hours).toLocalDateTime(TimeZone.UTC)

private fun LocalDateTime.plusOneDay(): LocalDateTime =
    LocalDateTime(date.plus(1, DateTimeUnit.DAY), time)

private fun LocalDateTime.truncatedToHour(): LocalDateTime = LocalDateTime(date, LocalTime(hour, 0))

/**
 * Fecha/hora sugerida al abrir el formulario, en tres escalones:
 *
 *  1. Hoy a las 7 p.m., si todavía cumple la anticipación mínima.
 *  2. Si esa hora ya pasó, esta misma noche a la siguiente hora en punto: a las
 *     8:30 p.m. todavía se arma un partido para las 10.
 *  3. Solo cuando ya no cabe nada hoy —la siguiente hora se pasa de medianoche—
 *     se propone mañana a las 7 p.m.
 *
 * [now] se inyecta solo para poder probar los bordes de la hora.
 */
fun defaultScheduledAt(now: LocalDateTime): LocalDateTime {
    val todayAtDefaultHour = LocalDateTime(now.date, LocalTime(DEFAULT_MATCH_HOUR, 0))
    // Estricto a propósito: es la misma comparación que valida el formulario, así
    // que proponer un horario "justo en el límite" lo dejaría inválido de entrada.
    val earliest = now.plusHours(MIN_HOURS_AHEAD)
    if (todayAtDefaultHour > earliest) return todayAtDefaultHour

    val tonight = earliest.truncatedToHour().plusHours(1)
    return if (tonight.date == now.date) tonight else todayAtDefaultHour.plusOneDay()
}

/**
 * El DatePicker de Material3 no habla en instantes sino en "medianoche UTC del
 * día" de cada celda. Convertir con la zona local corre el día cuando la hora
 * local ya cae en otro día UTC: 7 p.m. en Colombia (UTC-5) son las 00:00 UTC del
 * día siguiente, y el calendario marcaba un día de más.
 *
 * Por eso aquí solo entra la FECHA y la conversión es siempre en UTC: la hora del
 * partido no participa, así que da igual si es un partido de las 8 a.m. o de las
 * 11 p.m. — los nocturnos eran justo los que se corrían.
 */
fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * Igual, para la fecha/hora del partido: descarta la hora ANTES de convertir, que
 * es justo el paso que faltaba. Existe para que el llamador no tenga que acordarse
 * de hacerlo (olvidarlo es el bug que corrían los partidos nocturnos).
 */
fun LocalDateTime.toPickerMillis(): Long = date.toPickerMillis()

/** Inversa de [toPickerMillis]: el día que representa la celda elegida. */
fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
