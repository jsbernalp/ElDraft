package com.eldraft.domain.match

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private val ZONE = TimeZone.UTC
private const val SCHEDULED_AT = "2026-03-05T20:30:00"
private val START = LocalDateTime.parse(SCHEDULED_AT).toInstant(ZONE)

private fun at(minutesFromStart: Long): Instant = START.plus(minutesFromStart.minutes)

class MatchTimingTest {

    @Test
    fun minutesSinceStart_devuelveNullConFechaInvalida() {
        assertNull(minutesSinceStart("no-es-una-fecha", now = START, zone = ZONE))
    }

    @Test
    fun minutesSinceStart_esNegativoAntesDeEmpezar() {
        assertEquals(-10L, minutesSinceStart(SCHEDULED_AT, now = at(-10), zone = ZONE))
    }

    @Test
    fun hasMatchStarted_umbralEnElMinutoCero() {
        assertFalse(hasMatchStarted(SCHEDULED_AT, now = at(-1), zone = ZONE))
        assertTrue(hasMatchStarted(SCHEDULED_AT, now = at(0), zone = ZONE))
    }

    @Test
    fun canReportNoShowByTime_umbralEnElMinuto15() {
        assertFalse(canReportNoShowByTime(SCHEDULED_AT, now = at(NO_SHOW_OPEN_MINUTES - 1), zone = ZONE))
        assertTrue(canReportNoShowByTime(SCHEDULED_AT, now = at(NO_SHOW_OPEN_MINUTES), zone = ZONE))
    }

    @Test
    fun isMatchOver_umbralEnElMinuto45() {
        assertFalse(isMatchOver(SCHEDULED_AT, now = at(MATCH_END_MINUTES - 1), zone = ZONE))
        assertTrue(isMatchOver(SCHEDULED_AT, now = at(MATCH_END_MINUTES), zone = ZONE))
    }

    @Test
    fun matchPhase_recorreLasTresFasesEnOrden() {
        assertEquals(MatchPhase.BEFORE_REPORT, matchPhase(SCHEDULED_AT, now = at(0), zone = ZONE))
        assertEquals(MatchPhase.REPORT_WINDOW, matchPhase(SCHEDULED_AT, now = at(NO_SHOW_OPEN_MINUTES), zone = ZONE))
        assertEquals(MatchPhase.AFTER_END, matchPhase(SCHEDULED_AT, now = at(MATCH_END_MINUTES), zone = ZONE))
    }

    @Test
    fun matchPhase_esUnknownConFechaInvalida() {
        assertEquals(MatchPhase.UNKNOWN, matchPhase("no-es-una-fecha", now = START, zone = ZONE))
    }
}
