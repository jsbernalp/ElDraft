package com.eldraft.backend.service

import com.eldraft.backend.repository.CoPlayRow
import java.time.LocalDateTime
import java.util.UUID

/** Máximo de sugerencias que devuelve `GET /teams/suggestions`. */
const val TEAM_SUGGESTIONS_LIMIT = 50

/** Con cuántos partidos comparte [playerId] con el usuario y cuándo fue el último. */
data class CoPlayCount(
    val playerId: UUID,
    val matches: Int,
    val lastPlayedAt: LocalDateTime,
)

/**
 * "Ya jugaron juntos", contado por jugador a partir de las filas crudas de
 * [com.eldraft.backend.repository.TeamRepository.coPlayRows].
 *
 * - Participante de un partido = postulación `approved` u organizador.
 * - Partido válido = no cancelado y terminado ([MatchWindows.isOver]).
 * - Si [me] quedó marcado no-show (o era el organizador y hubo
 *   `organizer_no_show`), ese partido no cuenta para nadie: no estuve.
 * - Si el otro quedó marcado no-show, él no cuenta en ese partido.
 * - [me] nunca aparece en el resultado.
 *
 * No exige escaneo de QR ni `organizer_confirmed`: reutiliza la verdad de
 * asistencia que ya existe (las marcas de no-show).
 *
 * Orden: más partidos juntos primero; a igualdad, el último partido más reciente.
 * Se corta en [limit].
 */
fun coPlayCounts(
    rows: List<CoPlayRow>,
    me: UUID,
    now: LocalDateTime,
    limit: Int = TEAM_SUGGESTIONS_LIMIT,
): List<CoPlayCount> {
    val matches = mutableMapOf<UUID, Int>()
    val last = mutableMapOf<UUID, LocalDateTime>()

    rows.groupBy { it.convocatoryId }.values.forEach { convRows ->
        val conv = convRows.first()
        if (conv.convocatoryStatus == "cancelled") return@forEach
        if (!MatchWindows.isOver(conv.scheduledAt, now)) return@forEach

        // Participantes con su bandera de ausencia: aprobados + organizador.
        val present = mutableMapOf<UUID, Boolean>()
        convRows.filter { it.postulationStatus == "approved" }
            .forEach { present[it.playerId] = !it.playerMarkedNoShow }
        present[conv.organizerId] = !conv.organizerNoShow

        if (present[me] != true) return@forEach

        present.filter { (id, wasThere) -> id != me && wasThere }.keys.forEach { other ->
            matches[other] = (matches[other] ?: 0) + 1
            val prev = last[other]
            if (prev == null || conv.scheduledAt.isAfter(prev)) last[other] = conv.scheduledAt
        }
    }

    return matches.map { (id, n) -> CoPlayCount(id, n, last.getValue(id)) }
        .sortedWith(compareByDescending<CoPlayCount> { it.matches }.thenByDescending { it.lastPlayedAt })
        .take(limit)
}
