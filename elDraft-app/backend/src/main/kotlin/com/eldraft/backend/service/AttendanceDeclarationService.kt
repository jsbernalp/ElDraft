package com.eldraft.backend.service

import com.eldraft.backend.repository.AttendanceDeclarationRepository
import com.eldraft.backend.repository.PlayerAttendanceRow
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/** Errores de dominio de la declaración de asistencia (las rutas los mapean a HTTP). */
class DeclarationForbidden(message: String) : RuntimeException(message)
class DeclarationNotFound(message: String) : RuntimeException(message)
class DeclarationWindowClosed(message: String) : RuntimeException(message)

/**
 * Declaración de asistencia por el organizador (caso "el convocado no llegó").
 *
 * El organizador es la autoridad del partido: al cierre declara, en una sola
 * pasada, quién de sus convocados aprobados NO llegó. Lo no marcado cuenta como
 * presente (modelo explícito). Reglas:
 *  - Solo el organizador puede declarar.
 *  - Si el consenso lo marcó a ÉL como no-show, no puede declarar (no estuvo).
 *  - Solo tras el cierre del partido (scheduled_at + [MATCH_END_MINUTES]).
 *  - El QR es prueba firme: a quien escaneó no se le puede marcar ausente.
 *  - Solo se puede marcar a jugadores aprobados de esa convocatoria.
 *
 * La lista es re-declarable: al guardar se reemplazan las marcas y se recalculan
 * attendance_pct y responsabilidad de los afectados, de forma reversible.
 */
class AttendanceDeclarationService(
    private val repository: AttendanceDeclarationRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /** Lista de aprobados con su estado, para pintar la pantalla (solo organizador). */
    fun attendanceList(convocatoryId: UUID, requesterId: UUID): List<PlayerAttendanceRow> {
        val ctx = repository.context(convocatoryId)
            ?: throw DeclarationNotFound("Convocatoria no encontrada")
        if (requesterId != ctx.organizerId) {
            throw DeclarationForbidden("Solo el organizador puede ver la asistencia")
        }
        if (ctx.organizerNoShow) {
            throw DeclarationForbidden("No puedes declarar la asistencia: el consenso marcó que no llegaste al partido")
        }
        return repository.approvedPlayers(convocatoryId)
    }

    /** Declara la lista de ausentes y recalcula métricas. Devuelve la lista actualizada. */
    fun declare(
        convocatoryId: UUID,
        requesterId: UUID,
        absentPlayerIds: Set<UUID>,
    ): List<PlayerAttendanceRow> {
        val ctx = repository.context(convocatoryId)
            ?: throw DeclarationNotFound("Convocatoria no encontrada")

        if (requesterId != ctx.organizerId) {
            throw DeclarationForbidden("Solo el organizador puede declarar la asistencia")
        }
        if (ctx.organizerNoShow) {
            throw DeclarationForbidden("No puedes declarar la asistencia: el consenso marcó que no llegaste al partido")
        }

        val now = LocalDateTime.now(clock)
        if (now.isBefore(ctx.scheduledAt.plusMinutes(MATCH_END_MINUTES))) {
            throw DeclarationWindowClosed(
                "Aún no puedes declarar la asistencia: disponible al terminar el partido"
            )
        }

        val players = repository.approvedPlayers(convocatoryId)
        val approvedIds = players.map { it.playerId }.toSet()
        val scannedIds = players.filter { it.scanned }.map { it.playerId }.toSet()

        val notApproved = absentPlayerIds - approvedIds
        if (notApproved.isNotEmpty()) {
            throw DeclarationForbidden("Solo puedes marcar a jugadores convocados de este partido")
        }
        val scannedAbsent = absentPlayerIds intersect scannedIds
        if (scannedAbsent.isNotEmpty()) {
            throw DeclarationForbidden("No puedes marcar ausente a quien registró su asistencia")
        }

        val affected = repository.replaceMarks(convocatoryId, absentPlayerIds)
        affected.forEach { pid ->
            repository.recomputeAttendance(pid)
            repository.recomputeResponsibility(pid)
        }

        // Declarar (aunque la lista sea vacía) confirma que el organizador estuvo:
        // bloquea reportes de "no llegó" en su contra.
        repository.markOrganizerConfirmed(convocatoryId)

        return repository.approvedPlayers(convocatoryId)
    }

    private companion object {
        // Minutos tras el inicio en que el partido se considera terminado.
        // Vive en MatchWindows: también decide qué se ve en las listas.
        const val MATCH_END_MINUTES = MatchWindows.MATCH_END_MINUTES
    }
}
