package com.eldraft.backend.service

import com.eldraft.backend.repository.AttendanceDeclarationRepository
import com.eldraft.backend.repository.NoShowRepository
import com.eldraft.backend.repository.RatingRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * Plazos compartidos del ciclo de vida de un partido.
 *
 * Estaban duplicados en companions privados de [NoShowService] y
 * [AttendanceDeclarationService]. Ahora que también deciden qué se ve en las
 * listas, dos copias del mismo 45 eran una divergencia esperando a pasar.
 */
object MatchWindows {
    /** Minutos tras el inicio en que el partido se considera terminado. */
    const val MATCH_END_MINUTES = 45L

    /** Margen de tolerancia tras el inicio antes de poder reportar un no-show. */
    const val NO_SHOW_OPEN_AFTER_MINUTES = 15L

    /** Cierre del reporte de "el organizador no llegó". */
    const val NO_SHOW_CLOSE_AFTER_HOURS = 48L
}

/**
 * Decide si una convocatoria YA TERMINADA sigue mereciendo un sitio en la lista
 * de quien la mira.
 *
 * El problema que resuelve: nada retiraba nunca un partido. `findByOrganizer`
 * filtraba solo por status='active' y `findByPlayer` no filtraba nada, así que
 * cada partido creado se quedaba para siempre entre los que aún no empiezan.
 *
 * El criterio es "¿te queda algo por hacer aquí?", no "¿ya calificaste?". La
 * diferencia importa: calificar exige haber asistido, así que un organizador que
 * no se presentó no puede calificar a nadie nunca, y con el criterio de
 * "ya calificaste a todos" su tarjeta no se iría jamás. Sin nada que hacer, la
 * tarjeta se va.
 *
 * Lo pendiente NO caduca por tiempo (decisión de producto): mientras quede algo
 * por hacer, el partido sigue visible en su sección. El único plazo es el del
 * reporte de no-show, que ya tenía el suyo propio de 48 h.
 */
class MatchLifecycle(
    private val ratings: RatingRepository,
    private val declarations: AttendanceDeclarationRepository,
    private val noShow: NoShowRepository,
) {

    /** True si el partido ya terminó (inicio + 45 min). */
    fun isOver(scheduledAt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): Boolean =
        !now.isBefore(scheduledAt.plusMinutes(MatchWindows.MATCH_END_MINUTES))

    /**
     * Al organizador le queda algo si todavía debe declarar quién asistió, o si
     * le faltan jugadores por calificar.
     *
     * Sin jugadores aprobados no hay nada que declarar ni a quién calificar: ese
     * es el partido que nadie llegó a jugar, y se va en cuanto termina.
     */
    fun organizerHasPending(convocatoryId: UUID, organizerId: UUID): Boolean {
        val ctx = noShow.context(convocatoryId) ?: return false
        if (declarations.approvedPlayers(convocatoryId).isEmpty()) return false

        // Si el consenso ya lo marcó ausente no puede declarar: no estuvo, no
        // puede dar fe de quién llegó.
        val mustDeclare = !ctx.organizerConfirmed && !ctx.organizerNoShow
        return mustDeclare || pendingRatings(convocatoryId, organizerId) > 0
    }

    /**
     * Al jugador le queda algo si puede calificar a alguien, o si todavía puede
     * reportar que el organizador no llegó.
     *
     * Lo segundo no es un detalle: si el organizador no apareció, nadie escaneó
     * el QR, nadie tiene asistencia y por tanto nadie puede calificar. Sin esta
     * condición la tarjeta desaparecería justo en el caso en que el jugador más
     * necesita actuar.
     */
    fun playerHasPending(
        convocatoryId: UUID,
        playerId: UUID,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        val ctx = noShow.context(convocatoryId) ?: return false

        val reportClosesAt = ctx.scheduledAt.plusHours(MatchWindows.NO_SHOW_CLOSE_AFTER_HOURS)
        val canReportNoShow = now.isBefore(reportClosesAt) &&
            !ctx.organizerConfirmed &&
            !ctx.organizerNoShow &&
            !noShow.hasReported(convocatoryId, playerId)

        return canReportNoShow || pendingRatings(convocatoryId, playerId) > 0
    }

    /**
     * Cuántas personas le faltan por calificar. Cero si no asistió: sin
     * asistencia validada no puede calificar a nadie, y ese partido ya no le pide
     * nada.
     */
    private fun pendingRatings(convocatoryId: UUID, viewerId: UUID): Int {
        if (!ratings.attended(convocatoryId, viewerId)) return 0
        return ratings.teammates(convocatoryId, viewerId).count { !it.alreadyRated }
    }
}
