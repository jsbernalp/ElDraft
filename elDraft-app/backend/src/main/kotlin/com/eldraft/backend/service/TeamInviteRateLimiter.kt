package com.eldraft.backend.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Límite de invitaciones por correo: [limit] intentos por usuario en una ventana
 * deslizante de [window]. Evita usar el campo de correo para sondear qué correos
 * tienen cuenta.
 *
 * Cada intento cuenta sea cual sea su resultado (incluido "no existe"); los
 * intentos rechazados por el propio límite no se registran. Vive en memoria: un
 * reinicio lo limpia y con varias instancias el límite es por instancia, lo cual
 * basta para frenar el sondeo masivo.
 */
open class TeamInviteRateLimiter(
    private val clock: Clock = Clock.systemUTC(),
    private val limit: Int = DEFAULT_LIMIT,
    private val window: Duration = DEFAULT_WINDOW,
) {
    private val hits = ConcurrentHashMap<UUID, ArrayDeque<Instant>>()

    /** Registra un intento de [userId]. False si ya agotó el cupo de la ventana. */
    open fun tryAcquire(userId: UUID): Boolean {
        val now = clock.instant()
        val windowStart = now.minus(window)
        val queue = hits.computeIfAbsent(userId) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && !queue.first().isAfter(windowStart)) queue.removeFirst()
            if (queue.size >= limit) return false
            queue.addLast(now)
            return true
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        val DEFAULT_WINDOW: Duration = Duration.ofMinutes(60)
    }
}
