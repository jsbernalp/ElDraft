package com.eldraft.backend.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamInviteRateLimiterTest {

    /** Reloj que el test mueve a mano. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) { now = now.plus(d) }
    }

    private val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
    private val limiter = TeamInviteRateLimiter(clock)
    private val user = UUID.randomUUID()

    @Test
    fun pasan_20_y_el_21_falla() {
        repeat(20) { assertTrue(limiter.tryAcquire(user), "intento ${it + 1}") }
        assertFalse(limiter.tryAcquire(user))
    }

    @Test
    fun la_ventana_deslizante_libera_cupo_al_pasar_la_hora() {
        repeat(20) {
            limiter.tryAcquire(user)
            clock.advance(Duration.ofMinutes(1))
        }
        assertFalse(limiter.tryAcquire(user))
        // El primer intento fue hace 20 min; a los 60 min de él se libera uno solo.
        clock.advance(Duration.ofMinutes(40))
        assertTrue(limiter.tryAcquire(user))
        assertFalse(limiter.tryAcquire(user))
    }

    @Test
    fun el_contador_es_por_usuario() {
        repeat(20) { limiter.tryAcquire(user) }
        assertFalse(limiter.tryAcquire(user))
        assertTrue(limiter.tryAcquire(UUID.randomUUID()))
    }
}
